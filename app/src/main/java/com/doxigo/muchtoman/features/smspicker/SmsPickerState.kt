package com.doxigo.muchtoman.features.smspicker

import com.doxigo.muchtoman.Bank
import com.doxigo.muchtoman.BankSms
import com.doxigo.muchtoman.MAX_PLAUSIBLE_RIAL
import com.doxigo.muchtoman.RawSms
import com.doxigo.muchtoman.bankOf
import com.doxigo.muchtoman.guessBank
import com.doxigo.muchtoman.isMaskanHint
import com.doxigo.muchtoman.legacySmsKey
import com.doxigo.muchtoman.parseBankSms
import com.doxigo.muchtoman.senderKey
import com.doxigo.muchtoman.smsKey
import com.doxigo.muchtoman.srcHash
import kotlin.math.abs

/**
 * The SMS inbox picker's whole decision model, with no Android and no I/O in it.
 *
 * The screen is a rendering of one of these, and the batch she confirms is a pure function of
 * that state plus the inbox. That is deliberate: the four things this feature must not get
 * wrong — an empty inbox, a sender the app cannot read, a message it has already dealt with,
 * and "select all" quietly sweeping either of those in — are then testable on a laptop instead
 * of on a phone with a year of real bank messages.
 *
 * The parsing itself is not re-implemented here. [parseBankSms] answers "would the ledger make
 * a row out of this", [bankOf] answers "is this sender one of her banks", and the identity
 * functions ([smsKey], [legacySmsKey], [srcHash]) are the app's existing dedup keys. The
 * picker decides only *which* messages get offered; every rule about what a message means
 * stays in one place.
 */

/**
 * One inbox row and everything the app already knows about it.
 *
 * [read] is the parser's answer, kept so the screen can preview the transaction before it is
 * created instead of promising one and finding out later. [hint] is a Maskan purchase
 * initiation: it parses, but the ledger deliberately never makes a row of it — it only lends
 * its merchant name to the debit that follows.
 */
data class PickerMessage(
    val sms: RawSms,
    val ids: SmsMessageIds,
    val read: BankSms?,
    val hint: Boolean,
    val alreadyProcessed: Boolean,
) {
    /** The app's content identity, and the key her selection is stored under. */
    val key: String get() = ids.smsKey
    val sender: String get() = sms.from.trim()
    val at: Long get() = sms.at
    val body: String get() = sms.body

    /**
     * Whether ticking this row can change the ledger at all.
     *
     * An already-processed row is history and stays history — offering it again would produce a
     * second row or, worse, a second balance movement. A message no bank rule can read is not
     * a ledger row either: a one-time code and a message from her family both look like this,
     * and neither can be talked into a number.
     */
    val selectable: Boolean get() = !alreadyProcessed && read != null

    /**
     * Whether the ledger's own derive() will make a transaction out of it.
     *
     * Mirrors the two gates [parseBankSms]'s consumers apply: the Maskan hint is skipped on
     * purpose, and an implausible figure is dropped rather than shown as a confident total.
     * The bound is imported from the ledger, not copied, so a change there moves this preview
     * with it.
     */
    val createsEntry: Boolean get() = read != null && !hint && plausibleFigure(read)

    /** "in" / "out", or null when the message states no direction. */
    val direction: String? get() = read?.delta?.let { if (it > 0) "in" else "out" }

    /** Whole Rial, as the bank printed it; the screen divides by ten for Toman. */
    val amountRial: Long? get() = read?.amountRial
}

/**
 * The three identities one inbox row can be known by, because three parts of the app dedupe by
 * different keys and the picker has to respect all of them.
 *
 * [smsKey] is what the balance fold writes into `Store.seenSms`; [legacyKey] is what builds
 * before the sender-aware hash wrote there, and dropping it would re-offer a year of messages
 * on upgrade; [srcHash] is the primary key of `sms_source`, and also what the permanent-delete
 * tombstone and the hidden-transaction set hold. A row fails "already processed" when *any*
 * of the three is on file.
 */
data class SmsMessageIds(val smsKey: String, val legacyKey: String, val srcHash: String) {
    fun anyIn(known: Set<String>): Boolean =
        smsKey in known || legacyKey in known || srcHash in known

    companion object {
        fun of(sms: RawSms): SmsMessageIds = SmsMessageIds(
            smsKey = smsKey(sms.from, sms.body, sms.at),
            legacyKey = legacySmsKey(sms.body, sms.at),
            srcHash = srcHash(sms.from, sms.body, sms.at),
        )
    }
}

