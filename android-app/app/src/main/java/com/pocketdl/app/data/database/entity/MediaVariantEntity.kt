package com.pocketdl.app.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an available media quality/variant linked to a parent captured item.
 */
@Entity(
    tableName = "media_variants",
    foreignKeys = [
        ForeignKey(
            entity = CapturedMediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["captured_media_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["captured_media_id"], name = "idx_media_variants_captured_media_id")
    ]
)
data class MediaVariantEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "captured_media_id")
    val capturedMediaId: String,

    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "codec")
    val codec: String,

    @ColumnInfo(name = "container")
    val container: String,

    @ColumnInfo(name = "size_text")
    val sizeText: String,

    @ColumnInfo(name = "bitrate_text")
    val bitrateText: String,

    @ColumnInfo(name = "download_url")
    val downloadUrl: String = "",

    @ColumnInfo(name = "is_selected")
    val isSelected: Boolean = false
)
