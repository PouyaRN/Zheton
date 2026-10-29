package com.doxigo.muchtoman.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.doxigo.muchtoman.Hero
import com.doxigo.muchtoman.LocalHeroPalette
import com.doxigo.muchtoman.Space

/**
 * The top of Home and of the detail screens: no box, no corners — the page itself, lit from
 * behind by two soft auras in the theme's accent pair. Revolut's balance sits in exactly this
 * kind of light, and it is what lets the total feel like the subject of the screen without a
 * card drawn around it. The auras fade to the page colour, so the list below starts seamlessly.
 */
@Composable
internal fun HeroPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val page = Hero.bottom
    val accent = Hero.gold
    val glow = LocalHeroPalette.current.glow
    // Light pages need far less: the same alpha on paper reads as a stain, not as light.
    val strength = if (page.luminance() > 0.5f) 0.16f else 0.34f
    Box(
        modifier
            .fillMaxWidth()
            .background(page),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = strength), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.08f),
                    radius = size.width * 0.85f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(glow.copy(alpha = strength * 0.8f), Color.Transparent),
                    center = Offset(size.width * 0.12f, size.height * 0.22f),
                    radius = size.width * 0.75f,
                ),
            )
            // Hand the bottom edge back to the page so no seam shows where the list begins.
            drawRect(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to page,
                ),
            )
        }
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = Space.xl, end = Space.xl, top = Space.s, bottom = Space.l),
            content = content,
        )
    }
}
