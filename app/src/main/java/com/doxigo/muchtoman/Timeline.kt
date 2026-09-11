package com.doxigo.muchtoman

import com.doxigo.muchtoman.ui.home.HeroPanel

import androidx.activity.compose.BackHandler

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The activity timeline — what happened, grouped by the day it happened in Tehran.
 *
 * Every row traces back to the message it came from, because "every number can be traced back
 * to a transaction" is not a feature that can be added later: it is either true of every row on
 * the screen or it is not true at all.
 */

internal val MONTHS = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
)

/** «۳ مرداد ۱۴۰۵», or «امروز» and «دیروز» for the two days she is actually thinking about. */
fun faDay(day: Long, today: Long = tehranDay(System.currentTimeMillis())): String = when (day) {
    today -> "امروز"
    today - 1 -> "دیروز"
    else -> jalaliOf(day).let { "${faNumber(it.day.toDouble())} ${MONTHS[it.month - 1]} ${faYear(it.year)}" }
}

private fun faYear(year: Int): String = buildString {
    for (c in year.toString()) append('۰' + (c - '0'))
}

@Composable
fun TimelineScreen(
    ledger: LedgerView,
    state: UiState,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpen: (LedgerEntry) -> Unit,
    onMarkCredit: (LedgerEntry) -> Unit,
    onLinkInstallment: (LedgerEntry) -> Unit,
    onExport: () -> Unit,
    initialCategoryId: String? = null,
    initialSearchQuery: String? = null,
    onRefresh: () -> Unit = {},
    onAddManual: (amountRial: Long, isExpense: Boolean, categoryId: String?, merchant: String, note: String, at: Long) -> Unit = { _, _, _, _, _, _ -> },
    onBulkCategorise: (List<LedgerEntry>, String) -> Unit = { _, _ -> },
    onBulkHide: (List<LedgerEntry>, Boolean) -> Unit = { _, _ -> },
    onBulkFlip: (List<LedgerEntry>) -> Unit = {},
    onBulkDelete: (List<LedgerEntry>) -> Unit = {},
    onBulkNote: (List<LedgerEntry>, String) -> Unit = { _, _ -> },
    onBulkMerchant: (List<LedgerEntry>, String) -> Unit = { _, _ -> },
    onHide: (LedgerEntry, Boolean) -> Unit = { _, _ -> },
    onToggleShowHidden: () -> Unit = {},
    onToggleTransfer: (LedgerEntry) -> Unit = {},
    /** Tells the host to widen the ledger window while a structured filter is live. */
    onFilterWide: (Boolean) -> Unit = {},
    privacyMode: Boolean = false,
) {
    val today = remember { tehranDay(System.currentTimeMillis()) }
    // Without the reveal, hidden rows leave the ledger entirely; with it, they come back
    // struck through and tapping one restores it.
    val visible = remember(ledger.entries, ledger.hiddenEntries, state.showHidden) {
        val base = ledger.entries.filterNot { it.duplicate }
        if (state.showHidden) base + ledger.hiddenEntries else base
    }
    var searchQuery by remember(initialSearchQuery) { mutableStateOf(initialSearchQuery ?: "") }
    var selectedCategoryId by remember(initialCategoryId) { mutableStateOf(initialCategoryId) }
    var showAddManual by remember { mutableStateOf(false) }
    // The structured filter sheet (ticket 04+). Tokens AND together on top of the search bar
    // and the category chips. Editing the filter clears any in-progress selection, because the
    // batch bar must describe a set that is still in front of the user.
    var filterTokens by remember { mutableStateOf(listOf<FilterToken>()) }
    var showFilter by remember { mutableStateOf(false) }
    // Selection mode. A long press (or the select button in the search row) enters it; rows
    // then carry a checkbox, the header becomes a batch bar, and "select all" fills the
    // current filter result. Selected refs are tracked here so bulk actions can consume them.
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    val filtered = remember(visible, searchQuery, selectedCategoryId, filterTokens) {
        visible.filter { entry ->
            val note = ledger.notes[entry.txn.ref].orEmpty()
            val matchesSearch = matchesTimelineQuery(entry, note, searchQuery)
            val matchesCategory = selectedCategoryId == null || entry.categoryId == selectedCategoryId
            matchesSearch && matchesCategory && matchesFilter(entry, filterTokens)
        }
    }

    val grouped = remember(filtered) { filtered.groupBy { it.txn.day }.toSortedMap(compareByDescending { it }) }

    // Back exits selection mode before it ever leaves the timeline.
    if (selecting) {
        BackHandler {
            selecting = false
            selected = emptySet()
        }
    }

    // While a structured filter is live, the host widens the ledger window to the whole store;
    // turning it back off narrows to the browsing window again.
    LaunchedEffect(filterTokens) { onFilterWide(filterTokens.isNotEmpty()) }

    if (showAddManual) {
        ManualTxnSheet(
            categories = ledger.categories,
            onDismiss = { showAddManual = false },
            onSave = { amountRial, isExpense, catId, merchant, note, at ->
                onAddManual(amountRial, isExpense, catId, merchant, note, at)
                showAddManual = false
            },
        )
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (selecting) {
                // The batch bar replaces the header while selecting.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        selecting = false
                        selected = emptySet()
                    }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "خروج از انتخاب",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Text(
                        if (selected.isEmpty()) "هیچ‌کدام انتخاب نشده" else "${selected.size} انتخاب شده",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
TextButton(onClick = {
        // Grouped by day, and the day bands only ever hold the `filtered` rows (duplicates
        // excluded). The slug is counted once; no double counting.
        selected = if (selected.size == grouped.values.flatten().size && grouped.isNotEmpty()) {
            emptySet()
        } else {
            grouped.values.flatten().map { it.txn.ref }.toSet()
        }
    }) {
        Text(if (selected.size == grouped.values.flatten().size && grouped.isNotEmpty()) "برداشتن همه" else "انتخاب همه")
    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "دفتر",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        selecting = true
                        selected = emptySet()
                    }) {
                        Text("انتخاب", fontSize = 14.sp)
                    }
                    IconButton(onClick = { showAddManual = true }) {
                        Icon(Icons.Rounded.Add, contentDescription = "تراکنش جدید")
                    }
                    IconButton(onClick = onExport) {
                        Icon(Icons.Rounded.Share, contentDescription = "خروجی CSV")
                    }
                    if (state.ledger.hiddenCount > 0 && !selecting) {
                        FilterChip(
                            selected = state.showHidden,
                            onClick = onToggleShowHidden,
                            label = {
                                Text(
                                    if (state.showHidden) "مخفی‌ها" else "پنهان‌شده (${state.ledger.hiddenCount})",
                                    fontSize = 12.sp,
                                )
                            },
                        )
                    }
                }
            }

            // Search bar — plus the filter sheet entry. The button carries the active-token
            // count so a filter that is narrowing the list is visible at a glance.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("جستجو...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "پاک کردن")
                            }
                        }
                    },
                    singleLine = true,
                )
                TextButton(onClick = {
                    showFilter = !showFilter
                    if (showFilter) {
                        selecting = false
                        selected = emptySet()
                    }
                }) {
                    Text(
                        if (filterTokens.isEmpty()) "فیلتر" else "فیلتر (${filterTokens.size})",
                        fontSize = 13.sp,
                    )
                }
            }

            if (showFilter) {
                FilterSheet(
                    tokens = filterTokens,
                    categories = ledger.categories,
                    onApply = { tokens ->
                        filterTokens = tokens
                        selecting = false
                        selected = emptySet()
                    },
                    onDismiss = { showFilter = false },
                )
            }

            // Category filter chips
            if (ledger.categories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(vertical = Space.s),
                    contentPadding = PaddingValues(horizontal = Space.xl),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("همه", fontSize = 12.sp) },
                        )
                    }
                    items(ledger.categories.filter { it.kind != "transfer" && it.id !in state.hiddenCategories }) { category ->
                        FilterChip(
                            selected = category.id == selectedCategoryId,
                            onClick = {
                                selectedCategoryId = if (category.id == selectedCategoryId) null else category.id
                            },
                            label = { Text(category.nameFa, fontSize = 12.sp) },
                        )
                    }
                }
            }

            // 14-day spending outflow sparkline chart
            DailyOutflowSparkCard(
                entries = visible,
                todayEpochDay = today,
                privacyMode = privacyMode,
            )

            // Failed transaction watchdog banner (Ticket 05)
            val failedWatchdogs = remember(visible) {
                val failed = visible.filter { isFailedDebitTxn(it) }
                reconcileFailedWatchdogs(failed, visible, System.currentTimeMillis())
            }
            FailedTxnWatchdogBanner(
                watchdogs = failedWatchdogs,
                onFilterRef = { ref ->
                    searchQuery = ref
                },
            )

            // Active filter banner showing match count and fast one-tap reset
            val isFilterActive = searchQuery.isNotBlank() || selectedCategoryId != null || filterTokens.isNotEmpty()
            if (isFilterActive) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(Radius.field),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.xl, vertical = Space.xs)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.field)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.m, vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${faNumber(filtered.size.toDouble())} تراکنش پیدا شد",
                            fontSize = 13.sp,
                            fontFamily = ModamFigures,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(
                            onClick = {
                                searchQuery = ""
                                selectedCategoryId = null
                                filterTokens = emptyList()
                                selecting = false
                                selected = emptySet()
                            },
                            contentPadding = PaddingValues(horizontal = Space.s, vertical = 0.dp),
                        ) {
                            Text(
                                "پاک کردن فیلترها",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            var pulled by remember { mutableStateOf(false) }
            LaunchedEffect(state.refreshing) { if (!state.refreshing) pulled = false }
            val pullState = rememberPullToRefreshState()

            PullToRefreshBox(
                isRefreshing = state.refreshing && pulled,
                onRefresh = { pulled = true; onRefresh() },
                state = pullState,
                modifier = Modifier.fillMaxSize().weight(1f),
            ) {
                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(Space.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "موردی یافت نشد",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Space.xl, end = Space.xl, bottom = bottomInset + 100.dp,
                        ),
                    ) {
                        for ((day, rows) in grouped) {
                            item(key = "day-$day") {
                                DayBand(
                                    day = day,
                                    today = today,
                                    rows = rows,
                                    state = state,
                                    selecting = selecting,
                                    selected = selected,
                                    onSelect = { ref ->
                                        selected = if (ref in selected) selected - ref else selected + ref
                                    },
                                    onEnterSelection = { ref ->
                                        selecting = true
                                        selected = setOf(ref)
                                    },
                                    onOpen = onOpen,
                                    onHide = onHide,
                                    onMarkCredit = onMarkCredit,
                                    onLinkInstallment = onLinkInstallment,
                                    onToggleTransfer = onToggleTransfer,
                                    privacyMode = privacyMode,
                                )
                            }
                        }
                    }
                }
            }

            // The batch action bar: appears only while something is selected. Category filing is
            // the first bulk action; the same refs are what later bulk hide/delete will consume.
            if (selecting && selected.isNotEmpty()) {
                val rows = filtered.filter { it.txn.ref in selected }
                BatchActionBar(
                    selectedRefs = selected,
                    matchCount = filtered.size,
                    categories = ledger.categories,
                    hiddenCategories = state.hiddenCategories,
                    onPickCategory = { categoryId ->
                        if (rows.isNotEmpty()) onBulkCategorise(rows, categoryId)
                        selecting = false
                        selected = emptySet()
                    },
                    onHide = {
                        onBulkHide(rows, true)
                        selecting = false
                        selected = emptySet()
                    },
                    onFlip = {
                        onBulkFlip(rows)
                        selecting = false
                        selected = emptySet()
                    },
                    onDelete = {
                        onBulkDelete(rows)
                        selecting = false
                        selected = emptySet()
                    },
                    onNote = { note ->
                        if (rows.isNotEmpty()) onBulkNote(rows, note)
                        selecting = false
                        selected = emptySet()
                    },
                    onMerchant = { name ->
                        if (rows.isNotEmpty()) onBulkMerchant(rows, name)
                        selecting = false
                        selected = emptySet()
                    },
                    onCancel = {
                        selecting = false
                        selected = emptySet()
                    },
                )
            }
        }
    }
}

