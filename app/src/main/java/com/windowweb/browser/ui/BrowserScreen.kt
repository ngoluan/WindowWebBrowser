@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.windowweb.browser.ui

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.windowweb.browser.BuildConfig
import com.windowweb.browser.core.BrowserChromeMode
import com.windowweb.browser.core.BrowserNavigationState
import com.windowweb.browser.core.PermissionDecision
import com.windowweb.browser.core.SnapPosition
import com.windowweb.browser.core.WindowMode
import com.windowweb.browser.data.ConsoleLogEntity
import com.windowweb.browser.data.TabEntity
import com.windowweb.browser.data.WebAppEntity
import com.windowweb.browser.data.WindowEntity
import com.windowweb.browser.webview.BrowserWebChromeClient
import com.windowweb.browser.webview.BrowserWebViewClient
import com.windowweb.browser.webview.WebViewBrowserSession

@Composable
fun BrowserScreen(viewModel: BrowserViewModel) {
    val rootContext = LocalContext.current
    val state by viewModel.state.collectAsState()
    val memoryWindowKey = state.windows.joinToString { "${it.id}:${it.activeTabId}:${it.minimized}:${it.zIndex}:${it.mode}" }
    val memoryTabKey = state.tabs.joinToString { "${it.id}:${it.windowId}:${it.lastActiveAt}" }

    LaunchedEffect(
        memoryWindowKey,
        memoryTabKey,
        state.focusedWindow?.id,
        state.settings.maxLiveWebViews,
        state.settings.discardBackgroundTabs,
        state.settings.freezeMinimizedTabs,
        state.memoryPressure,
    ) {
        viewModel.reconcileMemoryPolicy()
    }

    Surface(Modifier.fillMaxSize().systemBarsPadding(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (state.windows.isEmpty()) {
                LoadingSeedState()
            } else {
                WorkspaceCanvas(state, viewModel)
            }

            Taskbar(
                state = state,
                onNewWindow = viewModel::createWindow,
                onRestoreWindow = viewModel::restoreWindow,
                onOverview = viewModel::toggleOverview,
                onLauncher = viewModel::toggleLauncher,
                onShowDownloads = { viewModel.showPanel(BrowserPanel.DOWNLOADS) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            if ((state.checkpoint?.cleanExit == false) && !state.recoveryBannerDismissed) {
                RecoveryBanner(
                    state = state,
                    onDismiss = viewModel::dismissRecoveryBanner,
                    onStartFresh = viewModel::startFreshWorkspace,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            if (state.launcherVisible) {
                LauncherPanel(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            if (state.overviewVisible) {
                WindowOverview(
                    state = state,
                    onFocus = { id -> viewModel.restoreWindow(id); viewModel.focusWindow(id); viewModel.toggleOverview() },
                    onClose = viewModel::closeWindow,
                    onSnapLeft = { viewModel.snapWindow(it, SnapPosition.LEFT) },
                    onSnapRight = { viewModel.snapWindow(it, SnapPosition.RIGHT) },
                    onSnapTop = { viewModel.snapWindow(it, SnapPosition.TOP) },
                    onSnapBottom = { viewModel.snapWindow(it, SnapPosition.BOTTOM) },
                    onMini = { viewModel.snapWindow(it, SnapPosition.PICTURE_IN_PICTURE) },
                    onDismiss = viewModel::toggleOverview
                )
            }

            state.commandSheetWindowId?.let { windowId ->
                CommandSheet(
                    state = state,
                    windowId = windowId,
                    viewModel = viewModel,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            state.permissionPrompt?.let { prompt ->
                PermissionPromptCard(
                    prompt = prompt,
                    onAllowOnce = { viewModel.respondPermission(prompt, PermissionDecision.ALLOW, remember = false) },
                    onAlwaysAllow = { viewModel.respondPermission(prompt, PermissionDecision.ALLOW, remember = true) },
                    onBlock = { viewModel.respondPermission(prompt, PermissionDecision.BLOCK, remember = true) },
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            state.externalUrlPrompt?.let { url ->
                ExternalUrlPromptCard(
                    url = url,
                    onOpen = {
                        openExternal(rootContext, url)
                        viewModel.dismissExternalUrlPrompt()
                    },
                    onCancel = viewModel::dismissExternalUrlPrompt,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            when (state.panel) {
                BrowserPanel.CONSOLE -> ConsoleDrawer(state, viewModel, Modifier.align(Alignment.BottomCenter))
                BrowserPanel.NETWORK -> NetworkDrawer(state, viewModel, Modifier.align(Alignment.BottomCenter))
                BrowserPanel.PERMISSIONS -> PermissionsDrawer(state, viewModel::closePanel, Modifier.align(Alignment.BottomCenter))
                BrowserPanel.DOWNLOADS -> DownloadsDrawer(state, viewModel::closePanel, Modifier.align(Alignment.BottomCenter))
                BrowserPanel.SETTINGS -> SettingsDrawer(state, viewModel, Modifier.align(Alignment.BottomCenter))
                BrowserPanel.RECENTLY_CLOSED -> RecentlyClosedDrawer(state, viewModel, Modifier.align(Alignment.BottomCenter))
                BrowserPanel.WEB_APPS -> WebAppsDrawer(state, viewModel, Modifier.align(Alignment.BottomCenter))
                BrowserPanel.NONE -> Unit
            }
        }
    }
}

@Composable
private fun WorkspaceCanvas(state: BrowserUiState, viewModel: BrowserViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = 58.dp)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        state.windows
            .sortedBy { it.zIndex }
            .filterNot { it.minimized }
            .forEach { window ->
                BrowserWindowFrame(
                    window = window,
                    tabs = state.tabs.filter { it.windowId == window.id },
                    state = state,
                    viewModel = viewModel,
                    containerWidth = maxWidth,
                    containerHeight = maxHeight,
                    containerWidthPx = widthPx,
                    containerHeightPx = heightPx
                )
            }
    }
}

@Composable
private fun BrowserWindowFrame(
    window: WindowEntity,
    tabs: List<TabEntity>,
    state: BrowserUiState,
    viewModel: BrowserViewModel,
    containerWidth: Dp,
    containerHeight: Dp,
    containerWidthPx: Float,
    containerHeightPx: Float
) {
    val mode = windowMode(window)
    val chromeMode = chromeMode(window)
    val focused = state.focusedWindow?.id == window.id
    val activeTab = tabs.firstOrNull { it.id == window.activeTabId } ?: tabs.firstOrNull()
    val nav = activeTab?.let { state.navigationByTab[it.id] } ?: BrowserNavigationState(url = activeTab?.url.orEmpty())
    val bounds = windowBounds(window, mode)

    Surface(
        modifier = Modifier
            .offset(x = containerWidth * bounds.x, y = containerHeight * bounds.y)
            .width(containerWidth * bounds.width)
            .height(containerHeight * bounds.height)
            .border(
                BorderStroke(if (focused) 2.dp else 1.dp, if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                RoundedCornerShape(16.dp)
            )
            .clickable { viewModel.focusWindow(window.id) },
        shape = RoundedCornerShape(16.dp),
        tonalElevation = if (focused) 8.dp else 3.dp,
        shadowElevation = if (focused) 8.dp else 3.dp
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (chromeMode == BrowserChromeMode.STANDARD) {
                    WindowTitleBar(
                        window = window,
                        title = activeTab?.title ?: nav.title ?: nav.url.ifBlank { "Window" },
                        onDrag = { dx, dy ->
                            viewModel.moveWindow(window.id, window.x + dx / containerWidthPx, window.y + dy / containerHeightPx)
                        },
                        onMinimize = { viewModel.minimizeWindow(window.id) },
                        onMaximize = { viewModel.snapWindow(window.id, SnapPosition.MAXIMIZE) },
                        onFloat = { viewModel.snapWindow(window.id, SnapPosition.FLOATING) },
                        onClose = { viewModel.closeWindow(window.id) }
                    )
                    BrowserToolbar(
                        state = state,
                        window = window,
                        tabs = tabs,
                        navigation = nav,
                        onAddressChanged = { viewModel.updateAddressText(window.id, it) },
                        onLoad = { viewModel.loadAddress(window.id) },
                        onBack = { viewModel.goBack(window.id) },
                        onForward = { viewModel.goForward(window.id) },
                        onReloadOrStop = { viewModel.reloadOrStop(window.id) },
                        onNewTab = { viewModel.createTab(window.id) },
                        onTabSelected = { viewModel.activateTab(window.id, it) },
                        onCloseTab = viewModel::closeTab,
                        onMenu = { viewModel.showCommandSheet(window.id) }
                    )
                }

                if (nav.loading && nav.progress in 1..99) {
                    LinearProgressIndicator(progress = { nav.progress / 100f }, modifier = Modifier.fillMaxWidth())
                } else {
                    Spacer(Modifier.height(2.dp))
                }

                Box(Modifier.fillMaxSize()) {
                    if (activeTab == null) {
                        EmptyWindowState(onNewTab = { viewModel.createTab(window.id) })
                    } else if (activeTab.discarded || !viewModel.shouldKeepTabLive(activeTab.id)) {
                        TabLifecyclePlaceholder(
                            tab = activeTab,
                            onRestore = { viewModel.restoreDiscardedTab(activeTab.id) }
                        )
                    } else {
                        BrowserWebViewHost(
                            tab = activeTab,
                            window = window,
                            viewModel = viewModel,
                            settings = state.settings,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (!nav.loading && nav.errorDescription != null) {
                        PageErrorOverlay(
                            url = nav.url,
                            description = nav.errorDescription,
                            onRetry = { viewModel.reloadOrStop(window.id) }
                        )
                    }
                }
            }

            if (chromeMode != BrowserChromeMode.STANDARD && nav.url.isNotBlank()) {
                DomainChip(url = nav.url, modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            }

            if (chromeMode == BrowserChromeMode.MINIMAL || chromeMode == BrowserChromeMode.FULLSCREEN_WEB_APP || chromeMode == BrowserChromeMode.AUTO_HIDE) {
                Button(
                    onClick = { viewModel.showCommandSheet(window.id) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) { Text("•••") }
            }

            if (mode == WindowMode.FLOATING || mode == WindowMode.PICTURE_IN_PICTURE) {
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SmallToolbarButton("−", enabled = true) { viewModel.resizeWindow(window.id, window.width - 0.06f, window.height - 0.06f) }
                    SmallToolbarButton("+", enabled = true) { viewModel.resizeWindow(window.id, window.width + 0.06f, window.height + 0.06f) }
                }
            }
        }
    }
}

private data class WindowBounds(val x: Float, val y: Float, val width: Float, val height: Float)

private fun windowBounds(window: WindowEntity, mode: WindowMode): WindowBounds = when (mode) {
    WindowMode.MAXIMIZED, WindowMode.FULLSCREEN -> WindowBounds(0f, 0f, 1f, 1f)
    WindowMode.SPLIT_LEFT -> WindowBounds(0f, 0f, 0.5f, 1f)
    WindowMode.SPLIT_RIGHT -> WindowBounds(0.5f, 0f, 0.5f, 1f)
    WindowMode.SPLIT_TOP -> WindowBounds(0f, 0f, 1f, 0.5f)
    WindowMode.SPLIT_BOTTOM -> WindowBounds(0f, 0.5f, 1f, 0.5f)
    WindowMode.PICTURE_IN_PICTURE -> WindowBounds(window.x, window.y, window.width, window.height)
    WindowMode.FLOATING -> WindowBounds(window.x, window.y, window.width, window.height)
    WindowMode.MINIMIZED -> WindowBounds(window.x, window.y, window.width, window.height)
}

private fun windowMode(window: WindowEntity): WindowMode = runCatching { WindowMode.valueOf(window.mode) }.getOrDefault(WindowMode.FLOATING)
private fun chromeMode(window: WindowEntity): BrowserChromeMode = runCatching { BrowserChromeMode.valueOf(window.chromeMode) }.getOrDefault(BrowserChromeMode.STANDARD)

@Composable
private fun WindowTitleBar(
    window: WindowEntity,
    title: String,
    onDrag: (Float, Float) -> Unit,
    onMinimize: () -> Unit,
    onMaximize: () -> Unit,
    onFloat: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(window.id) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        SmallToolbarButton("_", true, onMinimize)
        SmallToolbarButton("□", true, onMaximize)
        SmallToolbarButton("↘", true, onFloat)
        SmallToolbarButton("×", true, onClose)
    }
}

@Composable
private fun BrowserToolbar(
    state: BrowserUiState,
    window: WindowEntity,
    tabs: List<TabEntity>,
    navigation: BrowserNavigationState,
    onAddressChanged: (String) -> Unit,
    onLoad: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onNewTab: () -> Unit,
    onTabSelected: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onMenu: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallToolbarButton("←", navigation.canGoBack, onBack)
            SmallToolbarButton("→", navigation.canGoForward, onForward)
            SmallToolbarButton(if (navigation.loading) "×" else "↻", true, onReloadOrStop)
            OutlinedTextField(
                value = state.addressDraftByWindow[window.id] ?: navigation.url,
                onValueChange = onAddressChanged,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onLoad() }),
                placeholder = { Text("Search or enter address") }
            )
            SmallToolbarButton("Go", true, onLoad)
            SmallToolbarButton("⋮", true, onMenu)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.take(5).forEach { tab ->
                val active = tab.id == window.activeTabId
                Button(
                    onClick = { onTabSelected(tab.id) },
                    contentPadding = ButtonDefaults.ContentPadding,
                    modifier = Modifier.height(34.dp),
                    border = if (active) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                ) { Text(tab.title ?: tab.url, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (active) SmallToolbarButton("×", true) { onCloseTab(tab.id) }
            }
            SmallToolbarButton("+", true, onNewTab)
        }
    }
}

@Composable
private fun CommandSheet(
    state: BrowserUiState,
    windowId: String,
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier
) {
    val window = state.windows.firstOrNull { it.id == windowId } ?: return
    val tabs = state.tabs.filter { it.windowId == window.id }
    val activeTab = tabs.firstOrNull { it.id == window.activeTabId }
    val nav = activeTab?.let { state.navigationByTab[it.id] } ?: BrowserNavigationState(url = activeTab?.url.orEmpty())
    val sheetHeight = if (state.commandSheetExpanded) Modifier.fillMaxHeight(0.72f) else Modifier

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(sheetHeight)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 10.dp,
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Controls", style = MaterialTheme.typography.titleMedium)
                    Text(domainFromUrl(nav.url), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SmallToolbarButton(if (state.commandSheetExpanded) "Less" else "More", true) { viewModel.toggleCommandSheetMore() }
                SmallToolbarButton("Close", true) { viewModel.dismissCommandSheet() }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.addressDraftByWindow[window.id] ?: nav.url,
                    onValueChange = { viewModel.updateAddressText(window.id, it) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { viewModel.loadAddress(window.id); viewModel.dismissCommandSheet() }),
                    placeholder = { Text("Search or enter address") }
                )
                SmallToolbarButton("Go", true) { viewModel.loadAddress(window.id); viewModel.dismissCommandSheet() }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickControlButton("←", nav.canGoBack) { viewModel.goBack(window.id) }
                QuickControlButton("→", nav.canGoForward) { viewModel.goForward(window.id) }
                QuickControlButton(if (nav.loading) "×" else "↻", true) { viewModel.reloadOrStop(window.id) }
                QuickControlButton("+", true) { viewModel.createTab(window.id) }
                QuickControlButton("▦", true) { viewModel.toggleOverview(); viewModel.dismissCommandSheet() }
                QuickControlButton("↗", true) { viewModel.createWindow() }
                QuickControlButton("⋮", true) { viewModel.toggleCommandSheetMore() }
            }

            SectionHeader("Display")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BrowserChromeMode.entries.forEach { mode ->
                    SmallToolbarButton(
                        label = mode.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                        enabled = true,
                        selected = mode.name == window.chromeMode
                    ) { viewModel.setChromeMode(window.id, mode) }
                }
            }

            if (state.commandSheetExpanded) {
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        SectionHeader("Window")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallToolbarButton("Split left", true) { viewModel.snapWindow(window.id, SnapPosition.LEFT) }
                            SmallToolbarButton("Split right", true) { viewModel.snapWindow(window.id, SnapPosition.RIGHT) }
                            SmallToolbarButton("Split top", true) { viewModel.snapWindow(window.id, SnapPosition.TOP) }
                            SmallToolbarButton("Split bottom", true) { viewModel.snapWindow(window.id, SnapPosition.BOTTOM) }
                            SmallToolbarButton("Mini", true) { viewModel.snapWindow(window.id, SnapPosition.PICTURE_IN_PICTURE) }
                            SmallToolbarButton("Maximize", true) { viewModel.snapWindow(window.id, SnapPosition.MAXIMIZE) }
                            SmallToolbarButton("Float", true) { viewModel.snapWindow(window.id, SnapPosition.FLOATING) }
                            SmallToolbarButton("Minimize", true) { viewModel.minimizeWindow(window.id); viewModel.dismissCommandSheet() }
                        }
                    }
                    item {
                        SectionHeader("Tab")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallToolbarButton("New tab", true) { viewModel.createTab(window.id) }
                            activeTab?.let {
                                SmallToolbarButton("Duplicate", true) { viewModel.duplicateTab(it.id) }
                                SmallToolbarButton("Detach", true) { viewModel.detachTab(it.id) }
                                SmallToolbarButton("Close tab", true) { viewModel.closeTab(it.id); viewModel.dismissCommandSheet() }
                            }
                            SmallToolbarButton("Closed tabs", true) { viewModel.showPanel(BrowserPanel.RECENTLY_CLOSED) }
                        }
                    }
                    item {
                        SectionHeader("Page")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallToolbarButton(if (window.desktopMode) "Mobile site" else "Desktop site", true) { viewModel.setDesktopMode(window.id, !window.desktopMode) }
                            SmallToolbarButton("Install web app", activeTab != null) { viewModel.installActiveSiteAsWebApp() }
                            SmallToolbarButton("Zoom −", true) { viewModel.zoomWindow(window.id, -10) }
                            SmallToolbarButton("Zoom +", true) { viewModel.zoomWindow(window.id, 10) }
                            SmallToolbarButton("Text −", true) { viewModel.textZoomWindow(window.id, -10) }
                            SmallToolbarButton("Text +", true) { viewModel.textZoomWindow(window.id, 10) }
                            SmallToolbarButton("Reset zoom", true) { viewModel.resetWindowZoom(window.id) }
                            SmallToolbarButton("Viewport −", window.desktopViewportWidthDp > 980) { viewModel.adjustDesktopViewport(window.id, -160) }
                            SmallToolbarButton("Viewport +", window.desktopViewportWidthDp < 1920) { viewModel.adjustDesktopViewport(window.id, 160) }
                        }
                        Text(
                            "Desktop: ${if (window.desktopMode) "on" else "off"} • Page zoom: ${window.pageZoomPercent}% • Text zoom: ${window.textZoomPercent}% • Viewport: ${window.desktopViewportWidthDp}dp",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    item {
                        SectionHeader("Tools")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallToolbarButton("Console", true) { viewModel.showPanel(BrowserPanel.CONSOLE) }
                            SmallToolbarButton("Network", true) { viewModel.showPanel(BrowserPanel.NETWORK) }
                            SmallToolbarButton("Downloads", true) { viewModel.showPanel(BrowserPanel.DOWNLOADS) }
                            SmallToolbarButton("Permissions", true) { viewModel.showPanel(BrowserPanel.PERMISSIONS) }
                            SmallToolbarButton("Web apps", true) { viewModel.showPanel(BrowserPanel.WEB_APPS) }
                            SmallToolbarButton("Settings", true) { viewModel.showPanel(BrowserPanel.SETTINGS) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun QuickControlButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        contentPadding = ButtonDefaults.ContentPadding,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) { Text(label, maxLines = 1, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun SmallToolbarButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    SmallToolbarButton(label = label, enabled = enabled, selected = false, onClick = onClick)
}

@Composable
private fun SmallToolbarButton(label: String, enabled: Boolean, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(34.dp),
        contentPadding = ButtonDefaults.ContentPadding,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
    }
}


@Composable
private fun DomainChip(url: String, modifier: Modifier = Modifier) {
    val domain = domainFromUrl(url)
    if (domain.isBlank()) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Text(
            domain,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PageErrorOverlay(url: String, description: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.padding(20.dp),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Page could not load", style = MaterialTheme.typography.titleLarge)
                Text(domainFromUrl(url).ifBlank { url }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallToolbarButton("Retry", true, onRetry)
                    SmallToolbarButton("Check connection", true) { }
                }
            }
        }
    }
}

private fun domainFromUrl(url: String): String = runCatching {
    url.toUri().host?.removePrefix("www.") ?: url
}.getOrElse { url }

@Composable
private fun LoadingSeedState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Creating browser workspace…")
        }
    }
}

@Composable
private fun EmptyWindowState(onNewTab: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onNewTab) { Text("New tab") }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserWebViewHost(
    tab: TabEntity,
    window: WindowEntity,
    viewModel: BrowserViewModel,
    settings: com.windowweb.browser.data.BrowserSettingsEntity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        fileCallback?.onReceiveValue(uris.toTypedArray())
        fileCallback = null
    }
    var webViewRef by remember(tab.id) { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                configureBrowserWebView(context, settings, window)
                webViewClient = BrowserWebViewClient(
                    tabId = tab.id,
                    onPageStarted = viewModel::onPageStarted,
                    onPageFinished = viewModel::onPageFinished,
                    onNavigationStateChanged = viewModel::onNavigationStateChanged,
                    onRequestSeen = viewModel::onNetworkEntry,
                    onPageError = viewModel::onPageError,
                    onExternalUrl = {
                        if (settings.externalUrlConfirmationEnabled) viewModel.showExternalUrlPrompt(it) else openExternal(context, it)
                    },
                    desktopMode = window.desktopMode,
                    desktopViewportWidthDp = window.desktopViewportWidthDp,
                    pageZoomPercent = window.pageZoomPercent
                )
                webChromeClient = BrowserWebChromeClient(
                    tabId = tab.id,
                    onProgressChanged = viewModel::onProgressChanged,
                    onConsoleEntry = viewModel::onConsoleEntry,
                    onFileChooser = { callback, params ->
                        fileCallback?.onReceiveValue(null)
                        fileCallback = callback
                        val mimeTypes = params.acceptTypes.filter { it.isNotBlank() }.toTypedArray()
                        fileLauncher.launch(if (mimeTypes.isEmpty()) arrayOf("*/*") else mimeTypes)
                        true
                    },
                    onPermissionRequest = viewModel::onPermissionRequest,
                    onCreateWindowRequest = viewModel::onCreateWindowRequest
                )
                setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                    val managerId = enqueueDownload(context, url, userAgent, contentDisposition, mimeType)
                    val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    viewModel.recordDownload(url, fileName, mimeType, managerId)
                }
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG || settings.remoteDebuggingEnabled)
                webViewRef = this
                viewModel.attachSession(WebViewBrowserSession(tab.id, this))
                loadUrl(tab.url)
            }
        },
        update = { webView ->
            webView.applyDesktopAndZoomSettings(context, settings, window)
            if (webView.url.isNullOrBlank()) webView.loadUrl(tab.url)
        }
    )

    DisposableEffect(tab.id) {
        onDispose {
            viewModel.detachSession(tab.id)
            webViewRef?.let { webView ->
                webView.stopLoading()
                webView.webChromeClient = null
                webView.webViewClient = android.webkit.WebViewClient()
                webView.destroy()
            }
            webViewRef = null
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureBrowserWebView(context: Context, settingsEntity: com.windowweb.browser.data.BrowserSettingsEntity, window: WindowEntity) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.loadsImagesAutomatically = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.savePassword = false
    setBackgroundColor(android.graphics.Color.WHITE)
    applyDesktopAndZoomSettings(context, settingsEntity, window)
}

private fun WebView.applyDesktopAndZoomSettings(
    context: Context,
    settingsEntity: com.windowweb.browser.data.BrowserSettingsEntity,
    window: WindowEntity
) {
    importantForAutofill = if (settingsEntity.androidAutofillEnabled) View.IMPORTANT_FOR_AUTOFILL_YES else View.IMPORTANT_FOR_AUTOFILL_NO
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = window.desktopMode
    settings.setSupportZoom(settingsEntity.enablePinchZoom)
    settings.builtInZoomControls = settingsEntity.enablePinchZoom
    settings.displayZoomControls = false
    settings.textZoom = window.textZoomPercent.coerceIn(50, 300)
    settings.safeBrowsingEnabled = settingsEntity.safeBrowsingEnabled
    settings.mixedContentMode = if (settingsEntity.blockMixedContent) {
        WebSettings.MIXED_CONTENT_NEVER_ALLOW
    } else {
        WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    }
    settings.userAgentString = if (window.desktopMode) desktopUserAgent() else defaultMobileUserAgent(context)
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, settingsEntity.thirdPartyCookiesEnabled)
    if (window.pageZoomPercent != 100) {
        setInitialScale(window.pageZoomPercent.coerceIn(50, 300))
    }
}

