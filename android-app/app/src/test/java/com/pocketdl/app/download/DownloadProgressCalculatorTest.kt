package com.pocketdl.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressCalculatorTest {

    @Test
    fun calculateSpeed_calculatesSmoothedSpeedOverTime() {
        val calc = DownloadProgressCalculator(windowDurationMs = 2000L)
        val t0 = 1000L
        calc.recordSampleAndCalculateSpeed(bytesWritten = 0L, nowMs = t0)

        // 1 second later, 1,048,576 bytes (1 MB) written
        val speed = calc.recordSampleAndCalculateSpeed(bytesWritten = 1024 * 1024L, nowMs = t0 + 1000L)
        assertEquals(1024 * 1024L, speed)
    }

    @Test
    fun formatSpeed_formatsKbAndMbCorrectly() {
        assertEquals("0 KB/s", DownloadProgressCalculator.formatSpeed(0L))
        assertEquals("500 KB/s", DownloadProgressCalculator.formatSpeed(500 * 1024L))
        assertEquals("12.5 MB/s", DownloadProgressCalculator.formatSpeed((12.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun formatEta_formatsMinutesAndHours() {
        assertEquals("--:--", DownloadProgressCalculator.formatEta(0L))
        assertEquals("00:45", DownloadProgressCalculator.formatEta(45L))
        assertEquals("02:15", DownloadProgressCalculator.formatEta(135L))
        assertEquals("01:10:00", DownloadProgressCalculator.formatEta(4200L))
    }

    @Test
    fun calculateEtaSeconds_handlesZeroAndNormalCases() {
        assertEquals(0L, DownloadProgressCalculator.calculateEtaSeconds(100L, 100L, 50L))
        assertEquals(0L, DownloadProgressCalculator.calculateEtaSeconds(50L, 100L, 0L))
        // 100 MB total, 50 MB downloaded, 10 MB/s speed -> 5 seconds
        assertEquals(5L, DownloadProgressCalculator.calculateEtaSeconds(50L, 100L, 10L))
    }

    @Test
    fun formatBytes_formatsHumanReadable() {
        assertEquals("0 MB", DownloadProgressCalculator.formatBytes(0L))
        assertEquals("500 KB", DownloadProgressCalculator.formatBytes(500 * 1024L))
        assertEquals("15.0 MB", DownloadProgressCalculator.formatBytes(15 * 1024 * 1024L))
        assertEquals("1.5 GB", DownloadProgressCalculator.formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }
}
