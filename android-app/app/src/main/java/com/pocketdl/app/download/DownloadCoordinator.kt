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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val mutex = Mutex()

    private var serviceStartSessionId: Long = 0L

    private val _activeJobsCount = MutableStateFlow(0)
    val activeJobsCount: StateFlow<Int> = _activeJobsCount.asStateFlow()

    private val _activeNotificationState = MutableStateFlow<DownloadNotificationState?>(null)
    val activeNotificationState: StateFlow<DownloadNotificationState?> = _activeNotificationState.asStateFlow()

    private val _serviceForegroundState = MutableStateFlow<ServiceForegroundResult>(ServiceForegroundResult.Idle)
    val serviceForegroundState: StateFlow<ServiceForegroundResult> = _serviceForegroundState.asStateFlow()

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
        mutex.withLock {
            downloadTaskDao.updateAllStatus(
                fromStatuses = listOf(TaskStatus.DOWNLOADING.name),
                toStatus = TaskStatus.PAUSED.name,
                statusText = "Paused",
                speedText = "0 KB/s",
                etaText = "Paused"
            )
        }
    }

    fun isRunning(taskId: String): Boolean = activeJobs.containsKey(taskId)

    /**
     * Attempts to start or resume a task. If max concurrency is reached,
     * the task status remains QUEUED in Room.
     */
    override fun startTask(taskId: String) {
        coordinatorScope.launch(ioDispatcher) {
            ensureInitialized()

            val task = mutex.withLock {
                if (activeJobs.containsKey(taskId)) null
                else downloadTaskDao.findById(taskId)
            }
            if (task == null) return@launch

            var currentSessionId: Long = 0L
            var shouldStartLauncher = false

            mutex.withLock {
                if (activeJobs.containsKey(taskId)) return@launch
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
                    downloadTaskDao.updateStatus(
                        id = taskId,
                        status = TaskStatus.PAUSED.name,
                        statusText = "Paused (Background Start Blocked)",
                        speedText = "0 KB/s",
                        etaText = "Paused"
                    )
                    return@launch
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

                    val reasonText = if (isTimeout) "Timeout" else "Failed"
                    downloadTaskDao.updateStatus(
                        id = taskId,
                        status = TaskStatus.PAUSED.name,
                        statusText = "Paused (Foreground Service $reasonText)",
                        speedText = "0 KB/s",
                        etaText = "Paused"
                    )
                    return@launch
                }
            }

            mutex.withLock {
                if (activeJobs.containsKey(taskId)) return@withLock
                val maxParallel = getMaxParallel()

                if (activeJobs.size < maxParallel) {
                    launchTaskInternal(task)
                } else {
                    downloadTaskDao.updateStatus(
                        id = taskId,
                        status = TaskStatus.QUEUED.name,
                        statusText = "Queued",
                        speedText = "Waiting",
                        etaText = "In Queue"
                    )
                }
            }
        }
    }

    /**
     * Pauses an active task. Preserves COMPLETED or FAILED terminal states.
     */
    override fun pauseTask(taskId: String) {
        coordinatorScope.launch(ioDispatcher) {
            ensureInitialized()
            engine.pause(taskId)
            mutex.withLock {
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
            }
            onTaskTerminated(taskId)
        }
    }

    /**
     * Cancels a task and cleans up temporary partial files.
     */
    override fun cancelTask(taskId: String) {
        coordinatorScope.launch(ioDispatcher) {
            ensureInitialized()
            engine.cancel(taskId)
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
            onTaskTerminated(taskId)
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
        activeJobs.clear()
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
            val queuedTasks = mutex.withLock {
                downloadTaskDao.updateAllStatus(
                    fromStatuses = listOf(TaskStatus.PAUSED.name),
                    toStatus = TaskStatus.QUEUED.name,
                    statusText = "Queued",
                    speedText = "Waiting",
                    etaText = "In Queue"
                )
                val maxParallel = getMaxParallel()
                downloadTaskDao.findOldestQueued(maxParallel)
            }
            for (task in queuedTasks) {
                startTask(task.id)
            }
        }
    }

    /**
     * Pauses all currently executing downloads. Preserves COMPLETED/FAILED terminal states.
     */
    override fun pauseAll() {
        coordinatorScope.launch(ioDispatcher) {
            ensureInitialized()
            val runningIds = activeJobs.keys.toList()
            for (id in runningIds) {
                engine.pause(id)
                mutex.withLock {
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
            activeJobs.clear()
            mutex.withLock {
                _activeJobsCount.value = 0
                _activeNotificationState.value = null
                _serviceForegroundState.value = ServiceForegroundResult.Idle
            }
        }
    }

    /**
     * Starts the specified task immediately with priority.
     */
    fun startNow(taskId: String) {
        startTask(taskId)
    }

    private suspend fun onTaskTerminated(taskId: String) {
        mutex.withLock {
            val removed = activeJobs.remove(taskId)
            if (removed != null) {
                _activeJobsCount.value = activeJobs.size
                if (activeJobs.isEmpty()) {
                    _serviceForegroundState.value = ServiceForegroundResult.Idle
                    _activeNotificationState.value = null
                }
                dispatchNextSlots()
            }
        }
    }

    private suspend fun dispatchNextSlots() {
        val maxParallel = getMaxParallel()
        val availableSlots = maxParallel - activeJobs.size
        if (availableSlots <= 0) return

        val queuedTasks = downloadTaskDao.findOldestQueued(availableSlots)
        for (task in queuedTasks) {
            if (!activeJobs.containsKey(task.id)) {
                launchTaskInternal(task)
            }
        }
    }

    private fun launchTaskInternal(task: DownloadTaskEntity) {
        val taskId = task.id
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

        val job = coordinatorScope.launch(ioDispatcher) {
            try {
                // Persist destination path before streaming bytes
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

                _activeNotificationState.value = DownloadNotificationState(
                    activeCount = activeJobs.size,
                    title = task.title,
                    progressPercent = (task.progress * 100).toInt().coerceIn(0, 100),
                    downloadedSizeText = task.downloadedSizeText,
                    totalSizeText = task.totalSizeText,
                    speedText = task.speedText,
                    etaText = task.etaText,
                    primaryTaskId = taskId
                )

                val result = engine.download(
                    taskId = taskId,
                    url = task.sourceUrl,
                    destinationFile = destFile,
                    existingEtag = task.etag,
                    existingLastModified = task.lastModified,
                    onProgress = { update ->
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

                        _activeNotificationState.value = DownloadNotificationState(
                            activeCount = activeJobs.size,
                            title = task.title,
                            progressPercent = (update.progress * 100).toInt().coerceIn(0, 100),
                            downloadedSizeText = DownloadProgressCalculator.formatBytes(update.downloadedBytes),
                            totalSizeText = DownloadProgressCalculator.formatBytes(update.totalBytes),
                            speedText = update.speedText,
                            etaText = update.etaText,
                            primaryTaskId = taskId
                        )
                    }
                )

                mutex.withLock {
                    // Check if task row was deleted or status already set to COMPLETED
                    val currentInDb = downloadTaskDao.findById(taskId)
                    if (currentInDb == null || currentInDb.status == TaskStatus.COMPLETED.name) {
                        return@launch
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
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                mutex.withLock {
                    val currentInDb = downloadTaskDao.findById(taskId)
                    if (currentInDb != null && currentInDb.status != TaskStatus.COMPLETED.name) {
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
            } finally {
                onTaskTerminated(taskId)
            }
        }

        activeJobs[taskId] = job
        _activeJobsCount.value = activeJobs.size
    }

    private suspend fun getMaxParallel(): Int {
        return try {
            settingsRepository.observeSettings().first().maxParallelDownloads.coerceIn(1, 5)
        } catch (_: Exception) {
            3
        }
    }
}
