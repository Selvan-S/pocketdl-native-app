package com.pocketdl.app.core.di

import android.content.Context
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

    override val capturedMediaRepository: CapturedMediaRepository by lazy {
        RoomCapturedMediaRepository(
            capturedMediaDao = database.capturedMediaDao(),
            ioDispatcher = dispatchers.io
        )
    }

    override val downloadRepository: DownloadRepository by lazy {
        RoomDownloadRepository(
            downloadTaskDao = database.downloadTaskDao(),
            ioDispatcher = dispatchers.io
        )
    }

    override val extensionRepository: ExtensionRepository by lazy {
        InMemoryRepositoryProvider.extensionRepository
    }

    override val settingsRepository: SettingsRepository by lazy {
        InMemoryRepositoryProvider.settingsRepository
    }

    override val storageRepository: StorageRepository by lazy {
        InMemoryRepositoryProvider.storageRepository
    }
}

