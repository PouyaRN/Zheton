package com.doxigo.muchtoman.widget.engine

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.AndisTheme
import com.doxigo.muchtoman.LedgerEntry
import com.doxigo.muchtoman.Modam
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.TABULAR
import com.doxigo.muchtoman.UiState
import com.doxigo.muchtoman.widget.library.AccountsWidget
import com.doxigo.muchtoman.widget.library.BankCardWidget
import com.doxigo.muchtoman.widget.library.BillsWidget
import com.doxigo.muchtoman.widget.library.BudgetWidget
import com.doxigo.muchtoman.widget.library.CurrencyWidget
import com.doxigo.muchtoman.widget.library.DailyBurnWidget
import com.doxigo.muchtoman.widget.library.FinancialHealthWidget
import com.doxigo.muchtoman.widget.library.GoalsWidget
import com.doxigo.muchtoman.widget.library.InstallmentsWidget
import com.doxigo.muchtoman.widget.library.MarketWatchWidget
import com.doxigo.muchtoman.widget.library.NetWorthWidget
import com.doxigo.muchtoman.widget.library.PortfolioWidget
import com.doxigo.muchtoman.widget.library.SpendingWidget
import com.doxigo.muchtoman.widget.library.TransactionsWidget
import com.doxigo.muchtoman.widget.primitives.DotMatrixBadge
import com.doxigo.muchtoman.widget.primitives.DotMatrixText
import com.doxigo.muchtoman.widget.primitives.StatusIndicator
import com.doxigo.muchtoman.widget.primitives.WidgetDensity

/**
 * ANDIS Financial OS — Command Center Dashboard.
 * Modular, customizable asymmetric widget grid inspired by Nothing OS and modern financial terminals.
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
    var showCustomizeSheet by remember { mutableStateOf(false) }
    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize().background(AndisTheme.colors.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = bottomInset + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ANDIS OS Header
            item(key = "header") {
                CommandCenterHeader(
                    refreshing = state.refreshing,
                    onCustomizeClick = { showCustomizeSheet = true }
                )
                Spacer(Modifier.height(4.dp))
            }

            // Visible widgets sorted by order
            val visibleSlots = slots.filter { it.isVisible }.sortedBy { it.order }

            items(visibleSlots, key = { it.id.name }) { slot ->
                when (slot.id) {
                    WidgetId.NET_WORTH -> {
                        NetWorthWidget(
                            state = state,
                            density = slot.density,
                            privacyMode = privacyMode,
                            onClick = onOpenReports
                        )
                    }
                    WidgetId.CURRENCY -> {
                        CurrencyWidget(
                            state = state,
                            density = slot.density,
                            onClick = onOpenMarkets
                        )
                    }
                    WidgetId.FINANCIAL_HEALTH -> {
                        FinancialHealthWidget(
                            score = 84,
                            statusText = "شاخص پایدار",
                            onClick = onOpenReports
                        )
                    }
                    WidgetId.ACCOUNTS -> {
                        AccountsWidget(
                            state = state,
                            privacyMode = privacyMode,
                            onClick = onOpenMarkets
                        )
                    }
                    WidgetId.DAILY_BURN -> {
                        DailyBurnWidget(
                            story = state.story,
                            privacyMode = privacyMode,
                            onClick = onOpenReports
                        )
                    }
                    WidgetId.SPENDING -> {
                        SpendingWidget(
                            story = state.story,
                            privacyMode = privacyMode,
                            onClick = onOpenReports
                        )
                    }
                    WidgetId.INSTALLMENTS -> {
                        InstallmentsWidget(
                            commitments = state.commitments,
                            privacyMode = privacyMode,
                            onClick = onOpenReports
                        )
                    }
                    WidgetId.TRANSACTIONS -> {
                        TransactionsWidget(
                            entries = state.ledger.entries,
                            privacyMode = privacyMode,
                            onClick = onOpenLedger,
                            onEntryClick = onEntryClick
                        )
                    }
                    WidgetId.MARKET_WATCH -> {
                        MarketWatchWidget(
                            state = state,
                            onClick = onOpenMarkets
                        )
                    }
                    WidgetId.BUDGET -> {
                        BudgetWidget(
                            story = state.story,
                            onClick = onOpenReports
                        )
                    }
                    WidgetId.PORTFOLIO -> {
                        PortfolioWidget(
                            state = state,
                            density = slot.density,
                            privacyMode = privacyMode,
                            onClick = onOpenMarkets
                        )
                    }
                    WidgetId.GOALS -> {
                        GoalsWidget(
                            goals = emptyList(),
                            privacyMode = privacyMode,
                            onClick = onOpenReports
                        )
                    }
                    WidgetId.BILLS -> {
                        BillsWidget(
                            commitments = state.commitments,
                            privacyMode = privacyMode,
                            onClick = onOpenReports
                        )
                    }
                }
            }
        }
    }

    if (showCustomizeSheet) {
        WidgetCustomizeSheet(
            slots = slots,
            onSave = { updated ->
                onSaveSlots(updated)
                showCustomizeSheet = false
            },
            onDismiss = { showCustomizeSheet = false }
        )
    }
}

/**
 * ANDIS OS Technical Command Center Header.
 */
