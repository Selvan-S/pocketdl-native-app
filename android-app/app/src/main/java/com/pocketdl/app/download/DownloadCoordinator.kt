package com.pocketdl.app.download

import com.pocketdl.app.data.database.dao.DownloadTaskDao
import com.pocketdl.app.data.database.entity.DownloadTaskEntity
import com.pocketdl.app.data.repository.SettingsRepository
import com.pocketdl.app.ui.mock.TaskStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal Phase 6 Download Coordinator.
 * Enforces max parallel downloads (1–5) from [SettingsRepository], prevents duplicate execution,
 * manages Start All / Pause All / Start Now, and auto-advances the queue on task completion or failure.
 *
 * Implements a strict startup initialization barrier: reconciles any stale DOWNLOADING tasks
 * to PAUSED state before accepting or dispatching work.
 */
class DownloadCoordinator(
    private val engine: DownloadEngine,
    private val downloadTaskDao: DownloadTaskDao,
    private val settingsRepository: SettingsRepository,
    private val destinationDirProvider: () -> File,
    private val coordinatorScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DownloadActionHandler {
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val mutex = Mutex()

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
            mutex.withLock {
                if (activeJobs.containsKey(taskId)) return@withLock
                val task = downloadTaskDao.findById(taskId) ?: return@withLock
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
     * Pauses an active task. Temporary partial file is preserved.
     */
    override fun pauseTask(taskId: String) {
        coordinatorScope.launch(ioDispatcher) {
            ensureInitialized()
            engine.pause(taskId)
            downloadTaskDao.updateStatus(
                id = taskId,
                status = TaskStatus.PAUSED.name,
                statusText = "Paused",
                speedText = "0 KB/s",
                etaText = "Paused"
            )
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
            mutex.withLock {
                downloadTaskDao.updateAllStatus(
                    fromStatuses = listOf(TaskStatus.PAUSED.name),
                    toStatus = TaskStatus.QUEUED.name,
                    statusText = "Queued",
                    speedText = "Waiting",
                    etaText = "In Queue"
                )
                dispatchNextSlots()
            }
        }
    }

    /**
     * Pauses all currently executing downloads.
     */
    override fun pauseAll() {
        coordinatorScope.launch(ioDispatcher) {
            ensureInitialized()
            val runningIds = activeJobs.keys.toList()
            for (id in runningIds) {
                engine.pause(id)
                downloadTaskDao.updateStatus(
                    id = id,
                    status = TaskStatus.PAUSED.name,
                    statusText = "Paused",
                    speedText = "0 KB/s",
                    etaText = "Paused"
                )
            }
            activeJobs.clear()
        }
    }

    /**
     * Starts the specified task immediately with priority.
     */
    fun startNow(taskId: String) {
        coordinatorScope.launch(ioDispatcher) {
            ensureInitialized()
            mutex.withLock {
                val task = downloadTaskDao.findById(taskId) ?: return@withLock
                if (!activeJobs.containsKey(taskId)) {
                    launchTaskInternal(task)
                }
            }
        }
    }

    private suspend fun onTaskTerminated(taskId: String) {
        mutex.withLock {
            val removed = activeJobs.remove(taskId)
            if (removed != null) {
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
            // Persist the stable destination path BEFORE writing any bytes or starting engine
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
                }
            )

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

            onTaskTerminated(taskId)
        }

        activeJobs[taskId] = job
    }

    private suspend fun getMaxParallel(): Int {
        return try {
            settingsRepository.observeSettings().first().maxParallelDownloads.coerceIn(1, 5)
        } catch (_: Exception) {
            3
        }
    }
}
