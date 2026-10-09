package com.pocketdl.app.download

import java.util.Locale

/**
 * Utility for smoothing speed calculation, formatting ETA, and formatting human-readable byte sizes.
 */
class DownloadProgressCalculator(
    private val windowDurationMs: Long = 2000L
) {
    private val samples = ArrayDeque<Pair<Long, Long>>() // timestampMs to cumulativeBytesWritten

    /**
     * Records a byte progress sample at [nowMs] and calculates current smoothed speed in bytes/sec.
     */
    fun recordSampleAndCalculateSpeed(bytesWritten: Long, nowMs: Long = System.currentTimeMillis()): Long {
        samples.addLast(nowMs to bytesWritten)

        // Evict samples outside the sliding window
        while (samples.size > 1 && (nowMs - samples.first().first) > windowDurationMs) {
            samples.removeFirst()
        }

        if (samples.size < 2) return 0L

        val oldest = samples.first()
        val deltaBytes = bytesWritten - oldest.second
        val deltaMs = nowMs - oldest.first

        return if (deltaMs > 0 && deltaBytes > 0) {
            (deltaBytes * 1000L) / deltaMs
        } else {
            0L
        }
    }

    /**
     * Resets internal speed calculation samples (e.g. on pause or resume).
     */
    fun reset() {
        samples.clear()
    }

    companion object {
        fun calculateEtaSeconds(downloadedBytes: Long, totalBytes: Long, speedBps: Long): Long {
            if (totalBytes <= 0 || downloadedBytes >= totalBytes || speedBps <= 0) {
                return 0L
            }
            val remainingBytes = totalBytes - downloadedBytes
            return (remainingBytes / speedBps).coerceAtLeast(0L)
        }

        fun formatSpeed(speedBps: Long): String {
            if (speedBps <= 0) return "0 KB/s"
            val kb = speedBps / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0

            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.1f GB/s", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB/s", mb)
                else -> String.format(Locale.US, "%.0f KB/s", kb)
            }
        }

        fun formatEta(etaSeconds: Long): String {
            if (etaSeconds <= 0) return "--:--"
            val hours = etaSeconds / 3600
            val minutes = (etaSeconds % 3600) / 60
            val seconds = etaSeconds % 60

            return if (hours > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 MB"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0

            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
                else -> "$bytes B"
            }
        }
    }
}
