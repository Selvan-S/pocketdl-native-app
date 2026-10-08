package com.pocketdl.app.ui.screens.storage

import com.pocketdl.app.ui.mock.DownloadTaskMock
import com.pocketdl.app.ui.mock.StorageUsageMock

data class StorageCleanupUiState(
    val isLoading: Boolean = false,
    val storage: StorageUsageMock? = null,
    val items: List<DownloadTaskMock> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val userMessage: String? = null
)
