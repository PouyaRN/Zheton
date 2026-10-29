package com.doxigo.muchtoman.ui.rikka.components.badge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.ui.rikka.foundation.RikkaTheme

enum class BadgeVariant {
    Default,
    Secondary,
    Outline,
    Destructive,
    Success,
}

@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    variant: BadgeVariant = BadgeVariant.Default,
) {
    val colors = RikkaTheme.colors
    val (bg, textColor, border) = when (variant) {
        BadgeVariant.Default -> Triple(colors.primary, colors.onPrimary, Color.Transparent)
        BadgeVariant.Secondary -> Triple(colors.secondary, colors.onSecondary, Color.Transparent)
        BadgeVariant.Outline -> Triple(Color.Transparent, colors.onSurface, colors.border)
        BadgeVariant.Destructive -> Triple(colors.destructive.copy(alpha = 0.18f), colors.destructive, Color.Transparent)
        BadgeVariant.Success -> Triple(colors.success.copy(alpha = 0.18f), colors.success, Color.Transparent)
    }

    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .then(if (border != Color.Transparent) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = Modam,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = textColor
            )
        )
    }
}
