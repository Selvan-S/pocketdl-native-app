package com.pocketdl.app.data.database

import com.pocketdl.app.data.database.entity.CapturedMediaEntity
import com.pocketdl.app.data.database.entity.DownloadTaskEntity
import com.pocketdl.app.data.database.entity.MediaVariantEntity
import com.pocketdl.app.ui.mock.CapturedMediaMock
import com.pocketdl.app.ui.mock.DownloadTaskMock
import com.pocketdl.app.ui.mock.QualityOptionMock
import com.pocketdl.app.ui.mock.TaskStatus

/**
 * Mappers between Room entities and domain/UI models.
 */
fun CapturedMediaEntity.toDomain(): CapturedMediaMock = CapturedMediaMock(
    id = id,
    title = title,
    sourceDomain = sourceDomain,
    originalUrl = originalUrl,
    durationText = durationText,
    resolutionBadge = resolutionBadge,
    formatBadge = formatBadge,
    estimatedSizeText = estimatedSizeText,
    isHls = isHls,
    captureTimestampText = captureTimestampText
)

fun CapturedMediaMock.toEntity(createdAt: Long = System.currentTimeMillis()): CapturedMediaEntity = CapturedMediaEntity(
    id = id,
    title = title,
    sourceDomain = sourceDomain,
    originalUrl = originalUrl,
    durationText = durationText,
    resolutionBadge = resolutionBadge,
    formatBadge = formatBadge,
    estimatedSizeText = estimatedSizeText,
    isHls = isHls,
    captureTimestampText = captureTimestampText,
    createdAt = createdAt
)

fun DownloadTaskEntity.toDomain(): DownloadTaskMock {
    val taskStatus = try {
        TaskStatus.valueOf(status)
    } catch (_: Exception) {
        TaskStatus.QUEUED
    }
    return DownloadTaskMock(
        id = id,
        title = title,
        sourceDomain = sourceDomain,
        statusText = statusText,
        status = taskStatus,
        progress = progress,
        downloadedSizeText = downloadedSizeText,
        totalSizeText = totalSizeText,
        speedText = speedText,
        etaText = etaText,
        resolutionBadge = resolutionBadge,
        codecBadge = codecBadge,
        localPath = localPath,
        sourceUrl = sourceUrl
    )
}

fun DownloadTaskMock.toEntity(
    capturedMediaId: String? = null,
    sourceUrl: String = this.sourceUrl,
    localPath: String? = this.localPath,
    createdAt: Long = System.currentTimeMillis(),
    completedAt: Long? = null,
    etag: String? = null,
    lastModified: String? = null
): DownloadTaskEntity = DownloadTaskEntity(
    id = id,
    capturedMediaId = capturedMediaId,
    title = title,
    sourceDomain = sourceDomain,
    sourceUrl = sourceUrl,
    status = status.name,
    statusText = statusText,
    progress = progress,
    downloadedSizeText = downloadedSizeText,
    totalSizeText = totalSizeText,
    speedText = speedText,
    etaText = etaText,
    resolutionBadge = resolutionBadge,
    codecBadge = codecBadge,
    localPath = localPath,
    createdAt = createdAt,
    completedAt = completedAt,
    etag = etag,
    lastModified = lastModified
)

fun MediaVariantEntity.toDomain(): QualityOptionMock = QualityOptionMock(
    id = id,
    label = label,
    codec = codec,
    container = container,
    sizeText = sizeText,
    bitrateText = bitrateText,
    isSelected = isSelected
)

fun QualityOptionMock.toEntity(capturedMediaId: String, downloadUrl: String = ""): MediaVariantEntity = MediaVariantEntity(
    id = id,
    capturedMediaId = capturedMediaId,
    label = label,
    codec = codec,
    container = container,
    sizeText = sizeText,
    bitrateText = bitrateText,
    downloadUrl = downloadUrl,
    isSelected = isSelected
)
