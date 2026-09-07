package com.doxigo.muchtoman

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * Which reading produced a row. Bump it whenever [parseBankSms] changes what it makes of a
 * message, and the next launch rebuilds every transaction from the stored messages.
 *
 * This is what `SMS_SCHEMA` used to be, with its teeth pulled. It no longer deletes anything:
 * a rebuild is a local recompute of a few thousand rows, offline, needing no permission, and
 * leaving every anchor and every correction exactly where she left them.
 */
// 2: implausible figures are dropped rather than saturating Long. Bumping this is the whole
// mechanism — the next launch rebuilds every transaction from the stored messages, offline,
// with no inbox read and no permission, and every correction and anchor stays exactly put.
const val PARSER_VERSION = 3

private const val META_PARSER_VER = "parser_ver"
private const val META_DERIVED_AT = "derived_at"
private const val META_DERIVE_MS = "derive_ms"

/**
 * `derived.db` — everything a parser computed, and nothing else.
 *
 * Every table here is disposable by design. It is dropped and rebuilt from `sms_source` whenever
 * the reading changes, which is why it may carry a destructive fallback and why `durable.db`
 * never may. Nothing in here is referenced by a foreign key from outside this file, because
 * nothing outside this file is allowed to depend on a row that a rebuild will delete.
 */
@Database(
    entities = [Txn::class, TxnClass::class, LinkCandidate::class, LedgerMeta::class],
    // Bump this whenever anything in this file's schema changes. There are no migrations here
    // on purpose — the version going up drops every table, and the marker that says which
    // parser built these rows goes with them, so the next launch rebuilds. That is the intended
    // behaviour and not a shortcut: it costs under a second and nothing here is irreplaceable.
    version = 5,
    exportSchema = true,
)
abstract class DerivedDb : RoomDatabase() {
    abstract fun txn(): TxnDao
    abstract fun classes(): TxnClassDao
    abstract fun links(): LinkCandidateDao
    abstract fun meta(): LedgerMetaDao

    companion object {
        @Volatile private var instance: DerivedDb? = null

        fun get(context: Context): DerivedDb = instance ?: synchronized(this) {
            instance ?: Room
                .databaseBuilder(context.applicationContext, DerivedDb::class.java, "derived.db")
                // Allowed here and nowhere else: everything in this file can be rebuilt from
                // durable.db in under a second.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { instance = it }
        }
    }
}

/**
 * One transaction, as read from one message.
 *
 * [ref] is `s:<srcHash>:<seq>` — a function of the message, never of what was made of it. That
 * is what lets a correction she made a year ago survive a parser fix: the pointer is to the
 * thing that did not change.
 */
@Entity(
    tableName = "txn",
    indices = [
        Index("at"),
        Index("account_id", "at"),
    ],
)
data class Txn(
    @PrimaryKey val ref: String,
    @ColumnInfo(name = "src_hash") val srcHash: String,
    val seq: Int,
    /** When the network stamped the message. */
    val at: Long,
    /** Tehran day, precomputed — API 24's SQLite has no generated columns. */
    val day: Long,
    val bank: String,
    /**
     * The account this belongs to, which is the bank and only the bank.
     *
     * Not the printed identifier, and per-transaction rows do not change why. One real Saman
     * account prints its number on a transfer, a card mask on a purchase, and nothing at all on
     * some alerts; keying on what was printed forked it into three accounts, each anchored at
     * the same full balance, and the total added all three. [mask] and [instrument] live on the
     * row where they group card spend and key nothing.
     */
    @ColumnInfo(name = "account_id") val accountId: String,
    /** "in", "out", or null when the message did not say. */
    val direction: String?,
    /** Magnitude in whole Rial. Present even when [direction] is not. */
    @ColumnInfo(name = "amount_rial") val amountRial: Long?,
    /** [amountRial] with its sign, or null when the direction is unknown. */
    @ColumnInfo(name = "signed_rial") val signedRial: Long?,
    /** The bank's own stated مانده, which is what anchors a derived balance. */
    @ColumnInfo(name = "balance_rial") val balanceRial: Long?,
    @ColumnInfo(name = "fee_rial") val feeRial: Long?,
    val mask: String,
    val instrument: String,
    val merchant: String,
    @ColumnInfo(name = "merchant_norm") val merchantNorm: String,
    @ColumnInfo(name = "ref_no") val refNo: String,
    @ColumnInfo(name = "printed_at") val printedAt: String,
    val channel: String,
    @ColumnInfo(name = "unit_printed") val unitPrinted: String,
    val inferred: Boolean,
    @ColumnInfo(name = "parser_ver") val parserVer: Int,
    /** Stable family sync id. Blank for a transaction created on this device. */
    @ColumnInfo(name = "family_ref") val familyRef: String = "",
    /** Immutable person identity. Blank means the current local member, or no family yet. */
    @ColumnInfo(name = "owner_member_id") val ownerMemberId: String = "",
    /** `sms` or `manual`; independent from which device currently renders the row. */
    @ColumnInfo(name = "source_kind") val sourceKind: String = "sms",
)

