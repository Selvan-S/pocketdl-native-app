package com.pocketdl.app.data.repository

import com.pocketdl.app.ui.mock.DownloadTaskMock
import com.pocketdl.app.ui.mock.QualityOptionMock
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing download tasks and queue.
 * Backed by in-memory mock data in Phase 3; will be backed by Room & Downloader engine in later phases.
 */
interface DownloadRepository : Repository {
    fun observeDownloads(): Flow<List<DownloadTaskMock>>
    fun getDownloadById(id: String): Flow<DownloadTaskMock?>
    fun pauseDownload(id: String)
    fun resumeDownload(id: String)
    fun cancelDownload(id: String)
    fun retryDownload(id: String)
    fun startAll()
    fun pauseAll()
    fun startNow(id: String)
    fun removeQueued(id: String)
    fun purgeDownloads(ids: Set<String>)
    fun enqueueDownload(title: String, url: String, sourceDomain: String, quality: QualityOptionMock): DownloadTaskMock
}
