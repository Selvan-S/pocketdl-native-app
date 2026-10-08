package com.pocketdl.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketdl.app.data.repository.DownloadEngineType
import com.pocketdl.app.data.repository.InMemoryRepositoryProvider
import com.pocketdl.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepo: SettingsRepository = InMemoryRepositoryProvider.settingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepo.observeSettings().collect { data ->
                _uiState.update {
                    it.copy(
                        selectedEngine = data.engine,
                        maxParallelDownloads = data.maxParallelDownloads,
                        allowCellular = data.allowCellular,
                        autoDetectLinks = data.autoDetectLinks,
                        downloadPath = data.downloadPath
                    )
                }
            }
        }
    }

    fun onEngineSelected(engine: DownloadEngineType) {
        settingsRepo.setEngine(engine)
        _uiState.update { it.copy(userMessage = "Switched engine to ${engine.displayName}") }
    }

    fun onAllowCellularToggled(allow: Boolean) {
        settingsRepo.setAllowCellular(allow)
        _uiState.update {
            it.copy(userMessage = if (allow) "Cellular downloads enabled" else "Wi-Fi only enabled")
        }
    }

    fun onAutoDetectLinksToggled(detect: Boolean) {
        settingsRepo.setAutoDetectLinks(detect)
    }

    fun cycleMaxParallel() {
        val current = _uiState.value.maxParallelDownloads
        val next = if (current >= 5) 1 else current + 1
        settingsRepo.setMaxParallelDownloads(next)
        _uiState.update { it.copy(userMessage = "Parallel downloads limit: $next tasks") }
    }

    fun onDismissMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
