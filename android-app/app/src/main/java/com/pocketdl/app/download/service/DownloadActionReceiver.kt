package com.pocketdl.app.download.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pocketdl.app.PocketDlApplication
import com.pocketdl.app.download.DownloadActionHandler

/**
 * Sealed interface representing validated action commands dispatched from notification intents.
 */
sealed interface DownloadActionCommand {
    data class PauseTask(val taskId: String) : DownloadActionCommand
    data class ResumeTask(val taskId: String) : DownloadActionCommand
    data class CancelTask(val taskId: String) : DownloadActionCommand
    data object PauseAll : DownloadActionCommand
    data object StartAll : DownloadActionCommand

    companion object {
        /**
         * Validates intent action strings and task IDs, rejecting null, blank, or malformed inputs.
         */
        fun parse(action: String?, taskId: String?): DownloadActionCommand? {
            if (action.isNullOrBlank()) return null
            return when (action) {
                DownloadActionReceiver.ACTION_PAUSE_TASK -> {
                    val trimmed = taskId?.trim()
                    if (!trimmed.isNullOrEmpty()) PauseTask(trimmed) else null
                }
                DownloadActionReceiver.ACTION_RESUME_TASK -> {
                    val trimmed = taskId?.trim()
                    if (!trimmed.isNullOrEmpty()) ResumeTask(trimmed) else null
                }
                DownloadActionReceiver.ACTION_CANCEL_TASK -> {
                    val trimmed = taskId?.trim()
                    if (!trimmed.isNullOrEmpty()) CancelTask(trimmed) else null
                }
                DownloadActionReceiver.ACTION_PAUSE_ALL -> PauseAll
                DownloadActionReceiver.ACTION_START_ALL -> StartAll
                else -> null
            }
        }
    }
}

/**
 * BroadcastReceiver capturing notification action button taps (Pause, Resume, Cancel, Pause All, Start All)
 * and safely delegating them to [DownloadActionHandler].
 */
class DownloadActionReceiver(
    private val actionHandlerProvider: ((Context) -> DownloadActionHandler?)? = null
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val command = DownloadActionCommand.parse(
            action = intent.action,
            taskId = intent.getStringExtra(EXTRA_TASK_ID)
        ) ?: return

        val handler = actionHandlerProvider?.invoke(context)
            ?: (context.applicationContext as? PocketDlApplication)?.container?.downloadCoordinator
            ?: return

        when (command) {
            is DownloadActionCommand.PauseTask -> handler.pauseTask(command.taskId)
            is DownloadActionCommand.ResumeTask -> handler.startTask(command.taskId)
            is DownloadActionCommand.CancelTask -> handler.cancelTask(command.taskId)
            is DownloadActionCommand.PauseAll -> handler.pauseAll()
            is DownloadActionCommand.StartAll -> handler.startAll()
        }
    }

    companion object {
        const val ACTION_PAUSE_TASK = "com.pocketdl.app.ACTION_PAUSE_TASK"
        const val ACTION_RESUME_TASK = "com.pocketdl.app.ACTION_RESUME_TASK"
        const val ACTION_CANCEL_TASK = "com.pocketdl.app.ACTION_CANCEL_TASK"
        const val ACTION_PAUSE_ALL = "com.pocketdl.app.ACTION_PAUSE_ALL"
        const val ACTION_START_ALL = "com.pocketdl.app.ACTION_START_ALL"

        const val EXTRA_TASK_ID = "extra_task_id"
    }
}
