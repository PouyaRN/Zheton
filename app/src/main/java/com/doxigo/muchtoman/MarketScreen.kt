package com.doxigo.muchtoman

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.ui.components.CanvasGlyph
import com.doxigo.muchtoman.ui.components.OutlineIconButton
import com.doxigo.muchtoman.ui.components.PillChip
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.truncate

/*
 * The دارایی tab, as docs/design/canvas/project/Market.dc.html draws it: caption with the real
 * refresh time, 40sp title, a two-way segmented control, then either her holdings (allocation
 * ring, legend, rows) or every live rate in the same row style.
 *
 * Every figure here is real: values from the rates on screen, changes and sparklines from the
 * stored daily closes (HoldingPnl.kt). Where no stored close exists the change is left out,
 * never invented.
 */

/** One share of the ring and one legend entry: a kind she actually holds. */
internal data class KindShare(val kind: Kind, val label: String, val valueToman: Double, val share: Double)

/**
 * Her holdings' value split by kind, largest first. Holdings with no rate, excluded ones and
 * zero amounts are left out, since they are not in the total either.
 */
internal fun allocationByKind(rows: List<Pair<AssetType, Double?>>): List<KindShare> {
    val byKind = rows.mapNotNull { (type, value) -> value?.takeIf { it > 0.0 && it.isFinite() }?.let { type to it } }
        .groupBy { it.first.kind }
    val total = byKind.values.sumOf { list -> list.sumOf { it.second } }
    if (total <= 0.0) return emptyList()
    return byKind.map { (kind, list) ->
        val ids = list.map { it.first.id }.toSet()
        KindShare(kind, kindLabel(kind, ids), list.sumOf { it.second }, list.sumOf { it.second } / total)
    }.sortedByDescending { it.valueToman }
}

/** «دلار» rather than «ارز» when the dollar is the only currency she holds, as the canvas says it. */
private fun kindLabel(kind: Kind, ids: Set<String>): String = when {
    ids == setOf("usd") -> "دلار"
    ids == setOf("usdt") -> "تتر"
    kind == Kind.CASH -> "نقد"
    else -> kind.fa
}

/**
 * Today's total against yesterday's, from each holding's own day change. Cash does not move.
 * Null as soon as one priced holding has no stored close to compare with: a partial change
 * passed off as the whole one would be an invented number.
 */
internal fun portfolioDayChangePercent(rows: List<Triple<AssetType, Double?, DayChange?>>): Double? {
    var today = 0.0
    var yesterday = 0.0
    for ((type, value, change) in rows) {
        val v = value?.takeIf { it > 0.0 && it.isFinite() } ?: continue
        today += v
        yesterday += when {
            type.kind == Kind.CASH -> v
            change == null -> return null
            else -> v / (1.0 + change.percent / 100.0)
        }
    }
    if (yesterday <= 0.0) return null
    return (today - yesterday) / yesterday * 100.0
}

/** «+۴٫۱٪», «−۰٫۸٪», «۰٫۰٪»: signed, one decimal, the sign kept on the left of the digits. */
internal fun faSignedPercent(percent: Double): String {
    val r = Math.round(percent * 10.0) / 10.0
    val body = faDecimal(abs(r), 1, pad = true) + "٪"
    return when {
        r > 0.0 -> ltrFigure("+$body")
        r < 0.0 -> ltrFigure("−$body")
        else -> body
    }
}

/** The figure in the ring and its unit line: «۵۲٫۰» over «میلیون تومان». Truncated, never rounded. */
internal fun ringFigure(total: Double): Pair<String, String> {
    val (div, unit) = when {
        total >= 1_000_000_000_000.0 -> 1_000_000_000_000.0 to "همت"
        total >= 1_000_000_000.0 -> 1_000_000_000.0 to "میلیارد تومان"
        total >= 1_000_000.0 -> 1_000_000.0 to "میلیون تومان"
        total >= 1_000.0 -> 1_000.0 to "هزار تومان"
        else -> return faNumber(total) to "تومان"
    }
    val t = truncate(total / div * 10.0) / 10.0
    return faDecimal(t, 1, pad = true) to unit
}

