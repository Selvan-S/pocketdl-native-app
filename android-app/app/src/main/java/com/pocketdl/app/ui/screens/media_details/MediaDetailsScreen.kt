package com.pocketdl.app.ui.screens.media_details

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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketdl.app.ui.components.BadgeVariant
import com.pocketdl.app.ui.components.MetricBadge
import com.pocketdl.app.ui.components.PocketDLTopAppBar
import com.pocketdl.app.ui.components.QualitySelectionBottomSheet
import com.pocketdl.app.ui.mock.MockDataProvider
import com.pocketdl.app.ui.theme.BorderDark
import com.pocketdl.app.ui.theme.BorderVariantDark
import com.pocketdl.app.ui.theme.ElectricCyan
import com.pocketdl.app.ui.theme.MetricLarge
import com.pocketdl.app.ui.theme.MetricSmall
import com.pocketdl.app.ui.theme.SurfaceDark
import com.pocketdl.app.ui.theme.SurfaceHighDark
import com.pocketdl.app.ui.theme.SurfaceLowDark
import com.pocketdl.app.ui.theme.TextPrimaryDark
import com.pocketdl.app.ui.theme.TextSecondaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaDetailsScreen(
    mediaId: String,
    onBackClick: () -> Unit,
    onNavigateToAnalysis: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val media = MockDataProvider.sampleCapturedList.find { it.id == mediaId } ?: MockDataProvider.sampleCapturedList.first()
    val options = MockDataProvider.sampleQualityOptions
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            PocketDLTopAppBar(title = "Media Specs & Quality", onBackClick = onBackClick)
        },
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
            // Media Preview Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLowDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(SurfaceHighDark, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderVariantDark, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(48.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = media.originalUrl,
                        style = MetricSmall,
                        color = TextSecondaryDark
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MetricBadge(text = media.resolutionBadge, variant = BadgeVariant.CYAN)
                        MetricBadge(text = media.formatBadge, variant = BadgeVariant.DEFAULT)
                        MetricBadge(text = media.durationText, variant = BadgeVariant.OUTLINE)
                    }
                }
            }

            // Available Streams Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceLowDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Extracted Stream Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(SurfaceHighDark, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = option.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                                Text(text = "${option.codec} • ${option.container} • ${option.bitrateText}", style = MetricSmall, color = TextSecondaryDark)
                            }
                            Text(text = option.sizeText, style = MetricLarge, color = ElectricCyan)
                        }
                    }
                }
            }

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showSheet = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = SurfaceDark),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Download Stream", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { onNavigateToAnalysis(media.id) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Sniff", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (showSheet) {
            QualitySelectionBottomSheet(
                sheetState = sheetState,
                options = options,
                onDismissRequest = { showSheet = false },
                onConfirmQuality = { showSheet = false }
            )
        }
    }
}