/**
 * Notes about the derivation, kept **in `derived.db`** rather than beside the messages.
 *
 * That placement is the whole point. The marker saying "these rows came from parser version N"
 * has to be destroyed by the same thing that destroys the rows, or the two disagree — and they
 * did: bumping this database's version dropped every table while a marker over in `durable.db`
 * went on claiming the work was done, so nothing rebuilt and the ledger sat empty. Keeping the
 * marker with the thing it describes makes that unrepresentable.
 */
@Entity(tableName = "ledger_meta")
data class LedgerMeta(@PrimaryKey val k: String, val v: String)

@Dao
interface LedgerMetaDao {
    @Query("SELECT v FROM ledger_meta WHERE k = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: LedgerMeta)
}

/** An account and what it holds, as the ledger works it out. */
data class DerivedBalance(
    val accountId: String,
    val rial: Long,
    val at: Long,
    /** False when nothing has ever stated a balance, so it is a running sum and not a figure. */
    val anchored: Boolean,
)

private class Anchor(val rial: Long, val at: Long, val ref: String)

@Dao
interface TxnDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<Txn>)

    @Query("DELETE FROM txn")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM txn")
    suspend fun count(): Int

    @Query("SELECT * FROM txn ORDER BY at DESC LIMIT :limit")
    suspend fun newest(limit: Int): List<Txn>

    @Query("SELECT DISTINCT account_id FROM txn")
    suspend fun accountIds(): List<String>

    /**
     * One account's transactions, oldest first, `ref` breaking a tie on the millisecond.
     *
     * The whole account rather than an aggregate: thirteen months of one account is a few
     * hundred rows, and the arithmetic being an ordinary Kotlin function is what lets
     * [deriveBalance] be tested on a JVM instead of only on a phone.
     *
     * ponytail: loads the account. Push the sum back into SQL if an account ever holds enough
     * rows for this to show up in a frame.
     */
    @Query("SELECT * FROM txn WHERE account_id = :accountId ORDER BY at ASC, ref ASC")
    suspend fun forAccount(accountId: String): List<Txn>
}

/** Whitespace-collapsed and trimmed. The body was already letter-folded by the parser. */
fun merchantNorm(merchant: String): String =
    merchant.trim().replace(WHITESPACE, " ")

/**
 * Apply the merchant name she pinned to a source string.
 *
 * Display only, and deliberately so: [Txn.merchantNorm] stays the source name — classification
 * rules, links and reports key off it — so renaming «شاپرک» to «سوپرمارکت رفاه» changes the
 * words on the timeline without quietly re-classifying the chain.
 */
fun applyMerchantAliases(rows: List<Txn>, aliases: Map<String, String>): List<Txn> =
    if (aliases.isEmpty()) rows
    else rows.map { row -> aliases[row.merchantNorm]?.let { row.copy(merchant = it) } ?: row }

/**
 * Apply the direction she pinned to a transaction.
 *
 * Flipping «deposit read as withdrawal» back to its truth signs the movement the same way the
 * parser would have if it had read the direction right the first time: [Txn.direction] and
 * [Txn.signedRial] reverse, [Txn.amountRial] keeps its magnitude, and the stated balance is
 * untouched. Rows without a direction decision — and a decision with no direction — pass
 * straight through.
 */
