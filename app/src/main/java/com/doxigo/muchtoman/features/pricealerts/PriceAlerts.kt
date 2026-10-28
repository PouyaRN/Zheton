package com.doxigo.muchtoman.features.pricealerts

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * The price-alert feature's whole public API.
 *
 * Alerts live in their own preferences file (not in [com.doxigo.muchtoman.Store]) so this feature
 * owns its persistence outright. Every write goes through one process-wide lock, because the
 * foreground refresh and [PriceAlertWorker] can both check at the same moment and an alert must
 * fire exactly once.
 *
 * Usage:
 *  * `PriceAlerts.add(context, assetId, assetName, target, direction)` / `remove(context, id)`
 *  * `PriceAlerts.forAsset(context, assetId)` or observe [changes] for the list
 *  * `PriceAlerts.check(context, rates)` after any rate refresh; posts and disarms.
 */
object PriceAlerts {

    private const val PREFS = "price_alerts"
    private const val KEY = "alerts"
    private val JSON = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private val _changes = MutableStateFlow(0)

    /** Bumps on every write, so a screen can re-read without polling. */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    fun all(context: Context): List<PriceAlert> = synchronized(lock) { read(context) }

    fun forAsset(context: Context, assetId: String): List<PriceAlert> =
        all(context).filter { it.assetId == assetId }

    fun hasArmed(context: Context): Boolean = all(context).any { it.armed }

    fun add(
        context: Context,
        assetId: String,
        assetName: String,
        target: Double,
        direction: AlertDirection,
        now: Long = System.currentTimeMillis(),
    ): PriceAlert {
        require(target.isFinite() && target > 0.0) { "target must be positive" }
        val alert = PriceAlert(
            id = UUID.randomUUID().toString(),
            assetId = assetId,
            assetName = assetName,
            target = target,
            direction = direction,
            createdAt = now,
        )
        synchronized(lock) { write(context, read(context) + alert) }
        PriceAlertWorker.sync(context)
        return alert
    }

    fun remove(context: Context, id: String) {
        synchronized(lock) { write(context, read(context).filterNot { it.id == id }) }
        PriceAlertWorker.sync(context)
    }

    /**
     * Replaces every alert with [alerts] (a restored backup) and resyncs the worker. The caller
     * decides which are armed; see `restoreMapStores` in the backup feature.
     */
    fun replaceAll(context: Context, alerts: List<PriceAlert>) {
        val app = context.applicationContext
        synchronized(lock) { write(app, alerts) }
        PriceAlertWorker.sync(app)
    }

    /**
     * Check every armed alert against [rates] (Toman per unit, as `effectiveRates` returns).
     * Each crossed alert posts one notification and is disarmed. An alert whose notification
     * could not be posted (permission off, channel muted) stays armed, and is checked again on
     * the next refresh against the price as it is then.
     *
     * Returns the alerts that fired.
     */
    fun check(
        context: Context,
        rates: Map<String, Double>,
        now: Long = System.currentTimeMillis(),
    ): List<PriceAlert> {
        val app = context.applicationContext
        val fired = synchronized(lock) {
            val current = read(app)
            if (current.none { it.armed }) return emptyList()
            val result = PriceAlertRules.check(current, rates, now)
            if (result.fired.isEmpty()) return emptyList()
            val posted = result.fired.filter { PriceAlertNotifications.post(app, it) }.map { it.id }.toSet()
            if (posted.isEmpty()) return emptyList()
            write(app, current.map { if (it.id in posted) it.copy(armed = false, firedAt = now) else it })
            result.fired.filter { it.id in posted }
        }
        PriceAlertWorker.sync(app)
        return fired
    }

    private fun read(context: Context): List<PriceAlert> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { JSON.decodeFromString<List<PriceAlert>>(it) }.getOrNull() }
            ?: emptyList()

    private fun write(context: Context, alerts: List<PriceAlert>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, JSON.encodeToString(alerts))
            .commit()
        _changes.value = _changes.value + 1
    }
}
