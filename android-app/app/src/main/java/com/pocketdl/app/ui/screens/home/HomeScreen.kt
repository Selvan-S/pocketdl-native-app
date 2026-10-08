package com.pocketdl.app.ui.screens.home

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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketdl.app.ui.components.DownloadProgressCard
import com.pocketdl.app.ui.components.ExtensionStatusCard
import com.pocketdl.app.ui.components.MediaItemCard
import com.pocketdl.app.ui.components.PocketDLTopAppBar
import com.pocketdl.app.ui.components.UrlInputField
import com.pocketdl.app.ui.mock.MockDataProvider
import com.pocketdl.app.ui.theme.BorderDark
import com.pocketdl.app.ui.theme.ElectricCyan
import com.pocketdl.app.ui.theme.MetricSmall
import com.pocketdl.app.ui.theme.SurfaceDark
import com.pocketdl.app.ui.theme.SurfaceLowDark
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
    modifier: Modifier = Modifier
) {
    val extensionStatus = MockDataProvider.sampleExtensionStatus
    val capturedList = MockDataProvider.sampleCapturedList
    val activeTask = MockDataProvider.sampleDownloadsList.firstOrNull()

    Scaffold(
        topBar = {
            PocketDLTopAppBar(title = "PocketDL Engine")
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
            // Hero URL Input
            UrlInputField(
                onDetectUrl = { url ->
                    onNavigateToAnalysis("cap_1")
                }
            )

            // Extension Status Banner
            ExtensionStatusCard(
                status = extensionStatus,
                onCardClick = onNavigateToExtension
            )

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
            if (activeTask != null) {
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
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    DownloadProgressCard(
                        task = activeTask,
                        onPauseResumeClick = {},
                        onCancelClick = {}
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
                        text = "Captured Inbox (${capturedList.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "See Inbox",
                        style = MetricSmall,
                        color = ElectricCyan,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                capturedList.take(2).forEach { media ->
                    MediaItemCard(
                        item = media,
                        onDownloadClick = { onNavigateToMediaDetail(media.id) },
                        onQualityClick = { onNavigateToMediaDetail(media.id) },
                        onSniffClick = { onNavigateToAnalysis(media.id) },
                        onDeleteClick = {},
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }
        }
    }
}