fun applyDirectionOverride(rows: List<Txn>, directions: Map<String, String>): List<Txn> =
    if (directions.isEmpty()) rows
    else rows.map { row ->
        val pinned = directions[row.ref] ?: return@map row
        when (pinned) {
            "in" -> row.copy(direction = "in", signedRial = row.amountRial?.let { it })
            "out" -> row.copy(direction = "out", signedRial = row.amountRial?.let { -it })
            else -> row
        }
    }

/**
 * One stored message, read into transaction rows.
 *
 * A list because a single message can in principle describe more than one movement; today it
 * never does, so [Txn.seq] is 0 on every row there is. The shape is here so that the day one
 * bank starts batching, nothing above has to change.
 */
/**
 * Beyond this, a figure is a parse gone wrong rather than money.
 *
 * Ten trillion Toman is orders of magnitude past any personal transaction in Iran, and the
 * failure it catches is real: a twenty-one-digit run parses into a `Double` that has already
 * lost precision and then saturates `Long` at 9.2 × 10¹⁸ on the way in. A confident wrong total
 * is the one thing this app must never produce, so an implausible figure is dropped exactly the
 * way an asset with no rate is — left out, not guessed at.
 */
const val MAX_PLAUSIBLE_RIAL = 100_000_000_000_000L // 10 trillion Toman

private fun plausible(rial: Long?): Boolean =
    rial == null || (rial in -MAX_PLAUSIBLE_RIAL..MAX_PLAUSIBLE_RIAL)

// ─────────────────────── merchant-hint correlation ───────────────────────

internal data class PendingHint(
    val bank: String,
    val amountRial: Long,
    val merchant: String,
    val at: Long,
)

private const val HINT_TTL_MS = 10 * 60 * 1000L // 10 minutes

/**
 * Scan all sources for Maskan hint messages and build the pending-hint buffer. Hints are
 * identified by body shape, not by sender — the sender gate already ran at ingest time.
 * Runs once before the main parse pass.
 */
private fun scanHints(
    sources: List<SmsSource>,
    extra: Map<String, Bank>,
): MutableMap<String, MutableList<PendingHint>> {
    val buffer = mutableMapOf<String, MutableList<PendingHint>>()
    for (source in sources) {
        if (!isMaskanHint(source.body)) continue
        val bank = bankOf(source.sender, extra)?.name ?: continue
        val read = parseBankSms(source.sender, source.body, source.at, extra) ?: continue
        if (read.amountRial == null) continue
        val merchant = merchantInHint(source.body)
        if (merchant.isEmpty()) continue
        buffer.getOrPut(bank) { mutableListOf() }
            .add(PendingHint(bank, read.amountRial, merchant, source.at))
    }
    return buffer
}

/**
 * Find the closest preceding hint for this transaction. Consumes (deletes) the match so it
 * cannot be reused. Returns empty string when no unambiguous match exists.
 */
private fun consumeHint(
    bank: String,
    amountRial: Long?,
    at: Long,
    buffer: MutableMap<String, MutableList<PendingHint>>,
): String {
    if (amountRial == null) return ""
    val candidates = buffer[bank] ?: return ""
    val match = candidates
        .filter { it.amountRial == amountRial && it.at <= at && (at - it.at) <= HINT_TTL_MS }
        .minByOrNull { at - it.at }
    if (match != null) {
        candidates.remove(match)
        return match.merchant
    }
    return ""
}

