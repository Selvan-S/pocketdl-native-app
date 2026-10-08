package com.pocketdl.app.ui.screens.captured

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketdl.app.data.repository.CapturedMediaRepository
import com.pocketdl.app.data.repository.DownloadRepository
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
import com.pocketdl.app.ui.mock.CapturedMediaMock
import com.pocketdl.app.ui.mock.MockDataProvider
import com.pocketdl.app.ui.mock.QualityOptionMock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CapturedViewModel(
    private val capturedRepo: CapturedMediaRepository = InMemoryRepositoryProvider.capturedMediaRepository,
    private val downloadRepo: DownloadRepository = InMemoryRepositoryProvider.downloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CapturedUiState(isLoading = true))
    val uiState: StateFlow<CapturedUiState> = _uiState.asStateFlow()

    private var allCapturedItems: List<CapturedMediaMock> = emptyList()

    init {
        viewModelScope.launch {
            capturedRepo.observeCapturedMedia().collect { list ->
                allCapturedItems = list
                updateFilteredList()
            }
        }
    }

    fun onFilterSelected(filter: CapturedFilter) {
        _uiState.update { it.copy(filter = filter) }
        updateFilteredList()
    }

    private fun updateFilteredList() {
        val currentFilter = _uiState.value.filter

        val filtered = when (currentFilter) {
            CapturedFilter.ALL -> allCapturedItems
            CapturedFilter.VIDEO -> allCapturedItems.filter { !it.isHls && it.formatBadge != "MP3" }
            CapturedFilter.HLS -> allCapturedItems.filter { it.isHls || it.formatBadge == "HLS" }
            CapturedFilter.AUDIO -> allCapturedItems.filter { it.formatBadge == "MP3" || it.resolutionBadge == "AUDIO" }
        }

        val total = allCapturedItems.size
        val video = allCapturedItems.count { !it.isHls && it.formatBadge != "MP3" }
        val hls = allCapturedItems.count { it.isHls || it.formatBadge == "HLS" }
        val audio = allCapturedItems.count { it.formatBadge == "MP3" || it.resolutionBadge == "AUDIO" }

        _uiState.update {
            it.copy(
                isLoading = false,
                items = filtered,
                totalCount = total,
                videoCount = video,
                hlsCount = hls,
                audioCount = audio
            )
        }
    }

    fun onOpenQualitySheet(item: CapturedMediaMock) {
        _uiState.update {
            it.copy(
                selectedMediaForQuality = item,
                qualityOptions = MockDataProvider.sampleQualityOptions,
                showQualitySheet = true
            )
        }
    }

    fun onDismissQualitySheet() {
        _uiState.update {
            it.copy(
                showQualitySheet = false,
                selectedMediaForQuality = null
            )
        }
    }

    fun onConfirmQuality(option: QualityOptionMock) {
        val item = _uiState.value.selectedMediaForQuality
        if (item != null) {
            downloadRepo.enqueueDownload(
                title = item.title,
                url = item.originalUrl,
                sourceDomain = item.sourceDomain,
                quality = option
            )
            _uiState.update {
                it.copy(
                    showQualitySheet = false,
                    selectedMediaForQuality = null,
                    userMessage = "Enqueued download for '${item.title}' (${option.label})"
                )
            }
        } else {
            // General batch confirm
            val currentItems = _uiState.value.items
            currentItems.forEach { media ->
                downloadRepo.enqueueDownload(
                    title = media.title,
                    url = media.originalUrl,
                    sourceDomain = media.sourceDomain,
                    quality = option
                )
            }
            _uiState.update {
                it.copy(
                    showQualitySheet = false,
                    userMessage = "Enqueued ${currentItems.size} items for download"
                )
            }
        }
    }

    fun onDownloadAllClick() {
        if (_uiState.value.items.isEmpty()) return
        _uiState.update {
            it.copy(
                selectedMediaForQuality = null,
                qualityOptions = MockDataProvider.sampleQualityOptions,
                showQualitySheet = true
            )
        }
    }

    fun onDeleteCaptured(id: String) {
        capturedRepo.deleteCapturedMedia(id)
        _uiState.update { it.copy(userMessage = "Captured item removed") }
    }

    fun onDismissMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
