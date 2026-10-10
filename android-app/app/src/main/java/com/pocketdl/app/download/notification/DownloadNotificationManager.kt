package com.pocketdl.app.download.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pocketdl.app.MainActivity
import com.pocketdl.app.download.service.DownloadActionReceiver

/**
 * Handles creation of notification channels, ongoing progress notifications,
 * completion alerts, and failure notifications for PocketDL downloads.
 */
class DownloadNotificationManager(
    private val context: Context
) {
    init {
        createNotificationChannel()
    }

    /**
     * Registers the notification channel required for Android O (API 26) and above.
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Builds an ongoing progress notification suitable for foreground service display.
     */
    fun buildOngoingNotification(
        activeCount: Int,
        title: String?,
        progressPercent: Int,
        downloadedSizeText: String,
        totalSizeText: String,
        speedText: String,
        etaText: String,
        primaryTaskId: String? = null
    ): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationTitle = when {
            activeCount > 1 -> "Downloading ($activeCount active downloads)"
            !title.isNullOrBlank() -> title
            else -> "Downloading media"
        }

        val contentText = "$downloadedSizeText / $totalSizeText ($speedText - ETA $etaText)"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(notificationTitle)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progressPercent in 0..100) {
            builder.setProgress(100, progressPercent, false)
        } else {
            builder.setProgress(100, 0, true)
        }

        // Action 1: Pause / Pause All
        if (!primaryTaskId.isNullOrBlank() && activeCount == 1) {
            val pauseIntent = Intent(context, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_PAUSE_TASK
                putExtra(DownloadActionReceiver.EXTRA_TASK_ID, primaryTaskId)
            }
            val pausePendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
        } else {
            val pauseAllIntent = Intent(context, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_PAUSE_ALL
            }
            val pauseAllPendingIntent = PendingIntent.getBroadcast(
                context,
                102,
                pauseAllIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Pause All", pauseAllPendingIntent)
        }

        // Action 2: Cancel / Start All
        if (!primaryTaskId.isNullOrBlank() && activeCount == 1) {
            val cancelIntent = Intent(context, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_CANCEL_TASK
                putExtra(DownloadActionReceiver.EXTRA_TASK_ID, primaryTaskId)
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(
                context,
                103,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)
        } else {
            val resumeAllIntent = Intent(context, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_START_ALL
            }
            val resumeAllPendingIntent = PendingIntent.getBroadcast(
                context,
                104,
                resumeAllIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "Resume All", resumeAllPendingIntent)
        }

        return builder.build()
    }

    /**
     * Builds a completion notification for a finished download task.
     */
    fun buildCompletionNotification(taskId: String, title: String): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download Completed")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    /**
     * Builds a failure notification for a failed download task.
     */
    fun buildFailureNotification(taskId: String, title: String, errorMessage: String): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download Failed")
            .setContentText("$title: $errorMessage")
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    /**
     * Posts a notification safely. Catches exceptions if notification permissions are denied.
     */
    fun postNotification(notificationId: Int, notification: Notification): Boolean {
        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Cancels a posted notification safely.
     */
    fun cancelNotification(notificationId: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (_: Exception) {
            // Ignore cancel failures
        }
    }

    companion object {
        const val CHANNEL_ID = "pocketdl_downloads"
        const val CHANNEL_NAME = "Active Downloads"
        const val CHANNEL_DESCRIPTION = "Shows progress and status of PocketDL active media downloads"
        const val ONGOING_NOTIFICATION_ID = 1001
    }
}