internal fun parseToRows(
    source: SmsSource,
    extra: Map<String, Bank>,
    hintBuffer: MutableMap<String, MutableList<PendingHint>> = mutableMapOf(),
): List<Txn> {
    // If this is a Maskan hint, it was already scanned into the buffer — skip it entirely so
    // it never produces a ledger entry.
    if (isMaskanHint(source.body)) return emptyList()

    val read = parseBankSms(source.sender, source.body, source.at, extra) ?: return emptyList()
    if (!plausible(read.amountRial) || !plausible(read.balanceRial)) return emptyList()
    val direction = read.delta?.let { if (it > 0) "in" else "out" }

    // Enrich merchant from hint buffer when the parser found none on its own.
    val enrichedMerchant = read.merchant.ifEmpty {
        consumeHint(read.bank.name, read.amountRial, source.at, hintBuffer)
    }

    return listOf(
        Txn(
            ref = refOf(source.srcHash, 0),
            srcHash = source.srcHash,
            seq = 0,
            at = source.at,
            day = tehranDay(source.at),
            bank = read.bank.name,
            accountId = read.bank.name,
            direction = direction,
            amountRial = read.amountRial,
            signedRial = read.amountRial?.let { a -> direction?.let { if (it == "in") a else -a } },
            balanceRial = read.balanceRial,
            feeRial = read.feeRial,
            mask = read.mask,
            instrument = read.instrument.name.lowercase(),
            merchant = enrichedMerchant,
            merchantNorm = merchantNorm(enrichedMerchant),
            refNo = read.refNo,
            printedAt = read.printedAt,
            channel = read.channel.name.lowercase(),
            unitPrinted = read.unitPrinted.name.lowercase(),
            inferred = read.inferred,
            parserVer = PARSER_VERSION,
        )
    )
}

/** A hand-entered transaction, in the same shape as one read from a message. */
fun manualToRow(row: ManualTxn): Txn = Txn(
    ref = manualRef(row.id),
    // There is no message behind it, so the id stands in — nothing downstream reads this except
    // to look a body up, and there is no body to find.
    srcHash = row.id,
    seq = 0,
    at = row.at,
    day = row.day,
    bank = "MANUAL",
    accountId = row.accountId ?: "MANUAL",
    direction = if (row.amountRial > 0) "in" else "out",
    amountRial = kotlin.math.abs(row.amountRial),
    signedRial = row.amountRial,
    balanceRial = null,
    feeRial = null,
    mask = "",
    instrument = "unknown",
    merchant = row.merchant,
    merchantNorm = merchantNorm(row.merchant),
    refNo = "",
    printedAt = "",
    channel = "unknown",
    unitPrinted = "none",
    inferred = false,
    parserVer = PARSER_VERSION,
    sourceKind = "manual",
)

/** A remote parsed transaction, without pretending it was entered manually on this phone. */
fun familyToRow(row: FamilyTxn): Txn = Txn(
    ref = familyLocalRef(row.id),
    srcHash = row.id,
    seq = 0,
    at = row.at,
    day = row.day,
    bank = row.bank,
    accountId = "family:${row.ownerMemberId}:${row.bank}",
    direction = if (row.amountRial > 0) "in" else "out",
    amountRial = kotlin.math.abs(row.amountRial),
    signedRial = row.amountRial,
    balanceRial = null,
    feeRial = null,
    mask = "",
    instrument = "unknown",
    merchant = row.merchant,
    merchantNorm = merchantNorm(row.merchant),
    refNo = "",
    printedAt = "",
    channel = "unknown",
    unitPrinted = "none",
    inferred = false,
    parserVer = PARSER_VERSION,
    familyRef = row.id,
    ownerMemberId = row.ownerMemberId,
    sourceKind = row.sourceKind,
)

/**
 * Rebuild every transaction from the stored messages.
 *
 * Total and idempotent, with no incremental path and no watermark: running it twice over the
 * same `sms_source` must produce byte-identical rows, and that property is what makes shipping
 * a parser fix a thing you can do on a Tuesday. About 8,000 rows land in well under a second.
 *
 * Cancelled half way it simply never writes [META_PARSER_VER], so the next launch derives again.
 * Correct by construction rather than by bookkeeping.
 *
 * ponytail: full rebuild every time. Add an incremental path when sms_source passes ~50k rows
 * or measured rebuild time passes ~2s — [META_DERIVE_MS] records the real trigger.
 */
