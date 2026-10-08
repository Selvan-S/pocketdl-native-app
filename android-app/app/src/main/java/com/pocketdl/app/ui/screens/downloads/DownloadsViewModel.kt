package com.pocketdl.app.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketdl.app.data.repository.DownloadRepository
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
import com.pocketdl.app.data.repository.StorageRepository
import com.pocketdl.app.ui.mock.DownloadTaskMock
import com.pocketdl.app.ui.mock.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val downloadRepo: DownloadRepository = InMemoryRepositoryProvider.downloadRepository,
    private val storageRepo: StorageRepository = InMemoryRepositoryProvider.storageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState(isLoading = true))
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private var allDownloads: List<DownloadTaskMock> = emptyList()

    init {
        viewModelScope.launch {
            combine(
                downloadRepo.observeDownloads(),
                storageRepo.observeStorageUsage()
            ) { downloads, storage ->
                allDownloads = downloads
                val currentFilter = _uiState.value.filter
                val filtered = applyFilter(downloads, currentFilter)

                val total = downloads.size
                val active = downloads.count { it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.PAUSED }
                val completed = downloads.count { it.status == TaskStatus.COMPLETED }
                val queued = downloads.count { it.status == TaskStatus.QUEUED }
                val failed = downloads.count { it.status == TaskStatus.FAILED }

                _uiState.value.copy(
                    isLoading = false,
                    items = filtered,
                    storage = storage,
                    totalCount = total,
                    activeCount = active,
                    completedCount = completed,
                    queuedCount = queued,
                    failedCount = failed
                )
            }.collect { combinedState ->
                _uiState.value = combinedState
            }
        }
    }

    fun onFilterSelected(filter: DownloadFilter) {
        _uiState.update {
            it.copy(
                filter = filter,
                items = applyFilter(allDownloads, filter)
            )
        }
    }

    private fun applyFilter(list: List<DownloadTaskMock>, filter: DownloadFilter): List<DownloadTaskMock> {
        return when (filter) {
            DownloadFilter.ALL -> list
            DownloadFilter.ACTIVE -> list.filter { it.status == TaskStatus.DOWNLOADING || it.status == TaskStatus.PAUSED }
            DownloadFilter.COMPLETED -> list.filter { it.status == TaskStatus.COMPLETED }
            DownloadFilter.QUEUED -> list.filter { it.status == TaskStatus.QUEUED }
            DownloadFilter.FAILED -> list.filter { it.status == TaskStatus.FAILED }
        }
    }

    fun onPauseResume(downloadId: String) {
        val task = allDownloads.find { it.id == downloadId } ?: return
        if (task.status == TaskStatus.DOWNLOADING) {
            downloadRepo.pauseDownload(downloadId)
            _uiState.update { it.copy(userMessage = "Download paused: ${task.title}") }
        } else if (task.status == TaskStatus.PAUSED || task.status == TaskStatus.QUEUED) {
            downloadRepo.resumeDownload(downloadId)
            _uiState.update { it.copy(userMessage = "Download resumed: ${task.title}") }
        }
    }

    fun onCancel(downloadId: String) {
        val task = allDownloads.find { it.id == downloadId }
        downloadRepo.cancelDownload(downloadId)
        _uiState.update { it.copy(userMessage = "Removed: ${task?.title ?: downloadId}") }
    }

    fun onRetry(downloadId: String) {
        downloadRepo.retryDownload(downloadId)
        _uiState.update { it.copy(userMessage = "Retrying download") }
    }

    fun onDismissMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
