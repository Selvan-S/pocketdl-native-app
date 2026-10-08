package com.pocketdl.app.ui.screens.captured

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.pocketdl.app.ui.components.MediaItemCard
import com.pocketdl.app.ui.components.MetricBadge
import com.pocketdl.app.ui.components.PocketDLTopAppBar
import com.pocketdl.app.ui.components.QualitySelectionBottomSheet
import com.pocketdl.app.ui.theme.ElectricCyan
import com.pocketdl.app.ui.theme.SurfaceDark
import com.pocketdl.app.ui.theme.TextSecondaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturedScreen(
    onMediaSelected: (String) -> Unit,
    onNavigateToAnalysis: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CapturedViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onDismissMessage()
        }
    }

    Scaffold(
        topBar = {
            PocketDLTopAppBar(title = "Captured Media Inbox")
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
            // Filter Bar & Download All
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricBadge(
                        text = "All (${uiState.totalCount})",
                        variant = if (uiState.filter == CapturedFilter.ALL) BadgeVariant.CYAN else BadgeVariant.DEFAULT,
                        modifier = Modifier.clickable { viewModel.onFilterSelected(CapturedFilter.ALL) }
                    )
                    MetricBadge(
                        text = "Video (${uiState.videoCount})",
                        variant = if (uiState.filter == CapturedFilter.VIDEO) BadgeVariant.CYAN else BadgeVariant.DEFAULT,
                        modifier = Modifier.clickable { viewModel.onFilterSelected(CapturedFilter.VIDEO) }
                    )
                    MetricBadge(
                        text = "HLS (${uiState.hlsCount})",
                        variant = if (uiState.filter == CapturedFilter.HLS) BadgeVariant.CYAN else BadgeVariant.DEFAULT,
                        modifier = Modifier.clickable { viewModel.onFilterSelected(CapturedFilter.HLS) }
                    )
                    MetricBadge(
                        text = "Audio (${uiState.audioCount})",
                        variant = if (uiState.filter == CapturedFilter.AUDIO) BadgeVariant.CYAN else BadgeVariant.DEFAULT,
                        modifier = Modifier.clickable { viewModel.onFilterSelected(CapturedFilter.AUDIO) }
                    )
                }

                Button(
                    onClick = { viewModel.onDownloadAllClick() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = SurfaceDark
                    ),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.padding(start = 4.dp))
                    Text(text = "Download All", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "No media items found for '${uiState.filter.label}' filter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryDark
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.items, key = { it.id }) { media ->
                        MediaItemCard(
                            item = media,
                            onDownloadClick = { onMediaSelected(media.id) },
                            onQualityClick = { viewModel.onOpenQualitySheet(media) },
                            onSniffClick = { onNavigateToAnalysis(media.id) },
                            onDeleteClick = { viewModel.onDeleteCaptured(media.id) }
                        )
                    }
                }
            }
        }

        if (uiState.showQualitySheet) {
            QualitySelectionBottomSheet(
                sheetState = sheetState,
                options = uiState.qualityOptions,
                onDismissRequest = { viewModel.onDismissQualitySheet() },
                onConfirmQuality = { selectedOption ->
                    viewModel.onConfirmQuality(selectedOption)
                }
            )
        }
    }
}
