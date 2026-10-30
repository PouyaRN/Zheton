package com.doxigo.muchtoman

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.doxigo.muchtoman.ui.components.CanvasCard
import com.doxigo.muchtoman.ui.components.CanvasGlyph
import com.doxigo.muchtoman.ui.components.OutlineIconButton
import com.doxigo.muchtoman.ui.haptics.LocalFintechHaptics
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * The report tab (گزارش) as drawn in docs/design/canvas/project/Report.dc.html: a month switcher
 * and share button, the month in one sentence, this month's running spend against last month's,
 * and where the money went. Every figure is [monthOverMonth]'s; this file only lays it out.
 *
 * Everything the older report could do (budget, burn-down, wealth trends, …) stays one tap away
 * behind «گزارش کامل» at the end of the page, which opens [ReportTabScreen].
 */

// ─────────────────────────── pure pieces (unit-tested) ───────────────────────────

/** One part of the category split bar: a category, or every smaller one merged (id null). */
internal data class SplitPart(
    val categoryId: String?,
    val nameFa: String,
    val spentRial: Long,
    val share: Double,
    val change: Double?,
)

/** Name of the merged tail of the split bar. */
internal const val REST_NAME = "بقیه"

/**
 * The split bar's parts: every category when there are at most [maxParts], otherwise the
 * largest `maxParts − 1` and the rest merged into one last part. Shares are the real ones.
 */
internal fun splitParts(categories: List<CategoryChange>, maxParts: Int = 5): List<SplitPart> {
    val spending = categories.filter { it.spentRial > 0 }.sortedByDescending { it.spentRial }
    fun CategoryChange.part() = SplitPart(categoryId, nameFa, spentRial, share, change)
    if (spending.size <= maxParts) return spending.map { it.part() }
    val head = spending.take(maxParts - 1)
    val tail = spending.drop(maxParts - 1)
    val tailNow = tail.sumOf { it.spentRial }
    val tailBefore = tail.sumOf { it.previousRial }
    // The merged part's change only means something when every merged category had spending
    // before; one new category would make it a change from a partial base.
    val tailChange = if (tail.all { it.previousRial > 0 } && tailBefore > 0) {
        (tailNow - tailBefore).toDouble() / tailBefore
    } else null
    return head.map { it.part() } + SplitPart(null, REST_NAME, tailNow, tail.sumOf { it.share }, tailChange)
}

/** «+۱۴٪ از شهریور» / «−۳۱٪ از شهریور», or null when there is nothing to say. */
internal fun changeLabel(change: Double?, previousMonthFa: String): String? {
    if (change == null) return null
    val pct = (abs(change) * 100).roundToLong()
    if (pct == 0L) return null
    val sign = if (change > 0) "+" else "−"
    return "$sign${faNumber(pct)}٪ از $previousMonthFa"
}

private val PRIVATE_MONEY = Regex(
    "[۰-۹][۰-۹٫٬]*(?![۰-۹٫٬]*٪)(?:\\s(?:همت|میلیارد|میلیون|هزار))?",
)

/** The sentence with its money figures hidden in privacy mode; percents stay. */
internal fun maskMoneyInSentence(text: String, privacy: Boolean): String =
    if (!privacy) text else PRIVATE_MONEY.replace(text, "••••")

/** Western digits as Persian, without grouping: 1405 -> «۱۴۰۵». */
internal fun faPlainDigits(n: Int): String = n.toString().map { c ->
    if (c in '0'..'9') '۰' + (c - '0') else c
}.joinToString("")

internal fun ReportMonth.ordinal(): Int = year * 12 + (month - 1)

/** «مهر ۱۴۰۵ در یک نگاه» */
internal fun reportCaption(month: ReportMonth): String = "${month.nameFa} ${faPlainDigits(month.year)} در یک نگاه"

/** «کل مهر ۱۹٫۶ میلیون، شهریور ۲۲٫۳ میلیون» */
internal fun totalsLine(m: MonthOverMonth): String =
    "کل ${m.month.nameFa} ${faCompact(tomanOf(m.spentRial))}، ${m.previousMonth.nameFa} ${faCompact(tomanOf(m.previousSpentRial))}"

/** The report as plain text for the share sheet. */
internal fun reportShareText(m: MonthOverMonth): String = buildString {
    appendLine(reportCaption(m.month))
    appendLine(m.summaryText.trim())
    appendLine()
    appendLine(totalsLine(m))
    val parts = splitParts(m.categories)
    if (parts.isNotEmpty()) {
        appendLine()
        parts.forEach { p ->
            val change = changeLabel(p.change, m.previousMonth.nameFa)?.let { " ($it)" } ?: ""
            appendLine("• ${p.nameFa}: ${faCompact(tomanOf(p.spentRial))}$change")
        }
    }
    append("— ژتون")
}

