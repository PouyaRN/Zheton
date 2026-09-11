package com.doxigo.muchtoman

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Snapshot containing pre-computed data required by Glance widgets.
 * Eliminates redundant SQLite connection thrashing across multiple parallel widget updates.
 */
data class WidgetSnapshot(
    val netWorthToman: Double,
    val isMasked: Boolean,
    val deltaPercent: Double?,
    val deltaSince: String,
    val todaySpendToman: Long,
    val goldRateToman: Double?,
    val tetherRateToman: Double?,
    val nextCommitmentText: String?,
    val timestamp: Long = System.currentTimeMillis(),
)

object WidgetSnapshotEngine {
    private val mutex = Mutex()
    @Volatile
    private var cachedSnapshot: WidgetSnapshot? = null
    private const val CACHE_TTL_MS = 5_000L // 5-second deduplication window for parallel widget broadcasts

    /**
     * Loads or returns a cached snapshot in a single pass.
     */
    suspend fun getSnapshot(context: Context, forceRefresh: Boolean = false): WidgetSnapshot = mutex.withLock {
        val now = System.currentTimeMillis()
        val cached = cachedSnapshot
        if (!forceRefresh && cached != null && (now - cached.timestamp) < CACHE_TTL_MS) {
            return@withLock cached
        }

        withContext(Dispatchers.IO) {
            val store = Store(context)
            val rates = store.cachedRates
            val history = store.history
            val effective = effectiveRates(rates, store.overrides, store.cachedStocks)
            val total = computeTotals(
                listHoldings(store.holdings, store.smsEnabled, store.bankAccounts, store.disabledBanks),
                effective,
            ).toman

            val today = now / DAY_MS
            val baseDay = history.keys.filter { it < today }.maxOrNull()
            val base = baseDay?.let { history.getValue(it) }
            val delta = base?.let { total - it }
            val deltaPercent = base?.takeIf { it > 0 }?.let { (total - it) / it * 100 }
            val deltaSince = when {
                baseDay == null -> ""
                today - baseDay == 1L -> "از دیروز"
                else -> "${faNumber((today - baseDay).toDouble())} روز پیش"
            }

            var todaySpendToman = 0L
            runCatching {
                val derived = DerivedDb.get(context)
                val todayTehran = tehranDay(now)
                val txns = derived.txn().newest(200)
                val rial = txns.filter { it.day == todayTehran && it.direction == "out" }
                    .sumOf { abs(it.signedRial ?: 0L) }
                todaySpendToman = rial / 10L
            }

            val nextCommitment = store.commitments
                .filter { !it.isSettled && it.nextDueTimestamp > 0L }
                .minByOrNull { it.nextDueTimestamp }

            val nextCommitmentText = nextCommitment?.let {
                val days = it.nextDueDays
                val dueLabel = when (days) {
                    0 -> "سررسید امروز"
                    1 -> "سررسید فردا"
                    else -> "${faNumber(days.toDouble())} روز مانده"
                }
                val providerName = it.provider.ifBlank { it.title }
                "$providerName: $dueLabel (${faCompact(it.remainingAmountToman.toDouble())} ت)"
            }

            val goldRate = effective["gold18"] ?: effective["geram18"]
            val tetherRate = effective["usdt"] ?: effective["usd"]

            val snapshot = WidgetSnapshot(
                netWorthToman = total,
                isMasked = store.widgetLock,
                deltaPercent = deltaPercent,
                deltaSince = deltaSince,
                todaySpendToman = todaySpendToman,
                goldRateToman = goldRate,
                tetherRateToman = tetherRate,
                nextCommitmentText = nextCommitmentText,
                timestamp = now,
            )
            cachedSnapshot = snapshot
            snapshot
        }
    }

    fun invalidateCache() {
        cachedSnapshot = null
    }
}
