package com.windowweb.browser.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.HttpAuthHandler
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.windowweb.browser.BuildConfig
import com.windowweb.browser.core.ConsoleEntry
import com.windowweb.browser.core.ConsoleLevel
import com.windowweb.browser.util.DevServerEndpointPolicy
import com.windowweb.browser.util.DevServerProxyPolicy
 
 class BrowserWebViewClient(
    private val tabId: String,
    private val onPageStarted: (tabId: String, url: String?) -> Unit,
    private val onPageFinished: (tabId: String, url: String?, title: String?) -> Unit,
    private val onNavigationStateChanged: (tabId: String, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    private val onRequestSeen: (tabId: String, url: String, method: String, resourceType: String) -> Unit = { _, _, _, _ -> },
    private val onPageError: (tabId: String, url: String?, description: String?, errorCode: Int?) -> Unit = { _, _, _, _ -> },
    private val onExternalUrl: (String) -> Unit = {},
    private val onHttpAuthRequest: (
        tabId: String,
        handler: HttpAuthHandler,
        host: String,
        realm: String?
    ) -> Unit = { _, handler, _, _ -> handler.cancel() },
    private val isTrustedDevServer: (host: String) -> Boolean = { false },
    private val onSslErrorRequest: (
        tabId: String,
        handler: SslErrorHandler,
        error: SslError,
        host: String
    ) -> Unit = { _, handler, _, _ -> handler.cancel() },
    private val onDiagnosticEntry: (ConsoleEntry) -> Unit = {},
    private val desktopMode: Boolean = false,
    private val desktopViewportWidthDp: Int = 1280,
    private val pageZoomPercent: Int = 100
) : WebViewClient() {

    @Volatile
    private var mainFrameOrigin: String? = null

    private val devServerRequestProxy = DevServerRequestProxy(
        tabId = tabId,
        onDiagnosticEntry = onDiagnosticEntry
    )

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        mainFrameOrigin = DevServerProxyPolicy.originOf(url)
        onPageStarted(tabId, url)
        onNavigationStateChanged(tabId, view?.canGoBack() == true, view?.canGoForward() == true)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // Fallback for WebView providers without document-start script support.
        view?.evaluateJavascript(BrowserPageDiagnostics.documentStartScript, null)
        if (desktopMode) injectDesktopViewport(view)
        onPageFinished(tabId, url, view?.title)
        onNavigationStateChanged(tabId, view?.canGoBack() == true, view?.canGoForward() == true)
        schedulePageDiagnostics(view, url)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        return shouldOpenExternally(uri)
    }

    @Suppress("DEPRECATION")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        val uri = url?.let(Uri::parse) ?: return false
        return shouldOpenExternally(uri)
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame != true) return

        val rawDescription = error?.description?.toString()
        if (isHttpResponseCodeFailure(rawDescription)) {
            /*
             * Chromium uses net::ERR_HTTP_RESPONSE_CODE_FAILURE for some HTTP
             * status navigations and still dispatches the normal page lifecycle.
             * Converting it into our own full-screen overlay hides WebView's
             * HTTP error/diagnostic result, so leave rendering to WebView.
             */
            return
        }

        val errorCode = error?.errorCode
        val description = describeMainFrameError(errorCode, rawDescription)
        onPageError(tabId, request.url?.toString(), description, errorCode)
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        val status = errorResponse?.statusCode ?: return
        if (status < 400) return
        val url = request?.url?.toString().orEmpty()
        val reason = errorResponse.reasonPhrase?.takeIf { it.isNotBlank() }.orEmpty()
        val mainFrame = request?.isForMainFrame == true
        if (!mainFrame && status < 500 && !isPageCriticalResource(url)) return
        onDiagnosticEntry(
            ConsoleEntry(
                tabId = tabId,
                level = if (mainFrame || status >= 500) ConsoleLevel.ERROR else ConsoleLevel.WARNING,
                message = buildString {
                    append("[WindowWeb HTTP] ")
                    append(status)
                    if (reason.isNotBlank()) append(" $reason")
                    if (mainFrame) append(" (main document)")
                    append(" — ")
                    append(url)
                },
                source = url,
                lineNumber = null
            )
        )
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val activeRequest = request ?: return super.shouldInterceptRequest(view, null)
        onRequestSeen(tabId, activeRequest.url.toString(), activeRequest.method, inferResourceType(activeRequest.url))

        devServerRequestProxy.intercept(
            request = activeRequest,
            mainFrameOrigin = mainFrameOrigin
        )?.let { return it }

        return super.shouldInterceptRequest(view, activeRequest)
    }

    override fun onReceivedHttpAuthRequest(
        view: WebView?,
        handler: HttpAuthHandler?,
        host: String?,
        realm: String?
    ) {
        val activeHandler = handler ?: return
        val resolvedHost = host.orEmpty().ifBlank {
            runCatching { Uri.parse(view?.url.orEmpty()).host.orEmpty() }.getOrDefault("")
        }
        if (resolvedHost.isBlank()) {
            activeHandler.cancel()
            return
        }
        onHttpAuthRequest(tabId, activeHandler, resolvedHost, realm)
    }

    override fun onSafeBrowsingHit(
        view: WebView?,
        request: WebResourceRequest?,
        threatType: Int,
        callback: SafeBrowsingResponse?
    ) {
        // Production-safe default: go back to safety rather than rendering known-dangerous content.
        callback?.backToSafety(true)
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        val activeHandler = handler ?: return
        val activeError = error ?: run {
            activeHandler.cancel()
            return
        }
        val failingUrl = activeError.url ?: view?.url.orEmpty()
        val parsedEndpoint = runCatching {
            val uri = Uri.parse(failingUrl)
            val host = uri.host.orEmpty()
            val endpoint = if (host.isBlank()) "" else if (uri.port >= 0) "$host:${uri.port}" else host
            host to endpoint
        }.getOrDefault("" to "")
        val host = parsedEndpoint.first
        val endpoint = parsedEndpoint.second

        if (endpoint.isBlank()) {
            activeHandler.cancel()
            onPageError(
                tabId,
                failingUrl,
                "Secure connection failed and the server host could not be identified.",
                ERROR_FAILED_SSL_HANDSHAKE
            )
            return
        }

        if (!DevServerEndpointPolicy.isEligible(host, BuildConfig.DEBUG)) {
            activeHandler.cancel()
            onPageError(
                tabId,
                failingUrl,
                "The certificate is invalid. Certificate exceptions are only available for local/private development servers in release builds.",
                ERROR_FAILED_SSL_HANDSHAKE
            )
            return
        }

        if (isTrustedDevServer(endpoint)) {
            activeHandler.proceed()
            return
        }

        // The connection remains paused until the user explicitly cancels or proceeds.
        onSslErrorRequest(tabId, activeHandler, activeError, endpoint)
    }

    private fun isHttpResponseCodeFailure(description: String?): Boolean =
        description?.contains("ERR_HTTP_RESPONSE_CODE_FAILURE", ignoreCase = true) == true

    private fun shouldOpenExternally(uri: Uri): Boolean {
        return when (uri.scheme?.lowercase()) {
            "http", "https", "about", "data", "blob" -> false
            else -> {
                onExternalUrl(uri.toString())
                true
            }
        }
    }

    private fun describeMainFrameError(errorCode: Int?, fallback: String?): String = when (errorCode) {
        ERROR_FAILED_SSL_HANDSHAKE ->
            "ERR_SSL_PROTOCOL_ERROR: the secure TLS connection could not be negotiated. " +
                "The server may be using an unsupported protocol, presenting an invalid certificate, " +
                "or serving plain HTTP on an HTTPS address."

        ERROR_HOST_LOOKUP -> "The host name could not be resolved. Check the address and network connection."
        ERROR_CONNECT -> "The connection was refused or the server could not be reached."
        ERROR_TIMEOUT -> "The page took too long to respond."
        ERROR_REDIRECT_LOOP -> "The site redirected too many times."
        ERROR_UNSUPPORTED_SCHEME -> "This address uses a protocol that WindowWeb cannot load internally."
        ERROR_BAD_URL -> "The address is malformed."
        ERROR_FILE_NOT_FOUND -> "The requested page or file was not found."
        ERROR_AUTHENTICATION -> "The server could not authenticate the connection."
        ERROR_PROXY_AUTHENTICATION -> "The proxy server requires authentication."
        ERROR_IO -> "A network input/output error interrupted the page load."
        else -> fallback?.takeIf { it.isNotBlank() } ?: "The page could not be loaded."
    }

    private fun schedulePageDiagnostics(view: WebView?, sourceUrl: String?) {
        val webView = view ?: return
        listOf(350L, 1_500L).forEach { delay ->
            webView.postDelayed({
                val currentUrl = webView.url
                if (currentUrl.isNullOrBlank()) return@postDelayed
                if (!sourceUrl.isNullOrBlank() && currentUrl != sourceUrl) return@postDelayed
                webView.evaluateJavascript(BrowserPageDiagnostics.snapshotScript) { result ->
                    onDiagnosticEntry(
                        BrowserPageDiagnostics.consoleEntry(
                            tabId = tabId,
                            rawResult = result,
                            sourceUrl = sourceUrl ?: webView.url
                        )
                    )
                }
            }, delay)
        }
    }

    private fun injectDesktopViewport(view: WebView?) {
        val viewportWidth = desktopViewportWidthDp.coerceIn(980, 1920)
        val initialScale = pageZoomPercent.coerceIn(50, 300) / 100f
        val script = """
            (function() {
              var content = 'width=' + $viewportWidth + ', initial-scale=' + $initialScale + ', minimum-scale=0.25, maximum-scale=5.0, user-scalable=yes';
              var meta = document.querySelector('meta[name=viewport]');
              if (!meta) {
                meta = document.createElement('meta');
                meta.setAttribute('name', 'viewport');
                document.head.appendChild(meta);
              }
              meta.setAttribute('content', content);
              document.documentElement.style.minWidth = $viewportWidth + 'px';
              document.body.style.minWidth = $viewportWidth + 'px';
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private fun isPageCriticalResource(url: String): Boolean {
        val path = runCatching { Uri.parse(url).path.orEmpty().lowercase() }.getOrDefault("")
        return path.endsWith(".js") ||
            path.endsWith(".mjs") ||
            path.endsWith(".css") ||
            path.endsWith(".wasm") ||
            path.endsWith(".json") ||
            path.contains("/api/")
    }

    private fun inferResourceType(uri: Uri): String {
        val path = uri.path.orEmpty().lowercase()
        return when {
            path.endsWith(".js") -> "script"
            path.endsWith(".css") -> "stylesheet"
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".svg") -> "image"

            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") -> "font"
            path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mp3") || path.endsWith(".m4a") -> "media"
            else -> "document/request"
        }
    }
}
