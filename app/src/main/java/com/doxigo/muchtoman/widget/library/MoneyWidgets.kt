package com.doxigo.muchtoman.widget.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.AndisTheme
import com.doxigo.muchtoman.BankAccount
import com.doxigo.muchtoman.HomeStory
import com.doxigo.muchtoman.Kind
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.TABULAR
import com.doxigo.muchtoman.UiState
import com.doxigo.muchtoman.compositionByKind
import com.doxigo.muchtoman.fa
import com.doxigo.muchtoman.faCompact
import com.doxigo.muchtoman.formatCardPanLtr
import com.doxigo.muchtoman.tomanOf
import com.doxigo.muchtoman.widget.primitives.AccountRow
import com.doxigo.muchtoman.widget.primitives.DotMatrixBadge
import com.doxigo.muchtoman.widget.primitives.DotProgressBar
import com.doxigo.muchtoman.widget.primitives.FinancialDelta
import com.doxigo.muchtoman.widget.primitives.FinancialValue
import com.doxigo.muchtoman.widget.primitives.FinancialValueSize
import com.doxigo.muchtoman.widget.primitives.Metric
import com.doxigo.muchtoman.widget.primitives.SegmentedProgress
import com.doxigo.muchtoman.widget.primitives.WidgetContainer
import com.doxigo.muchtoman.widget.primitives.WidgetDensity
import com.doxigo.muchtoman.widget.primitives.WidgetHeader

/**
 * 03 — BANK CARD WIDGET
 * Financial instrument card with masked PAN, balance, and quick copy action.
 */
@Composable
fun BankCardWidget(
    account: BankAccount,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onCopyPan: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val panDisplay = if (privacyMode) "••••  ••••  ••••  ••••" else formatCardPanLtr(account.mask)
    val balanceToman = account.balance

    WidgetContainer(
        modifier = modifier,
        header = {
            WidgetHeader(
                title = account.bankFa,
                badgeText = "CARD INSTRUMENT"
            )
        },
        onClick = onClick
    ) {
        Column {
            Text(
                text = panDisplay,
                style = TextStyle(
                    fontFamily = ModamFigures,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    fontFeatureSettings = TABULAR,
                    color = AndisTheme.colors.textSecondary,
                    textDirection = TextDirection.Ltr
                ),
                modifier = Modifier.then(
                    if (onCopyPan != null) Modifier.clickable(onClick = onCopyPan) else Modifier
                )
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                FinancialValue(
                    amount = fa(Math.round(balanceToman)),
                    size = FinancialValueSize.MEDIUM,
                    privacyMode = privacyMode
                )
                if (account.sheba.isNotBlank()) {
                    DotMatrixBadge(
                        text = "SHEBA",
                        activeColor = AndisTheme.colors.textSecondary,
                        surfaceColor = AndisTheme.colors.surfaceSecondary
                    )
                }
            }
        }
    }
}

/**
 * 04 — ACCOUNTS WIDGET
 * Breakdown of Bank, Cash, USD, Gold, and Crypto holdings with allocation.
 */
@Composable
fun AccountsWidget(
    state: UiState,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val total = state.totals.toman.coerceAtLeast(1.0)
    val composition = remember(state.holdings, state.coins, state.effective, state.stocks) {
        compositionByKind(state.holdings, state.coins, state.effective, state.stocks)
    }

    WidgetContainer(
        modifier = modifier,
        header = {
            WidgetHeader(
                title = "ترکیب حساب‌ها و دارایی",
                badgeText = "ALLOCATION"
            )
        },
        onClick = onClick
    ) {
        Column {
            AccountRow(
                name = "حساب‌های بانکی",
                balance = faCompact(state.bankToman),
                allocationPercent = ((state.bankToman / total) * 100.0).coerceIn(0.0, 100.0),
                indicatorColor = AndisTheme.colors.accentLime
            )
            for ((kind, amount) in composition.take(3)) {
                val color = when (kind) {
                    Kind.GOLD, Kind.SILVER, Kind.COIN -> Color(0xFFFFD700)
                    Kind.FIAT -> Color(0xFF38BDF8)
                    Kind.CRYPTO -> Color(0xFFA78BFA)
                    else -> AndisTheme.colors.accentLime
                }
                AccountRow(
                    name = kind.fa,
                    balance = faCompact(amount),
                    allocationPercent = ((amount / total) * 100.0).coerceIn(0.0, 100.0),
                    indicatorColor = color
                )
            }
        }
    }
}

/**
 * 05 — SPENDING WIDGET
 * Monthly cash outflow, income comparison, and burn trajectory.
 */
@Composable
fun SpendingWidget(
    story: HomeStory,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val month = story.month
    val spentToman = tomanOf(month.spentRial)
    val incomeToman = tomanOf(month.incomeRial)

    WidgetContainer(
        modifier = modifier,
        header = {
            WidgetHeader(
                title = "گردش مالی ماه",
                badgeText = "MONTH FLOW"
            )
        },
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "مجموع مخارج ماه",
                    style = TextStyle(
                        fontFamily = Modam,
                        fontSize = 11.sp,
                        color = AndisTheme.colors.textMuted
                    )
                )
                Spacer(Modifier.height(2.dp))
                FinancialValue(
                    amount = faCompact(spentToman),
                    size = FinancialValueSize.LARGE,
                    privacyMode = privacyMode
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "درآمد ماه",
                    style = TextStyle(
                        fontFamily = Modam,
                        fontSize = 11.sp,
                        color = AndisTheme.colors.textMuted
                    )
                )
                Spacer(Modifier.height(2.dp))
                FinancialValue(
                    amount = faCompact(incomeToman),
                    size = FinancialValueSize.MEDIUM,
                    color = AndisTheme.colors.accentLime,
                    privacyMode = privacyMode
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val totalFlow = (spentToman + incomeToman).coerceAtLeast(1.0)
        val progress = (spentToman / totalFlow).toFloat()
        DotProgressBar(
            progress = progress,
            activeColor = if (spentToman > incomeToman) AndisTheme.colors.danger else AndisTheme.colors.accentLime
        )
    }
}

