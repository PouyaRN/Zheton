package com.doxigo.muchtoman.features.backup

import com.doxigo.muchtoman.BalanceAnchor
import com.doxigo.muchtoman.BankAccount
import com.doxigo.muchtoman.Category
import com.doxigo.muchtoman.Commitment
import com.doxigo.muchtoman.CommitmentKind
import com.doxigo.muchtoman.FamilyMember
import com.doxigo.muchtoman.FamilyTxn
import com.doxigo.muchtoman.Goal
import com.doxigo.muchtoman.Holding
import com.doxigo.muchtoman.HoldingLot
import com.doxigo.muchtoman.LotSide
import com.doxigo.muchtoman.InstallmentObligation
import com.doxigo.muchtoman.InstallmentPayment
import com.doxigo.muchtoman.LinkDecision
import com.doxigo.muchtoman.ManualTxn
import com.doxigo.muchtoman.MerchantAlias
import com.doxigo.muchtoman.Reservation
import com.doxigo.muchtoman.Rule
import com.doxigo.muchtoman.SmsSource
import com.doxigo.muchtoman.StrangeSender
import com.doxigo.muchtoman.SyncPublication
import com.doxigo.muchtoman.TxnDecision
import com.doxigo.muchtoman.features.pricealerts.AlertDirection
import com.doxigo.muchtoman.features.pricealerts.PriceAlert
import com.doxigo.muchtoman.widget.primitives.WidgetDensity
import com.doxigo.muchtoman.widget.engine.WidgetId
import com.doxigo.muchtoman.widget.engine.WidgetSlot
import kotlinx.serialization.Serializable

/**
 * The plaintext shape of a backup, and the *only* thing the codec ever serialises.
 *
 * Two decisions here are load-bearing.
 *
 * 1. **The payload schema is not the Room schema.** Room entities are mirrored into these DTOs
 *    rather than annotated `@Serializable`, because the two have different lifetimes: a
 *    migration may add a column tomorrow, and a backup written by today's build must still
 *    restore — and be readable by the tests that pin this format. The mirrors also let a
 *    future schema bump rewrite the payload without touching `Ledger.kt`.
 *
 * 2. **Recovery credentials never enter this file.** `sync_key`, `sync_token`, `sync_device`,
 *    `sync_base` and the rest of the `sync_*` durable-meta keys are deliberately absent, and
 *    [BACKUP_META_KEYS] is an allowlist so a crafted file cannot inject them either. A backup
 *    is a copy of the ledger, not a copy of the household's house keys; restoring on a second
 *    phone re-pairs through the invite flow instead of impersonating the original device.
 *
 * [includesMessages] is recorded rather than implied: a backup without raw SMS bodies is a
 * legitimate export ("the numbers, not the messages"), and the restore path must not read an
 * empty message list as "delete every message".
 *
 * ## Version history
 *
 * - **1**: preferences, the store's lists, the durable tables.
 * - **2**: adds the stores the redesign brought in: holding purchase lots
 *   ([BackupLedger.holdingLots]), the monthly budget ([BackupPrefs.monthlyBudgetToman]), price
 *   alerts ([BackupLedger.priceAlerts]) and the daily rate history ([BackupPayload.dailyRates]).
 *   Each is **nullable, and null means "this file does not carry it"**: a version-1 file decodes
 *   with all four null and its restore leaves those stores exactly as they are. A present value,
 *   even an empty one, is a snapshot and is applied (see `restoreMapStores`).
 */
const val BACKUP_SCHEMA_VERSION: Int = 2

/**
 * The built-in forward migrations, `version → version + 1`. 1 → 2 only adds nullable fields, so
 * it is the identity: the missing keys decode as null, which is what "not in this file" means.
 */
val BACKUP_MIGRATIONS: Map<Int, (kotlinx.serialization.json.JsonObject) -> kotlinx.serialization.json.JsonObject> =
    mapOf(1 to { obj -> obj })