/**
 * The structured filter editor (ticket 04). A list of tokens — each a field+operator+value row —
 * AND-composed, with a live token count in the header. Tokens for merchant/bank/category match
 * substrings; amount and date take ranges; direction picks in/out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    tokens: List<FilterToken>,
    categories: List<Category>,
    onApply: (List<FilterToken>) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(tokens) { mutableStateOf(tokens) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Space.xl, vertical = Space.m),
        ) {
            Text("فیلتر دفتر", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Space.xs))
            Text(
                "شرط‌ها به هم اضافه می‌شوند؛ برای «اسنپ» بدون «اسنپپی» دو شرط بزن.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.m))

            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                itemsIndexed(draft, key = { idx, token -> token.positionKey(idx) }) { idx, token ->
                    FilterTokenRow(
                        token = token,
                        categories = categories,
                        onChanged = { updated ->
                            draft = draft.mapIndexed { i, t -> if (i == idx) updated else t }
                        },
                        onRemove = { draft = draft.filterIndexed { i, _ -> i != idx } },
                    )
                    Spacer(Modifier.height(Space.s))
                }
            }

            Row(Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    draft = draft + FilterToken(FilterField.MERCHANT, FilterOp.CONTAINS, "")
                }) {
                    Text("+ افزودن شرط", fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { draft = emptyList() }) { Text("پاک کردن", fontSize = 13.sp) }
            }
            Spacer(Modifier.height(Space.s))
            Button(
                onClick = { onApply(draft) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("اعمال فیلتر", fontSize = 14.sp) }
        }
    }
}

@Composable
private fun FilterTokenRow(
    token: FilterToken,
    categories: List<Category>,
    onChanged: (FilterToken) -> Unit,
    onRemove: () -> Unit,
) {
    var fieldMenu by remember { mutableStateOf(false) }
    var opMenu by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(token.value) }

    LaunchedEffect(token) { value = token.value }

    // Field select: the tokens keep a canonical lowercase id, the label merges with the
    // operator text for a single readable line.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.field))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
Box {
            OutlinedButton(onClick = { fieldMenu = true }, contentPadding = PaddingValues(horizontal = Space.s)) {
                Text(token.field.fa, fontSize = 13.sp)
            }
            DropdownMenu(expanded = fieldMenu, onDismissRequest = { fieldMenu = false }) {
                FilterField.entries.forEach { field ->
                    DropdownMenuItem(
                        text = { Text(field.fa, fontSize = 13.sp) },
                        onClick = {
                            fieldMenu = false
                            onChanged(token.copy(field = field))
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(Space.xs))
        Box {
            OutlinedButton(onClick = { opMenu = true }, contentPadding = PaddingValues(horizontal = Space.s)) {
                Text(token.op.fa, fontSize = 13.sp)
            }
            DropdownMenu(expanded = opMenu, onDismissRequest = { opMenu = false }) {
                val ops = if (token.field == FilterField.MERCHANT) FilterOp.entries
                else FilterOp.entries.filterNot { it == FilterOp.REGEX }
                ops.forEach { op ->
                    DropdownMenuItem(
                        text = { Text(op.fa, fontSize = 13.sp) },
                        onClick = {
                            opMenu = false
                            onChanged(token.copy(op = op))
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(Space.xs))
        OutlinedTextField(
            value = value,
            onValueChange = {
                value = it
                onChanged(token.copy(value = it))
            },
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholderFor(token.field), fontSize = 12.sp) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
        )
        Spacer(Modifier.width(Space.xs))
        TextButton(onClick = onRemove) {
            Text("حذف", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** The value placeholder that matches each filter field. */
private fun placeholderFor(field: FilterField): String = when (field) {
    FilterField.MERCHANT -> "مثلا اسنپ"
    FilterField.CATEGORY -> "نام دسته"
    FilterField.BANK -> "نام بانک"
    FilterField.DIRECTION -> "دریافتی / پرداختی"
    FilterField.AMOUNT -> "مبلغ (تومان)"
    FilterField.DATE -> "تاریخ"
}

