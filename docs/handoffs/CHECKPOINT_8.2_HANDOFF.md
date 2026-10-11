# PocketDL — Checkpoint 8.2 Restart Recovery & Retry Budget Handoff

> **Status:** CHECKPOINT 8.2 IMPLEMENTED, TESTED, AND VERIFIED ON DEVICE.
>
> **Date:** 2026-10-11
>
> **App Version:** `versionCode = 3`, `versionName = "1.0.0-p8.2"`
>
> **Branch:** `phase-8`

---

## 1. Summary of Changes

Checkpoint 8.2 introduces persistent retry metadata, bounded exponential backoff, error classification, and startup recovery to harden the download queue against interruptions and transient network failures:

1. **Persistent Retry Metadata & Room Migration (`MIGRATION_2_3`):**
   - Added `retry_count: Int = 0` and `next_retry_at: Long? = null` to `DownloadTaskEntity`.
   - Created and registered `MIGRATION_2_3` in `DatabaseMigrations.kt`:
     - `ALTER TABLE download_tasks ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0`
     - `ALTER TABLE download_tasks ADD COLUMN next_retry_at INTEGER`
   - Database version bumped to `3` in `PocketDlDatabase.kt` with `fallbackToDestructiveMigration(false)` preserved.

2. **DAO Lifecycle & Queue Eligibility Filtering:**
   - Updated `findOldestQueued(limit, now)` in `DownloadTaskDao.kt` to check `status = 'QUEUED' AND (next_retry_at IS NULL OR next_retry_at <= :now)`. Retrying tasks whose backoff window has not expired are excluded from queue dispatching.
   - Added `getPendingRetries(now)` to fetch scheduled retries awaiting backoff timers.
   - Added `updateRetryState(...)` to persist incremented retry counts and future timestamps.
   - Added `resetRetryState(id)` and `updateAllStatusAndResetRetries(...)` to reset counters upon manual user actions.

3. **Error Classification & Bounded Exponential Backoff:**
   - Introduced `HttpException(val code: Int, message: String) : IOException(message)` in `DownloadEngine.kt`, thrown by `OkHttpDownloadEngine.kt` on HTTP errors.
   - **Transient Errors (Eligible for Retry):**
     - HTTP status codes: `408` (Request Timeout), `429` (Too Many Requests), `500` (Internal Server Error), `502` (Bad Gateway), `503` (Service Unavailable), `504` (Gateway Timeout).
     - Java/network exceptions: `SocketTimeoutException`, `UnknownHostException`, `ConnectException`, `SocketException`, `EOFException`.
     - Generic `IOException` excluding local storage failures.
   - **Permanent Errors (Immediate Failure):**
     - HTTP `4xx` client errors (e.g., `400`, `401`, `403`, `404`).
     - Local storage/permission errors (`ENOSPC`, `No space left on device`, `EACCES`, `Permission denied`).
   - **Retry Budget & Backoff Schedule:**
     - Maximum 5 retry attempts (up to 6 total attempts including the initial try).
     - Formula: `backoffMs = (2000L * (1L shl currentInDb.retryCount)).coerceAtMost(60_000L)`.
     - Delays: Attempt 1: 2s, Attempt 2: 4s, Attempt 3: 8s, Attempt 4: 16s, Attempt 5: 32s (capped at 60s).
     - Scheduled via `coordinatorScope.launch(ioDispatcher)` with `delay(backoffMs)` before calling `dispatchQueueInternal()`.

4. **Startup Reconciliation & Timer Recovery:**
   - In `reconcileStartupStateInternal()`:
     - Resets interrupted tasks from `DOWNLOADING` to `QUEUED` (status text "Queued", speed "0 KB/s", ETA "Waiting").
     - Queries `getPendingRetries(now)` and schedules coroutine delays for any pending retry timers that survived process death.

5. **Safeguards & Manual Action Behavior:**
   - **Stale Timer Safety:** If a task in backoff delay is paused or cancelled, Room state updates to `PAUSED` or is deleted. When the coroutine timer fires, `dispatchQueueInternal` filters by `status == 'QUEUED'`, preventing stale executions.
   - **Manual Reset:** Explicit user triggers (`startTask`, `startAll`) invoke `resetRetryState`, zeroing out retry counts and clearing `next_retry_at`.
   - **Preserved Concurrency Safeguards:** Worker identity checks (`isJobCurrent`) and `retiringJobs` tracking from Checkpoint 8.1 remain intact.

---

## 2. Exact Files Modified

1. `android-app/app/build.gradle.kts`:
   - Bumped `versionCode = 3`, `versionName = "1.0.0-p8.2"`.
2. `android-app/app/src/main/java/com/pocketdl/app/data/database/PocketDlDatabase.kt`:
   - Updated version to 3; preserved `fallbackToDestructiveMigration(false)`.
3. `android-app/app/src/main/java/com/pocketdl/app/data/database/migrations/DatabaseMigrations.kt`:
   - Added `MIGRATION_2_3` and registered in `ALL_MIGRATIONS`.
