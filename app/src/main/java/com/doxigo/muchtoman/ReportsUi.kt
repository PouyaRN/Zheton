package com.doxigo.muchtoman

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    val today = remember { tehranDay(System.currentTimeMillis()) }
    var window by remember { mutableStateOf(ReportWindow.MONTH) }

    // Compute the window start date
    val windowStart = remember(today, window) {
        when (window) {
            ReportWindow.MONTH -> jalaliMonthStart(today)
            ReportWindow.THREE_MONTH -> jalaliMonthsBack(today, 3)
            ReportWindow.SIX_MONTH -> jalaliMonthsBack(today, 6)
            ReportWindow.YEAR -> jalaliMonthsBack(today, 12)
            ReportWindow.ALL -> ledger.entries.minOfOrNull { it.txn.day } ?: today
        }
    }

    val snapshot = remember(ledger.entries, windowStart) {
        val visible = ledger.entries.filter { !it.duplicate && !it.hidden && !it.transfer && it.txn.day >= windowStart }
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
    val now = today
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
                        CategorySpendRow(catFa, amount, snapshot.expense, isUncategorized)
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
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Radius.group))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(Space.l),
                            ) {
                                Column {
                                    HistoryChart(
                                        points,
                                        tone = changeTone(change),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp),
                                    )
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
            .clip(RoundedCornerShape(Radius.group)),
        color = MaterialTheme.colorScheme.surfaceVariant,
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
private fun CategorySpendRow(nameFa: String, amount: Long, totalExpense: Long, isUncategorized: Boolean) {
    val pct = if (totalExpense > 0) (amount.toDouble() / totalExpense * 100).toInt() else 0
    Row(
        Modifier
            .fillMaxWidth()
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
