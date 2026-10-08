package com.pocketdl.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pocketdl.app.ui.theme.BorderVariantDark
import com.pocketdl.app.ui.theme.ElectricCyan
import com.pocketdl.app.ui.theme.EmeraldGreen
import com.pocketdl.app.ui.theme.MetricSmall
import com.pocketdl.app.ui.theme.PillShape
import com.pocketdl.app.ui.theme.SurfaceDark
import com.pocketdl.app.ui.theme.SurfaceHighDark
import com.pocketdl.app.ui.theme.TextPrimaryDark
import com.pocketdl.app.ui.theme.TextSecondaryDark
import com.pocketdl.app.ui.theme.WarningAmber

enum class BadgeVariant {
    DEFAULT,
    CYAN,
    EMERALD,
    AMBER,
    OUTLINE
}

@Composable
fun MetricBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.DEFAULT
) {
    val (backgroundColor, textColor, borderColor) = when (variant) {
        BadgeVariant.DEFAULT -> Triple(SurfaceHighDark, TextSecondaryDark, Color.Transparent)
        BadgeVariant.CYAN -> Triple(ElectricCyan, SurfaceDark, Color.Transparent)
        BadgeVariant.EMERALD -> Triple(EmeraldGreen, SurfaceDark, Color.Transparent)
        BadgeVariant.AMBER -> Triple(WarningAmber, SurfaceDark, Color.Transparent)
        BadgeVariant.OUTLINE -> Triple(SurfaceDark, TextPrimaryDark, BorderVariantDark)
    }

    Box(
        modifier = modifier
            .background(color = backgroundColor, shape = PillShape)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(width = 1.dp, color = borderColor, shape = PillShape)
                } else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MetricSmall,
            color = textColor
        )
    }
}
