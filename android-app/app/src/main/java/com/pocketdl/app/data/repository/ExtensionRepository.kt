package com.pocketdl.app.data.repository

import com.pocketdl.app.ui.mock.ExtensionStatusMock
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing browser extension connection state and pairing tokens.
 */
interface ExtensionRepository : Repository {
    fun observeExtensionStatus(): Flow<ExtensionStatusMock>
    fun toggleConnection()
    fun regeneratePairingToken(): String
    fun getPairingToken(): Flow<String>
    fun updatePort(port: Int)
}
