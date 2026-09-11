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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
    bankAccounts: List<BankAccount> = emptyList(),
    onSelectCategory: (String?) -> Unit = {},
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    privacyMode: Boolean = false,
    /** The user's chosen financial-month window; the month view follows it. */
    cycle: CycleSpec = CycleSpec(),
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
    val windowStart = remember(today.longValue, window, cycle) {
        when (window) {
            ReportWindow.MONTH -> cycleWindow(today.longValue, cycle).startDay
            ReportWindow.THREE_MONTH -> jalaliMonthsBack(today.longValue, 3)
            ReportWindow.SIX_MONTH -> jalaliMonthsBack(today.longValue, 6)
            ReportWindow.YEAR -> jalaliMonthsBack(today.longValue, 12)
            ReportWindow.ALL -> ledger.allEntries.minOfOrNull { it.txn.day } ?: today.longValue
        }
    }

    val windowDays = remember(window, today.longValue, windowStart, cycle) {
        when (window) {
            ReportWindow.MONTH -> (cycleWindow(today.longValue, cycle).endDay - windowStart).toInt().coerceAtLeast(1)
            ReportWindow.THREE_MONTH -> 90
            ReportWindow.SIX_MONTH -> 180
            ReportWindow.YEAR -> 365
            ReportWindow.ALL -> (today.longValue - windowStart).toInt().coerceAtLeast(1)
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

    // 50/30/20 Structural Budget Classification for the selected window
    val visibleEntries = remember(ledger.allEntries, windowStart) {
        ledger.allEntries.filter { !it.duplicate && !it.hidden && !it.transfer && it.txn.day >= windowStart }
    }
    val budget503020 = remember(visibleEntries) {
        BudgetRule503020Summary.compute(visibleEntries)
    }

    // Micro-leak detection for micro-transactions under 50,000 Tomans
    val microLeakSummary = remember(visibleEntries) {
        MicroLeakDetector.analyze(visibleEntries, thresholdToman = 50_000L)
    }

    // Bank fee & friction tracker
    val bankFeeSummary = remember(visibleEntries, windowDays) {
        BankFeeTracker.analyze(visibleEntries, windowDays = windowDays)
    }

    // Spending time heatmap & behavioral patterns
    val heatmapInsight = remember(visibleEntries, windowDays) {
        SpendingHeatmapAnalyzer.analyze(visibleEntries, windowDays = windowDays)
    }

    // Idle Cash & Anti-Inflation Sweep recommendation
    val idleCashWarning = remember(bankAccounts, snapshot.expense, windowDays) {
        val totalCheckingBalanceToman = bankAccounts.sumOf { it.balance }.toLong()
        val expenseToman = snapshot.expense / 10L
        val monthlyBurnToman = if (windowDays > 0) (expenseToman * 30L / windowDays) else expenseToman
        IdleCashAnalyzer.evaluate(
            checkingBalanceToman = totalCheckingBalanceToman,
            monthlyExpenseToman = monthlyBurnToman,
        )
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
                    start = Space.xl, end = Space.xl, top = Space.s, bottom = bottomInset + 100.dp,
                ),
            ) {
                // Section 1: Monzo Trends-inspired Payday Burn-Down Trajectory Card
                if (window == ReportWindow.MONTH) {
                    item {
                        PaydayBurnDownCard(
                            entries = visibleEntries,
                            today = today.longValue,
                            cycle = cycle,
                            totalIncomeRial = snapshot.income,
                            totalExpenseRial = snapshot.expense,
                            privacyMode = privacyMode,
                        )
                        Spacer(Modifier.height(Space.l))
                    }
                }

                // Executive Cash Flow & Velocity Card
                item {
                    ExecutiveCashFlowCard(
                        snapshot = snapshot,
                        window = window,
                        windowDays = windowDays,
                        change = change,
                        privacyMode = privacyMode,
                    )
                }

                // Bank Fees & Friction Card
                if (bankFeeSummary.totalFeeToman > 0L || bankFeeSummary.feeCount > 0) {
                    item {
                        Spacer(Modifier.height(Space.l))
                        BankFeesCard(summary = bankFeeSummary, privacyMode = privacyMode)
                    }
                }

                // Spending Time Heatmap Card
                if (heatmapInsight.peakSpendToman > 0L) {
                    item {
                        Spacer(Modifier.height(Space.l))
                        SpendingHeatmapCard(insight = heatmapInsight, privacyMode = privacyMode)
                    }
                }

                // Wealth Defense Index gauge card
                item {
                    Spacer(Modifier.height(Space.l))
                    WealthDefenseGaugeCard(unhedgedToman = unhedgedToman, hedgedToman = hedgedToman, privacyMode = privacyMode)
                }

                // Idle Cash Sweep Warning Card
                if (idleCashWarning.hasIdleCash) {
                    item {
                        Spacer(Modifier.height(Space.l))
                        IdleCashSweepCard(warning = idleCashWarning, privacyMode = privacyMode)
                    }
                }

                // 50/30/20 Structural Expense Classification Card
                if (budget503020.totalExpenseRial > 0L) {
                    item {
                        Spacer(Modifier.height(Space.l))
                        Budget503020Card(summary = budget503020, privacyMode = privacyMode)
                    }
                }

                // Micro-Leak Radar Card
                if (microLeakSummary.hasLeaks) {
                    item {
                        Spacer(Modifier.height(Space.l))
                        MicroLeakCard(leakSummary = microLeakSummary, privacyMode = privacyMode)
                    }
                }

                // Interactive Category Donut Chart with Slice Selection
                if (snapshot.catSpend.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(Space.l))
                        CategoryDonutCard(
                            catSpend = snapshot.catSpend,
                            categories = ledger.categories,
                            totalExpenseRial = snapshot.expense,
                            privacyMode = privacyMode,
                            onSelectCategory = onSelectCategory,
                        )
                    }
                }

                // Reverse Opportunity Cost Simulator Card
                item {
                    Spacer(Modifier.height(Space.l))
                    OpportunityCostSimulatorCard(checkingCashToman = unhedgedToman)
                }

                // Asset Allocation Donut chart
                if (composition.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(Space.l))
                        AssetAllocationDonutCard(composition = composition, totalToman = current, privacyMode = privacyMode)
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
                        CategorySpendRow(
                            nameFa = catFa,
                            amount = amount,
                            totalExpense = snapshot.expense,
                            isUncategorized = isUncategorized,
                            privacyMode = privacyMode,
                            onClick = { onSelectCategory(catId) },
                        )
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
                            ChangeFigure(change, window.fa, now, privacyMode = privacyMode)
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
                                                    if (privacyMode) "•••••• تومان" else "${faCompact(v, 3, pad = true)} تومان",
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
                                        ChartCaption("اکنون", current, privacyMode = privacyMode)
                                        Spacer(Modifier.weight(1f))
                                        ChartCaption("شروع", sorted.getValue(change.sinceDay), privacyMode = privacyMode)
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
                        CompositionBar(composition, privacyMode = privacyMode)
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
private fun ExecutiveCashFlowCard(
    snapshot: ReportSnapshot,
    window: ReportWindow,
    windowDays: Int,
    change: Change?,
    privacyMode: Boolean = false,
) {
    val dailyBurnToman = if (windowDays > 0) tomanOf(snapshot.expense) / windowDays else 0L

    val periodInflation = when (window) {
        ReportWindow.MONTH -> 3.75
        ReportWindow.THREE_MONTH -> 11.25
        ReportWindow.SIX_MONTH -> 22.5
        ReportWindow.YEAR -> 45.0
        ReportWindow.ALL -> 45.0
    }
    val nominalPct: Double? = change?.percent
    val realGrowth: Double? = nominalPct?.let { it - periodInflation }

    Surface(
        modifier = Modifier
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
                    "تراز مالی و جریان نقدینگی",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "${window.fa} (${faNumber(windowDays.toDouble())} روز)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = Space.s, vertical = 2.dp),
                )
            }

            Spacer(Modifier.height(Space.m))

            // 2x2 Financial Matrix
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                // Inflow
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(Space.m),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("درآمد / واریز", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("↑", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E9E5B))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (privacyMode) "••••••" else bidi(faCompact(tomanOf(snapshot.income))),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = ModamFigures,
                        color = Color(0xFF2E9E5B),
                    )
                    Text("تومان", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Outflow
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(Space.m),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("مخارج / هزینه", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("↓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (privacyMode) "••••••" else bidi(faCompact(tomanOf(snapshot.expense))),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = ModamFigures,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text("تومان", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(Space.s))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                // Net
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(Space.m),
                ) {
                    Text("تراز خالص دوره", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (privacyMode) "••••••" else bidi(faSignedCompact(tomanOf(snapshot.net), snapshot.net > 0)),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = ModamFigures,
                        color = if (snapshot.net >= 0) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        if (snapshot.net >= 0) "مازاد نقدی مثبت" else "کسری تراز نقدینگی",
                        fontSize = 10.sp,
                        color = if (snapshot.net >= 0) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error,
                    )
                }

                // Savings Rate
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(Space.m),
                ) {
                    Text("نرخ پس‌انداز", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "٪${faNumber(snapshot.savingsRate.toDouble())}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = ModamFigures,
                        color = if (snapshot.savingsRate >= 20) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        when {
                            snapshot.savingsRate >= 30 -> "سپر پس‌انداز قوی"
                            snapshot.savingsRate >= 15 -> "سطح مطلوب"
                            else -> "نیازمند صرفه‌جویی"
                        },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(Space.m))

            // Daily Burn Velocity
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                    .padding(horizontal = Space.m, vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔥", fontSize = 18.sp, modifier = Modifier.padding(end = Space.s))
                Column(Modifier.weight(1f)) {
                    Text(
                        "سرعت خرج‌کرد روزانه",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (privacyMode) "•••••• تومان در روز" else "${faRate(dailyBurnToman.toDouble())} تومان در روز",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModamFigures,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            // Real Inflation Return Insight
            if (realGrowth != null) {
                Spacer(Modifier.height(Space.s))
                val isAhead = realGrowth >= 0.0
                val accentColor = if (isAhead) Color(0xFF10B981) else Color(0xFFF59E0B)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.card))
                        .background(accentColor.copy(alpha = 0.12f))
                        .padding(horizontal = Space.m, vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (isAhead) "🛡️" else "⚠️", fontSize = 18.sp, modifier = Modifier.padding(end = Space.s))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "سنجش قدرت خرید حقیقی پس از کسر تورم",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                        )
                        Text(
                            text = if (isAhead) {
                                "رشد اسمی ٪${faDecimal(nominalPct, 1)} در برابر تورم ٪${faDecimal(periodInflation, 1)} (بازدهی خالص حقیقی: +٪${faDecimal(realGrowth, 1)})"
                            } else {
                                "تورم دوره ٪${faDecimal(periodInflation, 1)} بوده و بازدهی خالص ٪${faDecimal(kotlin.math.abs(realGrowth), 1)}- از تورم عقب مانده است."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
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
    privacyMode: Boolean = false,
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
                if (privacyMode) "••••••" else bidi(faCompact(tomanOf(amount))),
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
    privacyMode: Boolean = false,
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
                        if (privacyMode) "•••••• تومان" else "${faCompact(defense.unhedgedToman.toDouble())} تومان",
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
                        if (privacyMode) "•••••• ت / روز" else "${faCompact(defense.dailyBleedToman.toDouble())} ت / روز",
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
private fun IdleCashSweepCard(
    warning: IdleCashWarning,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.group))
            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(Radius.group)),
        color = Color(0xFFFEF3C7).copy(alpha = 0.35f),
    ) {
        Column(Modifier.padding(Space.xl)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("💡", fontSize = 18.sp)
                    Text(
                        "هشدار انتقال نقدینگی مازاد (Sweep)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(Radius.pill),
                    color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                ) {
                    Text(
                        "فرصت حفظ ارزش",
                        modifier = Modifier.padding(horizontal = Space.m, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB45309),
                    )
                }
            }

            Spacer(Modifier.height(Space.m))

            Text(
                warning.recommendationFa,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(Space.m))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "نقدینگی مازاد بر نیاز",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (privacyMode) "•••••• تومان" else "${faCompact(warning.excessCashToman.toDouble())} تومان",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModamFigures,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "زیان سالانه ناشی از تورم",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (privacyMode) "•••••• تومان" else "${faCompact(warning.annualOpportunityLossToman.toDouble())} تومان",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModamFigures,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun Budget503020Card(
    summary: BudgetRule503020Summary,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
) {
    val needColor = Color(0xFF3B82F6)
    val wantColor = Color(0xFFF97316)
    val savingsColor = Color(0xFF10B981)

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
                    "توزیع ساختاری مخارج (قانون ۵۰/۳۰/۲۰)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "هدف: ۵۰ / ۳۰ / ۲۰",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ModamFigures,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(Space.m))

            // Multi-segment horizontal ratio bar
            val total = summary.totalExpenseRial.toFloat().coerceAtLeast(1f)
            val needRatio = summary.needRial / total
            val wantRatio = summary.wantRial / total
            val savingsRatio = summary.savingsRial / total

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
            ) {
                if (needRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(needRatio)
                            .fillMaxHeight()
                            .background(needColor),
                    )
                }
                if (wantRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(wantRatio)
                            .fillMaxHeight()
                            .background(wantColor),
                    )
                }
                if (savingsRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(savingsRatio)
                            .fillMaxHeight()
                            .background(savingsColor),
                    )
                }
            }

            Spacer(Modifier.height(Space.l))

            // Three columns: Need, Want, Savings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Budget503020Column(
                    title = "نیازها (۵۰٪)",
                    amountToman = summary.needToman,
                    actualPercent = summary.needPercent,
                    color = needColor,
                    privacyMode = privacyMode,
                )
                Budget503020Column(
                    title = "خواسته‌ها (۳۰٪)",
                    amountToman = summary.wantToman,
                    actualPercent = summary.wantPercent,
                    color = wantColor,
                    privacyMode = privacyMode,
                )
                Budget503020Column(
                    title = "سرمایه (۲۰٪)",
                    amountToman = summary.savingsToman,
                    actualPercent = summary.savingsPercent,
                    color = savingsColor,
                    privacyMode = privacyMode,
                )
            }
        }
    }
}