/** The round mark: «۱۸» for 18k gold, «س» for a coin, the currency's sign, the coin's ticker. */
internal fun assetMark(type: AssetType): String = when (type.id) {
    "gold18" -> "۱۸"
    "gold_mesghal" -> "م"
    "usd", "cad" -> "$"
    "eur" -> "€"
    "gbp" -> "£"
    "try" -> "₺"
    "aed" -> "د"
    "nok" -> "kr"
    "usdt" -> "₮"
    "btc" -> "₿"
    "eth" -> "Ξ"
    TOMAN_ID, BANK_ID -> "ت"
    else -> when (type.kind) {
        Kind.COIN -> "س"
        Kind.GOLD -> "ط"
        Kind.SILVER -> "ن"
        Kind.CASH -> "ت"
        Kind.STOCK -> type.fa.take(1)
        Kind.CRYPTO, Kind.FIAT -> type.id.take(3).uppercase()
    }
}

/** Ring and mark colours per kind: the canvas's four, and quiet neighbours for the rest. */
private data class KindTone(val ring: Color, val markFg: Color, val markBgDark: Color)

private fun kindTone(kind: Kind, id: String?): KindTone = when (kind) {
    Kind.GOLD -> KindTone(Color(0xFFF5B840), Color(0xFFF5B840), Color(0xFF1C1608))
    Kind.COIN -> KindTone(Color(0xFFB8752A), Color(0xFFD89A4E), Color(0xFF1E140A))
    Kind.FIAT -> KindTone(Color(0xFF7FB2FF), Color(0xFF7FB2FF), Color(0xFF0E1A2E))
    Kind.CRYPTO -> if (id == null || id == "usdt") KindTone(Color(0xFF3FD98B), Color(0xFF3FD98B), Color(0xFF0D2218))
        else KindTone(Color(0xFF3FD98B), Color(0xFFB79CFF), Color(0xFF1A1430))
    Kind.SILVER -> KindTone(Color(0xFFC7C7CC), Color(0xFFC7C7CC), Color(0xFF1A1A1C))
    Kind.STOCK -> KindTone(Color(0xFFB79CFF), Color(0xFFB79CFF), Color(0xFF1A1430))
    Kind.CASH -> KindTone(Color(0xFF8E8E93), Color(0xFFB5B5B8), Color(0xFF161618))
}

private enum class MarketMode { MINE, RATES }

/** The live rates the «همهٔ نرخ‌ها» list shows, in catalogue order: fixed assets, then the main coins. */
private val RATE_LIST_CRYPTO = listOf("usdt", "btc", "eth", "ton", "sol")

