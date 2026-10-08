package com.pocketdl.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing persistent download task metadata and queue lifecycle state.
 */
@Entity(
    tableName = "download_tasks",
    indices = [
        Index(value = ["status"], name = "idx_download_tasks_status")
    ]
)
data class DownloadTaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "captured_media_id")
    val capturedMediaId: String? = null,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "source_domain")
    val sourceDomain: String,

    @ColumnInfo(name = "source_url")
    val sourceUrl: String = "",

    @ColumnInfo(name = "status")
    val status: String, // Matches TaskStatus name: DOWNLOADING, PAUSED, QUEUED, COMPLETED, FAILED

    @ColumnInfo(name = "status_text")
    val statusText: String,

    @ColumnInfo(name = "progress")
    val progress: Float,

    @ColumnInfo(name = "downloaded_size_text")
    val downloadedSizeText: String,

    @ColumnInfo(name = "total_size_text")
    val totalSizeText: String,

    @ColumnInfo(name = "speed_text")
    val speedText: String,

    @ColumnInfo(name = "eta_text")
    val etaText: String,

    @ColumnInfo(name = "resolution_badge")
    val resolutionBadge: String,

    @ColumnInfo(name = "codec_badge")
    val codecBadge: String,

    @ColumnInfo(name = "local_path")
    val localPath: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null
)
