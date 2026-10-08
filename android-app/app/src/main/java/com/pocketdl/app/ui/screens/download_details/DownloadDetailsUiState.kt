package com.pocketdl.app.ui.screens.download_details

import com.pocketdl.app.ui.mock.DownloadTaskMock

data class DownloadDetailsUiState(
    val isLoading: Boolean = false,
    val task: DownloadTaskMock? = null,
    val isNotFound: Boolean = false,
    val userMessage: String? = null
)
