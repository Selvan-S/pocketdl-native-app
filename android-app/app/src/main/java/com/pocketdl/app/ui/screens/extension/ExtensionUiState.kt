package com.pocketdl.app.ui.screens.extension

import com.pocketdl.app.ui.mock.ExtensionStatusMock

data class ExtensionUiState(
    val isLoading: Boolean = false,
    val status: ExtensionStatusMock? = null,
    val pairingToken: String = "PKT-9482-WIFI",
    val isRegeneratingToken: Boolean = false,
    val userMessage: String? = null
)
