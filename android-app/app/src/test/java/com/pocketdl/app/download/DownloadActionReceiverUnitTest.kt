package com.pocketdl.app.download

import com.pocketdl.app.download.service.DownloadActionCommand
import com.pocketdl.app.download.service.DownloadActionReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadActionReceiverUnitTest {

    @Test
    fun parse_nullOrBlankAction_returnsNull() {
        assertNull(DownloadActionCommand.parse(null, "task_1"))
        assertNull(DownloadActionCommand.parse("", "task_1"))
        assertNull(DownloadActionCommand.parse("   ", "task_1"))
        assertNull(DownloadActionCommand.parse("com.pocketdl.app.UNKNOWN_ACTION", "task_1"))
    }

    @Test
    fun parse_pauseTaskWithNullOrBlankTaskId_returnsNull() {
        assertNull(DownloadActionCommand.parse(DownloadActionReceiver.ACTION_PAUSE_TASK, null))
        assertNull(DownloadActionCommand.parse(DownloadActionReceiver.ACTION_PAUSE_TASK, ""))
        assertNull(DownloadActionCommand.parse(DownloadActionReceiver.ACTION_PAUSE_TASK, "   "))
    }

    @Test
    fun parse_validPauseTask_returnsPauseTaskCommand() {
        val result = DownloadActionCommand.parse(DownloadActionReceiver.ACTION_PAUSE_TASK, " task_123  ")
        assertEquals(DownloadActionCommand.PauseTask("task_123"), result)
    }

    @Test
    fun parse_validResumeTask_returnsResumeTaskCommand() {
        val result = DownloadActionCommand.parse(DownloadActionReceiver.ACTION_RESUME_TASK, "task_456")
        assertEquals(DownloadActionCommand.ResumeTask("task_456"), result)
    }

    @Test
    fun parse_validCancelTask_returnsCancelTaskCommand() {
        val result = DownloadActionCommand.parse(DownloadActionReceiver.ACTION_CANCEL_TASK, "task_789")
        assertEquals(DownloadActionCommand.CancelTask("task_789"), result)
    }

    @Test
    fun parse_validPauseAll_returnsPauseAllCommand() {
        val result = DownloadActionCommand.parse(DownloadActionReceiver.ACTION_PAUSE_ALL, null)
        assertEquals(DownloadActionCommand.PauseAll, result)
    }

    @Test
    fun parse_validStartAll_returnsStartAllCommand() {
        val result = DownloadActionCommand.parse(DownloadActionReceiver.ACTION_START_ALL, null)
        assertEquals(DownloadActionCommand.StartAll, result)
    }
}
