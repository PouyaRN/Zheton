package com.doxigo.muchtoman.features.backup

import com.doxigo.muchtoman.CategoryKind
import com.doxigo.muchtoman.GoalKind
import com.doxigo.muchtoman.GoalPeriod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs

/** Anything wrong with the *shape* of a backup, as opposed to the passphrase that opens it. */
open class BackupFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

class UnsupportedBackupVersionException(val version: Int) : BackupFormatException(
    "این پشتیبان با نسخهٔ $version ساخته شده و این نسخهٔ برنامه آن را نمی‌شناسد.",
)

/**
 * The JSON layer: encode, version gate, forward migrations, decode, validate.
 *
 * ## Versioning
 *
 * `schemaVersion` is written explicitly and is the **first** thing read. A file whose version
 * is newer than [BACKUP_SCHEMA_VERSION] is refused with [UnsupportedBackupVersionException] and
 * nothing in it is applied — the one failure mode that must never be guessed at, because
 * applying half a future format is how a restore quietly corrupts a ledger.
 *
 * Older versions climb a chain of **forward-migration hooks**: `migrations[v]` receives the
 * object at version `v` and returns the object at `v + 1`. Today the map is empty because
 * version 1 is the first format; the hook exists so the codec is testable as a migration
 * machine now, rather than the day a migration is first needed.
 *
 * ## Validation is a security boundary
 *
 * A `.mtbackup` file is attacker-controlled the moment it is opened: it could be a file someone
 * was sent, or one altered on disk. Everything structural is therefore checked before the
 * restore path sees it — row counts, string lengths, Rial amounts, timestamps, id shape — and
 * `durable_meta` keys are filtered against [BACKUP_META_KEYS] so a crafted file cannot inject
 * `sync_key`/`sync_token` and hand a stranger the household key.
 */
