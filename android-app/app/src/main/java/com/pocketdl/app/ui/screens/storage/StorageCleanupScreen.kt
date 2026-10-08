package com.pocketdl.app.ui.screens.storage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.pocketdl.app.ui.components.PocketDLTopAppBar
import com.pocketdl.app.ui.components.StorageBreakdownBar
import com.pocketdl.app.ui.mock.MockDataProvider
import com.pocketdl.app.ui.theme.ElectricCyan
import com.pocketdl.app.ui.theme.ErrorRed
import com.pocketdl.app.ui.theme.MetricSmall
import com.pocketdl.app.ui.theme.SurfaceDark
import com.pocketdl.app.ui.theme.TextPrimaryDark
import com.pocketdl.app.ui.theme.TextSecondaryDark

@Composable
fun StorageCleanupScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val downloads = MockDataProvider.sampleDownloadsList
    val storage = MockDataProvider.sampleStorage

    Scaffold(
        topBar = {
            PocketDLTopAppBar(title = "Batch Edit & Storage Cleanup", onBackClick = onBackClick)
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
            StorageBreakdownBar(storage = storage)

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Storage Cleanup Tools", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                    Text(text = "Select items to free up disk space", style = MetricSmall, color = TextSecondaryDark)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {},
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = SurfaceDark),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 4.dp))
                        Text(text = "Purge Selected", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloads, key = { it.id }) { task ->
                    DownloadProgressCard(
                        task = task,
                        onPauseResumeClick = {},
                        onCancelClick = {}
                    )
                }
            }
        }
    }
}