private fun defaultMobileUserAgent(context: Context): String =
    WebSettings.getDefaultUserAgent(context).let { default ->
        if (default.contains("WindowWeb/")) default else "$default WindowWeb/0.4"
    }

private fun desktopUserAgent(): String =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 WindowWeb/0.4"

private fun enqueueDownload(context: Context, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?): Long? {
    return runCatching {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
        if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
        val manager = context.getSystemService(DownloadManager::class.java)
        manager.enqueue(request)
    }.getOrNull()
}

private fun openExternal(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun Taskbar(
    state: BrowserUiState,
    onNewWindow: () -> Unit,
    onRestoreWindow: (String) -> Unit,
    onOverview: () -> Unit,
    onLauncher: () -> Unit,
    onShowDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.settings.showLauncherButton) QuickControlButton("☰", true, onLauncher)
            QuickControlButton("+", true, onNewWindow)
            QuickControlButton("▦", true, onOverview)
            QuickControlButton("↓", true, onShowDownloads)
            state.windows.sortedByDescending { it.zIndex }.take(3).forEach { window ->
                val title = state.tabs.firstOrNull { it.id == window.activeTabId }?.title
                    ?: domainFromUrl(state.tabs.firstOrNull { it.id == window.activeTabId }?.url.orEmpty()).ifBlank { "Window" }
                Button(
                    onClick = { onRestoreWindow(window.id) },
                    modifier = Modifier.height(34.dp).weight(1f, fill = false),
                    contentPadding = ButtonDefaults.ContentPadding,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (window.minimized) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(if (window.minimized) "▣ $title" else title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun WindowOverview(
    state: BrowserUiState,
    onFocus: (String) -> Unit,
    onClose: (String) -> Unit,
    onSnapLeft: (String) -> Unit,
    onSnapRight: (String) -> Unit,
    onSnapTop: (String) -> Unit,
    onSnapBottom: (String) -> Unit,
    onMini: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(Modifier.fillMaxSize().padding(18.dp), shape = RoundedCornerShape(22.dp), tonalElevation = 12.dp, shadowElevation = 12.dp) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Window overview", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                SmallToolbarButton("Close", true, onDismiss)
            }
            Text("Live WebViews: ${state.liveTabIds.size.coerceAtLeast(0)} • Suspended: ${state.suspendedTabCount} • Discarded: ${state.discardedTabCount}", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.windows.sortedByDescending { it.zIndex }) { window ->
                    val tab = state.tabs.firstOrNull { it.id == window.activeTabId }
                    Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 3.dp) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(tab?.title ?: tab?.url ?: "Empty window", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${window.mode} • ${window.chromeMode}", style = MaterialTheme.typography.labelSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SmallToolbarButton("Focus", true) { onFocus(window.id) }
                                SmallToolbarButton("Left", true) { onSnapLeft(window.id) }
                                SmallToolbarButton("Right", true) { onSnapRight(window.id) }
                                SmallToolbarButton("Top", true) { onSnapTop(window.id) }
                                SmallToolbarButton("Bottom", true) { onSnapBottom(window.id) }
                                SmallToolbarButton("Mini", true) { onMini(window.id) }
                                SmallToolbarButton("Close", true) { onClose(window.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPromptCard(
    prompt: com.windowweb.browser.core.PermissionPrompt,
    onAllowOnce: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.padding(24.dp), shape = RoundedCornerShape(20.dp), tonalElevation = 12.dp, shadowElevation = 12.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Site permission request", style = MaterialTheme.typography.titleLarge)
            Text(prompt.origin, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(prompt.resources.joinToString("\n") { "• ${it.substringAfterLast('.') }" })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallToolbarButton("Block", true, onBlock)
                SmallToolbarButton("Allow once", true, onAllowOnce)
                SmallToolbarButton("Always allow", true, onAlwaysAllow)
            }
        }
    }
}

@Composable
private fun ExternalUrlPromptCard(
    url: String,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.padding(24.dp), shape = RoundedCornerShape(20.dp), tonalElevation = 12.dp, shadowElevation = 12.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Open external app?", style = MaterialTheme.typography.titleLarge)
            Text(url, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("This link wants to leave WindowWeb and open another Android app.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallToolbarButton("Cancel", true, onCancel)
                SmallToolbarButton("Open", true, onOpen)
            }
        }
    }
}

@Composable
private fun ConsoleDrawer(state: BrowserUiState, viewModel: BrowserViewModel, modifier: Modifier = Modifier) {
    BottomDrawer(title = "Console", modifier = modifier, onClose = viewModel::closePanel) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = state.consoleFilter, onValueChange = viewModel::updateConsoleFilter, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("Filter logs") })
            SmallToolbarButton("Clear", true, viewModel::clearConsole)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = state.jsInput, onValueChange = viewModel::updateJsInput, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text("JavaScript expression") })
            SmallToolbarButton("Run", state.settings.developerModeEnabled, viewModel::runJs)
        }
        val filtered = state.consoleEntries.filter { it.message.contains(state.consoleFilter, ignoreCase = true) || it.level.contains(state.consoleFilter, ignoreCase = true) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered) { log -> ConsoleRow(log) }
        }
    }
}

