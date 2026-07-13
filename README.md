# WindowWeb Browser — Phase 1–15

WindowWeb is a Chromium WebView-based Android browser prototype that treats tabs as movable browser windows inside a phone-friendly workspace.

This package includes the phase 1–14 implementation plus the consolidated Phase 15 window-management and secure-navigation patch.

## Phase 15 — Full window management and HTTPS hardening

- Resize floating windows by dragging any border or corner.
- Tap the URL/domain control to open the command sheet; the separate ellipsis control is removed.
- Web content receives touch events normally while a touch still focuses its window.
- Added pin, layout lock, opacity, duplicate-window, quadrant/third snapping, collect, tile, cascade, stack, minimize-all and restore-all controls.
- Added a Room 4-to-5 migration so existing workspaces survive the new window fields.
- URL parsing repairs common malformed `https:/` and `https//` addresses.
- SSL/TLS failures now show a useful browser error instead of silently bypassing certificate validation.
- `ERR_SSL_PROTOCOL_ERROR` offers an explicit **Try HTTP** action for trusted servers that are actually serving plain HTTP.
- User agents are derived from the installed Android System WebView instead of advertising a hard-coded old Chrome version.
- Added network-security configuration with debug-only support for user-installed certificate authorities.
- Added unit coverage for URL normalization and window geometry.

## Phase 15.1 — Development-server access

- `net::ERR_HTTP_RESPONSE_CODE_FAILURE` no longer triggers WindowWeb's full-screen network overlay; WebView is allowed to render the server's HTTP 4xx/5xx response or diagnostic page.
- Added HTTP Basic authentication prompts for development servers that return an authentication challenge.
- Self-signed or otherwise invalid development certificates now show an explicit warning with **Continue once** and **Always trust host** actions.
- Persistent certificate exceptions are scoped to the exact host and port and can be cleared from Settings. Release builds only offer this exception for local/private development endpoints; debug builds can approve other development hosts explicitly.
- Non-recoverable TLS protocol failures still remain blocked and may offer an explicit HTTP fallback when the server is actually plain HTTP.

## Phase 15.2 — In-app page console and blank-page diagnostics

- Added a permanent console shortcut to the bottom taskbar, including an error/warning count for the active tab.
- Opening the console automatically enables developer mode and console capture for subsequent page activity.
- JavaScript errors and warnings are retained even when full console capture had previously been disabled.
- Added document-start listeners for uncaught JavaScript errors, failed resources, unhandled promise rejections and Content Security Policy violations.
- HTTP 4xx/5xx responses are recorded in the console, including failed script and stylesheet requests that can leave a page blank.
- Page-finish diagnostics report DOM size, visible text, body dimensions and visibility. Suspicious empty pages are highlighted as warnings.
- Added a **Diagnose page** action and JavaScript runner directly inside the expanded console drawer.
- Web console messages are also mirrored to Android Logcat under the `WindowWebConsole` tag.


## Phase 15.3 — Console workspace and stateful tab retention

- Replaced the short bottom console drawer with a nearly full-screen developer console.
- Added compact filters, issues-only mode, optional JavaScript runner, automatic scrolling and per-entry/full-view copy actions.
- Console text is selectable and rendered in a monospace layout with clearer level/source separation.
- Repeated autofocus noise is suppressed and duplicate messages are collapsed before persistence.
- Console object arguments, fetch failures and EventSource connection details are serialized instead of degrading to `[object Object]`.
- Live background tabs remain mounted instead of destroying their WebViews during every tab switch.
- Resident hidden tabs keep JavaScript, WebSocket and EventSource state alive, while the existing memory policy still limits how many WebViews remain resident.
- Renderer priority is raised for the visible tab and retained for resident background tabs to reduce blank returns after switching.

## Phase 15.4 — Native development-server transport

- Removed the diagnostic monkey-patches that replaced `window.fetch` and `window.EventSource`; diagnostics now observe errors without changing page networking primitives.
- Added a same-origin native transport for OpenCode's `/global/health`, `/global/event`, and `/event` GET streams.
- The native transport forwards request headers and cookies, disables response compression, preserves HTTP status/headers, and keeps SSE response streams open until WebView closes them.
- If the native request cannot connect, WindowWeb logs the underlying exception and lets Chromium perform its normal request instead.
- Cross-origin requests, request methods with bodies, page assets, and unrelated websites are not proxied.
- Page snapshots now include `navigator.onLine` state.

The app version is now `0.5.4-native-dev-transport`.

This package includes the earlier phases:

## Phase 12 — Workspace persistence and crash recovery

- Persistent workspace, window, tab, history, permission, download, console, network and setting tables.
- New `WorkspaceCheckpointEntity` records the latest focused window, active tab, window count, tab count and clean-exit status.
- `MainActivity.onStop()` writes a clean checkpoint.
- App launch marks the session dirty so a hard crash leaves a recoverable state.
- Startup restores the previous workspace from persisted window/tab tables.
- Recovery banner appears when the last checkpoint was not clean.
- Recovery banner supports **Keep** and **Start fresh**.
- Settings now include restore/crash-recovery controls.

## Phase 13 — Memory management

- Added live WebView policy driven by `maxLiveWebViews`.
- Visible/focused tabs are prioritized.
- Background tabs are marked suspended/discarded in Room.
- Minimized tab freezing can be toggled.
- Android memory pressure through `onTrimMemory()` forces an aggressive one-live-WebView policy.
- Discarded tabs show a restore placeholder instead of keeping a WebView alive.
- Window overview shows live/suspended/discarded tab counts.

## Phase 14 — Taskbar, launcher and web apps

- Taskbar now includes a launcher button.
- Launcher includes quick actions: new window, overview, downloads, recently closed, settings and web apps.
- Added persistent `WebAppEntity` records.
- Current site can be installed as a web app.
- Installed web apps launch as fullscreen web-app windows.
- Web apps can be launched or deleted from the launcher or web-app drawer.

## Earlier phases included

- WebView browser foundation.
- Browser engine/session abstraction.
- Room persistence.
- Multiple tabs.
- Detach tabs into windows.
- Floating/internal windows.
- Minimize, restore, maximize and split left/right.
- Per-window chrome modes: Standard, Auto-hide placeholder, Minimal and Fullscreen web app.
- Minimal `•••` command sheet.
- Android Autofill / Google Password Manager compatibility hooks.
- Credential Manager dependencies.
- Console drawer, JavaScript runner and limited network log.
- Remote WebView debugging toggle.
- Site permission interception and persistence.
- Download recording and file upload picker.

## Open in Android Studio

1. Unzip the project.
2. Open the folder in Android Studio.
3. Let Gradle sync.
4. Run the `app` configuration on an Android emulator or device.

The sandbox used to generate this project does not include Gradle, so a full Android build was not executed here. Android Studio may suggest dependency or plugin updates during sync.

## Important implementation notes

- This remains a WebView-first prototype, not a full Chromium fork.
- Web app windows are saved browser-window presets, not native Android apps.
- Crash recovery restores URLs/layout, not exact form state.
- The memory policy destroys WebViews by unmounting them from Compose; it restores by reloading the saved URL.
- Passkey/OAuth behavior still needs device testing because WebView support varies by provider and website.
