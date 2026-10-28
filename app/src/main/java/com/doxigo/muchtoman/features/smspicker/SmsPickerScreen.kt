package com.doxigo.muchtoman.features.smspicker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.doxigo.muchtoman.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.doxigo.muchtoman.ui.components.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doxigo.muchtoman.Bank
import com.doxigo.muchtoman.BankLogo
import com.doxigo.muchtoman.Hero
import com.doxigo.muchtoman.ModamFigures
import com.doxigo.muchtoman.Radius
import com.doxigo.muchtoman.RawSms
import com.doxigo.muchtoman.Space
import com.doxigo.muchtoman.faAgo
import com.doxigo.muchtoman.faCompact
import com.doxigo.muchtoman.faNumber
import com.doxigo.muchtoman.jalaliOf
import com.doxigo.muchtoman.snippetOf
import com.doxigo.muchtoman.tehranDay
import com.doxigo.muchtoman.tomanOf
import kotlinx.coroutines.delay

/**
 * The inbox picker screen: every thread, a tick per sender or per message, a preview of the
 * transactions that would appear, and the privacy sentence that says where the processing
 * happens.
 *
 * The screen owns only what is genuinely view state — which threads are open and the pending
 * undo window. Everything about *what* a message is or would become lives in [SmsPickerState],
 * so the same batch the preview promises is the batch handed to [onConfirm].
 *
 * Nothing is written on a tap. Confirmation starts a [undoWindowMs] timer during which the
 * batch can be called back; only when it closes does [onConfirm] run. "Undo" therefore means
 * the write never happened, which is the only kind of undo that cannot leave half a balance
 * behind.
 */
