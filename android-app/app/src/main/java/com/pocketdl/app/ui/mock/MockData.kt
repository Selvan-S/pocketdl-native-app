package com.pocketdl.app.ui.mock

data class CapturedMediaMock(
    val id: String,
    val title: String,
    val sourceDomain: String,
    val originalUrl: String,
    val durationText: String,
    val resolutionBadge: String,
    val formatBadge: String,
    val estimatedSizeText: String,
    val isHls: Boolean = false,
    val captureTimestampText: String
)

data class DownloadTaskMock(
    val id: String,
    val title: String,
    val sourceDomain: String,
    val statusText: String,
    val status: TaskStatus,
    val progress: Float,
    val downloadedSizeText: String,
    val totalSizeText: String,
    val speedText: String,
    val etaText: String,
    val resolutionBadge: String,
    val codecBadge: String,
    val localPath: String? = null,
    val sourceUrl: String = ""
)

enum class TaskStatus {
    DOWNLOADING,
    PAUSED,
    QUEUED,
    COMPLETED,
    FAILED
}

data class ExtensionStatusMock(
    val isConnected: Boolean,
    val version: String,
    val listenerPort: Int,
    val pairedBrowserName: String,
    val activeCapturesCount: Int,
    val lastHeartbeatText: String
)

data class StorageUsageMock(
    val totalGb: Double,
    val mediaGb: Double,
    val appGb: Double,
    val freeGb: Double
)

data class QualityOptionMock(
    val id: String,
    val label: String,
    val codec: String,
    val container: String,
    val sizeText: String,
    val bitrateText: String,
    val isSelected: Boolean = false
)

object MockDataProvider {
    val sampleExtensionStatus = ExtensionStatusMock(
        isConnected = true,
        version = "v1.4.2",
        listenerPort = 8080,
        pairedBrowserName = "Chrome Desktop (macOS)",
        activeCapturesCount = 3,
        lastHeartbeatText = "2 sec ago"
    )

    val sampleCapturedList = listOf(
        CapturedMediaMock(
            id = "cap_1",
            title = "Keynote 2026 — Next Gen Native Android Architecture & Compose",
            sourceDomain = "youtube.com",
            originalUrl = "https://youtube.com/watch?v=demo123",
            durationText = "42:15",
            resolutionBadge = "4K",
            formatBadge = "WEBM",
            estimatedSizeText = "1.8 GB",
            isHls = false,
            captureTimestampText = "5 mins ago"
        ),
        CapturedMediaMock(
            id = "cap_2",
            title = "Linux Kernel Live Stream HLS Master Playlist",
            sourceDomain = "stream.cdn.org",
            originalUrl = "https://stream.cdn.org/live/master.m3u8",
            durationText = "01:12:00",
            resolutionBadge = "1080p",
            formatBadge = "HLS",
            estimatedSizeText = "2.4 GB",
            isHls = true,
            captureTimestampText = "12 mins ago"
        ),
        CapturedMediaMock(
            id = "cap_3",
            title = "Deep Dive into Multi-Threaded Engine Parsers #104",
            sourceDomain = "podcasts.dev",
            originalUrl = "https://podcasts.dev/episodes/104.mp3",
            durationText = "28:40",
            resolutionBadge = "AUDIO",
            formatBadge = "MP3",
            estimatedSizeText = "65 MB",
            isHls = false,
            captureTimestampText = "1 hour ago"
        )
    )

    val sampleDownloadsList = listOf(
        DownloadTaskMock(
            id = "dl_1",
            title = "SpaceX Starship Orbital Test Flight Camera 1",
            sourceDomain = "spacex.com",
            statusText = "Downloading",
            status = TaskStatus.DOWNLOADING,
            progress = 0.68f,
            downloadedSizeText = "850 MB",
            totalSizeText = "1.25 GB",
            speedText = "14.2 MB/s",
            etaText = "00:32",
            resolutionBadge = "4K 60fps",
            codecBadge = "AV1"
        ),
        DownloadTaskMock(
            id = "dl_2",
            title = "Android Jetpack Compose Animation Masterclass",
            sourceDomain = "developer.android.com",
            statusText = "Paused",
            status = TaskStatus.PAUSED,
            progress = 0.42f,
            downloadedSizeText = "210 MB",
            totalSizeText = "500 MB",
            speedText = "0 KB/s",
            etaText = "Paused",
            resolutionBadge = "1080p",
            codecBadge = "H.264"
        ),
        DownloadTaskMock(
            id = "dl_3",
            title = "Open Source AI Benchmark Suite Release Video",
            sourceDomain = "huggingface.co",
            statusText = "Completed",
            status = TaskStatus.COMPLETED,
            progress = 1.0f,
            downloadedSizeText = "640 MB",
            totalSizeText = "640 MB",
            speedText = "Done",
            etaText = "Finished",
            resolutionBadge = "1080p",
            codecBadge = "VP9"
        ),
        DownloadTaskMock(
            id = "dl_4",
            title = "High Velocity Data Processing in Rust & C++",
            sourceDomain = "techconf.io",
            statusText = "Queued",
            status = TaskStatus.QUEUED,
            progress = 0.0f,
            downloadedSizeText = "0 MB",
            totalSizeText = "920 MB",
            speedText = "Waiting",
            etaText = "In Queue",
            resolutionBadge = "4K",
            codecBadge = "HEVC"
        )
    )

    val sampleQualityOptions = listOf(
        QualityOptionMock(
            id = "q_2160p",
            label = "2160p 60fps Ultra HD",
            codec = "AV1",
            container = "WEBM",
            sizeText = "1.8 GB",
            bitrateText = "18.5 Mbps",
            isSelected = true
        ),
        QualityOptionMock(
            id = "q_1080p",
            label = "1080p 60fps Full HD",
            codec = "H.264",
            container = "MP4",
            sizeText = "650 MB",
            bitrateText = "6.2 Mbps"
        ),
        QualityOptionMock(
            id = "q_720p",
            label = "720p HD Standard",
            codec = "H.264",
            container = "MP4",
            sizeText = "320 MB",
            bitrateText = "3.1 Mbps"
        ),
        QualityOptionMock(
            id = "q_audio",
            label = "Audio Only (Original Bitrate)",
            codec = "AAC",
            container = "M4A",
            sizeText = "48 MB",
            bitrateText = "256 kbps"
        )
    )

    val sampleStorage = StorageUsageMock(
        totalGb = 128.0,
        mediaGb = 34.2,
        appGb = 1.8,
        freeGb = 92.0
    )
}
