package com.pocketdl.app.download

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Job
import okhttp3.Call

/**
 * Progress payload emitted during an active streaming download.
 */
data class DownloadProgressUpdate(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val progress: Float,
    val speedBps: Long,
    val speedText: String,
    val etaSeconds: Long,
    val etaText: String,
    val etag: String? = null,
    val lastModified: String? = null
)

/**
 * Terminal result of a download execution.
 */
sealed interface DownloadResult {
    data class Success(
        val file: File,
        val totalBytes: Long,
        val etag: String? = null,
        val lastModified: String? = null
    ) : DownloadResult

    data class Failure(
        val error: Throwable,
        val canResume: Boolean = true
    ) : DownloadResult

    data object Paused : DownloadResult

    data object Cancelled : DownloadResult
}

/**
 * Atomic control handle for an active download execution.
 */
class DownloadJobHandle(
    val taskId: String,
    val job: Job,
    var call: Call? = null,
    val isPaused: AtomicBoolean = AtomicBoolean(false),
    val isCancelled: AtomicBoolean = AtomicBoolean(false)
)

/**
 * Core interface for executing low-level streaming HTTP/HTTPS file downloads.
 */
interface DownloadEngine {
    /**
     * Executes a streaming HTTP download with resume and Range support.
     * Throttled progress updates are emitted via [onProgress].
     */
    suspend fun download(
        taskId: String,
        url: String,
        destinationFile: File,
        existingEtag: String? = null,
        existingLastModified: String? = null,
        onProgress: suspend (DownloadProgressUpdate) -> Unit
    ): DownloadResult

    /**
     * Signals pause to an active task. The temporary partial file is preserved.
     */
    fun pause(taskId: String)

    /**
     * Signals cancel to an active task. The temporary partial file is deleted.
     */
    fun cancel(taskId: String)

    /**
     * Checks if a task currently has an active download job running.
     */
    fun isRunning(taskId: String): Boolean
}
