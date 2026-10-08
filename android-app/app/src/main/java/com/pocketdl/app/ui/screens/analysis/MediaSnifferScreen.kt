package com.pocketdl.app.ui.screens.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
fun MediaSnifferScreen(
    mediaId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MediaSnifferViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(mediaId) {
        viewModel.loadMedia(mediaId)
    }

    Scaffold(
        topBar = {
            PocketDLTopAppBar(title = "Media Sniffer Analysis", onBackClick = onBackClick)
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
            // Sniffer Header Info
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
                        Text(
                            text = "Socket Sniffer Output",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        MetricBadge(text = uiState.statusText, variant = BadgeVariant.EMERALD)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = uiState.media?.originalUrl ?: "Socket stream",
                        style = MetricSmall,
                        color = ElectricCyan
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = uiState.timestampText,
                        style = MetricSmall,
                        color = TextSecondaryDark
                    )
                }
            }

            // Technical Manifest / Chunk Inspector
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
                        Text(
                            text = "Extracted Manifest Headers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )

                        Button(
                            onClick = { viewModel.refreshAnalysis() },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceHighDark,
                                contentColor = ElectricCyan
                            ),
                            enabled = !uiState.isReanalyzing,
                            modifier = Modifier.height(32.dp)
                        ) {
                            if (uiState.isReanalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = ElectricCyan
                                )
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "Re-Sniff", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    uiState.headers.forEach { (key, value) ->
                        Text(
                            text = "$key: $value",
                            style = MetricSmall,
                            color = TextSecondaryDark,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(SurfaceHighDark, RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}
