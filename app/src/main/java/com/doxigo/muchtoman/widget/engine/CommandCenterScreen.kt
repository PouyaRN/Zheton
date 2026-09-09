package com.doxigo.muchtoman.widget.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.BankAccount
import com.doxigo.muchtoman.Commitment
import com.doxigo.muchtoman.FluctuationInterval
import com.doxigo.muchtoman.Hero
import com.doxigo.muchtoman.HomeGlanceBar
import com.doxigo.muchtoman.HomeStory
import com.doxigo.muchtoman.LedgerEntry
import com.doxigo.muchtoman.NetWorthFluctuationCard
import com.doxigo.muchtoman.Space
import com.doxigo.muchtoman.UiState
import com.doxigo.muchtoman.fa
import com.doxigo.muchtoman.faCompact
import com.doxigo.muchtoman.faNumber
import com.doxigo.muchtoman.formatCardPanLtr
import com.doxigo.muchtoman.tomanOf

enum class HomeCategoryFilter(val labelFa: String) {
    ALL("همه"),
    CARDS("کارت‌ها"),
    FLOW("مخارج ماه"),
    COMMITMENTS("اقساط و چک"),
    HOLDINGS("سبد دارایی"),
}

/**
 * ANDIS Financial OS — Market-Parity Home Command Center.
 * Brings interactive spline charts with timeframe scrubbing, glance carousels,
 * and high-legibility cards to the Home dashboard, mirroring MarketUi.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandCenterScreen(
    state: UiState,
    slots: List<WidgetSlot>,
    onSaveSlots: (List<WidgetSlot>) -> Unit,
    onRefresh: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenMarkets: () -> Unit,
    onOpenReports: () -> Unit,
    onEntryClick: ((LedgerEntry) -> Unit)? = null,
    privacyMode: Boolean = false,
    bottomInset: Dp = 80.dp,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    var selectedFilter by remember { mutableStateOf(HomeCategoryFilter.ALL) }
    var selectedNetWorthInterval by remember { mutableStateOf(FluctuationInterval.MONTH_1) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val isTablet = maxWidth >= 600.dp
        val horizontalPadding = if (isTablet) 24.dp else 0.dp

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding, top = 0.dp, bottom = bottomInset + 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Market-Style Header with live pulse pill
                item(key = "home_header") {
                    HomeScreenHeader(
                        onRefresh = onRefresh,
                        refreshing = state.refreshing,
                        onOpenReports = onOpenReports,
                    )
                }

                // 2. Horizontal Metrics Glance Carousel
                item(key = "home_glance_bar") {
                    HomeGlanceBar(
                        totalToman = state.totals.toman,
                        bankToman = state.bankToman,
                        gold18Toman = state.effective["gold18"] ?: 0.0,
                        usdCashToman = state.effective["usd"] ?: 0.0,
                        onSelectNetWorth = onOpenReports,
                        onSelectBank = onOpenMarkets,
                        onSelectGold = onOpenMarkets,
                        onSelectUsd = onOpenMarkets,
                    )
                }

                // 3. Hero Net Worth Interactive Spline Chart Card (matches MarketFluctuationCard)
                item(key = "net_worth_fluctuation_card") {
                    NetWorthFluctuationCard(
                        currentToman = state.totals.toman,
                        history = state.history,
                        privacyMode = privacyMode,
                        selectedInterval = selectedNetWorthInterval,
                        onSelectInterval = { selectedNetWorthInterval = it },
                    )
                }

                // 4. Category Filter Pills
                item(key = "home_filter_pills") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.xs),
                        horizontalArrangement = Arrangement.spacedBy(Space.s),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(HomeCategoryFilter.entries) { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = {
                                    Text(
                                        filter.labelFa,
                                        fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Hero.top,
                                    selectedLabelColor = Hero.gold,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                }

                // 5. Monthly Flow Card
                if (selectedFilter == HomeCategoryFilter.ALL || selectedFilter == HomeCategoryFilter.FLOW) {
                    item(key = "card_monthly_flow") {
                        MonthlyCashFlowCard(
                            story = state.story,
                            privacyMode = privacyMode,
                            onClick = onOpenReports,
                        )
                    }
                }

                // 6. Active Bank Cards
                if (selectedFilter == HomeCategoryFilter.ALL || selectedFilter == HomeCategoryFilter.CARDS) {
                    item(key = "card_bank_cards") {
                        ActiveBankCardsCard(
                            accounts = state.bankAccounts,
                            privacyMode = privacyMode,
                            onOpenCards = onOpenMarkets,
                        )
                    }
                }

                // 7. Commitments & Upcoming Bills
                if (selectedFilter == HomeCategoryFilter.ALL || selectedFilter == HomeCategoryFilter.COMMITMENTS) {
                    item(key = "card_commitments") {
                        CommitmentsSummaryCard(
                            commitments = state.commitments,
                            privacyMode = privacyMode,
                            onOpenCommitments = onOpenReports,
                        )
                    }
                }

                // 8. Recent Transactions Stream
                if (selectedFilter == HomeCategoryFilter.ALL) {
                    item(key = "card_recent_txns") {
                        RecentTransactionsCard(
                            entries = state.ledger.entries,
                            privacyMode = privacyMode,
                            onOpenLedger = onOpenLedger,
                            onEntryClick = onEntryClick,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean Header matching MarketScreenHeader.
 */