suspend fun derive(
    durable: DurableDb,
    derived: DerivedDb,
    extra: Map<String, Bank>,
    now: Long = System.currentTimeMillis(),
): Int {
    val started = System.currentTimeMillis()
    val sources = durable.smsSource().allOldestFirst()
    val bodies = sources.associate { it.srcHash to it.body }

    // Everything she has decided, loaded once. The two databases cannot be joined — that is the
    // point of them being two files — so the join happens here, in a map, keyed by the message.
    val pinned = durable.decisions().ofKind(DecisionKind.CATEGORY).associate { it.ref to it.value }
    val rules = durable.rules().active()
    val verdicts = durable.linkDecisions().all()
    val aliases = durable.merchantAliases().all()
        .filter { it.displayName.isNotBlank() }
        .associate { it.sourceNorm to it.displayName }
    // A direction she pinned overrides the parser's reading. Applied after parse, before the
    // row is stored, so it flows into classification, links and balances exactly as if the
    // parser had originally read it that way.
    val directions = durable.decisions().ofKind(DecisionKind.DIRECTION)
        .filter { it.active }
        .mapNotNull { d -> d.value?.let { d.ref to it } }
        .toMap()

    var written = 0
    derived.withTransaction {
        derived.txn().deleteAll()
        derived.classes().deleteAll()
        derived.links().deleteAll()

        // Scan for merchant hints (Maskan purchase-initiation messages) before the main pass.
        // The buffer is consumed as debits are matched, so stale hints never accumulate.
        val hintBuffer = scanHints(sources, extra)

        val all = mutableListOf<Txn>()
        for (chunk in sources.chunked(500)) {
            val rows = applyMerchantAliases(
                applyDirectionOverride(chunk.flatMap { parseToRows(it, extra, hintBuffer) }, directions),
                aliases,
            )
            derived.txn().insertAll(rows)
            all += rows
            written += rows.size
        }
        // Typed in here, or on the iPhone and synced across. From this line down nothing
        // distinguishes them from a message — they classify, link and report identically.
        val manual = durable.manual().all().map(::manualToRow)
        derived.txn().insertAll(manual)
        all += manual
        written += manual.size

        val family = durable.familyTxns().all().map(::familyToRow)
        derived.txn().insertAll(family)
        all += family
        written += family.size

        val links = findLinks(all, verdicts) { bodies[it.srcHash].orEmpty() }
        derived.links().putAll(links)

        val transfers = transferRefs(links)
        derived.classes().putAll(
            all.map { classify(it, rules, pinned[it.ref], transfers) }
        )
    }
    derived.meta().put(LedgerMeta(META_PARSER_VER, PARSER_VERSION.toString()))
    derived.meta().put(LedgerMeta(META_DERIVED_AT, now.toString()))
    derived.meta().put(LedgerMeta(META_DERIVE_MS, (System.currentTimeMillis() - started).toString()))
    return written
}

/**
 * Whether the stored transactions were produced by the reading this build ships.
 *
 * True when the parser has moved on, and true when this database was rebuilt from scratch —
 * which is the same question, because the marker lives here and dies with the rows.
 */
suspend fun needsDerive(derived: DerivedDb): Boolean =
    derived.meta().get(META_PARSER_VER)?.toIntOrNull() != PARSER_VERSION

/**
 * Put the shipped categories and rules in place, and keep them current.
 *
 * REPLACE by id, so a build that renames a category or retunes a shipped rule takes effect —
 * while anything she made, which carries an id of its own, is untouched. Archiving is how a
 * builtin retires; deleting one would orphan every row that named it.
 */
suspend fun seedBuiltins(durable: DurableDb, now: Long = System.currentTimeMillis()) {
    durable.categories().putAll(BUILTIN_CATEGORIES.map { it.copy(updatedAt = now) })
    durable.rules().putAll(BUILTIN_RULES.map { it.copy(createdAt = now, updatedAt = now) })
}

/** How many transactions are waiting for her, which is the only number the deck needs. */
suspend fun reviewCount(derived: DerivedDb): Int =
    derived.classes().reviewCount() + derived.links().askCount()

/** One line of the timeline: what happened, what it was filed as, and whether that is settled. */
data class LedgerEntry(
    val txn: Txn,
    val categoryId: String,
    val categoryFa: String,
    val confidence: Int,
    val needsReview: Boolean,
    /** The later leg of a duplicate. Shown struck through, never deleted, always undoable. */
    val duplicate: Boolean,
    /** Both legs of a settled transfer. Counted in neither income nor spending. */
    val transfer: Boolean,
    /** She hid it from this ledger; visible only under the reveal toggle, restore-able. */
    val hidden: Boolean = false,
    val ownerMemberId: String = "",
    val ownerName: String = "",
    val categoryEditorName: String = "",
)

