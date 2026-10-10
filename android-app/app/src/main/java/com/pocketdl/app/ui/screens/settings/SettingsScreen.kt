package com.pocketdl.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketdl.app.BuildConfig
import com.pocketdl.app.data.repository.DownloadEngineType
import com.pocketdl.app.ui.components.BadgeVariant
import com.pocketdl.app.ui.components.MetricBadge
import com.pocketdl.app.ui.components.PocketDLTopAppBar
import com.pocketdl.app.ui.theme.BorderDark
import com.pocketdl.app.ui.theme.ElectricCyan
import com.pocketdl.app.ui.theme.MetricSmall
import com.pocketdl.app.ui.theme.SurfaceDark
import com.pocketdl.app.ui.theme.SurfaceHighDark
import com.pocketdl.app.ui.theme.SurfaceLowDark
import com.pocketdl.app.ui.theme.TextPrimaryDark
import com.pocketdl.app.ui.theme.TextSecondaryDark

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onDismissMessage()
        }
    }

    Scaffold(
        topBar = {
            PocketDLTopAppBar(title = "Engine & App Settings")
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SurfaceDark,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Engine Selector Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLowDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Download Engine Core",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Choose active native download worker engine", style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricBadge(
                            text = "Native OkHttp",
                            variant = if (uiState.selectedEngine == DownloadEngineType.NATIVE_OKHTTP) BadgeVariant.CYAN else BadgeVariant.DEFAULT,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.onEngineSelected(DownloadEngineType.NATIVE_OKHTTP) }
                        )
                        MetricBadge(
                            text = "Aria2 Engine",
                            variant = if (uiState.selectedEngine == DownloadEngineType.ARIA2) BadgeVariant.CYAN else BadgeVariant.DEFAULT,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.onEngineSelected(DownloadEngineType.ARIA2) }
                        )
                        MetricBadge(
                            text = "FFmpeg HLS",
                            variant = if (uiState.selectedEngine == DownloadEngineType.FFMPEG_HLS) BadgeVariant.CYAN else BadgeVariant.DEFAULT,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.onEngineSelected(DownloadEngineType.FFMPEG_HLS) }
                        )
                    }
                }
            }

            // Engine Limits & Network Preferences
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLowDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Network & Bandwidth",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Max Parallel Downloads", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                            Text(text = "Tap to cycle 1-5 concurrent tasks", style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
                        }
                        MetricBadge(
                            text = "${uiState.maxParallelDownloads} Tasks",
                            variant = BadgeVariant.CYAN,
                            modifier = Modifier.clickable { viewModel.cycleMaxParallel() }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Allow Cellular Downloads", style = MaterialTheme.typography.bodyMedium, color = TextPrimaryDark)
                            Text(text = "Download without Wi-Fi connection", style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
                        }
                        Switch(
                            checked = uiState.allowCellular,
                            onCheckedChange = { viewModel.onAllowCellularToggled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SurfaceDark,
                                checkedTrackColor = ElectricCyan
                            )
                        )
                    }
                }
            }

            // Storage Path Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLowDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Download Location",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.downloadPath,
                        style = MetricSmall,
                        color = ElectricCyan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SurfaceHighDark, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )
                }
            }

            // App Information & Version
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PocketDL v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark
                )
            }
        }
    }
}
