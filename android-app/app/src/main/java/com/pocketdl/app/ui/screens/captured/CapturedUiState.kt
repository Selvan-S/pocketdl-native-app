package com.pocketdl.app.ui.screens.captured

import com.pocketdl.app.ui.mock.CapturedMediaMock
import com.pocketdl.app.ui.mock.QualityOptionMock

enum class CapturedFilter(val label: String) {
    ALL("All"),
    VIDEO("Video"),
    HLS("HLS"),
    AUDIO("Audio")
}

data class CapturedUiState(
    val isLoading: Boolean = false,
    val filter: CapturedFilter = CapturedFilter.ALL,
    val items: List<CapturedMediaMock> = emptyList(),
    val totalCount: Int = 0,
    val videoCount: Int = 0,
    val hlsCount: Int = 0,
    val audioCount: Int = 0,
    val selectedMediaForQuality: CapturedMediaMock? = null,
    val qualityOptions: List<QualityOptionMock> = emptyList(),
    val showQualitySheet: Boolean = false,
    val userMessage: String? = null
)
