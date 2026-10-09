package com.pocketdl.app.download

import java.io.File
import java.io.IOException

/**
 * File utilities for secure path resolution, filename sanitization,
 * .part temporary file management, and atomic completion renaming.
 */
object DownloadFileUtils {

    private val RESERVED_CHARS_REGEX = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    /**
     * Sanitizes a candidate filename, stripping path traversal tokens and illegal filesystem characters.
     */
    fun sanitizeFileName(rawName: String, fallbackId: String = System.currentTimeMillis().toString()): String {
        var cleaned = rawName
            .replace("\\", "/")
            .substringAfterLast("/")
            .replace(RESERVED_CHARS_REGEX, "_")
            .trim()
            .trim('.')

        if (cleaned.isBlank()) {
            cleaned = "download_${fallbackId.take(8)}.mp4"
        }

        if (!cleaned.contains(".")) {
            cleaned = "$cleaned.mp4"
        }

        return cleaned
    }

    /**
     * Resolves the final destination file, asserting that the canonical path
     * stays strictly within the designated [downloadDir] to prevent path traversal attacks.
     */
    fun resolveDestinationFile(downloadDir: File, fileName: String, fallbackId: String = "file"): File {
        val safeName = sanitizeFileName(fileName, fallbackId)
        val candidate = File(downloadDir, safeName)

        val dirCanonical = downloadDir.canonicalPath
        val fileCanonical = candidate.canonicalPath

        if (!fileCanonical.startsWith(dirCanonical)) {
            throw SecurityException("Path traversal attempt detected for file: $fileName")
        }

        return candidate
    }

    /**
     * Resolves the corresponding .part temporary file for an in-progress download.
     */
    fun resolvePartFile(destinationFile: File): File {
        return File(destinationFile.parentFile, "${destinationFile.name}.part")
    }

    /**
     * Atomically finalizes a completed download by renaming the .part file to the final destination file.
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

        // If destination file already exists, remove it before atomic rename
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val success = partFile.renameTo(destinationFile)
        if (!success) {
            // Fallback to copy and delete if renameTo fails across different mount points
            partFile.copyTo(destinationFile, overwrite = true)
            partFile.delete()
        }

        return destinationFile
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
