package com.pocketdl.app.ui.screens.media_details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketdl.app.data.repository.CapturedMediaRepository
import com.pocketdl.app.data.repository.DownloadRepository
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
import com.pocketdl.app.ui.mock.MockDataProvider
import com.pocketdl.app.ui.mock.QualityOptionMock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MediaDetailsViewModel(
    private val capturedRepo: CapturedMediaRepository = InMemoryRepositoryProvider.capturedMediaRepository,
    private val downloadRepo: DownloadRepository = InMemoryRepositoryProvider.downloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaDetailsUiState(isLoading = true))
    val uiState: StateFlow<MediaDetailsUiState> = _uiState.asStateFlow()

    private var currentMediaId: String? = null

    fun loadMedia(mediaId: String) {
        currentMediaId = mediaId
        viewModelScope.launch {
            capturedRepo.getCapturedMediaById(mediaId).collect { item ->
                if (item != null) {
                    val defaultOptions = MockDataProvider.sampleQualityOptions
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            media = item,
                            options = defaultOptions,
                            selectedOptionId = defaultOptions.firstOrNull()?.id ?: "",
                            isNotFound = false
                        )
                    }
                } else {
                    // Try fallback to first item if mock id doesn't match
                    val first = MockDataProvider.sampleCapturedList.firstOrNull()
                    if (first != null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                media = first,
                                options = MockDataProvider.sampleQualityOptions,
                                selectedOptionId = MockDataProvider.sampleQualityOptions.first().id,
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

    fun onSelectQualityOption(optionId: String) {
        _uiState.update { it.copy(selectedOptionId = optionId) }
    }

    fun onOpenQualitySheet() {
        _uiState.update { it.copy(showQualitySheet = true) }
    }

    fun onDismissQualitySheet() {
        _uiState.update { it.copy(showQualitySheet = false) }
    }

    fun onConfirmDownload(option: QualityOptionMock) {
        val media = _uiState.value.media ?: return
        downloadRepo.enqueueDownload(
            title = media.title,
            url = media.originalUrl,
            sourceDomain = media.sourceDomain,
            quality = option
        )
        _uiState.update {
            it.copy(
                showQualitySheet = false,
                downloadEnqueued = true,
                feedbackMessage = "Added to download queue: ${media.title} (${option.label})"
            )
        }
    }

    fun onDismissFeedback() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }
}
