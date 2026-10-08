package com.pocketdl.app.ui.screens.settings

import com.pocketdl.app.data.repository.DownloadEngineType

data class SettingsUiState(
    val selectedEngine: DownloadEngineType = DownloadEngineType.NATIVE_OKHTTP,
    val maxParallelDownloads: Int = 3,
    val allowCellular: Boolean = false,
    val autoDetectLinks: Boolean = true,
    val downloadPath: String = "/storage/emulated/0/Download/PocketDL/",
    val userMessage: String? = null
)