@Composable
fun MarketTabScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onAddHolding: () -> Unit,
    onOpenHolding: (Holding) -> Unit,
    onDeleteHolding: (String) -> Unit,
    onArchiveHolding: (String, Boolean) -> Unit,
    bottomInset: Dp,
    privacyMode: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val t = CanvasTheme.tokens
    var mode by rememberSaveable { mutableStateOf(MarketMode.MINE) }
    var rateQuery by rememberSaveable { mutableStateOf("") }
    var rateCategory by rememberSaveable { mutableStateOf(RateCategory.ALL) }
    var openTool by rememberSaveable { mutableStateOf<String?>(null) }

    // The caption says how long ago the rates were fetched; keep "N دقیقه پیش" honest as time passes.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.rates.updatedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    val effective = state.effective
    val types = remember(state.coins, state.stocks) { catalog(state.coins, state.stocks).associateBy { it.id } }
    fun typeOf(id: String) = resolveType(id, types)

    // listHoldings, as Home's net worth reads it: her holdings plus the bank row (the messages'
    // balances), so the ring adds up to the same total and the bank row can open the accounts.
    val active = state.listHoldings
    val archived = remember(state.holdings) { state.holdings.filter { it.archived } }

    val counted = active.filterNot { it.excluded }.map { h ->
        val type = typeOf(h.typeId)
        val value = effective[h.typeId]?.takeIf { it.isFinite() && it > 0.0 }?.let { it * h.amount }
        Triple(type, value, state.dayChangeOf(h.typeId, now))
    }
    val slices = allocationByKind(counted.map { it.first to it.second })
    val total = slices.sumOf { it.valueToman }
    val dayChange = portfolioDayChangePercent(counted)

    Box(modifier.fillMaxSize().background(t.ground)) {
        MarketGlow()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
        ) {
            item(key = "header") {
                MarketHeader(
                    caption = ratesCaption(state.rates.updatedAt, now),
                    fresh = state.rates.updatedAt > 0 && now - state.rates.updatedAt < 60 * 60_000L,
                    refreshing = state.loading,
                    onRefresh = onRefresh,
                    mode = mode,
                    onMode = { mode = it },
                    onAdd = onAddHolding,
                )
            }

            if (mode == MarketMode.MINE) {
                item(key = "ring") {
                    Spacer(Modifier.height(16.dp))
                    AllocationRing(
                        slices = slices,
                        total = total,
                        dayChangePercent = dayChange,
                        privacyMode = privacyMode,
                    )
                }
                if (slices.isNotEmpty()) {
                    item(key = "legend") {
                        Spacer(Modifier.height(16.dp))
                        Legend(slices)
                        Spacer(Modifier.height(16.dp))
                    }
                }
                if (active.isEmpty()) {
                    item(key = "empty") {
                        EmptyHoldings(onAddHolding)
                    }
                }
                items(active, key = { "h:" + it.key }) { h ->
                    HoldingRow(h, typeOf(h.typeId), state, now, privacyMode, dimmed = h.excluded,
                        onOpen = { onOpenHolding(h) },
                        onArchive = { onArchiveHolding(h.key, true) },
                        onDelete = { onDeleteHolding(h.key) },
                    )
                }
                if (archived.isNotEmpty()) {
                    item(key = "archived_title") {
                        Text(
                            "بایگانی‌شده · ${fa(archived.size)}",
                            fontFamily = Modam,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = t.muted,
                            modifier = Modifier.padding(start = CanvasTheme.gutter, end = CanvasTheme.gutter, top = 24.dp, bottom = 4.dp),
                        )
                    }
                    items(archived, key = { "a:" + it.key }) { h ->
                        HoldingRow(h, typeOf(h.typeId), state, now, privacyMode, dimmed = true, archived = true,
                            onOpen = { onOpenHolding(h) },
                            onArchive = { onArchiveHolding(h.key, false) },
                            onDelete = { onDeleteHolding(h.key) },
                        )
                    }
                }
            } else {
                // Every live rate she can look up: the fixed catalogue and main coins first, then
                // (only while searching) the rest of the catalogue, بورس included.
                val ids = buildList {
                    STATIC_CATALOG.forEach { if (it.id != TOMAN_ID && effective[it.id] != null) add(it.id) }
                    RATE_LIST_CRYPTO.forEach { if (effective[it] != null && it !in this) add(it) }
                    state.holdings.forEach { h ->
                        if (h.typeId != TOMAN_ID && h.typeId != BANK_ID && effective[h.typeId] != null && h.typeId !in this) add(h.typeId)
                    }
                    if (rateQuery.isNotBlank()) {
                        types.values.forEach { ty ->
                            if (ty.id != TOMAN_ID && ty.id != BANK_ID && effective[ty.id] != null && ty.id !in this) add(ty.id)
                        }
                    }
                }
                val shown = ids.filter { id ->
                    val ty = typeOf(id)
                    rateCategory.matches(ty.kind) && (rateQuery.isBlank() || rateMatches(ty, rateQuery))
                }
                item(key = "rates_search") {
                    RateSearchField(
                        query = rateQuery,
                        onQuery = { rateQuery = it },
                        modifier = Modifier.padding(start = CanvasTheme.gutter, end = CanvasTheme.gutter, top = 18.dp),
                    )
                }
                item(key = "rates_chips") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = CanvasTheme.gutter, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RateCategory.entries.forEach { c ->
                            PillChip(c.label, selected = rateCategory == c, onClick = { rateCategory = c })
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item(key = "rates_empty") {
                        Text(
                            when {
                                state.loading && ids.isEmpty() -> "در حال گرفتن نرخ‌ها…"
                                ids.isEmpty() -> "هنوز نرخی دریافت نشده"
                                else -> "نرخی با این مشخصات پیدا نشد"
                            },
                            fontFamily = Modam,
                            fontSize = 13.sp,
                            color = t.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                        )
                    }
                }
                items(shown, key = { "r:$it" }) { id ->
                    RateRow(typeOf(id), effective.getValue(id), state, now)
                }
                // The old rates page's two tools, kept one tap away: both need real rates.
                item(key = "rates_tools") {
                    Spacer(Modifier.height(18.dp))
                    val gold18 = effective["gold18"]
                    val emami = effective["coin_emami"]
                    val nim = effective["coin_nim"]
                    val rob = effective["coin_rob"]
                    val usd = effective["usd"]
                    val usdt = effective["usdt"]
                    ToolRow(
                        title = "حباب‌سنج سکه",
                        sub = "قیمت بازار امامی، نیم و ربع در برابر ارزش طلای خودشان",
                        ready = gold18 != null && emami != null && nim != null && rob != null,
                        open = openTool == "bubble",
                        onToggle = { openTool = if (openTool == "bubble") null else "bubble" },
                    )
                    if (openTool == "bubble" && gold18 != null && emami != null && nim != null && rob != null) {
                        GoldCoinBubbleGauge(emamiPrice = emami, nimPrice = nim, robPrice = rob, gold18Price = gold18)
                    }
                    ToolRow(
                        title = "رادار آربیتراژ",
                        sub = "فاصلهٔ تتر با دلار اسکناس و حباب سکهٔ امامی",
                        ready = gold18 != null && emami != null && rob != null && usd != null && usdt != null,
                        open = openTool == "radar",
                        onToggle = { openTool = if (openTool == "radar") null else "radar" },
                    )
                    if (openTool == "radar" && gold18 != null && emami != null && rob != null && usd != null && usdt != null) {
                        MarketArbitrageRadar(
                            usdtToman = usdt,
                            usdCashToman = usd,
                            emamiCoinToman = emami,
                            robCoinToman = rob,
                            gold18Toman = gold18,
                        )
                    }
                }
            }
        }
    }
}

