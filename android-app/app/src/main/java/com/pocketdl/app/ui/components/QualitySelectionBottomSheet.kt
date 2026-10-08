package com.pocketdl.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketdl.app.ui.mock.QualityOptionMock
import com.pocketdl.app.ui.theme.BorderDark
import com.pocketdl.app.ui.theme.BottomSheetShape
import com.pocketdl.app.ui.theme.ElectricCyan
import com.pocketdl.app.ui.theme.MetricSmall
import com.pocketdl.app.ui.theme.SurfaceDark
import com.pocketdl.app.ui.theme.SurfaceHighDark
import com.pocketdl.app.ui.theme.SurfaceLowDark
import com.pocketdl.app.ui.theme.TextPrimaryDark
import com.pocketdl.app.ui.theme.TextSecondaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySelectionBottomSheet(
    sheetState: SheetState,
    options: List<QualityOptionMock>,
    onDismissRequest: () -> Unit,
    onConfirmQuality: (QualityOptionMock) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedId by remember { mutableStateOf(options.firstOrNull { it.isSelected }?.id ?: options.firstOrNull()?.id ?: "") }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = SurfaceDark,
        contentColor = TextPrimaryDark,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Select Stream Quality & Codec",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark
            )
            Text(
                text = "Extracted media streams available for download",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark
            )

            Spacer(modifier = Modifier.height(16.dp))

            options.forEach { option ->
                val isSelected = option.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            color = if (isSelected) SurfaceHighDark else SurfaceLowDark,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) ElectricCyan else BorderDark,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedId = option.id }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "${option.codec} • ${option.container} • ${option.bitrateText}",
                            style = MetricSmall,
                            color = TextSecondaryDark
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetricBadge(
                            text = option.sizeText,
                            variant = if (isSelected) BadgeVariant.CYAN else BadgeVariant.DEFAULT
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = ElectricCyan
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val selected = options.find { it.id == selectedId } ?: options.first()
                    onConfirmQuality(selected)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = SurfaceDark
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start Download",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
