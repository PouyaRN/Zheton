package com.doxigo.muchtoman

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import java.io.File
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.doxigo.muchtoman.features.reminders.HandledReminder
import com.doxigo.muchtoman.features.reminders.ReminderActions
import com.doxigo.muchtoman.features.reminders.ReminderScheduler

private const val WALLET_REFRESH_MS = 10 * 60_000L
private const val STOCK_REFRESH_MS = 10 * 60_000L
private const val MAX_PARALLEL_WALLET_FETCHES = 4
/** The default timeline browsing window — recently used in ledgerEntries' default limit. */
private const val LEDGER_WINDOW = 300
/** When a structured filter is live, the whole stored ledger becomes the matchable source. */
private const val FILTER_WIDE_LIMIT = 50_000

class AppVm(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val ledgerEngine = LedgerEngine(app, extraBankNumbersProvider = { store.extraBankNumbers })

    /**
     * The scan in flight, if any. Only one runs at a time, and anything that wants the inbox
     * read differently — from the start, or from further back — waits for this one to finish
     * before changing what it would read. Without that, a scan can wake up holding balances
     * computed against a store that has since been wiped and write them straight back over it.
     */
    private var scanJob: Job? = null
    private var stockJob: Job? = null
    private val walletSemaphore = Semaphore(MAX_PARALLEL_WALLET_FETCHES)

    private val _state = MutableStateFlow(
        UiState(
            holdings = store.holdings,
            rates = store.cachedRates,
            tse = store.cachedStocks,
            overrides = store.overrides,
            lockEnabled = store.lockEnabled,
            widgetLock = store.widgetLock,
            locked = store.lockEnabled,
            name = store.name,
            themeMode = store.themeMode,
            history = store.history,
            smsEnabled = store.smsEnabled,
            bankAccounts = store.bankAccounts,
            disabledBanks = store.disabledBanks,
            hiddenCategories = store.hiddenCategories,
            showHidden = store.showHidden,
            strangeSenders = store.strangeSenders,
            dismissedUpdate = store.dismissedUpdate,
            commitments = store.commitments,
            rateHistoryStore = store.rateHistoryStore,
            hourlyWageToman = store.hourlyWageToman,
            cycleMode = store.cycleMode,
            cycleAnchorDay = store.cycleAnchorDay,
            monthlyBudgetToman = store.monthlyBudgetToman,
            widgetSlots = store.widgetSlots,
            holdingLots = store.holdingLots,
            onboardingCompleted = store.onboardingCompleted,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun completeOnboarding() {
        store.onboardingCompleted = true
        _state.update { it.copy(onboardingCompleted = true) }
    }

    private var ledgerJob: Job? = null
    private var familySyncJob: Job? = null
    /** Serialises obligation/reservation state refreshes so rapid taps don't race. */
    private val ledgerMutex = Mutex()

    init {
        loadDailyRates()
        refresh()
        refreshStocksIfHeld()
        refreshWallets()
        scanSms()
        runLedger()
        viewModelScope.launch {
            ledgerJob?.join()
            requestFamilySync(silent = true)
        }
        runCatching {
            val jobScheduler = app.getSystemService(android.app.job.JobScheduler::class.java)
            jobScheduler?.allPendingJobs
                ?.filter { it.networkType != android.app.job.JobInfo.NETWORK_TYPE_NONE }
                ?.forEach { jobScheduler.cancel(it.id) }
        }
        scheduleDailySnapshot(app)
        com.doxigo.muchtoman.features.pricealerts.PriceAlertWorker.sync(app)

        // Reminders are local WorkManager jobs: re-plan on every cold start, because a rebuild
        // after an app update, a restore or a reboot must not leave a due date unarmed. The
        // planner is idempotent and cheap (prefs + one Room read), so this runs beside the
        // daily snapshot rather than behind a flag.
        viewModelScope.launch { ReminderScheduler.reschedule(app) }
        // A tap on «رسیدگی شد» arrives from a notification receiver. While this VM is alive it
        // is handled here (mark the installment paid / settle the commitment); a tap that landed
        // while the app was dead was queued in prefs and is drained now, exactly once.
        ReminderActions.onHandled = ::onReminderHandled
        viewModelScope.launch {
            ReminderActions.drainPending(app).forEach(::onReminderHandled)
        }
    }

    /**
     * One handled reminder, from the notification shade to the obligation it was about.
     *
     * The planner tags items `installment:<paymentId>` / `commitment:<id>`; anything else (the
     * daily check-in) only needs the schedule rebuilt.
     */
    private fun onReminderHandled(event: HandledReminder) {
        when (event.source) {
            "installment" -> event.rawId?.let { markInstallmentPaid(it) }
            "commitment" -> event.rawId?.let { toggleCommitmentSettled(it) }
        }
        viewModelScope.launch { ReminderScheduler.reschedule(getApplication()) }
    }

    /**
     * The ledger, deliberately **not** behind the SMS permission.
     *
     * Ingest needs `READ_SMS`; deriving does not, and wiring the two together broke the one
     * promise the split was built for — a parser fix rebuilding her ledger offline, from the
     * messages already stored, after she has revoked the permission. It ran inside the scan at
     * first, and revoking the permission silently stopped every rebuild.
     *
     * It also runs beside the balance fold rather than instead of it, and writes nothing the
     * fold reads, so a failure here costs a rebuild later and never a balance now.
     */
    fun runLedger() {
        if (ledgerJob?.isActive == true) return
        val app = getApplication<Application>()
        ledgerJob = viewModelScope.launch(Dispatchers.Default) {
            runCatching { runLedgerPipeline(app, extraLookup(store.extraBankNumbers)) }
                .onFailure { android.util.Log.d("zheton", "ledger pipeline failed: $it") }
        }
    }

    /**
     * Called every time the app comes back to the foreground. The Worker's edge cache is
     * 10 minutes, so anything younger than that cannot change; anything older gets refetched
     * without her having to find the button.
     */
    fun refreshIfStale() {
        if (System.currentTimeMillis() - _state.value.rates.updatedAt > 10 * 60_000L) refresh()
        refreshStocksIfHeld()
        refreshWallets()
        // Coming back is also when messages that arrived while she was away get read.
        scanSms()
        runLedger()
        requestFamilySync(silent = true)
    }

    fun refreshAll() {
        refresh()
        refreshStocksIfHeld(force = true)
        refreshWallets(force = true)
    }

    /** Rescan SMS and refresh all external pricing and holdings data for the ledger timeline. */
    fun refreshLedger() {
        scanSms()
        refreshAll()
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            var failure: String? = null
            fetchRates(BuildConfig.RATES_URL).onSuccess { fetched ->
                val fresh = mergeRates(fetched, store.cachedRates)
                store.cachedRates = fresh
                store.rateHistoryStore.recordAllSnapshot(
                    fresh.toman,
                    timestampMs = if (fresh.updatedAt > 0L) fresh.updatedAt else System.currentTimeMillis(),
                )
                _state.update { it.copy(rates = fresh) }
                checkPriceAlerts()
                saveDailyRates(fetched.toman, fetched.updatedAt)
            }.onFailure { e ->
                // Keep showing the cached rates; an old number beats a blank screen. The
                // log line is the only place the real reason survives — the UI stays Persian
                // and calm, but `adb logcat -s muchtoman` must be able to answer "why".
                android.util.Log.d("zheton", "rates fetch failed: $e")
                failure = "نرخ‌ها به‌روز نشدن. اینترنتت رو چک کن."
            }

            _state.update { it.copy(loading = false, error = failure) }
            recordSnapshot()
        }
    }

    /** Price alerts are checked on every successful foreground rate refresh. */
    private fun checkPriceAlerts() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                com.doxigo.muchtoman.features.pricealerts.PriceAlerts.check(
                    app,
                    effectiveRates(store.cachedRates, store.overrides, store.cachedStocks),
                )
            }
        }
    }

    fun refreshStocksForPicker() = refreshStocks(requireHolding = false)

    private fun refreshStocksIfHeld(force: Boolean = false) =
        refreshStocks(force = force, requireHolding = true)

    private fun refreshStocks(force: Boolean = false, requireHolding: Boolean) {
        val current = _state.value
        if (requireHolding && current.holdings.none { isStockId(it.typeId) }) return
        if (!force && System.currentTimeMillis() - current.tse.updatedAt <= STOCK_REFRESH_MS) return
        if (stockJob?.isActive == true) return

        _state.update { it.copy(stocksLoading = true) }
        stockJob = viewModelScope.launch {
            var changed = false
            fetchTse().onSuccess { fresh ->
                changed = true
                store.cachedStocks = fresh
                _state.update { it.copy(tse = fresh) }
                checkPriceAlerts()
                saveDailyRates(fresh.toman, fresh.updatedAt)
                android.util.Log.d("zheton", "tse ok: ${fresh.stocks.size} instruments")
            }.onFailure { error ->
                android.util.Log.d("zheton", "tse fetch failed: $error")
            }
            _state.update { it.copy(stocksLoading = false) }
            if (changed && _state.value.holdings.any { isStockId(it.typeId) }) recordSnapshot()
        }
    }

    /** Waves off one release by name, so the next one asks again. */
    fun dismissUpdate(version: String) {
        store.dismissedUpdate = version
        _state.update { it.copy(dismissedUpdate = version) }
    }

    fun setHolding(key: String, typeId: String, amount: Double) {
        saveHolding(key, typeId, amount, wallet = null)
        _state.update { it.copy(walletErrors = it.walletErrors - key) }
    }

    /** Her own name for a holding, or blank to go back to the asset's own. */
    fun setLabel(key: String, label: String) = persist(
        _state.value.holdings.map {
            if (it.key == key) it.copy(label = label.trim().take(32)) else it
        },
    )

    /**
     * Writes the row [key] names, or adds one under that key if she has no such row yet — which
     * is how a second Tether beside the first one gets made: the picker hands out a fresh key
     * rather than the asset's own, so nothing here can find the holding she already had.
     */
    private fun saveHolding(key: String, typeId: String, amount: Double, wallet: WalletLink?) {
        val list = _state.value.holdings
        // Copied, not rebuilt: editing the amount must not quietly un-exclude a set-aside
        // asset, nor drop the name she gave it.
        val next = if (list.any { it.key == key }) {
            list.map { if (it.key == key) it.copy(amount = amount, wallet = wallet) else it }
        } else {
            list + Holding(typeId = typeId, amount = amount, wallet = wallet, id = key)
        }
        // Fixed assets keep their catalogue order; coins fall in after them in the order she
        // added them (sortedBy is stable), since there is no meaningful order for 250 coins.
        val order = STATIC_CATALOG.withIndex().associate { (i, t) -> t.id to i }
        persist(next.sortedBy { order[it.typeId] ?: Int.MAX_VALUE })
    }

    /** A rainy-day asset: stays on the list, drops out of the total. */
    fun setExcluded(key: String, excluded: Boolean) =
        persist(_state.value.holdings.map {
            if (it.key == key) it.copy(excluded = excluded) else it
        })

    /**
     * Files the row away in بایگانی: off Home, off بازار, off the totals — but still hers,
     * one tap from coming back. Not the same as [setExcluded], which keeps the row in view;
     * and never applied to the bank row, whose existence belongs to the accounts sheet.
     */
    fun setArchived(key: String, archived: Boolean) =
        persist(_state.value.holdings.map {
            if (it.key == key && it.typeId != BANK_ID) it.copy(archived = archived) else it
        })

    /**
     * Writes the manage-list order: [key] moves to [toIndex] within the visible section,
     * and every row between shifts by one. Only relative order is kept — the numbers are
     * renumbered densely from zero so gaps never accumulate through repeated drags.
     */
    fun reorderHolding(key: String, toIndex: Int) {
        val rows = _state.value.holdings
        val visible = rows.filter { !it.archived }
        val clamped = toIndex.coerceIn(0, visible.lastIndex.coerceAtLeast(0))
        val reordered = visible.toMutableList().apply {
            val from = indexOfFirst { it.key == key }
            if (from < 0) return
            add(clamped, removeAt(from))
        }
        val newOrder = reordered.withIndex().associate { (i, h) -> h.key to i }
        persist(rows.map { h ->
            val at = newOrder[h.key]
            if (at != null) h.copy(order = at) else h
        })
    }

    fun removeHolding(key: String) {
        persist(_state.value.holdings.filterNot { it.key == key })
        if (key in _state.value.holdingLots) persistLots(_state.value.holdingLots - key)
        _state.update {
            it.copy(
                refreshingWallets = it.refreshingWallets - key,
                walletErrors = it.walletErrors - key,
            )
        }
    }

    fun deleteHolding(key: String) = removeHolding(key)

    fun clearWalletError(key: String) {
        if (key !in _state.value.walletErrors) return
        _state.update { it.copy(walletErrors = it.walletErrors - key) }
    }

    fun connectWallet(
        key: String,
        typeId: String,
        option: WalletOption,
        address: String,
        onSuccess: () -> Unit,
    ) {
        if (key in _state.value.refreshingWallets) return
        val wallet = WalletLink(
            network = option.network,
            networkFa = option.networkFa,
            address = address.trim(),
            contract = option.contract,
        )
        _state.update {
            it.copy(
                refreshingWallets = it.refreshingWallets + key,
                walletErrors = it.walletErrors - key,
            )
        }
        viewModelScope.launch {
            walletSemaphore.withPermit { fetchWalletBalance(BuildConfig.RATES_URL, wallet) }
                .onSuccess { balance ->
                    saveHolding(
                        key,
                        typeId,
                        balance.amount,
                        wallet.copy(updatedAt = balance.updatedAt),
                    )
                    _state.update {
                        it.copy(
                            refreshingWallets = it.refreshingWallets - key,
                            walletErrors = it.walletErrors - key,
                        )
                    }
                    onSuccess()
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            refreshingWallets = it.refreshingWallets - key,
                            walletErrors = it.walletErrors + (key to walletErrorMessage(error)),
                        )
                    }
                }
        }
    }

    fun refreshWallets(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val busy = _state.value.refreshingWallets
        val tracked = _state.value.holdings.filter { holding ->
            val wallet = holding.wallet ?: return@filter false
            holding.key !in busy && (force || now - wallet.updatedAt >= WALLET_REFRESH_MS)
        }
        if (tracked.isEmpty()) return

        val ids = tracked.map { it.key }.toSet()
        _state.update {
            it.copy(
                refreshingWallets = it.refreshingWallets + ids,
                walletErrors = it.walletErrors - ids,
            )
        }
        viewModelScope.launch {
            val results = tracked.map { holding ->
                async {
                    holding to walletSemaphore.withPermit {
                        fetchWalletBalance(BuildConfig.RATES_URL, holding.wallet!!)
                    }
                }
            }.awaitAll().associateBy { it.first.key }

            val current = _state.value
            val errors = current.walletErrors.toMutableMap()
            var changed = false
            val next = current.holdings.map { holding ->
                val (original, result) = results[holding.key] ?: return@map holding
                val wallet = holding.wallet ?: return@map holding
                if (wallet != original.wallet) return@map holding
                result.fold(
                    onSuccess = { balance ->
                        changed = true
                        errors -= holding.key
                        holding.copy(
                            amount = balance.amount,
                            wallet = wallet.copy(updatedAt = balance.updatedAt),
                        )
                    },
                    onFailure = { error ->
                        errors[holding.key] = walletErrorMessage(error)
                        holding
                    },
                )
            }

            if (changed) store.holdings = next
            _state.update {
                it.copy(
                    holdings = next,
                    refreshingWallets = it.refreshingWallets - ids,
                    walletErrors = errors,
                )
            }
            if (changed) recordSnapshot()
        }
    }

    private fun walletErrorMessage(error: Throwable): String =
        when ((error as? WalletFetchException)?.reason) {
            "invalid_address", "invalid_contract" ->
                "این آدرس با شبکه انتخاب‌شده جور نیست."
            "unsupported_network" -> "هنوز نمی‌شه از این شبکه استفاده کرد."
            else -> "موجودی نیومد. اینترنتت رو چک کن و دوباره امتحان کن."
        }

    fun setName(name: String) {
        store.name = name
        _state.update { it.copy(name = name.trim()) }
    }

    fun setThemeMode(mode: ThemeMode) {
        store.themeMode = mode
        _state.update { it.copy(themeMode = mode) }
    }

    fun updateWidgetSlots(slots: List<com.doxigo.muchtoman.widget.engine.WidgetSlot>) {
        store.widgetSlots = slots
        _state.update { it.copy(widgetSlots = slots) }
    }

    /** Locked state is session-only; the *preference* is what persists. */
    fun setLockEnabled(on: Boolean) {
        store.lockEnabled = on
        _state.update { it.copy(lockEnabled = on, locked = false) }
    }

    /** The widget's own mask, independent of the app lock. Takes effect on the spot. */
    fun setWidgetLock(on: Boolean) {
        store.widgetLock = on
        _state.update { it.copy(widgetLock = on) }
        updateTotalWidget(getApplication())
    }

    /** Hourly wage in Toman for calculating work-hours cost of expenses. */
    fun setHourlyWageToman(wage: Long) {
        store.hourlyWageToman = wage
        _state.update { it.copy(hourlyWageToman = wage) }
    }

    /** Monthly budget in Toman; 0 clears it. */
    fun setMonthlyBudgetToman(toman: Long) {
        val v = toman.coerceAtLeast(0L)
        store.monthlyBudgetToman = v
        _state.update { it.copy(monthlyBudgetToman = v) }
    }

    /** The window one financial month spans: calendar month or a payday-anchored cycle. */
    fun setCycleMode(mode: CycleMode) {
        store.cycleMode = mode
        _state.update { it.copy(cycleMode = mode) }
    }

    /** The Jalali day each payday-anchored cycle starts on (1..31, capped at month length). */
    fun setCycleAnchorDay(day: Int) {
        val clamped = day.coerceIn(1, 31)
        store.cycleAnchorDay = clamped
        _state.update { it.copy(cycleAnchorDay = clamped) }
    }

    /**
     * Resets scan watermarks and re-ingests SMS inbox messages from [daysAgo] (0 = full 13-month horizon).
     * Re-derives the full ledger and refreshes widgets.
     */
    fun forceRescanSms(daysAgo: Int = 90) {
        val app = getApplication<Application>()
        if (!store.smsEnabled || !canReadSms(app)) return
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val targetSince = if (daysAgo <= 0) 0L else System.currentTimeMillis() - (daysAgo * 24L * 3600L * 1000L)
            durable.meta().put(DurableMeta(SOURCE_SCANNED_TO, targetSince.toString()))
            store.smsScannedTo = targetSince
            runLedgerPipeline(app, extraLookup(store.extraBankNumbers))
            updateTotalWidget(app)
        }
    }

    /** Called when the app leaves the foreground, so returning to it asks again. */
    fun relock() {
        if (store.lockEnabled) _state.update { it.copy(locked = true) }
    }

    fun unlock() = _state.update { it.copy(locked = false) }

    /**
     * Reads whatever has landed in the inbox since the last look and folds it into the
     * balances. Cheap enough to run on every foreground: the query is bounded by the last scan
     * and a message already counted is skipped by content key, never re-applied.
     */
    fun scanSms() {
        val app = getApplication<Application>()
        if (!store.smsEnabled) return
        // She can take the permission away in Android's own settings, and then these balances
        // are frozen at whatever they last read while her real accounts move on. Switching the
        // feature off is what keeps them out of the total — they stay listed, and turning it
        // back on re-reads everything since.
        if (!canReadSms(app)) {
            setSmsEnabled(false)
            return
        }
        // One at a time. Two used to start on every cold open — init{} launches one and the
        // lifecycle observer replays ON_START into refreshIfStale() a moment later — and both
        // walked the whole inbox for one of them to be thrown away at the end.
        if (scanJob?.isActive == true) return
        // Dispatchers.Default: only the cursor read was ever off the UI thread. Everything
        // after it — the JSON decode of every balance and of every message already counted,
        // the parse of each message, and the encode on the way back — ran on the main thread,
        // and 1.0.2's schema bump makes the first scan after an upgrade the WHOLE inbox. On a
        // phone with years of messages that is the app frozen on first open.
        scanJob = viewModelScope.launch(Dispatchers.Default) { runScan(app) }
    }

    /**
     * ingest → derive, and then a check that the two ways of reaching a balance agree.
     *
     * Reaching back thirteen Jalali months on the first run is the point of ingest: her ledger
     * can only ever go as far back as the messages still in her inbox on the day this arrives,
     * and every release without it is a month of history nobody can get back.
     *
     * The fold and the derivation both run for now, and disagreeing is a log line rather than a
     * visible change. The fold is what she sees until they have agreed on a real phone for a
     * release; then the fold goes and this becomes the only answer.
     */
    private suspend fun runLedgerPipeline(app: Application, extra: Map<String, Bank>) {
        val durable = DurableDb.get(app)
        val derived = DerivedDb.get(app)

        migrateAnchorsFromPrefs(store.bankAccounts, durable, System.currentTimeMillis())
        seedBuiltins(durable)
        loadSession(durable)?.let { refreshFamily(it) }
        val added = ingestBankSms(app, durable, extra)
        // Derive when anything new arrived, or when this build reads messages differently from
        // whatever produced the rows already there. The second case needs no inbox, no
        // permission and no network — it is why a parser fix is now a Tuesday job.
        if (added > 0 || needsDerive(derived)) {
            val rows = derive(durable, derived, extra)
            android.util.Log.i("muchtoman", "ledger: +$added messages, $rows transactions")
        }

        _state.update { it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit())) }

        // Load obligations, payments and reservations so the home screen cards appear on
        // cold start, not only after a family-sync session or an explicit user action.
        refreshObligations(durable)

        // Only a disagreement about the *same* evidence is worth a line. The backfill reaches
        // thirteen months back while the fold only ever saw what arrived after its watermark,
        // so the ledger routinely knows about messages the fold never read — and then the two
        // differ because the ledger is right, which is the whole point of the backfill.
        for (balance in allBalances(derived, durable)) {
            val folded = store.bankAccounts.firstOrNull { it.bank == balance.accountId } ?: continue
            if (folded.anchored != balance.anchored) continue
            if (balance.at > folded.updatedAt) continue
            val foldedRial = Math.round(folded.balance * 10.0)
            if (foldedRial != balance.rial) {
                android.util.Log.w(
                    "muchtoman",
                    "ledger disagrees on ${balance.accountId} over the same messages: " +
                        "fold=$foldedRial derived=${balance.rial} Rial (fold at ${folded.updatedAt}, " +
                        "ledger at ${balance.at})",
                )
            }
        }
    }

    /**
     * The timeline floats a window of rows at the newest edge of the ledger. A structured filter
     * (ticket 04+) must be able to reach rows beyond that window — an old «اسنپ» must still
     * match — so turning the filter on widens the window to the whole stored ledger (bounded by
     * SOURCE_HARD_CAP), and turning it off narrows back to the browsing window. The checked
     * recompute that follows a bulk action keeps working unchanged: it derives once and rebuilds
     * the same LedgerView object, just with a wider source when a filter is live.
     */
    fun setFilterWide(wide: Boolean) {
        if (wide == _state.value.filterWide) return
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val derived = DerivedDb.get(app)
            _state.update { it.copy(filterWide = wide) }
            _state.update { it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit())) }
        }
    }

    private fun ledgerLimit(): Int =
        if (_state.value.filterWide) FILTER_WIDE_LIMIT else LEDGER_WINDOW

    /**
     * File one transaction, and — if she says «همیشه» — teach the app to file everything like
     * it by itself, past and future both.
     *
     * There is no backfill job behind that promise. Classification is total, so one new rule
     * plus one re-derive is the whole mechanism, and it applies to every transaction she has
     * ever had rather than only the ones that arrive next.
     */
    fun categorise(entry: LedgerEntry, categoryId: String, always: Boolean) {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                ledgerEngine.categorise(entry, categoryId, always)
                val app = getApplication<Application>()
                val durable = DurableDb.get(app)
                val derived = DerivedDb.get(app)
                _state.update { it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit())) }
                requestFamilySync(silent = true)
            }.onFailure { android.util.Log.d("zheton", "categorise failed: $it") }
        }
    }

    /**
     * Pin a name to one merchant source — the string the parser actually found.
     *
     * Every transaction whose parsed merchant normalises to [sourceNorm] shows [display] from
     * now on, in the timeline and in search. Classification, links and reports keep keying off
     * the source name; only the words on the screen change. An empty display unpins.
     */
    fun pinMerchantAlias(sourceNorm: String, displayName: String) {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                ledgerEngine.pinMerchantAlias(sourceNorm, displayName)
                val app = getApplication<Application>()
                val durable = DurableDb.get(app)
                val derived = DerivedDb.get(app)
                _state.update { it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit())) }
            }.onFailure { android.util.Log.d("zheton", "pin merchant alias failed: $it") }
        }
    }

    /**
     * One category across many transactions at once. The always-teach switch stays off on
     * purpose: bulk is a cleanup pass over what already happened, not a declaration about the
     * future. Reuse [categorise]'s own path, so links, sync and rules behave exactly as if she
     * had tapped each row.
     */
    fun bulkCategorise(entries: List<LedgerEntry>, categoryId: String) {
        if (entries.isEmpty()) return
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                ledgerEngine.bulkCategorise(entries, categoryId)
                val durable = DurableDb.get(app)
                val derived = DerivedDb.get(app)
                _state.update { it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit())) }
                recomputeAccountBalancesFromLedger()
                requestFamilySync(silent = true)
            }.onFailure { android.util.Log.d("zheton", "bulk categorise failed: $it") }
        }
    }

    /**
     * One note for many transactions at once — or none, which clears (tombstones) the note on
     * each of them, exactly as a per-row edit would. Writes each durable NOTE row through the
     * same path, then derives once for the whole batch and syncs the family once.
     */
    fun bulkSaveNote(entries: List<LedgerEntry>, note: String) {
        if (entries.isEmpty()) return
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                ledgerEngine.bulkSaveNote(entries, note)
                val durable = DurableDb.get(app)
                val derived = DerivedDb.get(app)
                _state.update { it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit())) }
                recomputeAccountBalancesFromLedger()
                requestFamilySync(silent = true)
            }.onFailure { android.util.Log.d("zheton", "bulk save note failed: $it") }
        }
    }

    /**
     * Set the display name of every merchant involved in the selected rows — or clear it, which
     * unpins each alias so the parser's original name returns everywhere. The alias is global
     * per merchantNorm (ticket 02 semantics), so the write is deduplicated by source name.
     */
    fun bulkPinMerchant(entries: List<LedgerEntry>, displayName: String) {
        if (entries.isEmpty()) return
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                ledgerEngine.bulkPinMerchant(entries, displayName)
                val durable = DurableDb.get(app)
                val derived = DerivedDb.get(app)
                _state.update { it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit())) }
                recomputeAccountBalancesFromLedger()
                requestFamilySync(silent = true)
            }.onFailure { android.util.Log.d("zheton", "bulk pin merchant failed: $it") }
        }
    }

    /**
     * Hide a transaction from the ledger, or bring it back.
     *
     * A durable HIDE decision is the tombstone: visible = deleted row, hidden = live row. The
     * transaction's derived rows stay in place (restore must not need a re-read), the ledger
     * view and the balances both read the hide set. This is the manual fix for a wrong entry —
     * the Maskan double count, a transaction that was never hers.
     */
    fun setTransactionHidden(entry: LedgerEntry, hidden: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val durable = DurableDb.get(app)
                val derived = DerivedDb.get(app)
                val session = loadSession(durable)
                val now = System.currentTimeMillis()
                val ref = entry.txn.ref
                val existing = durable.decisions().forRef(ref).firstOrNull { it.kind == DecisionKind.HIDE }
                durable.decisions().put(
                    TxnDecision(
                        id = existing?.id ?: uuid7(now),
                        ref = ref,
                        kind = DecisionKind.HIDE,
                        value = "1",
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                        deleted = !hidden,
                        memberId = session?.member.orEmpty(),
                        familyRef = entry.txn.familyRef.ifBlank { "" },
                    )
                )
                _state.update { it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit())) }
                recomputeAccountBalancesFromLedger()
                requestFamilySync(silent = true)
            }.onFailure { android.util.Log.d("zheton", "hide transaction failed: $it") }
        }
    }

    /**
     * Pin a transaction to a direction, or return it to the parser's reading.
     *
     * Flipping the parse of «deposit read as withdrawal» reverses the sign; the recompute moves
     * the total. The category stays put — changing which way money went is not permission to
     * silently re-file it. Unpin (null) restores the parser's direction.
     */
    fun setTransactionDirection(entry: LedgerEntry, direction: String?) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val durable = DurableDb.get(app)
                val derived = DerivedDb.get(app)
                val session = loadSession(durable)
                val now = System.currentTimeMillis()
                val ref = entry.txn.ref
                val existing = durable.decisions().forRef(ref).firstOrNull { it.kind == DecisionKind.DIRECTION }
                durable.decisions().put(
                    TxnDecision(
                        id = existing?.id ?: uuid7(now),
                        ref = ref,
                        kind = DecisionKind.DIRECTION,
                        value = direction,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                        deleted = direction == null,
                        memberId = session?.member.orEmpty(),
                        familyRef = entry.txn.familyRef.ifBlank { "" },
                    )
                )
                derive(durable, derived, extraLookup(store.extraBankNumbers))
                _state.update { it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit())) }
                recomputeAccountBalancesFromLedger()
                requestFamilySync(silent = true)
            }.onFailure { android.util.Log.d("zheton", "direction change failed: $it") }
        }
    }

    /**
     * Return a transaction to rule-based classification: mark the pinned category decision
     * deleted, so the next re-derive files it however the rules do.
     */
    fun unpinCategory(entry: LedgerEntry) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val durable = DurableDb.get(app)
                val derived = DerivedDb.get(app)
                val session = loadSession(durable)
                val now = System.currentTimeMillis()
                val existing = durable.decisions().forRef(entry.txn.ref)
                    .firstOrNull { it.kind == DecisionKind.CATEGORY }
                if (existing != null) {
                    durable.decisions().put(
                        existing.copy(deleted = true, updatedAt = now, memberId = session?.member.orEmpty())
                    )
                }
                derive(durable, derived, extraLookup(store.extraBankNumbers))
                _state.update { it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit())) }
                requestFamilySync(silent = true)
            }.onFailure { android.util.Log.d("zheton", "unpin category failed: $it") }
        }
    }

    /**
     * Mark or unmark a transaction as an internal self-transfer between the user's own accounts.
     * Reconciled self-transfers neutralize both income and spending, preventing distorted cash flow.
     */
    fun toggleTransfer(entry: LedgerEntry) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val durable = DurableDb.get(app)
                val derived = DerivedDb.get(app)
                val session = loadSession(durable)
                val now = System.currentTimeMillis()
                val ref = entry.txn.ref
                val previous = durable.decisions().forRef(ref)
                    .firstOrNull { it.kind == DecisionKind.CATEGORY }

                if (entry.transfer) {
                    // Unmark transfer: reject any transfer link touching this ref
                    derived.links().touching(ref)
                        .filter { it.kind == LinkKind.TRANSFER }
                        .forEach { link ->
                            durable.linkDecisions().put(
                                linkDecision(
                                    link.aRef,
                                    link.bRef,
                                    LinkKind.TRANSFER,
                                    Verdict.REJECTED,
                                    now,
                                )
                            )
                        }
                    // If category was pinned to CAT_TRANSFER, delete the decision
                    if (previous?.value == CAT_TRANSFER) {
                        durable.decisions().put(
                            previous.copy(deleted = true, updatedAt = now, memberId = session?.member.orEmpty())
                        )
                    }
                } else {
                    // Mark as transfer: pin category to CAT_TRANSFER
                    durable.decisions().put(
                        TxnDecision(
                            id = previous?.id ?: uuid7(now),
                            ref = ref,
                            kind = DecisionKind.CATEGORY,
                            value = CAT_TRANSFER,
                            createdAt = previous?.createdAt ?: now,
                            updatedAt = now,
                            memberId = session?.member.orEmpty(),
                            familyRef = entry.txn.familyRef.ifBlank { "" },
                        )
                    )
                }
                derive(durable, derived, extraLookup(store.extraBankNumbers))
                _state.update { it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit())) }
                recomputeAccountBalancesFromLedger()
                requestFamilySync(silent = true)
            }.onFailure { android.util.Log.d("zheton", "toggle transfer failed: $it") }
        }
    }

    /** How long a batch action waits before it becomes irreversible. */
    private val pendingBatchWindowMs = 10_000L
    @Volatile private var pendingBatchJob: kotlinx.coroutines.Job? = null
    @Volatile private var pendingBatch: PendingBatch? = null

    private enum class PendingKind { DELETE, HIDE, FLIP }

    private class PendingBatch(
        val kind: PendingKind,
        val entries: List<LedgerEntry>,
        /** For HIDE: true hides, false restores. Meaningless (and ignored) for the other kinds. */
        val hidden: Boolean = false,
    ) {
        /** The key pattern every decision of this source shares — SMS refs are `s:<hash>:<seq>`. */
        fun keyOf(txn: com.doxigo.muchtoman.Txn): String =
            if (txn.sourceKind == "manual") "m:${txn.ref.removePrefix("m:")}"
            else "s:${txn.srcHash}:"
    }

    /** Schedule one batch with the shared undo door: nothing is written until the window closes. */
    private fun scheduleBatch(batch: PendingBatch, label: String) {
        pendingBatchJobCancel()
        pendingBatch = batch
        _state.update {
            it.copy(
                pendingUndo = PendingUndo(
                    count = batch.entries.size,
                    label = label,
                    dueAt = System.currentTimeMillis() + pendingBatchWindowMs,
                )
            )
        }
        pendingBatchJob = viewModelScope.launch(Dispatchers.Default) {
            delay(pendingBatchWindowMs)
            val settled = pendingBatch ?: return@launch
            pendingBatch = null
            commitBatch(settled)
        }
    }

    private fun pendingBatchJobCancel() {
        pendingBatchJob?.cancel()
    }

    /** Undo = "the batch never ran". The window closed without a commit, so nothing to invert. */
    fun undoPendingBatch() {
        pendingBatchJobCancel()
        pendingBatch = null
        _state.update { it.copy(pendingUndo = null) }
    }

    /** The one-and-only commit: placement varies by kind, dedupe and post-steps are shared. */
    private suspend fun commitBatch(batch: PendingBatch) {
        val app = getApplication<Application>()
        runCatching {
            val durable = DurableDb.get(app)
            val derived = DerivedDb.get(app)
            val session = loadSession(durable)
            val now = System.currentTimeMillis()
            durable.withTransaction {
                when (batch.kind) {
                    PendingKind.DELETE -> {
                        val tombstones = deletedSourceHashes(durable.meta().get(PERMANENTLY_DELETED))
                            .toMutableSet()
                        for (entry in batch.entries) {
                            val txn = entry.txn
                            durable.decisions().deleteForRef(txn.ref, batch.keyOf(txn) + "%")
                            if (txn.sourceKind == "manual") {
                                durable.manual().tombstone(txn.ref.removePrefix("m:"))
                            } else {
                                durable.smsSource().deleteOne(txn.srcHash)
                                tombstones += txn.srcHash
                            }
                        }
                        // The tombstone outlives the message: a rewind or a restored inbox could
                        // deliver the same bytes again — this is what keeps them gone (spec 16).
                        if (tombstones.isNotEmpty()) {
                            durable.meta().put(
                                DurableMeta(PERMANENTLY_DELETED, tombstones.sorted().joinToString("\n"))
                            )
                        }
                    }
                    PendingKind.HIDE -> {
                        for (entry in batch.entries) {
                            val ref = entry.txn.ref
                            val existing = durable.decisions().forRef(ref)
                                .firstOrNull { it.kind == DecisionKind.HIDE }
                            durable.decisions().put(
                                TxnDecision(
                                    id = existing?.id ?: uuid7(now),
                                    ref = ref,
                                    kind = DecisionKind.HIDE,
                                    value = "1",
                                    createdAt = existing?.createdAt ?: now,
                                    updatedAt = now,
                                    deleted = !batch.hidden,
                                    memberId = session?.member.orEmpty(),
                                    familyRef = entry.txn.familyRef.ifBlank { "" },
                                )
                            )
                        }
                    }
                    PendingKind.FLIP -> {
                        for (entry in batch.entries) {
                            val ref = entry.txn.ref
                            val existing = durable.decisions().forRef(ref)
                                .firstOrNull { it.kind == DecisionKind.DIRECTION }
                            val current = if (existing?.active == true) existing.value else entry.txn.direction
                            val flipped = if (current == "in") "out" else "in"
                            durable.decisions().put(
                                TxnDecision(
                                    id = existing?.id ?: uuid7(now),
                                    ref = ref,
                                    kind = DecisionKind.DIRECTION,
                                    value = flipped,
                                    createdAt = existing?.createdAt ?: now,
                                    updatedAt = now,
                                    deleted = false,
                                    memberId = session?.member.orEmpty(),
                                    familyRef = entry.txn.familyRef.ifBlank { "" },
                                )
                            )
                        }
                    }
                }
            }
            derive(durable, derived, extraLookup(store.extraBankNumbers))
            _state.update {
                it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit()), pendingUndo = null)
            }
            recomputeAccountBalancesFromLedger()
            requestFamilySync(silent = true)
        }.onFailure { android.util.Log.d("zheton", "batch ${batch.kind} failed: $it") }
    }

    /** Add a hand-entered cash or off-SMS transaction directly to durable storage. */
    fun addManualTransaction(
        amountRial: Long,
        isExpense: Boolean,
        categoryId: String?,
        merchant: String,
        note: String,
        epochMillis: Long = System.currentTimeMillis(),
        accountId: String = MANUAL_ACCOUNT_ID,
    ) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                recordManualTransaction(app, amountRial, isExpense, categoryId, merchant, note, epochMillis, accountId)
                val durable = DurableDb.get(app)
                val derived = DerivedDb.get(app)
                _state.update { it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit())) }
                // A row on one of her bank accounts moves that account's balance too.
                if (accountId != MANUAL_ACCOUNT_ID) recomputeAccountBalancesFromLedger()
                requestFamilySync(silent = true)
            }.onFailure { android.util.Log.d("zheton", "add manual transaction failed: $it") }
        }
    }

    /**
     * Permanent delete, with an undo window (spec 16).
     *
     * Nothing is touched until the window closes — not the durable source row, not the
     * decisions, not the tombstone. Undo cancels the batch; the same "deleted forever" guard is
     * the tombstone written only by the commit.
     */
    fun deletePermanently(entries: List<LedgerEntry>) {
        if (entries.isEmpty()) return
        scheduleBatch(
            PendingBatch(PendingKind.DELETE, entries, hidden = false),
            label = if (entries.size == 1) "این مورد حذف می‌شود" else "${entries.size} مورد حذف می‌شود",
        )
    }

    /** Cancel an in-flight batch before it becomes irreversible. */
    fun undoPermanentDelete() = undoPendingBatch()

    // ─────────────────────────── bulk actions ───────────────────────────

    /** Batch "Hide"/"Restore" hides the selected rows under the same undo door as delete (spec 16). */
    fun bulkHide(entries: List<LedgerEntry>, hidden: Boolean) {
        if (entries.isEmpty()) return
        scheduleBatch(
            PendingBatch(PendingKind.HIDE, entries, hidden),
            label = if (entries.size == 1)
                (if (hidden) "این مورد پنهان می‌شود" else "این مورد برمی‌گردد")
            else
                (if (hidden) "${entries.size} مورد پنهان می‌شود" else "${entries.size} مورد برمی‌گردد"),
        )
    }

    /** Batch "Flip direction" reverses every selected row once, under the same undo door. */
    fun bulkFlipDirection(entries: List<LedgerEntry>) {
        if (entries.isEmpty()) return
        scheduleBatch(
            PendingBatch(PendingKind.FLIP, entries, hidden = false),
            label = if (entries.size == 1) "جهت این مورد می‌چرخد" else "جهت ${entries.size} مورد می‌چرخد",
        )
    }

    /**
     * Batch "Delete" — a single undo window over every selected row (ticket 13). Uses the same
     * pending-delete mechanism as a one-at-a-time permanent delete.
     */
    fun bulkDelete(entries: List<LedgerEntry>) = deletePermanently(entries)

    // ─────────────────────────── category delete ───────────────────────────

    /**
     * Delete a category, deciding what happens to the transactions inside it.
     *
     * Empty categories leave with no prompt. A category holding transactions offers «انتقال
     * به…» (move every filing to a target category) — the only path where deleting cannot
     * orphan rows. Rules that filed into the dying category are retired with it.
     */
    fun deleteCategory(categoryId: String, targetId: String?) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val durable = DurableDb.get(app)
                val derived = DerivedDb.get(app)
                val now = System.currentTimeMillis()
                val category = durable.categories().get(categoryId) ?: return@runCatching
                if (category.builtin) return@runCatching
                if (targetId != null && targetId != categoryId) {
                    // Move everything currently filed under it. Both the pinned transactions and
                    // the ones a rule filed must land in the target, so every ref the derived
                    // ledger shows in the dying category gets a fresh category decision —
                    // rules are about to retire with the category, and a decision beats a rule.
                    val session = loadSession(durable)
                    val moves = derived.classes().all().filter { it.categoryId == categoryId }
                    durable.withTransaction {
                        for (row in moves) {
                            val ref = row.ref
                            val existing = durable.decisions().forRef(ref).firstOrNull { it.kind == DecisionKind.CATEGORY }
                            durable.decisions().put(
                                TxnDecision(
                                    id = existing?.id ?: uuid7(now),
                                    ref = ref,
                                    kind = DecisionKind.CATEGORY,
                                    value = targetId,
                                    createdAt = existing?.createdAt ?: now,
                                    updatedAt = now,
                                    deleted = false,
                                    memberId = session?.member.orEmpty(),
                                    familyRef = "",
                                )
                            )
                        }
                    }
                }
                // Rules that filed into the dying category retire with it — no rule may pin
                // rows to a category that no longer exists.
                durable.rules().deleteForCategory(categoryId, now)
                durable.categories().archive(categoryId, now)
                derive(durable, derived, extraLookup(store.extraBankNumbers))
                _state.update { it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit())) }
                requestFamilySync(silent = true)
            }.onFailure { android.util.Log.d("zheton", "deleteCategory failed: $it") }
        }
    }

    /** Where the household's ledger syncs. Same host as the PWA it serves, so one origin. */
    private val syncBase = BuildConfig.SYNC_URL

    private suspend fun refreshFamily(session: SyncSession?, note: String? = null, error: String? = null) {
        val durable = DurableDb.get(getApplication())
        val own = session?.let { active ->
            durable.familyMembers().get(active.member) ?: FamilyMember(
                id = active.member,
                name = store.name.trim().take(32).ifBlank { "من" },
                sharesSms = durable.meta().get(META_SYNC_SHARE_SMS).toBoolean(),
                updatedAt = System.currentTimeMillis(),
            ).also { durable.familyMembers().put(it) }
        }
        val members = if (session == null) emptyList() else durable.familyMembers().all()
        _state.update {
            it.copy(
                family = it.family.copy(
                    paired = session != null,
                    pendingPairing = if (session != null) null else it.family.pendingPairing,
                    memberId = session?.member.orEmpty(),
                    memberName = own?.name.orEmpty(),
                    members = members,
                    sharesSms = own?.sharesSms ?: false,
                    welcomeSeen = durable.meta().get(META_SYNC_WELCOME_SEEN) == "true",
                    mutedBanks = durable.meta().get(META_SYNC_MUTED_BANKS).orEmpty()
                        .split(',').filter(String::isNotBlank).toSet(),
                    // Households created before the owner stamp have none; their claimant keeps
                    // the role this way so the (only) management control still works for them.
                    ownerMemberId = durable.meta().get(META_SYNC_OWNER).orEmpty()
                        .ifBlank { session?.member.orEmpty() },
                    lastSync = note ?: it.family.lastSync,
                    error = error,
                    working = false,
                ),
            )
        }
    }

    /** Create a family with this phone's owner as its first member. */
    fun startFamily(name: String) {
        if (name.isBlank()) return
        val app = getApplication<Application>()
        _state.update { it.copy(family = it.family.copy(working = true, error = null)) }
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            runCatching { loadSession(durable) ?: claimHousehold(syncBase, durable, name) }
                .onSuccess {
                    refreshFamily(it, note = "خانواده ساخته شد.")
                    requestFamilySync(silent = true)
                }
                .onFailure {
                    android.util.Log.d("zheton", "claim failed: $it")
                    refreshFamily(null, error = "اتصال نشد. بعداً دوباره امتحان کن.")
                }
        }
    }

    /** Called from the Android deep link opened by a scanned family invitation. */
    fun acceptPairing(link: String?) {
        if (link.isNullOrBlank()) return
        if (_state.value.family.paired) {
            _state.update {
                it.copy(family = it.family.copy(error = "این گوشی از قبل عضو یک خانواده است."))
            }
            return
        }
        if (parsePairingLink(link) == null) {
            _state.update { it.copy(family = it.family.copy(error = "کد خانواده معتبر نیست.")) }
            return
        }
        _state.update {
            it.copy(
                family = it.family.copy(
                    pendingPairing = link,
                    error = null,
                    pairingUrl = null,
                )
            )
        }
    }

    fun joinFamily(name: String) {
        val link = _state.value.family.pendingPairing ?: return
        if (name.isBlank()) return
        val app = getApplication<Application>()
        _state.update { it.copy(family = it.family.copy(working = true, error = null)) }
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            loadSession(durable)?.let { existing ->
                refreshFamily(existing, error = "این گوشی از قبل عضو یک خانواده است.")
                return@launch
            }
            runCatching { joinHousehold(link, durable, name) }
                .onSuccess { session ->
                    _state.update { it.copy(family = it.family.copy(pendingPairing = null)) }
                    refreshFamily(session, note = "به خانواده پیوستی.")
                    requestFamilySync(silent = true)
                }
                .onFailure {
                    android.util.Log.d("zheton", "pair failed: $it")
                    _state.update {
                        it.copy(family = it.family.copy(working = false, error = "کد منقضی شده یا قبلاً استفاده شده."))
                    }
                }
        }
    }

    fun setFamilyName(name: String) {
        val clean = name.filterNot(Char::isISOControl).trim().take(32)
        if (clean.isBlank()) return
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val session = loadSession(durable) ?: return@launch
            val previous = durable.familyMembers().get(session.member)
            val now = maxOf(System.currentTimeMillis(), (previous?.updatedAt ?: 0L) + 1L)
            durable.familyMembers().put(
                (previous ?: FamilyMember(session.member, clean, updatedAt = now))
                    .copy(name = clean, updatedAt = now)
            )
            refreshFamily(session)
            requestFamilySync(silent = true)
        }
    }

    fun setFamilySmsSharing(enabled: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val session = loadSession(durable) ?: return@launch
            val previous = durable.familyMembers().get(session.member)
                ?: FamilyMember(session.member, "من", updatedAt = 0L)
            val now = maxOf(System.currentTimeMillis(), previous.updatedAt + 1L)
            durable.withTransaction {
                durable.meta().put(DurableMeta(META_SYNC_SHARE_SMS, enabled.toString()))
                durable.familyMembers().put(previous.copy(sharesSms = enabled, updatedAt = now))
            }
            refreshFamily(session)
            requestFamilySync(silent = false)
        }
    }

    /**
     * Per-bank publish scope (ticket 09): a muted bank's SMS transactions are withheld from the
     * family ledger. The local ledger is never touched; only the outgoing sync filter reads
     * this. Re-enabling publishes that bank's *future* transactions — a watermark records the
     * re-enable moment, so the rows that sat through the mute are never retroactively dug out.
     */
    fun setFamilyBankPublish(bank: String, publish: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val current = durable.meta().get(META_SYNC_MUTED_BANKS).orEmpty()
            val muted = current.split(',').filter { it.isNotBlank() }.toMutableSet().apply {
                if (publish) remove(bank) else add(bank)
            }
            durable.meta().put(
                DurableMeta(META_SYNC_MUTED_BANKS, muted.joinToString(","))
            )
            if (publish) {
                // The re-enable watermark: anything at-or-older than the switch stays put.
                val wm = durable.meta().get(META_SYNC_BANK_WATERMARK).orEmpty()
                val parsed = wm.split(',').filter { it.isNotBlank() }.toMutableList()
                val now = System.currentTimeMillis()
                parsed.removeAll { it.startsWith("$bank:") }
                parsed += "$bank:$now"
                durable.meta().put(DurableMeta(META_SYNC_BANK_WATERMARK, parsed.joinToString(",")))
            }
            refreshFamily(loadSession(durable))
            requestFamilySync(silent = false)
        }
    }

    /** Acknowledge the family tab's first-open explainer, once, durably. */
    fun ackFamilyWelcome() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            durable.meta().put(DurableMeta(META_SYNC_WELCOME_SEEN, "true"))
            refreshFamily(loadSession(durable))
        }
    }

    /**
     * Remove a member from the household (ticket 10). The owner only, never themselves, and it
     * is a membership act, not a deletion: rows they already shared stay in the ledger — this
     * just stops whatever arrives from them next, and stops their name appearing on this screen.
     * The revoke is stored locally, so a later re-pairing by them would need a fresh claim.
     */
    fun removeFamilyMember(memberId: String) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val session = loadSession(durable) ?: run {
                refreshFamily(null, error = "هنوز به خانواده‌ای وصل نیستی.")
                return@launch
            }
            // Households created before the owner stamp existed have no owner recorded; their
            // phones already believed they were the one in charge, so they keep that belief.
            val owner = durable.meta().get(META_SYNC_OWNER).orEmpty().ifBlank { session.member }
            if (owner != session.member) return@launch refreshFamily(
                session, error = "فقط صاحب خانواده می‌تونه عضو را حذف کنه."
            )
            if (memberId == session.member) return@launch refreshFamily(
                session, error = "نمی‌تونی خودت رو حذف کنی."
            )
            val member = durable.familyMembers().get(memberId) ?: return@launch
            val now = maxOf(System.currentTimeMillis(), member.updatedAt + 1L)
            runCatching {
                durable.withTransaction {
                    durable.familyMembers().put(member.copy(updatedAt = now, deleted = true))
                    val current = durable.meta().get(META_SYNC_REMOVED_MEMBERS).orEmpty()
                    val set = current.split(',').filter(String::isNotBlank).toMutableSet() + memberId
                    durable.meta().put(DurableMeta(META_SYNC_REMOVED_MEMBERS, set.joinToString(",")))
                }
            }.onFailure { android.util.Log.d("zheton", "remove member failed: $it") }
            refreshFamily(session)
            requestFamilySync(silent = true)
        }
    }

    /** A one-time code, shown as a QR. Ten minutes, one use. */
    fun inviteDevice() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val session = loadSession(durable) ?: return@launch refreshFamily(null)
            runCatching { pairingUrl(session, invite(session, durable)) }
                .onSuccess { url -> _state.update { it.copy(family = it.family.copy(paired = true, pairingUrl = url, error = null)) } }
                .onFailure {
                    android.util.Log.d("zheton", "invite failed: $it")
                    refreshFamily(session, error = "کد ساخته نشد. اینترنتت رو چک کن.")
                }
        }
    }

    fun syncFamily() = requestFamilySync(silent = false)

    /**
     * Family sync is dormant: the sync store, worker, and PWA remain in the repo unshipped.
     * This no-op preserves call sites without breaking anything.
     */
    private fun requestFamilySync(silent: Boolean) { /* dormant — no-op */ }

    fun addGoal(name: String, targetRial: Long, kind: String = GoalKind.SAVE, categoryId: String? = null, scopeMerchantNorm: String? = null) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val now = System.currentTimeMillis()
            val period = if (kind == GoalKind.CAP) GoalPeriod.MONTH else GoalPeriod.ONCE
            runCatching {
                durable.goals().put(
                    Goal(
                        id = uuid7(now),
                        nameFa = name.take(40),
                        targetRial = targetRial,
                        kind = kind,
                        categoryId = categoryId,
                        scopeMerchantNorm = scopeMerchantNorm,
                        period = period,
                        startsOn = jalaliMonthStart(tehranDay(now)),
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                _state.update { it.copy(ledger = ledgerView(DerivedDb.get(app), durable, limit = ledgerLimit())) }
            }.onFailure { android.util.Log.d("zheton", "addGoal failed: $it") }
        }
    }

    fun deleteGoal(id: String) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            runCatching {
                durable.goals().delete(id, System.currentTimeMillis())
                _state.update { it.copy(ledger = ledgerView(DerivedDb.get(app), durable, limit = ledgerLimit())) }
            }.onFailure { android.util.Log.d("zheton", "deleteGoal failed: $it") }
        }
    }

    // ──────────────────────── transaction notes ────────────────────────

    /** Get the note for a transaction from the decisions table. */
    fun getNote(ref: String): String {
        return _state.value.ledger.notes[ref].orEmpty()
    }

    /** Save a note for a transaction. Clearing the field tombstones the old one. */
    fun saveNote(entry: LedgerEntry, note: String) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val derived = DerivedDb.get(app)
            runCatching {
                val now = System.currentTimeMillis()
                val existing = durable.decisions().forRef(entry.txn.ref)
                    .firstOrNull { it.kind == DecisionKind.NOTE }
                if (note.isBlank()) {
                    // Clearing a note can't delete the durable row — a tombstone keeps the
                    // late-known existence of the decision so a re-derive never resurrects it.
                    if (existing != null) {
                        durable.decisions().put(existing.copy(deleted = true, updatedAt = now))
                    }
                } else {
                    val id = existing?.id ?: uuid7(now)
                    durable.decisions().put(
                        TxnDecision(
                            id = id,
                            ref = entry.txn.ref,
                            kind = DecisionKind.NOTE,
                            value = note,
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now,
                            deleted = false,
                        )
                    )
                }
                // Update the notes map in state so getNote returns the new value immediately.
                _state.update {
                    it.copy(ledger = it.ledger.copy(
                        notes = if (note.isBlank()) it.ledger.notes - entry.txn.ref
                        else it.ledger.notes + (entry.txn.ref to note),
                    ))
                }
                _state.update { it.copy(ledger = ledgerView(derived, durable, limit = ledgerLimit())) }
            }.onFailure { android.util.Log.d("zheton", "saveNote failed: $it") }
        }
    }

    // ──────────────────────── category management ────────────────────────

    /** Create a new user-defined category. */
    fun addCategory(nameFa: String, kind: String) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val now = System.currentTimeMillis()
            val maxSort = durable.categories().all().maxOfOrNull { it.sort } ?: 0
            val id = "cat_user_${uuid7(now)}"
            runCatching {
                durable.categories().put(
                    Category(
                        id = id,
                        nameFa = nameFa.trim().take(30),
                        kind = kind,
                        sort = maxSort + 10,
                        builtin = false,
                        updatedAt = now,
                    )
                )
                _state.update { it.copy(ledger = ledgerView(DerivedDb.get(app), durable, limit = ledgerLimit())) }
            }.onFailure { android.util.Log.d("zheton", "addCategory failed: $it") }
        }
    }

    /** Rename a user-created category. Builtin categories cannot be renamed. */
    fun editCategory(id: String, newNameFa: String) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val now = System.currentTimeMillis()
            runCatching {
                val existing = durable.categories().get(id) ?: return@runCatching
                if (existing.builtin) return@runCatching
                durable.categories().put(
                    existing.copy(nameFa = newNameFa.trim().take(30), updatedAt = now)
                )
                _state.update { it.copy(ledger = ledgerView(DerivedDb.get(app), durable, limit = ledgerLimit())) }
            }.onFailure { android.util.Log.d("zheton", "editCategory failed: $it") }
        }
    }

    // ──────────────────────── installment obligations ────────────────────────

    /** The entry currently being marked as a credit purchase, or null. */
    var markingCredit: LedgerEntry? by mutableStateOf(null)
        private set

    /** The entry currently being linked to an installment, or null. */
    var linkingInstallment: LedgerEntry? by mutableStateOf(null)
        private set

    fun startMarkCredit(entry: LedgerEntry) { markingCredit = entry }
    fun cancelMarkCredit() { markingCredit = null }

    fun startLinkInstallment(entry: LedgerEntry) { linkingInstallment = entry }
    fun cancelLinkInstallment() { linkingInstallment = null }

    /**
     * Save a new installment obligation and its payment schedule.
     * If [linkedTxnRef] is non-empty, the obligation is linked to that transaction.
     */
    fun saveObligation(
        provider: String,
        totalRial: Long,
        downPaymentRial: Long,
        installmentCount: Int,
        installmentRial: Long,
        startDate: Long,
        notes: String,
        linkedTxnRef: String,
    ) {
        android.util.Log.i("muchtoman", "saveObligation: provider=$provider, total=$totalRial, installments=$installmentCount, amount=$installmentRial")
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val now = System.currentTimeMillis()
            val id = uuid7(now)
            val (obligation, payments) = buildObligation(
                id, provider, totalRial, downPaymentRial, installmentCount,
                installmentRial, startDate, notes, linkedTxnRef, now,
            )
            runCatching {
                durable.obligations().put(obligation)
                durable.installmentPayments().putAll(payments)
                refreshObligations(durable)
                android.util.Log.i("muchtoman", "saveObligation: saved successfully, id=$id")
            }.onFailure { android.util.Log.d("zheton", "saveObligation failed: $it") }
        }
    }

    /** Mark an installment as paid, optionally linking it to a bank transaction. */
    fun markInstallmentPaid(paymentId: String, txnRef: String = "") {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val now = System.currentTimeMillis()
            runCatching {
                durable.installmentPayments().markPaid(paymentId, now, txnRef)
                refreshObligations(durable)
            }.onFailure { android.util.Log.d("zheton", "markInstallmentPaid failed: $it") }
        }
        viewModelScope.launch { ReminderScheduler.reschedule(getApplication()) }
    }

    /** Pay off all remaining installments of an obligation at once. */
    fun payOffRemaining(obligationId: String) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val now = System.currentTimeMillis()
            runCatching {
                durable.installmentPayments().markAllPaid(obligationId, now)
                refreshObligations(durable)
            }.onFailure { android.util.Log.d("zheton", "payOffRemaining failed: $it") }
        }
        viewModelScope.launch { ReminderScheduler.reschedule(getApplication()) }
    }

    /** Archive a completed obligation. */
    fun archiveObligation(obligationId: String) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val now = System.currentTimeMillis()
            runCatching {
                durable.obligations().archive(obligationId, now)
                refreshObligations(durable)
            }.onFailure { android.util.Log.d("zheton", "archiveObligation failed: $it") }
        }
    }

    private suspend fun refreshObligations(durable: DurableDb) = ledgerMutex.withLock {
        val obligations = durable.obligations().active()
        val payments = mutableMapOf<String, List<InstallmentPayment>>()
        for (ob in obligations) {
            payments[ob.id] = durable.installmentPayments().forObligation(ob.id)
        }
        val reservations = durable.reservations().active()
        _state.update { it.copy(obligations = obligations, obligationPayments = payments, reservations = reservations) }
    }

    // ──────────────────────── commitments (BNPL, Loans, Checks) ────────────────────────

    fun saveCommitment(commitment: Commitment) {
        val updated = (store.commitments.filterNot { it.id == commitment.id } + commitment)
        store.commitments = updated
        _state.update { it.copy(commitments = updated) }
        viewModelScope.launch { ReminderScheduler.reschedule(getApplication()) }
    }

    fun toggleCommitmentSettled(commitmentId: String) {
        val current = store.commitments
        val target = current.firstOrNull { it.id == commitmentId } ?: return
        val updated = current.map {
            if (it.id == commitmentId) it.copy(
                isSettled = !it.isSettled,
                paidInstallmentCount = if (!it.isSettled) it.installmentCount else 0,
                remainingAmountRial = if (!it.isSettled) 0L else it.totalAmountRial,
            ) else it
        }
        store.commitments = updated
        _state.update { it.copy(commitments = updated) }
        viewModelScope.launch { ReminderScheduler.reschedule(getApplication()) }
    }

    fun deleteCommitment(commitmentId: String) {
        val updated = store.commitments.filterNot { it.id == commitmentId }
        store.commitments = updated
        _state.update { it.copy(commitments = updated) }
        viewModelScope.launch { ReminderScheduler.reschedule(getApplication()) }
    }

    // ──────────────────────── fund reservations ────────────────────────

    fun saveReservation(purpose: String, amountRial: Long, categoryId: String?) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val now = System.currentTimeMillis()
            val id = uuid7(now)
            runCatching {
                durable.reservations().put(
                    Reservation(id, purpose, amountRial, categoryId, now, now)
                )
                refreshObligations(durable)
            }.onFailure { android.util.Log.d("zheton", "saveReservation failed: $it") }
        }
    }

    fun releaseReservation(id: String) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            val now = System.currentTimeMillis()
            runCatching {
                durable.reservations().release(id, now)
                refreshObligations(durable)
            }.onFailure { android.util.Log.d("zheton", "releaseReservation failed: $it") }
        }
    }

    fun deleteReservation(id: String) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            val durable = DurableDb.get(app)
            runCatching {
                durable.reservations().delete(id)
                refreshObligations(durable)
            }.onFailure { android.util.Log.d("zheton", "deleteReservation failed: $it") }
        }
    }

    // ──────────────────────── category suggestion ────────────────────────

    /** Quick-add's pre-selected category, its row and reason line, from the ledger in memory. */
    fun suggestCategory(
        amountRial: Long?,
        accountId: String?,
        at: Long = System.currentTimeMillis(),
        merchant: String? = null,
        note: String? = null,
        direction: String = "out",
    ): CategorySuggestion {
        val s = _state.value
        return com.doxigo.muchtoman.suggestCategory(
            entries = s.ledger.allEntries,
            categories = s.ledger.categories,
            amountRial = amountRial,
            accountId = accountId,
            at = at,
            merchant = merchant,
            note = note,
            notes = s.ledger.notes,
            direction = direction,
            hidden = s.hiddenCategories,
        )
    }

    // ──────────────────────── CSV export ────────────────────────

    /** Export ledger entries to CSV and share via Android intent. */
    fun exportCsv(context: Context) {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val ledger = _state.value.ledger
                val entries = ledger.allEntries.filterNot { it.duplicate || it.hidden }
                if (entries.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "تراکنشی برای خروجی گرفتن وجود ندارد", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                val csv = generateLedgerCsv(entries, ledger.notes)
                val file = File(context.cacheDir, "zheton_ledger.csv")
                file.writeText(csv, Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = android.content.ClipData.newRawUri("CSV Export", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "خروجی اکسل و CSV").apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                withContext(Dispatchers.Main) {
                    context.startActivity(chooser)
                }
            }.onFailure { e ->
                android.util.Log.e("muchtoman", "exportCsv failed: $e", e)
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "خطا در تهیه خروجی CSV", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** The message a transaction was read from, so every figure can be traced back to its source. */
    suspend fun sourceOf(entry: LedgerEntry): String =
        runCatching {
            DurableDb.get(getApplication()).smsSource().bodyOf(entry.txn.srcHash).orEmpty()
        }.getOrDefault("")

/** The scan itself. Separate so [restartScan] can run it in the coroutine it already owns. */
    private suspend fun runScan(app: Application) {
        val extra = extraLookup(store.extraBankNumbers)

        val messages = readSmsInbox(app, store.smsScannedTo)
        if (messages.isEmpty()) return

        // Corrections stick on this path too (spec 16): the fold is an input consumer of the
        // same durable facts the ledger reads — a message she permanently deleted, or one she
        // hid, must not re-enter the accounts even if the inbox serves the same bytes again.
        // One skip set, derived before the loop, queried once per message.
        val durable = DurableDb.get(app)
        val foldSkips = sourceSkips(hiddenRefs(durable), deletedSourceHashes(durable.meta().get(PERMANENTLY_DELETED)))

        // Re-keyed through senderKey on the way in: the key function has changed once
        // already (whitespace collapse), and a dismissal stored under an old key must
        // stay dismissed. senderKey is a fixpoint over its own output.
        val dismissed = store.dismissedSenders.map(::senderKey).toSet()
        var accounts = store.bankAccounts
        val seen = store.seenSms
        val fresh = mutableListOf<String>()
        val strangers = store.strangeSenders.toMutableList()
        for (m in messages) {
            val key = smsKey(m.from, m.body, m.at)
            if (key in seen || legacySmsKey(m.body, m.at) in seen || key in fresh) continue
            // Dealt with, permanently: mark it seen so an identical future re-read stays silent.
            if (foldSkips.isNotEmpty() && srcHash(m.from, m.body, m.at) in foldSkips) {
                fresh += key
                continue
            }
            val parsed = parseBankSms(m.from, m.body, m.at, extra)
            // A Maskan purchase-initiation message (خرید + مبلغ + رمز) must not move the fold:
            // it is the authorization half of a pair and would double-count the movement that
            // the debit confirmation that follows already books. parseBankSms keeps reading it
            // so the ledger can harvest its merchant; here it is simply skipped.
            if (isMaskanHint(m.body)) continue
            if (parsed == null) {
                // Skipped, and normally that is the end of it. The one silence worth
                // breaking: a message that names one of HER banks from a number the list
                // lacks — that is how a bank that adds a shortcode "freezes". It becomes a
                // one-tap suggestion in the sheet, and her tap is what adds the number.
                val from = m.from.trim()
                if (
                    from.isNotEmpty() &&
                    senderKey(from) !in dismissed &&
                    !isIgnoredBankSms(from, m.body) &&
                    looksLikeBankSms(m.body)
                ) {
                    guessBank(m.body)?.let { g ->
                        // Replace rather than skip. Messages arrive oldest first, so
                        // keeping the first sighting pinned every active sender to its
                        // earliest date — and the sort below then buried the sender she is
                        // actually asking about underneath a dozen one-off promos.
                        strangers.removeAll { senderKey(it.sender) == senderKey(from) }
                        strangers += StrangeSender(
                            sender = from,
                            bank = g.name,
                            snippet = snippetOf(m.body),
                            at = m.at,
                        )
                    }
                }
                continue
            }
            accounts = applyBankSms(accounts, parsed)
            fresh += key
        }

        if (!store.smsEnabled || !canReadSms(app)) return

        store.bankAccounts = accounts
        store.seenSms = rememberSeen(seen, fresh)
        // A stranger whose number has since been added resolves and drops off the list, as
        // does one she dismissed. Newest first, so the message she is asking about — which
        // is almost always the latest — sits at the top instead of behind old promos.
        val currentDismissed = store.dismissedSenders.map(::senderKey).toSet()
        store.strangeSenders = strangers
            .filter { bankOf(it.sender, extra) == null && senderKey(it.sender) !in currentDismissed }
            .sortedByDescending { it.at }
            .take(12)
        _state.update {
            it.copy(bankAccounts = accounts, strangeSenders = store.strangeSenders)
        }
        // Advanced past everything we looked at, parsed or not — an advert from a bank is
        // not worth re-reading on every launch. Never past now, though: one inbox row
        // stamped in 2030 by a restored backup or a skewed carrier clock would otherwise
        // put the watermark there and silently freeze every balance for ever after.
        store.smsScannedTo = minOf(messages.maxOf { it.at }, System.currentTimeMillis())
        _state.update { it.copy(bankAccounts = accounts) }
        recordSnapshot()
    }

    /**
     * Forget every balance and read the inbox again from the beginning.
     *
     * Messages are read once and then skipped for ever, by a watermark and a seen-list — which
     * is right until the reading itself changes, and then every balance is frozen at what an
     * older parser made of it with no way back. An upgrade clears it automatically; this is the
     * same thing on demand, so a figure that has gone wrong is never a dead end.
     */
    fun rescanSms() = restartScan {
        store.bankAccounts = emptyList()
        store.seenSms = emptySet()
        store.smsScannedTo = 0L
        store.strangeSenders = emptyList()
        _state.update { it.copy(bankAccounts = emptyList(), strangeSenders = emptyList()) }
    }

    /**
     * Change what the next scan would read, then read it — with the scan already in flight
     * stopped and waited for first.
     *
     * cancelAndJoin, not cancel: cancellation is cooperative and the parse loop never suspends,
     * so only joining actually guarantees that [prepare] is not overwritten by a scan that was
     * already most of the way through.
     *
     * The restart takes over [scanJob] straight away, before it suspends into that join. A job
     * reads as not-active the whole time it is cancelling, so leaving the old one there meant
     * any scanSms() arriving during the join — one foreground is enough — walked past the
     * one-at-a-time guard, started a scan with the old watermark and claimed the slot. Then
     * [prepare] ran, and our own scanSms() was the one turned away: the wipe landed with no
     * rescan behind it, and every balance she had anchored by hand was gone for good.
     *
     * NonCancellable around the join for the same reason one step up: the next restart cancels
     * this coroutine, and a join that gives up early lets the old scan wake and write over the
     * wipe — exactly what the join is here to prevent.
     */
    private fun restartScan(prepare: () -> Unit) {
        val previous = scanJob
        val app = getApplication<Application>()
        scanJob = viewModelScope.launch(Dispatchers.Default) {
            withContext(NonCancellable) { previous?.cancelAndJoin() }
            prepare()
            // Straight into the scan rather than back through scanSms(), which would only
            // find this very coroutine holding the slot and decline.
            if (store.smsEnabled && canReadSms(app)) runScan(app)
        }
    }

    /** She waved a suggestion away. It stays away — across rescans and upgrades too. */
    fun dismissSender(sender: String) {
        store.dismissedSenders = store.dismissedSenders + senderKey(sender)
        store.strangeSenders =
            store.strangeSenders.filterNot { senderKey(it.sender) == senderKey(sender) }
        _state.update { it.copy(strangeSenders = store.strangeSenders) }
    }

    /**
     * She confirmed a stranger: this number is one of her banks. It joins that bank's list on
     * this phone only, and the inbox is read again from the start so the messages it already
     * sent finally count.
     *
     * Winding the watermark back, not [rescanSms]. Confirming a sender is one tap on a
     * suggestion card, and it used to forget every balance in the app — including the ones she
     * typed in herself. Those cannot be rebuilt: a bank whose messages never state a مانده
     * comes back as the sum of its transactions, unanchored, which is exactly the figure that
     * does not count towards the total. Her net worth would drop by whatever she had anchored,
     * silently, with nothing storing the number she had entered.
     *
     * Nothing needs forgetting here anyway. A message from an unknown sender is skipped before
     * it is ever recorded as seen, so the only thing hiding these particular messages is how
     * far the inbox has been read; everything already counted is still in seenSms and stays
     * skipped.
     */
    fun addBankNumber(bank: String, sender: String) {
        if (runCatching { Bank.valueOf(bank) }.getOrNull() == null || sender.isBlank()) return
        val cur = store.extraBankNumbers
        store.extraBankNumbers = cur + (bank to ((cur[bank] ?: emptyList()) + sender).distinct())
        restartScan { store.smsScannedTo = 0L }
    }

    /** Turning it off leaves the balances where they are; it stops them moving on their own. */
    fun setSmsEnabled(on: Boolean) {
        store.smsEnabled = on
        _state.update { it.copy(smsEnabled = on) }
        if (on) scanSms()
    }

    /** A bank she switched off stays tracked and stays listed — it just leaves the total. */
    fun setBankEnabled(bank: String, on: Boolean) {
        val next = if (on) store.disabledBanks - bank else store.disabledBanks + bank
        store.disabledBanks = next
        _state.update { it.copy(disabledBanks = next) }
        recordSnapshot()
    }

/**
     * Hide or restore a built-in category in every picker and filter. The row itself stays —
     * builtins are structural — only the offering leaves. Existing transactions keep the name.
     */
    fun setCategoryHidden(id: String, hidden: Boolean) {
        val next = if (hidden) store.hiddenCategories + id else store.hiddenCategories - id
        store.hiddenCategories = next
        _state.update { it.copy(hiddenCategories = next) }
    }

    /** Show or hide the struck-through rows the timeline keeps for her own deletable mistakes. */
    fun setShowHidden(on: Boolean) {
        store.showHidden = on
        _state.update { it.copy(showHidden = on) }
    }

    /** She tells us what an account really holds; everything read after it builds on that. */
    fun setBankBalance(key: String, balance: Double) {
        restartScan {
            val next = anchorAccount(store.bankAccounts, key, balance, System.currentTimeMillis())
            store.bankAccounts = next
            _state.update { it.copy(bankAccounts = next) }
            recordSnapshot()
        }
    }

    /**
     * Drops an account that was read wrong. The messages behind it stay marked as seen on
     * purpose: re-reading them would rebuild the same wrong figure. It starts again, from
     * zero and unanchored, at the next message that bank sends.
     */
    fun forgetBankAccount(key: String) {
        restartScan {
            val next = store.bankAccounts.filterNot { it.key == key }
            store.bankAccounts = next
            _state.update { it.copy(bankAccounts = next) }
            recordSnapshot()
        }
    }

    fun updateAccountDetails(
        key: String,
        nickname: String,
        fullPan: String,
        expiry: String = "",
        theme: String? = null,
        sheba: String? = null,
    ) {
        val next = store.bankAccounts.map { acct ->
            if (acct.key == key) {
                val base = acct.copy(
                    nickname = nickname.trim(),
                    fullPan = fullPan.trim(),
                    expiry = expiry.trim(),
                )
                // SHEBA, like theme, is optional: null = caller never offered the field,
                // empty = explicit clear. Canonical form strips spacing and casing.
                val withSheba = if (sheba == null) base else base.copy(sheba = com.doxigo.muchtoman.domain.shetab.ShetabKit.cleanSheba(sheba))
                // Null = the editor was not given a theme row at all, so whatever is
                // stored stays; empty string = she picked «رنگ خود بانک», clearing it.
                if (theme == null) withSheba else withSheba.copy(theme = theme.trim())
            } else acct
        }
        store.bankAccounts = next
        _state.update { it.copy(bankAccounts = next) }
    }

    /**
     * Recompute the persisted fold from the corrected ledger.
     *
     * Every manual correction (direction flip, hide, delete, merchant) must land in the Home
     * total and the per-bank sheet, not just in the timeline. Corrections write a durable
     * decision and re-derive the ledger; this folds the derived balances back over the
     * persisted accounts, adopting the corrected figure when it is at least as new as the
     * fold's and leaving a newer hand-typed balance alone (see [reconcileBalances]).
     */
    fun recomputeAccountBalancesFromLedger() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val durable = DurableDb.get(app)
                val derived = DerivedDb.get(app)
                val next = reconcileBalances(store.bankAccounts, allBalances(derived, durable))
                store.bankAccounts = next
                _state.update { it.copy(bankAccounts = next) }
                recordSnapshot()
            }.onFailure { android.util.Log.d("zheton", "recompute balances failed: $it") }
        }
    }

    fun setOverride(typeId: String, rate: Double?) {
        val next = _state.value.overrides.toMutableMap()
        if (rate == null) next -= typeId else next[typeId] = rate
        store.overrides = next
        _state.update { it.copy(overrides = next) }
        recordSnapshot()
    }

    // ── Holding lots and daily rates (ticket #10; API in HoldingPnl.kt) ─────────────

    /**
     * ثبت خرید: [amount] units of [typeId] at [unitPriceToman] each, on [atMs]. Adds to the
     * holding [key] (creating it if she has none) and records the lot its cost comes from.
     * A wallet-tracked holding keeps the wallet's amount; only the lot is recorded.
     * False when the numbers are not positive and finite.
     */
    fun recordBuy(
        key: String,
        typeId: String,
        amount: Double,
        unitPriceToman: Double,
        atMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!isValidLot(amount, unitPriceToman)) return false
        val existing = _state.value.holdings.firstOrNull { it.key == key }
        when {
            existing == null -> saveHolding(key, typeId, amount, wallet = null)
            existing.wallet == null -> saveHolding(key, existing.typeId, existing.amount + amount, wallet = null)
        }
        addLot(key, HoldingLot(newHoldingId(), LotSide.BUY, amount, unitPriceToman, atMs))
        return true
    }

    /**
     * ثبت فروش: [amount] units of the holding [key] sold at [unitPriceToman] each. Takes the
     * units off the holding (never below zero; the row stays) and records the lot, which books
     * realised profit at the average cost. False for an unknown holding, bad numbers, or a
     * sale larger than what she holds.
     */
    fun recordSell(
        key: String,
        amount: Double,
        unitPriceToman: Double,
        atMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!isValidLot(amount, unitPriceToman)) return false
        val existing = _state.value.holdings.firstOrNull { it.key == key } ?: return false
        if (amount > existing.amount * (1 + 1e-9) + 1e-12) return false
        if (existing.wallet == null) {
            saveHolding(key, existing.typeId, (existing.amount - amount).coerceAtLeast(0.0), wallet = null)
        }
        addLot(key, HoldingLot(newHoldingId(), LotSide.SELL, amount, unitPriceToman, atMs))
        return true
    }

    /**
     * Gives the units no lot accounts for — every holding from before lots existed — the price
     * she paid, without changing the amount. Turns the dash into a real cost basis.
     */
    fun recordOpeningCost(key: String, unitPriceToman: Double, atMs: Long = System.currentTimeMillis()): Boolean {
        val holding = _state.value.holdings.firstOrNull { it.key == key } ?: return false
        val uncovered = positionOf(holding, _state.value.lotsOf(key), null).uncoveredAmount
        if (!isValidLot(uncovered, unitPriceToman)) return false
        addLot(key, HoldingLot(newHoldingId(), LotSide.BUY, uncovered, unitPriceToman, atMs))
        return true
    }

    /** Removes one recorded lot (a mistyped entry). The holding's amount is left alone. */
    fun deleteLot(key: String, lotId: String) {
        val lots = _state.value.holdingLots[key] ?: return
        val next = lots.filterNot { it.id == lotId }
        persistLots(if (next.isEmpty()) _state.value.holdingLots - key else _state.value.holdingLots + (key to next))
    }

    private fun addLot(key: String, lot: HoldingLot) {
        val all = _state.value.holdingLots
        persistLots(all + (key to (all[key].orEmpty() + lot)))
    }

    private fun persistLots(lots: Map<String, List<HoldingLot>>) {
        store.holdingLots = lots
        _state.update { it.copy(holdingLots = lots) }
    }

    /** Reads the stored closes off the main thread, importing the old intraday store once. */
    private fun loadDailyRates() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = runCatching {
                store.dailyRates.seedOnce(store.rateHistoryStore.allHistory())
                store.dailyRates.loadAll()
            }.getOrDefault(emptyMap())
            // Anything recorded while this ran is newer than the files read; it wins.
            _state.update { it.copy(dailyRates = loaded + it.dailyRates) }
        }
    }

    private suspend fun saveDailyRates(rates: Map<String, Double>, updatedAt: Long) {
        val holdings = _state.value.holdings
        val changed = withContext(Dispatchers.IO) {
            persistDailyRates(store.dailyRates, rates, updatedAt, holdings)
        }
        if (changed.isNotEmpty()) _state.update { it.copy(dailyRates = it.dailyRates + changed) }
    }

    private fun persist(list: List<Holding>) {
        store.holdings = list
        _state.update { it.copy(holdings = list) }
        recordSnapshot()
    }

    /**
     * Remembers today's total for the report chart, via the same [snapshotHistory] the daily
     * worker uses — and since this runs on every path that touches money, it is also the
     * moment the home-screen widget learns the new total.
     */
    private fun recordSnapshot() {
        val s = _state.value
        // listHoldings, not holdings: someone whose only money is bank balances read from her
        // messages has an empty holdings list and a perfectly good total, and guarding on the
        // raw list left her report saying "هنوز نموداری نیست" for ever.
        snapshotHistory(
            s.history, s.listHoldings, s.effective, s.rates.updatedAt,
            System.currentTimeMillis(),
        )?.let { next ->
            store.history = next
            _state.update { it.copy(history = next) }
        }
        updateTotalWidget(getApplication())
    }
}

