package com.pocketdl.app.ui.screens.queue

import com.pocketdl.app.ui.mock.DownloadTaskMock

data class QueueUiState(
    val isLoading: Boolean = false,
    val queuedTasks: List<DownloadTaskMock> = emptyList(),
    val maxParallel: Int = 3,
    val userMessage: String? = null
)
