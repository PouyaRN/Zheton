package com.doxigo.muchtoman

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.doxigo.muchtoman.ui.haptics.LocalFintechHaptics
import kotlinx.coroutines.launch

/**
 * Unified Activity & Trends screen (Tab 4: "دفتر و تحلیل").
 *
 * Consolidates the chronological transaction stream ([TimelineScreen]) and
 * the analytical budget & burn-down trends ([ReportsTab]) under a seamless
 * bidirectional horizontal pager with a top obsidian segmented pill.
 */
@Composable
fun UnifiedActivityAndTrendsScreen(
    state: UiState,
    vm: AppVm,
    bottomInset: Dp,
    timelineCategoryId: String?,
    timelineSearchQuery: String?,
    onOpenTransaction: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onSearchQueryChanged: (String?) -> Unit,
    privacyMode: Boolean,
    onSelectionModeChanged: (Boolean) -> Unit = {},
    initialPage: Int = 0,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val fintechHaptics = LocalFintechHaptics.current
    val activity = LocalActivity.current as? FragmentActivity

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Obsidian Segmented Switcher Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Space.m, vertical = Space.xs),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val pages = listOf("ریزتراکنش‌ها", "دیده‌بان و بودجه")
                pages.forEachIndexed { index, title ->
                    val isSelected = pagerState.currentPage == index
                    val bgCol by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "pill_bg_$index"
                    )
                    val textCol by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "pill_text_$index"
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(bgCol)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                if (!isSelected) {
                                    fintechHaptics.click()
                                    scope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = ModamFigures,
                            color = textCol,
                        )
                    }
                }
            }
        }

        // Horizontal Pager hosting both views
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> {
                    TimelineScreen(
                        ledger = state.ledger,
                        state = state,
                        bottomInset = bottomInset,
                        onOpen = { onOpenTransaction(it.txn.ref) },
                        onMarkCredit = { vm.startMarkCredit(it) },
                        onLinkInstallment = { vm.startLinkInstallment(it) },
                        onExport = { activity?.let { ctx -> vm.exportCsv(ctx) } },
                        initialCategoryId = timelineCategoryId,
                        initialSearchQuery = timelineSearchQuery,
                        onRefresh = { vm.refreshLedger() },
                        onAddManual = { amountRial, isExpense, catId, merchant, note, at ->
                            vm.addManualTransaction(amountRial, isExpense, catId, merchant, note, at)
                        },
                        onBulkCategorise = vm::bulkCategorise,
                        onBulkHide = vm::bulkHide,
                        onBulkFlip = vm::bulkFlipDirection,
                        onBulkDelete = vm::bulkDelete,
                        onBulkNote = vm::bulkSaveNote,
                        onBulkMerchant = vm::bulkPinMerchant,
                        onHide = { entry, hidden -> vm.setTransactionHidden(entry, hidden) },
                        onToggleShowHidden = { vm.setShowHidden(!state.showHidden) },
                        onToggleTransfer = { entry -> vm.toggleTransfer(entry) },
                        onFilterWide = { wide -> vm.setFilterWide(wide) },
                        privacyMode = privacyMode,
                        onSelectionModeChanged = onSelectionModeChanged,
                    )
                }
                1 -> {
                    val composition = remember(state.listHoldings, state.coins, state.effective, state.stocks) {
                        compositionByKind(state.listHoldings, state.coins, state.effective, state.stocks)
                    }
                    ReportsTab(
                        ledger = state.ledger,
                        history = state.history,
                        current = state.totals.toman,
                        composition = composition,
                        bankAccounts = state.bankAccounts,
                        commitments = state.commitments,
                        reservations = state.reservations,
                        privacyMode = privacyMode,
                        onSelectCategory = { catId ->
                            onCategorySelected(catId)
                            onSearchQueryChanged(null)
                            fintechHaptics.click()
                            scope.launch {
                                pagerState.animateScrollToPage(0)
                            }
                        },
                        bottomInset = bottomInset,
                        cycle = CycleSpec(state.cycleMode, state.cycleAnchorDay),
                    )
                }
            }
        }
    }
}
