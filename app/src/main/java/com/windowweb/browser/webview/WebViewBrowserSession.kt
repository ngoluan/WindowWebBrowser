package com.windowweb.browser.webview

import android.webkit.WebView
import com.windowweb.browser.core.BrowserSession
import com.windowweb.browser.util.UrlInputParser

class WebViewBrowserSession(
    override val tabId: String,
    private val webView: WebView
) : BrowserSession {
    override fun loadUrl(input: String) {
        webView.loadUrl(UrlInputParser.parse(input))
    }

    override fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    override fun goForward() {
        if (webView.canGoForward()) webView.goForward()
    }

    override fun reload() {
        webView.reload()
    }

    override fun stop() {
        webView.stopLoading()
    }

    override fun evaluateJavaScript(script: String, callback: (String?) -> Unit) {
        webView.evaluateJavascript(script, callback)
    }

    override fun zoomBy(factor: Float) {
        webView.zoomBy(factor.coerceIn(0.25f, 5.0f))
    }

    override fun setTextZoom(percent: Int) {
        webView.settings.textZoom = percent.coerceIn(50, 300)
    }

    override fun setDesktopMode(enabled: Boolean) {
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = enabled
        webView.settings.userAgentString = if (enabled) {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 WindowWeb/0.4"
        } else {
            android.webkit.WebSettings.getDefaultUserAgent(webView.context) + " WindowWeb/0.4"
        }
    }

    override fun canGoBack(): Boolean = webView.canGoBack()

    override fun canGoForward(): Boolean = webView.canGoForward()

    override fun currentUrl(): String? = webView.url
}