/** Everything the ledger screens need, read in one pass. */
data class LedgerView(
    /** Capped to the browsing window (300 rows) for the timeline. */
    val entries: List<LedgerEntry> = emptyList(),
    /** The full stored ledger — uncapped, for reports and goals. */
    val allEntries: List<LedgerEntry> = emptyList(),
    val categories: List<Category> = emptyList(),
    val goals: List<GoalProgress> = emptyList(),
    /** ref → yes | no | needed, for the ones she has answered. */
    val worthIt: Map<String, String> = emptyMap(),
    /** ref → note text, for the ones she has written a note for. */
    val notes: Map<String, String> = emptyMap(),
    val ready: Boolean = false,
    /** Rows she hid, for the reveal toggle; struck through and restore-able. */
    val hiddenEntries: List<LedgerEntry> = emptyList(),
    /** How many transactions are hidden (not in [entries] unless revealed). */
    val hiddenCount: Int = 0,
    /** How many transactions sit in each category — the number a delete must not orphan. */
    val categoryCounts: Map<String, Int> = emptyMap(),
) {
    /** Only what she actually has to answer, biggest first, and never more than a week's worth. */
    val review: List<LedgerEntry> by lazy {
        entries.filter { it.needsReview && !it.duplicate && !it.transfer }
            .sortedByDescending { it.txn.amountRial ?: 0L }
            .take(DECK_CAP)
    }
}

/**
 * A week with three hundred transactions still has to produce a deck she can finish. An
 * unbounded deck is a deck nobody opens, and one she never finishes is worse than none.
 */
const val DECK_CAP = 12

data class LedgerEntries(
    /** The live ledger — hidden rows do not appear here at all. */
    val entries: List<LedgerEntry>,
    /** Rows she hid, for the reveal toggle. Striking them through is the UI's business. */
    val hiddenEntries: List<LedgerEntry> = emptyList(),
    val categories: List<Category> = emptyList(),
    val categoryDecisions: Map<String, TxnDecision> = emptyMap(),
    /** How many transactions sit in each category — the number a delete must not orphan. */
    val categoryCounts: Map<String, Int> = emptyMap(),
)

suspend fun ledgerEntries(
    derived: DerivedDb,
    durable: DurableDb,
    limit: Int = 300,
): LedgerEntries {
    val fetchLimit = if (limit > Int.MAX_VALUE - MAX_HIDDEN_SLACK) Int.MAX_VALUE else limit + MAX_HIDDEN_SLACK
    val transactions = derived.txn().newest(fetchLimit)
    val categories = durable.categories().all()
    val names = categories.associate { it.id to it.nameFa }
    val members = durable.familyMembers().all().associateBy { it.id }
    val currentMemberId = durable.meta().get(META_SYNC_MEMBER).orEmpty()
    val categoryDecisions = durable.decisions().ofKind(DecisionKind.CATEGORY).associateBy { it.ref }
    val links = derived.links().all()
    val classes = derived.classes().all().associateBy { it.ref }
    val hidden = hiddenRefs(durable)
    val transfers = transferRefs(links)
    val built = transactions.map { txn ->
        val filed = classes[txn.ref]
        val ownerMemberId = txn.ownerMemberId.ifBlank { currentMemberId }
        val categoryEditorId = categoryDecisions[txn.ref]?.memberId.orEmpty()
        LedgerEntry(
            txn = txn,
            categoryId = filed?.categoryId ?: CAT_UNCATEGORISED,
            categoryFa = names[filed?.categoryId] ?: "دسته‌بندی نشده",
            confidence = filed?.confidence ?: Confidence.NONE,
            needsReview = filed?.needsReview ?: true,
            duplicate = txn.ref in hiddenRefs(links),
            transfer = txn.ref in transfers || filed?.categoryId == CAT_TRANSFER,
            hidden = txn.ref in hidden,
            ownerMemberId = ownerMemberId,
            ownerName = members[ownerMemberId]?.name.orEmpty(),
            categoryEditorName = members[categoryEditorId]?.name.orEmpty(),
        )
    }
    val (hiddenEntries, visible) = built.partition { it.hidden }
    val categoryCounts = classes.values
        .groupingBy { it.categoryId }
        .eachCount()
    return LedgerEntries(visible.take(limit), hiddenEntries.take(limit), categories, categoryDecisions, categoryCounts)
}

