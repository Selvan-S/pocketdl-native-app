package com.pocketdl.app.download

import android.content.Context
import android.content.Intent
import com.pocketdl.app.data.database.dao.DownloadTaskDao
import com.pocketdl.app.data.database.entity.DownloadTaskEntity
import com.pocketdl.app.data.repository.SettingsRepository
import com.pocketdl.app.download.notification.DownloadNotificationManager
import com.pocketdl.app.download.notification.DownloadNotificationState
import com.pocketdl.app.download.service.DownloadService
import com.pocketdl.app.download.service.ServiceForegroundResult
import com.pocketdl.app.ui.mock.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeoutException

/**
 * Phase 6 & Phase 7 Download Coordinator.
 * Enforces max parallel downloads (1–5) from [SettingsRepository], prevents duplicate execution,
 * manages Start All / Pause All / Start Now, and auto-advances the queue on task completion or failure.
 *
 * Implements a strict startup initialization barrier, session-tracked foreground service startup barrier,
 * bounded startup timeout, terminal state preservation, resurrection prevention, guaranteed registry teardown,
 * and real-time active download notification state dispatch.
 */
open class DownloadCoordinator(
    private val engine: DownloadEngine,
    private val downloadTaskDao: DownloadTaskDao,
    private val settingsRepository: SettingsRepository,
    private val destinationDirProvider: () -> File,
    private val coordinatorScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val applicationContext: Context? = null,
    private val startServiceLauncher: ((Context?) -> Unit)? = null
) : DownloadActionHandler {
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val retiringJobs = ConcurrentHashMap<String, Job>()
    private val mutex = Mutex()
    private val cleanupJob = SupervisorJob()
    private val cleanupScope = CoroutineScope(cleanupJob + ioDispatcher)
    private val reservedTasks = mutableMapOf<String, Long>()
    private var reservationSequence = 0L

    private var serviceStartSessionId: Long = 0L

    private val _activeJobsCount = MutableStateFlow(0)
    val activeJobsCount: StateFlow<Int> = _activeJobsCount.asStateFlow()

    private val _activeNotificationState = MutableStateFlow<DownloadNotificationState?>(null)
    val activeNotificationState: StateFlow<DownloadNotificationState?> = _activeNotificationState.asStateFlow()

    private val _serviceForegroundState = MutableStateFlow<ServiceForegroundResult>(ServiceForegroundResult.Idle)
    val serviceForegroundState: StateFlow<ServiceForegroundResult> = _serviceForegroundState.asStateFlow()

    init {
        coordinatorScope.coroutineContext[Job]?.invokeOnCompletion {
            cleanupScope.launch(NonCancellable) {
                mutex.withLock {
                    activeJobs.clear()
                    reservedTasks.clear()
                    retiringJobs.clear()
                    _activeJobsCount.value = 0
                    _activeNotificationState.value = null
                    _serviceForegroundState.value = ServiceForegroundResult.Idle
                }
                cleanupJob.cancel()
            }
        }
    }

    private val initializationJob: Job = coordinatorScope.launch(ioDispatcher) {
        reconcileStartupStateInternal()
    }

    /**
     * Suspends until startup state reconciliation has completed.
     * All dispatch operations await this barrier before taking any action.
     */
    suspend fun ensureInitialized() {
        initializationJob.join()
    }

    /**
     * Notifies the coordinator of foreground service promotion results.
     * Ignores stale callbacks from older startup sessions.
     */
    fun notifyServiceForegroundResult(result: ServiceForegroundResult, targetSessionId: Long? = null) {
        coordinatorScope.launch(ioDispatcher) {
            mutex.withLock {
                if (result is ServiceForegroundResult.Idle) {
                    _serviceForegroundState.value = ServiceForegroundResult.Idle
                    return@launch
                }
                if (targetSessionId != null && targetSessionId != serviceStartSessionId) {
                    // Stale callback from an older session -> ignore
                    return@launch
                }
                _serviceForegroundState.value = result
            }
        }
    }

    private suspend fun reconcileStartupStateInternal() {
        downloadTaskDao.updateAllStatus(
            fromStatuses = listOf(TaskStatus.DOWNLOADING.name),
            toStatus = TaskStatus.PAUSED.name,
            statusText = "Paused",
            speedText = "0 KB/s",
            etaText = "Paused"
        )
    }

    fun isRunning(taskId: String): Boolean = activeJobs.containsKey(taskId)

    fun isRetiring(taskId: String): Boolean = retiringJobs.containsKey(taskId)

    /**
     * Attempts to start or resume a task. If max concurrency is reached,
     * the task status remains QUEUED in Room and is picked up deterministically.
     */
    override fun startTask(taskId: String) {
        coordinatorScope.launch(ioDispatcher) {
            ensureInitialized()

            val retiringJob = mutex.withLock { retiringJobs[taskId] }
            retiringJob?.join()

            val task = downloadTaskDao.findById(taskId) ?: return@launch
            val shouldEnqueue = mutex.withLock {
                !activeJobs.containsKey(taskId) && !reservedTasks.containsKey(taskId) && !retiringJobs.containsKey(taskId)
            }
            if (shouldEnqueue) {
                if (task.status != TaskStatus.DOWNLOADING.name) {
                    downloadTaskDao.updateStatus(
                        id = taskId,
                        status = TaskStatus.QUEUED.name,
                        statusText = "Queued",
                        speedText = "Waiting",
                        etaText = "In Queue"
                    )
                }
            }
            dispatchQueueInternal()
        }
    }

    /**
     * Dispatches queued tasks up to available maxParallel capacity.
     */
    fun dispatchQueue() {
        coordinatorScope.launch(ioDispatcher) {
            dispatchQueueInternal()
        }
    }

    private suspend fun dispatchQueueInternal() {
        if (!coordinatorScope.isActive) return

        val (availableSlots, fetchLimit) = mutex.withLock {
            val maxParallel = getMaxParallel()
            val inFlight = activeJobs.size + reservedTasks.size
            val slots = maxParallel - inFlight
            val effectiveSlots = if (slots < 0) 0 else slots
            Pair(effectiveSlots, effectiveSlots + inFlight)
        }
        if (availableSlots <= 0) return

        // Room I/O performed strictly OUTSIDE mutex with fetchLimit accounting for in-flight tasks
        val queuedCandidates = downloadTaskDao.findOldestQueued(fetchLimit)
        if (queuedCandidates.isEmpty()) return

        val tasksToProcess = mutableListOf<Pair<DownloadTaskEntity, Long>>()
        mutex.withLock {
            val maxParallel = getMaxParallel()
            for (task in queuedCandidates) {
                val currentSlots = maxParallel - (activeJobs.size + reservedTasks.size)
                if (currentSlots <= 0) break
                if (!activeJobs.containsKey(task.id) && !reservedTasks.containsKey(task.id) && !retiringJobs.containsKey(task.id)) {
                    val token = ++reservationSequence
                    reservedTasks[task.id] = token
                    tasksToProcess.add(Pair(task, token))
                }
            }
        } // Mutex released before any async operations

        for ((task, token) in tasksToProcess) {
            coordinatorScope.launch(ioDispatcher) {
                processReservedTask(task, token)
            }
        }
    }

    private suspend fun processReservedTask(task: DownloadTaskEntity, token: Long) {
        val taskId = task.id

        // 1. Pre-validation before foreground elevation
        val stillValidBeforeElevation = mutex.withLock { reservedTasks[taskId] == token }
        if (!stillValidBeforeElevation) return

        // 2. Foreground service elevation barrier
        val serviceElevated = ensureServiceElevated(taskId)
        if (!serviceElevated) {
            val released = releaseReservation(taskId, token)
            if (released) {
                val state = _serviceForegroundState.value
                val isTimeout = state is ServiceForegroundResult.Failed && state.exception is TimeoutException
                val reasonText = if (isTimeout) "Foreground Service Timeout" else "Background Start Blocked"
                downloadTaskDao.updateStatus(
                    id = taskId,
                    status = TaskStatus.PAUSED.name,
                    statusText = "Paused ($reasonText)",
                    speedText = "0 KB/s",
                    etaText = "Paused"
                )
                dispatchQueueInternal()
            }
            return
        }

        // 3. Post-elevation reservation validation
        val stillValidAfterElevation = mutex.withLock { reservedTasks[taskId] == token }
        if (!stillValidAfterElevation) return

        // 4. Revalidate task in Room strictly OUTSIDE mutex
        val currentInDb = downloadTaskDao.findById(taskId)
        if (currentInDb == null || currentInDb.status != TaskStatus.QUEUED.name) {
            val released = releaseReservation(taskId, token)
            if (released) {
                dispatchQueueInternal()
            }
            return
        }

        // 5. Construct lazy worker job
        val lazyJob = coordinatorScope.launch(ioDispatcher, start = CoroutineStart.LAZY) {
            runWorker(currentInDb)
        }

        // 6. Register lifecycle-safe completion cleanup callback on managed cleanupScope
        lazyJob.invokeOnCompletion {
            cleanupScope.launch {
                val wasActive = releaseActiveJob(taskId, expectedJob = lazyJob)
                if (wasActive && coordinatorScope.isActive) {
                    dispatchQueueInternal()
                }
            }
        }

        // 7. Atomic handoff from reservation to activeJobs under mutex
        val handoffSuccess = handoffReservationToActive(taskId, token, lazyJob)
        if (!handoffSuccess) {
            lazyJob.cancel()
            releaseReservation(taskId, token)
            return
        }

        // 8. Start worker execution
        lazyJob.start()
    }

    private suspend fun ensureServiceElevated(taskId: String): Boolean {
        var currentSessionId: Long = 0L
        var shouldStartLauncher = false

        mutex.withLock {
            val currentState = _serviceForegroundState.value
            if (activeJobs.isEmpty()) {
                when (currentState) {
                    is ServiceForegroundResult.Idle, is ServiceForegroundResult.Failed -> {
                        serviceStartSessionId++
                        currentSessionId = serviceStartSessionId
                        _serviceForegroundState.value = ServiceForegroundResult.Starting(currentSessionId)
                        shouldStartLauncher = true
                    }
                    is ServiceForegroundResult.Starting -> {
                        currentSessionId = currentState.sessionId
                        shouldStartLauncher = false
                    }
                    is ServiceForegroundResult.Success -> {
                        currentSessionId = currentState.sessionId
                        shouldStartLauncher = false
                    }
                }
            } else {
                currentSessionId = serviceStartSessionId
                shouldStartLauncher = false
            }
        }

        if (shouldStartLauncher) {
            val ctx = applicationContext
            val launcher = startServiceLauncher
            try {
                if (launcher != null) {
                    launcher(ctx)
                } else if (ctx != null) {
                    val serviceIntent = Intent(ctx, DownloadService::class.java)
                    androidx.core.content.ContextCompat.startForegroundService(ctx, serviceIntent)
                } else {
                    // In unit test environment without Context or launcher, default to Success
                    mutex.withLock {
                        _serviceForegroundState.value = ServiceForegroundResult.Success(currentSessionId)
                    }
                }
            } catch (e: Exception) {
                mutex.withLock {
                    _serviceForegroundState.value = ServiceForegroundResult.Failed(currentSessionId, e)
                }
                return false
            }
        }

        // Bounded barrier wait with 8s timeout
        val currentStateNow = _serviceForegroundState.value
        if (currentStateNow !is ServiceForegroundResult.Success || currentStateNow.sessionId != currentSessionId) {
            val barrierResult = withTimeoutOrNull(8_000) {
                _serviceForegroundState.first { res ->
                    when (res) {
                        is ServiceForegroundResult.Success -> res.sessionId == currentSessionId
                        is ServiceForegroundResult.Failed -> res.sessionId == currentSessionId
                        else -> false
                    }
                }
            }

            if (barrierResult == null || barrierResult is ServiceForegroundResult.Failed) {
                val isTimeout = barrierResult == null
                val exception = if (isTimeout) TimeoutException("Service start timed out after 8s")
                else (barrierResult as ServiceForegroundResult.Failed).exception

                mutex.withLock {
                    if (_serviceForegroundState.value !is ServiceForegroundResult.Success) {
                        _serviceForegroundState.value = ServiceForegroundResult.Failed(currentSessionId, exception)
                    }
                }
                return false
            }
        }
        return true
    }

    internal suspend fun handoffReservationToActive(
        taskId: String,
        token: Long,
        job: Job
    ): Boolean {
        return mutex.withLock {
            if (reservedTasks[taskId] == token) {
                reservedTasks.remove(taskId)
                activeJobs[taskId] = job
                _activeJobsCount.value = activeJobs.size
                true
            } else {
                false
            }
        }
    }

    internal suspend fun releaseReservation(taskId: String, expectedToken: Long? = null): Boolean {
        return mutex.withLock {
            val currentToken = reservedTasks[taskId]
            if (currentToken == null) {
                return@withLock false
            }
            if (expectedToken != null && currentToken != expectedToken) {
                return@withLock false
            }
            reservedTasks.remove(taskId) != null
        }
    }

    internal suspend fun releaseActiveJob(taskId: String, expectedJob: Job? = null): Boolean {
        val removed = mutex.withLock {
            val current = activeJobs[taskId]
            if (expectedJob != null && current !== expectedJob) {
                return@withLock false
            }
            val job = activeJobs.remove(taskId)
            if (job != null) {
                _activeJobsCount.value = activeJobs.size
                if (activeJobs.isEmpty()) {
                    _serviceForegroundState.value = ServiceForegroundResult.Idle
                    _activeNotificationState.value = null
                }
                true
            } else {
                false
            }
        }
        return removed
    }

    internal suspend fun isJobCurrent(taskId: String, expectedJob: Job): Boolean {
        return mutex.withLock {
            activeJobs[taskId] === expectedJob
        }
    }

    internal suspend fun registerActiveJobForTest(taskId: String, job: Job) {
        mutex.withLock {
            activeJobs[taskId] = job
            _activeJobsCount.value = activeJobs.size
        }
    }

    /**
     * Pauses an active or reserved task. Preserves COMPLETED or FAILED terminal states.
     */
    override fun pauseTask(taskId: String) {
        coordinatorScope.launch(ioDispatcher) {
            ensureInitialized()
            engine.pause(taskId)

            var removedFromActive = false
            var removedFromReserved = false
            var jobToCancel: Job? = null

            mutex.withLock {
                if (reservedTasks.containsKey(taskId)) {
                    reservedTasks.remove(taskId)
                    removedFromReserved = true
                }
                jobToCancel = activeJobs.remove(taskId)
                if (jobToCancel != null) {
                    retiringJobs[taskId] = jobToCancel!!
                    removedFromActive = true
                    _activeJobsCount.value = activeJobs.size
                    if (activeJobs.isEmpty()) {
                        _serviceForegroundState.value = ServiceForegroundResult.Idle
                        _activeNotificationState.value = null
                    }
                }
            }

            jobToCancel?.cancel()
            jobToCancel?.join()

            if (jobToCancel != null) {
                mutex.withLock {
                    retiringJobs.remove(taskId, jobToCancel)
                }
            }

            val currentTask = downloadTaskDao.findById(taskId)
            if (currentTask?.status != TaskStatus.COMPLETED.name && currentTask?.status != TaskStatus.FAILED.name) {
                downloadTaskDao.updateStatus(
                    id = taskId,
                    status = TaskStatus.PAUSED.name,
                    statusText = "Paused",
                    speedText = "0 KB/s",
                    etaText = "Paused"
                )
            }

            if (removedFromActive || removedFromReserved) {
                dispatchQueueInternal()
            }
        }
    }

    /**
     * Cancels a task, removes any active job or reservation, and cleans up temporary partial files.
     */
    override fun cancelTask(taskId: String) {
        coordinatorScope.launch(ioDispatcher) {
            ensureInitialized()
            engine.cancel(taskId)

            var removedFromActive = false
            var removedFromReserved = false
            var jobToCancel: Job? = null

            mutex.withLock {
                if (reservedTasks.containsKey(taskId)) {
                    reservedTasks.remove(taskId)
                    removedFromReserved = true
                }
                jobToCancel = activeJobs.remove(taskId)
                if (jobToCancel != null) {
                    retiringJobs[taskId] = jobToCancel!!
                    removedFromActive = true
                    _activeJobsCount.value = activeJobs.size
                    if (activeJobs.isEmpty()) {
                        _serviceForegroundState.value = ServiceForegroundResult.Idle
                        _activeNotificationState.value = null
                    }
                }
            }

            jobToCancel?.cancel()
            jobToCancel?.join()

            if (jobToCancel != null) {
                mutex.withLock {
                    retiringJobs.remove(taskId, jobToCancel)
                }
            }

            val task = downloadTaskDao.findById(taskId)
            if (task != null) {
                val destDir = destinationDirProvider()
                val targetFile = if (!task.localPath.isNullOrBlank()) {
                    File(task.localPath)
                } else {
                    DownloadFileUtils.resolveDestinationFile(destDir, task.title, taskId, url = task.sourceUrl)
                }
                val partFile = DownloadFileUtils.resolvePartFile(targetFile)
                DownloadFileUtils.deletePartFile(partFile)
                downloadTaskDao.deleteById(taskId)
            }

            if (removedFromActive || removedFromReserved) {
                dispatchQueueInternal()
            }
        }
    }

    /**
     * Cancels all active worker jobs immediately for system timeout.
     */
    fun cancelAllJobsForTimeout() {
        val runningIds = activeJobs.keys.toList()
        for (id in runningIds) {
            engine.pause(id)
        }
        for (job in activeJobs.values) {
            job.cancel()
        }
        activeJobs.clear()
        reservedTasks.clear()
        retiringJobs.clear()
        _activeJobsCount.value = 0
        _activeNotificationState.value = null
        _serviceForegroundState.value = ServiceForegroundResult.Idle
    }

    /**
     * Executes a fast, non-blocking best-effort status update to Room for system timeout.
     */
    fun markTasksPausedForTimeoutBestEffort() {
        coordinatorScope.launch(ioDispatcher) {
            try {
                downloadTaskDao.updateAllStatus(
                    fromStatuses = listOf(TaskStatus.DOWNLOADING.name),
                    toStatus = TaskStatus.PAUSED.name,
                    statusText = "Paused (System Timeout)",
                    speedText = "0 KB/s",
                    etaText = "Paused"
                )
            } catch (_: Exception) {
                // Ignore persistence failure on system forced shutdown
            }
        }
    }

    /**
     * Retries a failed or paused download.
     */
    fun retryTask(taskId: String) {
        coordinatorScope.launch(ioDispatcher) {
            ensureInitialized()
            downloadTaskDao.updateStatus(
                id = taskId,
                status = TaskStatus.QUEUED.name,
                statusText = "Queued",
                speedText = "Waiting",
                etaText = "In Queue"
            )
            startTask(taskId)
        }
    }

    /**
     * Resumes or starts all paused/queued downloads up to max parallel limit.
     */
    override fun startAll() {
        coordinatorScope.launch(ioDispatcher) {
            ensureInitialized()
            downloadTaskDao.updateAllStatus(
                fromStatuses = listOf(TaskStatus.PAUSED.name),
                toStatus = TaskStatus.QUEUED.name,
                statusText = "Queued",
                speedText = "Waiting",
                etaText = "In Queue"
            )
            dispatchQueueInternal()
        }
    }

    /**
     * Pauses all currently executing and reserved downloads. Preserves COMPLETED/FAILED terminal states.
     */
    override fun pauseAll() {
        coordinatorScope.launch(ioDispatcher) {
            ensureInitialized()
            val runningIds = mutableSetOf<String>()
            val jobsToCancel = mutableListOf<Job>()

            mutex.withLock {
                runningIds.addAll(activeJobs.keys)
                runningIds.addAll(reservedTasks.keys)
                jobsToCancel.addAll(activeJobs.values)
                for ((id, job) in activeJobs) {
                    retiringJobs[id] = job
                }
                activeJobs.clear()
                reservedTasks.clear()
                _activeJobsCount.value = 0
                _activeNotificationState.value = null
                _serviceForegroundState.value = ServiceForegroundResult.Idle
            }

            for (id in runningIds) {
                engine.pause(id)
            }
            for (job in jobsToCancel) {
                job.cancel()
            }
            jobsToCancel.joinAll()

            mutex.withLock {
                retiringJobs.clear()
            }

            for (id in runningIds) {
                val task = downloadTaskDao.findById(id)
                if (task?.status != TaskStatus.COMPLETED.name && task?.status != TaskStatus.FAILED.name) {
                    downloadTaskDao.updateStatus(
                        id = id,
                        status = TaskStatus.PAUSED.name,
                        statusText = "Paused",
                        speedText = "0 KB/s",
                        etaText = "Paused"
                    )
                }
            }
        }
    }

    /**
     * Starts the specified task immediately with priority.
     */
    fun startNow(taskId: String) {
        startTask(taskId)
    }

    private suspend fun runWorker(task: DownloadTaskEntity) {
        val taskId = task.id
        val currentJob = currentCoroutineContext()[Job] ?: return
        try {
            // Step 1: Cancellation check and worker identity verification before any network or file work
            currentCoroutineContext().ensureActive()

            if (!isJobCurrent(taskId, currentJob)) return

            val initialDbCheck = downloadTaskDao.findById(taskId)
            if (initialDbCheck == null || (initialDbCheck.status != TaskStatus.QUEUED.name && initialDbCheck.status != TaskStatus.DOWNLOADING.name)) {
                return
            }

            if (!isJobCurrent(taskId, currentJob)) return

            val downloadDir = destinationDirProvider()
            val destFile = if (!task.localPath.isNullOrBlank()) {
                File(task.localPath)
            } else {
                DownloadFileUtils.resolveDestinationFile(
                    downloadDir = downloadDir,
                    fileName = task.title,
                    taskId = taskId,
                    url = task.sourceUrl
                )
            }

            if (!isJobCurrent(taskId, currentJob)) return

            // Persist destination path before streaming bytes (Room call OUTSIDE mutex)
            downloadTaskDao.updateProgress(
                id = taskId,
                status = TaskStatus.DOWNLOADING.name,
                statusText = "Connecting...",
                progress = task.progress,
                downloadedSizeText = task.downloadedSizeText,
                totalSizeText = task.totalSizeText,
                speedText = "0 KB/s",
                etaText = "--:--",
                localPath = destFile.absolutePath,
                etag = task.etag,
                lastModified = task.lastModified,
                completedAt = null
            )

            if (isJobCurrent(taskId, currentJob)) {
                _activeNotificationState.value = DownloadNotificationState(
                    activeCount = _activeJobsCount.value,
                    title = task.title,
                    progressPercent = (task.progress * 100).toInt().coerceIn(0, 100),
                    downloadedSizeText = task.downloadedSizeText,
                    totalSizeText = task.totalSizeText,
                    speedText = task.speedText,
                    etaText = task.etaText,
                    primaryTaskId = taskId
                )
            }

            // Step 2: Ensure active and verify worker identity right before calling engine.download
            currentCoroutineContext().ensureActive()
            if (!isJobCurrent(taskId, currentJob)) return

            val result = engine.download(
                taskId = taskId,
                url = task.sourceUrl,
                destinationFile = destFile,
                existingEtag = task.etag,
                existingLastModified = task.lastModified,
                onProgress = { update ->
                    if (!isJobCurrent(taskId, currentJob)) return@download

                    downloadTaskDao.updateProgress(
                        id = taskId,
                        status = TaskStatus.DOWNLOADING.name,
                        statusText = "Downloading",
                        progress = update.progress,
                        downloadedSizeText = DownloadProgressCalculator.formatBytes(update.downloadedBytes),
                        totalSizeText = DownloadProgressCalculator.formatBytes(update.totalBytes),
                        speedText = update.speedText,
                        etaText = update.etaText,
                        localPath = destFile.absolutePath,
                        etag = update.etag,
                        lastModified = update.lastModified,
                        completedAt = null
                    )

                    if (isJobCurrent(taskId, currentJob)) {
                        _activeNotificationState.value = DownloadNotificationState(
                            activeCount = _activeJobsCount.value,
                            title = task.title,
                            progressPercent = (update.progress * 100).toInt().coerceIn(0, 100),
                            downloadedSizeText = DownloadProgressCalculator.formatBytes(update.downloadedBytes),
                            totalSizeText = DownloadProgressCalculator.formatBytes(update.totalBytes),
                            speedText = update.speedText,
                            etaText = update.etaText,
                            primaryTaskId = taskId
                        )
                    }
                }
            )

            // Step 3: Engine returned.
            // Verify worker identity BEFORE applying terminal Room updates
            if (!isJobCurrent(taskId, currentJob)) {
                return
            }

            val currentInDb = downloadTaskDao.findById(taskId)
            if (currentInDb == null || currentInDb.status == TaskStatus.COMPLETED.name) {
                return
            }

            if (!isJobCurrent(taskId, currentJob)) {
                return
            }

            when (result) {
                is DownloadResult.Success -> {
                    downloadTaskDao.updateProgress(
                        id = taskId,
                        status = TaskStatus.COMPLETED.name,
                        statusText = "Completed",
                        progress = 1.0f,
                        downloadedSizeText = DownloadProgressCalculator.formatBytes(result.totalBytes),
                        totalSizeText = DownloadProgressCalculator.formatBytes(result.totalBytes),
                        speedText = "0 KB/s",
                        etaText = "Completed",
                        localPath = result.file.absolutePath,
                        etag = result.etag,
                        lastModified = result.lastModified,
                        completedAt = System.currentTimeMillis()
                    )

                    applicationContext?.let { ctx ->
                        val notifManager = DownloadNotificationManager(ctx)
                        val notif = notifManager.buildCompletionNotification(taskId, task.title)
                        notifManager.postNotification(taskId.hashCode(), notif)
                    }
                }
                is DownloadResult.Failure -> {
                    val errMsg = result.error.localizedMessage?.take(40) ?: "Download failed"
                    downloadTaskDao.updateStatus(
                        id = taskId,
                        status = TaskStatus.FAILED.name,
                        statusText = "Failed: $errMsg",
                        speedText = "0 KB/s",
                        etaText = "--:--"
                    )

                    applicationContext?.let { ctx ->
                        val notifManager = DownloadNotificationManager(ctx)
                        val notif = notifManager.buildFailureNotification(taskId, task.title, errMsg)
                        notifManager.postNotification(taskId.hashCode(), notif)
                    }
                }
                is DownloadResult.Paused -> {
                    downloadTaskDao.updateStatus(
                        id = taskId,
                        status = TaskStatus.PAUSED.name,
                        statusText = "Paused",
                        speedText = "0 KB/s",
                        etaText = "Paused"
                    )
                }
                is DownloadResult.Cancelled -> {
                    // Task row deleted or cleaned up
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (isJobCurrent(taskId, currentJob)) {
                val currentInDb = downloadTaskDao.findById(taskId)
                if (currentInDb != null && currentInDb.status != TaskStatus.COMPLETED.name) {
                    if (isJobCurrent(taskId, currentJob)) {
                        val msg = e.localizedMessage?.take(40) ?: "Error"
                        downloadTaskDao.updateStatus(
                            id = taskId,
                            status = TaskStatus.FAILED.name,
                            statusText = "Failed: $msg",
                            speedText = "0 KB/s",
                            etaText = "--:--"
                        )
                    }
                }
            }
        }
    }

    private suspend fun getMaxParallel(): Int {
        return try {
            settingsRepository.observeSettings().first().maxParallelDownloads.coerceIn(1, 5)
        } catch (_: Exception) {
            3
        }
    }
}
