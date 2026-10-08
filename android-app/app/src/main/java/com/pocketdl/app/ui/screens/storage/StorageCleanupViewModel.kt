package com.pocketdl.app.ui.screens.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketdl.app.data.repository.DownloadRepository
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
import com.pocketdl.app.data.repository.StorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StorageCleanupViewModel(
    private val downloadRepo: DownloadRepository = InMemoryRepositoryProvider.downloadRepository,
    private val storageRepo: StorageRepository = InMemoryRepositoryProvider.storageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StorageCleanupUiState(isLoading = true))
    val uiState: StateFlow<StorageCleanupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                downloadRepo.observeDownloads(),
                storageRepo.observeStorageUsage()
            ) { downloads, storage ->
                _uiState.value.copy(
                    isLoading = false,
                    items = downloads,
                    storage = storage,
                    // Filter out any IDs that were deleted
                    selectedIds = _uiState.value.selectedIds.filter { id -> downloads.any { it.id == id } }.toSet()
                )
            }.collect { combinedState ->
                _uiState.value = combinedState
            }
        }
    }

    fun toggleSelectItem(id: String) {
        val current = _uiState.value.selectedIds
        val next = if (current.contains(id)) current - id else current + id
        _uiState.update { it.copy(selectedIds = next) }
    }

    fun selectAll() {
        val allIds = _uiState.value.items.map { it.id }.toSet()
        _uiState.update { it.copy(selectedIds = allIds) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun purgeSelected() {
        val count = _uiState.value.selectedIds.size
        if (count == 0) return

        val freedGb = count * 0.75
        val idsToPurge = _uiState.value.selectedIds

        downloadRepo.purgeDownloads(idsToPurge)
        storageRepo.recalculateStorage(freedGb)

        _uiState.update {
            it.copy(
                selectedIds = emptySet(),
                userMessage = "Purged $count items. Freed ~$freedGb GB space."
            )
        }
    }

    fun onDismissMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