/**
 * The bottom bar while transactions are selected: count, "عملیات" opens the batch sheet, and
 * cancel. Confirmations stay in-dialog; the sheet is the entry point only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BatchActionBar(
    selectedRefs: Set<String>,
    /** How many rows the current filter matches — the batch bar shows it under the selection. */
    matchCount: Int,
    categories: List<Category>,
    hiddenCategories: Set<String>,
    onPickCategory: (String) -> Unit,
    onHide: () -> Unit,
    onFlip: () -> Unit,
    onDelete: () -> Unit,
    onNote: (String) -> Unit,
    onMerchant: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var showActionsSheet by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showHideConfirm by remember { mutableStateOf(false) }
    var showFlipConfirm by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showMerchantDialog by remember { mutableStateOf(false) }
    var noteValue by remember { mutableStateOf("") }
    var merchantValue by remember { mutableStateOf("") }

    Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.xl, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${selectedRefs.size} مورد انتخاب شده",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (matchCount != selectedRefs.size) {
                    Text(
                        "فیلتر با $matchCount مورد مطابقت دارد؛ «انتخاب همه» همهٔ آن‌ها را برمی‌دارد.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Button(onClick = { showActionsSheet = true }) {
                Text("عملیات", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onCancel) { Text("لغو", fontSize = 12.sp) }
        }
    }

    if (showActionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showActionsSheet = false }, sheetState = sheetState) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = Space.xl, vertical = Space.m),
            ) {
                Text(
                    "${selectedRefs.size} مورد انتخاب شده",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Space.m))

                // دستهبندی — first, with emphasis
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.field))
                        .clickable { showActionsSheet = false; showCategoryPicker = true },
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        "دسته‌بندی",
                        modifier = Modifier.padding(Space.m),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(Space.s))

                // Remaining actions
                listOf(
                    "یادداشت" to { noteValue = ""; showActionsSheet = false; showNoteDialog = true },
                    "فروشگاه" to { merchantValue = ""; showActionsSheet = false; showMerchantDialog = true },
                    "پنهان" to { showActionsSheet = false; showHideConfirm = true },
                    "جابجایی جهت" to { showActionsSheet = false; showFlipConfirm = true },
                ).forEach { (label, action) ->
                    TextButton(
                        onClick = action,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(label, fontSize = 15.sp, modifier = Modifier.fillMaxWidth())
                    }
                }
                TextButton(
                    onClick = { showActionsSheet = false; showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("حذف", fontSize = 15.sp, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(Space.s))
            }
        }
    }
    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false },
            title = { Text("یادداشت برای ${selectedRefs.size} مورد") },
            text = {
                OutlinedTextField(
                    value = noteValue,
                    onValueChange = { noteValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("یادداشت یکسان برای همهٔ این‌ها...") },
                )
            },
            confirmButton = {
                TextButton(onClick = { showNoteDialog = false; onNote(noteValue) }) { Text("ذخیره") }
            },
            dismissButton = {
                Row {
                    if (noteValue.isNotEmpty()) {
                        TextButton(onClick = { showNoteDialog = false; onNote("") }) { Text("پاک کردن", fontSize = 12.sp) }
                    }
                    TextButton(onClick = { showNoteDialog = false }) { Text("انصراف") }
                }
            },
        )
    }
    if (showMerchantDialog) {
        AlertDialog(
            onDismissRequest = { showMerchantDialog = false },
            title = { Text("نام فروشگاه برای ${selectedRefs.size} مورد") },
            text = {
                Column {
                    OutlinedTextField(
                        value = merchantValue,
                        onValueChange = { merchantValue = it.take(40) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("نام یکسان برای همهٔ فروشگاه‌های انتخاب‌شده...") },
                    )
                    Text(
                        "خالی کردن، نام اصلی پیامک را برای همه برمی‌گرداند.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showMerchantDialog = false; onMerchant(merchantValue) }) { Text("ذخیره") }
            },
            dismissButton = {
                Row {
                    if (merchantValue.isNotEmpty()) {
                        TextButton(onClick = { showMerchantDialog = false; onMerchant("") }) { Text("پاک کردن", fontSize = 12.sp) }
                    }
                    TextButton(onClick = { showMerchantDialog = false }) { Text("انصراف") }
                }
            },
        )
    }
    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = {
                Text("دسته‌بندی ${selectedRefs.size} مورد")
            },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(categoryChoices(categories, null, hiddenCategories), key = { it.id }) { category ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showCategoryPicker = false
                                    onPickCategory(category.id)
                                }
                                .padding(vertical = Space.s, horizontal = Space.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIcon(
                                category.nameFa,
                                MaterialTheme.colorScheme.onSurfaceVariant,
                                size = 18.dp,
                                stroke = 1.4.dp,
                            )
                            Spacer(Modifier.width(Space.m))
                            Text(category.nameFa, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCategoryPicker = false }) { Text("انصراف") }
            },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف دائمی ${selectedRefs.size} مورد") },
            text = {
                Text(
                    "پیام‌های اصلی و همهٔ تصمیم‌ها برای این ${selectedRefs.size} مورد پاک می‌شوند. " +
                        "ده ثانیه فرصت تعویض داری."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("انصراف") }
            },
        )
    }
    if (showHideConfirm) {
        AlertDialog(
            onDismissRequest = { showHideConfirm = false },
            title = { Text("پنهان کردن ${selectedRefs.size} مورد") },
            text = { Text("این ${selectedRefs.size} مورد دیگر در دفتر نمی‌آیند؛ با کلید مخفی‌ها می‌توانی آن‌ها را دوباره ببینی.") },
            confirmButton = {
                TextButton(
                    onClick = { showHideConfirm = false; onHide() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) { Text("پنهان") }
            },
            dismissButton = {
                TextButton(onClick = { showHideConfirm = false }) { Text("انصراف") }
            },
        )
    }
    if (showFlipConfirm) {
        AlertDialog(
            onDismissRequest = { showFlipConfirm = false },
            title = { Text("جابجایی جهت ${selectedRefs.size} مورد") },
            text = { Text("جهت این ${selectedRefs.size} مورد برعکس می‌شود (دریافتی ↔ پرداختی).") },
            confirmButton = {
                TextButton(
                    onClick = { showFlipConfirm = false; onFlip() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) { Text("جابجایی") }
            },
            dismissButton = {
                TextButton(onClick = { showFlipConfirm = false }) { Text("انصراف") }
            },
        )
    }
}

/**
 * A day, as one object.
 *
 * The rows used to float on the background as a single undifferentiated stack, and the day they
 * belonged to was a 13sp grey line easy to scroll straight past. Banding them is what `group` is
 * for — "the container a band of rows sits in, as one object rather than a stack of cards" — and
 * it lets the heading carry the day's net, which is the one figure the ledger owns and no other
 * screen shows.
 */
@Composable
private fun DayBand(
    day: Long,
    today: Long,
    rows: List<LedgerEntry>,
    state: UiState,
    selecting: Boolean = false,
    selected: Set<String> = emptySet(),
    onSelect: (String) -> Unit = {},
    onEnterSelection: (String) -> Unit = {},
    onOpen: (LedgerEntry) -> Unit,
    onHide: (LedgerEntry, Boolean) -> Unit = { _, _ -> },
    onMarkCredit: (LedgerEntry) -> Unit,
    onLinkInstallment: (LedgerEntry) -> Unit,
    onToggleTransfer: (LedgerEntry) -> Unit = {},
    privacyMode: Boolean = false,
) {
    // Transfers move money between her own accounts. Counting them would show a day that only
    // shuffled funds as a day she earned or spent, which is the whole reason they are struck
    // through in the rows below. Hidden rows have been deleted from her ledger — they count
    // in neither the day's figure nor anything else.
    val net = rows.filterNot { it.transfer || it.hidden }.sumOf { it.txn.signedRial ?: 0L }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.xl, bottom = Space.s, start = Space.s, end = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            faDay(day, today),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (net != 0L) {
            Text(
                if (privacyMode) "••••••" else faSignedCompact(tomanOf(net), net > 0),
                // A step under the rows it summarises: the day's figure is context for them,
                // not another entry competing with them.
                style = figureStyle(
                    if (net > 0) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.onSurfaceVariant,
                    FontWeight.Bold,
                ),
                fontSize = 13.sp,
            )
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.group))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(Radius.group)),
    ) {
        rows.forEach { entry ->
            TimelineRow(
                entry = entry,
                state = state,
                selecting = selecting,
                selected = entry.txn.ref in selected,
                onSelect = { onSelect(entry.txn.ref) },
                onEnterSelection = { onEnterSelection(entry.txn.ref) },
                onClick = {
                    if (entry.hidden) onHide(entry, false) else onOpen(entry)
                },
                onHide = onHide,
                onMarkCredit = onMarkCredit,
                onLinkInstallment = onLinkInstallment,
                onToggleTransfer = onToggleTransfer,
                privacyMode = privacyMode,
            )
        }
    }
}

