package com.pocketdl.app.ui.screens.captured

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.pocketdl.app.ui.components.MediaItemCard
import com.pocketdl.app.ui.components.MetricBadge
import com.pocketdl.app.ui.components.PocketDLTopAppBar
import com.pocketdl.app.ui.components.QualitySelectionBottomSheet
import com.pocketdl.app.ui.mock.MockDataProvider
import com.pocketdl.app.ui.theme.ElectricCyan
import com.pocketdl.app.ui.theme.SurfaceDark
import com.pocketdl.app.ui.theme.TextPrimaryDark
import com.pocketdl.app.ui.theme.TextSecondaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturedScreen(
    onMediaSelected: (String) -> Unit,
    onNavigateToAnalysis: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val capturedItems = MockDataProvider.sampleCapturedList
    val qualityOptions = MockDataProvider.sampleQualityOptions
    var showQualitySheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            PocketDLTopAppBar(title = "Captured Media Inbox")
        },
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
                    MetricBadge(text = "All (${capturedItems.size})", variant = BadgeVariant.CYAN)
                    MetricBadge(text = "Video", variant = BadgeVariant.DEFAULT)
                    MetricBadge(text = "HLS", variant = BadgeVariant.DEFAULT)
                    MetricBadge(text = "Audio", variant = BadgeVariant.DEFAULT)
                }

                Button(
                    onClick = { showQualitySheet = true },
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

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(capturedItems, key = { it.id }) { media ->
                    MediaItemCard(
                        item = media,
                        onDownloadClick = { onMediaSelected(media.id) },
                        onQualityClick = { showQualitySheet = true },
                        onSniffClick = { onNavigateToAnalysis(media.id) },
                        onDeleteClick = {}
                    )
                }
            }
        }

        if (showQualitySheet) {
            QualitySelectionBottomSheet(
                sheetState = sheetState,
                options = qualityOptions,
                onDismissRequest = { showQualitySheet = false },
                onConfirmQuality = { selectedOption ->
                    showQualitySheet = false
                }
            )
        }
    }
}
