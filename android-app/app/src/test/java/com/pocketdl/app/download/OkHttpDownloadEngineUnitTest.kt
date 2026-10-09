package com.pocketdl.app.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class OkHttpDownloadEngineUnitTest {

    private lateinit var server: MockWebServer
    private lateinit var engine: OkHttpDownloadEngine
    private lateinit var tempDir: File

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        engine = OkHttpDownloadEngine(
            client = OkHttpClient.Builder().followRedirects(true).build(),
            ioDispatcher = Dispatchers.IO
        )
        tempDir = File.createTempFile("okhttp_test_dir", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
    }

    @After
    fun teardown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun download_fullFile200_downloadsAndFinalizes() = runBlocking {
        val fileContent = "0123456789".repeat(100)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", fileContent.length.toString())
                .setHeader("ETag", "\"strong-etag-123\"")
                .setBody(fileContent)
        )

        val destFile = File(tempDir, "video.mp4")
        val progressUpdates = mutableListOf<DownloadProgressUpdate>()

        val result = engine.download(
            taskId = "task_200",
            url = server.url("/video.mp4").toString(),
            destinationFile = destFile,
            onProgress = { progressUpdates.add(it) }
        )

        assertTrue("Result should be Success", result is DownloadResult.Success)
        val success = result as DownloadResult.Success
        assertEquals(fileContent.length.toLong(), success.totalBytes)
        assertEquals("\"strong-etag-123\"", success.etag)
        assertEquals(fileContent, destFile.readText())
        assertFalse(DownloadFileUtils.resolvePartFile(destFile).exists())

        // Verify request headers sent
        val request = server.takeRequest()
        assertEquals("identity", request.getHeader("Accept-Encoding"))
        assertNull("No Range header should be sent for fresh download", request.getHeader("Range"))
    }

    @Test
    fun download_strongEtagSentInIfRange() = runBlocking {
        val destFile = File(tempDir, "resume_strong.mp4")
        val partFile = DownloadFileUtils.resolvePartFile(destFile)
        partFile.writeText("existing_10_bytes") // 17 bytes

        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 17-26/27")
                .setHeader("Content-Length", "10")
                .setBody("new_bytes!")
        )

        engine.download(
            taskId = "task_if_range_strong",
            url = server.url("/stream_strong.mp4").toString(),
            destinationFile = destFile,
            existingEtag = "\"strong_etag_val\"",
            onProgress = {}
        )

        val req1 = server.takeRequest()
        assertEquals("bytes=17-", req1.getHeader("Range"))
        assertEquals("\"strong_etag_val\"", req1.getHeader("If-Range"))
    }

    @Test
    fun download_weakEtagIgnored_fallsBackToLastModified() = runBlocking {
        val destFile = File(tempDir, "resume_weak.mp4")
        val partFile = DownloadFileUtils.resolvePartFile(destFile)
        partFile.writeText("existing_10_bytes")

        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 17-26/27")
                .setHeader("Content-Length", "10")
                .setBody("new_bytes!")
        )

        engine.download(
            taskId = "task_if_range_weak",
            url = server.url("/stream_weak.mp4").toString(),
            destinationFile = destFile,
            existingEtag = "W/\"weak_etag_val\"",
            existingLastModified = "Wed, 21 Oct 2025 07:28:00 GMT",
            onProgress = {}
        )

        val req2 = server.takeRequest()
        assertEquals("bytes=17-", req2.getHeader("Range"))
        assertEquals("Wed, 21 Oct 2025 07:28:00 GMT", req2.getHeader("If-Range"))
    }

    @Test
    fun download_valid206_appendsAndFinalizes() = runBlocking {
        val destFile = File(tempDir, "resume_video.mp4")
        val partFile = DownloadFileUtils.resolvePartFile(destFile)
        partFile.writeText("PART1_") // 6 bytes

        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 6-11/12")
                .setHeader("Content-Length", "6")
                .setBody("PART2_")
        )

        val result = engine.download(
            taskId = "task_resume",
            url = server.url("/video.mp4").toString(),
            destinationFile = destFile,
            onProgress = {}
        )

        val request = server.takeRequest()
        assertEquals("bytes=6-", request.getHeader("Range"))
        assertTrue(result is DownloadResult.Success)
        assertEquals("PART1_PART2_", destFile.readText())
        assertFalse(partFile.exists())
    }

    @Test
    fun download_mismatched206ContentRange_treatsAsUnsafeAndRestartsFromZero() = runBlocking {
        val destFile = File(tempDir, "unsafe_range.mp4")
        val partFile = DownloadFileUtils.resolvePartFile(destFile)
        partFile.writeText("EXISTING_BYTES") // 14 bytes

        // First response: bad start offset (server returns offset 0 instead of 14)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 0-19/20")
                .setHeader("Content-Length", "20")
                .setBody("ALL_NEW_FULL_STREAM_")
        )

        // Second response for the automatic safe restart from 0
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", "20")
                .setBody("ALL_NEW_FULL_STREAM_")
        )

        val result = engine.download(
            taskId = "task_mismatch",
            url = server.url("/video.mp4").toString(),
            destinationFile = destFile,
            onProgress = {}
        )

        assertTrue(result is DownloadResult.Success)
        assertEquals("ALL_NEW_FULL_STREAM_", destFile.readText())
    }

    @Test
    fun download_http416_whenFileAlreadyComplete_finalizesSuccessfully() = runBlocking {
        val destFile = File(tempDir, "already_complete.mp4")
        val partFile = DownloadFileUtils.resolvePartFile(destFile)
        val completeContent = "EXACT_COMPLETE_CONTENT"
        partFile.writeText(completeContent)

        // Server responds 416 with Content-Range: bytes */22
        server.enqueue(
            MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */${completeContent.length}")
        )

        val result = engine.download(
            taskId = "task_416_complete",
            url = server.url("/video.mp4").toString(),
            destinationFile = destFile,
            onProgress = {}
        )

        assertTrue("Should succeed when 416 indicates local file is already full size", result is DownloadResult.Success)
        assertEquals(completeContent, destFile.readText())
        assertFalse(partFile.exists())
    }

    @Test
    fun download_httpRedirect_followsRedirectAutomatically() = runBlocking {
        val finalContent = "REDIRECTED_VIDEO_DATA"
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "/redirected_target.mp4")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", finalContent.length.toString())
                .setBody(finalContent)
        )

        val destFile = File(tempDir, "redirect_test.mp4")
        val result = engine.download(
            taskId = "task_redirect",
            url = server.url("/initial.mp4").toString(),
            destinationFile = destFile,
            onProgress = {}
        )

        assertTrue(result is DownloadResult.Success)
        assertEquals(finalContent, destFile.readText())
    }

    @Test
    fun pauseAndCancel_doNotReportAsNetworkFailure() = runBlocking {
        val destFile = File(tempDir, "pause_test.mp4")
        val largeContent = "x".repeat(1024 * 1024) // 1MB

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", largeContent.length.toString())
                .throttleBody(1024, 50, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setBody(largeContent)
        )

        val downloadJob = async(Dispatchers.IO) {
            engine.download(
                taskId = "task_pause",
                url = server.url("/big.mp4").toString(),
                destinationFile = destFile,
                onProgress = {}
            )
        }

        delay(80) // Let download start and write first chunk
        engine.pause("task_pause")

        val result = downloadJob.await()
        assertEquals(DownloadResult.Paused, result)
        // Partial file is preserved
        assertTrue(DownloadFileUtils.resolvePartFile(destFile).exists())

        // Now test Cancel
        val cancelDest = File(tempDir, "cancel_test.mp4")
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", largeContent.length.toString())
                .throttleBody(1024, 50, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setBody(largeContent)
        )

        val cancelJob = async(Dispatchers.IO) {
            engine.download(
                taskId = "task_cancel",
                url = server.url("/big2.mp4").toString(),
                destinationFile = cancelDest,
                onProgress = {}
            )
        }

        delay(50)
        engine.cancel("task_cancel")

        val cancelResult = cancelJob.await()
        assertEquals(DownloadResult.Cancelled, cancelResult)
        // Partial file is deleted on cancel
        assertFalse(DownloadFileUtils.resolvePartFile(cancelDest).exists())
    }

    @Test
    fun download_http403Forbidden_returnsFailureAndLeavesNoFiles() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setStatus("HTTP/1.1 403 Forbidden")
                .setBody("AccessDenied")
        )

        val destFile = File(tempDir, "forbidden.mp4")
        val partFile = DownloadFileUtils.resolvePartFile(destFile)

        val result = engine.download(
            taskId = "task_403",
            url = server.url("/forbidden.mp4").toString(),
            destinationFile = destFile,
            onProgress = {}
        )

        assertTrue("Should return DownloadResult.Failure for HTTP 403", result is DownloadResult.Failure)
        val failure = result as DownloadResult.Failure
        assertTrue("Error should mention 403", failure.error.message?.contains("403") == true)
        assertFalse("Final file must not exist on failure", destFile.exists())
        assertFalse("Part file must not remain on failure before writes", partFile.exists())
    }

    @Test
    fun download_progressEmission_reflectsActualBytesWritten() = runBlocking {
        val payload = "A".repeat(64 * 1024) // 64 KB
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.length.toString())
                .setBody(payload)
        )

        val destFile = File(tempDir, "progress_test.mp4")
        val updates = mutableListOf<DownloadProgressUpdate>()

        val result = engine.download(
            taskId = "task_prog",
            url = server.url("/prog.mp4").toString(),
            destinationFile = destFile,
            onProgress = { updates.add(it) }
        )

        assertTrue(result is DownloadResult.Success)
        assertTrue("Must receive at least one progress update", updates.isNotEmpty())
        val finalUpdate = updates.last()
        assertEquals(payload.length.toLong(), finalUpdate.downloadedBytes)
        assertEquals(payload.length.toLong(), finalUpdate.totalBytes)
        assertEquals(1.0f, finalUpdate.progress, 0.001f)
        assertEquals(payload.length.toLong(), destFile.length())
    }
}
