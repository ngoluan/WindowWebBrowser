package com.windowweb.browser.webview

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class BrowserWebViewClient(
    private val tabId: String,
    private val onPageStarted: (tabId: String, url: String?) -> Unit,
    private val onPageFinished: (tabId: String, url: String?, title: String?) -> Unit,
    private val onNavigationStateChanged: (tabId: String, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    private val onRequestSeen: (tabId: String, url: String, method: String, resourceType: String) -> Unit = { _, _, _, _ -> },
    private val onPageError: (tabId: String, url: String?, description: String?) -> Unit = { _, _, _ -> },
    private val onExternalUrl: (String) -> Unit = {},
    private val desktopMode: Boolean = false,
    private val desktopViewportWidthDp: Int = 1280,
    private val pageZoomPercent: Int = 100
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(tabId, url)
        onNavigationStateChanged(tabId, view?.canGoBack() == true, view?.canGoForward() == true)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (desktopMode) injectDesktopViewport(view)
        onPageFinished(tabId, url, view?.title)
        onNavigationStateChanged(tabId, view?.canGoBack() == true, view?.canGoForward() == true)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (url.startsWith("http://") || url.startsWith("https://")) return false
        onExternalUrl(url)
        return true
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            onPageError(tabId, request.url?.toString(), error?.description?.toString())
        }
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        request?.let {
            onRequestSeen(tabId, it.url.toString(), it.method, inferResourceType(it.url))
        }
        return super.shouldInterceptRequest(view, request)
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

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        // Production-safe default: never bypass TLS failures automatically.
        handler?.cancel()
    }

    private fun injectDesktopViewport(view: WebView?) {
        val viewportWidth = desktopViewportWidthDp.coerceIn(980, 1920)
        val initialScale = (pageZoomPercent.coerceIn(50, 300) / 100f)
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

    private fun inferResourceType(uri: Uri): String {
        val path = uri.path.orEmpty().lowercase()
        return when {
            path.endsWith(".js") -> "script"
            path.endsWith(".css") -> "stylesheet"
            path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".svg") -> "image"
            path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") -> "font"
            path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".mp3") || path.endsWith(".m4a") -> "media"
            else -> "document/request"
        }
    }
}
