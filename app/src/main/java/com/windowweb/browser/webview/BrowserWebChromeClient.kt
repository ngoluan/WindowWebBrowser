package com.windowweb.browser.webview

import android.net.Uri
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.windowweb.browser.core.ConsoleEntry
import com.windowweb.browser.core.ConsoleLevel

class BrowserWebChromeClient(
    private val tabId: String,
    private val onProgressChanged: (tabId: String, progress: Int) -> Unit,
    private val onConsoleEntry: (ConsoleEntry) -> Unit,
    private val onFileChooser: (ValueCallback<Array<Uri>>, FileChooserParams) -> Boolean = { _, _ -> false },
    private val onPermissionRequest: (tabId: String, request: PermissionRequest) -> Unit = { _, request -> request.deny() },
    private val onCreateWindowRequest: (tabId: String, url: String?) -> Unit = { _, _ -> }
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(tabId, newProgress)
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean = onFileChooser(filePathCallback, fileChooserParams)

    override fun onPermissionRequest(request: PermissionRequest) {
        onPermissionRequest(tabId, request)
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        val parent = view ?: return false
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        val popup = WebView(parent.context)
        popup.settings.javaScriptEnabled = true
        popup.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(popupView: WebView?, request: WebResourceRequest?): Boolean {
                onCreateWindowRequest(tabId, request?.url?.toString())
                popup.destroy()
                return true
            }

            override fun onPageStarted(popupView: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (!url.isNullOrBlank()) {
                    onCreateWindowRequest(tabId, url)
                    popup.stopLoading()
                    popup.destroy()
                }
            }
        }
        transport.webView = popup
        resultMsg.sendToTarget()
        return true
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        val level = consoleMessage.messageLevel().toConsoleLevel()
        val rendered = "${consoleMessage.message()} — ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
        when (level) {
            ConsoleLevel.ERROR -> Log.e("WindowWebConsole", rendered)
            ConsoleLevel.WARNING -> Log.w("WindowWebConsole", rendered)
            ConsoleLevel.INFO -> Log.i("WindowWebConsole", rendered)
            ConsoleLevel.DEBUG, ConsoleLevel.LOG -> Log.d("WindowWebConsole", rendered)
        }
        onConsoleEntry(
            ConsoleEntry(
                tabId = tabId,
                level = level,
                message = consoleMessage.message(),
                source = consoleMessage.sourceId(),
                lineNumber = consoleMessage.lineNumber()
            )
        )
        return true
    }
}

private fun ConsoleMessage.MessageLevel.toConsoleLevel(): ConsoleLevel = when (this) {
    ConsoleMessage.MessageLevel.DEBUG -> ConsoleLevel.DEBUG
    ConsoleMessage.MessageLevel.ERROR -> ConsoleLevel.ERROR
    ConsoleMessage.MessageLevel.LOG -> ConsoleLevel.LOG
    ConsoleMessage.MessageLevel.TIP -> ConsoleLevel.INFO
    ConsoleMessage.MessageLevel.WARNING -> ConsoleLevel.WARNING
}