@Composable
fun SmsPickerScreen(
    state: SmsPickerState,
    onStateChange: (SmsPickerState) -> Unit,
    onConfirm: (List<RawSms>) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    /** True while the chosen batch is being written, so the screen cannot be tapped twice. */
    committing: Boolean = false,
    permissionGranted: Boolean = true,
    onRequestPermission: () -> Unit = {},
    onAddSender: (Bank, String) -> Unit = { _, _ -> },
    onClose: () -> Unit = {},
    undoWindowMs: Long = SMS_PICKER_UNDO_WINDOW_MS,
) {
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pending by remember { mutableStateOf<PendingConfirm?>(null) }
    val liveConfirm by rememberUpdatedState(onConfirm)
    val selectedCount = state.selectedMessages.size
    val preview = state.preview

    // The write itself, deferred until the window closes. If she leaves the screen first the
    // effect is cancelled with the composition and nothing was ever written.
    LaunchedEffect(pending) {
        val waiting = pending ?: return@LaunchedEffect
        val remaining = waiting.dueAt - System.currentTimeMillis()
        if (remaining > 0) delay(remaining)
        pending = null
        liveConfirm(waiting.batch)
    }

    // Ticking or unticking while the countdown runs is her answering "not these": cancel the
    // pending write rather than commit a batch that no longer matches the screen. A search or a
    // filter is only a view, so those do not cancel anything.
    val onSelectionChange: (SmsPickerState) -> Unit = { next ->
        if (pending != null) pending = null
        onStateChange(next)
    }

    // A countdown only needs to be roughly right; four repaints a second is plenty and costs
    // less than a per-frame animation nobody reads.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(pending) {
        while (pending != null) {
            now = System.currentTimeMillis()
            delay(200L)
        }
    }

    BackHandler {
        if (pending != null) pending = null else onClose()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Header(
                threadCount = state.threads.size,
                messageCount = state.messageCount,
                onClose = onClose,
            )
            PrivacyNote()

            when {
                !permissionGranted -> PermissionCard(onRequestPermission)
                loading -> LoadingCard()
                state.threads.isEmpty() -> EmptyInbox()
                else -> {
                    SearchRow(state, onStateChange, onSelectionChange, selectedCount)
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = Space.m, vertical = Space.s),
                        verticalArrangement = Arrangement.spacedBy(Space.s),
                    ) {
                        items(state.visibleThreads(), key = { it.thread.key }) { view ->
                            ThreadCard(
                                view = view,
                                state = state,
                                expanded = view.thread.key in expanded,
                                onToggleExpanded = {
                                    expanded = if (view.thread.key in expanded) {
                                        expanded - view.thread.key
                                    } else {
                                        expanded + view.thread.key
                                    }
                                },
                                onSelectionChange = onSelectionChange,
                                onAddSender = onAddSender,
                            )
                        }
                    }

                    if (!committing) {
                        val waiting = pending
                        if (waiting != null) {
                            UndoBar(waiting = waiting, now = now, onUndo = { pending = null })
                        } else {
                            ConfirmBar(
                                preview = preview,
                                selectedCount = selectedCount,
                                onConfirm = {
                                    if (preview.entryCount > 0) {
                                        pending = PendingConfirm(
                                            batch = state.batch,
                                            entryCount = preview.entryCount,
                                            dueAt = System.currentTimeMillis() + undoWindowMs,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        if (committing) {
            CommittingOverlay()
        }
    }
}

/** One confirmation waiting out its undo window. */
private data class PendingConfirm(
    val batch: List<RawSms>,
    val entryCount: Int,
    val dueAt: Long,
)

@Composable
private fun Header(threadCount: Int, messageCount: Int, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.m, end = Space.s, top = Space.s, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "همهٔ پیامک‌ها",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${faNumber(threadCount.toLong())} سرشماره · " +
                    "${faNumber(messageCount.toLong())} پیامک در گوشی",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClose) { Text("بستن") }
    }
}

/**
 * The line the product promised, kept verbatim. It sits above the list on purpose: it is the
 * answer to "where does this go", and the answer is the reason the feature is allowed at all.
 */
@Composable
private fun PrivacyNote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.xs)
            .clip(RoundedCornerShape(Radius.card))
            .background(Hero.well)
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text("🔒", fontSize = 14.sp)
        Text(
            text = SMS_PICKER_PRIVACY_LINE,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SearchRow(
    state: SmsPickerState,
    onStateChange: (SmsPickerState) -> Unit,
    onSelectionChange: (SmsPickerState) -> Unit,
    selectedCount: Int,
) {
    OutlinedTextField(
        value = state.query,
        onValueChange = { onStateChange(state.withQuery(it)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m, vertical = Space.xs),
        placeholder = { Text("جست‌وجو در فرستنده یا متن پیامک") },
        singleLine = true,
        shape = RoundedCornerShape(Radius.card),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (state.query.isNotEmpty()) {
                IconButton(onClick = { onStateChange(state.withQuery("")) }) {
                    Icon(Icons.Default.Clear, contentDescription = "پاک کردن جست‌وجو")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        FilterChip(
            selected = !state.showProcessed,
            onClick = { onStateChange(state.withShowProcessed(!state.showProcessed)) },
            label = { Text("فقط ثبت‌نشده‌ها", fontSize = 12.sp) },
        )
        Spacer(Modifier.weight(1f))
        if (selectedCount > 0) {
            TextButton(onClick = { onSelectionChange(state.clearSelection()) }) {
                Text("پاک کردن انتخاب", fontSize = 12.sp)
            }
        } else {
            TextButton(onClick = { onSelectionChange(state.selectAllVisible()) }) {
                Text("انتخاب همه", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ThreadCard(
    view: VisibleThread,
    state: SmsPickerState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSelectionChange: (SmsPickerState) -> Unit,
    onAddSender: (Bank, String) -> Unit,
) {
    val thread = view.thread
    val selection = state.selectionOf(thread)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BankLogo(
                    bank = thread.bank?.name ?: thread.guessedBank?.name ?: "",
                    size = 40.dp,
                )
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = thread.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (!thread.known) {
                            Spacer(Modifier.width(Space.xs))
                            StatusPill("ناشناخته", Hero.warn)
                        }
                    }
                    Text(
                        text = thread.sender,
                        fontSize = 12.sp,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = snippetOf(thread.lastBody),
                        fontSize = 12.sp,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        Text(
                            text = faAgo(thread.lastAt, System.currentTimeMillis()),
                            fontSize = 11.sp,
                            color = muted,
                        )
                        Text(
                            text = "${faNumber(thread.count.toLong())} پیامک",
                            fontSize = 11.sp,
                            color = muted,
                        )
                        if (thread.processedCount > 0) {
                            Text(
                                text = "${faNumber(thread.processedCount.toLong())} ثبت‌شده",
                                fontSize = 11.sp,
                                color = muted,
                            )
                        }
                    }
                }
                Checkbox(
                    checked = selection == SenderSelection.ALL,
                    onCheckedChange = { onSelectionChange(state.toggleThread(thread)) },
                    enabled = thread.eligibleCount > 0,
                    colors = CheckboxDefaults.colors(
                        checkedColor = Hero.gold,
                        uncheckedColor = muted,
                    ),
                )
            }

            if (!thread.known) {
                StrangerHint(thread, onAddSender)
            }

            if (expanded) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                )
                view.messages.forEach { message ->
                    MessageRow(message = message, state = state, onSelectionChange = onSelectionChange)
                }
                val hidden = thread.count - view.messages.size
                if (hidden > 0) {
                    Text(
                        text = "${faNumber(hidden.toLong())} پیامک دیگر با این فیلتر پنهان است",
                        fontSize = 11.sp,
                        color = muted,
                        modifier = Modifier.padding(horizontal = Space.m, vertical = Space.xs),
                    )
                }
            }
        }
    }
}

/**
 * A stranger is not a dead end. The body usually names the bank it is from — «بانک سامان»,
 * «Refah» — so that name is offered as a one-tap registration, exactly like the suggestion
 * card in the bank sheet. Confirming is what turns the thread's messages from unreadable into
 * ledger rows; the picker never does it on its own.
 */
@Composable
private fun StrangerHint(thread: SmsThread, onAddSender: (Bank, String) -> Unit) {
    val guess = thread.guessedBank
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.m, end = Space.m, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Text(
            text = if (guess != null) {
                "این سرشماره در فهرست بانک‌ها نیست؛ پیام‌ها شبیه «${guess.fa}» است."
            } else {
                "این سرشماره در فهرست بانک‌ها نیست، پس پیام‌هایش خوانده نمی‌شود."
            },
            modifier = Modifier.weight(1f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (guess != null) {
            TextButton(onClick = { onAddSender(guess, thread.sender) }) {
                Text("افزودن به ${guess.fa}", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MessageRow(
    message: PickerMessage,
    state: SmsPickerState,
    onSelectionChange: (SmsPickerState) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = message.selectable) { onSelectionChange(state.toggleMessage(message)) }
            .padding(start = Space.s, end = Space.m, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = state.isSelected(message),
            onCheckedChange = { onSelectionChange(state.toggleMessage(message)) },
            enabled = message.selectable,
            colors = CheckboxDefaults.colors(
                checkedColor = Hero.gold,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = message.body.trim(),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = faDate(message.at),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MessageStatus(message)
            }
        }
    }
}

/**
 * What the row would become, written where the row is. A tick beside a message that creates
 * nothing is the main way this screen could mislead her, so the reason is always spelled out.
 */
@Composable
private fun MessageStatus(message: PickerMessage) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val amountRial = message.amountRial
    when {
        message.alreadyProcessed -> StatusPill("قبلاً ثبت شده", muted)
        message.read == null -> StatusPill("پیامک بانکی نیست", muted)
        message.hint -> StatusPill("راهنمای خرید", Hero.gold)
        amountRial != null -> {
            val incoming = message.read?.delta?.let { it > 0 } == true
            StatusPill(
                text = (if (incoming) "واریز " else "برداشت ") +
                    faCompact(tomanOf(amountRial)) + " تومان",
                color = if (incoming) Hero.mint else Hero.warn,
            )
        }
        else -> StatusPill("بدون مبلغ", Hero.gold)
    }
}

/**
 * Blocks the screen while the batch is being written. The write takes about a second (a full
 * derive), and letting a second tap through in that window would run it twice.
 */
@Composable
private fun CommittingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(enabled = true) { },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.card),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(Space.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Hero.gold)
                Spacer(Modifier.height(Space.m))
                Text(
                    text = "در حال ثبت…",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ModamFigures,
            color = color,
            maxLines = 1,
        )
    }
}

/**
 * The preview and the confirm button. The numbers come from [SmsBatchPreview], which is
 * computed from the ticked rows only — never from what the list happens to be showing.
 */
@Composable
private fun ConfirmBar(
    preview: SmsBatchPreview,
    selectedCount: Int,
    onConfirm: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.m, vertical = Space.s),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${faNumber(selectedCount.toLong())} پیامک انتخاب شده",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${faNumber(preview.entryCount.toLong())} تراکنش",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ModamFigures,
                    color = if (preview.entryCount > 0) Hero.mint else muted,
                )
            }
            if (preview.debitRial > 0 || preview.creditRial > 0) {
                Text(
                    text = moneyLine(preview),
                    fontSize = 11.sp,
                    color = muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (selectedCount > 0 && preview.entryCount == 0) {
                Text(
                    text = "از این پیامک‌ها تراکنشی ساخته نمی‌شود",
                    fontSize = 11.sp,
                    color = muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(Space.s))
            Button(
                onClick = onConfirm,
                enabled = preview.entryCount > 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.card),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Hero.gold,
                    contentColor = Color(0xFF1B1915),
                ),
            ) {
                Text(
                    text = "ثبت ${faNumber(preview.entryCount.toLong())} تراکنش",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun UndoBar(waiting: PendingConfirm, now: Long, onUndo: () -> Unit) {
    val seconds = ((waiting.dueAt - now + 999) / 1000).coerceAtLeast(0)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${faNumber(waiting.entryCount.toLong())} تراکنش تا " +
                        "${faNumber(seconds)} ثانیه دیگر ثبت می‌شود",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "هنوز چیزی نوشته نشده است",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onUndo) {
                Text("بازگردانی", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("📩", fontSize = 40.sp)
        Spacer(Modifier.height(Space.m))
        Text(
            text = "برای دیدن پیامک‌های بانکی، اجازهٔ خواندن پیامک لازم است.",
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Space.m))
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(Radius.card),
            colors = ButtonDefaults.buttonColors(
                containerColor = Hero.gold,
                contentColor = Color(0xFF1B1915),
            ),
        ) {
            Text("اجازهٔ خواندن پیامک", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LoadingCard() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Hero.gold)
    }
}

@Composable
private fun EmptyInbox() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Space.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "هیچ پیامکی در صندوق ورودی پیدا نشد.",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** «واریز ۱٫۲ میلیون تومان · برداشت ۳۰۰ هزار تومان» — only the directions that exist. */
private fun moneyLine(preview: SmsBatchPreview): String {
    val parts = buildList {
        if (preview.creditRial > 0) {
            add("واریز ${faCompact(tomanOf(preview.creditRial))} تومان")
        }
        if (preview.debitRial > 0) {
            add("برداشت ${faCompact(tomanOf(preview.debitRial))} تومان")
        }
    }
    return parts.joinToString(" · ")
}

/**
 * «۱۴۰۵/۰۵/۰۳» for a moment. Jalali because that is the calendar the rest of the app and the
 * bank messages themselves use, and Persian digits because this screen has no numbers that
 * are meant to be copied out.
 */
private fun faDate(epochMillis: Long): String {
    val date = jalaliOf(tehranDay(epochMillis))
    val month = date.month.toString().padStart(2, '0')
    val day = date.day.toString().padStart(2, '0')
    return persianDigits("${date.year}/$month/$day")
}

private fun persianDigits(raw: String): String =
    raw.map { char -> if (char in '0'..'9') '۰' + (char - '0') else char }.joinToString("")
