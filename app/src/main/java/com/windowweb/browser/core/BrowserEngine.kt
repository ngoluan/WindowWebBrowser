package com.windowweb.browser.core

interface BrowserEngine {
    fun createSession(profileId: String, tabId: String): BrowserSession
    fun destroySession(tabId: String)
    fun suspendSession(tabId: String)
    fun restoreSession(tabId: String)
}

interface BrowserSession {
    val tabId: String

    fun loadUrl(input: String)
    fun goBack()
    fun goForward()
    fun reload()
    fun stop()
    fun evaluateJavaScript(script: String, callback: (String?) -> Unit = {})
    fun zoomBy(factor: Float)
    fun setTextZoom(percent: Int)
    fun setDesktopMode(enabled: Boolean)
    fun canGoBack(): Boolean
    fun canGoForward(): Boolean
    fun currentUrl(): String?
}
