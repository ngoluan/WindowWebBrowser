package com.windowweb.browser.ui

import android.webkit.PermissionRequest
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.windowweb.browser.BuildConfig
import com.windowweb.browser.core.BrowserChromeMode
import com.windowweb.browser.core.BrowserNavigationState
import com.windowweb.browser.core.BrowserSession
import com.windowweb.browser.core.ConsoleEntry
import com.windowweb.browser.core.ConsoleLevel
import com.windowweb.browser.core.NetworkEntry
import com.windowweb.browser.core.PermissionDecision
import com.windowweb.browser.core.PermissionPrompt
import com.windowweb.browser.core.SnapPosition
import com.windowweb.browser.data.BrowserSettingsEntity
import com.windowweb.browser.data.ConsoleLogEntity
import com.windowweb.browser.data.DownloadEntity
import com.windowweb.browser.data.NetworkLogEntity
import com.windowweb.browser.data.RecentlyClosedTabEntity
import com.windowweb.browser.data.SessionRepository
import com.windowweb.browser.data.SitePermissionEntity
import com.windowweb.browser.data.TabEntity
import com.windowweb.browser.data.WebAppEntity
import com.windowweb.browser.data.WindowEntity
import com.windowweb.browser.data.WorkspaceCheckpointEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class RuntimeState(
    val navigationByTab: Map<String, BrowserNavigationState> = emptyMap(),
    val addressDraftByWindow: Map<String, String> = emptyMap(),
    val panel: BrowserPanel = BrowserPanel.NONE,
    val overviewVisible: Boolean = false,
    val launcherVisible: Boolean = false,
    val commandSheetWindowId: String? = null,
    val commandSheetExpanded: Boolean = false,
    val permissionPrompt: PermissionPrompt? = null,
    val externalUrlPrompt: String? = null,
    val consoleFilter: String = "",
    val jsInput: String = "",
    val hiddenChromeWindows: Set<String> = emptySet(),
    val liveTabIds: Set<String> = emptySet(),
    val recoveryBannerDismissed: Boolean = false,
    val memoryPressure: Boolean = false,
)

enum class BrowserPanel {
    NONE,
    CONSOLE,
    NETWORK,
    PERMISSIONS,
    DOWNLOADS,
    SETTINGS,
    RECENTLY_CLOSED,
    WEB_APPS
}

data class BrowserUiState(
    val windows: List<WindowEntity> = emptyList(),
    val tabs: List<TabEntity> = emptyList(),
    val focusedWindow: WindowEntity? = null,
    val activeTab: TabEntity? = null,
    val navigationByTab: Map<String, BrowserNavigationState> = emptyMap(),
    val addressDraftByWindow: Map<String, String> = emptyMap(),
    val panel: BrowserPanel = BrowserPanel.NONE,
    val overviewVisible: Boolean = false,
    val launcherVisible: Boolean = false,
    val commandSheetWindowId: String? = null,
    val commandSheetExpanded: Boolean = false,
    val permissionPrompt: PermissionPrompt? = null,
    val externalUrlPrompt: String? = null,
    val consoleEntries: List<ConsoleLogEntity> = emptyList(),
    val networkEntries: List<NetworkLogEntity> = emptyList(),
    val permissions: List<SitePermissionEntity> = emptyList(),
    val downloads: List<DownloadEntity> = emptyList(),
    val settings: BrowserSettingsEntity = BrowserSettingsEntity(),
    val recentlyClosed: List<RecentlyClosedTabEntity> = emptyList(),
    val checkpoint: WorkspaceCheckpointEntity? = null,
    val webApps: List<WebAppEntity> = emptyList(),
    val consoleFilter: String = "",
    val jsInput: String = "",
    val hiddenChromeWindows: Set<String> = emptySet(),
    val liveTabIds: Set<String> = emptySet(),
    val recoveryBannerDismissed: Boolean = false,
    val memoryPressure: Boolean = false
) {
    val suspendedTabCount: Int get() = tabs.count { it.suspended && !it.discarded }
    val discardedTabCount: Int get() = tabs.count { it.discarded }
}

