package com.pocketdl.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketdl.app.ui.mock.StorageUsageMock
import com.pocketdl.app.ui.theme.BorderDark
import com.pocketdl.app.ui.theme.ElectricCyan
import com.pocketdl.app.ui.theme.EmeraldGreen
import com.pocketdl.app.ui.theme.MetricSmall
import com.pocketdl.app.ui.theme.SecondaryGreen
import com.pocketdl.app.ui.theme.SurfaceHighDark
import com.pocketdl.app.ui.theme.SurfaceLowDark
import com.pocketdl.app.ui.theme.TextPrimaryDark
import com.pocketdl.app.ui.theme.TextSecondaryDark

@Composable
fun StorageBreakdownBar(
    storage: StorageUsageMock,
    modifier: Modifier = Modifier
) {
    val usedTotalGb = storage.mediaGb + storage.appGb
    val mediaWeight = (storage.mediaGb / storage.totalGb).toFloat()
    val appWeight = (storage.appGb / storage.totalGb).toFloat()
    val freeWeight = (storage.freeGb / storage.totalGb).toFloat()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLowDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Storage Utilization",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${String.format("%.1f", usedTotalGb)} GB / ${storage.totalGb.toInt()} GB Used",
                    style = MetricSmall,
                    color = ElectricCyan,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Segmented Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(SurfaceHighDark, RoundedCornerShape(5.dp))
            ) {
                if (mediaWeight > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(mediaWeight)
                            .background(ElectricCyan, RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
                    )
                }
                if (appWeight > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(appWeight)
                            .background(SecondaryGreen)
                    )
                }
                if (freeWeight > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(freeWeight)
                            .background(SurfaceHighDark, RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Legend with non-squeezing vertical layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StorageLegendItem(
                    color = ElectricCyan,
                    label = "Media Files",
                    value = "${storage.mediaGb} GB",
                    modifier = Modifier.weight(1f)
                )
                StorageLegendItem(
                    color = SecondaryGreen,
                    label = "App Cache",
                    value = "${storage.appGb} GB",
                    modifier = Modifier.weight(1f)
                )
                StorageLegendItem(
                    color = TextSecondaryDark,
                    label = "Free Space",
                    value = "${storage.freeGb} GB",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StorageLegendItem(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MetricSmall,
            color = TextPrimaryDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 14.dp)
        )
    }
}

