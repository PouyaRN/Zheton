package com.doxigo.muchtoman

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class ReportWindow(val fa: String) {
    MONTH("این ماه"),
    THREE_MONTH("۳ ماه"),
    SIX_MONTH("۶ ماه"),
    YEAR("۱ سال"),
    ALL("همهٔ"),
}

/**
 * The reports tab: live financial snapshot for the selected window.
 *
 * Combines the spending snapshot (income/expense/net/savings rate, per-category spend) with the
 * wealth-trend chart (history line + composition bar). The window selector drives both views.
 * Expense-only denominator for category shares. Hidden, duplicate, and transfer rows excluded.
 */
@Composable
fun ReportsTab(
    ledger: LedgerView,
    /** Wealth-trend data from the portfolio/history layer. */
    history: Map<Long, Double> = emptyMap(),
    current: Double = 0.0,
    composition: List<Pair<Kind, Double>> = emptyList(),
    onSelectCategory: (String?) -> Unit = {},
) {
    val today = remember { mutableLongStateOf(tehranDay(System.currentTimeMillis())) }
    // Recompute at midnight so "این ماه" stays live.
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val tomorrowStart = tehranDayStart(tehranDay(now) + 1) + 1000
            delay(tomorrowStart - now)
            today.longValue = tehranDay(System.currentTimeMillis())
        }
    }
    var window by remember { mutableStateOf(ReportWindow.MONTH) }

    // Compute the window start date
    val windowStart = remember(today.longValue, window) {
        when (window) {
            ReportWindow.MONTH -> jalaliMonthStart(today.longValue)
            ReportWindow.THREE_MONTH -> jalaliMonthsBack(today.longValue, 3)
            ReportWindow.SIX_MONTH -> jalaliMonthsBack(today.longValue, 6)
            ReportWindow.YEAR -> jalaliMonthsBack(today.longValue, 12)
            ReportWindow.ALL -> ledger.allEntries.minOfOrNull { it.txn.day } ?: today.longValue
        }
    }

    val snapshot = remember(ledger.allEntries, windowStart) {
        val visible = ledger.allEntries.filter { !it.duplicate && !it.hidden && !it.transfer && it.txn.day >= windowStart }
        val income = visible.filter { it.txn.direction == "in" }.sumOf { it.txn.signedRial ?: 0L }
        val expense = visible.filter { it.txn.direction == "out" }.sumOf { kotlin.math.abs(it.txn.signedRial ?: 0L) }
        val net = income - expense
        val savingsRate = if (income > 0) ((income - expense).toDouble() / income * 100).toInt().coerceIn(0, 100) else 0

        val catSpend = visible
            .filter { it.txn.direction == "out" }
            .groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.sumOf { kotlin.math.abs(it.txn.signedRial ?: 0L) } }
            .toList()
            .sortedByDescending { it.second }

        ReportSnapshot(income, expense, net, savingsRate, catSpend)
    }

    // Wealth-trend: change over the selected window
    val sorted = remember(history) { history.toSortedMap() }
    val now = today.longValue
    val wealthDays = when (window) {
        ReportWindow.MONTH -> 30
        ReportWindow.THREE_MONTH -> 90
        ReportWindow.SIX_MONTH -> 180
        ReportWindow.YEAR -> 365
        ReportWindow.ALL -> null
    }
    val wealthAvailable = when {
        sorted.isEmpty() -> wealthDays == null
        wealthDays == null -> true
        else -> sorted.firstKey() <= now - wealthDays + 3
    }
    val change: Change? = if (wealthAvailable) {
        when {
            sorted.isEmpty() -> null
            wealthDays == null -> sorted.firstKey().let { d ->
                val base = sorted.getValue(d)
                Change(current - base, if (base > 0) (current - base) / base * 100 else null, d)
            }
            else -> changeOver(history, now, wealthDays, current)
        }
    } else null

    val points: List<Pair<Long, Double>> = change?.let { c ->
        sorted.filterKeys { it >= c.sinceDay && it < now }.map { it.key to it.value } +
            (now to current)
    } ?: emptyList()

    val unhedgedToman = remember(composition) {
        composition.find { it.first == Kind.CASH }?.second?.toLong() ?: 0L
    }
    val hedgedToman = remember(composition) {
        composition.filter { it.first != Kind.CASH }.sumOf { it.second }.toLong()
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Text(
                "گزارش‌ها",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = Space.xl, vertical = Space.m),
            )

            // Window selector
            WindowSelector(window) { window = it }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Space.xl, vertical = Space.s,
                ),
            ) {
                // Spending summary card
                item { ReportSummaryCard(snapshot, window) }

                // Wealth Defense Index gauge card
                item {
                    Spacer(Modifier.height(Space.l))
                    WealthDefenseGaugeCard(unhedgedToman = unhedgedToman, hedgedToman = hedgedToman)
                }

                // AI Purchasing & Hedging Advisor card
                item {
                    Spacer(Modifier.height(Space.l))
                    ReportsPurchasingAdvisorCard(unhedgedToman = unhedgedToman)
                }

                // Asset Allocation Donut chart
                if (composition.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(Space.l))
                        AssetAllocationDonutCard(composition = composition, totalToman = current)
                    }
                }

                // Category spend list
                if (snapshot.catSpend.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(Space.xl))
                        Text(
                            "هزینه بر اساس دسته‌بندی",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(Space.s))
                    }
                    items(snapshot.catSpend, key = { it.first ?: "uncategorized" }) { (catId, amount) ->
                        val catFa = ledger.categories.find { it.id == catId }?.nameFa ?: "دسته‌بندی نشده"
                        val isUncategorized = catId == null
                        CategorySpendRow(catFa, amount, snapshot.expense, isUncategorized, onClick = { onSelectCategory(catId) })
                    }
                }

                // Wealth-trend chart
                if (history.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(Space.xl))
                        Text(
                            "روند دارایی",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(Space.s))
                        if (change == null || points.size < 2) {
                            EmptyReport()
                        } else {
                            ChangeFigure(change, window.fa, now)
                            Spacer(Modifier.height(Space.xl))
                            var scrubPoint by remember(points) { mutableStateOf<Pair<Long, Double>?>(null) }
                            val tone = changeTone(change)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Radius.group))
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.group))
                                    .padding(Space.l),
                            ) {
                                Column {
                                    Box(Modifier.fillMaxWidth()) {
                                        scrubPoint?.let { (day, v) ->
                                            Row(
                                                modifier = Modifier
                                                    .align(Alignment.TopCenter)
                                                    .clip(RoundedCornerShape(Radius.pill))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .border(1.dp, tone.copy(alpha = 0.5f), RoundedCornerShape(Radius.pill))
                                                    .padding(horizontal = Space.m, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(
                                                    faDay(day),
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Spacer(Modifier.width(Space.s))
                                                Text(
                                                    "${faCompact(v, 3, pad = true)} تومان",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = ModamFigures,
                                                    color = tone,
                                                )
                                            }
                                        }
                                        HistoryChart(
                                            points,
                                            tone = tone,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(220.dp),
                                            onScrub = { scrubPoint = it },
                                        )
                                    }
                                    Spacer(Modifier.height(Space.l))
                                    Row(Modifier.fillMaxWidth()) {
                                        ChartCaption("اکنون", current)
                                        Spacer(Modifier.weight(1f))
                                        ChartCaption("شروع", sorted.getValue(change.sinceDay))
                                    }
                                }
                            }
                        }
                    }
                }

                // Composition bar
                if (composition.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(Space.xl))
                        CompositionBar(composition)
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowSelector(selected: ReportWindow, onSelect: (ReportWindow) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xl, vertical = Space.s),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        ReportWindow.entries.forEach { w ->
            FilterChip(
                selected = w == selected,
                onClick = { onSelect(w) },
                label = { Text(w.fa, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private data class ReportSnapshot(
    val income: Long,
    val expense: Long,
    val net: Long,
    val savingsRate: Int,
    val catSpend: List<Pair<String?, Long>>,
)

@Composable
private fun ReportSummaryCard(snapshot: ReportSnapshot, window: ReportWindow) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.group))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.group)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Space.xl)) {
            Text(
                window.fa,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("درآمد", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        bidi(faCompact(tomanOf(snapshot.income))),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E9E5B),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("هزینه", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        bidi(faCompact(tomanOf(snapshot.expense))),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            Spacer(Modifier.height(Space.m))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("خالص", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        bidi(faSignedCompact(tomanOf(snapshot.net), snapshot.net > 0)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (snapshot.net >= 0) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("نرخ پس‌انداز", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${snapshot.savingsRate}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (snapshot.savingsRate >= 20) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySpendRow(
    nameFa: String,
    amount: Long,
    totalExpense: Long,
    isUncategorized: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val pct = if (totalExpense > 0) (amount.toDouble() / totalExpense * 100).toInt() else 0
    val animatedProgress by animateFloatAsState(
        targetValue = (pct / 100f).coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "cat_spend_progress",
    )
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(
            nameFa,
            MaterialTheme.colorScheme.onSurfaceVariant,
            size = 18.dp,
            stroke = 1.4.dp,
        )
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(
                nameFa,
                fontSize = 14.sp,
                color = if (isUncategorized) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                fontWeight = if (isUncategorized) FontWeight.Normal else FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f))
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                bidi(faCompact(tomanOf(amount))),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "${pct}%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WealthDefenseGaugeCard(
    unhedgedToman: Long,
    hedgedToman: Long,
    modifier: Modifier = Modifier,
) {
    val defense = remember(unhedgedToman, hedgedToman) {
        PurchasingOptimizationEngine.computeWealthDefenseIndex(unhedgedToman, hedgedToman)
    }

    val animatedScore by animateFloatAsState(
        targetValue = defense.score / 100f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "wdi_score",
    )

    val gaugeColor = when {
        defense.score >= 80 -> Color(0xFF10B981)
        defense.score >= 60 -> Color(0xFF38BDF8)
        defense.score >= 40 -> Color(0xFFFBBF24)
        else -> Color(0xFFF87171)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.group))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.group)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Space.xl)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "شاخص مصونیت از تورم (WDI)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = gaugeColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, gaugeColor.copy(alpha = 0.4f)),
                ) {
                    Text(
                        "${defense.score}٪",
                        modifier = Modifier.padding(horizontal = Space.m, vertical = 2.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = ModamFigures,
                        color = gaugeColor,
                    )
                }
            }

            Spacer(Modifier.height(Space.m))

            // Gauge sweep bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedScore)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(gaugeColor.copy(alpha = 0.7f), gaugeColor),
                            ),
                        ),
                )
            }

            Spacer(Modifier.height(Space.m))

            Text(
                defense.levelFa,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = gaugeColor,
            )

            Spacer(Modifier.height(Space.m))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "نقدینگی در معرض تورم",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${faCompact(defense.unhedgedToman.toDouble())} تومان",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModamFigures,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "افت روزانه ارزش (Bleed)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${faCompact(defense.dailyBleedToman.toDouble())} ت / روز",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModamFigures,
                        color = if (defense.dailyBleedToman > 0) MaterialTheme.colorScheme.error else gaugeColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportsPurchasingAdvisorCard(
    unhedgedToman: Long,
    modifier: Modifier = Modifier,
) {
    val quickBudgets = remember(unhedgedToman) {
        listOfNotNull(
            if (unhedgedToman >= 1_000_000L) unhedgedToman else null,
            10_000_000L,
            50_000_000L,
            150_000_000L,
            500_000_000L,
        ).distinct()
    }
    var selectedBudget by remember(unhedgedToman) {
        mutableLongStateOf(if (unhedgedToman >= 1_000_000L) unhedgedToman else 50_000_000L)
    }

    val advice = remember(selectedBudget) {
        PurchasingOptimizationEngine.computeBestBuyAdvice(budgetToman = selectedBudget)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.group))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.group)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Space.xl)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "🛡️ دستیار هوشمند سپر تورمی",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "بودجه: ${faCompact(selectedBudget.toDouble())} ت",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ModamFigures,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(Space.s))

            // Budget quick selector chips
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                quickBudgets.forEach { b ->
                    val isSelected = b == selectedBudget
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedBudget = b },
                        label = {
                            Text(
                                if (b == unhedgedToman) "نقدینگی من" else "${faCompact(b.toDouble())} ت",
                                fontSize = 10.sp,
                                fontFamily = ModamFigures,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(Space.m))

            Text(
                advice.headlineFa,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                advice.summaryFa,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Space.m))

            // Allocation visual split bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            ) {
                advice.allocations.forEach { slice ->
                    val sliceColor = when (slice.asset) {
                        AssetClass.GOLD_ETF, AssetClass.MELTED_GOLD_18K, AssetClass.GOLD_COIN_BAHAR -> Color(0xFFFBBF24)
                        AssetClass.USDT_STABLECOIN -> Color(0xFF34D399)
                        AssetClass.REAL_ESTATE_FUND, AssetClass.PHYSICAL_PROPERTY -> Color(0xFF38BDF8)
                    }
                    Box(
                        Modifier
                            .weight(slice.percentage.toFloat())
                            .fillMaxHeight()
                            .background(sliceColor),
                    )
                }
            }

            Spacer(Modifier.height(Space.s))

            // Slices detail list
            advice.allocations.forEach { slice ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(slice.asset.icon, fontSize = 13.sp)
                        Text(
                            "${slice.percentage}٪ ${slice.asset.fa}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Text(
                        "${faCompact(slice.amountToman.toDouble())} ت",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModamFigures,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Warnings
            if (advice.warningsFa.isNotEmpty()) {
                Spacer(Modifier.height(Space.s))
                advice.warningsFa.forEach { warn ->
                    Text(
                        "⚠️ $warn",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetAllocationDonutCard(
    composition: List<Pair<Kind, Double>>,
    totalToman: Double,
    modifier: Modifier = Modifier,
) {
    if (composition.isEmpty() || totalToman <= 0.0) return

    val kindColors = mapOf(
        Kind.CASH to Color(0xFF10B981),
        Kind.GOLD to Color(0xFFF59E0B),
        Kind.COIN to Color(0xFFD97706),
        Kind.CRYPTO to Color(0xFF8B5CF6),
        Kind.FIAT to Color(0xFF38BDF8),
        Kind.STOCK to Color(0xFFEC4899),
        Kind.SILVER to Color(0xFF94A3B8),
    )

    var selectedKind by remember { mutableStateOf<Kind?>(null) }

    val activeItem = composition.find { it.first == selectedKind } ?: composition.maxByOrNull { it.second }
    val activePct = if (activeItem != null && totalToman > 0.0) (activeItem.second / totalToman * 100.0).toInt() else 0

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.group))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.group)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Space.xl)) {
            Text(
                "ترکیب سبد دارایی",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(Space.l))

            // Donut Ring Canvas with Center Value
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(160.dp)) {
                    val strokeWidth = 24.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                    val arcSize = Size(diameter, diameter)

                    var startAngle = -90f
                    composition.forEach { (kind, amount) ->
                        val sweep = ((amount / totalToman) * 360f).toFloat()
                        val color = kindColors[kind] ?: Color.Gray
                        val isSelected = kind == activeItem?.first

                        drawArc(
                            color = if (isSelected) color else color.copy(alpha = 0.85f),
                            startAngle = startAngle,
                            sweepAngle = sweep.coerceAtLeast(1.5f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(
                                width = if (isSelected) strokeWidth * 1.15f else strokeWidth,
                                cap = StrokeCap.Butt,
                            ),
                        )
                        startAngle += sweep
                    }
                }

                // Center hole label
                activeItem?.let { (kind, amount) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = kind.fa,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "$activePct٪",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = ModamFigures,
                            color = kindColors[kind] ?: MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "${faCompact(amount)} ت",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = ModamFigures,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.l))

            // Legend chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                composition.forEach { (kind, amount) ->
                    val color = kindColors[kind] ?: Color.Gray
                    val pct = (amount / totalToman * 100.0).toInt()
                    val isSelected = kind == activeItem?.first

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable { selectedKind = kind }
                            .background(if (isSelected) color.copy(alpha = 0.15f) else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(color),
                        )
                        Text(
                            text = "${kind.fa} $pct٪",
                            fontSize = 11.sp,
                            fontFamily = ModamFigures,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
