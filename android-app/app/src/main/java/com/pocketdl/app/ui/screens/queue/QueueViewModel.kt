package com.pocketdl.app.ui.screens.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketdl.app.data.repository.DownloadRepository
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
import com.pocketdl.app.data.repository.SettingsRepository
import com.pocketdl.app.ui.mock.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QueueViewModel(
    private val downloadRepo: DownloadRepository = InMemoryRepositoryProvider.downloadRepository,
    private val settingsRepo: SettingsRepository = InMemoryRepositoryProvider.settingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QueueUiState(isLoading = true))
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                downloadRepo.observeDownloads(),
                settingsRepo.observeSettings()
            ) { downloads, settings ->
                val queued = downloads.filter { it.status != TaskStatus.COMPLETED }
                _uiState.value.copy(
                    isLoading = false,
                    queuedTasks = queued,
                    maxParallel = settings.maxParallelDownloads
                )
            }.collect { combinedState ->
                _uiState.value = combinedState
            }
        }
    }

    fun startAll() {
        downloadRepo.startAll()
        _uiState.update { it.copy(userMessage = "Started all queued downloads") }
    }

    fun pauseAll() {
        downloadRepo.pauseAll()
        _uiState.update { it.copy(userMessage = "Paused all downloads") }
    }

    fun startNow(taskId: String) {
        downloadRepo.startNow(taskId)
        _uiState.update { it.copy(userMessage = "Prioritized task") }
    }

    fun removeTask(taskId: String) {
        downloadRepo.removeQueued(taskId)
        _uiState.update { it.copy(userMessage = "Removed task from queue") }
    }

    fun onDismissMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
