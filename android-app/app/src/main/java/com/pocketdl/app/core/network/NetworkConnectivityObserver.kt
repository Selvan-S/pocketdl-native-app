package com.pocketdl.app.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

enum class ConnectionType {
    WIFI,
    CELLULAR,
    ETHERNET,
    NONE,
    OTHER
}

data class NetworkState(
    val isConnected: Boolean,
    val isMetered: Boolean,
    val connectionType: ConnectionType
)

interface NetworkConnectivityObserver {
    fun observeNetworkState(): Flow<NetworkState>
    fun currentNetworkState(): NetworkState
}

object PermissiveNetworkConnectivityObserver : NetworkConnectivityObserver {
    private val state = MutableStateFlow(
        NetworkState(
            isConnected = true,
            isMetered = false,
            connectionType = ConnectionType.WIFI
        )
    )

    override fun observeNetworkState(): Flow<NetworkState> = state
    override fun currentNetworkState(): NetworkState = state.value
}
