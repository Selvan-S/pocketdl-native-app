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
    fun deletePartFile_removesFileSafely() {
        val tempFile = File.createTempFile("pocketdl_part_test", ".part")
        assertTrue(tempFile.exists())
        DownloadFileUtils.deletePartFile(tempFile)
        assertFalse(tempFile.exists())
    }
}