// ─────────────────────────── the screen ───────────────────────────

/** The muted grey of the canvas's receding words and last month's line (#6E6E73 at night). */
@Composable
private fun dimInk(): Color {
    val t = CanvasTheme.tokens
    return if (t == CanvasDark.tokens) Color(0xFF6E6E73) else lerp(t.muted, t.ground, 0.25f)
}

@Composable
private fun splitColors(): List<Color> {
    val t = CanvasTheme.tokens
    val rest = if (t == CanvasDark.tokens) Color(0xFF3A3A3F) else lerp(t.muted, t.ground, 0.55f)
    return listOf(t.ink, t.accent, Color(0xFFB8752A), Color(0xFF7FB2FF), rest)
}

/**
 * The report tab. [onSelectCategory] opens the ledger filtered to the tapped category (null for
 * the merged rest, which opens it unfiltered).
 */
@Composable
fun CanvasReportScreen(
    state: UiState,
    bottomInset: Dp,
    privacyMode: Boolean,
    onSelectCategory: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFull by rememberSaveable { mutableStateOf(false) }
    if (showFull) {
        BackHandler { showFull = false }
        Box(modifier.fillMaxSize()) {
            ReportTabScreen(
                state = state,
                bottomInset = bottomInset,
                privacyMode = privacyMode,
                onSelectCategory = onSelectCategory,
            )
            OutlineIconButton(
                label = "بازگشت به گزارش ماه",
                onClick = { showFull = false },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 14.dp, end = CanvasTheme.gutter),
            ) { tint ->
                // Points back toward the start edge: right in RTL.
                CanvasGlyph(listOf("M9 6l6 6-6 6"), tint, size = 20.dp, stroke = 2f)
            }
        }
        return
    }

    val t = CanvasTheme.tokens
    val haptics = LocalFintechHaptics.current
    val context = LocalContext.current
    val spec = CycleSpec(state.cycleMode, state.cycleAnchorDay)

    var today by remember { mutableLongStateOf(tehranDay(System.currentTimeMillis())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            today = tehranDay(System.currentTimeMillis())
        }
    }
    val current = remember(today, spec) { reportMonthOf(today, spec) }
    var picked by rememberSaveable { mutableIntStateOf(current.ordinal()) }
    val month = ReportMonth(picked / 12, picked % 12 + 1)
    val report = remember(state.ledger, month, spec, today) {
        monthOverMonth(state.ledger.allEntries, month, spec, today)
    }

    Box(modifier.fillMaxSize().background(t.ground)) {
        ReportGlow()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(start = CanvasTheme.gutter, end = CanvasTheme.gutter, top = 14.dp, bottom = bottomInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Month pills and share.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val prev = month.previous()
                    val next = month.next()
                    MonthPill(prev.nameFa, selected = false, enabled = true) {
                        haptics.click(); picked = prev.ordinal()
                    }
                    MonthPill(month.nameFa, selected = true, enabled = true) {}
                    MonthPill(next.nameFa, selected = false, enabled = next.ordinal() <= current.ordinal()) {
                        haptics.click(); picked = next.ordinal()
                    }
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(1.dp, t.outlineBorder, CircleShape)
                        .clickable(role = Role.Button) {
                            haptics.click()
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, reportShareText(report))
                            }
                            context.startActivity(Intent.createChooser(send, "اشتراک‌گذاری گزارش"))
                        }
                        .semantics { contentDescription = "اشتراک‌گذاری گزارش" },
                    contentAlignment = Alignment.Center,
                ) {
                    CanvasGlyph(
                        listOf("M12 15V4M8 8l4-4 4 4", "M5 13v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5"),
                        t.ink,
                        size = 18.dp,
                    )
                }
            }

            // The month, read like liner notes.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(reportCaption(month), fontFamily = Modam, fontSize = 11.sp, color = t.muted)
                SummarySentence(report.summary, privacyMode)
            }

            SpendCard(report, privacyMode)

            CategorySplit(report, privacyMode) { id -> haptics.click(); onSelectCategory(id) }

            Text(
                "گزارش کامل",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { haptics.click(); showFull = true }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                fontFamily = Modam,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = t.accentText,
            )
        }
    }
}

