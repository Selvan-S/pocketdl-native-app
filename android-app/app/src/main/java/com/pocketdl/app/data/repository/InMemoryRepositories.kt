package com.pocketdl.app.data.repository

import com.pocketdl.app.ui.mock.CapturedMediaMock
import com.pocketdl.app.ui.mock.DownloadTaskMock
import com.pocketdl.app.ui.mock.ExtensionStatusMock
import com.pocketdl.app.ui.mock.MockDataProvider
import com.pocketdl.app.ui.mock.QualityOptionMock
import com.pocketdl.app.ui.mock.StorageUsageMock
import com.pocketdl.app.ui.mock.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class InMemoryCapturedMediaRepository(
    initialData: List<CapturedMediaMock> = MockDataProvider.sampleCapturedList
) : CapturedMediaRepository {

    private val _capturedFlow = MutableStateFlow(initialData)

    override fun observeCapturedMedia(): Flow<List<CapturedMediaMock>> = _capturedFlow.asStateFlow()

    override fun getCapturedMediaById(id: String): Flow<CapturedMediaMock?> =
        _capturedFlow.map { list -> list.find { it.id == id } }

    override fun deleteCapturedMedia(id: String) {
        _capturedFlow.value = _capturedFlow.value.filterNot { it.id == id }
    }

    override fun addCapturedMedia(item: CapturedMediaMock) {
        _capturedFlow.value = listOf(item) + _capturedFlow.value
    }

    override fun clearAll() {
        _capturedFlow.value = emptyList()
    }
}

class InMemoryDownloadRepository(
    initialData: List<DownloadTaskMock> = MockDataProvider.sampleDownloadsList
) : DownloadRepository {

    private val _downloadsFlow = MutableStateFlow(initialData)

    override fun observeDownloads(): Flow<List<DownloadTaskMock>> = _downloadsFlow.asStateFlow()

    override fun getDownloadById(id: String): Flow<DownloadTaskMock?> =
        _downloadsFlow.map { list -> list.find { it.id == id } }

    override fun pauseDownload(id: String) {
        _downloadsFlow.value = _downloadsFlow.value.map { task ->
            if (task.id == id && task.status == TaskStatus.DOWNLOADING) {
                task.copy(
                    status = TaskStatus.PAUSED,
                    statusText = "Paused",
                    speedText = "0 KB/s",
                    etaText = "Paused"
                )
            } else task
        }
    }

    override fun resumeDownload(id: String) {
        _downloadsFlow.value = _downloadsFlow.value.map { task ->
            if (task.id == id && (task.status == TaskStatus.PAUSED || task.status == TaskStatus.QUEUED)) {
                task.copy(
                    status = TaskStatus.DOWNLOADING,
                    statusText = "Downloading",
                    speedText = "12.8 MB/s",
                    etaText = "00:45"
                )
            } else task
        }
    }

    override fun cancelDownload(id: String) {
        _downloadsFlow.value = _downloadsFlow.value.filterNot { it.id == id }
    }

    override fun retryDownload(id: String) {
        _downloadsFlow.value = _downloadsFlow.value.map { task ->
            if (task.id == id) {
                task.copy(
                    status = TaskStatus.DOWNLOADING,
                    statusText = "Downloading",
                    progress = 0.05f,
                    speedText = "9.4 MB/s",
                    etaText = "01:20"
                )
            } else task
        }
    }

    override fun startAll() {
        _downloadsFlow.value = _downloadsFlow.value.map { task ->
            if (task.status == TaskStatus.PAUSED || task.status == TaskStatus.QUEUED) {
                task.copy(
                    status = TaskStatus.DOWNLOADING,
                    statusText = "Downloading",
                    speedText = "11.5 MB/s",
                    etaText = "00:52"
                )
            } else task
        }
    }

    override fun pauseAll() {
        _downloadsFlow.value = _downloadsFlow.value.map { task ->
            if (task.status == TaskStatus.DOWNLOADING) {
                task.copy(
                    status = TaskStatus.PAUSED,
                    statusText = "Paused",
                    speedText = "0 KB/s",
                    etaText = "Paused"
                )
            } else task
        }
    }

    override fun startNow(id: String) {
        _downloadsFlow.value = _downloadsFlow.value.map { task ->
            if (task.id == id) {
                task.copy(
                    status = TaskStatus.DOWNLOADING,
                    statusText = "Downloading",
                    speedText = "15.0 MB/s",
                    etaText = "00:30"
                )
            } else task
        }
    }

    override fun removeQueued(id: String) {
        _downloadsFlow.value = _downloadsFlow.value.filterNot { it.id == id }
    }

    override fun purgeDownloads(ids: Set<String>) {
        _downloadsFlow.value = _downloadsFlow.value.filterNot { it.id in ids }
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
            codecBadge = quality.codec
        )
        _downloadsFlow.value = _downloadsFlow.value + newTask
        return newTask
    }
}

