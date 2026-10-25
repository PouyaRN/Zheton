package com.doxigo.muchtoman.features.backup

import androidx.room.withTransaction
import com.doxigo.muchtoman.CycleMode
import com.doxigo.muchtoman.DurableDb
import com.doxigo.muchtoman.DurableMeta
import com.doxigo.muchtoman.Store
import com.doxigo.muchtoman.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Applying a payload is a different kind of operation from producing one, and it is the
 * dangerous half. This interface exists so the apply path has exactly one implementation to
 * audit, and so the import screen can be tested with a fake that records calls instead of
 * writing a database.
 */
interface BackupTarget {
    suspend fun restore(
        payload: BackupPayload,
        options: BackupRestoreOptions = BackupRestoreOptions(),
    ): BackupRestoreReport
}

data class BackupRestoreOptions(
    /** Off when the user only wants the durable rows, not the settings from the file. */
    val restorePreferences: Boolean = true,
    /** Off to keep the messages that are already on this phone and take none from the file. */
    val restoreMessages: Boolean = true,
)

/** What a restore actually wrote, in the same vocabulary the confirmation sheet uses. */
data class BackupRestoreReport(
    val messages: Int = 0,
    val manualTransactions: Int = 0,
    val decisions: Int = 0,
    val rules: Int = 0,
    val categories: Int = 0,
    val anchors: Int = 0,
    val goals: Int = 0,
    val obligations: Int = 0,
    val payments: Int = 0,
    val reservations: Int = 0,
    val familyMembers: Int = 0,
    val familyTxns: Int = 0,
    val merchantAliases: Int = 0,
    val linkDecisions: Int = 0,
    val preferencesRestored: Boolean = false,
) {
    val totalRows: Int
        get() = messages + manualTransactions + decisions + rules + categories + anchors +
            goals + obligations + payments + reservations + familyMembers + familyTxns +
            merchantAliases + linkDecisions
}

/**
 * The real restore, and its two rules.
 *
 * ### Merge, never delete
 *
 * Durable rows are upserted by primary key. Importing a backup onto a phone that already has
 * data can *add* to it or *update* the rows named in the file; it cannot remove a message, a
 * correction or a manual entry. That asymmetry is on purpose: a file is easy to open by
 * mistake, and losing a ledger to a mis-tap is unrecoverable, while a duplicate row is visible
 * and fixable. The store's own lists (holdings, bank accounts, widget layout) are different —
 * they are a single snapshot and are replaced wholesale, which is why the UI asks first.
 *
 * Inserting messages uses the DAO's `IGNORE` conflict strategy, so re-importing the same file
 * is free and does not rewrite `ingested_at`.
 *
 * ### Durable first, preferences last
 *
 * Writing `smsScannedTo` before the messages would let an ingest racing the restore skip the
 * restored range. The rows land in one transaction, and the preferences that control what
 * happens *next* are written after it commits.
 *
 * [afterRestore] is the hook the app uses to re-derive the ledger and refresh widgets; it runs
 * after the transaction and is injectable, so a test can assert ordering without a database.
 */
class DeviceBackupTarget(
    private val store: Store,
    private val db: DurableDb,
    private val afterRestore: suspend () -> Unit = {},
) : BackupTarget {

    override suspend fun restore(
        payload: BackupPayload,
        options: BackupRestoreOptions,
    ): BackupRestoreReport = withContext(Dispatchers.IO) {
        val tables = payload.tables
        var report = BackupRestoreReport()

        db.withTransaction {
            val messages = if (options.restoreMessages && payload.includesMessages) {
                db.smsSource().insertAll(tables.messages.map { it.toEntity() }).count { it != -1L }
            } else {
                0
            }

            // Meta is an allowlist twice over: the codec dropped everything else, and this
            // loop refuses to write a key the app did not itself define.
            for (entry in tables.meta) {
                if (entry.k !in BACKUP_META_KEYS) continue
                db.meta().put(DurableMeta(entry.k, entry.v))
            }

            for (row in tables.anchors) db.anchors().put(row.toEntity())
            if (tables.categories.isNotEmpty()) {
                db.categories().putAll(tables.categories.map { it.toEntity() })
            }
            for (row in tables.rules) db.rules().put(row.toEntity())
            for (row in tables.decisions) db.decisions().put(row.toEntity())
            for (row in tables.linkDecisions) db.linkDecisions().put(row.toEntity())
            for (row in tables.manualTxns) db.manual().put(row.toEntity())
            for (row in tables.merchantAliases) db.merchantAliases().put(row.toEntity())
            for (row in tables.goals) db.goals().put(row.toEntity())
            for (row in tables.familyMembers) db.familyMembers().put(row.toEntity())
            for (row in tables.familyTxns) db.familyTxns().put(row.toEntity())
            if (tables.syncPublications.isNotEmpty()) {
                db.syncPublications().putAll(tables.syncPublications.map { it.toEntity() })
            }
            for (row in tables.obligations) db.obligations().put(row.toEntity())
            if (tables.payments.isNotEmpty()) {
                db.installmentPayments().putAll(tables.payments.map { it.toEntity() })
            }
            for (row in tables.reservations) db.reservations().put(row.toEntity())

            report = BackupRestoreReport(
                messages = messages,
                manualTransactions = tables.manualTxns.size,
                decisions = tables.decisions.size,
                rules = tables.rules.size,
                categories = tables.categories.size,
                anchors = tables.anchors.size,
                goals = tables.goals.size,
                obligations = tables.obligations.size,
                payments = tables.payments.size,
                reservations = tables.reservations.size,
                familyMembers = tables.familyMembers.size,
                familyTxns = tables.familyTxns.size,
                merchantAliases = tables.merchantAliases.size,
                linkDecisions = tables.linkDecisions.size,
                preferencesRestored = options.restorePreferences,
            )
        }

        if (options.restorePreferences) restoreStore(payload.ledger, payload.prefs)

        afterRestore()
        report
    }

    /**
     * The store's own lists and settings. Replaced wholesale because they are one snapshot,
     * except for anything a file must not be able to change: `lockEnabled` stays as it is.
     */
    private fun restoreStore(ledger: BackupLedger, prefs: BackupPrefs) {
        store.holdings = ledger.holdings
        store.commitments = ledger.commitments.map { it.toEntity() }
        store.overrides = ledger.overrides
        store.history = ledger.history
        store.bankAccounts = ledger.bankAccounts
        store.disabledBanks = ledger.disabledBanks
        store.hiddenCategories = ledger.hiddenCategories
        store.extraBankNumbers = ledger.extraBankNumbers
        store.dismissedSenders = ledger.dismissedSenders
        store.strangeSenders = ledger.strangeSenders
        store.widgetSlots = ledger.widgetSlots.mapNotNull { it.toEntityOrNull() }
        store.seenSms = prefs.seenSms

        store.name = prefs.name
        store.themeMode = runCatching { ThemeMode.valueOf(prefs.themeMode) }.getOrDefault(store.themeMode)
        store.smsEnabled = prefs.smsEnabled
        store.widgetLock = prefs.widgetLock
        store.showHidden = prefs.showHidden
        store.hourlyWageToman = prefs.hourlyWageToman
        store.cycleMode = if (prefs.cycleMode == CycleMode.ANCHOR.name) {
            CycleMode.ANCHOR
        } else {
            CycleMode.JALALI_MONTH
        }
        store.cycleAnchorDay = prefs.cycleAnchorDay
        store.smsScannedTo = prefs.smsScannedTo
        // lockEnabled is deliberately not restored: a file must not switch the app lock.
    }
}