@Composable
private fun HomeScreenHeader(
    onRefresh: () -> Unit,
    refreshing: Boolean,
    onOpenReports: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.m, vertical = Space.m),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "داشبورد مالی",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.width(Space.s))
                // Live pulse pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Hero.mint.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("زنده", color = Hero.top, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
            Text(
                "رصد دارایی‌ها، موجودی بانک‌ها، مخارج و اقساط",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRefresh) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "به‌روزرسانی")
                }
            }
        }
    }
}

/**
 * Monthly Cash Flow Card matching MarketFluctuationCard elevation and corners.
 */
@Composable
private fun MonthlyCashFlowCard(
    story: HomeStory,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val month = story.month
    val income = tomanOf(month.incomeRial)
    val spent = tomanOf(month.spentRial)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.xs)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(Space.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Hero.mint.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("📊", fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("جریان مالی این ماه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("درآمد در برابر مخارج", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("تحلیل کامل ←", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Hero.gold)
            }

            Spacer(Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("درآمد کل", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    val incStr = if (privacyMode) "••••••••" else "${faCompact(income)} تومان"
                    Text(incStr, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F9D58))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("مخارج کل", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(2.dp))
                    val spdStr = if (privacyMode) "••••••••" else "${faCompact(spent)} تومان"
                    Text(spdStr, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(12.dp))
            val total = (income + spent).coerceAtLeast(1.0)
            val share = (income / total).toFloat().coerceIn(0.08f, 0.92f)
            Row(
                Modifier.fillMaxWidth().height(8.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Box(
                    Modifier
                        .weight(share)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0F9D58))
                )
                Box(
                    Modifier
                        .weight(1f - share)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                )
            }
        }
    }
}

/**
 * Active Bank Cards Card matching Market screen style.
 */
@Composable
private fun ActiveBankCardsCard(
    accounts: List<BankAccount>,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onOpenCards: () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.xs)
            .clickable(onClick = onOpenCards),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(Space.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Hero.gold.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("💳", fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("کارت‌های بانکی فعال", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${fa(accounts.size)} حساب متصل به پیامک", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("مدیریت ←", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Hero.gold)
            }

            if (accounts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                accounts.take(3).forEachIndexed { i, acc ->
                    if (i > 0) Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(acc.bankFa, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            val pan = if (privacyMode) "••••  ••••" else formatCardPanLtr(acc.mask)
                            Text(pan, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val bal = if (privacyMode) "••••••••" else "${faCompact(acc.balance)} ت"
                        Text(bal, fontWeight = FontWeight.Black, fontSize = 13.sp, color = Hero.gold)
                    }
                }
            }
        }
    }
}

/**
 * Commitments & Installments Card matching Market screen style.
 */
@Composable
private fun CommitmentsSummaryCard(
    commitments: List<Commitment>,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onOpenCommitments: () -> Unit,
) {
    val active = commitments.filter { !it.isSettled }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.xs)
            .clickable(onClick = onOpenCommitments),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(Space.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Hero.warn.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⏳", fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("اقساط و چک‌های پیش‌رو", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        val sub = if (active.isNotEmpty()) "${fa(active.size)} تعهد فعال" else "همه تعهدات پرداخت شده"
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("مشاهده ←", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Hero.gold)
            }

            if (active.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                active.take(2).forEachIndexed { i, item ->
                    if (i > 0) Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("${item.provider} • ${item.title}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${fa(item.nextDueDays)} روز تا سررسید", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            val rem = if (privacyMode) "••••••••" else "${faCompact(item.remainingAmountToman.toDouble())} ت"
                            Text(rem, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            Text("${fa(item.paidInstallmentCount)} از ${fa(item.installmentCount)} قسط", fontSize = 10.sp, color = Hero.gold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Recent Transactions Card matching Market screen style.
 */
@Composable
private fun RecentTransactionsCard(
    entries: List<LedgerEntry>,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
    onOpenLedger: () -> Unit,
    onEntryClick: ((LedgerEntry) -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.xs)
            .clickable(onClick = onOpenLedger),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(Space.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Hero.mint.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🧾", fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("آخرین تراکنش‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("گردش لحظه‌ای پیامک‌های بانکی", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("دفترچه ←", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Hero.gold)
            }

            if (entries.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                entries.take(4).forEachIndexed { i, entry ->
                    if (i > 0) Spacer(Modifier.height(6.dp))
                    val amountRial = entry.txn.amountRial ?: 0L
                    val isExpense = amountRial < 0
                    val absRial = Math.abs(amountRial)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f))
                            .clickable { onEntryClick?.invoke(entry) ?: onOpenLedger() }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(entry.txn.merchant, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(entry.categoryFa.ifBlank { "عمومی" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val prefix = if (isExpense) "- " else "+ "
                        val amountText = if (privacyMode) "••••••••" else "$prefix${faCompact(tomanOf(absRial))} ت"
                        Text(
                            amountText,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = if (isExpense) MaterialTheme.colorScheme.onSurface else Color(0xFF0F9D58),
                        )
                    }
                }
            }
        }
    }
}
