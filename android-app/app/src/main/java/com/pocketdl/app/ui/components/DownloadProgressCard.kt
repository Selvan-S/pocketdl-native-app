package com.pocketdl.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketdl.app.ui.mock.DownloadTaskMock
import com.pocketdl.app.ui.mock.TaskStatus
import com.pocketdl.app.ui.theme.BorderDark
import com.pocketdl.app.ui.theme.BrightTeal
import com.pocketdl.app.ui.theme.ElectricCyan
import com.pocketdl.app.ui.theme.EmeraldGreen
import com.pocketdl.app.ui.theme.ErrorRed
import com.pocketdl.app.ui.theme.MetricSmall
import com.pocketdl.app.ui.theme.SurfaceHighDark
import com.pocketdl.app.ui.theme.SurfaceLowDark
import com.pocketdl.app.ui.theme.TextPrimaryDark
import com.pocketdl.app.ui.theme.TextSecondaryDark
import com.pocketdl.app.ui.theme.WarningAmber

@Composable
fun DownloadProgressCard(
    task: DownloadTaskMock,
    onPauseResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLowDark),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = "${task.sourceDomain} • ${task.resolutionBadge} (${task.codecBadge})",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                }
                val statusBadgeVariant = when (task.status) {
                    TaskStatus.DOWNLOADING -> BadgeVariant.CYAN
                    TaskStatus.COMPLETED -> BadgeVariant.EMERALD
                    TaskStatus.PAUSED -> BadgeVariant.AMBER
                    TaskStatus.QUEUED -> BadgeVariant.DEFAULT
                    TaskStatus.FAILED -> BadgeVariant.DEFAULT
                }
                MetricBadge(text = task.statusText, variant = statusBadgeVariant)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(SurfaceHighDark, RoundedCornerShape(3.dp)),
                color = if (task.status == TaskStatus.COMPLETED) EmeraldGreen else ElectricCyan,
                trackColor = SurfaceHighDark
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Speed & ETA Metrics Row
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (task.status == TaskStatus.DOWNLOADING) {
                        Text(
                            text = "${task.speedText} • ETA ${task.etaText}",
                            style = MetricSmall,
                            color = BrightTeal
                        )
                    } else if (task.status == TaskStatus.COMPLETED) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Completed", style = MetricSmall, color = EmeraldGreen)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (task.status == TaskStatus.DOWNLOADING) {
                        IconButton(onClick = onPauseResumeClick, modifier = Modifier.size(28.dp)) {
                            Icon(imageVector = Icons.Default.Pause, contentDescription = "Pause", tint = WarningAmber)
                        }
                    } else if (task.status == TaskStatus.PAUSED) {
                        IconButton(onClick = onPauseResumeClick, modifier = Modifier.size(28.dp)) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Resume", tint = ElectricCyan)
                        }
                    }

                    if (task.status != TaskStatus.COMPLETED) {
                        IconButton(onClick = onCancelClick, modifier = Modifier.size(28.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = ErrorRed)
                        }
                    }
                }
            }
        }
    }
}
