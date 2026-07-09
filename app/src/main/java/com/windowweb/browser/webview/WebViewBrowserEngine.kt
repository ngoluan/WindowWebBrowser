package com.windowweb.browser.webview

import android.webkit.WebView
import com.windowweb.browser.core.BrowserEngine
import com.windowweb.browser.core.BrowserSession

/**
 * Phase 1 engine adapter. In phases 4+, this can become a session pool that owns
 * WebView lifecycle, freezing, discarding and restore policies.
 */
class WebViewBrowserEngine(
    private val webViewProvider: (tabId: String) -> WebView?
) : BrowserEngine {
    private val sessions = mutableMapOf<String, BrowserSession>()

    override fun createSession(profileId: String, tabId: String): BrowserSession {
        sessions[tabId]?.let { return it }
        val webView = requireNotNull(webViewProvider(tabId)) {
            "WebView is not attached for tab $tabId"
        }
        return WebViewBrowserSession(tabId, webView).also { sessions[tabId] = it }
    }

    override fun destroySession(tabId: String) {
        sessions.remove(tabId)
    }

    override fun suspendSession(tabId: String) {
        webViewProvider(tabId)?.onPause()
    }

    override fun restoreSession(tabId: String) {
        webViewProvider(tabId)?.onResume()
    }
}
