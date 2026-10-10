package com.pocketdl.app.download

/**
 * Interface defining action dispatch operations for download task management.
 * Implemented by [DownloadCoordinator] to decouple action invocation from concrete implementation details.
 */
interface DownloadActionHandler {
    fun startTask(taskId: String)
    fun pauseTask(taskId: String)
    fun cancelTask(taskId: String)
    fun pauseAll()
    fun startAll()
}
