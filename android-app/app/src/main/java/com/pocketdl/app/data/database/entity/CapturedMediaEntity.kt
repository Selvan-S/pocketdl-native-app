package com.pocketdl.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing persistent metadata for a captured media item.
 */
@Entity(tableName = "captured_media")
data class CapturedMediaEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "source_domain")
    val sourceDomain: String,

    @ColumnInfo(name = "original_url")
    val originalUrl: String,

    @ColumnInfo(name = "duration_text")
    val durationText: String,

    @ColumnInfo(name = "resolution_badge")
    val resolutionBadge: String,

    @ColumnInfo(name = "format_badge")
    val formatBadge: String,

    @ColumnInfo(name = "estimated_size_text")
    val estimatedSizeText: String,

    @ColumnInfo(name = "is_hls")
    val isHls: Boolean = false,

    @ColumnInfo(name = "capture_timestamp_text")
    val captureTimestampText: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
