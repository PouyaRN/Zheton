package com.doxigo.muchtoman

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.features.pricealerts.PriceAlertSheet
import com.doxigo.muchtoman.features.pricealerts.PriceAlerts
import com.doxigo.muchtoman.ui.components.CanvasGlyph
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// ════════════════════════════════════════════════════════════════════════════════
// The asset screen (ticket #13), laid out as docs/design/canvas/project/Asset.dc.html.
//
// Every figure is real: the price is the effective rate, the chart is the stored daily closes
// (HoldingPnl.kt), the cost line and P&L come from the lots she recorded. Where one is missing
// the screen says so — a dash, «تاریخچه از …» — rather than drawing a guess.
// ════════════════════════════════════════════════════════════════════════════════

/**
 * The asset screen for [typeId]. [key] is the holding it opens on; null opens the asset on its
 * own (her first holding of it, if any — a ثبت خرید then starts a new one).
 *
 * [onEdit], when given, opens the holding's edit sheet from a tap on the asset's name: the
 * canvas draws no edit control, and the row that opens this screen used to open that sheet.
 */
@Composable
fun AssetDetailScreen(
    state: UiState,
    vm: AppVm,
    typeId: String,
    key: String?,
    onBack: () -> Unit,
    onEdit: ((key: String) -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    val t = CanvasTheme.tokens
    val context = LocalContext.current
    val type = remember(typeId, state.coins, state.stocks) { resolveType(typeId, state.coins, state.stocks) }
    val holding = if (key != null) state.holdings.firstOrNull { it.key == key }
    else state.holdings.firstOrNull { it.typeId == typeId && !it.archived }
    val freshKey = rememberSaveable(typeId) { newHoldingId() }
    val holdingKey = holding?.key ?: key ?: freshKey
    val name = holding?.nameOr(type.fa) ?: type.fa
    val price = state.effective[typeId]?.takeIf { it.isFinite() && it > 0.0 }
    val position = holding?.let { state.positionOf(it.key) }

    var period by rememberSaveable { mutableStateOf(RatePeriod.MONTH) }
    val today = tehranDay(System.currentTimeMillis())
    val series = remember(state.dailyRates, price, period, typeId, today) { state.rateSeriesOf(typeId, period) }

    val alertsVersion by PriceAlerts.changes.collectAsState()
    val alertArmed = remember(alertsVersion, typeId) { PriceAlerts.forAsset(context, typeId).any { it.armed } }

    var alertSheet by remember { mutableStateOf(false) }
    var lotSheet by remember { mutableStateOf<LotSide?>(null) }
    var openingSheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(t.ground)) {
        AssetGlow()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 14.dp, bottom = 24.dp),
        ) {
            // ── Header: back, mark + name + unit line, alert bell ────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RoundOutline(label = "بازگشت", onClick = onBack) {
                    CanvasGlyph(listOf("M9 6l6 6-6 6"), tint = t.ink, size = 20.dp, stroke = 2f)
                }
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .then(
                            if (onEdit != null && holding != null && typeId != BANK_ID)
                                Modifier.clickable(role = Role.Button) { onEdit(holding.key) }
                            else Modifier
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(t.accentWell),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            assetDetailMark(type),
                            fontFamily = Modam,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = t.accentText,
                            maxLines = 1,
                        )
                    }
                    Column {
                        Text(
                            name,
                            fontFamily = Modam,
                            fontSize = 15.sp,
                            lineHeight = 19.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = t.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            assetUnitLine(type),
                            fontFamily = Modam,
                            fontSize = 11.sp,
                            lineHeight = 14.3.sp,
                            color = t.muted,
                            maxLines = 1,
                        )
                    }
                }
                RoundOutline(label = if (alertArmed) "هشدار قیمت (فعال)" else "هشدار قیمت", onClick = { alertSheet = true }) {
                    BellGlyph(tint = t.ink, filled = alertArmed)
                }
            }

            // ── Big price and the period's change ────────────────────────────────────────
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    price?.let { faNumber(it) } ?: "—",
                    fontFamily = ModamFigures,
                    fontSize = 52.sp,
                    lineHeight = 55.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.ink,
                    maxLines = 1,
                )
                val change = series.changeToman
                val pct = series.changePercent
                val tone = when {
                    change == null || change == 0.0 -> t.muted
                    change > 0 -> t.gain
                    else -> t.loss
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        periodDelta(change, pct),
                        fontFamily = ModamFigures,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = tone,
                        maxLines = 1,
                    )
                    Spacer(Modifier.size(5.dp))
                    Text(
                        periodSpan(series, today),
                        fontFamily = Modam,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = t.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // ── Full-bleed chart with her average buy price across it ────────────────────
            AssetChart(
                series = series,
                today = today,
                averageCost = position?.averageCostToman,
                modifier = Modifier.padding(top = 16.dp),
            )

            // ── Period segmented control ─────────────────────────────────────────────────
            Row(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp)
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(t.card)
                    .border(1.dp, t.cardBorder, CircleShape)
                    .padding(3.dp)
                    .semantics { contentDescription = "بازهٔ زمانی" },
            ) {
                RatePeriod.entries.forEach { p ->
                    val on = p == period
                    Box(
                        Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(if (on) t.inverse else Color.Transparent)
                            .clickable(role = Role.Tab) { period = p }
                            .semantics { selected = on },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            p.labelFa,
                            fontFamily = Modam,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (on) t.onInverse else t.muted,
                            maxLines = 1,
                        )
                    }
                }
            }

            // ── What it means for her ────────────────────────────────────────────────────
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    label = holdingLabel(type),
                    value = position?.valueToman?.let { faCompact(it) } ?: "—",
                    sub = position?.let { "${faHeld(it.amount, type.dec)} ${type.unitFa}" } ?: "هنوز نداری",
                    modifier = Modifier.weight(1f),
                )
                val pnl = position?.pnlToman
                val pnlPct = position?.pnlPercent
                val canPriceOpening = position != null && position.uncoveredAmount > 0.0 && typeId != BANK_ID
                StatCard(
                    label = "سود تا امروز",
                    value = pnl?.let { faSignedCompact(it, it >= 0) } ?: "—",
                    valueColor = when {
                        pnl == null || pnl == 0.0 -> t.ink
                        pnl > 0 -> t.gain
                        else -> t.loss
                    },
                    sub = when {
                        pnlPct != null -> "${ltrFigure((if (pnlPct >= 0) "+" else "−") + faDecimal(abs(pnlPct), if (abs(pnlPct) >= 10) 0 else 1))}٪ نسبت به قیمت خرید"
                        canPriceOpening -> "قیمت خریدت رو بزن"
                        position == null -> "با اولین خرید"
                        else -> "قیمت روز نرسیده"
                    },
                    subColor = if (pnlPct == null && canPriceOpening) t.accentText else t.muted,
                    onSubClick = if (pnlPct == null && canPriceOpening) ({ openingSheet = true }) else null,
                    modifier = Modifier.weight(1f),
                )
            }

            // ── ثبت خرید / ثبت فروش ──────────────────────────────────────────────────────
            if (typeId != BANK_ID) {
                Row(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(CircleShape)
                            .background(t.inverse)
                            .clickable(role = Role.Button) { lotSheet = LotSide.BUY },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("ثبت خرید", fontFamily = Modam, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = t.onInverse)
                    }
                    val canSell = (holding?.amount ?: 0.0) > 0.0
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(CircleShape)
                            .border(1.dp, t.ink.copy(alpha = 0.2f), CircleShape)
                            .clickable(enabled = canSell, role = Role.Button) { lotSheet = LotSide.SELL },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "ثبت فروش",
                            fontFamily = Modam,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canSell) t.ink else t.muted,
                        )
                    }
                }
            }
        }
    }

    if (alertSheet) {
        PriceAlertSheet(assetId = typeId, assetName = type.fa, currentRate = price, onDismiss = { alertSheet = false })
    }
    lotSheet?.let { side ->
        LotSheet(
            side = side,
            type = type,
            held = holding?.amount ?: 0.0,
            currentRate = price,
            onDismiss = { lotSheet = null },
            onSave = { amount, unit ->
                val ok = if (side == LotSide.BUY) vm.recordBuy(holdingKey, typeId, amount, unit)
                else vm.recordSell(holdingKey, amount, unit)
                if (ok) lotSheet = null
                ok
            },
        )
    }
    if (openingSheet && position != null) {
        OpeningCostSheet(
            type = type,
            uncovered = position.uncoveredAmount,
            currentRate = price,
            onDismiss = { openingSheet = false },
            onSave = { unit ->
                val ok = vm.recordOpeningCost(position.key, unit)
                if (ok) openingSheet = false
                ok
            },
        )
    }
}