/** Report.dc.html's light: a 640×520 saffron ellipse, 200dp past the left edge, 120dp up. */
@Composable
private fun ReportGlow() {
    val t = CanvasTheme.tokens
    Canvas(Modifier.fillMaxSize()) {
        val rx = 320.dp.toPx()
        val ry = 260.dp.toPx()
        val centre = Offset(-200.dp.toPx() + rx, size.height - 120.dp.toPx() - ry)
        val brush = Brush.radialGradient(
            0f to t.glow.copy(alpha = if (t.isDark) 0.14f else 0.10f),
            1f to t.glow.copy(alpha = 0f),
            center = Offset.Zero,
            radius = rx,
        )
        // A circle of radius rx, squashed vertically into the ellipse.
        withTransform({
            translate(centre.x, centre.y)
            scale(1f, ry / rx, pivot = Offset.Zero)
        }) { drawCircle(brush, radius = rx, center = Offset.Zero) }
    }
}

@Composable
private fun MonthPill(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val t = CanvasTheme.tokens
    Box(
        Modifier
            .height(34.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(t.inverse)
                else Modifier.border(1.dp, t.outlineBorder, CircleShape)
            )
            .then(if (enabled && !selected) Modifier.clickable(role = Role.Tab, onClick = onClick) else Modifier)
            .semantics { this.selected = selected }
            .padding(horizontal = if (selected) 14.dp else 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontFamily = Modam,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
            color = if (selected) t.onInverse else t.muted,
            maxLines = 1,
        )
    }
}

@Composable
private fun SummarySentence(parts: List<SummaryPart>, privacy: Boolean) {
    val t = CanvasTheme.tokens
    val dim = dimInk()
    val text = buildAnnotatedString {
        parts.forEach { p ->
            val run = maskMoneyInSentence(p.text, privacy)
            when (p) {
                is SummaryPart.Plain -> append(run)
                is SummaryPart.Highlight -> withStyle(
                    SpanStyle(color = if (p.tone == SummaryTone.WARNING) t.accentText else t.ink),
                ) { append(run) }
            }
        }
    }
    Text(
        text,
        fontFamily = Modam,
        fontSize = 29.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.ExtraBold,
        color = dim,
    )
}

