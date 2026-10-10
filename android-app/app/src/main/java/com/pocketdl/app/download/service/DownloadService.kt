package com.pocketdl.app.download.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import com.pocketdl.app.PocketDlApplication
import com.pocketdl.app.download.DownloadCoordinator
import com.pocketdl.app.download.notification.DownloadNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Foreground Service hosting active PocketDL download execution.
 * Elevates process priority to foreground status (`dataSync` type on API 34+) and manages ongoing notifications.
 */
class DownloadService : Service() {

    private lateinit var notificationManager: DownloadNotificationManager
    private var coordinator: DownloadCoordinator? = null
    private var activeJobsObserverJob: Job? = null
    private var notificationUpdateJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        notificationManager = DownloadNotificationManager(applicationContext)
        val app = applicationContext as? PocketDlApplication
        coordinator = app?.container?.downloadCoordinator
        observeActiveJobs()
        observeNotificationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentCoordinator = coordinator ?: (applicationContext as? PocketDlApplication)?.container?.downloadCoordinator
        coordinator = currentCoordinator

        if (notificationUpdateJob == null || notificationUpdateJob?.isActive != true) {
            observeNotificationUpdates()
        }

        val activeState = currentCoordinator?.activeNotificationState?.value
        val ongoingNotification = if (activeState != null) {
            notificationManager.buildOngoingNotification(activeState)
        } else {
            val activeCount = currentCoordinator?.activeJobsCount?.value ?: 1
            notificationManager.buildOngoingNotification(
                activeCount = activeCount,
                title = null,
                progressPercent = 0,
                downloadedSizeText = "0 MB",
                totalSizeText = "0 MB",
                speedText = "0 KB/s",
                etaText = "--:--"
            )
        }

        val currentState = currentCoordinator?.serviceForegroundState?.value
        val sessionId = (currentState as? ServiceForegroundResult.Starting)?.sessionId ?: 0L

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    DownloadNotificationManager.ONGOING_NOTIFICATION_ID,
                    ongoingNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(
                    DownloadNotificationManager.ONGOING_NOTIFICATION_ID,
                    ongoingNotification
                )
            }
            currentCoordinator?.notifyServiceForegroundResult(ServiceForegroundResult.Success(sessionId), sessionId)
        } catch (e: Exception) {
            currentCoordinator?.notifyServiceForegroundResult(ServiceForegroundResult.Failed(sessionId, e), sessionId)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun observeActiveJobs() {
        val currentCoordinator = coordinator ?: return
        activeJobsObserverJob?.cancel()
        var hasHadActiveJobs = false
        activeJobsObserverJob = serviceScope.launch {
            currentCoordinator.activeJobsCount.collect { count ->
                if (count > 0) {
                    hasHadActiveJobs = true
                } else if (hasHadActiveJobs && count <= 0) {
                    if (currentCoordinator.serviceForegroundState.value !is ServiceForegroundResult.Starting) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun observeNotificationUpdates() {
        val currentCoordinator = coordinator ?: return
        notificationUpdateJob?.cancel()
        notificationUpdateJob = serviceScope.launch {
            currentCoordinator.activeNotificationState.collect { state ->
                if (state != null) {
                    val updatedNotification = notificationManager.buildOngoingNotification(state)
                    notificationManager.postNotification(
                        DownloadNotificationManager.ONGOING_NOTIFICATION_ID,
                        updatedNotification
                    )
                }
            }
        }
    }

    override fun onTimeout(startId: Int) {
        handleServiceTimeout()
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        handleServiceTimeout()
    }

    private fun handleServiceTimeout() {
        // 1. Immediately cancel active worker jobs (best-effort coroutine teardown)
        coordinator?.cancelAllJobsForTimeout()

        // 2. Best-effort status write attempt
        coordinator?.markTasksPausedForTimeoutBestEffort()

        // 3. Promptly release foreground status and stop service to comply with OS timeout
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        activeJobsObserverJob?.cancel()
        notificationUpdateJob?.cancel()
        coordinator?.notifyServiceForegroundResult(ServiceForegroundResult.Idle)
        super.onDestroy()
    }
}
