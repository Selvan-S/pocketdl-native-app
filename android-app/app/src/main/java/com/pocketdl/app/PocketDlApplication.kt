package com.pocketdl.app

import android.app.Application
import com.pocketdl.app.core.di.AppContainer
import com.pocketdl.app.core.di.DefaultAppContainer
import com.pocketdl.app.core.logging.AppLogger
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
import com.pocketdl.app.download.notification.DownloadNotificationManager

class PocketDlApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "Initializing PocketDL Application")
        val defaultContainer = DefaultAppContainer(this)
        container = defaultContainer

        // Initialize notification channels at application startup
        DownloadNotificationManager(this).createNotificationChannels()

        // Wire persistent Room repositories to active repository provider
        InMemoryRepositoryProvider.capturedMediaRepository = defaultContainer.capturedMediaRepository
        InMemoryRepositoryProvider.downloadRepository = defaultContainer.downloadRepository
    }

    companion object {
        private const val TAG = "PocketDlApplication"
    }
}