/** The rates list's category chips, as the old rates page had them. */
private enum class RateCategory(val label: String, val kinds: Set<Kind>?) {
    ALL("همه", null),
    FIAT("ارز", setOf(Kind.FIAT)),
    GOLD("طلا و سکه", setOf(Kind.GOLD, Kind.COIN, Kind.SILVER)),
    CRYPTO("رمزارز", setOf(Kind.CRYPTO)),
    STOCK("بورس", setOf(Kind.STOCK));

    fun matches(kind: Kind) = kinds == null || kind in kinds
}

private fun rateMatches(type: AssetType, query: String): Boolean {
    val q = query.trim()
    return type.fa.contains(q, ignoreCase = true) ||
        type.en.contains(q, ignoreCase = true) ||
        type.id.contains(q, ignoreCase = true)
}

/** A pill search field in the card colour, as the ledger's. */
@Composable
private fun RateSearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    val t = CanvasTheme.tokens
    Row(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(t.card)
            .border(1.dp, t.cardBorder, CircleShape)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CanvasGlyph(listOf("M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", "M20 20l-3.5-3.5"), t.muted, size = 18.dp)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("جست‌وجوی ارز، سکه، رمزارز یا نماد", fontFamily = Modam, fontSize = 13.sp, color = t.muted, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Modam, fontSize = 14.sp, color = t.ink),
                cursorBrush = SolidColor(t.accent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "جست‌وجوی نرخ‌ها" },
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "پاک کردن") { onQuery("") },
                contentAlignment = Alignment.Center,
            ) {
                CanvasGlyph(listOf("M6 6l12 12M18 6L6 18"), t.muted, size = 16.dp)
            }
        }
    }
}