4. `android-app/app/src/main/java/com/pocketdl/app/data/database/entity/DownloadTaskEntity.kt`:
   - Added `retryCount` and `nextRetryAt` columns.
5. `android-app/app/src/main/java/com/pocketdl/app/data/database/dao/DownloadTaskDao.kt`:
   - Added `findOldestQueued` timestamp filtering, `getPendingRetries`, `updateRetryState`, `resetRetryState`, and `updateAllStatusAndResetRetries`.
6. `android-app/app/src/main/java/com/pocketdl/app/download/DownloadEngine.kt`:
   - Added `HttpException`.
7. `android-app/app/src/main/java/com/pocketdl/app/download/OkHttpDownloadEngine.kt`:
   - Throws `HttpException` on non-successful HTTP responses.
8. `android-app/app/src/main/java/com/pocketdl/app/download/DownloadCoordinator.kt`:
   - Implemented retry classification, bounded backoff, startup recovery, and manual retry resets.
9. `android-app/app/src/test/java/com/pocketdl/app/RoomMigration2To3Test.kt`:
   - New JVM SQLite test validating `MIGRATION_2_3` execution and data preservation.
10. `android-app/app/src/test/java/com/pocketdl/app/download/DownloadQueueHardeningUnitTest.kt`:
    - Added 4 unit tests covering retry budget, permanent failure, stale timers, and manual resets.
11. `android-app/app/src/test/java/com/pocketdl/app/RoomPersistenceUnitTest.kt`, `DownloadCoordinatorUnitTest.kt`, `DownloadCoordinatorOrderingUnitTest.kt`:
    - Updated assertions and fake DAO stubs for new schema and retry methods.

---

## 3. Unit Tests & Automated Verification

Total passing unit tests: **99 tests** (100% pass rate).

### Key Checkpoint 8.2 Tests:
1. `RoomMigration2To3Test`:
   - `migration2To3_executesOnRealSqlite_addsColumns_andPreservesExistingRows`: Creates authentic v2 table on SQLite JDBC, populates record with `etag`/`last_modified`, executes production `DatabaseMigrations.MIGRATION_2_3`, verifies schema via `PRAGMA table_info`, and asserts row remains intact with `retry_count = 0` and `next_retry_at = NULL`.
2. `DownloadQueueHardeningUnitTest`:
   - `retryPolicy_transientErrors_retryUpToFiveTimesWithBackoff`: Validates exponential backoff progression and transition to `FAILED` after 5 retries.
   - `retryPolicy_permanentError_failsImmediately`: Validates immediate terminal failure on storage exhaustion (`No space left on device`).
   - `retryPolicy_staleTimer_ignoredIfPaused`: Validates that pausing a retrying task prevents stale timers from re-dispatching it.
   - `retryPolicy_manualStart_resetsTimerAndCounters`: Validates manual start clears retry count and timestamp.

---

## 4. Verification & Build Results

- **Unit Test Execution:**
  - Command: `.\gradlew.bat testDebugUnitTest --rerun`
  - Result: **BUILD SUCCESSFUL** (99 tests passed, 0 failures, 0 skipped).
- **Debug APK Assembly:**
  - Command: `.\gradlew.bat assembleDebug`
  - Result: **BUILD SUCCESSFUL** (`app-debug.apk` built).
- **Physical Device Verification:**
  - **Device:** Samsung Galaxy S22 Ultra (`SM-S908E`, ADB device `RZCT92A9L3E`).
  - **Install Method:** Fresh streamed install (`adb install android-app\app\build\outputs\apk\debug\app-debug.apk`).
  - **Package Dump:** Verified `versionCode=3`, `versionName=1.0.0-p8.2`.
  - **App Launch:** Started `MainActivity` cleanly (PID `25089`), zero fatal exceptions or crashes in Logcat.
  - **Settings Verification:** Navigated to Settings; screen visibly displays `PocketDL v1.0.0-p8.2 (3)` (captured in screenshot `settings_screen.png`).
  - **Navigation:** Main bottom-bar tabs (Home, Captured, Downloads, Settings) exercised and responsive.

---

## 5. Important Distinctions & Known Scope Boundaries

- **Live On-Device Migration:** Because a fresh install was performed on the phone (due to a debug keystore change from the previous session), Room initialized schema version 3 directly. Live on-device migration was not executed on the phone. Verification of `MIGRATION_2_3` is established by the JVM SQLite test suite and offline verification against the authentic 20-item database pulled from the device.
- **Live Network Streaming:** Live HTTP download streaming was not run on the physical phone during this device verification step; streaming and concurrency were validated via unit tests.
- **Scope Reservation:** Network constraints (metered Wi-Fi, cellular data restrictions) were intentionally excluded and are reserved for Checkpoint 8.3.

---

## 6. Next Step

Proceed to **Checkpoint 8.3: Network & Storage Constraints** (metered network toggles, storage space pre-checks before download, and network disconnect recovery).