/**
 * The durable-meta keys a restore is allowed to write. Everything else — every `sync_*`
 * credential in particular — is dropped at decode time.
 */
val BACKUP_META_KEYS: Set<String> = setOf(
    // How far the inbox was read. Restoring it avoids re-reading thirteen months of messages
    // on the new phone; the primary key on sms_source makes a re-read harmless either way.
    "source_scanned_to",
    // The tombstones. Without these a permanently deleted message is admitted again by the
    // next ingest, which is exactly the undo she asked not to be possible.
    "permanently_deleted",
    // The one-shot migration sentinel. Re-running migrateAnchorsFromPrefs on restore would
    // duplicate every hand-typed anchor with a fresh id, and anchors are money she cannot
    // retype from memory.
    "anchors_migrated",
)

@Serializable
data class BackupPayload(
    /** Explicit and checked. A newer value is refused, never guessed at. */
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val createdAt: Long = 0L,
    val appVersion: String = "",
    /** False when the export deliberately left raw SMS bodies out. */
    val includesMessages: Boolean = true,
    val prefs: BackupPrefs = BackupPrefs(),
    val ledger: BackupLedger = BackupLedger(),
    val tables: BackupTables = BackupTables(),
    /**
     * Since version 2. One close per Tehran day per asset (`filesDir/daily_rates`). Merged on
     * restore, never replacing a day the phone already has. Null: not in this file.
     */
    val dailyRates: Map<String, Map<Long, Double>>? = null,
)

/**
 * The store's own values. Its list-valued fields live in [BackupLedger] because they are data,
 * not settings.
 *
 * Deliberately missing: `lockEnabled` (a device-local security choice, not something a file
 * should be able to switch on) and every cached network payload (`rates`, `stocks`), which is
 * stale by definition and comes back on the first fetch.
 */
@Serializable
data class BackupPrefs(
    val name: String = "",
    val themeMode: String = "SYSTEM",
    val smsEnabled: Boolean = false,
    val widgetLock: Boolean = false,
    val showHidden: Boolean = false,
    val hourlyWageToman: Long = 0L,
    val cycleMode: String = "JALALI_MONTH",
    val cycleAnchorDay: Int = 1,
    val smsScannedTo: Long = 0L,
    /** Legacy dedupe cache, kept so an old phone's corrections are not re-counted. */
    val seenSms: Set<String> = emptySet(),
    /** Since version 2. `Store.monthlyBudgetToman`, 0 = unset. Null: not in this file. */
    val monthlyBudgetToman: Long? = null,
)

/**
 * The store's data lists. These are replaced wholesale on restore — a backup is a snapshot of
 * what she had — while the durable tables below are merged row by row and never delete.
 */
@Serializable
data class BackupLedger(
    val holdings: List<Holding> = emptyList(),
    val commitments: List<BackupCommitment> = emptyList(),
    val overrides: Map<String, Double> = emptyMap(),
    val history: Map<Long, Double> = emptyMap(),
    val bankAccounts: List<BankAccount> = emptyList(),
    val disabledBanks: Set<String> = emptySet(),
    val hiddenCategories: Set<String> = emptySet(),
    val extraBankNumbers: Map<String, List<String>> = emptyMap(),
    val dismissedSenders: Set<String> = emptySet(),
    val strangeSenders: List<StrangeSender> = emptyList(),
    val widgetSlots: List<BackupWidgetSlot> = emptyList(),
    /** Since version 2. `Store.holdingLots`, holding key → lots. Null: not in this file. */
    val holdingLots: Map<String, List<BackupHoldingLot>>? = null,
    /** Since version 2. The `price_alerts` preferences file. Null: not in this file. */
    val priceAlerts: List<BackupPriceAlert>? = null,
)

/** Mirror of [HoldingLot]; the side is a name, read leniently like the other enums here. */
@Serializable
data class BackupHoldingLot(
    val id: String,
    val side: String,
    val amount: Double,
    val unitPriceToman: Double,
    val atMs: Long,
)