@Composable
private fun TimelineRow(
    entry: LedgerEntry,
    state: UiState,
    selecting: Boolean = false,
    selected: Boolean = false,
    onSelect: () -> Unit = {},
    onEnterSelection: () -> Unit = {},
    onClick: () -> Unit,
    onHide: (LedgerEntry, Boolean) -> Unit = { _, _ -> },
    onMarkCredit: (LedgerEntry) -> Unit,
    onLinkInstallment: (LedgerEntry) -> Unit,
    onToggleTransfer: (LedgerEntry) -> Unit = {},
    privacyMode: Boolean = false,
) {
    val txn = entry.txn
    val incoming = txn.direction == "in"
    val tone = when {
        entry.transfer -> MaterialTheme.colorScheme.onSurfaceVariant
        incoming -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.onSurface
    }
    var menuOpen by remember { mutableStateOf(false) }
    val isCreditLinked = state.obligations.any { it.linkedTxnRef == txn.ref }
    val hasUnpaidInstallments = state.obligationPayments.values.any { payments ->
        payments.any { it.paidDate == null }
    }
    val rowBackground by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "row_bg",
    )
    val checkScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "check_scale",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .background(rowBackground)
            .combinedClickable(
                onClick = { if (selecting) onSelect() else onClick() },
                onLongClick = {
                    if (selecting) onSelect()
                    else onEnterSelection()
                },
            )
            .padding(vertical = Space.m, horizontal = Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            // The check cell. A raw Box, not Checkbox: the core icon set ships no check box,
            // and drawing one keeps the tint in step with the row's own figure colour.
            Box(
                Modifier
                    .size(22.dp)
                    .scale(checkScale)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
                        else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "انتخاب شده",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.width(Space.s))
        }
        Column(Modifier.weight(1f)) {
            Text(
                txn.merchant.ifBlank {
                    if (txn.sourceKind == "manual") "مورد دستی" else bankNameOf(txn.bank)
                },
                fontWeight = FontWeight.SemiBold,
                color = if (entry.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                textDecoration = if (entry.hidden) TextDecoration.LineThrough else null,
                maxLines = 1,
            )
            // Show the note subtitle when a note exists.
            val note = state.ledger.notes[txn.ref]
            if (!note.isNullOrBlank()) {
                Text(
                    note,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val categoryFa = if (entry.transfer) "انتقال بین حساب‌ها" else entry.categoryFa
                CategoryIcon(
                    categoryFa,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 14.dp,
                    stroke = 1.4.dp,
                )
                Spacer(Modifier.width(Space.xs))
                Text(
                    categoryFa,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.ownerName.isNotBlank()) {
                    Spacer(Modifier.width(Space.xs))
                    Text(
                        "از ${entry.ownerName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        val rial = txn.signedRial ?: txn.amountRial
        if (rial != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (privacyMode) "••••••" else faSignedCompact(tomanOf(rial), incoming),
                    style = figureStyle(tone, FontWeight.Bold),
                    fontSize = 15.sp,
                    textDecoration = if (entry.transfer) TextDecoration.LineThrough else null,
                )
                if (privacyMode) {
                    Text(
                        "⏱️ •••",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                } else if (state.hourlyWageToman > 0L && !incoming && !entry.transfer) {
                    val toman = kotlin.math.abs(tomanOf(rial)).toLong()
                    val energy = calculateLifeEnergy(toman, customHourlyWageToman = state.hourlyWageToman)
                    if (energy.hours > 0 || energy.minutes >= 10) {
                        Text(
                            "⏱️ ${energy.formattedFa}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        } else {
            Text(
                "مانده",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
        val context = androidx.compose.ui.platform.LocalContext.current

        // Three-dot action menu — comprehensive, clear, and feature-complete
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "عملیات",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("مشاهده و ویرایش") },
                    onClick = { menuOpen = false; onClick() },
                )
                DropdownMenuItem(
                    text = { Text("کپی متن رسید") },
                    onClick = {
                        menuOpen = false
                        val note = state.ledger.notes[entry.txn.ref].orEmpty()
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(formatReceipt(entry, note)))
                        android.widget.Toast.makeText(context, "رسید تراکنش کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (entry.hidden) "نمایش مجدد" else "پنهان‌سازی از گزارش‌ها") },
                    onClick = {
                        menuOpen = false
                        onHide(entry, !entry.hidden)
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(if (entry.transfer) "لغو انتقال بین حساب‌ها (محاسبه در مخارج)" else "ثبت به عنوان انتقال بین حساب‌ها")
                    },
                    onClick = {
                        menuOpen = false
                        onToggleTransfer(entry)
                    },
                )
                if (!entry.transfer && (txn.direction == "out" || txn.direction == null)) {
                    if (!isCreditLinked && rial != null && rial < 0) {
                        DropdownMenuItem(
                            text = { Text("ثبت به عنوان خرید اعتباری") },
                            onClick = { menuOpen = false; onMarkCredit(entry) },
                        )
                    }
                    if (hasUnpaidInstallments && rial != null && rial < 0) {
                        DropdownMenuItem(
                            text = { Text("پیوند به قسط وام") },
                            onClick = { menuOpen = false; onLinkInstallment(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLedger() {
    Column(
        Modifier.fillMaxSize().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
                "هنوز هیچ تراکنشی نیست",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(Space.s))
        Text(
            "از تنظیمات، پیامک‌های بانکی رو روشن کن تا دفترت خودش پر بشه.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun bankNameOf(bank: String): String =
    runCatching { Bank.valueOf(bank) }.getOrDefault(Bank.OTHER).fa

/**
 * A field that saves itself: typing re-arms a [debounceMs] write, [flush] writes the current
 * value immediately (used when the screen closes), and [justSaved] flips briefly so the
 * «ذخیره شد» caption can render. Sharing one helper across the note and merchant fields (and
 * any future text editor) keeps the save semantics in one place.
 */
private class AutoSaveState(
    initial: String,
    private val scope: CoroutineScope,
    private val onSave: (String) -> Unit,
    private val debounceMs: Long = 600L,
    private val flashMs: Long = 1400L,
) {
    var value by mutableStateOf(initial)
    var dirty by mutableStateOf(false)
    var justSaved by mutableStateOf(false)
    private var savedText: String = initial
    private var job: Job? = null

    fun onChange(next: String) {
        value = next
        dirty = next != savedText
        job?.cancel()
        job = scope.launch {
            delay(debounceMs)
            commit()
        }
    }

    /** Write whatever is typed right now — called when the screen goes away. */
    fun flush() = commit()

    private fun commit() {
        job?.cancel()
        job = null
        val text = value
        if (text == savedText) return
        savedText = text
        dirty = false
        runCatching { onSave(text) }
            .onFailure { android.util.Log.w("muchtoman", "auto-save failed", it) }
        justSaved = true
        scope.launch {
            delay(flashMs)
            justSaved = false
        }
    }
}

@Composable
private fun rememberAutoSave(
    key: Any,
    initial: String,
    onSave: (String) -> Unit,
    debounceMs: Long = 600L,
): AutoSaveState {
    val scope = rememberCoroutineScope()
    return remember(key) { AutoSaveState(initial, scope, onSave, debounceMs) }
}

/**
 * Normalizes text for search indexing and fuzzy Persian matching.
 * Converts Arabic variants to Persian, unifies Persian/Arabic digits to ASCII,
 * and strips punctuation, commas, and zero-width non-joiners.
 */
fun normalizeSearchToken(str: String): String {
    return str.lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace('ة', 'ه')
        .replace('آ', 'ا')
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('۰', '0')
        .replace('۱', '1')
        .replace('۲', '2')
        .replace('۳', '3')
        .replace('۴', '4')
        .replace('۵', '5')
        .replace('۶', '6')
        .replace('۷', '7')
        .replace('۸', '8')
        .replace('۹', '9')
        .replace('٠', '0')
        .replace('١', '1')
        .replace('٢', '2')
        .replace('٣', '3')
        .replace('٤', '4')
        .replace('٥', '5')
        .replace('٦', '6')
        .replace('٧', '7')
        .replace('٨', '8')
        .replace('٩', '9')
        .replace(",", "")
        .replace("،", "")
        .replace(" ", "")
        .replace("\u200c", "") // ZWNJ
}

/**
 * Checks if a ledger entry matches a free-text timeline search query.
 * Matches against merchant, category, notes, transaction reference ID, bank name, card mask,
 * direction (واریز/برداشت), and formatted/raw amounts in Rial and Toman.
 */
fun matchesTimelineQuery(entry: LedgerEntry, note: String, query: String): Boolean {
    val rawQ = query.trim()
    if (rawQ.isBlank()) return true
    val q = normalizeSearchToken(rawQ)

    val bankFa = bankNameOf(entry.txn.bank)
    val dirFa = if (entry.txn.direction == "in") "واریز" else "برداشت"
    val rial = entry.txn.amountRial
    val toman = rial?.let { it / 10L }

    val targets = listOfNotNull(
        entry.txn.merchant,
        entry.txn.merchantNorm,
        entry.categoryFa,
        note,
        entry.txn.ref,
        entry.txn.refNo,
        entry.txn.mask,
        entry.txn.bank,
        bankFa,
        dirFa,
        rial?.toString(),
        toman?.toString(),
        rial?.let { faNumber(it.toDouble()) },
        toman?.let { faNumber(it.toDouble()) }
    )

    return targets.any { normalizeSearchToken(it).contains(q) }
}

/**
 * Format a human-readable Persian transaction receipt for clipboard copying and sharing.
 */
fun formatReceipt(entry: LedgerEntry, note: String = ""): String {
    val txn = entry.txn
    val amountStr = txn.amountRial?.let { "${faCompact(tomanOf(it))} تومان" } ?: "نامشخص"
    val dirStr = when (txn.direction) {
        "in" -> "واریز"
        "out" -> "برداشت"
        else -> "تراکنش"
    }
    val dateStr = if (txn.printedAt.isNotBlank()) txn.printedAt else faDay(txn.day)
    return buildString {
        appendLine("رسید تراکنش ($dirStr)")
        appendLine("مبلغ: $amountStr")
        if (txn.merchant.isNotBlank()) appendLine("طرف حساب: ${txn.merchant}")
        if (entry.categoryFa.isNotBlank()) appendLine("دسته‌بندی: ${entry.categoryFa}")
        if (txn.bank.isNotBlank()) appendLine("بانک: ${bankNameOf(txn.bank)}")
        if (txn.mask.isNotBlank()) appendLine("کارت/حساب: ${txn.mask}")
        if (txn.refNo.isNotBlank()) appendLine("شماره پیگیری: ${txn.refNo}")
        appendLine("تاریخ: $dateStr")
        if (note.isNotBlank()) appendLine("یادداشت: $note")
    }.trimEnd()
}

@Composable
fun TransactionScreen(
    entry: LedgerEntry,
    categories: List<Category>,
    hiddenCategories: Set<String> = emptySet(),
    existingNote: String,
    onCategorise: (LedgerEntry, String, Boolean) -> Unit,
    onSaveNote: (LedgerEntry, String) -> Unit,
    onPinMerchant: (String, String) -> Unit = { _, _ -> },
    onSetDirection: (LedgerEntry, String?) -> Unit = { _, _ -> },
    onHide: (LedgerEntry, Boolean) -> Unit = { _, _ -> },
    onUnpinCategory: (LedgerEntry) -> Unit = {},
    onDeletePermanently: (LedgerEntry) -> Unit = {},
    onLoadSource: suspend (LedgerEntry) -> String,
    onToggleTransfer: (LedgerEntry) -> Unit = {},
    gold18RateToman: Double? = null,
    usdtRateToman: Double? = null,
    hourlyWageToman: Long = 0L,
    allEntries: List<LedgerEntry> = emptyList(),
    onBack: () -> Unit,
) {
    val txn = entry.txn
    val incoming = txn.direction == "in"
    var learnSimilar by rememberSaveable(txn.ref) { mutableStateOf(txn.merchantNorm.isNotBlank()) }
    var chosen by remember(txn.ref) { mutableStateOf(entry.categoryId) }
    var source by remember(txn.ref) { mutableStateOf<String?>(null) }
    var confirmDelete by remember(txn.ref) { mutableStateOf(false) }

    // The two free-text fields save themselves: a debounce while typing, a final flush when
    // the screen leaves. No «ذخیره یادداشت» / «ذخیره نام» buttons.
    val noteState = rememberAutoSave(txn.ref, existingNote, onSave = { text -> onSaveNote(entry, text) })
    val merchantState = rememberAutoSave(txn.ref, txn.merchant, onSave = { text ->
        onPinMerchant(txn.merchantNorm, text)
    })
    DisposableEffect(txn.ref) {
        onDispose {
            noteState.flush()
            merchantState.flush()
        }
    }

    LaunchedEffect(txn.ref) { source = onLoadSource(entry) }
    LaunchedEffect(entry.categoryId) { chosen = entry.categoryId }

    val details = buildList {
        add("نوع" to when (txn.direction) {
            "in" -> "واریز"
            "out" -> "برداشت"
            else -> "نامشخص"
        })
        entry.ownerName.takeIf { it.isNotBlank() }?.let { add("صاحب تراکنش" to it) }
        entry.categoryEditorName.takeIf { it.isNotBlank() }?.let { add("دسته‌بندی توسط" to it) }
        // No «تاریخ» row: the field above states the day, and «زمان ثبت» carries the exact one.
        add("ثبت شده از" to if (txn.sourceKind == "manual") "ورود دستی" else bankNameOf(txn.bank))
        txn.printedAt.takeIf { it.isNotBlank() }?.let { add("زمان ثبت" to bidi(it)) }
        txn.mask.takeIf { it.isNotBlank() }?.let { mask ->
            add("کارت یا حساب" to bidi(mask))
            lookupBankByCardBin(mask)?.let { binInfo ->
                add("بانک صادرکننده کارت" to "${binInfo.bankNameFa} (${binInfo.shortNameFa})")
            }
        }
        txn.refNo.takeIf { it.isNotBlank() }?.let { add("شماره پیگیری" to bidi(it)) }
        txn.balanceRial?.let { add("مانده بعد از تراکنش" to bidi("${faCompact(tomanOf(it))} تومان")) }
        txn.feeRial?.takeIf { it > 0 }?.let { add("کارمزد" to bidi("${faCompact(tomanOf(it))} تومان")) }
        if (txn.direction == "out") {
            val amountToman = (txn.amountRial ?: 0L) / 10L
            val energy = calculateLifeEnergy(amountToman, customHourlyWageToman = hourlyWageToman.takeIf { it > 0 })
            if (energy.hours > 0 || energy.minutes > 0) {
                add("ارزش زمان کاری" to "معادل ${energy.formattedFa} کار")
            }
            val goldBenchmark = calculateGoldSpendBenchmark(amountToman, gold18RateToman)
            if (goldBenchmark.grams18k > 0.0) {
                add("معادل طلای ۱۸ عیار" to "معادل ${goldBenchmark.formattedGramsFa}")
            }
        }
    }
    val choices = categoryChoices(categories, txn.direction, hiddenCategories)

    val isImpulse = remember(entry.txn.ref, allEntries) {
        ImpulseClusterDetector.isPartOfCluster(entry, allEntries)
    }
    val amountToman = (txn.amountRial ?: 0L) / 10L
    val goldBench = remember(amountToman, gold18RateToman) {
        if (!incoming && amountToman > 0L) calculateGoldSpendBenchmark(amountToman, gold18RateToman) else null
    }
    val usdtRate = usdtRateToman?.takeIf { it > 0.0 } ?: 95_000.0
    val usdtUnits = remember(amountToman, usdtRate) {
        if (!incoming && amountToman > 0L) amountToman.toDouble() / usdtRate else 0.0
    }

    // The hero bleeds to both edges and under the status bar, so the gutter belongs to the
    // items rather than to the list.
    val gutter = Modifier.padding(horizontal = Space.xl)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = Space.xxl),
        ) {
            item(key = "hero") {
                TransactionHero(
                    entry = entry,
                    incoming = incoming,
                    onBack = onBack,
                )
            }

            item(key = "receipt_actions") {
                val context = LocalContext.current
                Row(
                    modifier = gutter.padding(top = Space.m),
                    horizontalArrangement = Arrangement.spacedBy(Space.m),
                ) {
                    OutlinedButton(
                        onClick = {
                            val receipt = formatReceipt(entry, noteState.value)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("رسید تراکنش", receipt))
                            Toast.makeText(context, "رسید در کلیپ‌بورد کپی شد", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.width(Space.xs))
                        Text("کپی رسید", fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            val receipt = formatReceipt(entry, noteState.value)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, receipt)
                            }
                            context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری رسید"))
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null)
                        Spacer(Modifier.width(Space.xs))
                        Text("اشتراک‌گذاری", fontSize = 13.sp)
                    }
                }
            }

            // Impulse shopping cluster detection banner
            if (isImpulse) {
                item(key = "impulse_spree_badge") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(gutter)
                            .padding(top = Space.m),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(Radius.card),
                    ) {
                        Row(
                            modifier = Modifier.padding(Space.m),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("⚡", fontSize = 22.sp)
                            Spacer(Modifier.width(Space.s))
                            Column {
                                Text(
                                    "خرید متوالی و پرسرعت (خوشه تکانه‌ای)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                                Text(
                                    "این تراکنش در یک بازه زمانی فشرده (کمتر از ۲ ساعت) همگام با چند خرید دیگر انجام شده است.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }
                }
            }

            // Opportunity cost comparison card for outflows
            if (!incoming && amountToman > 0L && goldBench != null) {
                item(key = "opportunity_cost_card") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(gutter)
                            .padding(top = Space.m),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(Radius.card),
                    ) {
                        Column(modifier = Modifier.padding(Space.m)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚖️", fontSize = 16.sp)
                                Spacer(Modifier.width(Space.s))
                                Text(
                                    "ارزش دارایی جایگزین (هزینه فرصت)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                "معادل این هزینه در بازار دارایی‌های امن و ضدتورمی:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(Space.s))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.s),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(Radius.field))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .padding(Space.s),
                                ) {
                                    Column {
                                        Text("طلای ۱۸ عیار", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "${faDecimal(goldBench.grams18k, if (goldBench.grams18k >= 1.0) 2 else 3)} گرم",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Hero.gold,
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(Radius.field))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .padding(Space.s),
                                ) {
                                    Column {
                                        Text("تتر / دلار", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "${faDecimal(usdtUnits, if (usdtUnits >= 10.0) 1 else 2)} USDT",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF2E9E5B),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "transfer_toggle") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (entry.transfer) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    shape = RoundedCornerShape(Radius.card),
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(gutter)
                        .padding(top = Space.m),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = entry.transfer,
                                role = Role.Switch,
                                onValueChange = { onToggleTransfer(entry) },
                            )
                            .padding(Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "انتقال بین حساب‌های خودی",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (entry.transfer)
                                    "این تراکنش جابجایی بین حساب‌های خود شماست و در مخارج یا درآمد محاسبه نمی‌شود."
                                else
                                    "اگر این پول بین کارت‌های خودتان جابجا شده، روشن کنید تا خرج حساب نشود.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(Space.m))
                        Switch(
                            checked = entry.transfer,
                            onCheckedChange = null,
                        )
                    }
                }
            }

            item(key = "category_heading") {
                Column(gutter) {
                    Spacer(Modifier.height(Space.xxl))
                    Text(
                        "دسته‌بندی",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        when {
                            entry.transfer -> "این مورد انتقال تشخیص داده شده. با انتخاب یک دسته می‌تونی اصلاحش کنی."
                            entry.needsReview -> "دسته درست رو بزن تا این مورد تایید بشه."
                            else -> "دسته فعلی ${entry.categoryFa} است. برای تغییر، دسته تازه رو بزن."
                        },
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.m))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.field))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .toggleable(
                                value = learnSimilar,
                                role = Role.Switch,
                                onValueChange = { learnSimilar = it },
                            )
                            .padding(Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "برای موارد مشابه هم همین دسته",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "موارد قبلی و بعدی مشابه هم اصلاح می‌شن",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(Space.m))
                        Switch(checked = learnSimilar, onCheckedChange = null)
                    }
                    Spacer(Modifier.height(Space.l))
                }
            }

            item(key = "categories") {
                // Pills that wrap, not a stack of full-width rows: ten categories are three
                // lines instead of ten screens' worth of scrolling, and the one already on
                // this transaction is visible without hunting for it.
                FlowRow(
                    Modifier.fillMaxWidth().then(gutter),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    choices.forEach { category ->
                        CategoryChip(
                            category = category,
                            selected = category.id == chosen,
                            onClick = {
                                chosen = category.id
                                onCategorise(entry, category.id, learnSimilar)
                            },
                        )
                    }
                }
            }

            if (txn.merchantNorm.isNotBlank()) {
                item(key = "merchant_alias") {
                    Column(gutter) {
                        Spacer(Modifier.height(Space.xxl))
                        Text(
                            "نام فروشگاه",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.semantics { heading() },
                        )
                        Spacer(Modifier.height(Space.m))
                        OutlinedTextField(
                            value = merchantState.value,
                            onValueChange = { merchantState.onChange(it.take(40)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(txn.merchant.ifBlank { "نام فروشگاه..." }) },
                            maxLines = 2,
                        )
                        if (merchantState.justSaved) {
                            Text(
                                "ذخیره شد",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(
                            // The alias renames everywhere this source appears, past and future.
                            // merchantNorm stays the source string so classification is untouched.
                            "تغییر نام برای همه تراکنش‌هایی که همین نام را دارند اعمال می‌شود.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "notes") {
                Column(gutter) {
                    Spacer(Modifier.height(Space.xxl))
                    Text(
                        "یادداشت",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Space.m))
                    OutlinedTextField(
                        value = noteState.value,
                        onValueChange = { noteState.onChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("یادداشت خود را بنویسید...") },
                        maxLines = 3,
                    )
                    if (noteState.justSaved) {
                        Text(
                            "ذخیره شد",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item(key = "corrections") {
                Column(gutter) {
                    Spacer(Modifier.height(Space.xxl))
                    Text(
                        "اصلاح",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Space.m))

                    // Direction — the manual fix for «deposit read as withdrawal».
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.card))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = Space.l, vertical = Space.m),
                    ) {
                        Text("جهت مبلغ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Space.s))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.s),
                        ) {
                            FilterChip(
                                selected = incoming,
                                onClick = { onSetDirection(entry, "in") },
                                label = { Text("دریافتی", fontSize = 12.sp) },
                            )
                            FilterChip(
                                selected = !incoming,
                                onClick = { onSetDirection(entry, "out") },
                                label = { Text("پرداختی", fontSize = 12.sp) },
                            )
                            if (entry.categoryId != CAT_UNCATEGORISED) {
                                FilterChip(
                                    selected = false,
                                    onClick = { onUnpinCategory(entry) },
                                    label = { Text("بازگشت به خودکار", fontSize = 12.sp) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.s))
                    Text(
                        "تغییر جهت، دسته‌ها و جمع کل را هماهنگ اصلاح می‌کند.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.s))
                    TextButton(onClick = { onHide(entry, true) }) {
                        Text("پنهان کردن از دفتر", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("حذف دائمی", color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        "حذف دائمی پیام اصلی را هم پاک می‌کند؛ ده ثانیه فرصت تعویض داری.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "details") {
                Column(gutter) {
                    Spacer(Modifier.height(Space.xxl))
                    Text(
                        "جزئیات",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Space.m))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.card))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = Space.l, vertical = Space.m),
                    ) {
                        details.forEach { (label, value) -> DetailRow(label, value) }
                    }
                }
            }

            item(key = "source") {
                Column(gutter) {
                    Spacer(Modifier.height(Space.xxl))
                    Text(
                        "منبع",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Space.m))
                    SelectionContainer {
                        Text(
                            when {
                                txn.sourceKind == "manual" -> "این مورد دستی ثبت شده."
                                txn.ownerMemberId.isNotBlank() ->
                                    "متن خام پیامک فقط روی گوشی ${entry.ownerName.ifBlank { "صاحب تراکنش" }} نگه داشته می‌شه."
                                source == null -> "در حال خواندن پیامک اصلی..."
                                source!!.isBlank() -> "متن پیامک اصلی پیدا نشد."
                                else -> source!!
                            },
                            fontSize = 13.sp,
                            lineHeight = 23.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.panel(),
                        )
                    }
                }
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("حذف دائمی") },
                text = {
                    Text(
                        "این تراکنش و پیام اصلیش برای همیشه حذف می‌شوند و از پیامک‌های قبلی هم برنمی‌گردند. " +
                            "تا ده ثانیه می‌توانی تعویض کنی."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { confirmDelete = false; onDeletePermanently(entry) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("حذف") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text("لغو") }
                },
            )
        }
    }
}

/**
 * The transaction, in the field the app keeps for a figure that stands on its own.
 *
 * This screen is about one number, and it was setting that number two sizes down from the home
 * total inside a grey card — opting out of the app's strongest device on the one screen with
 * the strongest claim to it. Gold stays the net worth's alone: up here the figure speaks in the
 * ledger's own in/out colours, because whether money came or went is the first thing to read on
 * a transaction and the last thing that should need a second glance.
 */
@Composable
private fun TransactionHero(
    entry: LedgerEntry,
    incoming: Boolean,
    onBack: () -> Unit,
    backLabel: String = "برگشت",
    /** Progress, when the transaction is one of a run being worked through. */
    note: String? = null,
) {
    val txn = entry.txn
    val tone = if (incoming) Hero.mint else Hero.strong
    HeroPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    faDay(txn.day),
                    fontSize = 15.sp,
                    color = Hero.muted,
                    modifier = Modifier.weight(1f),
                )
                note?.let {
                    Text(
                        it,
                        fontSize = 13.sp,
                        fontFamily = ModamFigures,
                        color = Hero.muted,
                    )
                }
                TextButton(onClick = onBack) {
                    Text(backLabel, color = Hero.strong, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(Space.s))
            Text(
                txn.merchant.ifBlank {
                    if (txn.sourceKind == "manual") "مورد دستی" else bankNameOf(txn.bank)
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Hero.strong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )

            Spacer(Modifier.height(Space.xs))
            txn.amountRial?.let { rial ->
                val toman = tomanOf(rial)
                BasicText(
                    text = heroFigure(buildAnnotatedString {
                        // Three steps down, and the first one is what keeps the sign a sign:
                        // set the same size as the digits, the magnitude turns «−» into a dash
                        // joining two words. The sign travels with the digits; «میلیون» and
                        // «تومان» stay in the RTL line so the phrase still ends where a Persian
                        // sentence ends.
                        val (digits, magnitude) = faSignedParts(toman, incoming)
                        append(digits)
                        // Each word space carries a size of its own; inside a shrunk span it
                        // shrinks with it and the words end up touching.
                        magnitude?.let {
                            withStyle(SpanStyle(fontSize = 0.9.em)) { append(" ") }
                            withStyle(
                                SpanStyle(fontSize = 0.58.em, fontWeight = FontWeight.Bold),
                            ) { append(it) }
                        }
                    }, tone),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 52.sp),
                    style = figureStyle(tone, FontWeight.Black),
                )
                // faCompact truncates, so the words are the only exact figure on this screen —
                // and the check against reading a میلیون as a میلیارد.
                faWordsToman(toman)?.let { words ->
                    Text(
                        words,
                        fontSize = 14.sp,
                        lineHeight = 24.sp,
                        color = Hero.strong.copy(alpha = 0.93f),
                        modifier = Modifier.padding(top = Space.s),
                    )
                }
            } ?: Text(
                "مبلغ در پیامک مشخص نشده",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                // The field's one caution colour: gold up here already means «the answer».
                color = Hero.warn,
                modifier = Modifier.padding(top = Space.s),
            )
    }
}

/**
 * The chosen category wears the same filled pill the selected tab does — the app already says
 * "this one" in `primary`, and the screen whose whole job is choosing was saying it in a
 * container colour a shade off the unselected ones.
 *
 * Tapping writes immediately, so the colour landing over [Motion.fast] is the only receipt the
 * choice gets. It is the one authored moment on the screen; a snap reads as a redraw.
 */
@Composable
private fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    // What «selected» means differs by screen: on a transaction it is what the entry is filed
    // as; in the deck it is what she has just picked and not yet confirmed.
    selectedLabel: String = "دسته فعلی",
) {
    val fill by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        tween(Motion.fast, easing = Motion.enter),
        label = "chipFill",
    )
    val ink by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        tween(Motion.fast, easing = Motion.enter),
        label = "chipInk",
    )
    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            // The pill is sized by its label, so the tap target needs a floor of its own.
            .heightIn(min = 48.dp)
            .padding(horizontal = Space.m)
            .semantics {
                contentDescription = category.nameFa + if (selected) ", $selectedLabel" else ""
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The mark, in the same ink as the label: a second colour here would make the grid
            // eleven competing objects rather than one grid she reads at a glance.
            CategoryIcon(category.nameFa, ink)
            Text(
                category.nameFa,
                fontSize = 14.sp,
                // One weight for every state. Modam's weight axis changes the advance, so a bold
                // selected label is a wider pill — and the whole grid reflowed under her finger on
                // the tap that chose it. The fill says «this one» louder than a weight step could.
                fontWeight = FontWeight.SemiBold,
                color = ink,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.s),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTxnSheet(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (amountRial: Long, isExpense: Boolean, categoryId: String?, merchant: String, note: String, at: Long) -> Unit,
) {
    var isExpense by remember { mutableStateOf(true) }
    var amountText by remember { mutableStateOf("") }
    var isToman by remember { mutableStateOf(true) }
    var merchant by remember { mutableStateOf("") }
    var selectedCatId by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }

    val rawAmount = amountText.replace(",", "").replace(" ", "").toLongOrNull() ?: 0L
    val amountRial = if (isToman) rawAmount * 10L else rawAmount
    val amountToman = if (isToman) rawAmount else rawAmount / 10L

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // The sheet's own settle spring was eating the list's leftover fling and
                // bouncing: every scroll to the end jittered. The guard swallows upward
                // leftovers exactly as it does on the other sheets.
                .nestedScroll(SheetFlingGuard)
                .padding(horizontal = Space.l)
                .padding(bottom = Space.xl)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "ثبت تراکنش دستی",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = Space.l),
            )

            // Direction toggle: هزینه vs دریافت
            Row(
                Modifier.fillMaxWidth().padding(bottom = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.m),
            ) {
                FilterChip(
                    selected = isExpense,
                    onClick = { isExpense = true },
                    label = { Text("هزینه (پرداخت)") },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = !isExpense,
                    onClick = { isExpense = false },
                    label = { Text("درآمد (واریز)") },
                    modifier = Modifier.weight(1f),
                )
            }

            // Amount input + unit toggle
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { c -> c.isDigit() || c == ',' } },
                label = { Text("مبلغ") },
                trailingIcon = {
                    TextButton(onClick = { isToman = !isToman }) {
                        Text(if (isToman) "تومان" else "ریال", fontWeight = FontWeight.Bold)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.xs),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            if (rawAmount > 0) {
                Text(
                    if (isToman) "معادل ${faNumber(amountRial.toDouble())} ریال"
                    else "معادل ${faNumber(amountToman.toDouble())} تومان",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Space.m),
                )
            } else {
                Spacer(Modifier.height(Space.m))
            }

            // Merchant / Title
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("طرف حساب / شرح") },
                placeholder = { Text(if (isExpense) "مثال: نانوایی، اسنپ، خرید سوپرمارکت" else "مثال: حقوق، واریز نقدی") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
                singleLine = true,
            )

            // Categories
            Text("دسته‌بندی (اختیاری)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            selectedCatId?.let { catId ->
                val necessity = NecessityType.classifyCategory(catId)
                Text(
                    "طبقه‌بندی بودجه ۵۰/۳۰/۲۰: ${necessity.emoji} ${necessity.titleFa} (هدف: ${faNumber(necessity.targetPercent.toDouble())}٪)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp, bottom = Space.xs),
                )
            } ?: Spacer(Modifier.height(Space.xs))
            val availableCats = categoryChoices(categories, if (isExpense) "out" else "in")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.m),
            ) {
                availableCats.forEach { cat ->
                    FilterChip(
                        selected = selectedCatId == cat.id,
                        onClick = {
                            selectedCatId = if (selectedCatId == cat.id) null else cat.id
                        },
                        label = { Text(cat.nameFa, fontSize = 12.sp) },
                    )
                }
            }

            // Note
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("یادداشت (اختیاری)") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.l),
                maxLines = 2,
            )

            Button(
                onClick = {
                    if (amountRial > 0 && merchant.isNotBlank()) {
                        onSave(amountRial, isExpense, selectedCatId, merchant, note, System.currentTimeMillis())
                    }
                },
                enabled = amountRial > 0 && merchant.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("ثبت تراکنش")
            }
        }
    }
}

/**
 * Banner alerting user to failed debit transactions awaiting auto-reversal within 72 hours.
 */
@Composable
internal fun FailedTxnWatchdogBanner(
    watchdogs: List<FailedTxnWatchdog>,
    onFilterRef: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unresolved = watchdogs.filter { it.status != WatchdogStatus.REFUNDED }
    if (unresolved.isEmpty()) return

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xl, vertical = Space.s)
            .clip(RoundedCornerShape(Radius.card))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(Radius.card))
            .padding(Space.m),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(Space.s))
                Text(
                    "دیده‌بان تراکنش ناموفق (${bidi(unresolved.size.toString())} مورد در انتظار بازگشت وجه)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(Space.xs))

            unresolved.forEach { item ->
                val bankName = bankNameOf(item.bank)
                val toman = item.amountRial / 10L
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Space.xs)
                        .clickable { onFilterRef(item.ref) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$bankName: ${faCompact(toman.toDouble())} تومان",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (item.status == WatchdogStatus.OVERDUE) "⚠️ بیش از ۷۲ ساعت گذشته (نیاز به پیگیری)"
                        else "⏳ ${bidi(item.hoursRemaining.toString())} ساعت تا بازگشت خودکار",
                        fontSize = 11.sp,
                        color = if (item.status == WatchdogStatus.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Visual spending velocity & 14-day daily outflow sparkline card.
 */
internal data class DailyOutflowPoint(val day: Long, val toman: Double)

internal fun computeDailyOutflowPoints(
    entries: List<LedgerEntry>,
    todayEpochDay: Long,
    daysCount: Int = 14,
): List<DailyOutflowPoint> {
    val expenseEntries = entries.filter { it.txn.direction == "out" && !it.duplicate && !it.transfer }
    return ((daysCount - 1) downTo 0).map { daysAgo ->
        val d = todayEpochDay - daysAgo
        var sumRial = 0L
        for (e in expenseEntries) {
            if (e.txn.day == d) {
                sumRial += (e.txn.amountRial ?: 0L)
            }
        }
        DailyOutflowPoint(day = d, toman = sumRial / 10.0)
    }
}

@Composable
internal fun DailyOutflowSparkCard(
    entries: List<LedgerEntry>,
    todayEpochDay: Long,
    modifier: Modifier = Modifier,
    privacyMode: Boolean = false,
) {
    val dayPoints: List<DailyOutflowPoint> = remember(entries, todayEpochDay) {
        computeDailyOutflowPoints(entries, todayEpochDay, 14)
    }

    val total14DayToman = remember(dayPoints) { dayPoints.sumOf { it.toman } }
    if (total14DayToman <= 0.0) return

    val maxDayToman = remember(dayPoints) { dayPoints.maxOf { it.toman }.coerceAtLeast(1.0) }
    val peakPoint = remember(dayPoints) { dayPoints.maxByOrNull { it.toman } }
    var selectedPoint by remember { mutableStateOf<DailyOutflowPoint?>(null) }
    val streak = remember(entries, todayEpochDay) { calculateNoSpendStreak(entries, todayEpochDay) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xl, vertical = Space.xs),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(Space.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "روند مخارج ۱۴ روز گذشته",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (peakPoint != null && peakPoint.toman > 0.0) {
                        Spacer(Modifier.width(Space.s))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                if (privacyMode) "اوج: •••" else "اوج: ${faCompact(peakPoint.toman)}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Text(
                    if (privacyMode) "•••••• تومان" else "${faCompact(total14DayToman)} تومان",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(Space.s))

            // Chart area with dashed ceiling guide line and ceiling text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                // Dashed ceiling line
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.TopCenter),
                ) {
                    val pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.35f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = pathEffect,
                    )
                }

                Text(
                    if (privacyMode) "سقف: •••" else "سقف: ${faCompact(maxDayToman)} ت",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 2.dp, top = 2.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    for (point in dayPoints) {
                        val heightRatio = (point.toman / maxDayToman).toFloat().coerceIn(0.08f, 1f)
                        val isToday = point.day == todayEpochDay
                        val isPeak = point == peakPoint && point.toman > 0.0
                        val isSelected = point == selectedPoint

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    selectedPoint = if (selectedPoint == point) null else point
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(heightRatio)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.tertiary
                                            isPeak -> MaterialTheme.colorScheme.error
                                            isToday -> Hero.gold
                                            point.toman > 0.0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                        },
                                    ),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Day-of-week letter labels
            val dayNames = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (point in dayPoints) {
                    // Tehran day % 7: Saturday is (day - 2) % 7 == 0
                    val dayIdx = (((point.day - 2) % 7 + 7) % 7).toInt()
                    val dayName = dayNames.getOrElse(dayIdx) { "" }
                    val isToday = point.day == todayEpochDay
                    val isSelected = point == selectedPoint
                    Text(
                        text = dayName,
                        fontSize = 9.sp,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.tertiary
                            isToday -> Hero.gold
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Interactive inspection chip when tapping a bar
            selectedPoint?.let { sel ->
                val selDayIdx = (((sel.day - 2) % 7 + 7) % 7).toInt()
                val fullDayNames = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه")
                val dayNameFa = fullDayNames.getOrElse(selDayIdx) { "" }
                val dayLabel = if (sel.day == todayEpochDay) "امروز ($dayNameFa)" else "$dayNameFa ${faDay(sel.day, todayEpochDay)}"
                Spacer(Modifier.height(Space.xs))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = Space.s, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            dayLabel,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            if (privacyMode) "•••••• تومان" else if (sel.toman > 0.0) "${faNumber(sel.toman)} تومان خرج" else "روز بدون خرج 🌿",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sel.toman > 0.0) MaterialTheme.colorScheme.error else Color(0xFF2E9E5B),
                        )
                    }
                }
            }

            // No-Spend discipline and mindfulness badge
            Spacer(Modifier.height(Space.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2E9E5B).copy(alpha = 0.08f))
                    .padding(horizontal = Space.s, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "🌿 ${streak.motivationFa}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2E9E5B),
                    modifier = Modifier.weight(1f),
                )
                if (streak.currentStreakDays > 0) {
                    Text(
                        "${faNumber(streak.currentStreakDays)} روز",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E9E5B),
                    )
                }
            }
        }
    }
}