data class UiState(
    val onboardingCompleted: Boolean = false,
    val holdings: List<Holding> = emptyList(),
    val rates: Rates = Rates(),
    val overrides: Map<String, Double> = emptyMap(),
    val loading: Boolean = false,
    val stocksLoading: Boolean = false,
    val error: String? = null,
    val lockEnabled: Boolean = false,
    val widgetLock: Boolean = false,
    val locked: Boolean = false,
    val name: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val history: Map<Long, Double> = emptyMap(),
    val smsEnabled: Boolean = false,
    val bankAccounts: List<BankAccount> = emptyList(),
    val disabledBanks: Set<String> = emptySet(),
    val hiddenCategories: Set<String> = emptySet(),
    val showHidden: Boolean = false,
    /** When a structured filter (ticket 04+) is active, the ledger must not stop at the newest
     * window: widen it so the filter can match and count old transactions too. */
    val filterWide: Boolean = false,
    /** A pending permanent delete waiting out its undo window: label + size + when it fires. */
    val pendingUndo: PendingUndo? = null,
    val strangeSenders: List<StrangeSender> = emptyList(),
    val refreshingWallets: Set<String> = emptySet(),
    val walletErrors: Map<String, String> = emptyMap(),
    val dismissedUpdate: String = "",
    val tse: TseSnapshot = TseSnapshot(),
    val ledger: LedgerView = LedgerView(),
    val family: FamilyState = FamilyState(),
    val obligations: List<InstallmentObligation> = emptyList(),
    val obligationPayments: Map<String, List<InstallmentPayment>> = emptyMap(),
    val reservations: List<Reservation> = emptyList(),
    val commitments: List<Commitment> = emptyList(),
    val rateHistoryStore: MarketRateHistoryStore = MarketRateHistoryStore(),
    val hourlyWageToman: Long = 0L,
    val cycleMode: CycleMode = CycleMode.JALALI_MONTH,
    val cycleAnchorDay: Int = 1,
    /** User-set monthly budget in Toman; 0 = unset. */
    val monthlyBudgetToman: Long = 0L,
    val widgetSlots: List<com.doxigo.muchtoman.widget.engine.WidgetSlot> = com.doxigo.muchtoman.widget.engine.WidgetEngineDefaults.defaultSlots,
    /** ثبت خرید / ثبت فروش per holding key; see HoldingPnl.kt for what is computed from it. */
    val holdingLots: Map<String, List<HoldingLot>> = emptyMap(),
    /** Stored daily closes per asset id, from rates the app actually fetched. */
    val dailyRates: Map<String, DailyCloses> = emptyMap(),
) {
    val coins: List<Coin> get() = rates.coins

    /**
     * What the home screen says, worked out once per state rather than once per card.
     *
     * `by lazy` for the same reason `effective` is: the month walk and the ninety-day median
     * behind the runway are not free, and the summary reads them several times over.
     */
    val story: HomeStory by lazy {
        buildStory(ledger.entries, Math.round(bankToman * 10.0), tehranDay(System.currentTimeMillis()), CycleSpec(cycleMode, cycleAnchorDay))
    }
    val stocks: List<Stock> get() = tse.stocks

    /** Budget, spent, plan-to-date and today's share for the current cycle; see [monthlyBudget]. */
    val budget: MonthlyBudget by lazy {
        monthlyBudget(
            ledger.allEntries.ifEmpty { ledger.entries },
            monthlyBudgetToman,
            tehranDay(System.currentTimeMillis()),
            CycleSpec(cycleMode, cycleAnchorDay),
        )
    }

    /**
     * Update check permanently disabled for this fork.
     */
    val update: Release? get() = null
    /**
     * by lazy, not get(): building this copies the whole rate map three times over, and with
     * بورس loaded that map is the Worker's several hundred plus a couple of thousand نماد.
     * The screen reads it — directly, and through [totals] — the better part of ten times in
     * one composition pass, so as a get() a scrolling list rebuilt it on every frame and the
     * رمزارز rows stuttered. A UiState is immutable and copy() makes a new one, so lazy is
     * still exactly one build per state, and the value can never be stale.
     */
    val effective: Map<String, Double> by lazy { effectiveRates(rates, overrides, tse) }
    val refreshing: Boolean get() = loading || stocksLoading || refreshingWallets.isNotEmpty()

    /** The tracked balances as one figure, minus the banks she switched off. */
    val bankToman: Double get() = bankTotal(bankAccounts, disabledBanks)

    /** Per account, when its last bank SMS was read and "… پیش" for it (Accounts screen). */
    fun smsFreshness(now: Long = System.currentTimeMillis()): List<SmsFreshness> =
        com.doxigo.muchtoman.smsFreshness(bankAccounts, ledger.allEntries + ledger.hiddenEntries, now)

    /** 30 days of deposits/withdrawals across every account she has not switched off. */
    val accountsFlow: AccountFlow by lazy {
        com.doxigo.muchtoman.accountFlow(
            ledger.allEntries,
            bankAccounts.filter { it.bank !in disabledBanks }.map { it.key }.toSet(),
            tehranDay(System.currentTimeMillis()),
        )
    }

    /** 30 days of deposits/withdrawals for one account, by [BankAccount.key]. */
    fun accountFlow(accountKey: String): AccountFlow =
        com.doxigo.muchtoman.accountFlow(ledger.allEntries, setOf(accountKey), tehranDay(System.currentTimeMillis()))

    /**
     * Committed money: the sum of unpaid installments across all active obligations,
     * plus active reservations. Whole Rial, like everything else in the financial model.
     */
    val committedRial: Long by lazy {
        val installmentCommitted = obligations.filter { !it.archived }.sumOf { obligation ->
            val paid = obligationPayments[obligation.id]?.count { it.paidDate != null } ?: 0
            val unpaidCount = obligation.installmentCount - paid
            unpaidCount.toLong() * obligation.installmentRial
        }
        val reservationCommitted = reservations.filter { !it.released }.sumOf { it.amountRial }
        installmentCommitted + reservationCommitted
    }

    /**
     * Available to spend: bank balance minus money committed to future installment payments.
     * In Toman, derived from [bankToman] and [committedRial].
     */
    val availableToman: Double by lazy {
        bankToman - committedRial / 10.0
    }

    /** True while any tracked balance is a guess rather than something a bank stated. */
    val bankUnsure: Boolean
        get() = bankAccounts.any { it.bank !in disabledBanks && !it.trusted }

    /** See [com.doxigo.muchtoman.listHoldings] — shared with the widget and the daily worker. */
    val listHoldings: List<Holding> by lazy {
        listHoldings(
            // Filed-away rows vanish from every screen at once: Home, بازار, the widget and
            // the daily snapshot all read this list. The manage list reads [holdings], where
            // they wait in بایگانی to be restored.
            holdings.filterNot { it.archived },
            smsEnabled,
            bankAccounts,
            disabledBanks,
        )
    }

    /** Reads both of the above, so as a get() it paid for both of them again every time. */
    val totals: Totals by lazy { computeTotals(listHoldings, effective) }

    /**
     * The money she should not think of as spendable: gold, coins, currency and every other
     * priced holding outside the bank accounts. The gap between the two hero figures —
     * کل دارایی and نقدینگی آزاد — is this, and naming it keeps long-term savings from
     * reading as wallet money.
     */
    val illiquidToman: Double by lazy {
        val liquid = bankTotal(bankAccounts, disabledBanks)
        (totals.toman - liquid).coerceAtLeast(0.0)
    }
}