/**
 * How much slack the timeline allows above its visible budget, so the reveal toggle can still
 * find a hidden row that fell off the bottom of the newest-N window.
 */
private const val MAX_HIDDEN_SLACK = 200

suspend fun ledgerView(
    derived: DerivedDb,
    durable: DurableDb,
    limit: Int = 300,
): LedgerView {
    // Fetch the full store for reports/goals; cap for the timeline.
    val all = ledgerEntries(derived, durable, limit = Int.MAX_VALUE)
    val capped = LedgerEntries(
        entries = all.entries.take(limit),
        categories = all.categories,
        hiddenEntries = all.hiddenEntries,
        categoryCounts = all.categoryCounts,
    )
    val answers = durable.decisions().ofKind(DecisionKind.WORTH_IT)
        .mapNotNull { d -> d.value?.let { d.ref to it } }
        .toMap()
    val notes = durable.decisions().ofKind(DecisionKind.NOTE)
        .mapNotNull { d -> d.value?.let { d.ref to it } }
        .toMap()
    val today = tehranDay(System.currentTimeMillis())
    // Goals read from the full store, not the capped window.
    val goals = durable.goals().active().map { goalProgress(it, all.entries, today, all.categories) }
    return LedgerView(
        entries = capped.entries,
        allEntries = all.entries,
        categories = capped.categories,
        goals = goals,
        worthIt = answers,
        notes = notes,
        ready = true,
        hiddenCount = capped.hiddenEntries.size,
        hiddenEntries = capped.hiddenEntries,
        categoryCounts = capped.categoryCounts,
    )
}

/**
 * What an account holds, worked out rather than accumulated.
 *
 * The newest evidence wins, from either source: a balance the bank stated and a figure she
 * typed in compete on time alone. The bank takes a tie, because a stated balance is the bank's
 * own arithmetic and hers is a memory.
 *
 * Everything the old left fold guaranteed falls out of this more cleanly. A stated balance still
 * beats a computed delta — it *is* the anchor. An out-of-order message needs no guard, because
 * it is just a row with a timestamp. And a parser fix can repair a balance, which a fold could
 * only ever do by wiping and re-folding in order.
 */
fun deriveBalance(
    accountId: String,
    transactions: List<Txn>,
    typed: BalanceAnchor?,
): DerivedBalance? {
    if (transactions.isEmpty() && typed == null) return null
    val ordered = transactions.sortedWith(compareBy({ it.at }, { it.ref }))
    val newestAt = maxOf(ordered.lastOrNull()?.at ?: 0L, typed?.at ?: 0L)

    val stated = ordered.lastOrNull { it.balanceRial != null }
        ?.let { Anchor(it.balanceRial!!, it.at, it.ref) }
    val hers = typed?.let { Anchor(it.balanceRial, it.at, "") }
    // The bank takes a tie: a stated balance is the bank's own arithmetic, hers is a memory.
    val anchor = when {
        stated != null && hers != null -> if (hers.at > stated.at) hers else stated
        else -> stated ?: hers
    }
        ?: return DerivedBalance(
            accountId,
            // Nothing has ever stated a figure, so this is the change since we started reading
            // and not a balance at all. Reported, flagged, and kept out of every total —
            // exactly as an asset with no rate already is. Start on a withdrawal and it is
            // negative, and counting that would take money off her total that never existed.
            ordered.sumOf { it.signedRial ?: 0L },
            newestAt,
            anchored = false,
        )

    // Strictly after: a message that states a balance *and* a delta states the balance after
    // the transaction, so adding its own delta on top would count it twice.
    val since = ordered
        .filter { it.at > anchor.at || (it.at == anchor.at && it.ref > anchor.ref) }
        .sumOf { it.signedRial ?: 0L }
    return DerivedBalance(accountId, anchor.rial + since, newestAt, anchored = true)
}

suspend fun balanceOf(derived: DerivedDb, durable: DurableDb, accountId: String): DerivedBalance? =
    deriveBalance(
        accountId,
        derived.txn().forAccount(accountId).filterNot { it.ref in hiddenRefs(durable) },
        durable.anchors().newest(accountId),
    )

