package com.windowweb.browser.util

import android.content.Context
import android.webkit.WebSettings

/**
 * Builds user-agent strings from the installed Android System WebView version.
 * Keeping the actual Chrome/WebView version avoids advertising an obsolete TLS/browser stack.
 */
object BrowserUserAgent {
    private const val APP_TOKEN = "WindowWeb/0.5"

    fun mobile(context: Context): String =
        appendAppToken(WebSettings.getDefaultUserAgent(context))

    fun desktop(context: Context): String {
        val current = WebSettings.getDefaultUserAgent(context)
        val desktop = current
            .replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)")
            .replace("; wv", "")
            .replace(" Version/4.0", "")
            .replace(" Mobile ", " ")
        return appendAppToken(desktop)
    }

    private fun appendAppToken(userAgent: String): String {
        val withoutOldToken = userAgent.replace(Regex("\\s+WindowWeb/[^\\s]+"), "")
        return "$withoutOldToken $APP_TOKEN"
    }
}
