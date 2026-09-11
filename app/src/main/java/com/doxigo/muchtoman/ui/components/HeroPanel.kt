package com.doxigo.muchtoman.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.doxigo.muchtoman.Hero
import com.doxigo.muchtoman.Radius
import com.doxigo.muchtoman.Space

/**
 * Atmospheric gradient hero container shared across Dashboard header and Timeline transaction details.
 * Encapsulates radial specular lighting and top status bar insets.
 */
@Composable
internal fun HeroPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val topColor = Hero.top
    val bottomColor = Hero.bottom
    val goldColor = Hero.gold
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = Radius.hero, bottomEnd = Radius.hero))
            .background(Brush.verticalGradient(listOf(topColor, bottomColor))),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(goldColor.copy(alpha = 0.13f), Color.Transparent),
                    center = Offset(size.width * 0.88f, size.height * -0.05f),
                    radius = size.width * 0.95f,
                ),
            )
        }
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = Space.xl, end = Space.xl, top = Space.s, bottom = Space.xl),
            content = content,
        )
    }
}
