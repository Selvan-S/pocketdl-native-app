# Phase 3 — Screen State and Interactions

**Status:** `COMPLETED`

**Completed date:** `2026-10-08`

**Git branch:** `phase-3`

---

## 1. Phase Goal

Turn the existing Jetpack Compose screen shells into maintainable, unidirectional state-driven screens powered by dedicated ViewModels, explicit UI state models, and in-memory mock repositories, without altering the Stitch visual design or implementing real networking, downloading, or database persistence.

---

## 2. What Was Implemented

1. **In-Memory Repositories (`com.pocketdl.app.data.repository`)**:
   - `CapturedMediaRepository`: `observeCapturedMedia()`, `getCapturedMediaById()`, `deleteCapturedMedia()`, `addCapturedMedia()`, `clearAll()`.
   - `DownloadRepository`: `observeDownloads()`, `getDownloadById()`, `pauseDownload()`, `resumeDownload()`, `cancelDownload()`, `retryDownload()`, `startAll()`, `pauseAll()`, `startNow()`, `removeQueued()`, `purgeDownloads()`, `enqueueDownload()`.
   - `ExtensionRepository`: `observeExtensionStatus()`, `toggleConnection()`, `regeneratePairingToken()`, `getPairingToken()`, `updatePort()`.
   - `SettingsRepository`: `observeSettings()`, `setEngine()`, `setMaxParallelDownloads()`, `setAllowCellular()`, `setAutoDetectLinks()`.
   - `StorageRepository`: `observeStorageUsage()`, `recalculateStorage()`.
   - `InMemoryRepositories.kt`: Thread-safe StateFlow implementations and unified `InMemoryRepositoryProvider`.
   - Integrated into `AppContainer` (`DefaultAppContainer`).

2. **Screen ViewModels & Explicit UI State Models**:
   - `HomeScreen`: `HomeViewModel` + `HomeUiState` (URL detection, live active download pause/resume/cancel, recent capture deletion, and feedback).
   - `CapturedScreen`: `CapturedViewModel` + `CapturedUiState` (filtering: All/Video/HLS/Audio, category counts, quality selection bottom sheet, enqueue download, batch download all, item removal).
   - `DownloadsScreen`: `DownloadsViewModel` + `DownloadsUiState` (filtering: All/Active/Completed/Queued/Failed, pause/resume, cancel, retry, dynamic storage header).
   - `QueueScreen`: `QueueViewModel` + `QueueUiState` (queue priority, "Start All", "Pause All", "Start Now" priority boost, "Remove").
   - `MediaDetailsScreen`: `MediaDetailsViewModel` + `MediaDetailsUiState` (typed `mediaId` lookup, extracted streams table, quality option selection, bottom sheet confirmation, download enqueued state).
   - `MediaSnifferScreen`: `MediaSnifferViewModel` + `MediaSnifferUiState` (typed `mediaId` lookup, manifest headers inspection, interactive re-sniff action).
   - `ExtensionConnectionScreen`: `ExtensionViewModel` + `ExtensionUiState` (live socket toggling, pairing token regeneration, listener port display).
   - `SettingsScreen`: `SettingsViewModel` + `SettingsUiState` (engine switching between Native OkHttp / Aria2 / FFmpeg HLS, parallel tasks cycle 1-5, cellular data switch).
   - `StorageCleanupScreen`: `StorageCleanupViewModel` + `StorageCleanupUiState` (multi-item checkbox selection, select all, batch purge, dynamic storage recalculation).
   - `DownloadDetailsScreen`: `DownloadDetailsViewModel` + `DownloadDetailsUiState` (typed `downloadId` lookup, detailed metrics, progress bar, storage destination path, pause/resume/retry/cancel actions).

3. **Dynamic Navigation & Scaffolding**:
   - `MainActivity.kt`: Dynamic badge count on bottom navigation bar Captured tab that reacts in real time to captured inbox items.
   - Typed argument navigation in `AppNavHost.kt` for `mediaId` and `downloadId`.

4. **Testing**:
   - `Phase3ViewModelStateUnitTest.kt`: Unit tests verifying initial state, filtering, selections, action triggers, retry, purge, token generation, and repository updates.

---

## 3. What Was Intentionally NOT Implemented

- Real HTTP/HTTPS download engine or chunk downloading.
- HLS segment streaming or playlist downloading.
- Browser extension socket server (WebSocket/HTTP transport).
- Room database schema or persistent SQLite operations (scheduled for Phase 5).
- Android Foreground Services and background workers (scheduled for Phase 7).
- In-app browser (explicitly forbidden by architecture).
- Any remote backend.

---

## 4. Files / Areas Changed

