package com.pocketdl.app.ui.screens.downloads

import com.pocketdl.app.ui.mock.DownloadTaskMock
import com.pocketdl.app.ui.mock.StorageUsageMock

enum class DownloadFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    COMPLETED("Completed"),
    QUEUED("Queued"),
    FAILED("Failed")
}

data class DownloadsUiState(
    val isLoading: Boolean = false,
    val filter: DownloadFilter = DownloadFilter.ALL,
    val items: List<DownloadTaskMock> = emptyList(),
    val storage: StorageUsageMock? = null,
    val totalCount: Int = 0,
    val activeCount: Int = 0,
    val completedCount: Int = 0,
    val queuedCount: Int = 0,
    val failedCount: Int = 0,
    val userMessage: String? = null
)
