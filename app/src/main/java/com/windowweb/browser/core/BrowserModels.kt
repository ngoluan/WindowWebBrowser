package com.windowweb.browser.core

import java.util.UUID

enum class WindowMode {
    FLOATING,
    MAXIMIZED,
    MINIMIZED,
    SPLIT_LEFT,
    SPLIT_RIGHT,
    SPLIT_TOP,
    SPLIT_BOTTOM,
    PICTURE_IN_PICTURE,
    FULLSCREEN
}

enum class BrowserChromeMode {
    MINIMAL,
    AUTO_HIDE,
    STANDARD,
    FULLSCREEN_WEB_APP
}

enum class TabLifecycleState {
    ACTIVE,
    VISIBLE,
    BACKGROUND,
    FROZEN,
    DISCARDED
}

enum class ConsoleLevel {
    LOG,
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

enum class SnapPosition {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    PICTURE_IN_PICTURE,
    MAXIMIZE,
    FLOATING
}

enum class PermissionDecision {
    ASK,
    ALLOW,
    BLOCK
}

data class ConsoleEntry(
    val id: String = UUID.randomUUID().toString(),
    val tabId: String,
    val level: ConsoleLevel,
    val message: String,
    val source: String?,
    val lineNumber: Int?,
    val timestamp: Long = System.currentTimeMillis()
)

data class BrowserNavigationState(
    val url: String = "",
    val title: String? = null,
    val progress: Int = 0,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val errorDescription: String? = null
)

data class NetworkEntry(
    val id: String = UUID.randomUUID().toString(),
    val tabId: String,
    val url: String,
    val method: String,
    val resourceType: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PermissionPrompt(
    val origin: String,
    val resources: List<String>,
    val requestId: String = UUID.randomUUID().toString()
)