class InMemoryExtensionRepository(
    initialStatus: ExtensionStatusMock = MockDataProvider.sampleExtensionStatus,
    initialToken: String = "PKT-9482-WIFI"
) : ExtensionRepository {

    private val _statusFlow = MutableStateFlow(initialStatus)
    private val _tokenFlow = MutableStateFlow(initialToken)

    override fun observeExtensionStatus(): Flow<ExtensionStatusMock> = _statusFlow.asStateFlow()

    override fun getPairingToken(): Flow<String> = _tokenFlow.asStateFlow()

    override fun toggleConnection() {
        val current = _statusFlow.value
        val nextConnected = !current.isConnected
        _statusFlow.value = current.copy(
            isConnected = nextConnected,
            lastHeartbeatText = if (nextConnected) "Just now" else "Disconnected"
        )
    }

    override fun regeneratePairingToken(): String {
        val randomNum = (1000..9999).random()
        val newToken = "PKT-$randomNum-WIFI"
        _tokenFlow.value = newToken
        return newToken
    }

    override fun updatePort(port: Int) {
        _statusFlow.value = _statusFlow.value.copy(listenerPort = port)
    }
}

class InMemorySettingsRepository(
    initialSettings: SettingsData = SettingsData()
) : SettingsRepository {

    private val _settingsFlow = MutableStateFlow(initialSettings)

    override fun observeSettings(): Flow<SettingsData> = _settingsFlow.asStateFlow()

    override fun setEngine(engine: DownloadEngineType) {
        _settingsFlow.value = _settingsFlow.value.copy(engine = engine)
    }

    override fun setMaxParallelDownloads(count: Int) {
        _settingsFlow.value = _settingsFlow.value.copy(maxParallelDownloads = count)
    }

    override fun setAllowCellular(allow: Boolean) {
        _settingsFlow.value = _settingsFlow.value.copy(allowCellular = allow)
    }

    override fun setAutoDetectLinks(detect: Boolean) {
        _settingsFlow.value = _settingsFlow.value.copy(autoDetectLinks = detect)
    }
}

class InMemoryStorageRepository(
    initialStorage: StorageUsageMock = MockDataProvider.sampleStorage
) : StorageRepository {

    private val _storageFlow = MutableStateFlow(initialStorage)

    override fun observeStorageUsage(): Flow<StorageUsageMock> = _storageFlow.asStateFlow()

    override fun recalculateStorage(freedGb: Double) {
        val current = _storageFlow.value
        val newMedia = (current.mediaGb - freedGb).coerceAtLeast(0.0)
        val newFree = (current.freeGb + freedGb).coerceAtMost(current.totalGb)
        _storageFlow.value = current.copy(mediaGb = newMedia, freeGb = newFree)
    }
}

/**
 * Singleton repository provider for in-memory operations across ViewModels.
 */
object InMemoryRepositoryProvider {
    var capturedMediaRepository: CapturedMediaRepository = InMemoryCapturedMediaRepository()
    var downloadRepository: DownloadRepository = InMemoryDownloadRepository()
    val extensionRepository: ExtensionRepository = InMemoryExtensionRepository()
    val settingsRepository: SettingsRepository = InMemorySettingsRepository()
    val storageRepository: StorageRepository = InMemoryStorageRepository()
}
