package com.pocketdl.app

import android.app.Application
import com.pocketdl.app.core.di.AppContainer
import com.pocketdl.app.core.di.DefaultAppContainer
import com.pocketdl.app.core.logging.AppLogger

class PocketDlApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "Initializing PocketDL Application")
        container = DefaultAppContainer(this)
    }

    companion object {
        private const val TAG = "PocketDlApplication"
    }
}
