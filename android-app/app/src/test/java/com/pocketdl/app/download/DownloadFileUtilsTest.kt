package com.pocketdl.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class DownloadFileUtilsTest {

    @Test
    fun sanitizeFileName_removesPathTraversalAndIllegalChars() {
        assertEquals("video_presentation.mp4", DownloadFileUtils.sanitizeFileName("video:presentation.mp4"))
        assertEquals("test_video_.mp4", DownloadFileUtils.sanitizeFileName("../../test*video?.mp4"))
        assertEquals("safe_video.mp4", DownloadFileUtils.sanitizeFileName("..\\..\\safe_video.mp4"))
        assertEquals("my_video.mp4", DownloadFileUtils.sanitizeFileName("my|video.mp4"))
    }

    @Test
    fun sanitizeFileName_providesFallbackForEmptyNames() {
        val result = DownloadFileUtils.sanitizeFileName("", fallbackId = "task1234")
        assertTrue(result.startsWith("download_task1234"))
        assertTrue(result.endsWith(".mp4"))
    }

    @Test
    fun resolveDestinationFile_allowsSafePathsWithinDownloadDir() {
        val tempDir = File.createTempFile("pocketdl_test_dir", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

        val resolved = DownloadFileUtils.resolveDestinationFile(tempDir, "sample_clip.mp4")
        assertTrue(resolved.canonicalPath.startsWith(tempDir.canonicalPath))
        assertEquals("sample_clip.mp4", resolved.name)

        tempDir.deleteRecursively()
    }

    @Test
    fun resolveDestinationFile_rejectsPathTraversalAttack() {
        val tempDir = File.createTempFile("pocketdl_test_dir2", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

        // sanitizeFileName strips path traversal so candidate stays in directory
        val resolved = DownloadFileUtils.resolveDestinationFile(tempDir, "../../../etc/passwd.mp4")
        assertTrue(resolved.canonicalPath.startsWith(tempDir.canonicalPath))
        assertEquals("passwd.mp4", resolved.name)

        tempDir.deleteRecursively()
    }

    @Test
    fun resolvePartFile_appendsPartExtension() {
        val dest = File("/tmp/downloads/video.mp4")
        val part = DownloadFileUtils.resolvePartFile(dest)
        assertEquals("video.mp4.part", part.name)
        assertEquals(dest.parentFile, part.parentFile)
    }

    @Test
    fun finalizeDownload_atomicallyRenamesPartFile() {
        val tempDir = File.createTempFile("pocketdl_finalize_dir", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

        val destFile = File(tempDir, "final_video.mp4")
        val partFile = DownloadFileUtils.resolvePartFile(destFile)
        partFile.writeText("sample video content")

        assertTrue(partFile.exists())
        assertFalse(destFile.exists())

        val finalized = DownloadFileUtils.finalizeDownload(partFile, destFile)
        assertTrue(finalized.exists())
        assertFalse(partFile.exists())
        assertEquals("sample video content", finalized.readText())

        tempDir.deleteRecursively()
    }

    @Test
    fun resolveDestinationFile_includesTaskId_forUniqueIsolation() {
        val tempDir = File.createTempFile("pocketdl_test_isolate", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

        val res1 = DownloadFileUtils.resolveDestinationFile(tempDir, "sample_clip.mp4", "task-1")
        val res2 = DownloadFileUtils.resolveDestinationFile(tempDir, "sample_clip.mp4", "task-2")

        assertEquals("sample_clip_task-1.mp4", res1.name)
        assertEquals("sample_clip_task-2.mp4", res2.name)
        assertTrue(res1.canonicalPath.startsWith(tempDir.canonicalPath))
        assertTrue(res2.canonicalPath.startsWith(tempDir.canonicalPath))

        val part1 = DownloadFileUtils.resolvePartFile(res1)
        val part2 = DownloadFileUtils.resolvePartFile(res2)
        assertEquals("sample_clip_task-1.mp4.part", part1.name)
        assertEquals("sample_clip_task-2.mp4.part", part2.name)

        tempDir.deleteRecursively()
    }

    @Test
    fun finalizeDownload_whenDestinationFileExists_preservesExistingFileWithIndexedName() {
        val tempDir = File.createTempFile("pocketdl_collision_dir", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

        val destFile = File(tempDir, "final_video.mp4")
        destFile.writeText("existing original content")

        val partFile = File(tempDir, "final_video_task2.mp4.part")
        partFile.writeText("new download content")

        assertTrue(destFile.exists())
        assertTrue(partFile.exists())

        val finalized = DownloadFileUtils.finalizeDownload(partFile, destFile)

        assertEquals("final_video (1).mp4", finalized.name)
        assertTrue(finalized.exists())
        assertEquals("new download content", finalized.readText())

        // Original file must NOT be overwritten or deleted
        assertTrue(destFile.exists())
        assertEquals("existing original content", destFile.readText())
        assertFalse(partFile.exists())

        tempDir.deleteRecursively()
    }

    @Test
    fun deletePartFile_removesFileSafely() {
        val tempFile = File.createTempFile("pocketdl_part_test", ".part")
        assertTrue(tempFile.exists())
        DownloadFileUtils.deletePartFile(tempFile)
        assertFalse(tempFile.exists())
    }

    @Test
    fun resolveDestinationFile_urlWithDeDomain_producesMp4ExtensionFromPath() {
        val tempDir = File.createTempFile("pocketdl_de_domain_test", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val url = "https://avtshare01.rz.tu-ilmenau.de/avt-vqdb-uhd-1/test_1/segments/bigbuck_bunny_8bit_2000kbps_1080p_60.0fps_h264.mp4"
        val title = "Extracted Stream from avtshare01.rz.tu-ilmenau.de"

        val resolved = DownloadFileUtils.resolveDestinationFile(tempDir, title, "task-999", url = url)
        assertTrue("Filename must end with .mp4 and not .de", resolved.name.endsWith(".mp4"))
        assertFalse("Filename must not end with .de", resolved.name.endsWith(".de"))
        assertEquals("Extracted Stream from avtshare01.rz.tu-ilmenau.de_task-999.mp4", resolved.name)

        tempDir.deleteRecursively()
    }

    @Test
    fun resolveDestinationFile_urlWithComDomain_producesWebmExtensionFromPath() {
        val tempDir = File.createTempFile("pocketdl_com_domain_test", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val url = "https://media-server.example.com/videos/hd/clip_1080p.webm"
        val title = "Sample Clip from example.com"

        val resolved = DownloadFileUtils.resolveDestinationFile(tempDir, title, "task-888", url = url)
        assertTrue("Filename must end with .webm", resolved.name.endsWith(".webm"))
        assertEquals("Sample Clip from example.com_task-888.webm", resolved.name)

        tempDir.deleteRecursively()
    }

    @Test
    fun inferExtension_handlesQueryParametersAndFragmentsInUrl() {
        val url = "https://cdn.provider.org/assets/video_segment.mp4?token=abc123.de&quality=1080p#t=30,60"
        val ext = DownloadFileUtils.extractPathExtension(url)
        assertEquals("mp4", ext)
    }

    @Test
    fun inferExtension_ignoresMisleadingQueryParametersAndFragments() {
        val url = "https://cdn.provider.org/fetch_media?file=archive.de&other=data.zip#section.html"
        val pathExt = DownloadFileUtils.extractPathExtension(url)
        assertEquals(null, pathExt)

        val inferred = DownloadFileUtils.inferExtension(
            title = "Extracted Stream",
            url = url,
            contentType = null,
            contentDisposition = null
        )
        assertEquals("mp4", inferred)
    }

    @Test
    fun resolveDestinationFile_titleWithPeriods_preservesTitleAndAppendsExtension() {
        val tempDir = File.createTempFile("pocketdl_title_period_test", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        val url = "https://example.com/stream/episode1.mp4"
        val title = "Dr. Stone S01E01"

        val resolved = DownloadFileUtils.resolveDestinationFile(tempDir, title, "task-777", url = url)
        assertEquals("Dr. Stone S01E01_task-777.mp4", resolved.name)

        tempDir.deleteRecursively()
    }

    @Test
    fun sanitizeFileName_titleAlreadyWithExtension_preventsDuplicatedSuffix() {
        val title = "bigbuck_bunny_1080p.mp4"
        val url = "https://example.com/bigbuck_bunny_1080p.mp4"

        val sanitized = DownloadFileUtils.sanitizeFileName(title, url = url)
        assertEquals("bigbuck_bunny_1080p.mp4", sanitized)
    }

    @Test
    fun inferExtension_unknownOrMalformedMetadata_fallsBackSafely() {
        val malformedDisposition = "attachment; filename=\"\""
        val unknownMime = "application/octet-stream; charset=invalid"

        val ext = DownloadFileUtils.inferExtension(
            title = "Generic Title",
            url = "https://example.com/stream",
            contentType = unknownMime,
            contentDisposition = malformedDisposition
        )
        assertEquals("mp4", ext)
    }

    @Test
    fun inferExtension_precedenceOrder_dispositionOverTypeOverUrlOverTitle() {
        val disposition = "attachment; filename=\"custom_override.mkv\""
        val contentType = "video/webm"
        val url = "https://example.com/video.mp4"
        val title = "Dr. Stone.avi"

        // 1. Content-Disposition wins if present
        assertEquals("mkv", DownloadFileUtils.inferExtension(title, url, contentType, disposition))

        // 2. Content-Type wins over URL and Title
        assertEquals("webm", DownloadFileUtils.inferExtension(title, url, contentType, null))

        // 3. URL wins over Title
        assertEquals("mp4", DownloadFileUtils.inferExtension(title, url, null, null))

        // 4. Title wins if no higher metadata available
        assertEquals("avi", DownloadFileUtils.inferExtension(title, null, null, null))

        // 5. Default fallback
        assertEquals("mp4", DownloadFileUtils.inferExtension("Generic Title", null, null, null))
    }
}
