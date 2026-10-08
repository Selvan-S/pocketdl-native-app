package com.pocketdl.app

import com.pocketdl.app.data.database.entity.CapturedMediaEntity
import com.pocketdl.app.data.database.entity.DownloadTaskEntity
import com.pocketdl.app.data.database.entity.MediaVariantEntity
import com.pocketdl.app.data.database.migrations.DatabaseMigrations
import com.pocketdl.app.data.database.toDomain
import com.pocketdl.app.data.database.toEntity
import com.pocketdl.app.ui.mock.CapturedMediaMock
import com.pocketdl.app.ui.mock.DownloadTaskMock
import com.pocketdl.app.ui.mock.QualityOptionMock
import com.pocketdl.app.ui.mock.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomPersistenceUnitTest {

    @Test
    fun capturedMediaEntity_mappingBidirectional() {
        val originalMock = CapturedMediaMock(
            id = "test_cap_1",
            title = "Sample Video Title",
            sourceDomain = "example.com",
            originalUrl = "https://example.com/stream.m3u8",
            durationText = "10:00",
            resolutionBadge = "1080p",
            formatBadge = "HLS",
            estimatedSizeText = "120 MB",
            isHls = true,
            captureTimestampText = "Just now"
        )

        val entity = originalMock.toEntity(createdAt = 123456789L)
        assertEquals("test_cap_1", entity.id)
        assertEquals("Sample Video Title", entity.title)
        assertEquals("example.com", entity.sourceDomain)
        assertEquals("https://example.com/stream.m3u8", entity.originalUrl)
        assertEquals("10:00", entity.durationText)
        assertEquals("1080p", entity.resolutionBadge)
        assertEquals("HLS", entity.formatBadge)
        assertEquals("120 MB", entity.estimatedSizeText)
        assertTrue(entity.isHls)
        assertEquals("Just now", entity.captureTimestampText)
        assertEquals(123456789L, entity.createdAt)

        val domain = entity.toDomain()
        assertEquals(originalMock.id, domain.id)
        assertEquals(originalMock.title, domain.title)
        assertEquals(originalMock.sourceDomain, domain.sourceDomain)
        assertEquals(originalMock.originalUrl, domain.originalUrl)
        assertEquals(originalMock.durationText, domain.durationText)
        assertEquals(originalMock.resolutionBadge, domain.resolutionBadge)
        assertEquals(originalMock.formatBadge, domain.formatBadge)
        assertEquals(originalMock.estimatedSizeText, domain.estimatedSizeText)
        assertEquals(originalMock.isHls, domain.isHls)
        assertEquals(originalMock.captureTimestampText, domain.captureTimestampText)
    }

    @Test
    fun downloadTaskEntity_mappingBidirectional() {
        val originalMock = DownloadTaskMock(
            id = "test_dl_1",
            title = "Download Target File",
            sourceDomain = "download.org",
            statusText = "Downloading",
            status = TaskStatus.DOWNLOADING,
            progress = 0.55f,
            downloadedSizeText = "55 MB",
            totalSizeText = "100 MB",
            speedText = "5.2 MB/s",
            etaText = "00:09",
            resolutionBadge = "720p",
            codecBadge = "H.264"
        )

        val entity = originalMock.toEntity(
            capturedMediaId = "cap_source_1",
            sourceUrl = "https://download.org/file.mp4",
            localPath = "/sdcard/Downloads/file.mp4",
            createdAt = 5000L
        )

        assertEquals("test_dl_1", entity.id)
        assertEquals("cap_source_1", entity.capturedMediaId)
        assertEquals("Download Target File", entity.title)
        assertEquals("download.org", entity.sourceDomain)
        assertEquals("https://download.org/file.mp4", entity.sourceUrl)
        assertEquals(TaskStatus.DOWNLOADING.name, entity.status)
        assertEquals(0.55f, entity.progress)
        assertEquals("55 MB", entity.downloadedSizeText)
        assertEquals("100 MB", entity.totalSizeText)
        assertEquals("5.2 MB/s", entity.speedText)
        assertEquals("00:09", entity.etaText)
        assertEquals("720p", entity.resolutionBadge)
        assertEquals("H.264", entity.codecBadge)
        assertEquals("/sdcard/Downloads/file.mp4", entity.localPath)
        assertEquals(5000L, entity.createdAt)

        val domain = entity.toDomain()
        assertEquals(originalMock.id, domain.id)
        assertEquals(originalMock.title, domain.title)
        assertEquals(originalMock.sourceDomain, domain.sourceDomain)
        assertEquals(TaskStatus.DOWNLOADING, domain.status)
        assertEquals(originalMock.statusText, domain.statusText)
        assertEquals(originalMock.progress, domain.progress)
        assertEquals(originalMock.downloadedSizeText, domain.downloadedSizeText)
        assertEquals(originalMock.totalSizeText, domain.totalSizeText)
        assertEquals(originalMock.speedText, domain.speedText)
        assertEquals(originalMock.etaText, domain.etaText)
        assertEquals(originalMock.resolutionBadge, domain.resolutionBadge)
        assertEquals(originalMock.codecBadge, domain.codecBadge)
    }

    @Test
    fun mediaVariantEntity_mappingBidirectional() {
        val quality = QualityOptionMock(
            id = "q_1080p",
            label = "1080p 60fps Full HD",
            codec = "H.264",
            container = "MP4",
            sizeText = "650 MB",
            bitrateText = "6.2 Mbps",
            isSelected = true
        )

        val entity = quality.toEntity(
            capturedMediaId = "cap_parent",
            downloadUrl = "https://cdn.example.com/1080p.mp4"
        )

        assertEquals("q_1080p", entity.id)
        assertEquals("cap_parent", entity.capturedMediaId)
        assertEquals("1080p 60fps Full HD", entity.label)
        assertEquals("H.264", entity.codec)
        assertEquals("MP4", entity.container)
        assertEquals("650 MB", entity.sizeText)
        assertEquals("6.2 Mbps", entity.bitrateText)
        assertEquals("https://cdn.example.com/1080p.mp4", entity.downloadUrl)
        assertTrue(entity.isSelected)

        val domain = entity.toDomain()
        assertEquals(quality.id, domain.id)
        assertEquals(quality.label, domain.label)
        assertEquals(quality.codec, domain.codec)
        assertEquals(quality.container, domain.container)
        assertEquals(quality.sizeText, domain.sizeText)
        assertEquals(quality.bitrateText, domain.bitrateText)
        assertTrue(domain.isSelected)
    }

    @Test
    fun databaseMigrations_structureIsDefined() {
        assertNotNull(DatabaseMigrations.MIGRATION_1_2)
        assertEquals(1, DatabaseMigrations.MIGRATION_1_2.startVersion)
        assertEquals(2, DatabaseMigrations.MIGRATION_1_2.endVersion)
        assertEquals(1, DatabaseMigrations.ALL_MIGRATIONS.size)
    }
}