class BrowserViewModel(
    private val repository: SessionRepository
) : ViewModel() {

    private val runtime = MutableStateFlow(RuntimeState())
    private val sessions = mutableMapOf<String, BrowserSession>()
    private val pendingLoads = mutableMapOf<String, String>()
    private val pendingPermissionRequests = mutableMapOf<String, PermissionRequest>()

    private val activeTabFlow = repository.observeActiveTab()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val consoleFlow = activeTabFlow.flatMapLatest { tab ->
        if (tab == null) flowOf(emptyList()) else repository.observeConsoleForTab(tab.id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val networkFlow = activeTabFlow.flatMapLatest { tab ->
        if (tab == null) flowOf(emptyList()) else repository.observeNetworkForTab(tab.id)
    }

    val state: StateFlow<BrowserUiState> = combine(
        repository.observeWindowsForActiveWorkspace(),
        repository.observeAllTabs(),
        activeTabFlow,
        consoleFlow,
        networkFlow,
        repository.observePermissions(),
        repository.observeDownloads(),
        repository.observeSettings(),
        repository.observeRecentlyClosed(),
        repository.observeCheckpoint(),
        repository.observeWebApps(),
        runtime
    ) { values ->
        val windows = values[0] as List<WindowEntity>
        val tabs = values[1] as List<TabEntity>
        val activeTab = values[2] as TabEntity?
        val consoleEntries = values[3] as List<ConsoleLogEntity>
        val networkEntries = values[4] as List<NetworkLogEntity>
        val permissions = values[5] as List<SitePermissionEntity>
        val downloads = values[6] as List<DownloadEntity>
        val settings = (values[7] as BrowserSettingsEntity?) ?: BrowserSettingsEntity()
        val recentlyClosed = values[8] as List<RecentlyClosedTabEntity>
        val checkpoint = values[9] as WorkspaceCheckpointEntity?
        val webApps = values[10] as List<WebAppEntity>
        val runtimeState = values[11] as RuntimeState
        val focused = windows.filterNot { it.minimized }.maxByOrNull { it.zIndex }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG || settings.remoteDebuggingEnabled)

        BrowserUiState(
            windows = windows,
            tabs = tabs,
            focusedWindow = focused,
            activeTab = activeTab,
            navigationByTab = runtimeState.navigationByTab,
            addressDraftByWindow = runtimeState.addressDraftByWindow,
            panel = runtimeState.panel,
            overviewVisible = runtimeState.overviewVisible,
            launcherVisible = runtimeState.launcherVisible,
            commandSheetWindowId = runtimeState.commandSheetWindowId,
            commandSheetExpanded = runtimeState.commandSheetExpanded,
            permissionPrompt = runtimeState.permissionPrompt,
            externalUrlPrompt = runtimeState.externalUrlPrompt,
            consoleEntries = consoleEntries,
            networkEntries = networkEntries,
            permissions = permissions,
            downloads = downloads,
            settings = settings,
            recentlyClosed = recentlyClosed,
            checkpoint = checkpoint,
            webApps = webApps,
            consoleFilter = runtimeState.consoleFilter,
            jsInput = runtimeState.jsInput,
            hiddenChromeWindows = runtimeState.hiddenChromeWindows,
            liveTabIds = runtimeState.liveTabIds,
            recoveryBannerDismissed = runtimeState.recoveryBannerDismissed,
            memoryPressure = runtimeState.memoryPressure
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BrowserUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureSeedData()
            if (repository.getSettings().restoreWorkspaceOnLaunch) {
                repository.restoreWorkspaceFromCheckpoint()
            }
            repository.markLaunchDirty()
        }
    }

    fun attachSession(newSession: BrowserSession) {
        sessions[newSession.tabId] = newSession
        runtime.update { current ->
            val existing = current.navigationByTab[newSession.tabId] ?: BrowserNavigationState()
            current.copy(
                navigationByTab = current.navigationByTab + (
                    newSession.tabId to existing.copy(
                        canGoBack = newSession.canGoBack(),
                        canGoForward = newSession.canGoForward(),
                        url = newSession.currentUrl().orEmpty().ifBlank { existing.url }
                    )
                )
            )
        }
        pendingLoads.remove(newSession.tabId)?.let { newSession.loadUrl(it) }
    }

    fun detachSession(tabId: String) {
        sessions.remove(tabId)
    }

    fun updateAddressText(windowId: String, text: String) {
        runtime.update { it.copy(addressDraftByWindow = it.addressDraftByWindow + (windowId to text)) }
    }

    fun loadAddress(windowId: String) {
        val window = state.value.windows.firstOrNull { it.id == windowId } ?: return
        val tabId = window.activeTabId ?: return
        val input = state.value.addressDraftByWindow[windowId]
            ?: state.value.navigationByTab[tabId]?.url
            ?: state.value.tabs.firstOrNull { it.id == tabId }?.url
            ?: return
        restoreDiscardedTab(tabId)
        loadInTab(tabId, input)
    }

    fun loadIncomingUrl(url: String) {
        val activeWindow = state.value.focusedWindow
        if (activeWindow == null) {
            viewModelScope.launch { repository.createWindow(url) }
        } else {
            updateAddressText(activeWindow.id, url)
            loadAddress(activeWindow.id)
        }
    }

    private fun loadInTab(tabId: String, input: String) {
        sessions[tabId]?.loadUrl(input) ?: run { pendingLoads[tabId] = input }
    }

    fun goBack(windowId: String) = activeTabId(windowId)?.let { sessions[it]?.goBack() } ?: Unit
    fun goForward(windowId: String) = activeTabId(windowId)?.let { sessions[it]?.goForward() } ?: Unit
    fun reloadOrStop(windowId: String) {
        val tabId = activeTabId(windowId) ?: return
        val loading = state.value.navigationByTab[tabId]?.loading == true
        if (loading) sessions[tabId]?.stop() else sessions[tabId]?.reload()
    }

    fun createTab(windowId: String) {
        viewModelScope.launch { repository.createTab(windowId) }
    }

    fun activateTab(windowId: String, tabId: String) {
        restoreDiscardedTab(tabId)
        viewModelScope.launch { repository.activateTab(windowId, tabId) }
    }

    fun closeTab(tabId: String) {
        sessions.remove(tabId)
        viewModelScope.launch { repository.closeTab(tabId) }
    }

    fun duplicateTab(tabId: String) {
        viewModelScope.launch { repository.duplicateTab(tabId) }
    }

    fun detachTab(tabId: String) {
        viewModelScope.launch { repository.detachTabToNewWindow(tabId) }
    }

    fun createWindow() {
        viewModelScope.launch { repository.createWindow() }
    }

    fun focusWindow(windowId: String) {
        viewModelScope.launch { repository.focusWindow(windowId) }
    }

    fun closeWindow(windowId: String) {
        val tabIds = state.value.tabs.filter { it.windowId == windowId }.map { it.id }
        tabIds.forEach { sessions.remove(it) }
        viewModelScope.launch { repository.closeWindow(windowId) }
    }

    fun minimizeWindow(windowId: String) {
        viewModelScope.launch { repository.minimizeWindow(windowId) }
    }

    fun restoreWindow(windowId: String) {
        val tabId = state.value.windows.firstOrNull { it.id == windowId }?.activeTabId
        tabId?.let { restoreDiscardedTab(it) }
        viewModelScope.launch { repository.restoreWindow(windowId) }
    }

    fun snapWindow(windowId: String, snap: SnapPosition) {
        viewModelScope.launch { repository.snapWindow(windowId, snap) }
    }

    fun moveWindow(windowId: String, x: Float, y: Float) {
        viewModelScope.launch { repository.moveWindow(windowId, x.coerceIn(0f, 0.9f), y.coerceIn(0f, 0.9f)) }
    }

    fun resizeWindow(windowId: String, width: Float, height: Float) {
        viewModelScope.launch { repository.resizeWindow(windowId, width.coerceIn(0.32f, 1f), height.coerceIn(0.28f, 1f)) }
    }

    fun setChromeMode(windowId: String, mode: BrowserChromeMode) {
        viewModelScope.launch { repository.setChromeMode(windowId, mode) }
        dismissCommandSheet()
    }

    fun showCommandSheet(windowId: String) {
        focusWindow(windowId)
        runtime.update { it.copy(commandSheetWindowId = windowId, commandSheetExpanded = false, launcherVisible = false) }
    }

    fun toggleCommandSheetMore() {
        runtime.update { it.copy(commandSheetExpanded = !it.commandSheetExpanded) }
    }

    fun dismissCommandSheet() {
        runtime.update { it.copy(commandSheetWindowId = null, commandSheetExpanded = false) }
    }

    fun showExternalUrlPrompt(url: String) {
        runtime.update { it.copy(externalUrlPrompt = url) }
    }

    fun dismissExternalUrlPrompt() {
        runtime.update { it.copy(externalUrlPrompt = null) }
    }

    fun toggleOverview() {
        runtime.update { it.copy(overviewVisible = !it.overviewVisible, launcherVisible = false) }
    }

    fun toggleLauncher() {
        runtime.update { it.copy(launcherVisible = !it.launcherVisible, commandSheetWindowId = null, commandSheetExpanded = false, overviewVisible = false) }
    }

    fun closeLauncher() {
        runtime.update { it.copy(launcherVisible = false) }
    }

    fun showPanel(panel: BrowserPanel) {
        runtime.update { it.copy(panel = if (it.panel == panel) BrowserPanel.NONE else panel, commandSheetWindowId = null, commandSheetExpanded = false, launcherVisible = false) }
    }

    fun closePanel() {
        runtime.update { it.copy(panel = BrowserPanel.NONE) }
    }

    fun clearConsole() {
        val tabId = state.value.activeTab?.id ?: return
        viewModelScope.launch { repository.clearConsole(tabId) }
    }

    fun updateConsoleFilter(value: String) {
        runtime.update { it.copy(consoleFilter = value) }
    }

    fun updateJsInput(value: String) {
        runtime.update { it.copy(jsInput = value) }
    }

    fun runJs() {
        val tabId = state.value.activeTab?.id ?: return
        val script = state.value.jsInput
        if (script.isBlank()) return
        sessions[tabId]?.evaluateJavaScript(script) { result ->
            onConsoleEntry(
                ConsoleEntry(
                    tabId = tabId,
                    level = ConsoleLevel.INFO,
                    message = "> $script\n$result",
                    source = "WindowWeb JS Runner",
                    lineNumber = null
                )
            )
        }
        runtime.update { it.copy(jsInput = "") }
    }

    fun clearNetwork() {
        val tabId = state.value.activeTab?.id ?: return
        viewModelScope.launch { repository.clearNetwork(tabId) }
    }

    fun updateSetting(name: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { current ->
                when (name) {
                    "autofill" -> current.copy(androidAutofillEnabled = enabled)
                    "credentials" -> current.copy(credentialSuggestionsEnabled = enabled)
                    "passkeys" -> current.copy(passkeysEnabled = enabled)
                    "externalAuth" -> current.copy(openUnsupportedSecureSignInsExternally = enabled)
                    "developer" -> current.copy(developerModeEnabled = enabled)
                    "console" -> current.copy(captureConsoleEnabled = enabled)
                    "network" -> current.copy(captureNetworkEnabled = enabled)
                    "remote" -> current.copy(remoteDebuggingEnabled = enabled)
                    "restore" -> current.copy(restoreWorkspaceOnLaunch = enabled)
                    "askRestore" -> current.copy(askBeforeRestoringAfterCrash = enabled)
                    "freezeMinimized" -> current.copy(freezeMinimizedTabs = enabled)
                    "discardBackground" -> current.copy(discardBackgroundTabs = enabled)
                    "launcher" -> current.copy(showLauncherButton = enabled)
                    "defaultDesktop" -> current.copy(defaultDesktopMode = enabled)
                    "pinchZoom" -> current.copy(enablePinchZoom = enabled)
                    "safeBrowsing" -> current.copy(safeBrowsingEnabled = enabled)
                    "mixedContent" -> current.copy(blockMixedContent = enabled)
                    "thirdPartyCookies" -> current.copy(thirdPartyCookiesEnabled = enabled)
                    "externalLinks" -> current.copy(externalUrlConfirmationEnabled = enabled)
                    else -> current
                }
            }
        }
    }

    fun setMaxLiveWebViews(count: Int) {
        viewModelScope.launch {
            repository.updateSettings { current -> current.copy(maxLiveWebViews = count.coerceIn(1, 12)) }
        }
    }

    fun setDefaultDesktopViewport(widthDp: Int) {
        viewModelScope.launch {
            repository.updateSettings { current -> current.copy(desktopViewportWidthDp = widthDp.coerceIn(980, 1920)) }
        }
    }

    fun setDesktopMode(windowId: String, enabled: Boolean) {
        val tabId = activeTabId(windowId)
        sessions[tabId]?.setDesktopMode(enabled)
        viewModelScope.launch {
            repository.setDesktopMode(windowId, enabled)
            if (tabId != null) sessions[tabId]?.reload()
        }
    }

    fun zoomWindow(windowId: String, deltaPercent: Int) {
        val window = state.value.windows.firstOrNull { it.id == windowId } ?: return
        val oldZoom = window.pageZoomPercent.coerceIn(50, 300)
        val newZoom = (oldZoom + deltaPercent).coerceIn(50, 300)
        val factor = newZoom.toFloat() / oldZoom.toFloat()
        activeTabId(windowId)?.let { sessions[it]?.zoomBy(factor) }
        viewModelScope.launch { repository.setPageZoom(windowId, newZoom) }
    }

    fun textZoomWindow(windowId: String, deltaPercent: Int) {
        val window = state.value.windows.firstOrNull { it.id == windowId } ?: return
        val newZoom = (window.textZoomPercent + deltaPercent).coerceIn(50, 300)
        activeTabId(windowId)?.let { sessions[it]?.setTextZoom(newZoom) }
        viewModelScope.launch { repository.setTextZoom(windowId, newZoom) }
    }

    fun resetWindowZoom(windowId: String) {
        val window = state.value.windows.firstOrNull { it.id == windowId } ?: return
        val factor = 100f / window.pageZoomPercent.coerceIn(50, 300).toFloat()
        activeTabId(windowId)?.let { tabId ->
            sessions[tabId]?.zoomBy(factor)
            sessions[tabId]?.setTextZoom(100)
        }
        viewModelScope.launch {
            repository.setPageZoom(windowId, 100)
            repository.setTextZoom(windowId, 100)
        }
    }

    fun adjustDesktopViewport(windowId: String, deltaDp: Int) {
        val window = state.value.windows.firstOrNull { it.id == windowId } ?: return
        val width = (window.desktopViewportWidthDp + deltaDp).coerceIn(980, 1920)
        viewModelScope.launch {
            repository.setDesktopViewportWidth(windowId, width)
            activeTabId(windowId)?.let { sessions[it]?.reload() }
        }
    }

    fun onCreateWindowRequest(tabId: String, url: String?) {
        viewModelScope.launch {
            repository.createWindow(url?.takeIf { it.isNotBlank() } ?: "https://www.google.com")
        }
    }

    fun restoreRecentlyClosed(item: RecentlyClosedTabEntity) {
        viewModelScope.launch { repository.restoreRecentlyClosed(item) }
    }

    fun onPageStarted(tabId: String, url: String?) {
        val resolvedUrl = url.orEmpty()
        runtime.update { current ->
            val previous = current.navigationByTab[tabId] ?: BrowserNavigationState()
            current.copy(
                navigationByTab = current.navigationByTab + (tabId to previous.copy(url = resolvedUrl, loading = true, progress = 0, errorDescription = null))
            )
        }
        viewModelScope.launch { repository.updateTabNavigation(tabId, resolvedUrl, null, loading = true) }
    }

    fun onPageFinished(tabId: String, url: String?, title: String?) {
        val resolvedUrl = url.orEmpty()
        runtime.update { current ->
            val previous = current.navigationByTab[tabId] ?: BrowserNavigationState()
            current.copy(
                navigationByTab = current.navigationByTab + (tabId to previous.copy(url = resolvedUrl, title = title, loading = false, progress = 100))
            )
        }
        viewModelScope.launch { repository.updateTabNavigation(tabId, resolvedUrl, title, loading = false) }
    }

    fun onProgressChanged(tabId: String, progress: Int) {
        runtime.update { current ->
            val previous = current.navigationByTab[tabId] ?: BrowserNavigationState()
            current.copy(
                navigationByTab = current.navigationByTab + (tabId to previous.copy(progress = progress, loading = (progress in 1..99)))
            )
        }
    }

    fun onPageError(tabId: String, url: String?, description: String?) {
        runtime.update { current ->
            val previous = current.navigationByTab[tabId] ?: BrowserNavigationState()
            current.copy(
                navigationByTab = current.navigationByTab + (
                    tabId to previous.copy(
                        url = url ?: previous.url,
                        loading = false,
                        progress = 100,
                        errorDescription = description ?: "The page could not be loaded."
                    )
                )
            )
        }
    }

    fun onNavigationStateChanged(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        runtime.update { current ->
            val previous = current.navigationByTab[tabId] ?: BrowserNavigationState()
            current.copy(
                navigationByTab = current.navigationByTab + (tabId to previous.copy(canGoBack = canGoBack, canGoForward = canGoForward))
            )
        }
    }

    fun onConsoleEntry(entry: ConsoleEntry) {
        if (!state.value.settings.captureConsoleEnabled) return
        viewModelScope.launch { repository.appendConsole(entry) }
    }

    fun onNetworkEntry(tabId: String, url: String, method: String, resourceType: String) {
        if (!state.value.settings.captureNetworkEnabled) return
        viewModelScope.launch { repository.appendNetwork(NetworkEntry(tabId = tabId, url = url, method = method, resourceType = resourceType)) }
    }

    fun onPermissionRequest(tabId: String, request: PermissionRequest) {
        val origin = request.origin?.toString().orEmpty().ifBlank { "unknown origin" }
        val resources = request.resources?.toList().orEmpty()
        if (resources.isEmpty()) {
            request.deny()
            return
        }

        viewModelScope.launch {
            val decisions = resources.map { resource -> repository.getSitePermission(origin, resource)?.decision }
            when {
                decisions.all { it == PermissionDecision.ALLOW.name } -> request.grant(resources.toTypedArray())
                decisions.any { it == PermissionDecision.BLOCK.name } -> request.deny()
                else -> {
                    val prompt = PermissionPrompt(origin = origin, resources = resources)
                    pendingPermissionRequests[prompt.requestId] = request
                    runtime.update { it.copy(permissionPrompt = prompt) }
                }
            }
        }
    }

    fun respondPermission(prompt: PermissionPrompt, decision: PermissionDecision, remember: Boolean) {
        val request = pendingPermissionRequests.remove(prompt.requestId)
        when (decision) {
            PermissionDecision.ALLOW -> request?.grant(prompt.resources.toTypedArray())
            PermissionDecision.BLOCK, PermissionDecision.ASK -> request?.deny()
        }
        if (remember) {
            viewModelScope.launch { prompt.resources.forEach { repository.setSitePermission(prompt.origin, it, decision) } }
        }
        runtime.update { it.copy(permissionPrompt = null) }
    }

    fun recordDownload(url: String, fileName: String, mimeType: String?, managerDownloadId: Long?) {
        viewModelScope.launch { repository.recordDownload(url, fileName, mimeType, managerDownloadId) }
    }

    fun checkpointWorkspace(cleanExit: Boolean) {
        viewModelScope.launch { repository.checkpointWorkspace(cleanExit) }
    }

    fun dismissRecoveryBanner() {
        runtime.update { it.copy(recoveryBannerDismissed = true) }
    }

    fun startFreshWorkspace() {
        sessions.clear()
        pendingLoads.clear()
        runtime.update { it.copy(recoveryBannerDismissed = true, launcherVisible = false, overviewVisible = false, commandSheetExpanded = false, panel = BrowserPanel.NONE) }
        viewModelScope.launch { repository.startFreshWorkspace() }
    }

    fun reconcileMemoryPolicy() {
        val current = state.value
        if (current.tabs.isEmpty()) return
        val visibleWindows = current.windows.filterNot { it.minimized }.sortedByDescending { it.zIndex }
        val lifecycleCandidates = if (current.settings.freezeMinimizedTabs) visibleWindows else current.windows.sortedByDescending { it.zIndex }
        val visibleTabIds = visibleWindows.mapNotNull { it.activeTabId }.toSet()
        val activeTabId = current.focusedWindow?.activeTabId
        val maxLive = if (current.memoryPressure) 1 else current.settings.maxLiveWebViews.coerceIn(1, 12)
        val live = if (!current.settings.discardBackgroundTabs) {
            lifecycleCandidates.mapNotNull { it.activeTabId }.toSet()
        } else {
            buildList {
                if (activeTabId != null) add(activeTabId)
                lifecycleCandidates.mapNotNull { it.activeTabId }.forEach { if (it !in this) add(it) }
                current.tabs.sortedByDescending { it.lastActiveAt }.map { it.id }.forEach { if (size < maxLive && it !in this) add(it) }
            }.take(maxLive).toSet()
        }
        runtime.update { it.copy(liveTabIds = live) }
        viewModelScope.launch { repository.markTabsForLifecycle(live, visibleTabIds) }
    }

    fun handleMemoryPressure() {
        runtime.update { it.copy(memoryPressure = true) }
        reconcileMemoryPolicy()
    }

    fun clearMemoryPressureFlag() {
        runtime.update { it.copy(memoryPressure = false) }
    }

    fun shouldKeepTabLive(tabId: String): Boolean {
        val live = state.value.liveTabIds
        return live.isEmpty() || tabId in live
    }

    fun restoreDiscardedTab(tabId: String) {
        runtime.update { it.copy(memoryPressure = false, liveTabIds = it.liveTabIds + tabId) }
        viewModelScope.launch { repository.restoreTabLifecycle(tabId) }
    }

    fun installActiveSiteAsWebApp() {
        val tabId = state.value.activeTab?.id ?: return
        viewModelScope.launch {
            repository.installCurrentSiteAsWebApp(tabId)
            runtime.update { it.copy(panel = BrowserPanel.WEB_APPS, commandSheetWindowId = null, commandSheetExpanded = false) }
        }
    }

    fun launchWebApp(appId: String) {
        viewModelScope.launch {
            repository.launchWebApp(appId)
            runtime.update { it.copy(launcherVisible = false, panel = BrowserPanel.NONE) }
        }
    }

    fun deleteWebApp(appId: String) {
        viewModelScope.launch { repository.deleteWebApp(appId) }
    }

    private fun activeTabId(windowId: String): String? = state.value.windows.firstOrNull { it.id == windowId }?.activeTabId

    class Factory(private val repository: SessionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BrowserViewModel::class.java)) {
                return BrowserViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
