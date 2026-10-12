package com.pocketdl.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn

class AndroidNetworkConnectivityObserver(
    private val context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : NetworkConnectivityObserver {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _networkState: StateFlow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(resolveCurrentState())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(resolveFromCapabilities(capabilities))
            }

            override fun onLost(network: Network) {
                trySend(resolveCurrentState())
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        // Initial state emission
        trySend(resolveCurrentState())

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = resolveCurrentState()
    )

    override fun observeNetworkState(): Flow<NetworkState> = _networkState

    override fun currentNetworkState(): NetworkState = _networkState.value

    private fun resolveCurrentState(): NetworkState {
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkState(
            isConnected = false,
            isMetered = false,
            connectionType = ConnectionType.NONE
        )
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkState(
            isConnected = false,
            isMetered = false,
            connectionType = ConnectionType.NONE
        )
        return resolveFromCapabilities(capabilities)
    }

    private fun resolveFromCapabilities(capabilities: NetworkCapabilities): NetworkState {
        val isConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val connectionType = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
            else -> ConnectionType.OTHER
        }
        return NetworkState(
            isConnected = isConnected,
            isMetered = isMetered,
            connectionType = connectionType
        )
    }
}
