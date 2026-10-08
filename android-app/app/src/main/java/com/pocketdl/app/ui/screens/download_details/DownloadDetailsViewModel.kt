package com.pocketdl.app.ui.screens.download_details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketdl.app.data.repository.DownloadRepository
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
import com.pocketdl.app.ui.mock.MockDataProvider
import com.pocketdl.app.ui.mock.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DownloadDetailsViewModel(
    private val downloadRepo: DownloadRepository = InMemoryRepositoryProvider.downloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadDetailsUiState(isLoading = true))
    val uiState: StateFlow<DownloadDetailsUiState> = _uiState.asStateFlow()

    private var currentDownloadId: String? = null

    fun loadTask(downloadId: String) {
        currentDownloadId = downloadId
        viewModelScope.launch {
            downloadRepo.getDownloadById(downloadId).collect { item ->
                if (item != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            task = item,
                            isNotFound = false
                        )
                    }
                } else {
                    // Fallback to first download for testing/mock previews
                    val first = MockDataProvider.sampleDownloadsList.firstOrNull()
                    if (first != null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                task = first,
                                isNotFound = false
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, isNotFound = true) }
                    }
                }
            }
        }
    }

    fun onPauseResume() {
        val task = _uiState.value.task ?: return
        if (task.status == TaskStatus.DOWNLOADING) {
            downloadRepo.pauseDownload(task.id)
            _uiState.update { it.copy(userMessage = "Download paused") }
        } else if (task.status == TaskStatus.PAUSED || task.status == TaskStatus.QUEUED) {
            downloadRepo.resumeDownload(task.id)
            _uiState.update { it.copy(userMessage = "Download resumed") }
        }
    }

    fun onCancel() {
        val task = _uiState.value.task ?: return
        downloadRepo.cancelDownload(task.id)
        _uiState.update { it.copy(userMessage = "Download removed") }
    }

    fun onRetry() {
        val task = _uiState.value.task ?: return
        downloadRepo.retryDownload(task.id)
        _uiState.update { it.copy(userMessage = "Retrying download") }
    }

    fun onDismissMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
