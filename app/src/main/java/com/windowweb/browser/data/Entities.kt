package com.windowweb.browser.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.windowweb.browser.core.BrowserChromeMode
import com.windowweb.browser.core.WindowMode

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val active: Boolean
)

@Entity(
    tableName = "windows",
    indices = [Index("workspaceId")]
)
data class WindowEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val zIndex: Int,
    val mode: String = WindowMode.MAXIMIZED.name,
    val chromeMode: String = BrowserChromeMode.STANDARD.name,
    val desktopMode: Boolean = false,
    val pageZoomPercent: Int = 100,
    val textZoomPercent: Int = 100,
    val desktopViewportWidthDp: Int = 1280,
    val activeTabId: String?,
    val minimized: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "tabs",
    indices = [Index("windowId"), Index("profileId")]
)
data class TabEntity(
    @PrimaryKey val id: String,
    val windowId: String,
    val profileId: String,
    val url: String,
    val title: String?,
    val faviconUrl: String?,
    val loading: Boolean,
    val suspended: Boolean,
    val discarded: Boolean,
    val lastActiveAt: Long,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "history",
    indices = [Index("url"), Index("visitedAt")]
)
data class HistoryEntity(
    @PrimaryKey val id: String,
    val tabId: String,
    val url: String,
    val title: String?,
    val visitedAt: Long
)

@Entity(
    tableName = "console_logs",
    indices = [Index("tabId"), Index("timestamp")]
)
data class ConsoleLogEntity(
    @PrimaryKey val id: String,
    val tabId: String,
    val level: String,
    val message: String,
    val source: String?,
    val lineNumber: Int?,
    val timestamp: Long
)

@Entity(
    tableName = "network_logs",
    indices = [Index("tabId"), Index("timestamp"), Index("url")]
)
data class NetworkLogEntity(
    @PrimaryKey val id: String,
    val tabId: String,
    val url: String,
    val method: String,
    val resourceType: String,
    val timestamp: Long
)

@Entity(
    tableName = "site_permissions",
    indices = [Index(value = ["origin", "resource"], unique = true)]
)
data class SitePermissionEntity(
    @PrimaryKey val id: String,
    val origin: String,
    val resource: String,
    val decision: String,
    val updatedAt: Long
)

@Entity(
    tableName = "downloads",
    indices = [Index("createdAt"), Index("url")]
)
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val status: String,
    val managerDownloadId: Long?,
    val createdAt: Long
)

@Entity(tableName = "browser_settings")
data class BrowserSettingsEntity(
    @PrimaryKey val id: String = "default",
    val androidAutofillEnabled: Boolean = true,
    val credentialSuggestionsEnabled: Boolean = true,
    val passkeysEnabled: Boolean = true,
    val openUnsupportedSecureSignInsExternally: Boolean = true,
    val developerModeEnabled: Boolean = true,
    val captureConsoleEnabled: Boolean = true,
    val captureNetworkEnabled: Boolean = true,
    val remoteDebuggingEnabled: Boolean = false,
    val restoreWorkspaceOnLaunch: Boolean = true,
    val askBeforeRestoringAfterCrash: Boolean = true,
    val maxLiveWebViews: Int = 4,
    val freezeMinimizedTabs: Boolean = true,
    val discardBackgroundTabs: Boolean = true,
    val showLauncherButton: Boolean = true,
    val defaultDesktopMode: Boolean = false,
    val enablePinchZoom: Boolean = true,
    val safeBrowsingEnabled: Boolean = true,
    val blockMixedContent: Boolean = true,
    val thirdPartyCookiesEnabled: Boolean = true,
    val externalUrlConfirmationEnabled: Boolean = true,
    val desktopViewportWidthDp: Int = 1280,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recently_closed_tabs",
    indices = [Index("closedAt")]
)
data class RecentlyClosedTabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String?,
    val closedAt: Long
)

@Entity(
    tableName = "workspace_checkpoints",
    indices = [Index("createdAt")]
)
data class WorkspaceCheckpointEntity(
    @PrimaryKey val id: String = "latest",
    val workspaceId: String?,
    val windowCount: Int,
    val tabCount: Int,
    val focusedWindowId: String?,
    val activeTabId: String?,
    val cleanExit: Boolean,
    val createdAt: Long
)

@Entity(
    tableName = "web_apps",
    indices = [Index(value = ["origin"], unique = true), Index("createdAt")]
)
data class WebAppEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val origin: String,
    val iconUrl: String?,
    val defaultChromeMode: String,
    val defaultWindowMode: String,
    val createdAt: Long,
    val updatedAt: Long
)
