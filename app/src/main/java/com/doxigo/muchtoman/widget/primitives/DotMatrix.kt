package com.doxigo.muchtoman.widget.primitives

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.doxigo.muchtoman.AndisTheme

@Immutable
object DotMatrixGlyphs {
    const val MATRIX_WIDTH = 5
    const val MATRIX_HEIGHT = 7

    private val GLYPH_EMPTY = intArrayOf(0, 0, 0, 0, 0, 0, 0)

    private val GLYPHS: Map<Char, IntArray> = mapOf(
        // Numbers (Latin 0-9)
        '0' to intArrayOf(0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110),
        '1' to intArrayOf(0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110),
        '2' to intArrayOf(0b01110, 0b10001, 0b00001, 0b00110, 0b01000, 0b10000, 0b11111),
        '3' to intArrayOf(0b11110, 0b00001, 0b00001, 0b01110, 0b00001, 0b00001, 0b11110),
        '4' to intArrayOf(0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010),
        '5' to intArrayOf(0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110),
        '6' to intArrayOf(0b01110, 0b10000, 0b11110, 0b10001, 0b10001, 0b10001, 0b01110),
        '7' to intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000),
        '8' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110),
        '9' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00001, 0b01110),

        // Persian Numerals (۰-۹ / U+06F0 - U+06F9)
        '۰' to intArrayOf(0b00000, 0b00000, 0b00100, 0b01110, 0b00100, 0b00000, 0b00000),
        '۱' to intArrayOf(0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100),
        '۲' to intArrayOf(0b01101, 0b00101, 0b00011, 0b00001, 0b00001, 0b00001, 0b00001),
        '۳' to intArrayOf(0b10101, 0b10101, 0b01111, 0b00001, 0b00001, 0b00001, 0b00001),
        '۴' to intArrayOf(0b00010, 0b00100, 0b01000, 0b11110, 0b00101, 0b00010, 0b00001),
        '۵' to intArrayOf(0b00100, 0b01010, 0b10001, 0b10001, 0b10001, 0b01010, 0b00100),
        '۶' to intArrayOf(0b01111, 0b00001, 0b00001, 0b00001, 0b00001, 0b00001, 0b00001),
        '۷' to intArrayOf(0b10001, 0b10001, 0b10001, 0b01010, 0b01010, 0b00100, 0b00100),
        '۸' to intArrayOf(0b00100, 0b00100, 0b01010, 0b01010, 0b10001, 0b10001, 0b10001),
        '۹' to intArrayOf(0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00001, 0b00001),

        // Uppercase Latin Letters
        'A' to intArrayOf(0b01110, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001),
        'B' to intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10001, 0b10001, 0b11110),
        'C' to intArrayOf(0b01111, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b01111),
        'D' to intArrayOf(0b11110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b11110),
        'E' to intArrayOf(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b11111),
        'F' to intArrayOf(0b11111, 0b10000, 0b10000, 0b11110, 0b10000, 0b10000, 0b10000),
        'G' to intArrayOf(0b01111, 0b10000, 0b10000, 0b10111, 0b10001, 0b10001, 0b01111),
        'H' to intArrayOf(0b10001, 0b10001, 0b10001, 0b11111, 0b10001, 0b10001, 0b10001),
        'I' to intArrayOf(0b01110, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110),
        'J' to intArrayOf(0b00111, 0b00010, 0b00010, 0b00010, 0b10010, 0b10010, 0b01100),
        'K' to intArrayOf(0b10001, 0b10010, 0b10100, 0b11000, 0b10100, 0b10010, 0b10001),
        'L' to intArrayOf(0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b10000, 0b11111),
        'M' to intArrayOf(0b10001, 0b11011, 0b10101, 0b10101, 0b10001, 0b10001, 0b10001),
        'N' to intArrayOf(0b10001, 0b11001, 0b10101, 0b10011, 0b10001, 0b10001, 0b10001),
        'O' to intArrayOf(0b01110, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110),
        'P' to intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10000, 0b10000, 0b10000),
        'Q' to intArrayOf(0b01110, 0b10001, 0b10001, 0b10001, 0b10101, 0b10010, 0b01101),
        'R' to intArrayOf(0b11110, 0b10001, 0b10001, 0b11110, 0b10100, 0b10010, 0b10001),
        'S' to intArrayOf(0b01111, 0b10000, 0b10000, 0b01110, 0b00001, 0b00001, 0b11110),
        'T' to intArrayOf(0b11111, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100, 0b00100),
        'U' to intArrayOf(0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b10001, 0b01110),
        'V' to intArrayOf(0b10001, 0b10001, 0b10001, 0b10001, 0b01010, 0b01010, 0b00100),
        'W' to intArrayOf(0b10001, 0b10001, 0b10001, 0b10101, 0b10101, 0b11011, 0b10001),
        'X' to intArrayOf(0b10001, 0b10001, 0b01010, 0b00100, 0b01010, 0b10001, 0b10001),
        'Y' to intArrayOf(0b10001, 0b10001, 0b01010, 0b00100, 0b00100, 0b00100, 0b00100),
        'Z' to intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b11111),

        // Symbols & Operators
        '+' to intArrayOf(0b00000, 0b00100, 0b00100, 0b11111, 0b00100, 0b00100, 0b00000),
        '-' to intArrayOf(0b00000, 0b00000, 0b00000, 0b11111, 0b00000, 0b00000, 0b00000),
        '.' to intArrayOf(0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b01100, 0b01100),
        ':' to intArrayOf(0b00000, 0b01100, 0b01100, 0b00000, 0b01100, 0b01100, 0b00000),
        '%' to intArrayOf(0b11001, 0b11010, 0b00100, 0b01000, 0b10011, 0b01011, 0b00000),
        '/' to intArrayOf(0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b00000, 0b00000),
        '[' to intArrayOf(0b01110, 0b01000, 0b01000, 0b01000, 0b01000, 0b01000, 0b01110),
        ']' to intArrayOf(0b01110, 0b00010, 0b00010, 0b00010, 0b00010, 0b00010, 0b01110),
        ' ' to intArrayOf(0, 0, 0, 0, 0, 0, 0),
    )

    fun get(char: Char): IntArray {
        val uppercase = char.uppercaseChar()
        return GLYPHS[uppercase] ?: GLYPH_EMPTY
    }
}

