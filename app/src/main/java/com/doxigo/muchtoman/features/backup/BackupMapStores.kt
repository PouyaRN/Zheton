package com.doxigo.muchtoman.features.backup

import android.content.Context
import com.doxigo.muchtoman.DailyCloses
import com.doxigo.muchtoman.HoldingLot
import com.doxigo.muchtoman.Store
import com.doxigo.muchtoman.effectiveRates
import com.doxigo.muchtoman.features.pricealerts.PriceAlert
import com.doxigo.muchtoman.features.pricealerts.PriceAlertRules
import com.doxigo.muchtoman.features.pricealerts.PriceAlerts

/**
 * The stores added by the redesign (schema version 2), behind one seam: holding purchase lots,
 * the monthly budget, price alerts and the daily rate history.
 *
 * They live in three different places on the phone (`Store` prefs, the `price_alerts` prefs
 * file, `filesDir/daily_rates`), so the backup reads and writes them through this interface
 * rather than knowing each one. It is also what lets [restoreMapStores] — the part with the
 * compatibility rules — run on a plain JVM against a fake.
 */
interface MapStores {
    var holdingLots: Map<String, List<HoldingLot>>
    var monthlyBudgetToman: Long
    fun priceAlerts(): List<PriceAlert>

    /** Replaces every alert and resyncs the alert worker to what is now armed. */
    fun replacePriceAlerts(alerts: List<PriceAlert>)
    fun dailyRates(): Map<String, DailyCloses>

    /** Folds closes in; a day the phone already has keeps its own close. */
    fun mergeDailyRates(closes: Map<String, DailyCloses>)

    /** Toman per unit as the alert check sees them (cached fetch + TSE + overrides). */
    fun currentRates(): Map<String, Double>
}

class DeviceMapStores(context: Context, private val store: Store) : MapStores {
    private val app = context.applicationContext

    override var holdingLots: Map<String, List<HoldingLot>>
        get() = store.holdingLots
        set(v) { store.holdingLots = v }

    override var monthlyBudgetToman: Long
        get() = store.monthlyBudgetToman
        set(v) { store.monthlyBudgetToman = v }

    override fun priceAlerts(): List<PriceAlert> = PriceAlerts.all(app)

    override fun replacePriceAlerts(alerts: List<PriceAlert>) = PriceAlerts.replaceAll(app, alerts)

    override fun dailyRates(): Map<String, DailyCloses> = store.dailyRates.loadAll()

    override fun mergeDailyRates(closes: Map<String, DailyCloses>) {
        store.dailyRates.merge(closes)
    }

    override fun currentRates(): Map<String, Double> =
        effectiveRates(store.cachedRates, store.overrides, store.cachedStocks)
}

/** The version-2 sections of a snapshot, read from the phone. */
fun MapStores.snapshotInto(payload: BackupPayload): BackupPayload = payload.copy(
    prefs = payload.prefs.copy(monthlyBudgetToman = monthlyBudgetToman),
    ledger = payload.ledger.copy(
        holdingLots = holdingLots.mapValues { (_, lots) -> lots.map { it.toBackup() } },
        priceAlerts = priceAlerts().map { it.toBackup() },
    ),
    dailyRates = dailyRates(),
)

/** What [restoreMapStores] applied. Null/false: the file did not carry it, or it was skipped. */
data class MapStoresRestore(
    val holdingLots: Int? = null,
    val monthlyBudgetRestored: Boolean = false,
    val priceAlerts: Int? = null,
    /** Alerts the file had armed whose target the current rate has already reached. */
    val alertsAlreadyReached: Int = 0,
    val dailyRateAssets: Int? = null,
)

/**
 * Applies the version-2 sections of [payload]. The compatibility rules live here:
 *
 * - **Absent means untouched.** A section that is null (every version-1 file, or a future file
 *   that leaves one out) does not touch its store. Only a present section — even an empty one,
 *   which is a snapshot of "none" — is applied.
 * - **Lots, budget and alerts** are part of the store snapshot, so like holdings they follow
 *   [BackupRestoreOptions.restorePreferences] and are replaced wholesale.
 * - **Alerts are re-checked before arming.** An alert the file had armed whose target the
 *   current rate has already reached is restored disarmed («رسید», `firedAt = now`) without a
 *   notification, rather than firing about a crossing that happened while the file sat on a disk.
 *   With no current rate for its asset it stays armed and the next refresh checks it.
 *   [MapStores.replacePriceAlerts] resyncs the worker to what is armed afterwards.
 * - **Daily rates** are real fetched history, like the durable rows: merged always, never
 *   deleting, and a day already on the phone keeps its own close.
 */
fun restoreMapStores(
    payload: BackupPayload,
    options: BackupRestoreOptions,
    stores: MapStores,
    now: Long = System.currentTimeMillis(),
): MapStoresRestore {
    var result = MapStoresRestore()

    payload.dailyRates?.let { rates ->
        stores.mergeDailyRates(rates)
        result = result.copy(dailyRateAssets = rates.size)
    }

    if (!options.restorePreferences) return result

    payload.ledger.holdingLots?.let { backup ->
        val lots = backup
            .mapValues { (_, list) -> list.mapNotNull { it.toEntityOrNull() } }
            .filterValues { it.isNotEmpty() }
        stores.holdingLots = lots
        result = result.copy(holdingLots = lots.values.sumOf { it.size })
    }

    payload.prefs.monthlyBudgetToman?.let {
        stores.monthlyBudgetToman = it.coerceAtLeast(0L)
        result = result.copy(monthlyBudgetRestored = true)
    }

    payload.ledger.priceAlerts?.let { backup ->
        val alerts = backup.mapNotNull { it.toEntityOrNull() }
        val rates = runCatching { stores.currentRates() }.getOrDefault(emptyMap())
        var reached = 0
        val rechecked = alerts.map { alert ->
            if (PriceAlertRules.crossed(alert, rates[alert.assetId])) {
                reached += 1
                alert.copy(armed = false, firedAt = now)
            } else {
                alert
            }
        }
        stores.replacePriceAlerts(rechecked)
        result = result.copy(priceAlerts = rechecked.size, alertsAlreadyReached = reached)
    }

    return result
}
