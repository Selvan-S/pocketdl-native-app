package com.pocketdl.app.ui.screens.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.pocketdl.app.ui.components.DownloadProgressCard
import com.pocketdl.app.ui.components.ExtensionStatusCard
import com.pocketdl.app.ui.components.MediaItemCard
import com.pocketdl.app.ui.components.PocketDLTopAppBar
import com.pocketdl.app.ui.components.UrlInputField
import com.pocketdl.app.ui.theme.ElectricCyan
import com.pocketdl.app.ui.theme.MetricSmall
import com.pocketdl.app.ui.theme.SurfaceDark
import com.pocketdl.app.ui.theme.TextPrimaryDark
import com.pocketdl.app.ui.theme.TextSecondaryDark

@Composable
fun HomeScreen(
    onNavigateToCaptured: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToExtension: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToMediaDetail: (String) -> Unit,
    onNavigateToAnalysis: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
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
            PocketDLTopAppBar(title = "PocketDL Engine")
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
            // Hero URL Input
            UrlInputField(
                onDetectUrl = { url ->
                    viewModel.onDetectUrl(url) { targetId ->
                        onNavigateToAnalysis(targetId)
                    }
                }
            )

            // Extension Status Banner
            uiState.extensionStatus?.let { status ->
                ExtensionStatusCard(
                    status = status,
                    onCardClick = onNavigateToExtension
                )
            }

            // Quick Actions Grid Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToQueue,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ListAlt, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.padding(start = 4.dp))
                    Text(text = "Queue", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = onNavigateToStorage,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.padding(start = 4.dp))
                    Text(text = "Storage", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Active Downloads Section
            uiState.activeDownload?.let { activeTask ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Transfer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "View All",
                            style = MetricSmall,
                            color = ElectricCyan,
                            modifier = Modifier
                                .clickable { onNavigateToDownloads() }
                                .padding(vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    DownloadProgressCard(
                        task = activeTask,
                        onPauseResumeClick = { viewModel.onPauseResumeActiveDownload() },
                        onCancelClick = { viewModel.onCancelActiveDownload() }
                    )
                }
            }

            // Recent Captured Section
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Captured Inbox (${uiState.recentCaptured.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "See Inbox",
                        style = MetricSmall,
                        color = ElectricCyan,
                        modifier = Modifier
                            .clickable { onNavigateToCaptured() }
                            .padding(vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.recentCaptured.isEmpty()) {
                    Text(
                        text = "Inbox is empty. Paste a URL or capture media from the browser extension.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    uiState.recentCaptured.forEach { media ->
                        MediaItemCard(
                            item = media,
                            onDownloadClick = { onNavigateToMediaDetail(media.id) },
                            onQualityClick = { onNavigateToMediaDetail(media.id) },
                            onSniffClick = { onNavigateToAnalysis(media.id) },
                            onDeleteClick = { viewModel.onDeleteCaptured(media.id) },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