class BackupCodec(
    private val migrations: Map<Int, (JsonObject) -> JsonObject> = emptyMap(),
    private val maxPayloadBytes: Int = MAX_PAYLOAD_BYTES,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(payload: BackupPayload): ByteArray {
        if (payload.schemaVersion != BACKUP_SCHEMA_VERSION) {
            throw BackupFormatException("refusing to write schema ${payload.schemaVersion}")
        }
        return json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
    }

    /**
     * @param now used to bound timestamps; injectable so the tests do not depend on the clock.
     */
    fun decode(bytes: ByteArray, now: Long = System.currentTimeMillis()): BackupPayload {
        if (bytes.isEmpty()) throw BackupFormatException("empty backup")
        if (bytes.size > maxPayloadBytes) throw BackupFormatException("backup payload is too large")
        val text = runCatching { bytes.toString(Charsets.UTF_8) }
            .getOrElse { throw BackupFormatException("backup is not valid UTF-8", it) }
        val element = runCatching { json.parseToJsonElement(text) }
            .getOrElse { throw BackupFormatException("backup is not valid JSON", it) }
        val root = element as? JsonObject ?: throw BackupFormatException("backup is not a JSON object")

        val rawVersion = (root["schemaVersion"] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: throw BackupFormatException("backup has no schemaVersion")
        if (rawVersion < 0) throw BackupFormatException("backup has a negative schemaVersion")
        if (rawVersion > BACKUP_SCHEMA_VERSION) throw UnsupportedBackupVersionException(rawVersion)

        var current = root
        var version = rawVersion
        while (version < BACKUP_SCHEMA_VERSION) {
            val migration = migrations[version]
                ?: throw BackupFormatException("no migration from backup schema $version")
            current = migration(current)
            version += 1
        }
        current = withVersion(current, BACKUP_SCHEMA_VERSION)

        val decoded = runCatching { json.decodeFromJsonElement(BackupPayload.serializer(), current) }
            .getOrElse { throw BackupFormatException("backup does not match this build's schema", it) }

        validate(decoded, now)
        // Allowlist, not denylist: a future sync_* key cannot leak in by accident either.
        val safeMeta = decoded.tables.meta.filter { it.k in BACKUP_META_KEYS }
        return decoded.copy(tables = decoded.tables.copy(meta = safeMeta))
    }

    private fun withVersion(obj: JsonObject, version: Int): JsonObject =
        JsonObject(obj.toMutableMap().apply { put("schemaVersion", JsonPrimitive(version)) })

    private fun validate(payload: BackupPayload, now: Long) {
        fun fail(message: String): Nothing = throw BackupFormatException(message)
        fun rows(name: String, count: Int, max: Int) {
            if (count < 0 || count > max) fail("$name has an impossible row count")
        }
        fun text(name: String, value: String, max: Int, allowBlank: Boolean = true) {
            if (value.length > max) fail("$name is too long")
            if (!allowBlank && value.isBlank()) fail("$name is blank")
            if (value.any { it == '\u0000' }) fail("$name contains a NUL")
        }
        fun optText(name: String, value: String?, max: Int) {
            if (value != null) text(name, value, max)
        }
        fun stamp(name: String, value: Long) {
            if (value < 0 || value > now + MAX_FUTURE_DRIFT_MS) fail("$name is not a plausible timestamp")
        }
        fun rill(name: String, value: Long) {
            if (value < -MAX_RIAL || value > MAX_RIAL) fail("$name is not a plausible Rial amount")
        }
        fun amount(name: String, value: Double) {
            if (!value.isFinite() || abs(value) > MAX_AMOUNT) fail("$name is not a plausible amount")
        }
        fun id(name: String, value: String) = text(name, value, MAX_ID_CHARS, allowBlank = false)

        text("appVersion", payload.appVersion, MAX_APP_VERSION_CHARS)
        stamp("createdAt", payload.createdAt)

        payload.prefs.let { prefs ->
            text("prefs.name", prefs.name, MAX_NAME_CHARS)
            text("prefs.themeMode", prefs.themeMode, MAX_THEME_CHARS)
            text("prefs.cycleMode", prefs.cycleMode, MAX_THEME_CHARS)
            if (prefs.cycleAnchorDay !in 1..31) fail("prefs.cycleAnchorDay is out of range")
            if (prefs.hourlyWageToman < 0 || prefs.hourlyWageToman > MAX_RIAL) fail("prefs.hourlyWageToman is out of range")
            stamp("prefs.smsScannedTo", prefs.smsScannedTo)
            rows("prefs.seenSms", prefs.seenSms.size, MAX_SEEN_SMS)
            prefs.seenSms.forEach { if (it.length > MAX_ID_CHARS) fail("prefs.seenSms has an overlong key") }
        }

        payload.ledger.let { ledger ->
            rows("holdings", ledger.holdings.size, MAX_LEDGER_ROWS)
            ledger.holdings.forEach {
                id("holding.typeId", it.typeId)
                amount("holding.amount", it.amount)
                text("holding.label", it.label, MAX_NAME_CHARS)
                text("holding.id", it.id, MAX_ID_CHARS)
            }
            rows("commitments", ledger.commitments.size, MAX_LEDGER_ROWS)
            ledger.commitments.forEach {
                id("commitment.id", it.id)
                text("commitment.title", it.title, MAX_NAME_CHARS * 2)
                text("commitment.provider", it.provider, MAX_NAME_CHARS * 2)
                text("commitment.kind", it.kind, MAX_THEME_CHARS)
                rill("commitment.totalAmountRial", it.totalAmountRial)
                rill("commitment.remainingAmountRial", it.remainingAmountRial)
                if (it.installmentCount < 0 || it.installmentCount > 10_000) fail("commitment installment count")
                if (it.paidInstallmentCount < 0 || it.paidInstallmentCount > 10_000) fail("commitment paid count")
                stamp("commitment.nextDueTimestamp", it.nextDueTimestamp)
                text("commitment.note", it.note, MAX_NOTE_CHARS)
                stamp("commitment.createdAt", it.createdAt)
            }
            rows("overrides", ledger.overrides.size, MAX_RATE_ROWS)
            ledger.overrides.forEach { (key, value) ->
                id("override key", key)
                amount("override value", value)
            }
            rows("history", ledger.history.size, MAX_HISTORY_ROWS)
            ledger.history.forEach { (day, value) ->
                if (day < 0 || day > now / DAY_MS + 2) fail("history day is out of range")
                amount("history value", value)
            }
            rows("bankAccounts", ledger.bankAccounts.size, MAX_ACCOUNT_ROWS)
            ledger.bankAccounts.forEach {
                id("account.bank", it.bank)
                text("account.mask", it.mask, MAX_ID_CHARS)
                amount("account.balance", it.balance)
                text("account.sender", it.sender, MAX_TEXT_CHARS)
                text("account.nickname", it.nickname, MAX_NAME_CHARS)
                text("account.fullPan", it.fullPan, MAX_PAN_CHARS)
                text("account.sheba", it.sheba, MAX_PAN_CHARS)
                text("account.expiry", it.expiry, MAX_PAN_CHARS)
                text("account.theme", it.theme, MAX_THEME_CHARS)
                stamp("account.updatedAt", it.updatedAt)
            }
            rows("disabledBanks", ledger.disabledBanks.size, MAX_ACCOUNT_ROWS)
            ledger.disabledBanks.forEach { id("disabled bank", it) }
            rows("hiddenCategories", ledger.hiddenCategories.size, MAX_CATEGORY_ROWS)
            ledger.hiddenCategories.forEach { id("hidden category", it) }
            rows("extraBankNumbers", ledger.extraBankNumbers.size, MAX_ACCOUNT_ROWS)
            ledger.extraBankNumbers.forEach { (bank, numbers) ->
                id("extra bank", bank)
                rows("extra bank numbers", numbers.size, MAX_NUMBERS_PER_BANK)
                numbers.forEach { text("extra number", it, MAX_ID_CHARS) }
            }
            rows("dismissedSenders", ledger.dismissedSenders.size, MAX_LEDGER_ROWS)
            ledger.dismissedSenders.forEach { text("dismissed sender", it, MAX_TEXT_CHARS) }
            rows("strangeSenders", ledger.strangeSenders.size, MAX_LEDGER_ROWS)
            ledger.strangeSenders.forEach {
                text("strange sender", it.sender, MAX_TEXT_CHARS)
                text("strange bank", it.bank, MAX_TEXT_CHARS)
                text("strange snippet", it.snippet, MAX_NOTE_CHARS)
                stamp("strange at", it.at)
            }
            rows("widgetSlots", ledger.widgetSlots.size, MAX_WIDGET_SLOTS)
            ledger.widgetSlots.forEach {
                text("slot.id", it.id, MAX_THEME_CHARS, allowBlank = false)
                text("slot.density", it.density, MAX_THEME_CHARS, allowBlank = false)
                if (it.order !in 0..MAX_WIDGET_SLOTS) fail("slot.order is out of range")
            }
        }

        payload.tables.let { tables ->
            rows("messages", tables.messages.size, MAX_MESSAGES)
            if (!payload.includesMessages && tables.messages.isNotEmpty()) {
                fail("backup says it omits messages but carries them")
            }
            tables.messages.forEach {
                if (!HEX_64.matches(it.srcHash)) fail("message hash is not a SHA-256")
                text("message.sender", it.sender, MAX_TEXT_CHARS, allowBlank = false)
                text("message.addrKey", it.addrKey, MAX_TEXT_CHARS)
                text("message.body", it.body, MAX_MESSAGE_BODY_CHARS)
                stamp("message.at", it.at)
                stamp("message.ingestedAt", it.ingestedAt)
                if (it.keyGen !in 1..MAX_KEY_GEN) fail("message keyGen is unknown")
            }

            rows("meta", tables.meta.size, MAX_META_ROWS)
            tables.meta.forEach {
                id("meta key", it.k)
                // `permanently_deleted` is one 64-char hash per line; a full cap is megabytes.
                if (it.v.length > MAX_META_VALUE_CHARS) fail("meta value is too large")
            }

            rows("anchors", tables.anchors.size, MAX_LEDGER_ROWS)
            tables.anchors.forEach {
                id("anchor.id", it.id)
                id("anchor.accountId", it.accountId)
                stamp("anchor.at", it.at)
                rill("anchor.balanceRial", it.balanceRial)
                text("anchor.source", it.source, MAX_THEME_CHARS)
                stamp("anchor.createdAt", it.createdAt)
                stamp("anchor.updatedAt", it.updatedAt)
            }

            rows("categories", tables.categories.size, MAX_CATEGORY_ROWS)
            tables.categories.forEach {
                id("category.id", it.id)
                optText("category.parentId", it.parentId, MAX_ID_CHARS)
                text("category.nameFa", it.nameFa, MAX_NAME_CHARS * 2, allowBlank = false)
                if (it.kind !in CATEGORY_KINDS) fail("category.kind is unknown")
                stamp("category.updatedAt", it.updatedAt)
            }

            rows("rules", tables.rules.size, MAX_RULE_ROWS)
            tables.rules.forEach {
                id("rule.id", it.id)
                id("rule.categoryId", it.categoryId)
                optText("rule.merchantNorm", it.pMerchantNorm, MAX_TEXT_CHARS)
                optText("rule.merchantLike", it.pMerchantLike, MAX_TEXT_CHARS)
                optText("rule.addrKey", it.pAddrKey, MAX_TEXT_CHARS)
                optText("rule.bank", it.pBank, MAX_THEME_CHARS)
                optText("rule.channel", it.pChannel, MAX_THEME_CHARS)
                it.pDirection?.let { direction ->
                    if (direction !in setOf("in", "out")) fail("rule.direction is unknown")
                }
                val min = it.pMinRial
                val max = it.pMaxRial
                if (min != null) rill("rule.pMinRial", min)
                if (max != null) rill("rule.pMaxRial", max)
                if (min != null && max != null && min > max) fail("rule min is above its max")
                optText("rule.instrument", it.pInstrument, MAX_THEME_CHARS)
                optText("rule.originRef", it.originRef, MAX_TEXT_CHARS)
                stamp("rule.createdAt", it.createdAt)
                stamp("rule.updatedAt", it.updatedAt)
            }

            rows("decisions", tables.decisions.size, MAX_DECISION_ROWS)
            tables.decisions.forEach {
                id("decision.id", it.id)
                text("decision.ref", it.ref, MAX_TEXT_CHARS, allowBlank = false)
                text("decision.kind", it.kind, MAX_THEME_CHARS, allowBlank = false)
                optText("decision.value", it.value, MAX_TEXT_CHARS)
                stamp("decision.createdAt", it.createdAt)
                stamp("decision.updatedAt", it.updatedAt)
                text("decision.memberId", it.memberId, MAX_ID_CHARS)
                text("decision.familyRef", it.familyRef, MAX_TEXT_CHARS)
            }

            rows("linkDecisions", tables.linkDecisions.size, MAX_DECISION_ROWS)
            tables.linkDecisions.forEach {
                id("link.id", it.id)
                text("link.aRef", it.aRef, MAX_TEXT_CHARS, allowBlank = false)
                text("link.bRef", it.bRef, MAX_TEXT_CHARS, allowBlank = false)
                text("link.kind", it.kind, MAX_THEME_CHARS)
                text("link.verdict", it.verdict, MAX_THEME_CHARS)
                stamp("link.createdAt", it.createdAt)
                stamp("link.updatedAt", it.updatedAt)
            }

            rows("manualTxns", tables.manualTxns.size, MAX_MANUAL_ROWS)
            tables.manualTxns.forEach {
                id("manual.id", it.id)
                stamp("manual.at", it.at)
                if (it.day < 0 || it.day > now / DAY_MS + 2) fail("manual.day is out of range")
                rill("manual.amountRial", it.amountRial)
                optText("manual.accountId", it.accountId, MAX_ID_CHARS)
                optText("manual.categoryId", it.categoryId, MAX_ID_CHARS)
                text("manual.merchant", it.merchant, MAX_TEXT_CHARS)
                text("manual.note", it.note, MAX_NOTE_CHARS)
                stamp("manual.createdAt", it.createdAt)
                stamp("manual.updatedAt", it.updatedAt)
            }

            rows("merchantAliases", tables.merchantAliases.size, MAX_LEDGER_ROWS)
            tables.merchantAliases.forEach {
                text("alias.sourceNorm", it.sourceNorm, MAX_TEXT_CHARS, allowBlank = false)
                text("alias.displayName", it.displayName, MAX_NAME_CHARS)
                stamp("alias.createdAt", it.createdAt)
                stamp("alias.updatedAt", it.updatedAt)
            }

            rows("goals", tables.goals.size, MAX_LEDGER_ROWS)
            tables.goals.forEach {
                id("goal.id", it.id)
                text("goal.nameFa", it.nameFa, MAX_NAME_CHARS * 2, allowBlank = false)
                rill("goal.targetRial", it.targetRial)
                if (it.kind !in GOAL_KINDS) fail("goal.kind is unknown")
                optText("goal.categoryId", it.categoryId, MAX_ID_CHARS)
                optText("goal.scopeMerchantNorm", it.scopeMerchantNorm, MAX_TEXT_CHARS)
                if (it.period !in GOAL_PERIODS) fail("goal.period is unknown")
                stamp("goal.startsOn", it.startsOn)
                it.endsOn?.let { end -> stamp("goal.endsOn", end) }
                stamp("goal.createdAt", it.createdAt)
                stamp("goal.updatedAt", it.updatedAt)
            }

            rows("familyMembers", tables.familyMembers.size, MAX_LEDGER_ROWS)
            tables.familyMembers.forEach {
                id("familyMember.id", it.id)
                text("familyMember.name", it.name, MAX_NAME_CHARS)
                stamp("familyMember.updatedAt", it.updatedAt)
            }

            rows("familyTxns", tables.familyTxns.size, MAX_DECISION_ROWS)
            tables.familyTxns.forEach {
                text("familyTxn.id", it.id, MAX_TEXT_CHARS, allowBlank = false)
                id("familyTxn.ownerMemberId", it.ownerMemberId)
                text("familyTxn.sourceKind", it.sourceKind, MAX_THEME_CHARS)
                stamp("familyTxn.at", it.at)
                if (it.day < 0 || it.day > now / DAY_MS + 2) fail("familyTxn.day is out of range")
                rill("familyTxn.amountRial", it.amountRial)
                text("familyTxn.bank", it.bank, MAX_TEXT_CHARS)
                text("familyTxn.merchant", it.merchant, MAX_TEXT_CHARS)
                stamp("familyTxn.updatedAt", it.updatedAt)
            }

            rows("syncPublications", tables.syncPublications.size, MAX_DECISION_ROWS)
            tables.syncPublications.forEach {
                text("publication.id", it.id, MAX_TEXT_CHARS, allowBlank = false)
                text("publication.sourceKind", it.sourceKind, MAX_THEME_CHARS)
                text("publication.contentHash", it.contentHash, MAX_TEXT_CHARS)
                stamp("publication.updatedAt", it.updatedAt)
            }

            rows("obligations", tables.obligations.size, MAX_LEDGER_ROWS)
            tables.obligations.forEach {
                id("obligation.id", it.id)
                text("obligation.provider", it.provider, MAX_NAME_CHARS * 2)
                rill("obligation.totalRial", it.totalRial)
                rill("obligation.downPaymentRial", it.downPaymentRial)
                if (it.installmentCount !in 0..10_000) fail("obligation installment count")
                rill("obligation.installmentRial", it.installmentRial)
                stamp("obligation.startDate", it.startDate)
                text("obligation.notes", it.notes, MAX_NOTE_CHARS)
                text("obligation.linkedTxnRef", it.linkedTxnRef, MAX_TEXT_CHARS)
                stamp("obligation.createdAt", it.createdAt)
                stamp("obligation.updatedAt", it.updatedAt)
            }

            rows("payments", tables.payments.size, MAX_PAYMENT_ROWS)
            tables.payments.forEach {
                id("payment.id", it.id)
                id("payment.obligationId", it.obligationId)
                if (it.installmentNumber !in 1..10_000) fail("payment installment number")
                stamp("payment.dueDate", it.dueDate)
                rill("payment.amountRial", it.amountRial)
                it.paidDate?.let { paid -> stamp("payment.paidDate", paid) }
                text("payment.linkedTxnRef", it.linkedTxnRef, MAX_TEXT_CHARS)
            }

            rows("reservations", tables.reservations.size, MAX_LEDGER_ROWS)
            tables.reservations.forEach {
                id("reservation.id", it.id)
                text("reservation.purpose", it.purpose, MAX_NAME_CHARS * 2, allowBlank = false)
                rill("reservation.amountRial", it.amountRial)
                optText("reservation.categoryId", it.categoryId, MAX_ID_CHARS)
                stamp("reservation.createdAt", it.createdAt)
                stamp("reservation.updatedAt", it.updatedAt)
            }
        }
    }

    private companion object {
        val HEX_64 = Regex("^[a-f0-9]{64}$")
        val CATEGORY_KINDS = setOf(CategoryKind.EXPENSE, CategoryKind.INCOME, CategoryKind.TRANSFER)
        val GOAL_KINDS = setOf(GoalKind.SAVE, GoalKind.CAP)
        val GOAL_PERIODS = setOf(GoalPeriod.MONTH, GoalPeriod.ONCE)
    }
}

// Caps. Every one is generous enough for a decade of use and small enough that a hostile file
// cannot make the importer allocate or iterate without bound.
const val MAX_PAYLOAD_BYTES: Int = 64 * 1024 * 1024
private const val MAX_FUTURE_DRIFT_MS: Long = 30L * 24 * 60 * 60 * 1000
private const val DAY_MS: Long = 24L * 60 * 60 * 1000
private const val MAX_RIAL: Long = 1_000_000_000_000_000L
private const val MAX_AMOUNT: Double = 1e18
private const val MAX_ID_CHARS: Int = 300
private const val MAX_TEXT_CHARS: Int = 100_000
private const val MAX_NOTE_CHARS: Int = 200_000
private const val MAX_MESSAGE_BODY_CHARS: Int = 100_000
private const val MAX_NAME_CHARS: Int = 10_000
private const val MAX_PAN_CHARS: Int = 512
private const val MAX_THEME_CHARS: Int = 64
private const val MAX_APP_VERSION_CHARS: Int = 64
private const val MAX_META_VALUE_CHARS: Int = 8 * 1024 * 1024
private const val MAX_KEY_GEN: Int = 8
private const val MAX_MESSAGES: Int = 60_000
private const val MAX_META_ROWS: Int = 16
private const val MAX_LEDGER_ROWS: Int = 20_000
private const val MAX_DECISION_ROWS: Int = 200_000
private const val MAX_MANUAL_ROWS: Int = 200_000
private const val MAX_RULE_ROWS: Int = 20_000
private const val MAX_CATEGORY_ROWS: Int = 10_000
private const val MAX_ACCOUNT_ROWS: Int = 2_000
private const val MAX_RATE_ROWS: Int = 10_000
private const val MAX_HISTORY_ROWS: Int = 20_000
private const val MAX_SEEN_SMS: Int = 10_000
private const val MAX_PAYMENT_ROWS: Int = 200_000
private const val MAX_WIDGET_SLOTS: Int = 100
private const val MAX_NUMBERS_PER_BANK: Int = 100
