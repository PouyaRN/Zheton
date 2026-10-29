package com.doxigo.muchtoman.ui.rikka.components.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.doxigo.muchtoman.ui.rikka.foundation.RikkaTheme

@Composable
fun Progress(
    value: Float,
    modifier: Modifier = Modifier,
    color: Color = RikkaTheme.colors.primary,
    trackColor: Color = RikkaTheme.colors.secondary,
    height: Dp = 6.dp,
) {
    val clamped = value.coerceIn(0f, 1f)
    val shape = RoundedCornerShape(height / 2f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor, shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clamped)
                .fillMaxHeight()
                .clip(shape)
                .background(color, shape)
        )
    }
}
