package com.windowweb.browser.util

import java.net.URI
import java.net.URLEncoder

object UrlInputParser {
    private val supportedSchemes = setOf("http", "https", "about", "data", "file")
    private val explicitScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val malformedHttpScheme = Regex("^(https?)(?:(?::\\s*/{0,2})|(?:\\s*/{1,2}))", RegexOption.IGNORE_CASE)
    private val likelyHost = Regex(
        "^(localhost|\\[[0-9a-fA-F:]+]|(?:\\d{1,3}\\.){3}\\d{1,3}|[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)+)(?::\\d+)?(?:/.*)?$"
    )

    fun parse(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"

        normalizeMalformedHttpScheme(trimmed)?.let { return it }

        if (trimmed.startsWith("//")) {
            return "https:${trimmed}"
        }

        if (explicitScheme.containsMatchIn(trimmed)) {
            val scheme = trimmed.substringBefore(':').lowercase()
            return if (scheme in supportedSchemes) trimmed else search(trimmed)
        }

        if (!trimmed.contains(' ') && likelyHost.matches(trimmed)) {
            return "https://$trimmed"
        }

        return search(trimmed)
    }

    /**
     * Returns an explicit HTTP alternative for a failed HTTPS URL.
     * This is never loaded automatically; the UI requires a user action to avoid silent downgrades.
     */
    fun httpFallback(url: String): String? = runCatching {
        val uri = URI(url)
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
            return@runCatching null
        }
        URI(
            "http",
            uri.userInfo,
            uri.host,
            uri.port,
            uri.path,
            uri.query,
            uri.fragment
        ).toASCIIString()
    }.getOrNull()

    private fun normalizeMalformedHttpScheme(value: String): String? {
        val match = malformedHttpScheme.find(value) ?: return null
        if (match.range.first != 0) return null

        val scheme = match.groupValues[1].lowercase()
        val remainder = value.substring(match.range.last + 1).trimStart('/', ' ')
        if (remainder.isBlank()) return null
        return "$scheme://$remainder"
    }

    private fun search(query: String): String =
        "https://www.google.com/search?q=" + URLEncoder.encode(query, Charsets.UTF_8.name())
}