```text
android-app/app/build.gradle.kts
android-app/app/src/main/java/com/pocketdl/app/MainActivity.kt
android-app/app/src/main/java/com/pocketdl/app/core/di/AppContainer.kt
android-app/app/src/main/java/com/pocketdl/app/data/repository/CapturedMediaRepository.kt
android-app/app/src/main/java/com/pocketdl/app/data/repository/DownloadRepository.kt
android-app/app/src/main/java/com/pocketdl/app/data/repository/ExtensionRepository.kt
android-app/app/src/main/java/com/pocketdl/app/data/repository/SettingsRepository.kt
android-app/app/src/main/java/com/pocketdl/app/data/repository/StorageRepository.kt
android-app/app/src/main/java/com/pocketdl/app/data/repository/InMemoryRepositories.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/home/HomeUiState.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/home/HomeViewModel.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/home/HomeScreen.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/captured/CapturedUiState.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/captured/CapturedViewModel.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/captured/CapturedScreen.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/downloads/DownloadsUiState.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/downloads/DownloadsViewModel.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/downloads/DownloadsScreen.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/queue/QueueUiState.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/queue/QueueViewModel.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/queue/QueueScreen.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/media_details/MediaDetailsUiState.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/media_details/MediaDetailsViewModel.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/media_details/MediaDetailsScreen.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/analysis/MediaSnifferUiState.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/analysis/MediaSnifferViewModel.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/analysis/MediaSnifferScreen.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/extension/ExtensionUiState.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/extension/ExtensionViewModel.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/extension/ExtensionConnectionScreen.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/settings/SettingsUiState.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/settings/SettingsViewModel.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/settings/SettingsScreen.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/storage/StorageCleanupUiState.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/storage/StorageCleanupViewModel.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/storage/StorageCleanupScreen.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/download_details/DownloadDetailsUiState.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/download_details/DownloadDetailsViewModel.kt
android-app/app/src/main/java/com/pocketdl/app/ui/screens/download_details/DownloadDetailsScreen.kt
android-app/app/src/test/java/com/pocketdl/app/Phase3ViewModelStateUnitTest.kt
docs/07_PROJECT_STATUS.md
docs/handoffs/PHASE_3_HANDOFF.md
```

---

## 5. Architecture Changes

**None.**
Follows ADR-006 (UI must not own download/data logic), ADR-009 (Coroutines + StateFlow for observable UI state), and unidirectional state flow.

---

## 6. Dependencies Added / Removed

### Added

- `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1` (testImplementation) — required for testing coroutine flows and ViewModels in unit tests.

### Removed

- None.

---

## 7. Automated Verification

```text
./gradlew test                  ✅ (PASSING - 100% pass rate)
./gradlew assembleDebug        ✅ (BUILD SUCCESSFUL in 8s)
```

---

## 8. Manual Verification

### Device / Emulator

- Device: Physical Samsung Android device (connected via wireless ADB)
- Android version: Android 14 / 15
- Method: `adb install -r app/build/outputs/apk/debug/app-debug.apk` followed by `adb shell am start -n com.pocketdl.app/.MainActivity`

### Verified

- [x] App launches cleanly without crashes or errors
- [x] Home screen displays active download, extension status, and captured items
- [x] Captured screen filtering (All, Video, HLS, Audio) filters items correctly
- [x] Quality selection modal bottom sheet opens, accepts stream selection, and adds task to download repository
- [x] Dynamic badge counter on bottom navigation bar updates upon addition/deletion
- [x] Downloads screen filtering (All, Active, Completed, Queued) works cleanly
- [x] Queue screen "Start All" and "Pause All" actions update queue tasks
- [x] Storage cleanup checkboxes allow multi-item selection and purging with storage recalculation
- [x] Settings screen toggles engine, cellular data, and concurrency limits
- [x] Extension connection screen regenerates pairing token and toggles socket connection
- [x] Download details screen presents technical specs, storage path, and actions
- [x] Media sniffer analysis screen displays extracted headers and re-sniff loading state

---

## 9. Known Issues

| Issue | Severity | Workaround | Follow-up |
|---|---|---|---|
| None | N/A | N/A | N/A |

---

## 10. Important Decisions Made During This Phase

- Used isolated in-memory repositories implementing clean interfaces (`CapturedMediaRepository`, `DownloadRepository`, etc.) rather than dummy ViewModel variables, ensuring Phase 5 (Room) can swap the data access implementation without touching UI or ViewModel logic.
- Implemented unidirectional state flow: Composables only render immutable UI state and emit high-level user event callbacks.

---

## 11. Next Phase

**Phase:** Phase 4 / Phase 5 — Room Persistence

### Expected starting point

All 10 screen destinations are functional, reactive, and driven by ViewModels and repositories. The next phase will implement Room entities, DAOs, and repository implementations to persist captured media and download tasks across app restarts.

---

## 12. Notes for the Next AI Chat

```text
Phase 3 is complete and verified on a physical device.
All screens use ViewModels and StateFlows.
Do not redesign the UI or introduce any custom design tokens.
Do not implement the downloader engine before Room persistence is in place.
```
