package com.doxigo.muchtoman.ui.rikka.components.separator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.doxigo.muchtoman.ui.rikka.foundation.RikkaTheme

@Composable
fun Separator(
    modifier: Modifier = Modifier,
    color: Color = RikkaTheme.colors.border,
    thickness: Dp = 1.dp,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
    ) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height
        )
    }
}
