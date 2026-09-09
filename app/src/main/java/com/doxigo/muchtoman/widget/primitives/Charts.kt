package com.doxigo.muchtoman.widget.primitives

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.doxigo.muchtoman.AndisTheme

/**
 * Minimalist High-Contrast Financial Sparkline.
 * Smooth or stepped technical line graph with gradient fade.
 */
@Composable
fun Sparkline(
    data: List<Double>,
    modifier: Modifier = Modifier,
    lineColor: Color = AndisTheme.colors.accentLime,
    lineWidth: Dp = 1.8.dp,
    showGradient: Boolean = true,
) {
    if (data.size < 2) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                val width = size.width
                val height = size.height

                val min = data.minOrNull() ?: 0.0
                val max = data.maxOrNull() ?: 1.0
                val range = (max - min).coerceAtLeast(0.0001)

                val points = data.mapIndexed { index, value ->
                    val x = (index.toFloat() / (data.size - 1)) * width
                    val y = height - (((value - min) / range).toFloat() * (height * 0.82f) + (height * 0.09f))
                    Offset(x, y)
                }

                val strokePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        val p0 = points[i - 1]
                        val p1 = points[i]
                        val cx = (p0.x + p1.x) / 2f
                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }

                val fillBrush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )

                onDrawBehind {
                    if (showGradient) {
                        drawPath(fillPath, brush = fillBrush)
                    }
                    drawPath(
                        strokePath,
                        color = lineColor,
                        style = Stroke(
                            width = lineWidth.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }
            }
    ) {}
}

/**
 * Quantized 2D Matrix Dot Graph.
 * Illuminates discrete LED matrix points with vertical trailing VFD equalization.
 */
@Composable
fun DotGraphCanvas(
    data: List<Double>,
    modifier: Modifier = Modifier,
    columns: Int = 24,
    rows: Int = 10,
    dotRadius: Dp = 1.5.dp,
    activeColor: Color = AndisTheme.colors.accentLime,
    ghostColor: Color = AndisTheme.colors.ghostLedDark,
) {
    if (data.isEmpty()) return

    val density = LocalDensity.current
    val dotRadiusPx = with(density) { dotRadius.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                val stepX = size.width / (columns - 1).coerceAtLeast(1)
                val stepY = size.height / (rows - 1).coerceAtLeast(1)

                // Resample incoming data to exactly match column count
                val sampledData = FloatArray(columns) { col ->
                    val dataIdx = (col.toFloat() / (columns - 1) * (data.size - 1)).toInt().coerceIn(data.indices)
                    data[dataIdx].toFloat()
                }

                val minVal = sampledData.minOrNull() ?: 0f
                val maxVal = sampledData.maxOrNull() ?: 1f
                val range = (maxVal - minVal).coerceAtLeast(0.0001f)

                val ghostPoints = mutableListOf<Offset>()
                val activePoints = mutableListOf<Offset>()

                for (col in 0 until columns) {
                    val normalized = ((sampledData[col] - minVal) / range).coerceIn(0f, 1f)
                    val activeRow = ((1f - normalized) * (rows - 1)).toInt()

                    val x = col * stepX

                    for (row in 0 until rows) {
                        val y = row * stepY
                        val point = Offset(x, y)

                        if (row == activeRow) {
                            activePoints.add(point)
                        } else {
                            ghostPoints.add(point)
                        }
                    }
                }

                onDrawBehind {
                    // Draw inactive grid
                    if (ghostPoints.isNotEmpty()) {
                        drawPoints(
                            points = ghostPoints,
                            pointMode = PointMode.Points,
                            color = ghostColor,
                            strokeWidth = dotRadiusPx * 2f,
                            cap = StrokeCap.Round
                        )
                    }

                    // Draw active data trajectory
                    if (activePoints.isNotEmpty()) {
                        drawPoints(
                            points = activePoints,
                            pointMode = PointMode.Points,
                            color = activeColor,
                            strokeWidth = dotRadiusPx * 2f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
    ) {}
}

/**
 * 20-Dot Segmented Progress Bar.
 * Respects RTL reading direction (fills Right-to-Left in Persian locale).
 */
@Composable
fun DotProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    totalDots: Int = 20,
    activeColor: Color = AndisTheme.colors.accentLime,
    inactiveColor: Color = AndisTheme.colors.surfaceSecondary,
    dotRadius: Dp = 2.dp,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val clampedProgress = progress.coerceIn(0f, 1f)
    val activeCount = Math.round(clampedProgress * totalDots)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(dotRadius * 3),
        onDraw = {
            val step = size.width / (totalDots - 1).coerceAtLeast(1)
            val centerY = size.height / 2f
            val radiusPx = dotRadius.toPx()

            for (i in 0 until totalDots) {
                val isActive = if (isRtl) {
                    i >= (totalDots - activeCount)
                } else {
                    i < activeCount
                }

                val x = i * step
                drawCircle(
                    color = if (isActive) activeColor else inactiveColor,
                    radius = radiusPx,
                    center = Offset(x, centerY)
                )
            }
        }
    )
}

/**
 * Discrete Segmented Financial Progress Gauge.
 * Multi-bar meter for budget limits, runways, and goals.
 */
@Composable
fun SegmentedProgress(
    segments: Int,
    filledSegments: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = AndisTheme.colors.accentLime,
    inactiveColor: Color = AndisTheme.colors.surfaceSecondary,
    segmentHeight: Dp = 4.dp,
    gap: Dp = 2.dp,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(segmentHeight)
    ) {
        val gapPx = gap.toPx()
        val totalGaps = (segments - 1) * gapPx
        val segWidth = (size.width - totalGaps) / segments.coerceAtLeast(1)

        for (i in 0 until segments) {
            val isActive = if (isRtl) {
                i >= (segments - filledSegments)
            } else {
                i < filledSegments
            }

            val x = i * (segWidth + gapPx)
            drawRect(
                color = if (isActive) activeColor else inactiveColor,
                topLeft = Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(segWidth, size.height)
            )
        }
    }
}