/** One sender's conversation, newest message first. */
data class SmsThread(
    /** [senderKey], so the +98 / 0 / spaced forms of one number are one thread. */
    val key: String,
    /** The spelling the newest message carried, kept verbatim for display and reply. */
    val sender: String,
    /** The bank this sender is registered to, or null for the rest of her inbox. */
    val bank: Bank?,
    /**
     * For a stranger, the bank its own words claim. A suggestion she confirms with a tap —
     * never a reading, for the same reason the sender registry exists.
     */
    val guessedBank: Bank?,
    val messages: List<PickerMessage>,
) {
    val lastMessage: PickerMessage get() = messages.first()
    val lastBody: String get() = lastMessage.body
    val lastAt: Long get() = lastMessage.at
    val count: Int get() = messages.size
    val eligibleCount: Int get() = messages.count { it.selectable }
    val processedCount: Int get() = messages.count { it.alreadyProcessed }

    /** The name to show above the number: the bank, else the guess, else an honest unknown. */
    val title: String get() = bank?.fa ?: guessedBank?.fa ?: "سرشمارهٔ ناشناس"
    val known: Boolean get() = bank != null
}

/** How much of one thread is ticked — the sender checkbox has three states, not two. */
enum class SenderSelection { NONE, SOME, ALL }

/** One thread as the current search and filter leave it. */
data class VisibleThread(val thread: SmsThread, val messages: List<PickerMessage>) {
    /** Total in the thread, including rows the filter is hiding. */
    val totalCount: Int get() = thread.count
}

/**
 * What confirming the current selection would create, in the units she thinks in.
 *
 * Produced from the selected rows only, so it can never promise a row from a message the
 * filter is merely showing.
 */
data class SmsBatchPreview(
    val messageCount: Int,
    val entryCount: Int,
    /** Maskan hints: no row of their own, but they name the merchant of the debit beside them. */
    val hintCount: Int,
    val senderCount: Int,
    val debitRial: Long,
    val creditRial: Long,
    val earliestAt: Long?,
    val latestAt: Long?,
) {
    val empty: Boolean get() = messageCount == 0
    val hasEntries: Boolean get() = entryCount > 0

    companion object {
        val EMPTY = SmsBatchPreview(0, 0, 0, 0, 0L, 0L, null, null)
    }
}

/**
 * The screen's state: the inbox grouped, plus her selection and the two filters.
 *
 * Selection is stored as message keys and is *not* cleared by a search or a filter change.
 * Hiding a row is a view decision; unticking it would be a data decision, and losing a ticked
 * message because a search was typed is exactly the kind of silent loss this screen exists to
 * avoid.
 */
