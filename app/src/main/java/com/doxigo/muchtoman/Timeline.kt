package com.doxigo.muchtoman

import androidx.activity.compose.BackHandler

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onReview: () -> Unit,
    onOpen: (LedgerEntry) -> Unit,
    onMarkCredit: (LedgerEntry) -> Unit,
    onLinkInstallment: (LedgerEntry) -> Unit,
    onExport: () -> Unit,
    onBulkCategorise: (List<LedgerEntry>, String) -> Unit = { _, _ -> },
    onBulkHide: (List<LedgerEntry>, Boolean) -> Unit = { _, _ -> },
    onBulkFlip: (List<LedgerEntry>) -> Unit = {},
    onBulkDelete: (List<LedgerEntry>) -> Unit = {},
    onHide: (LedgerEntry, Boolean) -> Unit = { _, _ -> },
    onToggleShowHidden: () -> Unit = {},
) {
    val today = remember { tehranDay(System.currentTimeMillis()) }
    // Without the reveal, hidden rows leave the ledger entirely; with it, they come back
    // struck through and tapping one restores it.
    val visible = remember(ledger.entries, ledger.hiddenEntries, state.showHidden) {
        val base = ledger.entries.filterNot { it.duplicate }
        if (state.showHidden) base + ledger.hiddenEntries else base
    }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    // Selection mode. A long press (or the select button in the search row) enters it; rows
    // then carry a checkbox, the header becomes a batch bar, and "select all" fills the
    // current filter result. Selected refs are tracked here so bulk actions can consume them.
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    val filtered = remember(visible, searchQuery, selectedCategoryId) {
        visible.filter { entry ->
            val matchesSearch = searchQuery.isBlank() ||
                entry.txn.merchant.contains(searchQuery, ignoreCase = true) ||
                entry.categoryFa.contains(searchQuery, ignoreCase = true) ||
                (entry.txn.amountRial?.let { faNumber(it / 10.0).contains(searchQuery) } == true)
            val matchesCategory = selectedCategoryId == null || entry.categoryId == selectedCategoryId
            matchesSearch && matchesCategory
        }
    }

    val grouped = remember(filtered) { filtered.groupBy { it.txn.day }.toSortedMap(compareByDescending { it }) }
    val waiting = ledger.review.size

    // Back exits selection mode before it ever leaves the timeline.
    if (selecting) {
        BackHandler {
            selecting = false
            selected = emptySet()
        }
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
                    IconButton(onClick = onExport) {
                        Icon(Icons.Rounded.Share, contentDescription = "خروجی CSV")
                    }
                    if (waiting > 0) ReviewPill(waiting, onReview)
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

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Space.xl),
                placeholder = { Text("جستجو...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (selecting) {
                        // In selection mode the search bar also carries the "select" entry and
                        // acts as the batch entry point.
                        IconButton(onClick = { selecting = false; selected = emptySet() }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "پاک کردن")
                        }
                    } else if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "پاک کردن")
                        }
                    }
                },
                singleLine = true,
            )

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

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(Space.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "موردی یافت نشد",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            LazyColumn(
                Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(
                    start = Space.xl, end = Space.xl, bottom = Space.l,
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
                        )
                    }
                }
            }

            // The batch action bar: appears only while something is selected. Category filing is
            // the first bulk action; the same refs are what later bulk hide/delete will consume.
            if (selecting && selected.isNotEmpty()) {
                val rows = filtered.filter { it.txn.ref in selected }
                BatchActionBar(
                    selectedRefs = selected,
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
 * The bottom bar while transactions are selected: category filing, hide, flip direction, and
 * permanent delete — each over every selected row, with one confirmation/undo each.
 */
@Composable
private fun BatchActionBar(
    selectedRefs: Set<String>,
    categories: List<Category>,
    hiddenCategories: Set<String>,
    onPickCategory: (String) -> Unit,
    /** The count shown before a bulk action applies it — ticket 12/13 confirmations. */
    onHide: () -> Unit,
    onFlip: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.xl, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Text(
                "${selectedRefs.size} مورد انتخاب شده",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) { Text("لغو", fontSize = 12.sp) }
        }
        // A second row of actions keeps the bar from turning into a keyboard of buttons.
        // Three actions plus the count would crowd one line; the actions read better than the
        // count and deserve the width.
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.xl, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            TextButton(onClick = { showCategoryPicker = true }) { Text("دسته‌بندی", fontSize = 13.sp) }
            TextButton(onClick = onHide) { Text("پنهان", fontSize = 13.sp) }
            TextButton(onClick = onFlip) { Text("جابجایی جهت", fontSize = 13.sp) }
            TextButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("حذف", fontSize = 13.sp) }
        }
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
                faSignedCompact(tomanOf(net), net > 0),
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
            .background(MaterialTheme.colorScheme.surfaceVariant),
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
            )
        }
    }
}