/** The refs she has hidden from the ledger, from durable decisions. Restore = delete decision. */
suspend fun hiddenRefs(durable: DurableDb): Set<String> =
    durable.decisions().ofKind(DecisionKind.HIDE)
        .filter { it.active }
        .map { it.ref }
        .toSet()

/**
 * Every account the ledger knows about, with what it holds.
 *
 * Hidden transactions are money she decided never happened, so they leave the balance too —
 * the stated balance still binds, only the sums the hide would have moved are dropped.
 */
suspend fun allBalances(derived: DerivedDb, durable: DurableDb): List<DerivedBalance> {
    val hidden = hiddenRefs(durable)
    val fromTransactions = derived.txn().accountIds()
    val anchors = durable.anchors().all()
        .groupBy { it.accountId }
        .mapValues { (_, rows) -> rows.maxByOrNull { it.at }!! }
    val fromAnchors = anchors.keys
    return (fromTransactions + fromAnchors).distinct()
        .mapNotNull { accountId ->
            deriveBalance(
                accountId,
                derived.txn().forAccount(accountId).filterNot { it.ref in hidden },
                anchors[accountId],
            )
        }
}

/**
 * Fold the corrected ledger back over the persisted fold.
 *
 * A manual correction (direction flip, hide, delete) re-derives the ledger, and [DerivedBalance]
 * reflects the correction. This merges those figures back into the fold so the Home total and
 * the per-account sheet agree with what the corrections say.
 *
 * The rule that avoids clobbering her own numbers: the ledger figure is adopted only when it is
 * at least as new as the fold's (≥, so a tie goes to the ledger — the one the correction just
 * wrote), or when the fold had no figure for the account yet. A hand-typed balance she set
 * *after* the last message is newer than anything the ledger can produce and is left alone.
 * Accounts the ledger knows nothing about are untouched (a panic balance entered for a bank that
 * never messages again must not vanish on a rebuild).
 */
fun reconcileBalances(
    fold: List<BankAccount>,
    balances: List<DerivedBalance>,
    now: Long = System.currentTimeMillis(),
): List<BankAccount> {
    val byKey = balances.associateBy { it.accountId }
    return fold.map { account ->
        val balance = byKey[account.bank] ?: return@map account
        if (balance.at < account.updatedAt) return@map account
        account.copy(
            balance = balance.rial / 10.0,
            updatedAt = balance.at,
            inferred = !balance.anchored && account.balance != balance.rial / 10.0,
        )
    }.let { collapsed -> collapseAccounts(collapsed) }
}

/** Escapes a field for CSV output according to RFC 4180. */
fun csvEscape(value: String): String =
    if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }

/**
 * Generates an Excel-friendly CSV string for ledger entries.
 * Prepends \uFEFF (UTF-8 BOM) so Microsoft Excel displays Persian characters properly.
 */
fun generateLedgerCsv(entries: List<LedgerEntry>, notes: Map<String, String> = emptyMap()): String = buildString {
    append('\uFEFF')
    appendLine("تاریخ,ساعت,مبلغ (تومان),مبلغ (ریال),نوع,دسته‌بندی,طرف حساب/فروشگاه,بانک,کارت/حساب,شماره پیگیری,یادداشت")
    for (entry in entries) {
        val txn = entry.txn
        val date = jalaliOf(txn.day).toString()
        val time = txn.printedAt.ifBlank { "" }
        val amountRial = txn.signedRial ?: txn.amountRial ?: 0L
        val amountToman = amountRial / 10
        val direction = when {
            entry.transfer -> "انتقال"
            txn.direction == "in" -> "واریز"
            txn.direction == "out" -> "برداشت"
            else -> "نامشخص"
        }
        val category = if (entry.transfer) "انتقال بین حساب‌ها" else entry.categoryFa
        val merchant = txn.merchant
        val bank = bankNameOf(txn.bank)
        val mask = txn.mask
        val refNo = txn.refNo
        val note = notes[txn.ref].orEmpty()
        appendLine(
            listOf(
                date,
                time,
                amountToman.toString(),
                amountRial.toString(),
                direction,
                category,
                merchant,
                bank,
                mask,
                refNo,
                note,
            ).joinToString(",") { csvEscape(it) }
        )
    }
}