@Composable
private fun ConsoleRow(log: ConsoleLogEntity) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text("${log.level}  ${log.message}", style = MaterialTheme.typography.bodySmall)
        if (!log.source.isNullOrBlank()) Text("${log.source}:${log.lineNumber ?: 0}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NetworkDrawer(state: BrowserUiState, viewModel: BrowserViewModel, modifier: Modifier = Modifier) {
    BottomDrawer(title = "Network requests", modifier = modifier, onClose = viewModel::closePanel) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { SmallToolbarButton("Clear", true, viewModel::clearNetwork) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.networkEntries) { item ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text("${item.method} ${item.resourceType}", style = MaterialTheme.typography.labelMedium)
                    Text(item.url, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PermissionsDrawer(state: BrowserUiState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    BottomDrawer(title = "Site permissions", modifier = modifier, onClose = onClose) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.permissions) { item ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(item.origin, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.resource.substringAfterLast('.')} — ${item.decision}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun DownloadsDrawer(state: BrowserUiState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    BottomDrawer(title = "Downloads", modifier = modifier, onClose = onClose) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.downloads) { item ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.status} • ${item.mimeType ?: "unknown type"}", style = MaterialTheme.typography.labelMedium)
                    Text(item.url, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SettingsDrawer(state: BrowserUiState, viewModel: BrowserViewModel, modifier: Modifier = Modifier) {
    BottomDrawer(title = "Browser settings", modifier = modifier, onClose = viewModel::closePanel) {
        SettingSwitch("Android Autofill / Google Password Manager", state.settings.androidAutofillEnabled) { viewModel.updateSetting("autofill", it) }
        SettingSwitch("Credential suggestions", state.settings.credentialSuggestionsEnabled) { viewModel.updateSetting("credentials", it) }
        SettingSwitch("Passkeys feature flag", state.settings.passkeysEnabled) { viewModel.updateSetting("passkeys", it) }
        SettingSwitch("Open unsupported secure sign-ins externally", state.settings.openUnsupportedSecureSignInsExternally) { viewModel.updateSetting("externalAuth", it) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SettingSwitch("Developer mode", state.settings.developerModeEnabled) { viewModel.updateSetting("developer", it) }
        SettingSwitch("Capture console", state.settings.captureConsoleEnabled) { viewModel.updateSetting("console", it) }
        SettingSwitch("Capture network requests", state.settings.captureNetworkEnabled) { viewModel.updateSetting("network", it) }
        SettingSwitch("Remote WebView debugging", state.settings.remoteDebuggingEnabled) { viewModel.updateSetting("remote", it) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SettingSwitch("Restore workspace on launch", state.settings.restoreWorkspaceOnLaunch) { viewModel.updateSetting("restore", it) }
        SettingSwitch("Ask before restoring after crash", state.settings.askBeforeRestoringAfterCrash) { viewModel.updateSetting("askRestore", it) }
        SettingSwitch("Freeze minimized tabs", state.settings.freezeMinimizedTabs) { viewModel.updateSetting("freezeMinimized", it) }
        SettingSwitch("Discard background tabs", state.settings.discardBackgroundTabs) { viewModel.updateSetting("discardBackground", it) }
        SettingSwitch("Show launcher button", state.settings.showLauncherButton) { viewModel.updateSetting("launcher", it) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SettingSwitch("Default new windows to desktop mode", state.settings.defaultDesktopMode) { viewModel.updateSetting("defaultDesktop", it) }
        SettingSwitch("Enable pinch/page zoom", state.settings.enablePinchZoom) { viewModel.updateSetting("pinchZoom", it) }
        SettingSwitch("Safe Browsing", state.settings.safeBrowsingEnabled) { viewModel.updateSetting("safeBrowsing", it) }
        SettingSwitch("Block mixed content", state.settings.blockMixedContent) { viewModel.updateSetting("mixedContent", it) }
        SettingSwitch("Allow third-party cookies", state.settings.thirdPartyCookiesEnabled) { viewModel.updateSetting("thirdPartyCookies", it) }
        SettingSwitch("Confirm external app links", state.settings.externalUrlConfirmationEnabled) { viewModel.updateSetting("externalLinks", it) }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Desktop viewport: ${state.settings.desktopViewportWidthDp}dp", modifier = Modifier.weight(1f))
            SmallToolbarButton("−", true) { viewModel.setDefaultDesktopViewport(state.settings.desktopViewportWidthDp - 160) }
            SmallToolbarButton("+", true) { viewModel.setDefaultDesktopViewport(state.settings.desktopViewportWidthDp + 160) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Live WebViews: ${state.settings.maxLiveWebViews}", modifier = Modifier.weight(1f))
            SmallToolbarButton("−", true) { viewModel.setMaxLiveWebViews(state.settings.maxLiveWebViews - 1) }
            SmallToolbarButton("+", true) { viewModel.setMaxLiveWebViews(state.settings.maxLiveWebViews + 1) }
        }
    }
}

@Composable
private fun RecentlyClosedDrawer(state: BrowserUiState, viewModel: BrowserViewModel, modifier: Modifier = Modifier) {
    BottomDrawer(title = "Recently closed tabs", modifier = modifier, onClose = viewModel::closePanel) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.recentlyClosed) { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title ?: item.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.url, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    SmallToolbarButton("Restore", true) { viewModel.restoreRecentlyClosed(item) }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}


@Composable
private fun RecoveryBanner(state: BrowserUiState, onDismiss: () -> Unit, onStartFresh: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(10.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 10.dp,
        shadowElevation = 10.dp
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Workspace recovered", style = MaterialTheme.typography.titleMedium)
                val checkpoint = state.checkpoint
                Text(
                    "Restored ${checkpoint?.windowCount ?: state.windows.size} windows and ${checkpoint?.tabCount ?: state.tabs.size} tabs from the last checkpoint.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallToolbarButton("Keep", true, onDismiss)
                SmallToolbarButton("Start fresh", true, onStartFresh)
            }
        }
    }
}

@Composable
private fun TabLifecyclePlaceholder(tab: TabEntity, onRestore: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (tab.discarded) "Tab discarded to save memory" else "Tab paused")
            Text(tab.title ?: tab.url, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
            SmallToolbarButton("Restore tab", true, onRestore)
        }
    }
}

@Composable
private fun LauncherPanel(state: BrowserUiState, viewModel: BrowserViewModel, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().fillMaxHeight(0.72f).padding(10.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 12.dp,
        shadowElevation = 12.dp
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("WindowWeb launcher", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                SmallToolbarButton("Close", true, viewModel::closeLauncher)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallToolbarButton("New window", true) { viewModel.createWindow(); viewModel.closeLauncher() }
                SmallToolbarButton("Overview", true) { viewModel.toggleOverview(); viewModel.closeLauncher() }
                SmallToolbarButton("Downloads", true) { viewModel.showPanel(BrowserPanel.DOWNLOADS) }
                SmallToolbarButton("Recently closed", true) { viewModel.showPanel(BrowserPanel.RECENTLY_CLOSED) }
                SmallToolbarButton("Settings", true) { viewModel.showPanel(BrowserPanel.SETTINGS) }
                SmallToolbarButton("Web apps", true) { viewModel.showPanel(BrowserPanel.WEB_APPS) }
                SmallToolbarButton("Install current site", state.activeTab != null) { viewModel.installActiveSiteAsWebApp() }
            }
            HorizontalDivider()
            Text("Pinned web apps", style = MaterialTheme.typography.titleMedium)
            if (state.webApps.isEmpty()) {
                Text("No web apps installed yet. Open a site, tap •••, then choose Install web app.", style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.webApps) { app ->
                        WebAppRow(app = app, onLaunch = { viewModel.launchWebApp(app.id) }, onDelete = { viewModel.deleteWebApp(app.id) })
                    }
                }
            }
            HorizontalDivider()
            Text("Open windows", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.windows.sortedByDescending { it.zIndex }) { window ->
                    val tab = state.tabs.firstOrNull { it.id == window.activeTabId }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tab?.title ?: tab?.url ?: "Empty window", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${window.mode} • ${window.chromeMode}", style = MaterialTheme.typography.labelSmall)
                        }
                        SmallToolbarButton("Open", true) { viewModel.restoreWindow(window.id); viewModel.closeLauncher() }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebAppsDrawer(state: BrowserUiState, viewModel: BrowserViewModel, modifier: Modifier = Modifier) {
    BottomDrawer(title = "Web apps", modifier = modifier, onClose = viewModel::closePanel) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SmallToolbarButton("Install current", state.activeTab != null) { viewModel.installActiveSiteAsWebApp() }
        }
        if (state.webApps.isEmpty()) {
            Text("No web apps installed yet.")
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.webApps) { app ->
                    WebAppRow(app = app, onLaunch = { viewModel.launchWebApp(app.id) }, onDelete = { viewModel.deleteWebApp(app.id) })
                }
            }
        }
    }
}

@Composable
private fun WebAppRow(app: WebAppEntity, onLaunch: () -> Unit, onDelete: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.origin, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SmallToolbarButton("Launch", true, onLaunch)
            SmallToolbarButton("Delete", true, onDelete)
        }
    }
}

@Composable
private fun BottomDrawer(title: String, modifier: Modifier = Modifier, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier.fillMaxWidth().fillMaxHeight(0.48f).padding(8.dp), shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), tonalElevation = 10.dp, shadowElevation = 10.dp) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                SmallToolbarButton("Close", true, onClose)
            }
            content()
        }
    }
}