/**
 * Procedural Dot-Matrix text renderer.
 * Batched Skia GPU calls via DrawScope.drawPoints, 0 allocations per render frame.
 */
@Composable
fun DotMatrixText(
    text: String,
    modifier: Modifier = Modifier,
    dotSize: Dp = 2.dp,
    dotSpacing: Dp = 1.dp,
    charSpacing: Dp = 2.dp,
    activeColor: Color = AndisTheme.colors.textPrimary,
    inactiveColor: Color = AndisTheme.colors.ghostLedDark,
) {
    val density = LocalDensity.current
    val dotSizePx = with(density) { dotSize.toPx() }
    val dotSpacingPx = with(density) { dotSpacing.toPx() }
    val charSpacingPx = with(density) { charSpacing.toPx() }

    val charAdvancePx = (DotMatrixGlyphs.MATRIX_WIDTH * dotSizePx) +
            ((DotMatrixGlyphs.MATRIX_WIDTH - 1) * dotSpacingPx)
    val totalWidthPx = (text.length * charAdvancePx) +
            ((text.length - 1).coerceAtLeast(0) * charSpacingPx)
    val totalHeightPx = (DotMatrixGlyphs.MATRIX_HEIGHT * dotSizePx) +
            ((DotMatrixGlyphs.MATRIX_HEIGHT - 1) * dotSpacingPx)

    val widthDp = with(density) { totalWidthPx.toDp() }
    val heightDp = with(density) { totalHeightPx.toDp() }

    Canvas(
        modifier = modifier
            .size(widthDp, heightDp)
            .drawWithCache {
                val stepX = dotSizePx + dotSpacingPx
                val stepY = dotSizePx + dotSpacingPx
                val radius = dotSizePx / 2f

                val activePoints = mutableListOf<Offset>()
                val inactivePoints = mutableListOf<Offset>()

                var charOffsetX = 0f

                for (char in text) {
                    val glyph = DotMatrixGlyphs.get(char)

                    for (row in 0 until DotMatrixGlyphs.MATRIX_HEIGHT) {
                        val rowBits = glyph[row]
                        val posY = row * stepY + radius

                        for (col in 0 until DotMatrixGlyphs.MATRIX_WIDTH) {
                            val bitMask = 1 shl (DotMatrixGlyphs.MATRIX_WIDTH - 1 - col)
                            val isActive = (rowBits and bitMask) != 0
                            val posX = charOffsetX + (col * stepX) + radius
                            val point = Offset(posX, posY)

                            if (isActive) {
                                activePoints.add(point)
                            } else if (inactiveColor != Color.Transparent) {
                                inactivePoints.add(point)
                            }
                        }
                    }
                    charOffsetX += charAdvancePx + charSpacingPx
                }

                onDrawBehind {
                    if (inactivePoints.isNotEmpty()) {
                        drawPoints(
                            points = inactivePoints,
                            pointMode = PointMode.Points,
                            color = inactiveColor,
                            strokeWidth = dotSizePx,
                            cap = StrokeCap.Round
                        )
                    }

                    if (activePoints.isNotEmpty()) {
                        drawPoints(
                            points = activePoints,
                            pointMode = PointMode.Points,
                            color = activeColor,
                            strokeWidth = dotSizePx,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
    ) {}
}

/**
 * Brutalist Financial Status Badge (Nothing OS Aesthetic).
 */
@Composable
fun DotMatrixBadge(
    text: String,
    modifier: Modifier = Modifier,
    activeColor: Color = AndisTheme.colors.accentLime,
    surfaceColor: Color = AndisTheme.colors.surfaceSecondary,
    borderColor: Color = AndisTheme.colors.border,
    dotSize: Dp = 1.5.dp,
    showBeacon: Boolean = false,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badgeBeacon")
    val beaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconAlpha"
    )

    Box(
        modifier = modifier
            .background(surfaceColor, RoundedCornerShape(2.dp))
            .border(1.dp, borderColor, RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBeacon) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(activeColor.copy(alpha = beaconAlpha), CircleShape)
                )
                Spacer(Modifier.width(4.dp))
            }
            DotMatrixText(
                text = text,
                dotSize = dotSize,
                dotSpacing = 0.8.dp,
                charSpacing = 1.5.dp,
                activeColor = activeColor,
                inactiveColor = Color.Transparent
            )
        }
    }
}