/** One of the rates page's tools as a row: opens in place, or says it is waiting on rates. */
@Composable
private fun ToolRow(title: String, sub: String, ready: Boolean, open: Boolean, onToggle: () -> Unit) {
    val t = CanvasTheme.tokens
    val divider = if (t.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (ready) Modifier.clickable(role = Role.Button, onClick = onToggle) else Modifier)
            .padding(horizontal = CanvasTheme.gutter)
            .drawBehind {
                val h = 1.dp.toPx()
                drawRect(divider, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
            }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Modam, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = if (ready) t.ink else t.muted, maxLines = 1)
            Text(
                if (ready) sub else "منتظر نرخ‌های لازم",
                fontFamily = Modam, fontSize = 12.sp, lineHeight = 17.sp, color = t.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (ready) {
            Text(if (open) "بستن" else "نمایش", fontFamily = Modam, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = t.accentText)
        }
    }
}

private fun ratesCaption(updatedAt: Long, now: Long): String =
    if (updatedAt <= 0L) "نرخ‌ها هنوز به‌روز نشده" else "نرخ‌ها ${faAgo(updatedAt, now)} به‌روز شد"

/** Market.dc.html's own light: 520px, its centre 200px in from the right edge and 320px down. */
@Composable
private fun MarketGlow() {
    val t = CanvasTheme.tokens
    val alpha = if (t.isDark) 0.18f else t.glowCore
    Canvas(Modifier.fillMaxSize()) {
        val radius = 260.dp.toPx()
        val centre = Offset(size.width - 200.dp.toPx(), 320.dp.toPx())
        drawCircle(
            brush = Brush.radialGradient(
                0f to t.glow.copy(alpha = alpha),
                1f to t.glow.copy(alpha = 0f),
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
        )
    }
}

@Composable
private fun MarketHeader(
    caption: String,
    fresh: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    mode: MarketMode,
    onMode: (MarketMode) -> Unit,
    onAdd: () -> Unit,
) {
    val t = CanvasTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = CanvasTheme.gutter, end = CanvasTheme.gutter, top = 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Tapping the caption fetches the rates again.
            Row(
                Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "به‌روزرسانی نرخ‌ها", onClick = onRefresh),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                refreshing -> t.accent
                                fresh -> t.gain
                                else -> t.muted
                            }
                        )
                )
                Text(
                    if (refreshing) "در حال به‌روزرسانی نرخ‌ها…" else caption,
                    fontFamily = Modam,
                    fontSize = 11.sp,
                    color = t.inkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "دارایی",
                fontFamily = Modam,
                fontSize = 40.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = t.ink,
                maxLines = 1,
            )
        }
        Row(
            Modifier.padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Segmented(mode, onMode)
            OutlineIconButton("افزودن دارایی", onAdd, size = 40.dp) { tint ->
                CanvasGlyph(listOf("M12 5v14M5 12h14"), tint, size = 18.dp, stroke = 2f)
            }
        }
    }
}

