package com.pocketdl.app.ui.screens.extension

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketdl.app.data.repository.ExtensionRepository
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExtensionViewModel(
    private val extensionRepo: ExtensionRepository = InMemoryRepositoryProvider.extensionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtensionUiState(isLoading = true))
    val uiState: StateFlow<ExtensionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                extensionRepo.observeExtensionStatus(),
                extensionRepo.getPairingToken()
            ) { status, token ->
                _uiState.value.copy(
                    isLoading = false,
                    status = status,
                    pairingToken = token
                )
            }.collect { combinedState ->
                _uiState.value = combinedState
            }
        }
    }

    fun regenerateToken() {
        val newToken = extensionRepo.regeneratePairingToken()
        _uiState.update {
            it.copy(
                pairingToken = newToken,
                userMessage = "Generated new pairing token: $newToken"
            )
        }
    }

    fun toggleConnection() {
        extensionRepo.toggleConnection()
        val isNowConnected = _uiState.value.status?.isConnected == false // status will flip
        _uiState.update {
            it.copy(userMessage = if (isNowConnected) "Socket connected to browser" else "Socket disconnected")
        }
    }

    fun updatePort(port: Int) {
        extensionRepo.updatePort(port)
        _uiState.update { it.copy(userMessage = "Socket port updated to $port") }
    }

    fun onDismissMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