/**
 * The one thing asking for her, on the title line rather than across the page.
 *
 * It was a full-width card, and a card is what the app uses for something to read — so the only
 * control on the screen was wearing the costume of content, taking a whole band of the scroll to
 * say what the tab bar already says with a badge. A pill in the empty half of the title line
 * says it in the app's own «this one» gold, names its action instead of describing a state, and
 * gives the day bands the top of the list back.
 */
@Composable
private fun ReviewPill(waiting: Int, onReview: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(role = Role.Button, onClick = onReview)
            .heightIn(min = 48.dp)
            .padding(horizontal = Space.l),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "مرور ${faNumber(waiting.toDouble())} مورد",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
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
) {
    val txn = entry.txn
    val incoming = txn.direction == "in"
    val tone = when {
        entry.transfer -> MaterialTheme.colorScheme.onSurfaceVariant
        incoming -> Color(0xFF2E9E5B)
        else -> MaterialTheme.colorScheme.onBackground
    }
    var menuOpen by remember { mutableStateOf(false) }
    val isCreditLinked = state.obligations.any { it.linkedTxnRef == txn.ref }
    val hasUnpaidInstallments = state.obligationPayments.values.any { payments ->
        payments.any { it.paidDate == null }
    }

    Row(
        Modifier
            .fillMaxWidth()
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
                    .clip(RoundedCornerShape(5.dp))
                    .then(
                        if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
                        else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp))
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
                if (entry.needsReview && !entry.transfer) {
                    Spacer(Modifier.width(Space.xs))
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
        val rial = txn.signedRial ?: txn.amountRial
        if (rial != null) {
            Text(
                faSignedCompact(tomanOf(rial), incoming),
                style = figureStyle(tone, FontWeight.Bold),
                fontSize = 15.sp,
                textDecoration = if (entry.transfer) TextDecoration.LineThrough else null,
            )
        } else {
            Text(
                "مانده",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Three-dot action menu — only for debit transactions with meaningful actions.
        if (!entry.transfer && (txn.direction == "out" || txn.direction == null)) {
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
                    if (!isCreditLinked && rial != null && rial < 0) {
                        DropdownMenuItem(
                            text = { Text("ثبت به عنوان خرید اعتباری") },
                            onClick = { menuOpen = false; onMarkCredit(entry) },
                        )
                    }
                    if (hasUnpaidInstallments && rial != null && rial < 0) {
                        DropdownMenuItem(
                            text = { Text("پیوند به قسط") },
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
    onBack: () -> Unit,
) {
    val txn = entry.txn
    val incoming = txn.direction == "in"
    var learnSimilar by rememberSaveable(txn.ref) { mutableStateOf(false) }
    var chosen by remember(txn.ref) { mutableStateOf(entry.categoryId) }
    var source by remember(txn.ref) { mutableStateOf<String?>(null) }
    var noteText by remember(txn.ref) { mutableStateOf(existingNote) }
    var merchantText by remember(txn.ref) { mutableStateOf(txn.merchant) }
    var merchantDirty by remember(txn.ref) { mutableStateOf(false) }
    var categoryDirty by remember(txn.ref) { mutableStateOf(false) }
    var confirmDelete by remember(txn.ref) { mutableStateOf(false) }

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
        txn.mask.takeIf { it.isNotBlank() }?.let { add("کارت یا حساب" to bidi(it)) }
        txn.refNo.takeIf { it.isNotBlank() }?.let { add("شماره پیگیری" to bidi(it)) }
        txn.balanceRial?.let { add("مانده بعد از تراکنش" to bidi("${faCompact(tomanOf(it))} تومان")) }
        txn.feeRial?.takeIf { it > 0 }?.let { add("کارمزد" to bidi("${faCompact(tomanOf(it))} تومان")) }
    }
    val choices = categoryChoices(categories, txn.direction, hiddenCategories)

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
                            value = merchantText,
                            onValueChange = { merchantText = it.take(40); merchantDirty = true },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(txn.merchant.ifBlank { "نام فروشگاه..." }) },
                            maxLines = 2,
                        )
                        Spacer(Modifier.height(Space.s))
                        if (merchantDirty && merchantText != txn.merchant) {
                            TextButton(onClick = { onPinMerchant(txn.merchantNorm, merchantText) }) {
                                Text("ذخیره نام")
                            }
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
                        value = noteText,
                        onValueChange = { noteText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("یادداشت خود را بنویسید...") },
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(Space.s))
                    if (noteText != existingNote) {
                        TextButton(onClick = { onSaveNote(entry, noteText) }) {
                            Text("ذخیره یادداشت")
                        }
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

/**
 * The exception deck.
 *
 * One card at a time, three answers, and «همیشه» is the one that matters — it is the difference
 * between an app that asks the same question every month and one that learns. «حالا نه» writes
 * nothing at all, on purpose: skipping must cost her nothing and must not be recorded as an
 * opinion she does not have.
 */
@Composable
fun ReviewDeck(
    ledger: LedgerView,
    hiddenCategories: Set<String> = emptySet(),
    onDecide: (LedgerEntry, String, Boolean) -> Unit,
    onWorthIt: (LedgerEntry, String) -> Unit,
    onDone: () -> Unit,
) {
    var index by remember { mutableStateOf(0) }
    var skipped by remember { mutableStateOf(setOf<String>()) }
    val pending = ledger.review.filterNot { it.txn.ref in skipped }
    val entry = pending.getOrNull(index.coerceAtMost(pending.lastIndex.coerceAtLeast(0)))

    // The picked category, cleared whenever the deck moves on: the two answers below are about
    // the card on screen, and carrying a highlight onto the next one would offer to file a
    // transaction she has not looked at yet.
    var picked by remember(entry?.txn?.ref) { mutableStateOf<String?>(null) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (entry != null) {
                TransactionHero(
                    entry = entry,
                    incoming = entry.txn.direction == "in",
                    onBack = onDone,
                    backLabel = "بستن",
                    note = "${faNumber((index + 1).toDouble())} از ${faNumber(pending.size.toDouble())}",
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .then(if (entry == null) Modifier.systemBarsPadding() else Modifier)
                    .padding(horizontal = Space.xl),
            ) {
            if (entry == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "مرور هفتگی",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDone) { Text("بستن") }
                }
            }

            if (entry == null) {
                // The exceptions are done, so the one optional question gets asked — at most
                // two a week, and only about something large she chose to buy. Asked here
                // rather than mixed into the deck because it is not a correction: nothing is
                // wrong with these, and she is not obliged to have an opinion.
                val asking = remember(ledger) {
                    worthItCandidates(
                        ledger.entries,
                        ledger.worthIt.keys,
                        tehranDay(System.currentTimeMillis()),
                        largeSpendThreshold(ledger.entries),
                    )
                }
                Spacer(Modifier.height(Space.l))
                if (asking.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "همه‌چی بررسی شد",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Spacer(Modifier.height(Space.s))
                        Text(
                            "دیگه چیزی نمونده.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    for (candidate in asking) {
                        WorthItCard(candidate, onAnswer = { onWorthIt(candidate, it) })
                        Spacer(Modifier.height(Space.m))
                    }
                    TextButton(onClick = onDone) { Text("بعداً") }
                }
                return@Column
            }

            val choices = categoryChoices(ledger.categories, entry.txn.direction, hiddenCategories)
            // The question and the choices scroll; the answers do not. Only the grid can grow
            // past a short screen, and an answer she has to go looking for is one she will not
            // give.
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(Space.xxl))
                Text(
                    // «خرج» on money that arrived is the question asking her to agree with
                    // something untrue before she has answered it.
                    when (entry.txn.direction) {
                        "in" -> "این واریز رو چی حساب کنم؟"
                        "out" -> "این خرج رو چی حساب کنم؟"
                        else -> "این تراکنش رو چی حساب کنم؟"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Space.m))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                    verticalArrangement = Arrangement.spacedBy(Space.s),
                ) {
                    choices.forEach { category ->
                        CategoryChip(
                            category = category,
                            selected = category.id == picked,
                            onClick = { picked = category.id },
                            selectedLabel = "انتخاب‌شده",
                        )
                    }
                }
                Spacer(Modifier.height(Space.l))
            }

            // Three answers, in one block at the foot of the screen, weighted the way they
            // actually differ. «همیشه» means she is never asked this again, which is the
            // entire point of the product, so it is the filled one. «فعلاً نه» writes nothing
            // at all — but it was a bare text button stranded below a screen of empty space,
            // which made the cheapest answer the hardest one to find. It belongs with the
            // other two: skipping must cost her nothing, including the cost of hunting for it.
            //
            // The two that commit need a category to commit, so they arrive with one.
            Column(Modifier.fillMaxWidth().padding(bottom = Space.m).navigationBarsPadding()) {
                AnimatedVisibility(
                    visible = picked != null,
                    enter = expandVertically(tween(Motion.medium, easing = Motion.enter)) +
                        fadeIn(tween(Motion.medium)),
                    exit = shrinkVertically(tween(Motion.fast, easing = Motion.exit)) +
                        fadeOut(tween(Motion.fast)),
                ) {
                    Column {
                        DeckAnswer(
                            label = "از این به بعد هم همین دسته",
                            weight = AnswerWeight.PRIMARY,
                            onClick = {
                                picked?.let { onDecide(entry, it, true) }
                                index = 0
                            },
                        )
                        Spacer(Modifier.height(Space.s))
                        DeckAnswer(
                            label = "فقط همین یکی",
                            weight = AnswerWeight.SECONDARY,
                            onClick = {
                                picked?.let { onDecide(entry, it, false) }
                                index = 0
                            },
                        )
                        Spacer(Modifier.height(Space.s))
                    }
                }
                DeckAnswer(
                    label = "فعلاً نه",
                    weight = AnswerWeight.QUIET,
                    onClick = { skipped = skipped + entry.txn.ref },
                )
            }
            }
        }
    }
}

/** How much of an answer it is: two of these write something, and one deliberately does not. */
private enum class AnswerWeight { PRIMARY, SECONDARY, QUIET }

/** One of the three answers at the foot of the deck. */
@Composable
private fun DeckAnswer(label: String, weight: AnswerWeight, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.pill))
            .background(
                when (weight) {
                    AnswerWeight.PRIMARY -> MaterialTheme.colorScheme.primary
                    AnswerWeight.SECONDARY -> MaterialTheme.colorScheme.surfaceVariant
                    // Not a surface of its own: it is the answer that changes nothing, and a
                    // filled shape would promise it does.
                    AnswerWeight.QUIET -> Color.Transparent
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = Space.l),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = if (weight == AnswerWeight.QUIET) FontWeight.Medium else FontWeight.Bold,
            color = when (weight) {
                AnswerWeight.PRIMARY -> MaterialTheme.colorScheme.onPrimary
                AnswerWeight.SECONDARY -> MaterialTheme.colorScheme.onSurface
                AnswerWeight.QUIET -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