@Composable
private fun Segmented(mode: MarketMode, onMode: (MarketMode) -> Unit) {
    val t = CanvasTheme.tokens
    val fill = if (t == CanvasDark.tokens) Color(0xFF121214) else t.card
    Row(
        Modifier
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, t.cardBorder, CircleShape)
            .padding(3.dp)
            .semantics { contentDescription = "نمایش" },
    ) {
        listOf(MarketMode.MINE to "دارایی‌های من", MarketMode.RATES to "همهٔ نرخ‌ها").forEach { (m, label) ->
            val on = m == mode
            Box(
                Modifier
                    .height(34.dp)
                    .clip(CircleShape)
                    .background(if (on) t.inverse else Color.Transparent)
                    .clickable(role = Role.Tab) { onMode(m) }
                    .semantics { selected = on }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontFamily = Modam,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                    color = if (on) t.onInverse else t.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AllocationRing(
    slices: List<KindShare>,
    total: Double,
    dayChangePercent: Double?,
    privacyMode: Boolean,
) {
    val t = CanvasTheme.tokens
    val track = if (t == CanvasDark.tokens) Color(0xFF141416) else t.track
    // No holding priced yet: the value is unknown, not zero.
    val (figure, unit) = if (slices.isEmpty()) "—" to "تومان" else ringFigure(total)
    Box(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .semantics {
                contentDescription = "ترکیب دارایی: " + slices.joinToString("، ") {
                    "${it.label} ${fa(Math.round(it.share * 100))}٪"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(220.dp)) {
            val k = size.width / 220f
            val r = 80f * k
            val stroke = 18f * k
            val tl = Offset(center.x - r, center.y - r)
            val arcSize = Size(r * 2, r * 2)
            drawCircle(track, radius = r, center = center, style = Stroke(stroke))
            // As the artboard draws it: each share's arc less 12 units, starting 2 units in,
            // with round caps that close the gaps to a hairline.
            val c = (2 * Math.PI * 80).toFloat()
            var at = 0f
            slices.forEach { s ->
                val tone = kindTone(s.kind, null).ring
                val len = (s.share.toFloat() * c - 12f).coerceAtLeast(0.1f)
                val start = at + 2f
                drawArc(
                    color = tone,
                    startAngle = -90f + start / c * 360f,
                    sweepAngle = len / c * 360f,
                    useCenter = false,
                    topLeft = tl,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                at += s.share.toFloat() * c
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("ارزش امروز", fontFamily = Modam, fontSize = 11.sp, lineHeight = 14.sp, color = t.muted)
            Text(
                maskFinancialAmount(figure, privacyMode),
                fontFamily = ModamFigures,
                fontSize = 34.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = t.ink,
            )
            Text(unit, fontFamily = Modam, fontSize = 12.sp, lineHeight = 15.sp, color = t.muted)
            if (dayChangePercent != null) {
                val tone = when {
                    dayChangePercent >= 0.05 -> t.gain
                    dayChangePercent <= -0.05 -> t.loss
                    else -> t.muted
                }
                Text(
                    "${faSignedPercent(dayChangePercent)} نسبت به دیروز",
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clip(CircleShape)
                        .background(tone.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    fontFamily = Modam,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = tone,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Legend(slices: List<KindShare>) {
    val t = CanvasTheme.tokens
    Row(
        Modifier.fillMaxWidth().padding(horizontal = CanvasTheme.gutter),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
        slices.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(kindTone(s.kind, null).ring))
                Text(s.label, fontFamily = Modam, fontSize = 12.sp, color = t.inkSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EmptyHoldings(onAdd: () -> Unit) {
    val t = CanvasTheme.tokens
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("هنوز دارایی‌ای ثبت نکرده‌ای", fontFamily = Modam, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = t.ink)
        Text(
            "طلا، سکه، ارز یا رمزارزت را اضافه کن تا ارزش امروزش این‌جا حساب شود.",
            fontFamily = Modam, fontSize = 12.sp, color = t.muted, textAlign = TextAlign.Center,
        )
        Text(
            "افزودن دارایی",
            modifier = Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onAdd).padding(horizontal = 12.dp, vertical = 6.dp),
            fontFamily = Modam, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = t.accentText,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HoldingRow(
    holding: Holding,
    type: AssetType,
    state: UiState,
    now: Long,
    privacyMode: Boolean,
    dimmed: Boolean,
    archived: Boolean = false,
    onOpen: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val rate = state.effective[holding.typeId]?.takeIf { it.isFinite() && it > 0.0 }
    val value = rate?.let { it * holding.amount }
    val held = buildString {
        append(faHeld(holding.amount, type.dec))
        append(' ')
        append(if (type.unitFa.any { it in 'A'..'Z' }) bidi(type.unitFa) else type.unitFa)
        if (holding.wallet != null) append(" · کیف‌پول")
        if (holding.excluded && !archived) append(" · بیرون از جمع")
    }
    Box {
        AssetRowLayout(
            type = type,
            name = holding.nameOr(type.fa),
            sub = held,
            spark = if (type.kind == Kind.CASH) emptyList() else state.sparklineOf(holding.typeId, now),
            value = value?.let { maskFinancialAmount(faCompact(it, 1, pad = true), privacyMode) } ?: "—",
            change = if (type.kind == Kind.CASH) null else state.dayChangeOf(holding.typeId, now)?.percent,
            dimmed = dimmed,
            modifier = Modifier.combinedClickable(
                role = Role.Button,
                onClick = onOpen,
                // The bank row's amount comes from the messages: nothing to archive or delete.
                onLongClick = if (holding.typeId == BANK_ID) null else ({ menu = true }),
            ),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("ویرایش دارایی") }, onClick = { menu = false; onOpen() })
            DropdownMenuItem(
                text = { Text(if (archived) "بازیابی از بایگانی" else "انتقال به بایگانی") },
                onClick = { menu = false; onArchive() },
            )
            DropdownMenuItem(
                text = { Text("حذف دارایی", color = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

@Composable
private fun RateRow(type: AssetType, rate: Double, state: UiState, now: Long) {
    AssetRowLayout(
        type = type,
        name = type.fa,
        sub = "هر " + (if (type.unitFa.any { it in 'A'..'Z' }) bidi(type.unitFa) else type.unitFa),
        spark = state.sparklineOf(type.id, now),
        value = faRate(rate),
        change = state.dayChangeOf(type.id, now)?.percent,
        dimmed = false,
        modifier = Modifier,
    )
}

/** One canvas row: round mark, name over its sub line, sparkline, value over change. */
@Composable
private fun AssetRowLayout(
    type: AssetType,
    name: String,
    sub: String,
    spark: List<Double>,
    value: String,
    change: Double?,
    dimmed: Boolean,
    modifier: Modifier,
) {
    val t = CanvasTheme.tokens
    val toneOf = kindTone(type.kind, type.id)
    val divider = if (t.isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.06f)
    val tone = when {
        change == null -> t.muted
        Math.round(change * 10.0) > 0 -> t.gain
        Math.round(change * 10.0) < 0 -> t.loss
        else -> t.muted
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = CanvasTheme.gutter)
            .drawBehind {
                val h = 1.dp.toPx()
                drawRect(divider, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
            }
            .padding(vertical = 11.dp)
            .alpha(if (dimmed) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (t.isDark) toneOf.markBgDark else toneOf.markFg.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                assetMark(type),
                fontFamily = ModamFigures,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (t.isDark) toneOf.markFg else toneOf.markFg.darken(),
                maxLines = 1,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(name, fontFamily = Modam, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = t.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontFamily = Modam, fontSize = 12.sp, lineHeight = 17.sp, color = t.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Sparkline(spark, tone, Modifier.width(56.dp).height(26.dp))
        Column(Modifier.width(96.dp), horizontalAlignment = Alignment.End) {
            Text(value, fontFamily = ModamFigures, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = t.ink, maxLines = 1)
            Text(
                change?.let { faSignedPercent(it) } ?: "—",
                fontFamily = ModamFigures,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                color = tone,
                maxLines = 1,
            )
        }
    }
}

/** A light-theme mark glyph: the night colour taken down far enough to read on a pale well. */
private fun Color.darken(): Color = Color(red * 0.62f, green * 0.62f, blue * 0.62f, alpha)

/**
 * The stored closes as a smooth line in a 56×26 box, oldest on the left as the artboard draws
 * it. Fewer than two points draws nothing: one close is not a trend.
 */
@Composable
private fun Sparkline(points: List<Double>, tone: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        // A flat week stays a flat line: the scale never stretches below 2% of the price.
        val mid = (points.min() + points.max()) / 2
        val span = maxOf(points.max() - points.min(), abs(mid) * 0.02)
        val lo = mid - span / 2
        val hi = mid + span / 2
        val pad = 4.dp.toPx()
        val w = size.width - 2.dp.toPx()
        val h = size.height - pad * 2
        val x0 = 1.dp.toPx()
        fun pt(i: Int): Offset {
            val x = x0 + w * i / (points.size - 1)
            val y = if (hi - lo < 1e-9) pad + h / 2 else pad + h * (1f - ((points[i] - lo) / (hi - lo)).toFloat())
            return Offset(x, y)
        }
        val path = Path()
        var prev = pt(0)
        path.moveTo(prev.x, prev.y)
        for (i in 1 until points.size) {
            val cur = pt(i)
            val mx = (prev.x + cur.x) / 2
            path.cubicTo(mx, prev.y, mx, cur.y, cur.x, cur.y)
            prev = cur
        }
        drawPath(path, tone, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
