package com.pocketdl.app.download

import java.io.File
import java.io.IOException

/**
 * File utilities for secure path resolution, filename sanitization,
 * .part temporary file management, and atomic completion renaming.
 */
object DownloadFileUtils {

    private val RESERVED_CHARS_REGEX = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    private val MIME_TO_EXT = mapOf(
        "video/mp4" to "mp4",
        "video/webm" to "webm",
        "video/x-matroska" to "mkv",
        "video/mkv" to "mkv",
        "video/quicktime" to "mov",
        "video/x-msvideo" to "avi",
        "video/x-flv" to "flv",
        "video/mp2t" to "ts",
        "video/3gpp" to "3gp",
        "video/ogg" to "ogg",
        "audio/mpeg" to "mp3",
        "audio/mp3" to "mp3",
        "audio/aac" to "aac",
        "audio/ogg" to "ogg",
        "audio/wav" to "wav",
        "audio/x-wav" to "wav",
        "audio/flac" to "flac",
        "audio/x-flac" to "flac",
        "audio/mp4" to "m4a",
        "audio/m4a" to "m4a",
        "audio/x-m4a" to "m4a",
        "application/x-mpegurl" to "m3u8",
        "application/vnd.apple.mpegurl" to "m3u8"
    )

    private val KNOWN_MEDIA_EXTENSIONS = setOf(
        "mp4", "webm", "mkv", "mov", "avi", "flv", "ts", "3gp",
        "mp3", "aac", "ogg", "wav", "flac", "m4a", "m3u8"
    )

    private val IGNORABLE_PATH_EXTENSIONS = setOf(
        "html", "htm", "php", "asp", "aspx", "jsp", "cgi"
    )