@Composable
private fun Budget503020Column(
    title: String,
    amountToman: Long,
    actualPercent: Int,
    color: Color,
    privacyMode: Boolean = false,
) {
    Column {
        Row(
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
                title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "${faNumber(actualPercent.toDouble())}٪",
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            fontFamily = ModamFigures,
            color = color,
        )
        Text(
            if (privacyMode) "•••••• ت" else "${faCompact(amountToman.toDouble())} ت",
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = ModamFigures,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AssetAllocationDonutCard(
    composition: List<Pair<Kind, Double>>,
    totalToman: Double,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
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
                            text = if (privacyMode) "•••••• ت" else "${faCompact(amount)} ت",
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

/**
 * Visual card alerting the user to small invisible micro-leaks (transactions < 50k Tomans).
 */
@Composable
fun MicroLeakCard(
    leakSummary: MicroLeakSummary,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
) {
    if (!leakSummary.hasLeaks) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(Radius.card),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.l)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔍", fontSize = 18.sp, modifier = Modifier.padding(end = Space.s))
                    Column {
                        Text(
                            "رادار نشتی‌های ریز مالی",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "مبالغ زیر ۵۰ هزار تومان (اسنک، کارمزد، شارژ)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(Color(0xFFFFB74D).copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "${faNumber(leakSummary.leakCount.toDouble())} تراکنش خرد",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF57C00),
                    )
                }
            }

            Spacer(Modifier.height(Space.m))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(Space.m),
                ) {
                    Text(
                        "مجموع نشتی خرد",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (privacyMode) "•••••• ت" else "${faCompact(leakSummary.totalLeakToman.toDouble())} ت",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = ModamFigures,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(Space.m),
                ) {
                    Text(
                        "میانگین هر خرید خرد",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (privacyMode) "•••••• ت" else "${faNumber(leakSummary.averageAmountToman.toDouble())} ت",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = ModamFigures,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(Space.s))
            Text(
                leakSummary.recommendationFa,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * Interactive reverse opportunity cost simulator comparing cash holding against gold and Tether.
 */
@Composable
fun OpportunityCostSimulatorCard(
    modifier: Modifier = Modifier,
    checkingCashToman: Long = 0L,
) {
    var selectedMonths by remember { mutableStateOf(6) }
    var selectedAmountToman by remember {
        mutableStateOf(if (checkingCashToman > 1_000_000L) checkingCashToman else 10_000_000L)
    }

    val result = remember(selectedAmountToman, selectedMonths) {
        OpportunityCostSimulator.simulate(
            amountToman = selectedAmountToman,
            monthsAgo = selectedMonths,
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(Radius.card),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.l)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💡", fontSize = 18.sp, modifier = Modifier.padding(end = Space.s))
                    Column {
                        Text(
                            "شبیه‌ساز معکوس هزینه فرصت",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "مقایسه نگهداری ریال با طلا و تتر",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.m))

            // Horizon selection chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                listOf(3 to "۳ ماه پیش", 6 to "۶ ماه پیش", 12 to "۱ سال پیش").forEach { (months, label) ->
                    FilterChip(
                        selected = selectedMonths == months,
                        onClick = { selectedMonths = months },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }

            Spacer(Modifier.height(Space.s))

            // Amount selection chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                listOf(
                    5_000_000L to "۵ م ت",
                    10_000_000L to "۱۰ م ت",
                    25_000_000L to "۲۵ م ت",
                    50_000_000L to "۵۰ م ت",
                ).forEach { (amt, label) ->
                    FilterChip(
                        selected = selectedAmountToman == amt,
                        onClick = { selectedAmountToman = amt },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }

            Spacer(Modifier.height(Space.m))

            // Results comparison row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                // Gold Fund Result
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.card))
                        .background(Hero.gold.copy(alpha = 0.12f))
                        .border(1.dp, Hero.gold.copy(alpha = 0.3f), RoundedCornerShape(Radius.card))
                        .padding(Space.m),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🥇 صندوق طلا", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Hero.gold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${faCompact(result.goldValueToman.toDouble())} ت",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = ModamFigures,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "+${faNumber(result.goldGainPercent.toInt().toDouble())}٪ بازدهی",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2E9E5B),
                    )
                }

                // Tether Result
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.card))
                        .background(Hero.mint.copy(alpha = 0.12f))
                        .border(1.dp, Hero.mint.copy(alpha = 0.3f), RoundedCornerShape(Radius.card))
                        .padding(Space.m),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💵 تتر (دلار)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Hero.mint)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${faCompact(result.tetherValueToman.toDouble())} ت",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = ModamFigures,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "+${faNumber(result.tetherGainPercent.toInt().toDouble())}٪ بازدهی",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2E9E5B),
                    )
                }
            }

            Spacer(Modifier.height(Space.s))
            Text(
                result.narrativeFa,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * Visual card summarizing bank service charges, Satna/Paya fees, and friction costs.
 */
@Composable
private fun BankFeesCard(
    summary: BankFeeSummary,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
) {
    if (summary.totalFeeToman <= 0L && summary.feeCount <= 0) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.group))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.group)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Space.xl)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💳", fontSize = 18.sp, modifier = Modifier.padding(end = Space.s))
                    Column {
                        Text(
                            "کارمزدها و اصطکاک بانکی",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "انتقال پایا، ساتنا، کارت‌به‌کارت و آبونمان",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "${faNumber(summary.feeCount.toDouble())} تراکنش",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModamFigures,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(Space.m))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(Space.m),
                ) {
                    Text(
                        "مجموع کارمزد پرداختی",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (privacyMode) "•••••• ت" else "${faCompact(summary.totalFeeToman.toDouble())} ت",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = ModamFigures,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.card))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(Space.m),
                ) {
                    Text(
                        "سهم از کل مخارج",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${faDecimal(summary.feePercentageOfSpend, 1)}٪",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = ModamFigures,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.height(Space.s))

            Text(
                if (privacyMode) "در این بازه ${faNumber(summary.feeCount.toDouble())} مورد کارمزد و آبونمان بانکی ثبت شده است." else summary.narrativeFa,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * 2D Heatmap visual matrix plotting spending concentration across days of week and time-of-day slots.
 */
@Composable
private fun SpendingHeatmapCard(
    insight: HeatmapInsight,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
) {
    val maxSlotSpend = remember(insight) {
        insight.days.flatMap { it.slots.values }.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    }
    val slotOrder = listOf(TimeSlot.MORNING, TimeSlot.AFTERNOON, TimeSlot.EVENING, TimeSlot.NIGHT)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.group))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.group)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Space.xl)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏰", fontSize = 18.sp, modifier = Modifier.padding(end = Space.s))
                    Column {
                        Text(
                            "الگوی زمانی مخارج",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            "توزیع هزینه‌ها بر اساس روز و ساعت",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "${insight.peakDayNameFa} • ${insight.peakSlotTitleFa}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(Space.l))

            // 7 Columns (one for each Shamsi day) with 4 time slots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                insight.days.forEach { day ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                    ) {
                        Text(
                            day.nameFa.take(2), // e.g. شن، یک، دو، سه‌، چه‌، پن، جم
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (day.nameFa == insight.peakDayNameFa) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))

                        slotOrder.forEach { slot ->
                            val spend = day.slots[slot] ?: 0L
                            val ratio = (spend.toFloat() / maxSlotSpend.toFloat()).coerceIn(0f, 1f)
                            val cellAlpha = if (spend > 0L) (0.15f + ratio * 0.85f).coerceIn(0.15f, 1f) else 0.05f
                            val cellColor = if (spend > 0L) MaterialTheme.colorScheme.primary.copy(alpha = cellAlpha) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(18.dp)
                                    .padding(vertical = 1.5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(cellColor),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.s))

            // Time Slot legend below the heatmap
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                slotOrder.forEach { slot ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            slot.titleFa,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.m))

            // Peak Spend Metric Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.card))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .padding(horizontal = Space.m, vertical = Space.s),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "اوج خرج هفتگی (${insight.peakDayNameFa})",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (privacyMode) "•••••• ت" else "${faCompact(insight.peakSpendToman.toDouble())} ت",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ModamFigures,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(Space.s))

            Text(
                insight.summaryFa,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
    }
}