data class SmsPickerState(
    val threads: List<SmsThread> = emptyList(),
    /** [SmsMessageIds.smsKey] of every ticked row. */
    val selected: Set<String> = emptySet(),
    val query: String = "",
    /** Show rows the app has already dealt with, greyed and untickable. */
    val showProcessed: Boolean = true,
) {
    val messages: List<PickerMessage> get() = threads.flatMap { it.messages }
    val messageCount: Int get() = threads.sumOf { it.count }
    val eligibleCount: Int get() = threads.sumOf { it.eligibleCount }
    val processedCount: Int get() = threads.sumOf { it.processedCount }

    /**
     * The threads the current search and filter leave, with only the matching messages in
     * them. A thread survives when its header or any one of its messages matches; the sender
     * header match keeps a number searchable even when every body is in another script.
     */
    fun visibleThreads(): List<VisibleThread> {
        val needle = foldForSearch(query)
        return threads.mapNotNull { thread ->
            val headerMatches = needle.isEmpty() ||
                foldForSearch(thread.sender).contains(needle) ||
                foldForSearch(thread.title).contains(needle)
            val shown = thread.messages.filter { message ->
                if (!showProcessed && message.alreadyProcessed) return@filter false
                headerMatches ||
                    foldForSearch(message.body).contains(needle) ||
                    foldForSearch(message.sender).contains(needle)
            }
            if (shown.isEmpty()) null else VisibleThread(thread, shown)
        }
    }

    /** Every ticked row that can still produce something, oldest first — the order the fold reads. */
    val selectedMessages: List<PickerMessage>
        get() = messages
            .asSequence()
            .filter { it.key in selected && it.selectable }
            .sortedWith(compareBy<PickerMessage> { it.at }.thenBy { it.key })
            .toList()

    /** Exactly what the confirmation hands to the writer: the chosen messages and nothing else. */
    val batch: List<RawSms> get() = selectedMessages.map { it.sms }

    val preview: SmsBatchPreview get() = previewOf(selectedMessages)

    fun isSelected(message: PickerMessage): Boolean = message.key in selected

    fun toggleMessage(message: PickerMessage): SmsPickerState {
        if (!message.selectable) return this
        return copy(selected = if (isSelected(message)) selected - message.key else selected + message.key)
    }

    /**
     * Tick or untick every selectable message of one sender. A partly ticked sender becomes
     * fully ticked, which is what a second tap on the header means.
     */
    fun toggleThread(thread: SmsThread): SmsPickerState {
        val eligible = thread.messages.asSequence().filter { it.selectable }.map { it.key }.toSet()
        if (eligible.isEmpty()) return this
        return if (eligible.all { it in selected }) {
            copy(selected = selected - eligible)
        } else {
            copy(selected = selected + eligible)
        }
    }

    fun selectionOf(thread: SmsThread): SenderSelection {
        val eligible = thread.messages.filter { it.selectable }
        if (eligible.isEmpty()) return SenderSelection.NONE
        val ticked = eligible.count { it.key in selected }
        return when {
            ticked == 0 -> SenderSelection.NONE
            ticked == eligible.size -> SenderSelection.ALL
            else -> SenderSelection.SOME
        }
    }

    /**
     * "Select all" as the header checkbox means it: everything currently in view. A search is a
     * narrowing, so selecting only what survived it is the least surprising reading.
     */
    fun selectAllVisible(): SmsPickerState {
        val keys = visibleThreads()
            .flatMap { view -> view.messages.filter { it.selectable }.map { it.key } }
            .toSet()
        return copy(selected = selected + keys)
    }

    /**
     * "Select all unprocessed": every tickable row in the whole inbox, whatever the search box
     * says. Kept separate from [selectAllVisible] so the two intents never share one button's
     * surprising behaviour.
     */
    fun selectAllUnprocessed(): SmsPickerState =
        copy(selected = messages.asSequence().filter { it.selectable }.map { it.key }.toSet())

    fun clearSelection(): SmsPickerState = copy(selected = emptySet())

    fun withQuery(value: String): SmsPickerState = copy(query = value)

    fun withShowProcessed(value: Boolean): SmsPickerState = copy(showProcessed = value)

    /**
     * Rebuild the threads after a reload (a new message, a permission grant, a sender she just
     * added) and keep the ticks that are still valid. The intersection is what stops a stale
     * key from a previous inbox — or a row that has just been marked processed — riding along
     * into the batch.
     */
    fun reloaded(
        inbox: List<RawSms>,
        processed: Set<String> = emptySet(),
        extra: Map<String, Bank> = emptyMap(),
    ): SmsPickerState {
        val rebuilt = buildThreads(inbox, processed, extra)
        val stillEligible = rebuilt
            .asSequence()
            .flatMap { thread -> thread.messages.asSequence() }
            .filter { it.selectable }
            .map { it.key }
            .toSet()
        return copy(threads = rebuilt, selected = selected intersect stillEligible)
    }

    companion object {
        fun load(
            inbox: List<RawSms>,
            processed: Set<String> = emptySet(),
            extra: Map<String, Bank> = emptyMap(),
            query: String = "",
            showProcessed: Boolean = true,
        ): SmsPickerState = SmsPickerState(
            threads = buildThreads(inbox, processed, extra),
            query = query,
            showProcessed = showProcessed,
        )

        fun previewOf(chosen: List<PickerMessage>): SmsBatchPreview {
            if (chosen.isEmpty()) return SmsBatchPreview.EMPTY
            var entries = 0
            var hints = 0
            var debit = 0L
            var credit = 0L
            for (message in chosen) {
                if (message.hint) hints++
                if (!message.createsEntry) continue
                entries++
                val amount = message.amountRial ?: continue
                val delta = message.read?.delta ?: continue
                if (delta > 0) credit += amount else debit += amount
            }
            return SmsBatchPreview(
                messageCount = chosen.size,
                entryCount = entries,
                hintCount = hints,
                senderCount = chosen.map { senderKey(it.sender) }.distinct().size,
                debitRial = debit,
                creditRial = credit,
                earliestAt = chosen.minOf { it.at },
                latestAt = chosen.maxOf { it.at },
            )
        }
    }
}

/**
 * Group the inbox into sender threads.
 *
 * Grouping uses [senderKey], the app's existing sender identity, so a number that arrives as
 * +98999…, 98999… or 0999… on different messages is one conversation rather than three. A row
 * with no address is dropped: it is not a conversation and cannot be one of her banks.
 */
