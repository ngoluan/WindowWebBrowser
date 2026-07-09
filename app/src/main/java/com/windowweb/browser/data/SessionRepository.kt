package com.windowweb.browser.data

import com.windowweb.browser.core.BrowserChromeMode
import com.windowweb.browser.core.ConsoleEntry
import com.windowweb.browser.core.NetworkEntry
import com.windowweb.browser.core.PermissionDecision
import com.windowweb.browser.core.SnapPosition
import com.windowweb.browser.core.WindowMode
import java.net.URI
import com.windowweb.browser.util.Ids
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class SessionRepository(
    private val db: AppDatabase
) {
    private val workspaceDao = db.workspaceDao()
    private val windowDao = db.windowDao()
    private val tabDao = db.tabDao()
    private val historyDao = db.historyDao()
    private val consoleLogDao = db.consoleLogDao()
    private val networkLogDao = db.networkLogDao()
    private val sitePermissionDao = db.sitePermissionDao()
    private val downloadDao = db.downloadDao()
    private val settingsDao = db.browserSettingsDao()
    private val recentlyClosedTabDao = db.recentlyClosedTabDao()
    private val checkpointDao = db.workspaceCheckpointDao()
    private val webAppDao = db.webAppDao()

    suspend fun ensureSeedData() {
        val now = System.currentTimeMillis()
        if (settingsDao.get() == null) settingsDao.upsert(BrowserSettingsEntity(updatedAt = now))
        if (workspaceDao.count() > 0) return

        val workspaceId = Ids.newId("workspace")
        val windowId = Ids.newId("window")
        val tabId = Ids.newId("tab")

        workspaceDao.upsert(
            WorkspaceEntity(
                id = workspaceId,
                name = "Main Workspace",
                createdAt = now,
                updatedAt = now,
                active = true
            )
        )
        windowDao.upsert(
            WindowEntity(
                id = windowId,
                workspaceId = workspaceId,
                x = 0.04f,
                y = 0.04f,
                width = 0.92f,
                height = 0.88f,
                zIndex = 1,
                mode = WindowMode.MAXIMIZED.name,
                chromeMode = BrowserChromeMode.MINIMAL.name,
                activeTabId = tabId,
                minimized = false,
                createdAt = now,
                updatedAt = now
            )
        )
        tabDao.upsert(
            TabEntity(
                id = tabId,
                windowId = windowId,
                profileId = "default",
                url = "https://www.google.com",
                title = "Google",
                faviconUrl = null,
                loading = false,
                suspended = false,
                discarded = false,
                lastActiveAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    fun observeActiveWorkspace(): Flow<WorkspaceEntity?> = workspaceDao.observeActiveWorkspace()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeWindowsForActiveWorkspace(): Flow<List<WindowEntity>> =
        workspaceDao.observeActiveWorkspace().flatMapLatest { workspace ->
            if (workspace == null) flowOf(emptyList()) else windowDao.observeWindowsForWorkspace(workspace.id)
        }

    fun observeAllTabs(): Flow<List<TabEntity>> = tabDao.observeAll()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFocusedWindow(): Flow<WindowEntity?> =
        workspaceDao.observeActiveWorkspace().flatMapLatest { workspace ->
            if (workspace == null) flowOf(null) else windowDao.observeFocusedWindow(workspace.id)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeActiveTab(): Flow<TabEntity?> = observeFocusedWindow().flatMapLatest { window ->
        val tabId = window?.activeTabId
        if (tabId == null) flowOf(null) else tabDao.observeById(tabId)
    }

    fun observeTabsForWindow(windowId: String): Flow<List<TabEntity>> =
        tabDao.observeTabsForWindow(windowId)

    fun observeConsoleForTab(tabId: String): Flow<List<ConsoleLogEntity>> =
        consoleLogDao.observeForTab(tabId)

    fun observeNetworkForTab(tabId: String): Flow<List<NetworkLogEntity>> =
        networkLogDao.observeForTab(tabId)

    fun observePermissions(): Flow<List<SitePermissionEntity>> = sitePermissionDao.observeAll()

    fun observeDownloads(): Flow<List<DownloadEntity>> = downloadDao.observeRecent()

    fun observeSettings(): Flow<BrowserSettingsEntity?> = settingsDao.observe()

    suspend fun getSettings(): BrowserSettingsEntity = settingsDao.get() ?: BrowserSettingsEntity()

    fun observeRecentlyClosed(): Flow<List<RecentlyClosedTabEntity>> = recentlyClosedTabDao.observeRecent()

    fun observeCheckpoint(): Flow<WorkspaceCheckpointEntity?> = checkpointDao.observeLatest()

    fun observeWebApps(): Flow<List<WebAppEntity>> = webAppDao.observeAll()

    suspend fun createWindow(initialUrl: String = "https://www.google.com"): String {
        val workspace = workspaceDao.getActiveWorkspace() ?: return ""
        val now = System.currentTimeMillis()
        val windowId = Ids.newId("window")
        val tabId = Ids.newId("tab")
        val z = windowDao.maxZIndex(workspace.id) + 1
        val settings = getSettings()
        windowDao.upsert(
            WindowEntity(
                id = windowId,
                workspaceId = workspace.id,
                x = 0.08f + ((z % 4) * 0.04f),
                y = 0.08f + ((z % 4) * 0.04f),
                width = 0.84f,
                height = 0.74f,
                zIndex = z,
                mode = WindowMode.FLOATING.name,
                chromeMode = BrowserChromeMode.MINIMAL.name,
                desktopMode = settings.defaultDesktopMode,
                desktopViewportWidthDp = settings.desktopViewportWidthDp.coerceIn(980, 1920),
                activeTabId = tabId,
                minimized = false,
                createdAt = now,
                updatedAt = now
            )
        )
        tabDao.upsert(newTab(tabId, windowId, initialUrl, now))
        return windowId
    }

    suspend fun createTab(windowId: String, url: String = "https://www.google.com"): String {
        val now = System.currentTimeMillis()
        val tabId = Ids.newId("tab")
        tabDao.upsert(newTab(tabId, windowId, url, now))
        windowDao.setActiveTab(windowId, tabId, now)
        focusWindow(windowId)
        return tabId
    }

    suspend fun duplicateTab(tabId: String): String? {
        val source = tabDao.getById(tabId) ?: return null
        return createTab(source.windowId, source.url)
    }

    suspend fun closeTab(tabId: String) {
        val tab = tabDao.getById(tabId) ?: return
        val now = System.currentTimeMillis()
        recentlyClosedTabDao.insert(
            RecentlyClosedTabEntity(
                id = Ids.newId("closed"),
                url = tab.url,
                title = tab.title,
                closedAt = now
            )
        )
        tabDao.delete(tabId)
        val remaining = tabDao.getTabsForWindow(tab.windowId)
        if (remaining.isEmpty()) {
            closeWindow(tab.windowId)
        } else {
            windowDao.setActiveTab(tab.windowId, remaining.first().id, now)
        }
    }

    suspend fun restoreRecentlyClosed(item: RecentlyClosedTabEntity) {
        val window = workspaceDao.getActiveWorkspace()?.let { windowDao.getFocusedWindow(it.id) }
        if (window != null) createTab(window.id, item.url) else createWindow(item.url)
    }

    suspend fun activateTab(windowId: String, tabId: String) {
        val now = System.currentTimeMillis()
        tabDao.markActive(tabId, now)
        windowDao.setActiveTab(windowId, tabId, now)
        focusWindow(windowId)
    }

    suspend fun detachTabToNewWindow(tabId: String): String? {
        val tab = tabDao.getById(tabId) ?: return null
        val workspace = workspaceDao.getActiveWorkspace() ?: return null
        val now = System.currentTimeMillis()
        val windowId = Ids.newId("window")
        val z = windowDao.maxZIndex(workspace.id) + 1
        windowDao.upsert(
            WindowEntity(
                id = windowId,
                workspaceId = workspace.id,
                x = 0.12f,
                y = 0.12f,
                width = 0.76f,
                height = 0.72f,
                zIndex = z,
                mode = WindowMode.FLOATING.name,
                chromeMode = BrowserChromeMode.MINIMAL.name,
                activeTabId = tabId,
                minimized = false,
                createdAt = now,
                updatedAt = now
            )
        )
        tabDao.moveToWindow(tabId, windowId, now)
        val oldTabs = tabDao.getTabsForWindow(tab.windowId)
        if (oldTabs.isEmpty()) {
            windowDao.delete(tab.windowId)
        } else {
            windowDao.setActiveTab(tab.windowId, oldTabs.first().id, now)
        }
        return windowId
    }

    suspend fun focusWindow(windowId: String) {
        val window = windowDao.getById(windowId) ?: return
        val z = windowDao.maxZIndex(window.workspaceId) + 1
        windowDao.focus(windowId, z, System.currentTimeMillis())
    }

    suspend fun closeWindow(windowId: String) {
        tabDao.deleteForWindow(windowId)
        windowDao.delete(windowId)
    }

    suspend fun minimizeWindow(windowId: String) {
        val window = windowDao.getById(windowId) ?: return
        windowDao.updateBoundsAndMode(
            windowId,
            window.x,
            window.y,
            window.width,
            window.height,
            WindowMode.MINIMIZED.name,
            true,
            System.currentTimeMillis()
        )
    }

    suspend fun restoreWindow(windowId: String) {
        val window = windowDao.getById(windowId) ?: return
        val mode = if (window.mode == WindowMode.MINIMIZED.name) WindowMode.FLOATING.name else window.mode
        windowDao.updateBoundsAndMode(
            windowId,
            window.x,
            window.y,
            window.width,
            window.height,
            mode,
            false,
            System.currentTimeMillis()
        )
        focusWindow(windowId)
    }

    suspend fun snapWindow(windowId: String, snap: SnapPosition) {
        data class BoundsAndMode(
            val x: Float,
            val y: Float,
            val width: Float,
            val height: Float,
            val mode: String
        )

        val bounds = when (snap) {
            SnapPosition.LEFT -> BoundsAndMode(0f, 0f, 0.5f, 1f, WindowMode.SPLIT_LEFT.name)
            SnapPosition.RIGHT -> BoundsAndMode(0.5f, 0f, 0.5f, 1f, WindowMode.SPLIT_RIGHT.name)
            SnapPosition.TOP -> BoundsAndMode(0f, 0f, 1f, 0.5f, WindowMode.SPLIT_TOP.name)
            SnapPosition.BOTTOM -> BoundsAndMode(0f, 0.5f, 1f, 0.5f, WindowMode.SPLIT_BOTTOM.name)
            SnapPosition.PICTURE_IN_PICTURE -> BoundsAndMode(0.58f, 0.56f, 0.38f, 0.34f, WindowMode.PICTURE_IN_PICTURE.name)
            SnapPosition.MAXIMIZE -> BoundsAndMode(0f, 0f, 1f, 1f, WindowMode.MAXIMIZED.name)
            SnapPosition.FLOATING -> BoundsAndMode(0.08f, 0.08f, 0.84f, 0.76f, WindowMode.FLOATING.name)
        }
        windowDao.updateBoundsAndMode(
            windowId,
            bounds.x,
            bounds.y,
            bounds.width,
            bounds.height,
            bounds.mode,
            false,
            System.currentTimeMillis()
        )
        focusWindow(windowId)
    }

    suspend fun moveWindow(windowId: String, x: Float, y: Float) {
        val window = windowDao.getById(windowId) ?: return
        windowDao.updateBoundsAndMode(windowId, x, y, window.width, window.height, WindowMode.FLOATING.name, false, System.currentTimeMillis())
    }

    suspend fun resizeWindow(windowId: String, width: Float, height: Float) {
        val window = windowDao.getById(windowId) ?: return
        windowDao.updateBoundsAndMode(windowId, window.x, window.y, width, height, WindowMode.FLOATING.name, false, System.currentTimeMillis())
    }

    suspend fun setChromeMode(windowId: String, mode: BrowserChromeMode) {
        windowDao.setChromeMode(windowId, mode.name, System.currentTimeMillis())
    }

    suspend fun setDesktopMode(windowId: String, enabled: Boolean) {
        val settings = getSettings()
        windowDao.setDesktopMode(
            windowId = windowId,
            enabled = enabled,
            viewportWidthDp = settings.desktopViewportWidthDp.coerceIn(980, 1920),
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun setPageZoom(windowId: String, pageZoomPercent: Int) {
        windowDao.setPageZoom(
            windowId = windowId,
            pageZoomPercent = pageZoomPercent.coerceIn(50, 300),
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun setTextZoom(windowId: String, textZoomPercent: Int) {
        windowDao.setTextZoom(
            windowId = windowId,
            textZoomPercent = textZoomPercent.coerceIn(50, 300),
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun setDesktopViewportWidth(windowId: String, viewportWidthDp: Int) {
        windowDao.setDesktopViewportWidth(
            windowId = windowId,
            viewportWidthDp = viewportWidthDp.coerceIn(980, 1920),
            updatedAt = System.currentTimeMillis()
        )
    }

    suspend fun updateTabNavigation(tabId: String, url: String, title: String?, loading: Boolean) {
        val now = System.currentTimeMillis()
        tabDao.updateNavigation(tabId, url, title, loading, now)
        if (!loading && url.isNotBlank()) {
            historyDao.insert(
                HistoryEntity(
                    id = Ids.newId("history"),
                    tabId = tabId,
                    url = url,
                    title = title,
                    visitedAt = now
                )
            )
        }
    }

    suspend fun updateTabLoading(tabId: String, loading: Boolean) {
        tabDao.updateLoading(tabId, loading, System.currentTimeMillis())
    }

    suspend fun setTabDiscarded(tabId: String, discarded: Boolean, suspended: Boolean) {
        tabDao.updateLifecycle(tabId, discarded, suspended, System.currentTimeMillis())
    }

    suspend fun appendConsole(entry: ConsoleEntry) {
        consoleLogDao.insert(
            ConsoleLogEntity(
                id = entry.id,
                tabId = entry.tabId,
                level = entry.level.name,
                message = entry.message,
                source = entry.source,
                lineNumber = entry.lineNumber,
                timestamp = entry.timestamp
            )
        )
    }

    suspend fun clearConsole(tabId: String) {
        consoleLogDao.clearForTab(tabId)
    }

    suspend fun appendNetwork(entry: NetworkEntry) {
        networkLogDao.insert(
            NetworkLogEntity(
                id = entry.id,
                tabId = entry.tabId,
                url = entry.url,
                method = entry.method,
                resourceType = entry.resourceType,
                timestamp = entry.timestamp
            )
        )
    }

    suspend fun clearNetwork(tabId: String) {
        networkLogDao.clearForTab(tabId)
    }

    suspend fun getSitePermission(origin: String, resource: String): SitePermissionEntity? =
        sitePermissionDao.get(origin, resource)

    suspend fun setSitePermission(origin: String, resource: String, decision: PermissionDecision) {
        sitePermissionDao.upsert(
            SitePermissionEntity(
                id = "$origin|$resource",
                origin = origin,
                resource = resource,
                decision = decision.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordDownload(url: String, fileName: String, mimeType: String?, managerDownloadId: Long?) {
        downloadDao.upsert(
            DownloadEntity(
                id = Ids.newId("download"),
                url = url,
                fileName = fileName,
                mimeType = mimeType,
                status = "QUEUED",
                managerDownloadId = managerDownloadId,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateSettings(transform: (BrowserSettingsEntity) -> BrowserSettingsEntity) {
        val current = settingsDao.get() ?: BrowserSettingsEntity()
        settingsDao.upsert(transform(current).copy(updatedAt = System.currentTimeMillis()))
    }


    suspend fun checkpointWorkspace(cleanExit: Boolean) {
        val workspace = workspaceDao.getActiveWorkspace()
        val windows = workspace?.let { windowDao.getWindowsForWorkspace(it.id) }.orEmpty()
        val tabs = tabDao.getAllTabs()
        val focused = workspace?.let { windowDao.getFocusedWindow(it.id) }
        checkpointDao.upsert(
            WorkspaceCheckpointEntity(
                workspaceId = workspace?.id,
                windowCount = windows.size,
                tabCount = tabs.size,
                focusedWindowId = focused?.id,
                activeTabId = focused?.activeTabId,
                cleanExit = cleanExit,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markLaunchDirty() {
        val latest = checkpointDao.latest()
        val workspace = workspaceDao.getActiveWorkspace()
        val windows = workspace?.let { windowDao.getWindowsForWorkspace(it.id) }.orEmpty()
        val tabs = tabDao.getAllTabs()
        val focused = workspace?.let { windowDao.getFocusedWindow(it.id) }
        checkpointDao.upsert(
            WorkspaceCheckpointEntity(
                workspaceId = latest?.workspaceId ?: workspace?.id,
                windowCount = windows.size,
                tabCount = tabs.size,
                focusedWindowId = latest?.focusedWindowId ?: focused?.id,
                activeTabId = latest?.activeTabId ?: focused?.activeTabId,
                cleanExit = false,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun restoreWorkspaceFromCheckpoint() {
        // Layout and URLs already live in the persisted workspace/window/tab tables.
        // This method focuses the last known window and clears discarded flags for its active tab.
        val checkpoint = checkpointDao.latest() ?: return
        val focusedWindowId = checkpoint.focusedWindowId ?: return
        windowDao.getById(focusedWindowId)?.let { window ->
            window.activeTabId?.let { tabDao.updateLifecycle(it, discarded = false, suspended = false, updatedAt = System.currentTimeMillis()) }
            focusWindow(window.id)
        }
    }

    suspend fun startFreshWorkspace() {
        val workspace = workspaceDao.getActiveWorkspace() ?: return
        windowDao.getWindowsForWorkspace(workspace.id).forEach { window ->
            tabDao.deleteForWindow(window.id)
            windowDao.delete(window.id)
        }
        createWindow("https://www.google.com")
        checkpointWorkspace(cleanExit = true)
    }

    suspend fun markTabsForLifecycle(liveTabIds: Set<String>, visibleTabIds: Set<String>) {
        val now = System.currentTimeMillis()
        tabDao.getAllTabs().forEach { tab ->
            when {
                tab.id in liveTabIds -> tabDao.updateLifecycle(tab.id, discarded = false, suspended = false, updatedAt = now)
                tab.id in visibleTabIds -> tabDao.updateLifecycle(tab.id, discarded = false, suspended = true, updatedAt = now)
                else -> tabDao.updateLifecycle(tab.id, discarded = true, suspended = true, updatedAt = now)
            }
        }
    }

    suspend fun restoreTabLifecycle(tabId: String) {
        tabDao.updateLifecycle(tabId, discarded = false, suspended = false, updatedAt = System.currentTimeMillis())
    }

    suspend fun installCurrentSiteAsWebApp(tabId: String, nameOverride: String? = null): WebAppEntity? {
        val tab = tabDao.getById(tabId) ?: return null
        val origin = originFromUrl(tab.url) ?: return null
        val now = System.currentTimeMillis()
        val existing = webAppDao.getByOrigin(origin)
        val app = WebAppEntity(
            id = existing?.id ?: Ids.newId("webapp"),
            name = nameOverride?.takeIf { it.isNotBlank() } ?: tab.title?.takeIf { it.isNotBlank() } ?: origin.removePrefix("https://").removePrefix("http://"),
            url = tab.url,
            origin = origin,
            iconUrl = tab.faviconUrl,
            defaultChromeMode = BrowserChromeMode.FULLSCREEN_WEB_APP.name,
            defaultWindowMode = WindowMode.MAXIMIZED.name,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        webAppDao.upsert(app)
        return app
    }

    suspend fun launchWebApp(appId: String): String? {
        val app = webAppDao.getById(appId) ?: return null
        val workspace = workspaceDao.getActiveWorkspace() ?: return null
        val now = System.currentTimeMillis()
        val windowId = Ids.newId("window")
        val tabId = Ids.newId("tab")
        val z = windowDao.maxZIndex(workspace.id) + 1
        windowDao.upsert(
            WindowEntity(
                id = windowId,
                workspaceId = workspace.id,
                x = 0f,
                y = 0f,
                width = 1f,
                height = 1f,
                zIndex = z,
                mode = app.defaultWindowMode,
                chromeMode = app.defaultChromeMode,
                activeTabId = tabId,
                minimized = false,
                createdAt = now,
                updatedAt = now
            )
        )
        tabDao.upsert(newTab(tabId, windowId, app.url, now).copy(title = app.name))
        return windowId
    }

    suspend fun deleteWebApp(appId: String) {
        webAppDao.delete(appId)
    }

    private fun originFromUrl(url: String): String? = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme ?: return@runCatching null
        val host = uri.host ?: return@runCatching null
        val port = if (uri.port == -1) "" else ":${uri.port}"
        "$scheme://$host$port"
    }.getOrNull()

    private fun newTab(tabId: String, windowId: String, url: String, now: Long): TabEntity =
        TabEntity(
            id = tabId,
            windowId = windowId,
            profileId = "default",
            url = url,
            title = null,
            faviconUrl = null,
            loading = false,
            suspended = false,
            discarded = false,
            lastActiveAt = now,
            createdAt = now,
            updatedAt = now
        )
}
