package com.pocketdl.app.ui.screens.downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketdl.app.ui.components.BadgeVariant
import com.pocketdl.app.ui.components.DownloadProgressCard
import com.pocketdl.app.ui.components.MetricBadge
import com.pocketdl.app.ui.components.PocketDLTopAppBar
import com.pocketdl.app.ui.components.StorageBreakdownBar
import com.pocketdl.app.ui.theme.SurfaceDark
import com.pocketdl.app.ui.theme.TextSecondaryDark

@Composable
fun DownloadsScreen(
    onDownloadSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = viewModel()
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
            PocketDLTopAppBar(title = "Downloads Library")
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SurfaceDark,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Storage Bar Header
            uiState.storage?.let { storage ->
                StorageBreakdownBar(storage = storage)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricBadge(
                    text = "All (${uiState.totalCount})",
                    variant = if (uiState.filter == DownloadFilter.ALL) BadgeVariant.CYAN else BadgeVariant.DEFAULT,
                    modifier = Modifier.clickable { viewModel.onFilterSelected(DownloadFilter.ALL) }
                )
                MetricBadge(
                    text = "Active (${uiState.activeCount})",
                    variant = if (uiState.filter == DownloadFilter.ACTIVE) BadgeVariant.CYAN else BadgeVariant.DEFAULT,
                    modifier = Modifier.clickable { viewModel.onFilterSelected(DownloadFilter.ACTIVE) }
                )
                MetricBadge(
                    text = "Completed (${uiState.completedCount})",
                    variant = if (uiState.filter == DownloadFilter.COMPLETED) BadgeVariant.EMERALD else BadgeVariant.DEFAULT,
                    modifier = Modifier.clickable { viewModel.onFilterSelected(DownloadFilter.COMPLETED) }
                )
                MetricBadge(
                    text = "Queued (${uiState.queuedCount})",
                    variant = if (uiState.filter == DownloadFilter.QUEUED) BadgeVariant.CYAN else BadgeVariant.DEFAULT,
                    modifier = Modifier.clickable { viewModel.onFilterSelected(DownloadFilter.QUEUED) }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "No download tasks found in '${uiState.filter.label}' section.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryDark
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.items, key = { it.id }) { task ->
                        DownloadProgressCard(
                            task = task,
                            onPauseResumeClick = { viewModel.onPauseResume(task.id) },
                            onCancelClick = { viewModel.onCancel(task.id) },
                            modifier = Modifier.clickable { onDownloadSelected(task.id) }
                        )
                    }
                }
            }
        }
    }
}
