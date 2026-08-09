package com.doxigo.muchtoman

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The reports tab: live month-to-date financial snapshot.
 *
 * Every number covers the whole stored ledger (no 300-row cap). Expense-only denominator
 * for category shares: income is shown but does not dilute the category percentages.
 * Hidden, duplicate, and transfer rows are excluded.
 */
@Composable
fun ReportsTab(
    ledger: LedgerView,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    val today = remember { tehranDay(System.currentTimeMillis()) }
    val monthStart = remember(today) { jalaliMonthStart(today) }

    val mtd = remember(ledger.entries) {
        val visible = ledger.entries.filter { !it.duplicate && !it.hidden && !it.transfer && it.txn.day >= monthStart }
        val income = visible.filter { it.txn.direction == "in" }.sumOf { it.txn.signedRial ?: 0L }
        val expense = visible.filter { it.txn.direction == "out" }.sumOf { kotlin.math.abs(it.txn.signedRial ?: 0L) }
        val net = income - expense
        val savingsRate = if (income > 0) ((income - expense).toDouble() / income * 100).toInt().coerceIn(0, 100) else 0

        // Category shares — expense-only denominator
        val catSpend = visible
            .filter { it.txn.direction == "out" }
            .groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.sumOf { kotlin.math.abs(it.txn.signedRial ?: 0L) } }
            .toList()
            .sortedByDescending { it.second }

        MtdSnapshot(income, expense, net, savingsRate, catSpend)
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

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = Space.xl, vertical = Space.s,
                ),
            ) {
                // Month-to-date summary card
                item {
                    MtdSummaryCard(mtd)
                }

                // Per-category spend list
                if (mtd.catSpend.isNotEmpty()) {
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
                    items(mtd.catSpend, key = { it.first ?: "uncategorized" }) { (catId, amount) ->
                        val catFa = ledger.categories.find { it.id == catId }?.nameFa ?: "دسته‌بندی نشده"
                        val isUncategorized = catId == null
                        CategorySpendRow(catFa, amount, mtd.expense, isUncategorized)
                    }
                }
            }
        }
    }
}

private data class MtdSnapshot(
    val income: Long,
    val expense: Long,
    val net: Long,
    val savingsRate: Int,
    val catSpend: List<Pair<String?, Long>>,
)

@Composable
private fun MtdSummaryCard(mtd: MtdSnapshot) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.group)),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(Space.xl)) {
            Text(
                "این ماه",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("درآمد", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        bidi(faCompact(tomanOf(mtd.income))),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E9E5B),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("هزینه", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        bidi(faCompact(tomanOf(mtd.expense))),
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
                        bidi(faSignedCompact(tomanOf(mtd.net), mtd.net > 0)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (mtd.net >= 0) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.error,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("نرخ پس‌انداز", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${mtd.savingsRate}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (mtd.savingsRate >= 20) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.onBackground,
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
