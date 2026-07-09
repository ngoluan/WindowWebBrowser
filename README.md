# WindowWeb Browser — Phase 1–14 Starter

WindowWeb is a Chromium WebView-based Android browser prototype that treats tabs as movable browser windows inside a phone-friendly workspace.

This package includes the phase 1–11 implementation plus phases 12–14:

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
