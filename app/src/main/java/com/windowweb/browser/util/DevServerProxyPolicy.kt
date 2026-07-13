package com.windowweb.browser.util

import java.net.URI
import java.util.Locale

/**
 * Selects the small set of same-origin OpenCode bootstrap/stream requests that
 * may use WindowWeb's native transport when Chromium fetch is unreliable.
 *
 * The policy deliberately excludes arbitrary cross-origin requests and all
 * request methods with bodies because WebResourceRequest does not expose a
 * request body to WebViewClient.
 */
object DevServerProxyPolicy {
    private val proxiedPaths = setOf(
        "/global/health",
        "/global/event",
        "/event"
    )

    fun shouldProxy(method: String?, requestUrl: String?, mainFrameOrigin: String?): Boolean {
        val normalizedMethod = method.orEmpty().uppercase(Locale.US)
        if (normalizedMethod != "GET" && normalizedMethod != "HEAD") return false

        val request = parseHttpUri(requestUrl) ?: return false
        // The fallback targets cleartext development servers. HTTPS continues
        // through WebView so its certificate exception flow remains in control.
        if (!request.scheme.equals("http", ignoreCase = true)) return false
        val pageOrigin = normalizeOrigin(mainFrameOrigin) ?: return false
        if (originOf(request) != pageOrigin) return false

        val path = request.rawPath?.ifBlank { "/" } ?: "/"
        return path in proxiedPaths
    }

    fun originOf(url: String?): String? = parseHttpUri(url)?.let(::originOf)

    private fun originOf(uri: URI): String {
        val scheme = uri.scheme.lowercase(Locale.US)
        val host = uri.host.lowercase(Locale.US)
        val defaultPort = if (scheme == "https") 443 else 80
        val port = if (uri.port == -1) defaultPort else uri.port
        return "$scheme://$host:$port"
    }

    private fun normalizeOrigin(value: String?): String? {
        val uri = parseHttpUri(value) ?: return null
        return originOf(uri)
    }

    private fun parseHttpUri(value: String?): URI? = runCatching {
        val uri = URI(value?.trim().orEmpty())
        val scheme = uri.scheme?.lowercase(Locale.US)
        if ((scheme != "http" && scheme != "https") || uri.host.isNullOrBlank()) return@runCatching null
        uri
    }.getOrNull()
}
