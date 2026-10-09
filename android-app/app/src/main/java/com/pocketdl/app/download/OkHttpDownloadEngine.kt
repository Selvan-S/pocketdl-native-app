package com.pocketdl.app.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Robust OkHttp 5.5.0 streaming download engine.
 * Supports resume via HTTP Range requests, strong ETag / Last-Modified conditional validation,
 * Content-Range integrity assertions, throttled progress updates, and race-safe pause/cancel.
 */
class OkHttpDownloadEngine(
    private val client: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DownloadEngine {

    private val activeJobs = ConcurrentHashMap<String, DownloadJobHandle>()
    private val contentRangeRegex = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""")
    private val contentRange416Regex = Regex("""bytes\s+\*/(\d+)""")

    override fun isRunning(taskId: String): Boolean = activeJobs.containsKey(taskId)

    override fun pause(taskId: String) {
        val handle = activeJobs[taskId] ?: return
        handle.isPaused.set(true)
        handle.call?.cancel()
    }

    override fun cancel(taskId: String) {
        val handle = activeJobs[taskId] ?: return
        handle.isCancelled.set(true)
        handle.call?.cancel()
    }

    override suspend fun download(
        taskId: String,
        url: String,
        destinationFile: File,
        existingEtag: String?,
        existingLastModified: String?,
        onProgress: suspend (DownloadProgressUpdate) -> Unit
    ): DownloadResult = withContext(ioDispatcher) {
        val currentJob = currentCoroutineContext()[kotlinx.coroutines.Job]
            ?: error("download() must be called inside a coroutine Job")

        val handle = DownloadJobHandle(taskId = taskId, job = currentJob)
        activeJobs[taskId] = handle

        val partFile = DownloadFileUtils.resolvePartFile(destinationFile)
        var randomAccessFile: RandomAccessFile? = null
        val progressCalculator = DownloadProgressCalculator()

        try {
            partFile.parentFile?.let { parent ->
                if (!parent.exists()) parent.mkdirs()
            }

            var existingBytes = if (partFile.exists()) partFile.length() else 0L

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "PocketDL/1.0 (Android)")
                .header("Accept", "*/*")
                .header("Connection", "keep-alive")
                .header("Accept-Encoding", "identity") // Prevent transparent gzip from mutating byte counts

            if (existingBytes > 0L) {
                requestBuilder.header("Range", "bytes=$existingBytes-")
                applyIfRangeHeader(requestBuilder, existingEtag, existingLastModified)
            }

            val request = requestBuilder.build()
            val call = client.newCall(request)
            handle.call = call

            val response = call.execute()

            // Extract caching validators from response
            val responseEtag = response.header("ETag")?.trim()
            val responseLastModified = response.header("Last-Modified")?.trim()

            // Handle HTTP 416 (Range Not Satisfiable)
            if (response.code == 416) {
                response.close()
                val contentRangeHeader = response.header("Content-Range")
                val totalFrom416 = parse416Total(contentRangeHeader)

                if (totalFrom416 != null && existingBytes == totalFrom416 && totalFrom416 > 0L) {
                    // The local file is already completely downloaded
                    val finalized = DownloadFileUtils.finalizeDownload(partFile, destinationFile)
                    return@withContext DownloadResult.Success(
                        file = finalized,
                        totalBytes = totalFrom416,
                        etag = responseEtag ?: existingEtag,
                        lastModified = responseLastModified ?: existingLastModified
                    )
                } else {
                    // Invalidate partial file and restart from 0
                    partFile.delete()
                    existingBytes = 0L
                    return@withContext download(
                        taskId = taskId,
                        url = url,
                        destinationFile = destinationFile,
                        existingEtag = null,
                        existingLastModified = null,
                        onProgress = onProgress
                    )
                }
            }

            if (!response.isSuccessful) {
                val errorCode = response.code
                val errorMsg = response.message
                response.close()
                throw IOException("HTTP error $errorCode: $errorMsg")
            }

            val body = response.body ?: throw IOException("Empty response body from $url")
            val isPartial = (response.code == 206)

            var totalBytes: Long
            var resumeOffset = existingBytes

            if (isPartial) {
                val contentRange = response.header("Content-Range")
                val parsedRange = parseContentRange(contentRange)

                // Strict validation: if missing, malformed, or start offset mismatch, never append!
                if (parsedRange == null || parsedRange.startOffset != existingBytes) {
                    response.close()
                    // Treat as unsafe, reset partial file and restart cleanly from 0
                    partFile.delete()
                    return@withContext download(
                        taskId = taskId,
                        url = url,
                        destinationFile = destinationFile,
                        existingEtag = null,
                        existingLastModified = null,
                        onProgress = onProgress
                    )
                }

                totalBytes = if (parsedRange.totalBytes > 0L) {
                    parsedRange.totalBytes
                } else {
                    existingBytes + body.contentLength()
                }
            } else {
                // HTTP 200: Server sent complete file from offset 0
                resumeOffset = 0L
                partFile.delete()
                totalBytes = body.contentLength()
            }

            randomAccessFile = RandomAccessFile(partFile, "rw")
            if (resumeOffset > 0L) {
                randomAccessFile.seek(resumeOffset)
            } else {
                randomAccessFile.setLength(0L)
            }

            var downloadedBytes = resumeOffset
            val buffer = ByteArray(8192)
            val source = body.source()

            var lastEmitMs = 0L

            while (true) {
                currentCoroutineContext().ensureActive()

                val bytesRead = source.read(buffer)
                if (bytesRead == -1) break

                // Write to OS file buffer
                randomAccessFile.write(buffer, 0, bytesRead)

                // Metric increments strictly after successful write
                downloadedBytes += bytesRead

                val nowMs = System.currentTimeMillis()
                val speedBps = progressCalculator.recordSampleAndCalculateSpeed(downloadedBytes, nowMs)
                val etaSeconds = DownloadProgressCalculator.calculateEtaSeconds(downloadedBytes, totalBytes, speedBps)

                val progress = if (totalBytes > 0L) {
                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                // Throttle progress updates to at most once every 500ms
                if (nowMs - lastEmitMs >= 500L) {
                    lastEmitMs = nowMs
                    onProgress(
                        DownloadProgressUpdate(
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            progress = progress,
                            speedBps = speedBps,
                            speedText = DownloadProgressCalculator.formatSpeed(speedBps),
                            etaSeconds = etaSeconds,
                            etaText = DownloadProgressCalculator.formatEta(etaSeconds),
                            etag = responseEtag ?: existingEtag,
                            lastModified = responseLastModified ?: existingLastModified
                        )
                    )
                }
            }

            // Flush file descriptor on complete
            randomAccessFile.fd.sync()
            randomAccessFile.close()
            randomAccessFile = null

            // Emit final 100% progress
            onProgress(
                DownloadProgressUpdate(
                    downloadedBytes = downloadedBytes,
                    totalBytes = if (totalBytes > 0L) totalBytes else downloadedBytes,
                    progress = 1.0f,
                    speedBps = 0L,
                    speedText = "0 KB/s",
                    etaSeconds = 0L,
                    etaText = "Completed",
                    etag = responseEtag ?: existingEtag,
                    lastModified = responseLastModified ?: existingLastModified
                )
            )

            val finalized = DownloadFileUtils.finalizeDownload(partFile, destinationFile)
            return@withContext DownloadResult.Success(
                file = finalized,
                totalBytes = downloadedBytes,
                etag = responseEtag ?: existingEtag,
                lastModified = responseLastModified ?: existingLastModified
            )

        } catch (e: Exception) {
            try {
                randomAccessFile?.close()
            } catch (_: Exception) {}

            if (handle.isPaused.get()) {
                return@withContext DownloadResult.Paused
            }

            if (handle.isCancelled.get()) {
                DownloadFileUtils.deletePartFile(partFile)
                return@withContext DownloadResult.Cancelled
            }

            if (e is CancellationException) {
                return@withContext DownloadResult.Paused
            }

            return@withContext DownloadResult.Failure(e, canResume = partFile.exists() && partFile.length() > 0L)
        } finally {
            activeJobs.remove(taskId)
        }
    }

    private fun applyIfRangeHeader(builder: Request.Builder, etag: String?, lastModified: String?) {
        // RFC 9110 §13.1.5: An If-Range header MUST NOT use a weak validator (W/"...")
        if (!etag.isNullOrBlank() && !etag.startsWith("W/")) {
            builder.header("If-Range", etag)
        } else if (!lastModified.isNullOrBlank()) {
            builder.header("If-Range", lastModified)
        }
    }

    private data class ParsedContentRange(
        val startOffset: Long,
        val endOffset: Long,
        val totalBytes: Long
    )

    private fun parseContentRange(header: String?): ParsedContentRange? {
        if (header.isNullOrBlank()) return null
        val match = contentRangeRegex.matchEntire(header.trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = if (match.groupValues[3] == "*") -1L else match.groupValues[3].toLongOrNull() ?: -1L
        return ParsedContentRange(startOffset = start, endOffset = end, totalBytes = total)
    }

    private fun parse416Total(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val match = contentRange416Regex.matchEntire(header.trim()) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    companion object {
        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }
}
