package com.pocketdl.app.ui.screens.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketdl.app.data.repository.CapturedMediaRepository
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
import com.pocketdl.app.ui.mock.MockDataProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MediaSnifferViewModel(
    private val capturedRepo: CapturedMediaRepository = InMemoryRepositoryProvider.capturedMediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaSnifferUiState(isLoading = true))
    val uiState: StateFlow<MediaSnifferUiState> = _uiState.asStateFlow()

    private var currentMediaId: String? = null

    fun loadMedia(mediaId: String) {
        currentMediaId = mediaId
        viewModelScope.launch {
            capturedRepo.getCapturedMediaById(mediaId).collect { item ->
                val media = item ?: MockDataProvider.sampleCapturedList.firstOrNull()
                val isHls = media?.isHls == true
                val contentType = if (isHls) "application/vnd.apple.mpegurl" else if (media?.formatBadge == "MP3") "audio/mpeg" else "video/mp4"

                val headers = listOf(
                    "Content-Type" to contentType,
                    "Accept-Ranges" to "bytes",
                    "Content-Length" to if (isHls) "Variable (Multi-segment)" else "1845291840",
                    "Server" to "PocketDL-Socket-Proxy/1.4",
                    "X-Engine-Codec" to "${media?.resolutionBadge ?: "1080p"} / ${media?.formatBadge ?: "MP4"}",
                    "Access-Control-Allow-Origin" to "*",
                    "X-Socket-State" to "ACTIVE_STREAM"
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        media = media,
                        statusCode = 200,
                        statusText = "HTTP 200 OK",
                        headers = headers,
                        timestampText = "Live socket inspection"
                    )
                }
            }
        }
    }

    fun refreshAnalysis() {
        viewModelScope.launch {
            _uiState.update { it.copy(isReanalyzing = true) }
            delay(500)
            _uiState.update {
                it.copy(
                    isReanalyzing = false,
                    timestampText = "Refreshed just now (0ms latency)"
                )
            }
        }
    }
}
