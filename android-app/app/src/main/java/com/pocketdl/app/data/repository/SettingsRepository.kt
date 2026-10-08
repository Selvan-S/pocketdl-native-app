package com.pocketdl.app.data.repository

import kotlinx.coroutines.flow.Flow

enum class DownloadEngineType(val displayName: String) {
    NATIVE_OKHTTP("Native OkHttp"),
    ARIA2("Aria2 Engine"),
    FFMPEG_HLS("FFmpeg HLS")
}

data class SettingsData(
    val engine: DownloadEngineType = DownloadEngineType.NATIVE_OKHTTP,
    val maxParallelDownloads: Int = 3,
    val allowCellular: Boolean = false,
    val autoDetectLinks: Boolean = true,
    val downloadPath: String = "/storage/emulated/0/Download/PocketDL/"
)

/**
 * Repository interface for managing user preferences and download engine configurations.
 */
interface SettingsRepository : Repository {
    fun observeSettings(): Flow<SettingsData>
    fun setEngine(engine: DownloadEngineType)
    fun setMaxParallelDownloads(count: Int)
    fun setAllowCellular(allow: Boolean)
    fun setAutoDetectLinks(detect: Boolean)
}