/** Mirror of [PriceAlert]; the direction is a name, read leniently. */
@Serializable
data class BackupPriceAlert(
    val id: String,
    val assetId: String,
    val assetName: String = "",
    val target: Double,
    val direction: String,
    val armed: Boolean = true,
    val createdAt: Long = 0L,
    val firedAt: Long = 0L,
)

/**
 * Mirrors of the two store models that carry an enum.
 *
 * An enum is a one-way door for a file format: a backup written by a build that knows one more
 * `CommitmentKind` or widget id would fail to *decode* on an older build, taking the whole
 * restore with it. These encode the enum by name and read it back leniently, so an unknown
 * value costs one dropped row instead of the backup.
 */
@Serializable
data class BackupCommitment(
    val id: String,
    val title: String,
    val provider: String,
    val kind: String = "BNPL",
    val totalAmountRial: Long,
    val remainingAmountRial: Long,
    val installmentCount: Int = 1,
    val paidInstallmentCount: Int = 0,
    val nextDueTimestamp: Long = 0L,
    val note: String = "",
    val isSettled: Boolean = false,
    val createdAt: Long = 0L,
)

@Serializable
data class BackupWidgetSlot(
    val id: String,
    val density: String,
    val isPinned: Boolean = false,
    val isVisible: Boolean = true,
    val order: Int = 0,
)

@Serializable
data class BackupTables(
    val messages: List<BackupSmsSource> = emptyList(),
    val meta: List<BackupMeta> = emptyList(),
    val anchors: List<BackupBalanceAnchor> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val rules: List<BackupRule> = emptyList(),
    val decisions: List<BackupTxnDecision> = emptyList(),
    val linkDecisions: List<BackupLinkDecision> = emptyList(),
    val manualTxns: List<BackupManualTxn> = emptyList(),
    val merchantAliases: List<BackupMerchantAlias> = emptyList(),
    val goals: List<BackupGoal> = emptyList(),
    val familyMembers: List<BackupFamilyMember> = emptyList(),
    val familyTxns: List<BackupFamilyTxn> = emptyList(),
    val syncPublications: List<BackupSyncPublication> = emptyList(),
    val obligations: List<BackupInstallmentObligation> = emptyList(),
    val payments: List<BackupInstallmentPayment> = emptyList(),
    val reservations: List<BackupReservation> = emptyList(),
)

// ──────────────────────────── durable-table mirrors ────────────────────────────
//
// Field order and defaults follow the entities exactly. `deleted` is carried even though the
// read DAOs hand back live rows only: it keeps the format honest about what a tombstone is,
// and a later DAO that exposes them needs no format change.

@Serializable
data class BackupSmsSource(
    val srcHash: String,
    val sender: String,
    val addrKey: String,
    val body: String,
    val at: Long,
    val ingestedAt: Long,
    val keyGen: Int = 1,
)

@Serializable
data class BackupMeta(val k: String, val v: String)

