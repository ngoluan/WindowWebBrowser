package com.windowweb.browser.util

import java.net.URLEncoder

object UrlInputParser {
    fun parse(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"

        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed

            trimmed.contains('.') && !trimmed.contains(' ') -> "https://$trimmed"

            else -> "https://www.google.com/search?q=" +
                URLEncoder.encode(trimmed, Charsets.UTF_8.name())
        }
    }
}