fun buildThreads(
    inbox: List<RawSms>,
    processed: Set<String> = emptySet(),
    extra: Map<String, Bank> = emptyMap(),
): List<SmsThread> {
    val grouped = LinkedHashMap<String, MutableList<PickerMessage>>()
    val seen = HashSet<String>()

    for (sms in inbox) {
        val from = sms.from.trim()
        if (from.isEmpty()) continue
        val ids = SmsMessageIds.of(sms)
        // The provider can hand the same row back twice — a restore, a second SMS app, a
        // rewind — and one message is one ledger row, so it must be one row here too.
        if (!seen.add(ids.smsKey)) continue
        val message = PickerMessage(
            sms = sms,
            ids = ids,
            read = parseBankSms(sms.from, sms.body, sms.at, extra),
            hint = isMaskanHint(sms.body),
            alreadyProcessed = ids.anyIn(processed),
        )
        grouped.getOrPut(senderKey(from)) { mutableListOf() } += message
    }

    return grouped
        .map { (key, threadMessages) ->
            val newestFirst = threadMessages.sortedWith(
                compareByDescending<PickerMessage> { it.at }.thenByDescending { it.key },
            )
            val newest = newestFirst.first()
            val bank = bankOf(newest.sender, extra)
            SmsThread(
                key = key,
                sender = newest.sender,
                bank = bank,
                // A stranger earns a suggestion only when one of its messages actually names a
                // bank. Guessing from any single body is how "سامانه" once read as Saman.
                guessedBank = if (bank != null) null
                else newestFirst.asSequence().mapNotNull { guessBank(it.body) }.firstOrNull(),
                messages = newestFirst,
            )
        }
        .sortedWith(
            compareByDescending<SmsThread> { it.lastAt }.thenBy { it.key },
        )
}

/**
 * How many ledger rows a batch of raw messages would produce, by the same rules derive() uses.
 *
 * Used by the writer to report what actually got created. Kept next to the preview so a change
 * to "what counts as a row" lands in both in one edit.
 */
fun ledgerEntryCount(inbox: List<RawSms>, extra: Map<String, Bank> = emptyMap()): Int =
    inbox.count { sms ->
        val read = parseBankSms(sms.from, sms.body, sms.at, extra) ?: return@count false
        !isMaskanHint(sms.body) && plausibleFigure(read)
    }

/**
 * The same plausibility bound `parseToRows` applies, imported rather than remembered: a figure
 * past ten trillion Toman is dropped by the ledger, so the picker must not promise a row it
 * will not get.
 */
private fun plausibleFigure(read: BankSms): Boolean =
    plausibleRial(read.amountRial) && plausibleRial(read.balanceRial)

private fun plausibleRial(rial: Long?): Boolean =
    rial == null || abs(rial) <= MAX_PLAUSIBLE_RIAL

/** Runs of whitespace of any flavour, including the non-breaking space carriers insert. */
private val SEARCH_WHITESPACE = Regex("[\\s\\u00a0]+")

/**
 * Case-, spelling- and digit-insensitive form for the search box.
 *
 * Persian text arrives with either ک/ي spelling, with or without a zero-width non-joiner, and
 * with three different digit sets. Comparing raw strings misses half of the matches a person
 * typing «وام» or a card's last digits expects.
 */
fun foldForSearch(raw: String): String {
    val folded = raw
        .replace('ي', 'ی')
        .replace('ك', 'ک')
        .replace("\u200c", "")
        .replace("\u200e", "")
        .replace("\u200f", "")
        .lowercase()
        .map { char ->
            when (char) {
                in '۰'..'۹' -> '0' + (char - '۰')
                in '٠'..'٩' -> '0' + (char - '٠')
                else -> char
            }
        }
        .joinToString("")
    return folded.replace(SEARCH_WHITESPACE, " ").trim()
}

/**
 * The privacy sentence this screen is required to keep verbatim. It is a constant so the copy
 * has one home and a test can assert it still says what the product promised.
 */
const val SMS_PICKER_PRIVACY_LINE =
    "پردازش روی همین گوشی؛ هیچ پیامکی جایی فرستاده نمی‌شود"

/**
 * How long the confirmation waits before it is irreversible.
 *
 * Ten seconds, the same window [com.doxigo.muchtoman] already gives a batch delete: undo means
 * "the write never ran", not "a write was inverted", so nothing touches the ledger until the
 * window closes.
 */
const val SMS_PICKER_UNDO_WINDOW_MS = 10_000L
