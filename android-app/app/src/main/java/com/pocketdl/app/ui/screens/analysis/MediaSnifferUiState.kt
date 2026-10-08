package com.pocketdl.app.ui.screens.analysis

import com.pocketdl.app.ui.mock.CapturedMediaMock

data class MediaSnifferUiState(
    val isLoading: Boolean = false,
    val media: CapturedMediaMock? = null,
    val statusCode: Int = 200,
    val statusText: String = "HTTP 200 OK",
    val headers: List<Pair<String, String>> = emptyList(),
    val isReanalyzing: Boolean = false,
    val timestampText: String = "Live socket inspection"
)
