package com.pocketdl.app.core.storage

import java.io.File

object StoragePolicy {
    const val SAFETY_BUFFER_BYTES = 25L * 1024L * 1024L // 25 MiB
    const val MINIMUM_UNKNOWN_HEADROOM_BYTES = 50L * 1024L * 1024L // 50 MiB

    fun calculateRequiredBytes(expectedTotalBytes: Long?, existingBytes: Long): Long {
        return if (expectedTotalBytes != null && expectedTotalBytes > 0L) {
            val remaining = (expectedTotalBytes - existingBytes).coerceAtLeast(0L)
            remaining + SAFETY_BUFFER_BYTES
        } else {
            MINIMUM_UNKNOWN_HEADROOM_BYTES + SAFETY_BUFFER_BYTES
        }
    }

    fun parseBytesFromText(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim()
        val regex = Regex("""^([\d.]+)\s*([KkMmGgTt]?[Bb]?)$""")
        val match = regex.matchEntire(trimmed) ?: return null
        val number = match.groupValues[1].toDoubleOrNull() ?: return null
        val unit = match.groupValues[2].uppercase()
        val multiplier = when (unit) {
            "TB", "T" -> 1024L * 1024L * 1024L * 1024L
            "GB", "G" -> 1024L * 1024L * 1024L
            "MB", "M" -> 1024L * 1024L
            "KB", "K" -> 1024L
            "B", "" -> 1L
            else -> return null
        }
        val bytes = (number * multiplier).toLong()
        return if (bytes > 0L) bytes else null
    }

    fun hasSufficientSpace(
        storageChecker: StorageSpaceChecker,
        destinationDir: File,
        expectedTotalBytes: Long?,
        existingBytes: Long
    ): Boolean {
        val available = storageChecker.getUsableSpace(destinationDir)
        val required = calculateRequiredBytes(expectedTotalBytes, existingBytes)
        return available >= required
    }
}
