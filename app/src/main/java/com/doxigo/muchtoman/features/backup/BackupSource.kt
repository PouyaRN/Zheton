package com.doxigo.muchtoman.features.backup

import com.doxigo.muchtoman.BuildConfig
import com.doxigo.muchtoman.DurableDb
import com.doxigo.muchtoman.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything a backup needs, behind one method.
 *
 * The screen never touches `Store` or Room directly; it asks a [BackupSource] for a payload.
 * That is what lets the export path be tested on a plain JVM with a fake source, and it keeps
 * the one place that knows the shape of this app's storage to one file.
 *
 * ## What is deliberately left out, and why
 *
 * - **`sync_*` credentials** (`sync_key`, `sync_token`, `sync_base`, `sync_device`, …). A backup
 *   is a copy of the ledger, not of the household's keys. Restoring on a second phone joins
 *   the household again through the invite flow; copying the bearer token into a file that
 *   might sit in a Downloads folder would be a much larger blast radius for a smaller
 *   convenience.
 * - **`lockEnabled`.** A file must not be able to switch the app lock on (or off) on a phone.
 * - **Cached rates and the TSE snapshot.** Network data, stale by definition, refetched on the
 *   first launch.
 * - **Rows the read APIs do not return.** See [DeviceBackupSource]: archived categories,
 *   tombstoned rules/decisions/goals, and messages outside the durable store are not included.
 *
 * ## The wiring hook that has to live in `MainActivity`
 *
 * Two things are only reachable from `MainActivity` and are therefore documented rather than
 * wired here, because `MainActivity.kt` is outside this feature's write scope:
 *
 * 1. **Opening the screen.** `SettingsScreen` needs one more row:
 *    `onBackup = { activity.startActivity(Intent(activity, BackupActivity::class.java)) }`
 *    plus the manifest entry in the report. `BackupActivity` is a normal exported=false
 *    `ComponentActivity`.
 * 2. **Refreshing the in-memory UI after a restore.** `AppVm` reads `Store`/Room once into
 *    `UiState`; nothing here can reach it. The activity finishes with `RESULT_OK`, so the
 *    caller should `recreate()` the activity (or call a new `AppVm.reloadFromStore()`) when the
 *    result is OK. Without that the restored ledger is on disk but the screen still shows the
 *    previous one until the next process start.
 */
interface BackupSource {
    suspend fun snapshot(
        includeMessages: Boolean = true,
        now: Long = System.currentTimeMillis(),
    ): BackupPayload
}

/**
 * The real source: `Store` for preferences and its own lists, Room for the durable tables.
 *
 * ### Known gaps, created by the read APIs rather than chosen
 *
 * The DAOs expose *live* rows: `CategoryDao.all()` filters `archived = 0`, `RuleDao.active()`
 * and `TxnDecisionDao.all()` filter `deleted = 0`, `GoalDao.active()` filters `deleted = 0`,
 * and `InstallmentPaymentDao` has no `all()` (payments are gathered per obligation). Adding
 * the missing queries means editing `Ledger.kt`/`Rules.kt`, which is outside this feature's
 * write scope. The consequences are stated in the report and in the UI's threat-model card:
 *
 * - **Archived categories** are not backed up. A restored ledger keeps transaction references
 *   by id but the archived category's name may be missing until it is recreated.
 * - **Tombstoned** rules/decisions/goals are not backed up. They are not needed to read the
 *   ledger; what is missing is the recorded "this was deleted", so a re-derive does not
 *   resurrect anything that was never in the file.
 *
 * `durable_meta` also has no "all keys" query, so [BACKUP_META_KEYS] is read one key at a time.
 * That is the same allowlist the codec enforces on import, so the two cannot drift.
 */
class DeviceBackupSource(
    private val store: Store,
    private val db: DurableDb,
    private val appVersion: String = BuildConfig.VERSION_NAME,
) : BackupSource {

    override suspend fun snapshot(includeMessages: Boolean, now: Long): BackupPayload = withContext(Dispatchers.IO) {
        val meta = BACKUP_META_KEYS.mapNotNull { key ->
            db.meta().get(key)?.let { BackupMeta(key, it) }
        }

        // Payments are read per obligation; there is no all() query on the DAO.
        val obligations = db.obligations().all()
        val payments = obligations.flatMap { db.installmentPayments().forObligation(it.id) }

        val tables = BackupTables(
            messages = if (includeMessages) db.smsSource().allOldestFirst().map { it.toBackup() } else emptyList(),
            meta = meta,
            anchors = db.anchors().all().map { it.toBackup() },
            categories = db.categories().all().map { it.toBackup() },
            rules = db.rules().active().map { it.toBackup() },
            decisions = db.decisions().all().map { it.toBackup() },
            linkDecisions = db.linkDecisions().all().map { it.toBackup() },
            manualTxns = db.manual().all().map { it.toBackup() },
            merchantAliases = db.merchantAliases().all().map { it.toBackup() },
            goals = db.goals().active().map { it.toBackup() },
            familyMembers = db.familyMembers().all().map { it.toBackup() },
            familyTxns = db.familyTxns().all().map { it.toBackup() },
            syncPublications = db.syncPublications().all().map { it.toBackup() },
            obligations = obligations.map { it.toBackup() },
            payments = payments.map { it.toBackup() },
            reservations = db.reservations().all().map { it.toBackup() },
        )

        val ledger = BackupLedger(
            holdings = store.holdings,
            commitments = store.commitments.map { it.toBackup() },
            overrides = store.overrides,
            history = store.history,
            bankAccounts = store.bankAccounts,
            disabledBanks = store.disabledBanks,
            hiddenCategories = store.hiddenCategories,
            extraBankNumbers = store.extraBankNumbers,
            dismissedSenders = store.dismissedSenders,
            strangeSenders = store.strangeSenders,
            widgetSlots = store.widgetSlots.map { it.toBackup() },
        )

        val prefs = BackupPrefs(
            name = store.name,
            themeMode = store.themeMode.name,
            smsEnabled = store.smsEnabled,
            widgetLock = store.widgetLock,
            showHidden = store.showHidden,
            hourlyWageToman = store.hourlyWageToman,
            cycleMode = store.cycleMode.name,
            cycleAnchorDay = store.cycleAnchorDay,
            smsScannedTo = store.smsScannedTo,
            seenSms = store.seenSms,
        )

        BackupPayload(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            createdAt = now,
            appVersion = appVersion,
            includesMessages = includeMessages,
            prefs = prefs,
            ledger = ledger,
            tables = tables,
        )
    }
}
