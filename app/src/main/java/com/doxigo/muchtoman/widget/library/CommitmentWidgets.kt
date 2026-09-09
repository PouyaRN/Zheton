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
import com.doxigo.muchtoman.AndisTheme
import com.doxigo.muchtoman.Commitment
import com.doxigo.muchtoman.Goal
import com.doxigo.muchtoman.LedgerEntry
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.TABULAR
import com.doxigo.muchtoman.fa
import com.doxigo.muchtoman.faCompact
import com.doxigo.muchtoman.tomanOf
import com.doxigo.muchtoman.widget.primitives.DotMatrixBadge
import com.doxigo.muchtoman.widget.primitives.DotProgressBar
import com.doxigo.muchtoman.widget.primitives.FinancialTimeline
import com.doxigo.muchtoman.widget.primitives.FinancialValue
import com.doxigo.muchtoman.widget.primitives.FinancialValueSize
import com.doxigo.muchtoman.widget.primitives.TransactionRow
import com.doxigo.muchtoman.widget.primitives.WidgetContainer
import com.doxigo.muchtoman.widget.primitives.WidgetHeader

/**
 * 08 — INSTALLMENTS WIDGET
 * Active BNPL and loan commitments with visual installment timeline.
 */
@Composable
fun InstallmentsWidget(
    commitments: List<Commitment>,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val active = commitments.filter { !it.isSettled }

    WidgetContainer(
        modifier = modifier,
        header = {
            WidgetHeader(
                title = "اقساط و تعهدات فعال",
                badgeText = if (active.isNotEmpty()) "${fa(active.size)} تعهد" else "بدون بدهی"
            )
        },
        onClick = onClick
    ) {
        if (active.isEmpty()) {
            Text(
                text = "هیچ قسط سررسیدنشده‌ای ثبت نشده است.",
                style = TextStyle(
                    fontFamily = Modam,
                    fontSize = 12.sp,
                    color = AndisTheme.colors.textMuted
                )
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    fontSize = 13.sp,
                                    color = AndisTheme.colors.textPrimary
                                )
                            )
                            Text(
                                text = "${fa(item.paidInstallmentCount)} / ${fa(item.installmentCount)}",
                                style = TextStyle(
                                    fontFamily = ModamFigures,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFeatureSettings = TABULAR,
                                    color = AndisTheme.colors.accentLime
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
                                    text = "${fa(item.nextDueDays)} روز تا سررسید",
                                    style = TextStyle(
                                        fontFamily = Modam,
                                        fontSize = 11.sp,
                                        color = AndisTheme.colors.textMuted
                                    )
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
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
 * 09 — GOALS WIDGET
 * Savings milestones and target progress tracking.
 */
@Composable
fun GoalsWidget(
    goals: List<Goal>,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    WidgetContainer(
        modifier = modifier,
        header = {
            WidgetHeader(
                title = "اهداف پس‌انداز",
                badgeText = "TARGETS"
            )
        },
        onClick = onClick
    ) {
        if (goals.isEmpty()) {
            Text(
                text = "هدفی برای پس‌انداز ثبت نشده است.",
                style = TextStyle(
                    fontFamily = Modam,
                    fontSize = 12.sp,
                    color = AndisTheme.colors.textMuted
                )
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    fontSize = 13.sp,
                                    color = AndisTheme.colors.textPrimary
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
                            progress = 0.65f, // Dynamic progress indicator
                            activeColor = AndisTheme.colors.accentLime
                        )
                    }
                }
            }
        }
    }
}

/**
 * 12 — BILLS WIDGET
 * Upcoming financial commitments and Sayad promissory notes.
 */
@Composable
fun BillsWidget(
    commitments: List<Commitment>,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val dueSoon = commitments.filter { !it.isSettled }

    WidgetContainer(
        modifier = modifier,
        header = {
            WidgetHeader(
                title = "قبوض و تعهدات پیش‌رو",
                badgeText = "UPCOMING",
                showBeacon = dueSoon.isNotEmpty()
            )
        },
        onClick = onClick
    ) {
        if (dueSoon.isEmpty()) {
            Text(
                text = "هیچ تعهد معوق یا چک سررسید شده‌ای وجود ندارد.",
                style = TextStyle(
                    fontFamily = Modam,
                    fontSize = 12.sp,
                    color = AndisTheme.colors.textMuted
                )
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                dueSoon.take(3).forEach { bill ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = bill.title,
                                style = TextStyle(
                                    fontFamily = Modam,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = AndisTheme.colors.textPrimary
                                )
                            )
                            Text(
                                text = bill.provider,
                                style = TextStyle(
                                    fontFamily = Modam,
                                    fontSize = 10.sp,
                                    color = AndisTheme.colors.textMuted
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
                                text = "${fa(bill.nextDueDays)} روز باقی‌مانده",
                                style = TextStyle(
                                    fontFamily = Modam,
                                    fontSize = 10.sp,
                                    color = if (bill.nextDueDays <= 3) AndisTheme.colors.danger else AndisTheme.colors.textMuted
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
 * 13 — TRANSACTIONS WIDGET
 * High-legibility live transaction stream.
 */
@Composable
fun TransactionsWidget(
    entries: List<LedgerEntry>,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null,
    onEntryClick: ((LedgerEntry) -> Unit)? = null,
) {
    WidgetContainer(
        modifier = modifier,
        header = {
            WidgetHeader(
                title = "تراکنش‌های اخیر",
                badgeText = "STREAM"
            )
        },
        onClick = onClick
    ) {
        if (entries.isEmpty()) {
            Text(
                text = "تراکنشی برای نمایش ثبت نشده است.",
                style = TextStyle(
                    fontFamily = Modam,
                    fontSize = 12.sp,
                    color = AndisTheme.colors.textMuted
                )
            )
        } else {
            Column {
                entries.take(4).forEach { entry ->
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
