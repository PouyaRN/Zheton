package com.doxigo.muchtoman

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.round

/**
 * Shared visual components for wealth-trend and asset-composition reporting.
 *
 * Hosted by [ReportsTab] in [ReportsUi.kt].
 */

/**
 * The bar's own palette, not the icon discs' container tints: those are meant to sit quietly
 * behind an emoji, and in the dark theme they all but vanish against the background — an 89%
 * crypto segment read as an empty track. These are the app's green-to-gold family, fixed so
 * they hold on both themes; gold stays reserved for the gold kinds.
 */
private fun barTint(kind: Kind): Color = when (kind) {
    Kind.CASH -> Color(0xFF2E7D6B)
    Kind.FIAT -> Color(0xFF6FAF9F)
    Kind.CRYPTO -> Color(0xFF93B7B0)
    Kind.GOLD -> Color(0xFFF7C948)
    Kind.SILVER -> Color(0xFFAEB6BD)
    Kind.COIN -> Color(0xFFC99B2C)
    Kind.STOCK -> Color(0xFF4E8A7B)
}

/**
 * What the total is made of, right now. Groups are the home list's own sections, so this is
 * the same money in the same words — just side by side instead of stacked.
 */
@Composable
internal fun CompositionBar(composition: List<Pair<Kind, Double>>) {
    val total = composition.sumOf { it.second }
    Column {
        Text(
            "ترکیب دارایی",
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Space.m))
        Row(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(Radius.pill)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            composition.forEach { (kind, value) ->
                Box(
                    Modifier
                        .weight(value.toFloat())
                        .fillMaxHeight()
                        .background(barTint(kind)),
                )
            }
        }
        Spacer(Modifier.height(Space.m))
        composition.forEach { (kind, value) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = Space.xs),
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(barTint(kind)),
                )
                Spacer(Modifier.width(Space.s))
                Text(kind.fa, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(
                    // Parentheses, not a middot: beside Persian digits «·» reads as a zero.
                    "${faCompact(value, 3, pad = true)} تومان (${faNumber(round(value / total * 100))}٪)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The verdict's colour, shared by the figure, its pill and the chart line, so the whole screen
 * agrees at a glance about which way the money went. Words carry it; colour confirms.
 */
@Composable
internal fun changeTone(change: Change): Color = when {
    abs(change.delta) < 1 -> MaterialTheme.colorScheme.onSurfaceVariant
    // tertiary is the gain role: borrowing primary made "بیشتر" green in light mode but
    // gold — the colour of every button — in dark.
    change.delta > 0 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

/**
 * Bigger or smaller, by how much, since when — stacked the way the hero total is, because it
 * answers the same kind of question and has earned the same kind of answer.
 */
@Composable
internal fun ChangeFigure(change: Change, windowLabel: String, now: Long) {
    val gained = change.delta > 0
    val flat = abs(change.delta) < 1
    val tone = changeTone(change)
    val since = if (windowLabel == "همهٔ" || windowLabel == "همه") {
        "از ${faNumber((now - change.sinceDay).toDouble())} روز پیش"
    } else {
        "نسبت به $windowLabel پیش"
    }

    Column {
        Text(since, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.xs))
        // Auto-shrinks like the hero figure: a big delta must never wrap.
        BasicText(
            text = if (flat) "بدون تغییر" else "${faCompact(abs(change.delta), 3, pad = true)} تومان",
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 22.sp, maxFontSize = 44.sp),
            style = figureStyle(tone, FontWeight.Black),
        )
        if (!flat) {
            Spacer(Modifier.height(Space.m))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(tone.copy(alpha = 0.14f))
                        .padding(horizontal = Space.m, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TrendCaret(up = gained, tint = tone, box = 10.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        change.percent?.let { "${faDecimal(abs(it), 1)}٪" }
                            ?: if (gained) "بیشتر" else "کمتر",
                        fontSize = 14.sp,
                        fontFamily = ModamFigures,
                        fontWeight = FontWeight.Bold,
                        color = tone,
                    )
                }
                Spacer(Modifier.width(Space.s))
                Text(
                    if (gained) "بیشتر شده" else "کمتر شده",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${faNumber(abs(change.delta))} تومان",
                fontSize = 12.sp,
                fontFamily = ModamFigures,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.s),
            )
        }
    }
}

@Composable
internal fun ChartCaption(label: String, value: Double) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${faCompact(value)} تومان",
            fontSize = 14.sp,
            fontFamily = ModamFigures,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
internal fun EmptyReport() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) { Text("📈", fontSize = 40.sp) }
        Spacer(Modifier.height(Space.xl))
        Text("هنوز نموداری نیست", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(Space.s))
        Text(
            "از امروز هر روز یک نقطه ثبت می‌شه و نمودار کم‌کم کامل می‌شه.",
            fontSize = 15.sp,
            lineHeight = 25.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A plain area line — the shape of the money over time, nothing else. Time flows left to
 * right (charts keep that direction even in RTL interfaces; every Iranian bank app agrees).
 * It takes the verdict's colour, so the line and the figure above it can never disagree.
 */
@Composable
internal fun HistoryChart(
    points: List<Pair<Long, Double>>,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val minV = points.minOf { it.second }
        val maxV = points.maxOf { it.second }
        val span = (maxV - minV).takeIf { it > 0.0 } ?: 1.0 // a flat line draws mid-height
        val d0 = points.first().first
        val dSpan = (points.last().first - d0).coerceAtLeast(1L).toFloat()
        val padY = size.height * 0.12f

        fun px(day: Long) = (day - d0) / dSpan * size.width
        // A flat history really does draw mid-height: without the guard every point of an
        // unchanged total sat at the bottom edge, reading exactly like a crash to zero.
        fun py(v: Double) =
            if (maxV == minV) size.height / 2f
            else (size.height - padY) - (((v - minV) / span).toFloat() * (size.height - 2 * padY))

        val path = Path()
        points.forEachIndexed { i, (d, v) ->
            if (i == 0) path.moveTo(px(d), py(v)) else path.lineTo(px(d), py(v))
        }
        val area = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(listOf(tone.copy(alpha = 0.28f), tone.copy(alpha = 0f))),
        )
        drawPath(
            path,
            tone,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Today's point, ringed in the card's own colour so it reads as a marker sitting on
        // the line rather than as the line gone briefly fat.
        val end = Offset(px(points.last().first), py(points.last().second))
        drawCircle(tone.copy(alpha = 0.22f), radius = 11.dp.toPx(), center = end)
        drawCircle(surface, radius = 6.dp.toPx(), center = end)
        drawCircle(tone, radius = 4.dp.toPx(), center = end)
    }
}
