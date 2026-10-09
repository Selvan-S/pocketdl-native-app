package com.pocketdl.app.data.repository

import com.pocketdl.app.data.database.dao.DownloadTaskDao
import com.pocketdl.app.data.database.toDomain
import com.pocketdl.app.data.database.toEntity
import com.pocketdl.app.download.DownloadCoordinator
import com.pocketdl.app.ui.mock.DownloadTaskMock
import com.pocketdl.app.ui.mock.MockDataProvider
import com.pocketdl.app.ui.mock.QualityOptionMock
import com.pocketdl.app.ui.mock.TaskStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Room-backed persistent implementation of [DownloadRepository].
 * Coordinates download lifecycle and execution through [DownloadCoordinator].
 */
class RoomDownloadRepository(
    private val downloadTaskDao: DownloadTaskDao,
    private val coordinator: DownloadCoordinator? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DownloadRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        repositoryScope.launch {
            if (downloadTaskDao.count() == 0) {
                // Seed initial mock data on first launch
                val initialEntities = MockDataProvider.sampleDownloadsList.mapIndexed { index, mock ->
                    mock.toEntity(createdAt = System.currentTimeMillis() - (index * 60_000L))
                }
                downloadTaskDao.insertAll(initialEntities)
            }
        }
    }

    override fun observeDownloads(): Flow<List<DownloadTaskMock>> =
        downloadTaskDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getDownloadById(id: String): Flow<DownloadTaskMock?> =
        downloadTaskDao.getById(id).map { it?.toDomain() }

    override fun pauseDownload(id: String) {
        if (coordinator != null) {
            coordinator.pauseTask(id)
        } else {
            repositoryScope.launch {
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

    override fun resumeDownload(id: String) {
        if (coordinator != null) {
            coordinator.startTask(id)
        } else {
            repositoryScope.launch {
                downloadTaskDao.updateStatus(
                    id = id,
                    status = TaskStatus.DOWNLOADING.name,
                    statusText = "Downloading",
                    speedText = "12.8 MB/s",
                    etaText = "00:45"
                )
            }
        }
    }

    override fun cancelDownload(id: String) {
        if (coordinator != null) {
            coordinator.cancelTask(id)
        } else {
            repositoryScope.launch {
                downloadTaskDao.deleteById(id)
            }
        }
    }

    override fun retryDownload(id: String) {
        if (coordinator != null) {
            coordinator.retryTask(id)
        } else {
            repositoryScope.launch {
                downloadTaskDao.retryTask(
                    id = id,
                    status = TaskStatus.DOWNLOADING.name,
                    statusText = "Downloading",
                    progress = 0.05f,
                    speedText = "9.4 MB/s",
                    etaText = "01:20"
                )
            }
        }
    }

    override fun startAll() {
        if (coordinator != null) {
            coordinator.startAll()
        } else {
            repositoryScope.launch {
                downloadTaskDao.updateAllStatus(
                    fromStatuses = listOf(TaskStatus.PAUSED.name, TaskStatus.QUEUED.name),
                    toStatus = TaskStatus.DOWNLOADING.name,
                    statusText = "Downloading",
                    speedText = "11.5 MB/s",
                    etaText = "00:52"
                )
            }
        }
    }

    override fun pauseAll() {
        if (coordinator != null) {
            coordinator.pauseAll()
        } else {
            repositoryScope.launch {
                downloadTaskDao.updateAllStatus(
                    fromStatuses = listOf(TaskStatus.DOWNLOADING.name),
                    toStatus = TaskStatus.PAUSED.name,
                    statusText = "Paused",
                    speedText = "0 KB/s",
                    etaText = "Paused"
                )
            }
        }
    }

    override fun startNow(id: String) {
        if (coordinator != null) {
            coordinator.startNow(id)
        } else {
            repositoryScope.launch {
                downloadTaskDao.updateStatus(
                    id = id,
                    status = TaskStatus.DOWNLOADING.name,
                    statusText = "Downloading",
                    speedText = "15.0 MB/s",
                    etaText = "00:30"
                )
            }
        }
    }

    override fun removeQueued(id: String) {
        cancelDownload(id)
    }

    override fun purgeDownloads(ids: Set<String>) {
        repositoryScope.launch {
            for (id in ids) {
                coordinator?.cancelTask(id)
            }
            downloadTaskDao.deleteByIds(ids.toList())
        }
    }

    override fun enqueueDownload(
        title: String,
        url: String,
        sourceDomain: String,
        quality: QualityOptionMock
    ): DownloadTaskMock {
        val newTask = DownloadTaskMock(
            id = "dl_${UUID.randomUUID().toString().take(6)}",
            title = title,
            sourceDomain = sourceDomain,
            statusText = "Queued",
            status = TaskStatus.QUEUED,
            progress = 0.0f,
            downloadedSizeText = "0 MB",
            totalSizeText = quality.sizeText,
            speedText = "Waiting",
            etaText = "In Queue",
            resolutionBadge = quality.label.substringBefore(" ").take(10),
            codecBadge = quality.codec,
            sourceUrl = url
        )
        repositoryScope.launch {
            downloadTaskDao.insert(newTask.toEntity(sourceUrl = url))
            coordinator?.startTask(newTask.id)
        }
        return newTask
    }
}
