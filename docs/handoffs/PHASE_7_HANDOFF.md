# Phase 7 — Background Execution & Notifications Handoff

**Status:** `COMPLETED`

**Completed date:** `2026-10-10`

**Git commit:** `d124d2c`

**Git commit message:** `feat(download): implement DownloadService and real-time foreground notification progress`

---

## 1. Phase Goal

Transition PocketDL from foreground-only UI downloading to platform-compliant background execution using native Android foreground services (`dataSync` type, target SDK 37, compile SDK 37, min SDK 24). Ensure that active downloads continue reliably when the UI is backgrounded, with real-time notification shade progress updates (percentage, MB transferred, speed, ETA) and safe lifecycle teardown.

---

## 2. What Was Implemented

- **Manifest Permissions & Component Setup**:
  - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` declared in `AndroidManifest.xml`.
  - `DownloadService` declared with `android:foregroundServiceType="dataSync"`.
  - `DownloadActionReceiver` declared for background notification intent handling.
- **Notification Subsystem (`DownloadNotificationManager`)**:
  - `pocketdl_downloads` notification channel (`IMPORTANCE_LOW`, no badge).
  - Ongoing progress notification with progress bar (0–100%), downloaded/total MB text, speed, ETA, and actions ("Pause"/"Cancel" for single task, "Pause All"/"Resume All" for multiple tasks).
  - Terminal completion alert (`buildCompletionNotification`) and failure alert (`buildFailureNotification`).
- **Foreground Service Host (`DownloadService`)**:
  - `dataSync` foreground service elevation via `startForeground()`.
  - Android 15 (API 35+) `onTimeout(startId)` and `onTimeout(startId, fgsType)` bounded timeout callbacks that immediately cancel in-memory active jobs, fire a non-blocking best-effort Room write, and invoke `stopSelf()` promptly.
  - Safe idle shutdown in `observeActiveJobs()` that guards against initial zero emission and suppresses `stopSelf()` while a new start attempt is in progress.
  - Real-time notification updates observing `activeNotificationState` from `DownloadCoordinator`.
- **Download Coordinator Integration (`DownloadCoordinator`)**:
  - Single execution manager owned in application scope by `DefaultAppContainer`.
  - Session token tracking (`serviceStartSessionId`) to coordinate concurrent task requests into a single startup attempt and discard stale callbacks from older sessions.
  - Bounded 8-second startup wait with deterministic rollback to `PAUSED (Foreground Service Timeout)` on timeout or failure.
  - Terminal state protection (`COMPLETED` and `FAILED` cannot be overwritten by racing pause actions).
  - Task resurrection prevention (cancelled/deleted tasks cannot be re-inserted by late engine completions).
  - Guaranteed per-task registry cleanup using `try ... finally { onTaskTerminated(taskId) }`.
  - Real-time `activeNotificationState` StateFlow emitting live progress snapshots throttled to 500ms intervals by the engine.
  - Standardized `startAll()` and `startNow()` to route through `startTask()` for reliable foreground service elevation.

---

## 3. What Was Intentionally NOT Implemented

- **In-App Runtime `POST_NOTIFICATIONS` Permission Dialog**:
  - The manifest declares `POST_NOTIFICATIONS`, but automatic in-app Compose permission requesting / rationale dialogs were deferred to UI Polish / Phase 8.
- **HLS Stream Background Downloading**:
  - HLS engine is scheduled for Phase 11; Phase 7 strictly covers direct HTTP/HTTPS streaming downloads.
- **Secondary Execution Frameworks**:
  - No WorkManager database, secondary queue, or duplicate active job registry was introduced.

---

## 4. Files / Areas Changed

```text
android-app/app/src/main/AndroidManifest.xml
android-app/app/src/main/java/com/pocketdl/app/core/di/AppContainer.kt
android-app/app/src/main/java/com/pocketdl/app/download/DownloadCoordinator.kt
android-app/app/src/main/java/com/pocketdl/app/download/DownloadActionHandler.kt
android-app/app/src/main/java/com/pocketdl/app/download/notification/DownloadNotificationManager.kt
android-app/app/src/main/java/com/pocketdl/app/download/service/DownloadActionReceiver.kt
android-app/app/src/main/java/com/pocketdl/app/download/service/DownloadService.kt
android-app/app/src/main/java/com/pocketdl/app/download/service/ServiceForegroundResult.kt
android-app/app/src/test/java/com/pocketdl/app/download/DownloadActionReceiverUnitTest.kt
android-app/app/src/test/java/com/pocketdl/app/download/DownloadNotificationManagerUnitTest.kt
android-app/app/src/test/java/com/pocketdl/app/download/DownloadCoordinatorOrderingUnitTest.kt
```

---

## 5. Architecture Changes

Documented in `docs/08_ARCHITECTURE_DECISIONS.md`:
- **ADR-017**: Native Android Foreground Service for Active Downloads (`dataSync`).
- **ADR-018**: Single Execution Owner with Startup Ordering Barrier and Session Token Tracking.

---

## 6. Dependencies Added / Removed

### No changes
No third-party libraries or dependencies were added. Uses AndroidX Core KTX, Jetpack Compose, OkHttp 5.5.0, and Room.

---

## 7. Automated Verification

```text
./gradlew testDebugUnitTest          ✅ PASSING (83 tests, 0 failures, 100% success)
./gradlew assembleDebug              ✅ PASSING (Build successful)
```

Test breakdown:
- `DownloadCoordinatorOrderingUnitTest`: 9 tests (barrier ordering, failure rollback, concurrent sharing, stale callback rejection, session teardown, startup timeout, pause vs completion race, cancel resurrection prevention, registry cleanup).
- `DownloadActionReceiverUnitTest`: 7 tests (action parsing, intent dispatch, stale task validation).
- `DownloadNotificationManagerUnitTest`: 1 test (channel creation, notification construction).
- `DownloadCoordinatorUnitTest`: 10 tests (concurrency limits, auto-advance, duplicate prevention, resume).
- `OkHttpDownloadEngineUnitTest`: 10 tests (range resume, ETag matching, throttle, cancel).
- `DownloadFileUtilsTest`: 17 tests (naming collisions, part file resolution, sanitization).
- Pre-existing unit tests (Room, ViewModels, Foundation): 29 tests.

---

## 8. Manual Verification

### Device / Emulator
* **Device**: Physical Samsung Galaxy S22 Ultra
* **Connection**: Wireless ADB (`192.168.0.105:35189`)
* **Android version**: Android 14 (API 34)

### Verified
- [x] App launches cleanly via APK stream install (`adb install -r`).
- [x] Downloads start and transition process to foreground service (`dataSync`).
- [x] Ongoing notification appears in notification shade.
- [x] Real-time download progress bar, downloaded/total size, transfer speed, and ETA update accurately.
- [x] Background execution continues when the app is minimized or the screen is locked.
- [x] Notification channel correctly configured as `pocketdl_downloads`.

---

## 9. Known Issues & Outstanding Risks

| Issue | Severity | Workaround | Follow-up |
|---|---|---|---|
| In-app `POST_NOTIFICATIONS` runtime permission prompt missing | Medium | User must grant notification permission in OS settings or via ADB. | Add runtime permission request flow in UI (Phase 8/UI polish). |
| Android 15 6-hour cumulative background limit | Low | Supported via `onTimeout()` callbacks; downloads gracefully paused. | Verify on physical Android 15 (API 35+) device once hardware is available. |

---

## 10. Next Phase

**Phase:** `Phase 8 — Download Manager Hardening`

### Expected starting point
- Single coordinator owner (`DownloadCoordinator`) handling active job concurrency and execution.
- Native `DownloadService` foreground service running with real-time progress notifications.
- Reliable background download continuation and status recovery.

---
