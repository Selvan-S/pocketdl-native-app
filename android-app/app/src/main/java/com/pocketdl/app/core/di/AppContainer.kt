package com.pocketdl.app.core.di

import android.content.Context
import com.pocketdl.app.core.dispatchers.DefaultDispatcherProvider
import com.pocketdl.app.core.dispatchers.DispatcherProvider

/**
 * Dependency Injection container interface providing application-level dependencies.
 */
interface AppContainer {
    val dispatchers: DispatcherProvider
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
}
