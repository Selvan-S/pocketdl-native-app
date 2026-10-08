package com.pocketdl.app.ui.screens.media_details

import com.pocketdl.app.ui.mock.CapturedMediaMock
import com.pocketdl.app.ui.mock.QualityOptionMock

data class MediaDetailsUiState(
    val isLoading: Boolean = false,
    val media: CapturedMediaMock? = null,
    val options: List<QualityOptionMock> = emptyList(),
    val selectedOptionId: String = "",
    val showQualitySheet: Boolean = false,
    val downloadEnqueued: Boolean = false,
    val feedbackMessage: String? = null,
    val isNotFound: Boolean = false
)