@Composable
private fun SpendCard(m: MonthOverMonth, privacy: Boolean) {
    val t = CanvasTheme.tokens
    val dim = dimInk()
    CanvasCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "خرج از اول ماه",
                    modifier = Modifier.weight(1f),
                    fontFamily = Modam,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.ink,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendItem(m.month.nameFa) {
                        Box(Modifier.width(14.dp).height(3.dp).clip(CircleShape).background(t.accent))
                    }
                    LegendItem(m.previousMonth.nameFa) {
                        Canvas(Modifier.width(14.dp).height(2.dp)) {
                            drawLine(
                                dim,
                                Offset(0f, size.height / 2),
                                Offset(size.width, size.height / 2),
                                strokeWidth = size.height,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx())),
                            )
                        }
                    }
                }
            }
            CumulativeChart(m, Modifier.fillMaxWidth().height(132.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(
                    if (privacy) "کل ${m.month.nameFa} ••••، ${m.previousMonth.nameFa} ••••" else totalsLine(m),
                    modifier = Modifier.weight(1f),
                    fontFamily = Modam,
                    fontSize = 11.sp,
                    color = t.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${faNumber(1)} ${m.month.nameFa}", fontFamily = Modam, fontSize = 11.sp, color = t.muted)
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        swatch()
        Text(label, fontFamily = Modam, fontSize = 11.sp, color = CanvasTheme.tokens.inkSecondary)
    }
}

/**
 * Two running totals on one scale, drawn left to right from day 1 as on the artboard (its SVG is
 * LTR): this month a saffron line over a fading fill, last month a grey dashed line, each with a
 * dot at its last day. Days are spread over each month's own length, so a month in progress
 * stops part-way across.
 */
@Composable
private fun CumulativeChart(m: MonthOverMonth, modifier: Modifier) {
    val t = CanvasTheme.tokens
    val dim = dimInk()
    val gridFaint = t.ink.copy(alpha = 0.06f)
    val gridBase = t.ink.copy(alpha = 0.12f)
    val nowDays = (m.window.endDay - m.window.startDay).toInt().coerceAtLeast(1)
    val prevDays = m.previousCumulativeSpend.size.coerceAtLeast(1)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(modifier.semantics { contentDescription = totalsLine(m) }) {
            val w = size.width
            val k = size.height / 132f
            val base = 124f * k
            val h = 112f * k
            drawLine(gridFaint, Offset(0f, 44f * k), Offset(w, 44f * k), strokeWidth = 1.dp.toPx())
            drawLine(gridFaint, Offset(0f, 84f * k), Offset(w, 84f * k), strokeWidth = 1.dp.toPx())
            drawLine(gridBase, Offset(0f, base), Offset(w, base), strokeWidth = 1.dp.toPx())

            val top = maxOf(m.cumulativeSpend.lastOrNull() ?: 0L, m.previousCumulativeSpend.lastOrNull() ?: 0L)
            if (top <= 0L) return@Canvas
            val maxV = top * 1.075
            fun points(series: List<Long>, days: Int): List<Offset> =
                listOf(Offset(0f, base)) + series.mapIndexed { i, v ->
                    Offset((i + 1).toFloat() / days * w, (base - v / maxV * h).toFloat())
                }

            val prev = if (m.previousCumulativeSpend.isEmpty()) emptyList() else points(m.previousCumulativeSpend, prevDays)
            val now = if (m.cumulativeSpend.isEmpty()) emptyList() else points(m.cumulativeSpend, nowDays)

            if (prev.size > 1) {
                drawPath(
                    smoothPath(prev),
                    dim,
                    style = Stroke(
                        width = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                    ),
                )
            }
            if (now.size > 1) {
                val line = smoothPath(now)
                val fill = smoothPath(now).apply {
                    lineTo(now.last().x, base)
                    lineTo(0f, base)
                    close()
                }
                val minY = now.minOf { it.y }
                drawPath(
                    fill,
                    Brush.verticalGradient(
                        0f to t.accent.copy(alpha = 0.32f),
                        1f to t.accent.copy(alpha = 0f),
                        startY = minY,
                        endY = base,
                    ),
                )
                drawPath(line, t.accent, style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (prev.size > 1) drawCircle(dim, radius = 3.5.dp.toPx(), center = prev.last())
            if (now.size > 1) {
                drawCircle(t.card, radius = 5.5.dp.toPx(), center = now.last())
                drawCircle(t.accent, radius = 3.5.dp.toPx(), center = now.last())
            }
        }
    }
}

/**
 * A smooth line through the points that never overshoots them. The artboard smooths with
 * Catmull-Rom, which is fine on its gentle sample curve but on real spending (flat days, then
 * one big purchase) swings below zero and above the total. Monotone cubic (Fritsch–Carlson)
 * keeps the same soft look and stays between neighbouring values, as a running total must.
 */
private fun smoothPath(p: List<Offset>): Path = Path().apply {
    moveTo(p[0].x, p[0].y)
    val n = p.size
    if (n < 2) return@apply
    val d = FloatArray(n - 1) { i -> (p[i + 1].y - p[i].y) / (p[i + 1].x - p[i].x).coerceAtLeast(1e-3f) }
    val m = FloatArray(n) { i ->
        when (i) {
            0 -> d[0]
            n - 1 -> d[n - 2]
            else -> if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
        }
    }
    for (i in 0 until n - 1) {
        if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
        val a = m[i] / d[i]
        val b = m[i + 1] / d[i]
        val h = a * a + b * b
        if (h > 9f) {
            val t = 3f / kotlin.math.sqrt(h)
            m[i] = t * a * d[i]
            m[i + 1] = t * b * d[i]
        }
    }
    for (i in 0 until n - 1) {
        val dx = (p[i + 1].x - p[i].x) / 3f
        cubicTo(p[i].x + dx, p[i].y + m[i] * dx, p[i + 1].x - dx, p[i + 1].y - m[i + 1] * dx, p[i + 1].x, p[i + 1].y)
    }
}

@Composable
private fun CategorySplit(m: MonthOverMonth, privacy: Boolean, onSelect: (String?) -> Unit) {
    val parts = remember(m) { splitParts(m.categories) }
    if (parts.isEmpty()) return
    val t = CanvasTheme.tokens
    val colors = splitColors()
    fun colorOf(i: Int, p: SplitPart) = if (p.categoryId == null) colors.last() else colors[i.coerceAtMost(colors.size - 1)]
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(12.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            parts.forEachIndexed { i, p ->
                Box(
                    Modifier
                        .weight(p.share.toFloat().coerceAtLeast(0.004f))
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colorOf(i, p)),
                )
            }
        }
        parts.forEachIndexed { i, p ->
            val label = changeLabel(p.change, m.previousMonth.nameFa)
            val labelColor = when {
                label == null -> t.muted
                (p.change ?: 0.0) > 0 -> t.accentText
                else -> t.gain
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onSelect(p.categoryId) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colorOf(i, p)))
                Text(
                    p.nameFa,
                    modifier = Modifier.weight(1f),
                    fontFamily = Modam,
                    fontSize = 14.sp,
                    color = t.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (label != null) {
                    Text(label, fontFamily = Modam, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = labelColor)
                }
                Text(
                    maskFinancialAmount(faCompact(tomanOf(p.spentRial)), privacy),
                    modifier = Modifier.width(84.dp),
                    fontFamily = ModamFigures,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.ink,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
    }
}
