package com.pocketdl.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketdl.app.data.repository.CapturedMediaRepository
import com.pocketdl.app.data.repository.DownloadRepository
import com.pocketdl.app.data.repository.ExtensionRepository
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
import com.pocketdl.app.ui.mock.CapturedMediaMock
import com.pocketdl.app.ui.mock.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import java.util.UUID

class HomeViewModel(
    private val capturedRepo: CapturedMediaRepository = InMemoryRepositoryProvider.capturedMediaRepository,
    private val downloadRepo: DownloadRepository = InMemoryRepositoryProvider.downloadRepository,
    private val extensionRepo: ExtensionRepository = InMemoryRepositoryProvider.extensionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                extensionRepo.observeExtensionStatus(),
                downloadRepo.observeDownloads(),
                capturedRepo.observeCapturedMedia()
            ) { extStatus, downloads, captured ->
                val activeTask = downloads.firstOrNull { it.status == TaskStatus.DOWNLOADING }
                    ?: downloads.firstOrNull()

                _uiState.value.copy(
                    isLoading = false,
                    extensionStatus = extStatus,
                    activeDownload = activeTask,
                    recentCaptured = captured.take(3)
                )
            }.collect { combinedState ->
                _uiState.value = combinedState
            }
        }
    }

    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(urlInput = url) }
    }

    fun onDetectUrl(url: String, onNavigateToAnalysis: (String) -> Unit) {
        if (url.isBlank()) return

        val domain = runCatching {
            val uri = URI(url.trim())
            uri.host ?: "direct-media.com"
        }.getOrDefault("direct-media.com")

        val newId = "cap_${UUID.randomUUID().toString().take(6)}"
        val isHls = url.contains(".m3u8", ignoreCase = true)

        val newCaptured = CapturedMediaMock(
            id = newId,
            title = "Extracted Stream from $domain",
            sourceDomain = domain,
            originalUrl = url.trim(),
            durationText = if (isHls) "01:00:00" else "08:30",
            resolutionBadge = if (isHls) "1080p" else "4K",
            formatBadge = if (isHls) "HLS" else "MP4",
            estimatedSizeText = if (isHls) "1.2 GB" else "450 MB",
            isHls = isHls,
            captureTimestampText = "Just now"
        )

        capturedRepo.addCapturedMedia(newCaptured)
        _uiState.update {
            it.copy(
                urlInput = "",
                userMessage = "Captured media detected from $domain"
            )
        }
        onNavigateToAnalysis(newId)
    }

    fun onPauseResumeActiveDownload() {
        val active = _uiState.value.activeDownload ?: return
        if (active.status == TaskStatus.DOWNLOADING) {
            downloadRepo.pauseDownload(active.id)
        } else if (active.status == TaskStatus.PAUSED || active.status == TaskStatus.QUEUED) {
            downloadRepo.resumeDownload(active.id)
        }
    }

    fun onCancelActiveDownload() {
        val active = _uiState.value.activeDownload ?: return
        downloadRepo.cancelDownload(active.id)
        _uiState.update { it.copy(userMessage = "Download cancelled") }
    }

    fun onDeleteCaptured(id: String) {
        capturedRepo.deleteCapturedMedia(id)
        _uiState.update { it.copy(userMessage = "Item removed from inbox") }
    }

    fun onDismissMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
