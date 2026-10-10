package com.pocketdl.app.core.di

import android.content.Context
import android.os.Environment
import com.pocketdl.app.core.dispatchers.DefaultDispatcherProvider
import com.pocketdl.app.core.dispatchers.DispatcherProvider
import com.pocketdl.app.data.database.PocketDlDatabase
import com.pocketdl.app.data.repository.CapturedMediaRepository
import com.pocketdl.app.data.repository.DownloadRepository
import com.pocketdl.app.data.repository.ExtensionRepository
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
import com.pocketdl.app.data.repository.RoomCapturedMediaRepository
import com.pocketdl.app.data.repository.RoomDownloadRepository
import com.pocketdl.app.data.repository.SettingsRepository
import com.pocketdl.app.data.repository.StorageRepository
import com.pocketdl.app.download.DownloadCoordinator
import com.pocketdl.app.download.DownloadEngine
import com.pocketdl.app.download.OkHttpDownloadEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File

/**
 * Dependency Injection container interface providing application-level dependencies.
 */
interface AppContainer {
    val dispatchers: DispatcherProvider
    val capturedMediaRepository: CapturedMediaRepository
    val downloadRepository: DownloadRepository
    val extensionRepository: ExtensionRepository
    val settingsRepository: SettingsRepository
    val storageRepository: StorageRepository
    val downloadEngine: DownloadEngine
    val downloadCoordinator: DownloadCoordinator
}

/**
 * Default production implementation of [AppContainer].
 */
class DefaultAppContainer(
    private val applicationContext: Context
) : AppContainer {

    private val database: PocketDlDatabase by lazy {
        PocketDlDatabase.getInstance(applicationContext)
    }

    override val dispatchers: DispatcherProvider by lazy {
        DefaultDispatcherProvider()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpDownloadEngine.defaultClient()
    }

    override val downloadEngine: DownloadEngine by lazy {
        OkHttpDownloadEngine(
            client = okHttpClient,
            ioDispatcher = dispatchers.io
        )
    }

    override val settingsRepository: SettingsRepository by lazy {
        InMemoryRepositoryProvider.settingsRepository
    }

    override val downloadCoordinator: DownloadCoordinator by lazy {
        DownloadCoordinator(
            engine = downloadEngine,
            downloadTaskDao = database.downloadTaskDao(),
            settingsRepository = settingsRepository,
            destinationDirProvider = {
                val base = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(applicationContext.filesDir, "downloads")
                val targetDir = File(base, "PocketDL")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                targetDir
            },
            coordinatorScope = CoroutineScope(SupervisorJob() + dispatchers.io),
            ioDispatcher = dispatchers.io,
            applicationContext = applicationContext
        )
    }

    override val capturedMediaRepository: CapturedMediaRepository by lazy {
        RoomCapturedMediaRepository(
            capturedMediaDao = database.capturedMediaDao(),
            ioDispatcher = dispatchers.io
        )
    }

    override val downloadRepository: DownloadRepository by lazy {
        RoomDownloadRepository(
            downloadTaskDao = database.downloadTaskDao(),
            coordinator = downloadCoordinator,
            ioDispatcher = dispatchers.io
        )
    }

    override val extensionRepository: ExtensionRepository by lazy {
        InMemoryRepositoryProvider.extensionRepository
    }

    override val storageRepository: StorageRepository by lazy {
        InMemoryRepositoryProvider.storageRepository
    }
}
