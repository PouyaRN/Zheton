package com.doxigo.muchtoman

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.fragment.app.FragmentActivity
import com.doxigo.muchtoman.ui.components.CanvasPageHeader
import com.doxigo.muchtoman.ui.haptics.LocalFintechHaptics

/**
 * The ledger tab (دفتر): the chronological transaction stream ([TimelineScreen]) under the
 * canvas page header. The report used to share this tab behind a segmented pill; it is its own
 * tab now ([ReportTabScreen]).
 */
@Composable
fun LedgerTabScreen(
    state: UiState,
    vm: AppVm,
    bottomInset: Dp,
    timelineCategoryId: String?,
    timelineSearchQuery: String?,
    onOpenTransaction: (String) -> Unit,
    privacyMode: Boolean,
    onSelectionModeChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current as? FragmentActivity
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CanvasPageHeader(title = Tab.LEDGER.fa)
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
}

/**
 * The report tab (گزارش): budget, burn-down and wealth trends ([ReportsTab]) under the canvas
 * page header. Tapping a category hands it to [onSelectCategory], which opens the ledger on it.
 */
@Composable
fun ReportTabScreen(
    state: UiState,
    bottomInset: Dp,
    privacyMode: Boolean,
    onSelectCategory: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fintechHaptics = LocalFintechHaptics.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CanvasPageHeader(title = Tab.REPORTS.fa)
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
                fintechHaptics.click()
                onSelectCategory(catId)
            },
            bottomInset = bottomInset,
            cycle = CycleSpec(state.cycleMode, state.cycleAnchorDay),
        )
    }
}
