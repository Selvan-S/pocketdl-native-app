# PocketDL — Checkpoint 8.1 Concurrency Corrections Handoff

> **Status:** CHECKPOINT 8.1 CONCURRENCY CORRECTIONS IMPLEMENTED AND VALIDATED.  
> **Date:** 2026-10-10  
> **App Version:** `versionCode = 2`, `versionName = "1.0.0-p8.1"`  
> **Branch:** `phase-8`  

---

## 1. Summary of Changes

Two critical concurrency concerns identified in the review of `DownloadCoordinator.kt` were resolved with minimal, lifecycle-safe corrections:

1. **Completion Cleanup During Coordinator Shutdown:**
   - **Root Cause:** `cleanupScope` was originally created as `CoroutineScope(SupervisorJob(coordinatorScope.coroutineContext[Job]) + ioDispatcher)`. When `coordinatorScope` was cancelled, `cleanupScope` was cancelled simultaneously, preventing lazy jobs' `invokeOnCompletion` callbacks from launching their cleanup coroutines.
   - **Resolution:** Decoupled `cleanupScope` to use an independent `cleanupJob = SupervisorJob()`. Registered an `invokeOnCompletion` hook on `coordinatorScope.coroutineContext[Job]` that executes a `NonCancellable` block under `mutex.withLock` to clear all in-memory maps (`activeJobs`, `reservedTasks`, `retiringJobs`), resets `_activeJobsCount.value = 0`, resets foreground and notification states, and finally cancels `cleanupJob`.
   - **Safety:** Prevented new work from being dispatched into a cancelled scope by checking `coordinatorScope.isActive` in `lazyJob.invokeOnCompletion`. Ensured cleanup scope cannot leak.

2. **Worker Identity Enforcement & Concurrent Writer Prevention:**
   - **Worker Identity:** In `runWorker`, the coroutine's own `Job` is identified (`val currentJob = currentCoroutineContext()[Job]`). Verified `activeJobs[taskId] === currentJob` before starting download work, in `onProgress`, and before applying terminal Room updates (`COMPLETED`, `FAILED`, `PAUSED`). If an outdated worker detects identity mismatch, it immediately exits without mutating Room or notification state.
   - **Concurrent Writer Prevention:** Introduced `retiringJobs: ConcurrentHashMap<String, Job>`. When `pauseTask`, `cancelTask`, or `pauseAll` cancels an active worker, the job is transferred to `retiringJobs` under mutex. Outside the mutex, `jobToCancel?.join()` waits until the worker coroutine completely unwinds and closes its underlying file descriptor before `retiringJobs` removes the entry. In `cancelTask`, the `.part` file is deleted strictly after `join()` completes.
   - **Queue Protection:** `dispatchQueueInternal` and `startTask` check `!retiringJobs.containsKey(taskId)` (or await `retiringJob.join()`), guaranteeing a replacement worker can never write to a `.part` file while an old worker is still unwinding.
   - **Mutex-Safe Room I/O:** Removed all coordinator mutex locks from around Room operations (`findById`, `updateProgress`, `updateStatus`).

---

## 2. Exact Files Modified

1. `android-app/app/src/main/java/com/pocketdl/app/download/DownloadCoordinator.kt`:
   - Added `retiringJobs`, `cleanupJob = SupervisorJob()`, and `coordinatorScope.coroutineContext[Job]?.invokeOnCompletion` shutdown listener.
   - Added `isJobCurrent(taskId, currentJob)` helper.
   - Updated `runWorker` with identity checks before Room writes, during progress updates, and before terminal writes; moved Room updates outside coordinator mutex.
   - Updated `pauseTask`, `cancelTask`, and `pauseAll` with `retiringJobs` tracking and `jobToCancel?.join()`.
   - Updated `startTask` and `dispatchQueueInternal` to respect `retiringJobs` and check `coordinatorScope.isActive`.
   - Exposed `isRetiring(taskId)` and internal testing hooks `registerActiveJobForTest`.

2. `android-app/app/src/test/java/com/pocketdl/app/download/DownloadQueueHardeningUnitTest.kt`:
   - Added `onDownloadStarted` hook to `FakeDownloadEngine`.
   - Added 4 deterministic regression tests covering all Checkpoint 8.1 concurrency corrections.

---

## 3. Regression Tests

The following 4 focused, deterministic regression tests were added to `DownloadQueueHardeningUnitTest.kt`:

1. `lazyJobCompletion_whileCoordinatorScopeIsCancelling_cleansUpState`:
   - Verifies that cancelling `coordinatorScope` triggers cleanup on `cleanupScope`, clearing `activeJobs`, resetting `activeJobsCount` to 0, resetting foreground state to Idle, and not dispatching into the cancelled scope.
2. `oldWorkerUnwinding_afterReplacementWorkerRegistered_doesNotOverwriteRoomState`:
   - Verifies that when an old worker unwinds after a replacement worker has been registered, the old worker detects identity mismatch and does not overwrite the replacement worker's Room status (`DOWNLOADING`) or progress (0.85f). Verifies `releaseActiveJob` does not remove the newer job or decrement count.
3. `pauseThenImmediateQueueDispatch_ensuresNoConcurrentWriters`:
   - Verifies that pausing a task places it into `retiringJobs`, and an immediate queue dispatch or restart does not allow a replacement worker to start while the old worker is still writing. Confirms maximum observed concurrent writers never exceeds 1.
4. `completionCleanup_preservesNewerJobForSameTaskId`:
   - Verifies that `releaseActiveJob` with a stale `expectedJob` returns `false`, preserves the replacement job in `activeJobs`, and does not decrement queue capacity.

---

## 4. Verification & Build Results

- **Unit Tests:**
  - Command: `.\gradlew.bat testDebugUnitTest`
  - Result: **BUILD SUCCESSFUL** (94 tests passing, 0 failures, 0 skipped).
- **Assemble Debug APK:**
  - Command: `.\gradlew.bat assembleDebug`
  - Result: **BUILD SUCCESSFUL** (`app-debug.apk` built).
- **Physical Device Verification:**
  - Status: **VERIFIED** on Samsung Galaxy S22 Ultra (connected via ADB at `192.168.0.105:37161`).
  - Streamed installation completed successfully.
  - Package dump confirmed: `versionCode=2`, `versionName=1.0.0-p8.1`.
  - App launch confirmed (`MainActivity` launched cleanly with frame drawing verified).

---

## 5. Remaining Scope / Next Step

- Proceed to **Checkpoint 8.2: App Restart Recovery & Retry Budget** (reconciling in-flight downloads after process death, bounded retry attempts, exponential backoff, and network constraint checks).