// ── Pieces ──────────────────────────────────────────────────────────────────────────────

/** The artboard's 600×460 saffron ellipse, 160dp down, hanging 100dp off the right edge. */
@Composable
private fun AssetGlow() {
    val t = CanvasTheme.tokens
    Canvas(Modifier.fillMaxSize()) {
        val rx = 300.dp.toPx()
        val ry = 230.dp.toPx()
        val centre = Offset(size.width + 100.dp.toPx() - rx, 160.dp.toPx() + ry)
        scale(scaleX = 1f, scaleY = ry / rx, pivot = centre) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to t.glow.copy(alpha = if (t.isDark) 0.20f else 0.12f),
                    1f to t.glow.copy(alpha = 0f),
                    center = centre,
                    radius = rx,
                ),
                radius = rx,
                center = centre,
            )
        }
    }
}

/** 44dp round control with a 1px edge and no fill: the back link and the bell. */
@Composable
private fun RoundOutline(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val t = CanvasTheme.tokens
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(1.dp, t.outlineBorder, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val BELL_BODY = "M6 16V11a6 6 0 1 1 12 0v5l1.5 2h-15z"
private val BELL_CLAPPER = "M10 20.5a2 2 0 0 0 4 0"

/** The canvas bell, 18dp, stroked; filled while an alert is armed for this asset. */
@Composable
private fun BellGlyph(tint: Color, filled: Boolean) {
    val body = remember { PathParser().parsePathString(BELL_BODY).toPath() }
    val clapper = remember { PathParser().parsePathString(BELL_CLAPPER).toPath() }
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            val pen = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (filled) drawPath(body, tint, style = Fill)
            drawPath(body, tint, style = pen)
            drawPath(clapper, tint, style = pen)
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    sub: String,
    modifier: Modifier = Modifier,
    valueColor: Color = CanvasTheme.tokens.ink,
    subColor: Color = CanvasTheme.tokens.muted,
    onSubClick: (() -> Unit)? = null,
) {
    val t = CanvasTheme.tokens
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .clip(shape)
            .background(t.card)
            .border(1.dp, t.cardBorder, shape)
            .then(if (onSubClick != null) Modifier.clickable(role = Role.Button, onClick = onSubClick) else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, fontFamily = Modam, fontSize = 11.sp, color = t.muted, maxLines = 1)
        Text(
            value,
            fontFamily = ModamFigures,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            sub,
            fontFamily = Modam,
            fontSize = 11.sp,
            fontWeight = if (onSubClick != null) FontWeight.Bold else FontWeight.Normal,
            color = subColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The 230dp chart: the stored closes as a smooth saffron line over its gradient, her average
 * buy price dashed across it with its label, and a scrub marker with a price/date bubble that
 * follows the finger. Time runs left to right whatever the layout direction, as on the canvas.
 */
@Composable
private fun AssetChart(
    series: RateSeries,
    today: Long,
    averageCost: Double?,
    modifier: Modifier = Modifier,
) {
    val t = CanvasTheme.tokens
    val density = LocalDensity.current
    var widthPx by remember { mutableStateOf(0f) }
    var scrubIndex by remember(series) { mutableStateOf<Int?>(null) }
    val chartHeight = 230.dp
    val geom = remember(series, today, averageCost, widthPx, density) {
        with(density) {
            chartGeometry(series, today, averageCost, widthPx, top = 34.dp.toPx(), bottom = 210.dp.toPx())
        }
    }

    Box(modifier.fillMaxWidth().height(chartHeight)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { widthPx = it.width.toFloat() }
                .semantics { contentDescription = chartDescription(series, averageCost) }
                .pointerInput(geom) {
                    if (geom.xs.isEmpty()) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        scrubIndex = nearestIndex(geom.xs, down.position.x)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            scrubIndex = nearestIndex(geom.xs, change.position.x)
                        }
                        scrubIndex = null
                    }
                },
        ) {
            val top12 = 12.dp.toPx()
            val bottom218 = 218.dp.toPx()
            geom.costY?.let { y ->
                drawLine(
                    color = t.ink.copy(alpha = 0.35f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
                )
            }
            val pts = geom.xs.indices.map { Offset(geom.xs[it], geom.ys[it]) }
            if (pts.size >= 2) {
                val line = smoothPath(pts)
                val fill = smoothPath(pts).apply {
                    lineTo(pts.last().x, size.height)
                    lineTo(pts.first().x, size.height)
                    close()
                }
                drawPath(
                    fill,
                    brush = Brush.verticalGradient(
                        0f to t.accent.copy(alpha = 0.42f),
                        0.7f to t.accent.copy(alpha = 0.06f),
                        1f to t.accent.copy(alpha = 0f),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
                drawPath(line, t.accent, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (pts.isNotEmpty()) {
                val end = pts.last()
                drawCircle(t.ink, radius = 4.dp.toPx(), center = end)
            }
            scrubIndex?.let { i ->
                val p = pts.getOrNull(i) ?: return@let
                drawLine(t.ink.copy(alpha = 0.45f), Offset(p.x, top12), Offset(p.x, bottom218), strokeWidth = 1.dp.toPx())
                drawCircle(t.accent.copy(alpha = 0.2f), radius = 10.dp.toPx(), center = p)
                drawCircle(t.accent, radius = 5.dp.toPx(), center = p)
                drawCircle(t.ground, radius = 5.dp.toPx(), center = p, style = Stroke(2.dp.toPx()))
            }
        }

        if (series.points.isEmpty()) {
            Text(
                "هنوز قیمتی از این دارایی ذخیره نشده؛ از امروز جمع می‌شه.",
                fontFamily = Modam,
                fontSize = 12.sp,
                color = t.muted,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            )
        }

        // Her average buy price, on the right edge just above its line.
        if (averageCost != null && geom.costY != null) {
            val labelTop = with(density) { (geom.costY - 20.dp.toPx()).roundToInt() }
            Text(
                "میانگین قیمت خریدت ${faNumber(averageCost)}",
                fontFamily = Modam,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = t.onInverse,
                maxLines = 1,
                modifier = Modifier
                    .align(AbsoluteAlignment.TopRight)
                    .absoluteOffset { IntOffset(-20.dp.roundToPx(), labelTop) }
                    .clip(CircleShape)
                    .background(t.inverse.copy(alpha = 0.9f))
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            )
        }

        scrubIndex?.let { i ->
            val point = series.points.getOrNull(i) ?: return@let
            val x = geom.xs.getOrNull(i) ?: return@let
            val bubbleW = with(density) { 100.dp.toPx() }
            val left = (x - with(density) { 46.dp.toPx() })
                .coerceIn(with(density) { 8.dp.toPx() }, (widthPx - bubbleW).coerceAtLeast(0f))
            Column(
                Modifier
                    .align(AbsoluteAlignment.TopLeft)
                    .absoluteOffset { IntOffset(left.roundToInt(), 0) }
                    .clip(RoundedCornerShape(12.dp))
                    .background(t.inverse)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    faNumber(point.priceToman),
                    fontFamily = ModamFigures,
                    fontSize = 12.sp,
                    lineHeight = 15.6.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = t.onInverse,
                    maxLines = 1,
                )
                Text(
                    scrubDate(point.day, series.period, today),
                    fontFamily = Modam,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = t.onInverse.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            }
        }
    }
}

/** The canvas's smoothing: Catmull-Rom through every point, as cubic Béziers. */
private fun smoothPath(points: List<Offset>): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 0 until points.size - 1) {
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { p2 }
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
}

// ── Sheets ──────────────────────────────────────────────────────────────────────────────

/** ثبت خرید / ثبت فروش: how much, at what price each. [onSave] returns false to stay open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LotSheet(
    side: LotSide,
    type: AssetType,
    held: Double,
    currentRate: Double?,
    onDismiss: () -> Unit,
    onSave: (amount: Double, unitPriceToman: Double) -> Boolean,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountIn by remember { mutableStateOf("") }
    var priceIn by remember { mutableStateOf(currentRate?.roundToLong()?.toString().orEmpty()) }
    var failed by remember { mutableStateOf(false) }
    val amount = parseAmount(amountIn)?.takeIf { it > 0.0 }
    val unit = parseAmount(priceIn)?.takeIf { it > 0.0 }
    val tooMuch = side == LotSide.SELL && amount != null && amount > held * (1 + 1e-9) + 1e-12
    val buy = side == LotSide.BUY

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${if (buy) "ثبت خرید" else "ثبت فروش"} · ${type.fa}", fontFamily = Modam, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            if (!buy) {
                Text(
                    "الان ${faHeld(held, type.dec)} ${type.unitFa} داری",
                    fontFamily = Modam,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = amountIn,
                onValueChange = { amountIn = it; failed = false },
                label = { Text("چند ${type.unitFa}") },
                singleLine = true,
                isError = (amountIn.isNotBlank() && amount == null) || tooMuch,
                supportingText = if (tooMuch) ({ Text("بیشتر از چیزیه که داری") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = priceIn,
                onValueChange = { priceIn = it; failed = false },
                label = { Text("قیمت هر ${type.unitFa} (تومان)") },
                singleLine = true,
                isError = priceIn.isNotBlank() && unit == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (amount != null && unit != null) {
                Text(
                    "جمعاً ${faNumber(amount * unit)} تومان",
                    fontFamily = Modam,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (failed) {
                Text("ثبت نشد؛ عددها رو یه بار دیگه ببین.", fontFamily = Modam, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { if (amount != null && unit != null) failed = !onSave(amount, unit) },
                enabled = amount != null && unit != null && !tooMuch,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (buy) "ثبت خرید" else "ثبت فروش", fontFamily = Modam, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

/** Prices the units she held before lots existed, so «سود تا امروز» stops being a dash. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpeningCostSheet(
    type: AssetType,
    uncovered: Double,
    currentRate: Double?,
    onDismiss: () -> Unit,
    onSave: (unitPriceToman: Double) -> Boolean,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var priceIn by remember { mutableStateOf("") }
    val unit = parseAmount(priceIn)?.takeIf { it > 0.0 }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("قیمت خریدت · ${type.fa}", fontFamily = Modam, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "${faHeld(uncovered, type.dec)} ${type.unitFa} رو قبلاً داشتی؛ میانگین هر ${type.unitFa} رو چند خریده بودی؟",
                fontFamily = Modam,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = priceIn,
                onValueChange = { priceIn = it },
                label = { Text("قیمت هر ${type.unitFa} (تومان)") },
                placeholder = currentRate?.let { { Text(faNumber(it)) } },
                singleLine = true,
                isError = priceIn.isNotBlank() && unit == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { unit?.let { onSave(it) } },
                enabled = unit != null,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("ثبت قیمت خرید", fontFamily = Modam, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

// ── Pure helpers (tested in AssetDetailTest) ────────────────────────────────────────────

/** Where each point sits on the chart, and the y of her average buy line (null if unknown). */
internal data class ChartGeometry(val xs: List<Float>, val ys: List<Float>, val costY: Float?)

/**
 * Places [series] on a [width]-wide chart whose line spans [top]..[bottom]. x is by day across
 * the whole period, so a history shorter than the period occupies only its own stretch at the
 * right instead of being stretched to look like a full one. The y range takes in [averageCost]
 * so her cost line is always on the chart.
 */
internal fun chartGeometry(
    series: RateSeries,
    today: Long,
    averageCost: Double?,
    width: Float,
    top: Float,
    bottom: Float,
): ChartGeometry {
    val pts = series.points
    if (pts.isEmpty() || width <= 0f) return ChartGeometry(emptyList(), emptyList(), null)
    val span = series.period.days.toFloat()
    val start = today - series.period.days
    val xs = pts.map { p -> ((p.day - start).coerceAtLeast(0L).toFloat() / span).coerceIn(0f, 1f) * width }
    val cost = averageCost?.takeIf { it.isFinite() && it > 0.0 }
    val values = pts.map { it.priceToman } + listOfNotNull(cost)
    var lo = values.min()
    var hi = values.max()
    if (hi - lo < 1e-9) {
        val pad = if (hi > 0) hi * 0.01 else 1.0
        lo -= pad
        hi += pad
    }
    fun y(v: Double): Float = (bottom - ((v - lo) / (hi - lo)) * (bottom - top)).toFloat()
    return ChartGeometry(xs, pts.map { y(it.priceToman) }, cost?.let { y(it) })
}

internal fun nearestIndex(xs: List<Float>, x: Float): Int? =
    xs.indices.minByOrNull { abs(xs[it] - x) }

/** «در این ماه» and the rest: what the change beside the price is measured over. */
internal fun periodWords(period: RatePeriod): String = when (period) {
    RatePeriod.WEEK -> "در این هفته"
    RatePeriod.MONTH -> "در این ماه"
    RatePeriod.MONTH_3 -> "در ۳ ماه گذشته"
    RatePeriod.YEAR -> "در یک سال گذشته"
    RatePeriod.YEAR_5 -> "در ۵ سال گذشته"
}

/** The words after the change: the period's, or «تاریخچه از …» when history starts later. */
internal fun periodSpan(series: RateSeries, today: Long): String {
    val first = series.first ?: return "هنوز تاریخچه‌ای نیست"
    return if (series.complete) periodWords(series.period) else "تاریخچه از ${faDay(first.day, today)}"
}

/** «+۱۰۰٬۰۰۰ (۴٫۱٪)», or a dash with fewer than two points. */
internal fun periodDelta(changeToman: Double?, changePercent: Double?): String {
    if (changeToman == null) return "—"
    val sign = when {
        changeToman > 0 -> "+"
        changeToman < 0 -> "−"
        else -> ""
    }
    val digits = ltrFigure(sign + faNumber(abs(changeToman)))
    val pct = changePercent?.let { p ->
        val a = abs(p)
        " (${faDecimal(a, if (a >= 100) 0 else 1)}٪)"
    }.orEmpty()
    return digits + pct
}

/** The bubble's date: «۳ آبان» within a few months, «اسفند ۱۴۰۴» beyond, «امروز» today. */
internal fun scrubDate(day: Long, period: RatePeriod, today: Long): String {
    if (day == today) return "امروز"
    if (day == today - 1) return "دیروز"
    val j = jalaliOf(day)
    val month = MONTHS[j.month - 1]
    return when (period) {
        RatePeriod.WEEK, RatePeriod.MONTH, RatePeriod.MONTH_3 -> "${faNumber(j.day)} $month"
        RatePeriod.YEAR, RatePeriod.YEAR_5 -> "$month ${faDigitsOnly(j.year)}"
    }
}

private fun faDigitsOnly(n: Int): String = n.toString().map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

/** The round mark beside the name: the figure in it («۱۸» for طلای ۱۸ عیار), else its first letter. */
internal fun assetDetailMark(type: AssetType): String {
    val digits = Regex("[۰-۹]+").find(type.fa)?.value
    if (digits != null && digits.length <= 3) return digits
    return type.fa.trim().firstOrNull()?.toString() ?: "?"
}

/** «هر گرم · بازار آزاد»: what one unit is, and where its price comes from. */
internal fun assetUnitLine(type: AssetType): String {
    val market = when (type.kind) {
        Kind.CRYPTO -> "بازار رمزارز"
        Kind.STOCK -> "بورس تهران"
        Kind.CASH -> "تومان"
        else -> "بازار آزاد"
    }
    return "هر ${type.unitFa} · $market"
}

/** «طلای تو»: the holding card's caption in the canvas's voice. */
internal fun holdingLabel(type: AssetType): String = when (type.kind) {
    Kind.GOLD -> "طلای تو"
    Kind.SILVER -> "نقره‌ی تو"
    Kind.COIN -> "سکه‌های تو"
    Kind.FIAT -> "ارزِ تو"
    Kind.CRYPTO -> "رمزارزِ تو"
    Kind.STOCK -> "سهم تو"
    Kind.CASH -> "پولت"
}

private fun chartDescription(series: RateSeries, averageCost: Double?): String = buildString {
    append("نمودار قیمت")
    val last = series.last
    if (last != null && averageCost != null) {
        append(if (last.priceToman >= averageCost) "؛ قیمت فعلی بالای میانگین قیمت خریدته" else "؛ قیمت فعلی زیر میانگین قیمت خریدته")
    }
}