@Serializable
data class BackupBalanceAnchor(
    val id: String,
    val accountId: String,
    val at: Long,
    val balanceRial: Long,
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class BackupCategory(
    val id: String,
    val parentId: String? = null,
    val nameFa: String,
    val kind: String,
    val sort: Int = 0,
    val builtin: Boolean = false,
    val archived: Boolean = false,
    val updatedAt: Long = 0L,
)

@Serializable
data class BackupRule(
    val id: String,
    val priority: Int,
    val categoryId: String,
    val pMerchantNorm: String? = null,
    val pMerchantLike: String? = null,
    val pAddrKey: String? = null,
    val pBank: String? = null,
    val pChannel: String? = null,
    val pDirection: String? = null,
    val pMinRial: Long? = null,
    val pMaxRial: Long? = null,
    val pInstrument: String? = null,
    val enabled: Boolean = true,
    val builtin: Boolean = false,
    val originRef: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
)

@Serializable
data class BackupTxnDecision(
    val id: String,
    val ref: String,
    val kind: String,
    val value: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val memberId: String = "",
    val familyRef: String = "",
)

@Serializable
data class BackupLinkDecision(
    val id: String,
    val aRef: String,
    val bRef: String,
    val kind: String,
    val verdict: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class BackupManualTxn(
    val id: String,
    val at: Long,
    val day: Long,
    val amountRial: Long,
    val accountId: String? = null,
    val categoryId: String? = null,
    val merchant: String = "",
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class BackupMerchantAlias(
    val sourceNorm: String,
    val displayName: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupGoal(
    val id: String,
    val nameFa: String,
    val targetRial: Long,
    val kind: String,
    val categoryId: String? = null,
    val scopeMerchantNorm: String? = null,
    val period: String,
    val startsOn: Long,
    val endsOn: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class BackupFamilyMember(
    val id: String,
    val name: String,
    val sharesSms: Boolean = false,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class BackupFamilyTxn(
    val id: String,
    val ownerMemberId: String,
    val sourceKind: String,
    val at: Long,
    val day: Long,
    val amountRial: Long,
    val bank: String = "MANUAL",
    val merchant: String = "",
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class BackupSyncPublication(
    val id: String,
    val sourceKind: String,
    val contentHash: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

@Serializable
data class BackupInstallmentObligation(
    val id: String,
    val provider: String,
    val totalRial: Long,
    val downPaymentRial: Long,
    val installmentCount: Int,
    val installmentRial: Long,
    val startDate: Long,
    val notes: String = "",
    val linkedTxnRef: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
)

@Serializable
data class BackupInstallmentPayment(
    val id: String,
    val obligationId: String,
    val installmentNumber: Int,
    val dueDate: Long,
    val amountRial: Long,
    val paidDate: Long? = null,
    val linkedTxnRef: String = "",
)

@Serializable
data class BackupReservation(
    val id: String,
    val purpose: String,
    val amountRial: Long,
    val categoryId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val released: Boolean = false,
)

// ──────────────────────────── entity ⇄ mirror mapping ────────────────────────────

fun SmsSource.toBackup(): BackupSmsSource = BackupSmsSource(
    srcHash = srcHash, sender = sender, addrKey = addrKey, body = body,
    at = at, ingestedAt = ingestedAt, keyGen = keyGen,
)

fun BackupSmsSource.toEntity(): SmsSource = SmsSource(
    srcHash = srcHash, sender = sender, addrKey = addrKey, body = body,
    at = at, ingestedAt = ingestedAt, keyGen = keyGen,
)

fun BalanceAnchor.toBackup(): BackupBalanceAnchor = BackupBalanceAnchor(
    id = id, accountId = accountId, at = at, balanceRial = balanceRial, source = source,
    createdAt = createdAt, updatedAt = updatedAt, deleted = deleted,
)

fun BackupBalanceAnchor.toEntity(): BalanceAnchor = BalanceAnchor(
    id = id, accountId = accountId, at = at, balanceRial = balanceRial, source = source,
    createdAt = createdAt, updatedAt = updatedAt, deleted = deleted,
)

fun Category.toBackup(): BackupCategory = BackupCategory(
    id = id, parentId = parentId, nameFa = nameFa, kind = kind, sort = sort,
    builtin = builtin, archived = archived, updatedAt = updatedAt,
)

fun BackupCategory.toEntity(): Category = Category(
    id = id, parentId = parentId, nameFa = nameFa, kind = kind, sort = sort,
    builtin = builtin, archived = archived, updatedAt = updatedAt,
)

fun Rule.toBackup(): BackupRule = BackupRule(
    id = id, priority = priority, categoryId = categoryId,
    pMerchantNorm = pMerchantNorm, pMerchantLike = pMerchantLike, pAddrKey = pAddrKey,
    pBank = pBank, pChannel = pChannel, pDirection = pDirection,
    pMinRial = pMinRial, pMaxRial = pMaxRial, pInstrument = pInstrument,
    enabled = enabled, builtin = builtin, originRef = originRef,
    createdAt = createdAt, updatedAt = updatedAt, deleted = deleted,
)

fun BackupRule.toEntity(): Rule = Rule(
    id = id, priority = priority, categoryId = categoryId,
    pMerchantNorm = pMerchantNorm, pMerchantLike = pMerchantLike, pAddrKey = pAddrKey,
    pBank = pBank, pChannel = pChannel, pDirection = pDirection,
    pMinRial = pMinRial, pMaxRial = pMaxRial, pInstrument = pInstrument,
    enabled = enabled, builtin = builtin, originRef = originRef,
    createdAt = createdAt, updatedAt = updatedAt, deleted = deleted,
)

fun TxnDecision.toBackup(): BackupTxnDecision = BackupTxnDecision(
    id = id, ref = ref, kind = kind, value = value, createdAt = createdAt,
    updatedAt = updatedAt, deleted = deleted, memberId = memberId, familyRef = familyRef,
)

fun BackupTxnDecision.toEntity(): TxnDecision = TxnDecision(
    id = id, ref = ref, kind = kind, value = value, createdAt = createdAt,
    updatedAt = updatedAt, deleted = deleted, memberId = memberId, familyRef = familyRef,
)

fun LinkDecision.toBackup(): BackupLinkDecision = BackupLinkDecision(
    id = id, aRef = aRef, bRef = bRef, kind = kind, verdict = verdict,
    createdAt = createdAt, updatedAt = updatedAt, deleted = deleted,
)

fun BackupLinkDecision.toEntity(): LinkDecision = LinkDecision(
    id = id, aRef = aRef, bRef = bRef, kind = kind, verdict = verdict,
    createdAt = createdAt, updatedAt = updatedAt, deleted = deleted,
)

fun ManualTxn.toBackup(): BackupManualTxn = BackupManualTxn(
    id = id, at = at, day = day, amountRial = amountRial, accountId = accountId,
    categoryId = categoryId, merchant = merchant, note = note,
    createdAt = createdAt, updatedAt = updatedAt, deleted = deleted,
)

fun BackupManualTxn.toEntity(): ManualTxn = ManualTxn(
    id = id, at = at, day = day, amountRial = amountRial, accountId = accountId,
    categoryId = categoryId, merchant = merchant, note = note,
    createdAt = createdAt, updatedAt = updatedAt, deleted = deleted,
)

fun MerchantAlias.toBackup(): BackupMerchantAlias = BackupMerchantAlias(
    sourceNorm = sourceNorm, displayName = displayName,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun BackupMerchantAlias.toEntity(): MerchantAlias = MerchantAlias(
    sourceNorm = sourceNorm, displayName = displayName,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun Goal.toBackup(): BackupGoal = BackupGoal(
    id = id, nameFa = nameFa, targetRial = targetRial, kind = kind,
    categoryId = categoryId, scopeMerchantNorm = scopeMerchantNorm, period = period,
    startsOn = startsOn, endsOn = endsOn, createdAt = createdAt,
    updatedAt = updatedAt, deleted = deleted,
)

fun BackupGoal.toEntity(): Goal = Goal(
    id = id, nameFa = nameFa, targetRial = targetRial, kind = kind,
    categoryId = categoryId, scopeMerchantNorm = scopeMerchantNorm, period = period,
    startsOn = startsOn, endsOn = endsOn, createdAt = createdAt,
    updatedAt = updatedAt, deleted = deleted,
)

fun FamilyMember.toBackup(): BackupFamilyMember = BackupFamilyMember(
    id = id, name = name, sharesSms = sharesSms, updatedAt = updatedAt, deleted = deleted,
)

fun BackupFamilyMember.toEntity(): FamilyMember = FamilyMember(
    id = id, name = name, sharesSms = sharesSms, updatedAt = updatedAt, deleted = deleted,
)

fun FamilyTxn.toBackup(): BackupFamilyTxn = BackupFamilyTxn(
    id = id, ownerMemberId = ownerMemberId, sourceKind = sourceKind, at = at, day = day,
    amountRial = amountRial, bank = bank, merchant = merchant,
    updatedAt = updatedAt, deleted = deleted,
)

fun BackupFamilyTxn.toEntity(): FamilyTxn = FamilyTxn(
    id = id, ownerMemberId = ownerMemberId, sourceKind = sourceKind, at = at, day = day,
    amountRial = amountRial, bank = bank, merchant = merchant,
    updatedAt = updatedAt, deleted = deleted,
)

fun SyncPublication.toBackup(): BackupSyncPublication = BackupSyncPublication(
    id = id, sourceKind = sourceKind, contentHash = contentHash,
    updatedAt = updatedAt, deleted = deleted,
)

fun BackupSyncPublication.toEntity(): SyncPublication = SyncPublication(
    id = id, sourceKind = sourceKind, contentHash = contentHash,
    updatedAt = updatedAt, deleted = deleted,
)

fun InstallmentObligation.toBackup(): BackupInstallmentObligation = BackupInstallmentObligation(
    id = id, provider = provider, totalRial = totalRial, downPaymentRial = downPaymentRial,
    installmentCount = installmentCount, installmentRial = installmentRial,
    startDate = startDate, notes = notes, linkedTxnRef = linkedTxnRef,
    createdAt = createdAt, updatedAt = updatedAt, archived = archived,
)

fun BackupInstallmentObligation.toEntity(): InstallmentObligation = InstallmentObligation(
    id = id, provider = provider, totalRial = totalRial, downPaymentRial = downPaymentRial,
    installmentCount = installmentCount, installmentRial = installmentRial,
    startDate = startDate, notes = notes, linkedTxnRef = linkedTxnRef,
    createdAt = createdAt, updatedAt = updatedAt, archived = archived,
)

fun InstallmentPayment.toBackup(): BackupInstallmentPayment = BackupInstallmentPayment(
    id = id, obligationId = obligationId, installmentNumber = installmentNumber,
    dueDate = dueDate, amountRial = amountRial, paidDate = paidDate,
    linkedTxnRef = linkedTxnRef,
)

fun BackupInstallmentPayment.toEntity(): InstallmentPayment = InstallmentPayment(
    id = id, obligationId = obligationId, installmentNumber = installmentNumber,
    dueDate = dueDate, amountRial = amountRial, paidDate = paidDate,
    linkedTxnRef = linkedTxnRef,
)

fun Reservation.toBackup(): BackupReservation = BackupReservation(
    id = id, purpose = purpose, amountRial = amountRial, categoryId = categoryId,
    createdAt = createdAt, updatedAt = updatedAt, released = released,
)

fun BackupReservation.toEntity(): Reservation = Reservation(
    id = id, purpose = purpose, amountRial = amountRial, categoryId = categoryId,
    createdAt = createdAt, updatedAt = updatedAt, released = released,
)

fun Commitment.toBackup(): BackupCommitment = BackupCommitment(
    id = id,
    title = title,
    provider = provider,
    kind = kind.name,
    totalAmountRial = totalAmountRial,
    remainingAmountRial = remainingAmountRial,
    installmentCount = installmentCount,
    paidInstallmentCount = paidInstallmentCount,
    nextDueTimestamp = nextDueTimestamp,
    note = note,
    isSettled = isSettled,
    createdAt = createdAt,
)

fun BackupCommitment.toEntity(): Commitment = Commitment(
    id = id,
    title = title,
    provider = provider,
    kind = runCatching { CommitmentKind.valueOf(kind) }.getOrDefault(CommitmentKind.BNPL),
    totalAmountRial = totalAmountRial,
    remainingAmountRial = remainingAmountRial,
    installmentCount = installmentCount,
    paidInstallmentCount = paidInstallmentCount,
    nextDueTimestamp = nextDueTimestamp,
    note = note,
    isSettled = isSettled,
    createdAt = createdAt,
)

fun WidgetSlot.toBackup(): BackupWidgetSlot = BackupWidgetSlot(
    id = id.name,
    density = density.name,
    isPinned = isPinned,
    isVisible = isVisible,
    order = order,
)

/** Null when this build does not know the widget id or density; the restore skips that row. */
fun BackupWidgetSlot.toEntityOrNull(): WidgetSlot? = runCatching {
    WidgetSlot(
        id = WidgetId.valueOf(id),
        density = WidgetDensity.valueOf(density),
        isPinned = isPinned,
        isVisible = isVisible,
        order = order,
    )
}.getOrNull()

fun HoldingLot.toBackup(): BackupHoldingLot = BackupHoldingLot(
    id = id, side = side.name, amount = amount, unitPriceToman = unitPriceToman, atMs = atMs,
)

/** Null when this build does not know the side; the restore skips that lot. */
fun BackupHoldingLot.toEntityOrNull(): HoldingLot? = runCatching {
    HoldingLot(id = id, side = LotSide.valueOf(side), amount = amount, unitPriceToman = unitPriceToman, atMs = atMs)
}.getOrNull()

fun PriceAlert.toBackup(): BackupPriceAlert = BackupPriceAlert(
    id = id, assetId = assetId, assetName = assetName, target = target,
    direction = direction.name, armed = armed, createdAt = createdAt, firedAt = firedAt,
)

/** Null when this build does not know the direction; the restore skips that alert. */
fun BackupPriceAlert.toEntityOrNull(): PriceAlert? = runCatching {
    PriceAlert(
        id = id, assetId = assetId, assetName = assetName, target = target,
        direction = AlertDirection.valueOf(direction), armed = armed,
        createdAt = createdAt, firedAt = firedAt,
    )
}.getOrNull()

// ──────────────────────────── what the screens show ────────────────────────────

/** Counts for the confirmation sheet, so nobody restores a file blind. */
data class BackupSummary(
    val schemaVersion: Int,
    val createdAt: Long,
    val appVersion: String,
    val includesMessages: Boolean,
    val bytes: Long,
    val messages: Int,
    val manualTransactions: Int,
    val decisions: Int,
    val rules: Int,
    val categories: Int,
    val holdings: Int,
    val bankAccounts: Int,
    val commitments: Int,
    val goals: Int,
    val obligations: Int,
    val reservations: Int,
    /** Since version 2; 0 when the file does not carry them. */
    val holdingLots: Int = 0,
    val priceAlerts: Int = 0,
    val dailyRateAssets: Int = 0,
) {
    val totalRows: Int
        get() = messages + manualTransactions + decisions + rules + categories +
            holdings + bankAccounts + commitments + goals + obligations + reservations
}

fun BackupPayload.summary(bytes: Long = 0L): BackupSummary = BackupSummary(
    schemaVersion = schemaVersion,
    createdAt = createdAt,
    appVersion = appVersion,
    includesMessages = includesMessages,
    bytes = bytes,
    messages = tables.messages.size,
    manualTransactions = tables.manualTxns.size,
    decisions = tables.decisions.size + tables.linkDecisions.size,
    rules = tables.rules.size,
    categories = tables.categories.size,
    holdings = ledger.holdings.size,
    bankAccounts = ledger.bankAccounts.size,
    commitments = ledger.commitments.size,
    goals = tables.goals.size,
    obligations = tables.obligations.size,
    reservations = tables.reservations.size,
    holdingLots = ledger.holdingLots?.values?.sumOf { it.size } ?: 0,
    priceAlerts = ledger.priceAlerts?.size ?: 0,
    dailyRateAssets = dailyRates?.size ?: 0,
)