/**
 * 06 — DAILY BURN WIDGET
 * Daily burn rate and runway indicator.
 */
@Composable
fun DailyBurnWidget(
    story: HomeStory,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val dailySpent = tomanOf(story.month.spentRial / 30L)
    val runwayDays = story.bufferDays ?: 30

    WidgetContainer(
        modifier = modifier,
        header = {
            WidgetHeader(
                title = "نرخ سوخت روزانه",
                badgeText = "DAILY BURN"
            )
        },
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Metric(
                label = "میانگین خرج روزانه",
                value = faCompact(dailySpent),
                unit = "تومان / روز"
            )
            Metric(
                label = "تاب‌آوری نقدینگی",
                value = "${fa(runwayDays)}",
                unit = "روز باقی‌مانده"
            )
        }
        Spacer(Modifier.height(10.dp))
        SegmentedProgress(
            segments = 30,
            filledSegments = runwayDays.coerceIn(0, 30),
            activeColor = if (runwayDays < 10) AndisTheme.colors.danger else AndisTheme.colors.accentLime
        )
    }
}

/**
 * 07 — FINANCIAL HEALTH WIDGET
 * Segmented 0-100 financial operating score inspired by Nothing OS.
 */
@Composable
fun FinancialHealthWidget(
    score: Int = 84,
    statusText: String = "وضعیت پایدار",
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    WidgetContainer(
        modifier = modifier,
        header = {
            WidgetHeader(
                title = "شاخص سلامت مالی",
                badgeText = "INDEX: $score/100",
                showBeacon = true
            )
        },
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$score",
                    style = TextStyle(
                        fontFamily = ModamFigures,
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp,
                        fontFeatureSettings = TABULAR,
                        color = AndisTheme.colors.accentLime
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = statusText,
                    style = TextStyle(
                        fontFamily = Modam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = AndisTheme.colors.textPrimary
                    ),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            DotMatrixBadge(
                text = "GRADE A",
                activeColor = AndisTheme.colors.accentLime
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("پس‌انداز", style = TextStyle(fontFamily = Modam, fontSize = 10.sp, color = AndisTheme.colors.textMuted))
                Spacer(Modifier.height(3.dp))
                SegmentedProgress(segments = 5, filledSegments = 4, activeColor = AndisTheme.colors.accentLime)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("نقدینگی", style = TextStyle(fontFamily = Modam, fontSize = 10.sp, color = AndisTheme.colors.textMuted))
                Spacer(Modifier.height(3.dp))
                SegmentedProgress(segments = 5, filledSegments = 5, activeColor = AndisTheme.colors.accentLime)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("بدهی", style = TextStyle(fontFamily = Modam, fontSize = 10.sp, color = AndisTheme.colors.textMuted))
                Spacer(Modifier.height(3.dp))
                SegmentedProgress(segments = 5, filledSegments = 2, activeColor = AndisTheme.colors.warning)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("انضباط", style = TextStyle(fontFamily = Modam, fontSize = 10.sp, color = AndisTheme.colors.textMuted))
                Spacer(Modifier.height(3.dp))
                SegmentedProgress(segments = 5, filledSegments = 4, activeColor = AndisTheme.colors.accentLime)
            }
        }
    }
}

/**
 * 14 — BUDGET WIDGET
 * 50/30/20 Rule budget envelope indicator.
 */
@Composable
fun BudgetWidget(
    story: HomeStory,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    WidgetContainer(
        modifier = modifier,
        header = {
            WidgetHeader(
                title = "بودجه ۵۰ / ۳۰ / ۲۰",
                badgeText = "ENVELOPE"
            )
        },
        onClick = onClick
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("نیازهای اساسی (۵۰٪)", style = TextStyle(fontFamily = Modam, fontSize = 11.sp, color = AndisTheme.colors.textSecondary))
                Text("۴۲٪ مصرف‌شده", style = TextStyle(fontFamily = ModamFigures, fontSize = 11.sp, color = AndisTheme.colors.textPrimary))
            }
            Spacer(Modifier.height(4.dp))
            DotProgressBar(progress = 0.42f, activeColor = AndisTheme.colors.accentLime)

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("خواسته‌ها و تفریح (۳۰٪)", style = TextStyle(fontFamily = Modam, fontSize = 11.sp, color = AndisTheme.colors.textSecondary))
                Text("۲۸٪ مصرف‌شده", style = TextStyle(fontFamily = ModamFigures, fontSize = 11.sp, color = AndisTheme.colors.textPrimary))
            }
            Spacer(Modifier.height(4.dp))
            DotProgressBar(progress = 0.28f, activeColor = AndisTheme.colors.accentLime)

            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("پس‌انداز و سرمایه (۲۰٪)", style = TextStyle(fontFamily = Modam, fontSize = 11.sp, color = AndisTheme.colors.textSecondary))
                Text("۱۸٪ ذخیره‌شده", style = TextStyle(fontFamily = ModamFigures, fontSize = 11.sp, color = AndisTheme.colors.accentLime))
            }
            Spacer(Modifier.height(4.dp))
            DotProgressBar(progress = 0.18f, activeColor = AndisTheme.colors.accentLime)
        }
    }
}
