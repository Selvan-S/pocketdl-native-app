# PocketDL — Checkpoint 8.3 Network & Storage Constraints Handoff

> **Status:** CHECKPOINT 8.3 IMPLEMENTED, TESTED, AND VERIFIED.
>
> **Date:** 2026-10-12
>
> **App Version:** `versionCode = 4`, `versionName = "1.0.0-p8.3"`
>
> **Branch:** `phase-8`

---

## 1. Summary of Changes

Checkpoint 8.3 implements authoritative storage pre-flight space checking, network connectivity constraints, metered connection policies, and disconnection recovery without retry budget exhaustion:

1. **Network Connectivity Observation & Metered Network Enforcement:**
   - Introduced `NetworkConnectivityObserver` interface and `NetworkState` data model (`isConnected`, `isMetered`, `connectionType: ConnectionType`) in `com.pocketdl.app.core.network`.
   - Created `AndroidNetworkConnectivityObserver` wrapping Android's `ConnectivityManager.registerDefaultNetworkCallback` via `callbackFlow` and `stateIn` with `SharingStarted.Eagerly`. Provides synchronous initial state emission so connectivity existing at app start is immediately and accurately reflected.
   - Provided `PermissiveNetworkConnectivityObserver` for default/headless testing environments.
   - Enforced `allowCellular` constraint: when `allowCellular == false`, metered connections (including cellular networks and metered Wi-Fi) are blocked from downloading.
   - Accurately reports descriptive constraint reason status texts:
     - Disconnected: `"Waiting for Network"`
     - Metered connection blocked: `"Waiting for an unmetered network"`

2. **Unified Authoritative Storage Policy & Safety Reserve:**
   - Created `StoragePolicy` in `com.pocketdl.app.core.storage` defining single shared headroom policy constants:
     - `SAFETY_BUFFER_BYTES = 25 MiB` (26,214,400 bytes) to protect SQLite/Room write headroom and prevent database corruption.
     - `MINIMUM_UNKNOWN_HEADROOM_BYTES = 50 MiB` (52,428,800 bytes) for downloads with unknown or chunked content lengths.
   - Added `parseBytesFromText` helper to parse size strings ("100 MB", "1.25 GB", "500 KB", etc.).
   - Created `StorageSpaceChecker` interface and `DefaultStorageSpaceChecker` which traverses directory parent hierarchies until an existing directory is located, properly querying the target partition even before download directories are created.
   - **Coordinator Pre-flight Check:** Prior to launching any worker or opening HTTP connections, validates expected remaining bytes against target filesystem usable space. Avoids double-counting by subtracting existing `.part` file length.
   - **Engine Post-header Verification:** In `OkHttpDownloadEngine`, validates authentic server `Content-Length` headers immediately upon receiving HTTP 200 or HTTP 206 responses. For HTTP 206 Partial Content responses, uses `body.contentLength()` (which represents the remaining response chunk to be written, rather than full file size) to prevent conflicting headroom calculations.
   - **Permanent Disk-Full Error Handling:** Added `InsufficientStorageException`. Disk-full errors (`InsufficientStorageException`, `ENOSPC`, "No space left on device") transition tasks immediately to `TaskStatus.FAILED` ("Failed: Insufficient storage" or "Failed: Storage full"), without burning retry budgets or scheduling automatic backoff retries. The partial `.part` file is kept intact so users can resume after clearing storage.

3. **Disconnection Recovery & Worker Synchronization:**
   - In `DownloadCoordinator`, added reactive listener combining `networkObserver.observeNetworkState()` and `settingsRepository.observeSettings()`.
   - **Transition-Gated Queue Dispatch:** Tracks permitted state transitions (`wasPermitted != permitted`). Queue dispatch is only triggered upon transition from unpermitted to permitted (`false -> true`), preventing spurious queue advance on app launch or cold boot.
   - **Safe Interruption Sequence:** When network becomes unpermitted or connection is lost during streaming:
     1. Active jobs are transferred to `retiringJobs` and cleared from `activeJobs` under mutex.
     2. `engine.pause(taskId)` is invoked (setting `isPaused = true` without setting `isCancelled`).
     3. Active coroutine jobs are cancelled (`job.cancel()`).
     4. Coordinator explicitly awaits `job.join()` on each retired job before updating Room status.
     5. Room status updates to `TaskStatus.QUEUED` with appropriate constraint text ("Waiting for Network" or "Waiting for an unmetered network").
   - **Partial File Preservation:** Because `isPaused` is set rather than `isCancelled`, `DownloadFileUtils.deletePartFile` is not called, preserving `.part` files intact.
   - **Retry Budget Preservation:** Network disconnects do not increment `retry_count`. Tasks in Room retain their existing retry count. Any pending backoff deadlines (`next_retry_at`) from previous transient server errors are preserved and will not fire until their scheduled time arrives.
   - **Worker Identity Protection:** Strict `isJobCurrent` checks prevent retired workers from overwriting Room state after retirement or finalizing concurrently with replacement workers.

