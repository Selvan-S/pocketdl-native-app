package com.pocketdl.app.ui.screens.download_details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.pocketdl.app.ui.components.BadgeVariant
import com.pocketdl.app.ui.components.MetricBadge
import com.pocketdl.app.ui.components.PocketDLTopAppBar
import com.pocketdl.app.ui.mock.TaskStatus
import com.pocketdl.app.ui.theme.BorderDark
import com.pocketdl.app.ui.theme.BrightTeal
import com.pocketdl.app.ui.theme.ElectricCyan
import com.pocketdl.app.ui.theme.EmeraldGreen
import com.pocketdl.app.ui.theme.ErrorRed
import com.pocketdl.app.ui.theme.MetricSmall
import com.pocketdl.app.ui.theme.SurfaceDark
import com.pocketdl.app.ui.theme.SurfaceHighDark
import com.pocketdl.app.ui.theme.SurfaceLowDark
import com.pocketdl.app.ui.theme.TextPrimaryDark
import com.pocketdl.app.ui.theme.TextSecondaryDark
import com.pocketdl.app.ui.theme.WarningAmber

@Composable
fun DownloadDetailsScreen(
    downloadId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadDetailsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(downloadId) {
        viewModel.loadTask(downloadId)
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onDismissMessage()
        }
    }

    Scaffold(
        topBar = {
            PocketDLTopAppBar(title = "Download Task Specs", onBackClick = onBackClick)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SurfaceDark,
        modifier = modifier
    ) { innerPadding ->
        if (uiState.isNotFound) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Download task not found: $downloadId",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondaryDark
                )
            }
        } else {
            val task = uiState.task
            if (task != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Task Progress & Status Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceLowDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimaryDark
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = task.sourceDomain,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondaryDark
                                    )
                                }

                                val badgeVariant = when (task.status) {
                                    TaskStatus.DOWNLOADING -> BadgeVariant.CYAN
                                    TaskStatus.COMPLETED -> BadgeVariant.EMERALD
                                    TaskStatus.PAUSED -> BadgeVariant.AMBER
                                    TaskStatus.QUEUED -> BadgeVariant.DEFAULT
                                    TaskStatus.FAILED -> BadgeVariant.DEFAULT
                                }
                                MetricBadge(text = task.statusText, variant = badgeVariant)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Progress track
                            LinearProgressIndicator(
                                progress = { task.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .background(SurfaceHighDark, RoundedCornerShape(4.dp)),
                                color = if (task.status == TaskStatus.COMPLETED) EmeraldGreen else ElectricCyan,
                                trackColor = SurfaceHighDark
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${task.downloadedSizeText} / ${task.totalSizeText} (${(task.progress * 100).toInt()}%)",
                                    style = MetricSmall,
                                    color = TextPrimaryDark
                                )

                                if (task.status == TaskStatus.DOWNLOADING) {
                                    Text(
                                        text = "${task.speedText} • ETA ${task.etaText}",
                                        style = MetricSmall,
                                        color = BrightTeal
                                    )
                                }
                            }
                        }
                    }

                    // Technical Specifications Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceLowDark),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Technical Stream Attributes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricBadge(text = task.resolutionBadge, variant = BadgeVariant.CYAN)
                                MetricBadge(text = task.codecBadge, variant = BadgeVariant.DEFAULT)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(text = "Local Destination:", style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
                            Spacer(modifier = Modifier.height(4.dp))
                            val destinationText = task.localPath ?: "Pending download..."
                            Text(
                                text = destinationText,
                                style = MetricSmall,
                                color = ElectricCyan,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SurfaceHighDark, RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            )

                            if (task.sourceUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = "Source URL:", style = MaterialTheme.typography.bodySmall, color = TextSecondaryDark)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = task.sourceUrl,
                                    style = MetricSmall,
                                    color = ElectricCyan,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SurfaceHighDark, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                )
                            }
                        }
                    }

                    // Action Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (task.status == TaskStatus.DOWNLOADING) {
                            Button(
                                onClick = { viewModel.onPauseResume() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = SurfaceDark),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Pause Task", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        } else if (task.status == TaskStatus.PAUSED || task.status == TaskStatus.QUEUED) {
                            Button(
                                onClick = { viewModel.onPauseResume() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = SurfaceDark),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Resume Task", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        } else if (task.status == TaskStatus.FAILED) {
                            Button(
                                onClick = { viewModel.onRetry() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = SurfaceDark),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Retry Download", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.onCancel()
                                onBackClick()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = ErrorRed)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Cancel", style = MaterialTheme.typography.titleMedium, color = ErrorRed)
                        }
                    }
                }
            }
        }
    }
}