/**
 * A permanent delete waiting out its undo window.
 *
 * [count] is how many transactions the tap covers; when the window elapses with no undo, the
 * durable source rows and every decision attached to them go for good. UI renders this as a
 * snackbar-style bar with an "undo" action.
 */
data class PendingUndo(
    val count: Int,
    val label: String,
    val dueAt: Long,
)

// FragmentActivity, not ComponentActivity: androidx.biometric's BiometricPrompt requires it.
class MainActivity : FragmentActivity() {
    private lateinit var appVm: AppVm

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFormat(android.graphics.PixelFormat.RGBA_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
        }
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashView -> splashView.remove() }
        }
        appVm = ViewModelProvider(this)[AppVm::class.java]
        appVm.acceptPairing(intent?.dataString)
        enableEdgeToEdge()
        setContent {
            val state by appVm.state.collectAsStateWithLifecycle()

            MuchTomanTheme(mode = state.themeMode) {
                // The whole app is Persian, so force RTL regardless of the phone's locale.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

                    // With the lock on, keep the total out of the app-switcher thumbnail too —
                    // otherwise the number is readable without ever unlocking anything.
                    //
                    // That is all it was ever meant to do. FLAG_SECURE does it by blocking
                    // every form of screen capture, which also stops her sending a screenshot
                    // to whoever helps her with the phone — a real cost for a lock that only
                    // guards a number. Where the platform offers the narrow tool, use it.
                    LaunchedEffect(state.lockEnabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            setRecentsScreenshotEnabled(!state.lockEnabled)
                        } else if (state.lockEnabled) {
                            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }

                    AppRoot(appVm, state, this@MainActivity)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        appVm.acceptPairing(intent.dataString)
    }
}