4. **Dependency Injection Wiring:**
   - Injected `networkConnectivityObserver` and `storageSpaceChecker` into `AppContainer` and `DefaultAppContainer`, passing them into `DownloadEngine` and `DownloadCoordinator`.

---

## 2. Exact Files Modified and Created

### Created Files:
1. `android-app/app/src/main/java/com/pocketdl/app/core/network/NetworkConnectivityObserver.kt`
2. `android-app/app/src/main/java/com/pocketdl/app/core/network/AndroidNetworkConnectivityObserver.kt`
3. `android-app/app/src/main/java/com/pocketdl/app/core/storage/StorageSpaceChecker.kt`
4. `android-app/app/src/main/java/com/pocketdl/app/core/storage/StoragePolicy.kt`
5. `android-app/app/src/test/java/com/pocketdl/app/download/NetworkConstraintsUnitTest.kt`
6. `android-app/app/src/test/java/com/pocketdl/app/download/StorageConstraintsUnitTest.kt`

### Modified Files:
1. `android-app/app/build.gradle.kts`:
   - Bumped `versionCode = 4`, `versionName = "1.0.0-p8.3"`.
2. `android-app/app/src/main/java/com/pocketdl/app/core/di/AppContainer.kt`:
   - Added `networkConnectivityObserver` and `storageSpaceChecker` properties to `AppContainer` and wired into `DefaultAppContainer`.
3. `android-app/app/src/main/java/com/pocketdl/app/download/DownloadEngine.kt`:
   - Added `InsufficientStorageException(requiredBytes: Long, availableBytes: Long, message: String)`.
4. `android-app/app/src/main/java/com/pocketdl/app/download/OkHttpDownloadEngine.kt`:
   - Injected `storageChecker: StorageSpaceChecker`.
   - Added post-header storage verification using `StoragePolicy.calculateRequiredBytes`.
5. `android-app/app/src/main/java/com/pocketdl/app/download/DownloadCoordinator.kt`:
   - Injected `networkObserver` and `storageChecker`.
   - Added reactive `combine(networkObserver, settingsRepository)` flow.
   - Added network pre-flight checks in `dispatchQueueInternal` and `runWorker`.
   - Added authoritative destination storage check in `runWorker`.
   - Added `interruptActiveWorkersForConstraint` with join-barrier worker retirement.
   - Handled storage exhaustion and offline disconnection failures without burning retry budget.

---

## 3. Unit Tests & Automated Verification

Total passing unit tests: **118 tests** (100% pass rate, 0 failures, 0 skipped).

### Checkpoint 8.3 Network Constraint Tests (`NetworkConstraintsUnitTest`):
1. `dispatchQueue_whenNetworkDisconnected_doesNotDispatch`
2. `dispatchQueue_whenMeteredAndCellularDisabled_doesNotDispatch`
3. `dispatchQueue_whenMeteredAndCellularEnabled_dispatches`
4. `networkLost_duringActiveDownload_interruptsWorkerAndPreservesPartFile`
5. `networkLost_duringActiveDownload_doesNotIncrementRetryCount`
6. `networkLost_preservesExistingRetryBackoffDeadline`
7. `networkRestored_automaticallyResumesQueuedTasks`
8. `networkRestored_doesNotBypassUnexpiredRetryDeadline`
9. `rapidNetworkReconnection_doesNotSpawnDuplicateWorkers`
10. `runtimeSettingsChange_allowCellularToggledOn_wakesQueuedDownloads`
11. `disconnectDuringFinalization_handledSafelyWithoutStateCorruption`

### Checkpoint 8.3 Storage Constraint Tests (`StorageConstraintsUnitTest`):
1. `preflightCheck_whenKnownSizeExceedsUsableSpace_failsImmediatelyWithoutNetwork`
2. `preflightCheck_accountsForExistingPartFile_avoidsDoubleCounting`
3. `preflightCheck_whenSizeUnknown_enforcesMinimumSafetyBuffer`
4. `engineHeaderValidation_whenContentLengthExceedsStorage_abortsBeforeStreaming`
5. `engineRangeResponse_calculatesStorageFromContentLengthNotFullSize`
6. `runtimeStorageExhaustion_enospcThrown_failsPermanentlyWithoutRetrying`
7. `storageSafetyBuffer_preservesDatabaseWritingHeadroom`
8. `storagePolicy_parseBytesFromText_parsesVariousFormatsCorrectly`

