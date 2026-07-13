package com.windowweb.browser.webview

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.windowweb.browser.core.ConsoleEntry
import com.windowweb.browser.core.ConsoleLevel
import com.windowweb.browser.util.DevServerProxyPolicy
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Native GET/SSE transport for the OpenCode endpoints that establish the
 * initial server connection. It is used only for same-origin requests selected
 * by [DevServerProxyPolicy].
 *
 * Android WebView can occasionally reject these requests with a bare
 * `TypeError: Failed to fetch` even though the document itself loaded. Routing
 * the health check and event stream through HttpURLConnection avoids the
 * renderer fetch/CORS/service-worker path while preserving the response as a
 * normal WebResourceResponse.
 */
class DevServerRequestProxy(
    private val tabId: String,
    private val onDiagnosticEntry: (ConsoleEntry) -> Unit
) {
    private val loggedSuccesses = ConcurrentHashMap.newKeySet<String>()

    fun intercept(
        request: WebResourceRequest,
        mainFrameOrigin: String?
    ): WebResourceResponse? {
        val url = request.url.toString()
        if (!DevServerProxyPolicy.shouldProxy(request.method, url, mainFrameOrigin)) return null

        return runCatching { execute(request, url) }
            .onFailure { error ->
                onDiagnosticEntry(
                    ConsoleEntry(
                        tabId = tabId,
                        level = ConsoleLevel.WARNING,
                        message = buildString {
                            append("[WindowWeb native transport] ")
                            append(request.method)
                            append(' ')
                            append(url)
                            append(" failed: ")
                            append(error.javaClass.simpleName)
                            error.message?.takeIf { it.isNotBlank() }?.let {
                                append(": ")
                                append(it)
                            }
                            append(". Chromium fetch will be tried next.")
                        },
                        source = url,
                        lineNumber = null
                    )
                )
            }
            .getOrNull()
    }

    private fun execute(request: WebResourceRequest, url: String): WebResourceResponse? {
        val encodedPath = request.url.encodedPath.orEmpty()
        val eventStream = encodedPath == "/global/event" || encodedPath == "/event"
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = request.method.uppercase(Locale.US)
            instanceFollowRedirects = true
            connectTimeout = 12_000
            readTimeout = if (eventStream) 0 else 15_000
            useCaches = false
            doInput = true

            request.requestHeaders.forEach { (name, value) ->
                if (name.isSafeForwardHeader() && value.isNotBlank()) {
                    setRequestProperty(name, value)
                }
            }

            // Avoid handing a compressed raw stream to WebResourceResponse.
            setRequestProperty("Accept-Encoding", "identity")
            if (getRequestProperty("Cache-Control").isNullOrBlank()) {
                setRequestProperty("Cache-Control", "no-cache")
            }
            if (eventStream) {
                setRequestProperty("Accept", "text/event-stream")
            }

            if (getRequestProperty("Cookie").isNullOrBlank()) {
                CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                    setRequestProperty("Cookie", it)
                }
            }
        }

        val status = connection.responseCode
        if (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_PROXY_AUTH) {
            // Let WebView's existing HTTP-auth flow handle credential challenges.
            connection.disconnect()
            return null
        }

        val reason = connection.responseMessage?.takeIf { it.isNotBlank() }
            ?: if (status in 200..299) "OK" else "HTTP $status"

        connection.headerFields.entries
            .firstOrNull { (name, _) -> name?.equals("Set-Cookie", ignoreCase = true) == true }
            ?.value
            ?.forEach { cookie -> CookieManager.getInstance().setCookie(url, cookie) }

        val contentType = connection.contentType.orEmpty()
        val mimeType = contentType.substringBefore(';').trim().ifBlank {
            if (eventStream) "text/event-stream" else "application/octet-stream"
        }
        val encoding = contentType
            .substringAfter("charset=", missingDelimiterValue = "")
            .substringBefore(';')
            .trim()
            .ifBlank { "UTF-8" }

        val headers = linkedMapOf<String, String>()
        connection.headerFields.forEach { (name, values) ->
            if (name != null && !values.isNullOrEmpty()) {
                headers[name] = values.joinToString(", ")
            }
        }
        // The response body is already identity encoded.
        headers.removeCaseInsensitive("Content-Encoding")
        headers.removeCaseInsensitive("Content-Length")
        headers.removeCaseInsensitive("Transfer-Encoding")
        headers.removeCaseInsensitive("Connection")
        headers.removeCaseInsensitive("Set-Cookie")

        val body: InputStream = when {
            request.method.equals("HEAD", ignoreCase = true) -> ByteArrayInputStream(ByteArray(0))
            status >= 400 -> connection.errorStream ?: ByteArrayInputStream(ByteArray(0))
            else -> connection.inputStream
        }

        val endpointKey = "${request.method.uppercase(Locale.US)}:$encodedPath"
        if (loggedSuccesses.add(endpointKey)) {
            onDiagnosticEntry(
                ConsoleEntry(
                    tabId = tabId,
                    level = ConsoleLevel.INFO,
                    message = "[WindowWeb native transport] ${request.method} $encodedPath → $status $reason",
                    source = url,
                    lineNumber = null
                )
            )
        }

        return WebResourceResponse(
            mimeType,
            encoding,
            status,
            reason,
            headers,
            DisconnectingInputStream(connection, body)
        )
    }

    private fun String.isSafeForwardHeader(): Boolean = !equals("Host", ignoreCase = true) &&
        !equals("Connection", ignoreCase = true) &&
        !equals("Content-Length", ignoreCase = true) &&
        !equals("Accept-Encoding", ignoreCase = true) &&
        !equals("If-None-Match", ignoreCase = true) &&
        !equals("If-Modified-Since", ignoreCase = true)

    private fun MutableMap<String, String>.removeCaseInsensitive(name: String) {
        keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let { remove(it) }
    }

    private class DisconnectingInputStream(
        private val connection: HttpURLConnection,
        delegate: InputStream
    ) : FilterInputStream(delegate) {
        override fun close() {
            try {
                super.close()
            } finally {
                connection.disconnect()
            }
        }
    }
}
