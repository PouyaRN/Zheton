package com.doxigo.muchtoman.features.smspicker

import android.content.Context
import com.doxigo.muchtoman.Bank
import com.doxigo.muchtoman.DerivedDb
import com.doxigo.muchtoman.DurableDb
import com.doxigo.muchtoman.PERMANENTLY_DELETED
import com.doxigo.muchtoman.RawSms
import com.doxigo.muchtoman.SmsSource
import com.doxigo.muchtoman.Store
import com.doxigo.muchtoman.allBalances
import com.doxigo.muchtoman.bankOf
import com.doxigo.muchtoman.deletedSourceHashes
import com.doxigo.muchtoman.derive
import com.doxigo.muchtoman.extraLookup
import com.doxigo.muchtoman.hiddenRefs
import com.doxigo.muchtoman.ledgerEntries
import com.doxigo.muchtoman.readSmsInbox
import com.doxigo.muchtoman.reconcileBalances
import com.doxigo.muchtoman.srcAddrKeyV1
import com.doxigo.muchtoman.srcHash
import com.doxigo.muchtoman.srcHashOfRef
import com.doxigo.muchtoman.updateTotalWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Android half of the picker: read the inbox, work out what is already on file, and — when
 * she confirms — hand the chosen messages to the app's existing ledger machinery.
 *
 * Everything here is glue over public app APIs on purpose. What a message means, how balances
 * fold, and what a transaction row looks like all stay in `Sms.kt`, `Ledger.kt` and
 * `Derived.kt`; duplicating any of it would give the picker a second opinion about her money.
 */

/** Everything the screen needs in one shot: the inbox, what is already known, her extra senders. */
data class SmsPickerInbox(
    val messages: List<RawSms>,
    /** Every identity the app has already dealt with — see [SmsMessageIds]. */
    val processedIds: Set<String>,
    /** Sender numbers she confirmed by hand, folded into [bankOf] by the scanner too. */
    val extra: Map<String, Bank>,
)

/**
 * Read the whole inbox and everything the app already knows about it, off the main thread.
 *
 * The processed set is the union of the four places a "dealt with" decision can live: the
 * legacy balance-fold seen-list, the durable source table the ledger is built from, the
 * permanent-delete tombstones, and the refs of hidden transactions. Missing any one of them
 * would re-offer a message the app has already booked — the exact double-count this screen
 * exists to prevent.
 *
 * `since = 0` is deliberate. The picker's premise is "every message you have, and you pick",
 * so a horizon would silently hide the older threads she is here to find.
 */
suspend fun loadPickerInbox(context: Context): SmsPickerInbox = withContext(Dispatchers.IO) {
    val app = context.applicationContext
    val store = Store(app)
    val durable = DurableDb.get(app)

    val processed = buildSet {
        addAll(store.seenSms)
        addAll(deletedSourceHashes(durable.meta().get(PERMANENTLY_DELETED)))
        addAll(hiddenRefs(durable).mapNotNull(::srcHashOfRef))
        durable.smsSource().allOldestFirst().forEach { add(it.srcHash) }
    }

    SmsPickerInbox(
        messages = readSmsInbox(app, 0L),
        processedIds = processed,
        extra = extraLookup(store.extraBankNumbers),
    )
}

/** What the writer actually did, so the screen can report it honestly instead of guessing. */
data class SmsCommitReport(
    /** Rows newly added to the durable source table. */
    val storedMessages: Int,
    /** Transaction rows the chosen messages produce, by the ledger's own rules. */
    val createdEntries: Int,
)

/**
 * The one place the host app can take ownership of the write.
 *
 * Both hooks are `null` by default and the Activity falls back to [DurableSmsPickerIngest],
 * which is built from public APIs and needs no wiring beyond the manifest entry. A host that
 * wants the picker to go through its own view-model — so balances, the widget, family sync and
 * the timeline all refresh in one step — sets [onConfirm] once at startup:
 *
 * ```
 * SmsPickerWiring.onConfirm = { chosen -> appVm.ingestChosenSms(chosen) }
 * SmsPickerWiring.onAddSender = { bank, sender -> appVm.addBankNumber(bank.name, sender) }
 * ```
 *
 * [onConfirm] receives exactly the messages she ticked, oldest first, and is called only after
 * the undo window has closed — so "undo" means the write never happened rather than a write
 * that had to be reversed.
 */
