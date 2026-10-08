package com.pocketdl.app.core.di

import android.content.Context
import com.pocketdl.app.core.dispatchers.DefaultDispatcherProvider
import com.pocketdl.app.core.dispatchers.DispatcherProvider
import com.pocketdl.app.data.repository.CapturedMediaRepository
import com.pocketdl.app.data.repository.DownloadRepository
import com.pocketdl.app.data.repository.ExtensionRepository
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
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
    override val dispatchers: DispatcherProvider by lazy {
        DefaultDispatcherProvider()
    }
    override val capturedMediaRepository: CapturedMediaRepository by lazy {
        InMemoryRepositoryProvider.capturedMediaRepository
    }
    override val downloadRepository: DownloadRepository by lazy {
        InMemoryRepositoryProvider.downloadRepository
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