/**
 * Donut chart showing breakdown of expenses by category with interactive selection.
 */
@Composable
private fun CategoryDonutCard(
    catSpend: List<Pair<String?, Long>>,
    categories: List<Category>,
    totalExpenseRial: Long,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onSelectCategory: (String?) -> Unit = {},
) {
    if (catSpend.isEmpty() || totalExpenseRial <= 0L) return

    val categoryColors = listOf(
        Color(0xFF3B82F6), // Blue
        Color(0xFFF97316), // Orange
        Color(0xFF10B981), // Emerald
        Color(0xFF8B5CF6), // Purple
        Color(0xFFEC4899), // Pink
        Color(0xFFF59E0B), // Amber
        Color(0xFF06B6D4), // Cyan
        Color(0xFF84CC16), // Lime
        Color(0xFF64748B), // Slate
    )

    var selectedCatId by remember { mutableStateOf<String?>(null) }
    val activeItem = catSpend.find { it.first == selectedCatId } ?: catSpend.firstOrNull()
    val activeCat = categories.find { it.id == activeItem?.first }
    val activeCatName = activeCat?.nameFa ?: if (activeItem?.first == CAT_UNCATEGORISED) "دسته‌بندی‌نشده" else "سایر"
    val activeAmountRial = activeItem?.second ?: 0L
    val activeAmountToman = activeAmountRial / 10L
    val activePct = if (totalExpenseRial > 0L) ((activeAmountRial.toDouble() / totalExpenseRial.toDouble()) * 100.0).toInt() else 0

    val activeIndex = catSpend.indexOfFirst { it.first == activeItem?.first }.coerceAtLeast(0)
    val activeColor = categoryColors[activeIndex % categoryColors.size]

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.group))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.group)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(Space.xl)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "ترکیب دسته‌بندی مخارج",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "مجموع: " + (if (privacyMode) "•••••• ت" else "${faCompact((totalExpenseRial / 10L).toDouble())} ت"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ModamFigures,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

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
                    catSpend.forEachIndexed { index, (catId, amountRial) ->
                        val sweep = ((amountRial.toDouble() / totalExpenseRial.toDouble()) * 360f).toFloat()
                        val color = categoryColors[index % categoryColors.size]
                        val isSelected = catId == activeItem?.first

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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = activeCatName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${faNumber(activePct.toDouble())}٪",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = ModamFigures,
                        color = activeColor,
                    )
                    Text(
                        text = if (privacyMode) "•••••• ت" else "${faCompact(activeAmountToman.toDouble())} ت",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = ModamFigures,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            Spacer(Modifier.height(Space.l))

            // Legend chips for top categories
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                itemsIndexed(catSpend.take(6)) { index, (catId, amountRial) ->
                    val cat = categories.find { it.id == catId }
                    val name = cat?.nameFa ?: if (catId == CAT_UNCATEGORISED) "دسته‌بندی‌نشده" else "سایر"
                    val color = categoryColors[index % categoryColors.size]
                    val pct = if (totalExpenseRial > 0L) ((amountRial.toDouble() / totalExpenseRial.toDouble()) * 100.0).toInt() else 0
                    val isSelected = catId == activeItem?.first

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .clickable {
                                if (selectedCatId == catId) {
                                    onSelectCategory(catId)
                                } else {
                                    selectedCatId = catId
                                }
                            }
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
                            text = "$name $pct٪",
                            fontSize = 11.sp,
                            fontFamily = ModamFigures,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Interactive Drill-Down Action
            if (activeCat != null || activeItem != null) {
                Spacer(Modifier.height(Space.m))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .clickable { onSelectCategory(activeItem?.first) }
                        .padding(horizontal = Space.m, vertical = Space.s),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "مشاهده جزئیات و تراکنش‌های $activeCatName در دفتر کل",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Hero.gold,
                        )
                        Text(
                            text = "کاوش >",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Hero.gold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Monzo Trends-inspired Payday Burn-Down Trajectory Card (Spec §8.3).
 * Visualizes current expenditure velocity against ideal linear budget and remaining cycle days.
 */
@Composable
private fun PaydayBurnDownCard(
    entries: List<LedgerEntry>,
    today: Long,
    cycle: CycleSpec,
    totalIncomeRial: Long,
    totalExpenseRial: Long,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
) {
    val window = remember(today, cycle) { cycleWindow(today, cycle) }
    val totalDays = (window.endDay - window.startDay).toInt().coerceAtLeast(1)
    val elapsedDays = (today - window.startDay + 1).toInt().coerceIn(1, totalDays)
    val remainingDays = (window.endDay - today).toInt().coerceAtLeast(1)

    val spentToman = (totalExpenseRial / 10L).toDouble()
    val incomeToman = (totalIncomeRial / 10L).toDouble()
    val estimatedBudgetToman = if (incomeToman > 0) incomeToman else (spentToman * 1.3).coerceAtLeast(10_000_000.0)
    val remainingBudgetToman = (estimatedBudgetToman - spentToman).coerceAtLeast(0.0)

    val safeDailySpendToman = if (remainingDays > 0) (remainingBudgetToman / remainingDays).toLong() else 0L

    val idealProgress = elapsedDays.toFloat() / totalDays.toFloat()
    val actualBurnProgress = if (estimatedBudgetToman > 0) (spentToman / estimatedBudgetToman).toFloat().coerceIn(0f, 1.5f) else 0f

    val isOverPacing = actualBurnProgress > idealProgress * 1.15f
    val paceColor = if (isOverPacing) Color(0xFFEF4444) else Color(0xFF10B981)
    val paceLabel = if (isOverPacing) "🔴 سرعت مصرف بالا (خطر کسری)" else "🟢 در محدوده بودجه امن"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.group))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.group)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(Space.xl)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📉", fontSize = 16.sp)
                    Spacer(Modifier.width(Space.s))
                    Text(
                        "منحنی پیش‌بینی مصرف (Burn-Down)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(paceColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = paceLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = paceColor,
                    )
                }
            }

            Spacer(Modifier.height(Space.m))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("خرج تا امروز", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (privacyMode) "•••••• ت" else "${faCompact(spentToman)} ت",
                        fontSize = 16.sp,
                        fontFamily = ModamFigures,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("سقف امن روزانه", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (privacyMode) "•••••• ت" else "${fa(safeDailySpendToman)} ت",
                        fontSize = 16.sp,
                        fontFamily = ModamFigures,
                        fontWeight = FontWeight.Black,
                        color = Hero.mint,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("روزهای باقی‌مانده", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${fa(remainingDays)} روز",
                        fontSize = 16.sp,
                        fontFamily = ModamFigures,
                        fontWeight = FontWeight.Black,
                        color = Hero.gold,
                    )
                }
            }

            Spacer(Modifier.height(Space.l))

            // Comparative Burn-down progress track
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "مسیر زمانی سیکل (${fa(elapsedDays)} از ${fa(totalDays)} روز)",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${(idealProgress * 100).toInt()}٪ زمان",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(idealProgress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(Color(0xFF64748B)),
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "میزان مصرف بودجه",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${(actualBurnProgress * 100).toInt()}٪ مصرف",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = paceColor,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(actualBurnProgress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(paceColor),
                    )
                }
            }
        }
    }
}