    /**
     * Extracts an extension strictly from the path component of a URL, ignoring scheme, authority,
     * host, query parameters, and fragments.
     */
    fun extractPathExtension(urlString: String?): String? {
        if (urlString.isNullOrBlank()) return null
        return try {
            val cleanUrl = urlString.trim()
            val withoutFragment = cleanUrl.substringBefore('#')
            val withoutQuery = withoutFragment.substringBefore('?')

            val path = if (withoutQuery.contains("://")) {
                withoutQuery.substringAfter("://").substringAfter('/', "")
            } else {
                withoutQuery.substringAfter('/', "")
            }

            if (path.isBlank()) return null

            val lastSegment = path.substringAfterLast('/')
            if (!lastSegment.contains('.')) return null

            val ext = lastSegment.substringAfterLast('.').lowercase()
            if (ext.matches(Regex("""^[a-z0-9]{1,10}$""")) && ext !in IGNORABLE_PATH_EXTENSIONS) {
                ext
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extracts a filename from an HTTP Content-Disposition header safely.
     */
    fun extractContentDispositionFilename(header: String?): String? {
        if (header.isNullOrBlank()) return null
        try {
            val filenameStarMatch = Regex("""filename\*\s*=\s*([^\s;]+)""", RegexOption.IGNORE_CASE).find(header)
            if (filenameStarMatch != null) {
                val raw = filenameStarMatch.groupValues[1].trim('"', '\'')
                val encodingAndValue = raw.split("''", limit = 2)
                val value = if (encodingAndValue.size == 2) {
                    val charset = encodingAndValue[0]
                    val encodedText = encodingAndValue[1]
                    try {
                        java.net.URLDecoder.decode(encodedText, charset.ifBlank { "UTF-8" })
                    } catch (_: Exception) {
                        encodedText
                    }
                } else {
                    raw
                }
                val cleaned = value.replace("\\", "/").substringAfterLast("/").trim()
                if (cleaned.isNotBlank()) return cleaned
            }

            val filenameMatch = Regex("""filename\s*=\s*("([^"]+)"|([^\s;]+))""", RegexOption.IGNORE_CASE).find(header)
            if (filenameMatch != null) {
                val raw = (filenameMatch.groupValues[2].ifEmpty { filenameMatch.groupValues[3] }).trim('"', '\'')
                val cleaned = raw.replace("\\", "/").substringAfterLast("/").trim()
                if (cleaned.isNotBlank()) return cleaned
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Maps an HTTP Content-Type header to a standard media file extension.
     */
    fun extractContentTypeExtension(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val mime = header.substringBefore(';').trim().lowercase()
        return MIME_TO_EXT[mime]
    }

    /**
     * Returns true if the extension string is a recognized media format extension.
     */
    fun isKnownMediaExtension(ext: String): Boolean {
        return KNOWN_MEDIA_EXTENSIONS.contains(ext.trim().lowercase())
    }

    private fun isValidExtension(ext: String): Boolean {
        val clean = ext.trim().lowercase()
        return clean.matches(Regex("""^[a-z0-9]{1,10}$""")) && clean !in IGNORABLE_PATH_EXTENSIONS
    }

    private fun hasKnownMediaExtension(name: String): Boolean {
        if (!name.contains(".")) return false
        val ext = name.substringAfterLast(".").lowercase()
        return isKnownMediaExtension(ext)
    }

    /**
     * Determines the optimal extension using a strict precedence order:
     * 1. Content-Disposition header filename extension
     * 2. Content-Type header MIME type mapping
     * 3. URL path extension (path segment only)
     * 4. Title/filename existing extension (if known media extension)
     * 5. Default fallback ("mp4")
     */
    fun inferExtension(
        title: String? = null,
        url: String? = null,
        contentType: String? = null,
        contentDisposition: String? = null
    ): String {
        val cdFilename = extractContentDispositionFilename(contentDisposition)
        if (!cdFilename.isNullOrBlank() && cdFilename.contains(".")) {
            val cdExt = cdFilename.substringAfterLast(".").lowercase()
            if (isValidExtension(cdExt)) {
                return cdExt
            }
        }

        val mimeExt = extractContentTypeExtension(contentType)
        if (!mimeExt.isNullOrBlank() && isValidExtension(mimeExt)) {
            return mimeExt
        }

        val urlExt = extractPathExtension(url)
        if (!urlExt.isNullOrBlank() && isValidExtension(urlExt)) {
            return urlExt
        }

        if (!title.isNullOrBlank() && title.contains(".")) {
            val titleExt = title.substringAfterLast(".").lowercase()
            if (isKnownMediaExtension(titleExt)) {
                return titleExt
            }
        }

        return "mp4"
    }

    /**
     * Sanitizes a candidate filename, stripping path traversal tokens and illegal filesystem characters.
     * Incorporates HTTP metadata and URL path logic for accurate extension preservation.
     */
    fun sanitizeFileName(
        rawName: String,
        fallbackId: String = System.currentTimeMillis().toString(),
        url: String? = null,
        contentType: String? = null,
        contentDisposition: String? = null
    ): String {
        val targetExt = inferExtension(
            title = rawName,
            url = url,
            contentType = contentType,
            contentDisposition = contentDisposition
        )

        var cleaned = rawName
            .replace("\\", "/")
            .substringAfterLast("/")
            .replace(RESERVED_CHARS_REGEX, "_")
            .trim()
            .trim('.')

        if (cleaned.isBlank()) {
            cleaned = "download_${fallbackId.take(8)}"
        }

        val baseName = when {
            cleaned.endsWith(".$targetExt", ignoreCase = true) -> {
                cleaned.substring(0, cleaned.length - targetExt.length - 1)
            }
            hasKnownMediaExtension(cleaned) -> {
                cleaned.substringBeforeLast(".")
            }
            else -> cleaned
        }

        val finalBase = baseName.ifBlank { "download_${fallbackId.take(8)}" }
        return "$finalBase.$targetExt"
    }

    /**
     * Resolves the final destination file, asserting that the canonical path
     * stays strictly within the designated [downloadDir] to prevent path traversal attacks.
     * Incorporates [taskId] when provided to guarantee stable task-specific file isolation.
     */
    fun resolveDestinationFile(
        downloadDir: File,
        fileName: String,
        taskId: String = "file",
        url: String? = null,
        contentType: String? = null,
        contentDisposition: String? = null
    ): File {
        val sanitized = sanitizeFileName(
            rawName = fileName,
            fallbackId = taskId,
            url = url,
            contentType = contentType,
            contentDisposition = contentDisposition
        )
        val ext = sanitized.substringAfterLast(".", "mp4")
        val nameWithoutExt = sanitized.substringBeforeLast(".")
        val cleanTaskId = taskId.replace(Regex("""[^a-zA-Z0-9_-]"""), "_")

        val uniqueName = if (cleanTaskId.isNotBlank() && cleanTaskId != "file") {
            "${nameWithoutExt}_${cleanTaskId}.$ext"
        } else {
            "$nameWithoutExt.$ext"
        }

        val candidate = File(downloadDir, uniqueName)

        val dirCanonical = downloadDir.canonicalPath
        val fileCanonical = candidate.canonicalPath

        if (!fileCanonical.startsWith(dirCanonical)) {
            throw SecurityException("Path traversal attempt detected for file: $fileName")
        }

        return candidate
    }

    /**
     * Re-evaluates destination file based on HTTP response metadata and updates extension if appropriate.
     */
    fun refineDestinationFile(
        currentDestFile: File,
        taskId: String = "file",
        url: String? = null,
        contentType: String? = null,
        contentDisposition: String? = null
    ): File {
        val currentExt = currentDestFile.extension.lowercase()
        val refinedExt = inferExtension(
            title = currentDestFile.nameWithoutExtension,
            url = url,
            contentType = contentType,
            contentDisposition = contentDisposition
        )

        if (refinedExt != currentExt && refinedExt.isNotBlank()) {
            val parentDir = currentDestFile.parentFile ?: File(".")
            val baseName = currentDestFile.nameWithoutExtension
            val newName = "$baseName.$refinedExt"
            val candidate = File(parentDir, newName)
            if (candidate.canonicalPath.startsWith(parentDir.canonicalPath)) {
                return candidate
            }
        }

        return currentDestFile
    }

    /**
     * Resolves the corresponding .part temporary file for an in-progress download.
     */
    fun resolvePartFile(destinationFile: File): File {
        return File(destinationFile.parentFile, "${destinationFile.name}.part")
    }

    /**
     * Atomically finalizes a completed download by renaming the .part file to the destination file.
     * Preserves existing destination files without destructive deletion by finding an unused indexed filename.
     * Returns the actual final file created.
     */
    @Throws(IOException::class)
    fun finalizeDownload(partFile: File, destinationFile: File): File {
        if (!partFile.exists()) {
            throw IOException("Partial file does not exist: ${partFile.absolutePath}")
        }

        destinationFile.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }

        var finalTarget = destinationFile
        var counter = 1
        val parentDir = destinationFile.parentFile
        val baseName = destinationFile.nameWithoutExtension
        val ext = destinationFile.extension

        // Never delete or overwrite an existing destination file: find an available indexed name
        while (finalTarget.exists()) {
            val suffix = if (ext.isNotBlank()) ".$ext" else ""
            finalTarget = File(parentDir, "$baseName ($counter)$suffix")
            counter++
        }

        val success = partFile.renameTo(finalTarget)
        if (!success) {
            // Fallback to copy and delete if renameTo fails across different mount points
            partFile.copyTo(finalTarget, overwrite = false)
            partFile.delete()
        }

        return finalTarget
    }

    /**
     * Deletes the temporary .part file if it exists (e.g. on cancellation).
     */
    fun deletePartFile(partFile: File): Boolean {
        return if (partFile.exists()) {
            partFile.delete()
        } else {
            true
        }
    }
}