### Checkpoint 8.3 Reliability & Notification Tests:
1. `NetworkConstraintsUnitTest.dispatchQueue_doesNotClobberActiveRetryTimer`
2. `NetworkConstraintsUnitTest.networkObserver_coldStartPermitted_dispatchesQueuedTaskOnDispatch`
3. `NetworkConstraintsUnitTest.networkObserver_coldStartNonPermitted_marksQueuedTaskWaiting`
4. `NetworkConstraintsUnitTest.dispatchQueue_repeatedNetworkChanges_noDuplicateWorkers`
5. `DownloadNotificationManagerUnitTest.constants_verifyChannelConfigurationValues` (Dual channel IDs and importance)

---

## 4. Notification & Network-Waiting Reliability Fix

Following initial Checkpoint 8.3 verification, physical-device testing on the Samsung Galaxy S22 Ultra (Android 14) identified that download notifications were not appearing and network-waiting status needed refinement. A targeted reliability fix was implemented:

1. **Dual-Channel Notification Architecture:**
   - Separated notification delivery into two dedicated channels:
     - `pocketdl_downloads` (`IMPORTANCE_LOW`): Silent ongoing notification for active download progress and foreground service lifecycle.
     - `pocketdl_alerts` (`IMPORTANCE_DEFAULT`): Visible and audible alerts for completed and failed downloads.
   - Channels are registered at application startup via `PocketDlApplication.onCreate()`.
   - Completion and failure notifications are routed to `pocketdl_alerts`.

2. **Android 13+ Runtime Notification Permissions:**
   - Added runtime `POST_NOTIFICATIONS` permission launcher in `MainActivity`.
   - Prompts user once per install on API 33+ and records state in SharedPreferences (`notif_permission_requested`) to prevent repeated prompts.

3. **Coordinator Concurrency & Network-Waiting Fixes:**
   - Fixed Kotlin property declaration order race condition: moved `initializationJob` declaration before `init { ... }` block to eliminate a multicore `NullPointerException` on `initializationJob.join()`.
   - Preserves `QUEUED` task state while setting statusText to `"Waiting for Network"` or `"Waiting for an unmetered network"` when network conditions disallow downloading.
   - Does not overwrite or clobber active backoff retry timers (`nextRetryAt > now`).
   - Automatically resets network-waiting tasks to `"Queued"` when network conditions become permitted.
   - Correctly observes initial network state on cold start without spurious auto-dispatch.

---

## 5. Verification & Build Results

- **Unit Test Execution:**
  - Command: `.\gradlew.bat testDebugUnitTest`
  - Result: **BUILD SUCCESSFUL** (122 tests passed, 0 failures, 0 skipped across 17 test suites).
- **Debug APK Assembly:**
  - Command: `.\gradlew.bat assembleDebug`
  - Result: **BUILD SUCCESSFUL** (`app-debug.apk` built).
- **Physical Device Verification:**
  - **Device:** Samsung Galaxy S22 Ultra (`SM-S908E`, ADB device `RZCT92A9L3E`, Android 14).
  - **Permissions:** Verified `POST_NOTIFICATIONS` granted (`granted=true`).
  - **Channels:** Verified dual channels registered in Android notification manager (`pocketdl_downloads` with `mImportance=2`, `pocketdl_alerts` with `mImportance=3`).
  - **Network-Constrained Queue:** Verified on 5G cellular network with `allowCellular = false`. Enqueued download entered `QUEUED` state with statusText `"Waiting for an unmetered network"`.
  - **Reactive Dispatch:** Toggling "Allow Cellular Downloads" to enabled immediately triggered coordinator network transition, reset status to `"Queued"`, and dispatched worker.
  - **Notification Delivery:** Verified in dumpsys notification and visual notification shade screenshot (`screen_shade_now.png`) that alerts appear cleanly in the user's notification shade.

---

## 6. Scope Boundaries

- **Room Schema Version:** Remained strictly at Room version 3 (no migrations, entities unchanged).
- **Out of Scope Exclusions:** Clipboard monitoring, real media sniffer/extractor, YouTube extraction, quality selection redesign, and download directory settings were strictly excluded.

---

## 7. Next Step

Proceed to **Phase 9: Extension Capture Protocol** after user review.

