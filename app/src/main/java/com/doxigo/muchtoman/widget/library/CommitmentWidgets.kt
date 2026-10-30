package com.doxigo.muchtoman.widget.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.ZhetonTheme
import com.doxigo.muchtoman.Commitment
import com.doxigo.muchtoman.Goal
import com.doxigo.muchtoman.HairlineDivider
import com.doxigo.muchtoman.LedgerEntry
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.TABULAR
import com.doxigo.muchtoman.fa
import com.doxigo.muchtoman.faCompact
import com.doxigo.muchtoman.tomanOf
import com.doxigo.muchtoman.widget.primitives.DotProgressBar
import com.doxigo.muchtoman.widget.primitives.EditorialSection
import com.doxigo.muchtoman.widget.primitives.FinancialTimeline
import com.doxigo.muchtoman.widget.primitives.FinancialValue
import com.doxigo.muchtoman.widget.primitives.FinancialValueSize
import com.doxigo.muchtoman.widget.primitives.TransactionRow

/**
 * 08 — INSTALLMENTS & LIABILITIES
 */
@Composable
fun InstallmentsWidget(
    commitments: List<Commitment>,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val active = commitments.filter { !it.isSettled }

    EditorialSection(
        title = "اقساط و تعهدات",
        technicalLabel = if (active.isNotEmpty()) "${fa(active.size)} COMMITMENTS" else "ALL SETTLED",
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        if (active.isEmpty()) {
            Text(
                text = "هیچ تعهد سررسیدنشده‌ای ثبت نشده است.",
                style = TextStyle(
                    fontFamily = Modam,
                    fontSize = 13.sp,
                    color = ZhetonTheme.colors.textMuted
                )
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                active.take(2).forEach { item ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.provider} • ${item.title}",
                                style = TextStyle(
                                    fontFamily = Modam,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ZhetonTheme.colors.textPrimary
                                )
                            )
                            Text(
                                text = "${fa(item.paidInstallmentCount)} / ${fa(item.installmentCount)}",
                                style = TextStyle(
                                    fontFamily = ModamFigures,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFeatureSettings = TABULAR,
                                    color = ZhetonTheme.colors.accentLime
                                )
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            FinancialValue(
                                amount = faCompact(item.remainingAmountToman.toDouble()),
                                size = FinancialValueSize.COMPACT,
                                privacyMode = privacyMode
                            )
                            if (item.nextDueDays > 0) {
                                Text(
                                    text = "${fa(item.nextDueDays)} روز مانده",
                                    style = TextStyle(
                                        fontFamily = Modam,
                                        fontSize = 12.sp,
                                        color = ZhetonTheme.colors.textSecondary
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        FinancialTimeline(
                            totalSteps = item.installmentCount.coerceAtLeast(2),
                            currentStep = item.paidInstallmentCount
                        )
                    }
                }
            }
        }
    }
}

/**
 * 09 — GOALS
 */
@Composable
fun GoalsWidget(
    goals: List<Goal>,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    EditorialSection(
        title = "اهداف مالی",
        technicalLabel = "SAVINGS TARGETS",
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        if (goals.isEmpty()) {
            Text(
                text = "هنوز هدفی برای پس‌انداز تعریف نشده است.",
                style = TextStyle(
                    fontFamily = Modam,
                    fontSize = 13.sp,
                    color = ZhetonTheme.colors.textMuted
                )
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                goals.take(2).forEach { goal ->
                    val targetToman = goal.targetRial / 10.0
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = goal.nameFa,
                                style = TextStyle(
                                    fontFamily = Modam,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ZhetonTheme.colors.textPrimary
                                )
                            )
                            FinancialValue(
                                amount = faCompact(targetToman),
                                size = FinancialValueSize.COMPACT,
                                privacyMode = privacyMode
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        DotProgressBar(
                            progress = 0.65f,
                            activeColor = ZhetonTheme.colors.accentLime
                        )
                    }
                }
            }
        }
    }
}

/**
 * 12 — UPCOMING BILLS
 */
@Composable
fun BillsWidget(
    commitments: List<Commitment>,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val dueSoon = commitments.filter { !it.isSettled }

    EditorialSection(
        title = "پرداخت‌های پیش‌رو",
        technicalLabel = "SCHEDULED BILLS",
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        if (dueSoon.isEmpty()) {
            Text(
                text = "هیچ چک سررسید شده یا بدهی معوقی وجود ندارد.",
                style = TextStyle(
                    fontFamily = Modam,
                    fontSize = 13.sp,
                    color = ZhetonTheme.colors.textMuted
                )
            )
        } else {
            Column {
                dueSoon.take(3).forEachIndexed { index, bill ->
                    if (index > 0) {
                        HairlineDivider(color = ZhetonTheme.colors.border)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = bill.title,
                                style = TextStyle(
                                    fontFamily = Modam,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = ZhetonTheme.colors.textPrimary
                                )
                            )
                            Text(
                                text = bill.provider,
                                style = TextStyle(
                                    fontFamily = Modam,
                                    fontSize = 11.sp,
                                    color = ZhetonTheme.colors.textMuted
                                )
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            FinancialValue(
                                amount = faCompact(bill.remainingAmountToman.toDouble()),
                                size = FinancialValueSize.COMPACT,
                                privacyMode = privacyMode
                            )
                            Text(
                                text = "${fa(bill.nextDueDays)} روز مانده",
                                style = TextStyle(
                                    fontFamily = Modam,
                                    fontSize = 11.sp,
                                    color = if (bill.nextDueDays <= 3) ZhetonTheme.colors.danger else ZhetonTheme.colors.textMuted
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 13 — TRANSACTION STREAM
 */
@Composable
fun TransactionsWidget(
    entries: List<LedgerEntry>,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null,
    onEntryClick: ((LedgerEntry) -> Unit)? = null,
) {
    EditorialSection(
        title = "آخرین تراکنش‌ها",
        technicalLabel = "TRANSACTION STREAM",
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        if (entries.isEmpty()) {
            Text(
                text = "هنوز تراکنشی در دفتر ثبت نشده است.",
                style = TextStyle(
                    fontFamily = Modam,
                    fontSize = 13.sp,
                    color = ZhetonTheme.colors.textMuted
                )
            )
        } else {
            Column {
                entries.take(4).forEachIndexed { index, entry ->
                    if (index > 0) {
                        HairlineDivider(color = ZhetonTheme.colors.border)
                    }
                    val amountRial = entry.txn.amountRial ?: 0L
                    val isExpense = amountRial < 0
                    val absRial = Math.abs(amountRial)
                    TransactionRow(
                        merchant = entry.txn.merchant ?: "تراکنش",
                        category = entry.categoryFa.ifBlank { "عمومی" },
                        amount = if (privacyMode) "••••••••" else faCompact(tomanOf(absRial)),
                        time = "امروز",
                        isExpense = isExpense,
                        accountName = entry.txn.bank,
                        onClick = if (onEntryClick != null) { { onEntryClick(entry) } } else null
                    )
                }
            }
        }
    }
}