@Composable
private fun CommandCenterHeader(
    refreshing: Boolean,
    onCustomizeClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIndicator(
                active = true,
                pulsing = refreshing,
                activeColor = AndisTheme.colors.accentLime
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "اندیس // سیستم مالی",
                style = TextStyle(
                    fontFamily = Modam,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = AndisTheme.colors.textPrimary
                )
            )
            Spacer(Modifier.width(6.dp))
            DotMatrixBadge(
                text = "ANDIS OS",
                activeColor = AndisTheme.colors.accentLime,
                dotSize = 1.3.dp
            )
        }

        Box(
            modifier = Modifier
                .background(AndisTheme.colors.surfacePrimary, RoundedCornerShape(2.dp))
                .border(1.dp, AndisTheme.colors.border, RoundedCornerShape(2.dp))
                .clickable(onClick = onCustomizeClick)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "چیدمان ویجت‌ها",
                style = TextStyle(
                    fontFamily = Modam,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = AndisTheme.colors.textSecondary
                )
            )
        }
    }
}

/**
 * Widget Layout Customization Sheet.
 * Allows reordering, toggling visibility, and changing density.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetCustomizeSheet(
    slots: List<WidgetSlot>,
    onSave: (List<WidgetSlot>) -> Unit,
    onDismiss: () -> Unit,
) {
    var workingSlots by remember { mutableStateOf(slots.sortedBy { it.order }) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AndisTheme.colors.surfacePrimary,
        contentColor = AndisTheme.colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "تنظیمات ویجت‌های داشبورد",
                    style = TextStyle(
                        fontFamily = Modam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AndisTheme.colors.textPrimary
                    )
                )
                Text(
                    text = "ذخیره",
                    style = TextStyle(
                        fontFamily = Modam,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = AndisTheme.colors.accentLime
                    ),
                    modifier = Modifier.clickable { onSave(workingSlots) }
                )
            }
            Spacer(Modifier.height(14.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(workingSlots, key = { it.id.name }) { slot ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AndisTheme.colors.surfaceSecondary, RoundedCornerShape(4.dp))
                            .border(1.dp, AndisTheme.colors.border, RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = slot.id.titleFa,
                                style = TextStyle(
                                    fontFamily = Modam,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (slot.isVisible) AndisTheme.colors.textPrimary else AndisTheme.colors.textMuted
                                )
                            )
                            Text(
                                text = slot.id.badge,
                                style = TextStyle(
                                    fontFamily = ModamFigures,
                                    fontSize = 10.sp,
                                    color = AndisTheme.colors.textMuted
                                )
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (slot.isVisible) AndisTheme.colors.accentLime.copy(alpha = 0.2f) else AndisTheme.colors.border,
                                        RoundedCornerShape(2.dp)
                                    )
                                    .clickable {
                                        workingSlots = workingSlots.map {
                                            if (it.id == slot.id) it.copy(isVisible = !it.isVisible) else it
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (slot.isVisible) "نمایش" else "مخفی",
                                    style = TextStyle(
                                        fontFamily = Modam,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = if (slot.isVisible) AndisTheme.colors.accentLime else AndisTheme.colors.textMuted
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
