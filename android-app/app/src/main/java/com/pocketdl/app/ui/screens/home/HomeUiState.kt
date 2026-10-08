package com.pocketdl.app.ui.screens.home

import com.pocketdl.app.ui.mock.CapturedMediaMock
import com.pocketdl.app.ui.mock.DownloadTaskMock
import com.pocketdl.app.ui.mock.ExtensionStatusMock

/**
 * UI State for HomeScreen.
 */
data class HomeUiState(
    val isLoading: Boolean = false,
    val urlInput: String = "",
    val extensionStatus: ExtensionStatusMock? = null,
    val activeDownload: DownloadTaskMock? = null,
    val recentCaptured: List<CapturedMediaMock> = emptyList(),
    val userMessage: String? = null
)