object SmsPickerWiring {
    @Volatile
    var onConfirm: ((List<RawSms>) -> Unit)? = null

    /** Injected override for "this stranger is really bank X". */
    @Volatile
    var onAddSender: ((Bank, String) -> Unit)? = null
}

/**
 * Default sender registration when nothing was injected: append the number to the bank's
 * hand-confirmed list on this phone only, and let the next reload re-classify the thread.
 *
 * It does not wind the scan watermark back the way the in-app suggestion card does. That reset
 * exists to make a *future* scan revisit the sender's old messages; here the messages are in
 * front of her and the confirmation writes the chosen ones directly, so the reset would only
 * cost a full inbox re-read.
 */
suspend fun registerPickerSender(context: Context, bank: Bank, sender: String): Unit =
    withContext(Dispatchers.IO) {
        if (sender.isBlank()) return@withContext
        val store = Store(context.applicationContext)
        val current = store.extraBankNumbers
        val numbers = current[bank.name].orEmpty()
        if (sender in numbers) return@withContext
        store.extraBankNumbers = current + (bank.name to (numbers + sender))
    }

/**
 * The writer used when the host has not injected one.
 *
 * It does exactly what ingest already does for the messages it reads on its own — same sender
 * gate, same frozen-address hash, same tombstone check, same `derive()` — with the one
 * difference this feature is about: the *set* comes from her ticks instead of a watermark.
 * That is why the privacy gate stays: a bug in the screen must never be able to store a
 * message from her family or a one-time code, so only a sender [bankOf] recognises is written.
 *
 * A message she permanently deleted stays deleted: its hash is in the tombstone set and is
 * skipped before the insert, not filtered out afterwards.
 */
class DurableSmsPickerIngest(context: Context) {
    private val app = context.applicationContext

    suspend fun commit(chosen: List<RawSms>): SmsCommitReport = withContext(Dispatchers.IO) {
        if (chosen.isEmpty()) return@withContext SmsCommitReport(0, 0)

        val durable = DurableDb.get(app)
        val derived = DerivedDb.get(app)
        val store = Store(app)
        val extra = extraLookup(store.extraBankNumbers)
        val now = System.currentTimeMillis()
        val tombstones = deletedSourceHashes(durable.meta().get(PERMANENTLY_DELETED))

        val rows = chosen
            .distinctBy { srcHash(it.from, it.body, it.at) }
            .mapNotNull { sms ->
                // The same gate ingestBankSms applies, kept here as defence in depth: the
                // screen only offers readable senders, and this makes that a guarantee.
                if (bankOf(sms.from, extra) == null) return@mapNotNull null
                val hash = srcHash(sms.from, sms.body, sms.at)
                if (hash in tombstones) return@mapNotNull null
                SmsSource(
                    srcHash = hash,
                    sender = sms.from,
                    addrKey = srcAddrKeyV1(sms.from),
                    body = sms.body,
                    at = sms.at,
                    ingestedAt = now,
                )
            }

        // What the ledger held before this commit, so the report can count what was created
        // rather than restating the size of the chosen list (which over-reported duplicates).
        val entriesBefore = ledgerEntries(derived, durable, limit = 10_000).entries.size

        // IGNORE on the primary key: a message that is already on file costs nothing and the
        // count returned here is the number of rows that were actually new.
        val stored = if (rows.isEmpty()) 0
        else durable.smsSource().insertAll(rows).count { it != -1L }

        // Always rebuild, even when every row was a duplicate. derive() is total and
        // idempotent, and "did this particular row need a rebuild" is exactly the bookkeeping
        // the ledger layer was built to avoid.
        derive(durable, derived, extra)

        // The ledger now holds the messages, but Home's bank cards and total still read the
        // persisted fold. Fold the rebuilt ledger back over it with the app's own
        // reconciliation — the same call a manual correction makes — so a transaction booked
        // here moves the number she looks at, not only the timeline.
        store.bankAccounts = reconcileBalances(store.bankAccounts, allBalances(derived, durable))
        updateTotalWidget(app)

        SmsCommitReport(
            storedMessages = stored,
            createdEntries = (ledgerEntries(derived, durable, limit = 10_000).entries.size - entriesBefore).coerceAtLeast(0),
        )
    }
}
