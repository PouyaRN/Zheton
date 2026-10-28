package com.doxigo.muchtoman

import kotlinx.serialization.Serializable
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

// ════════════════════════════════════════════════════════════════════════════════
// Holding cost basis, P&L and per-asset daily rate history (ticket #10).
//
// Everything here is either pure (tested in HoldingPnlTest) or a thin file store. No number
// in this file is made up: a cost is only known from lots she recorded, a change only from
// closes the app actually fetched and saved. Where either is missing the answer is null, and
// the screens print a dash — never a zero, which would read as "no profit" or "flat".
// ════════════════════════════════════════════════════════════════════════════════

@Serializable
enum class LotSide { BUY, SELL }

/**
 * One ثبت خرید / ثبت فروش: [amount] units of the holding's asset at [unitPriceToman] each,
 * on [atMs]. Stored per holding key (see [Holding.key]) in [Store.holdingLots].
 */
@Serializable
data class HoldingLot(
    val id: String,
    val side: LotSide,
    val amount: Double,
    val unitPriceToman: Double,
    val atMs: Long,
)

/** Whether a lot is worth storing at all: positive, finite numbers only. */
fun isValidLot(amount: Double, unitPriceToman: Double): Boolean =
    amount.isFinite() && amount > 0.0 && unitPriceToman.isFinite() && unitPriceToman > 0.0

/**
 * The lots folded with the average-cost method, oldest first.
 *
 * A buy adds its units and its cost. A sell takes units out at the current average cost
 * (the average itself does not move) and books the difference as [realizedPnlToman]. A sell
 * larger than the tracked units only removes what was tracked: the rest came out of units she
 * owned before recording anything, whose cost nobody knows.
 */
data class LotSummary(
    /** Units the lots account for. */
    val trackedAmount: Double,
    /** What those units cost her. */
    val trackedCostToman: Double,
    /** Profit already taken by sells, against the average cost at the time of each sell. */
    val realizedPnlToman: Double,
) {
    /** Null when the lots hold nothing — there is no average of nothing. */
    val averageCostToman: Double?
        get() = if (trackedAmount > EPS) trackedCostToman / trackedAmount else null
}

private const val EPS = 1e-9

fun summarizeLots(lots: List<HoldingLot>): LotSummary {
    var qty = 0.0
    var cost = 0.0
    var realized = 0.0
    for (lot in lots.sortedBy { it.atMs }) {
        if (!isValidLot(lot.amount, lot.unitPriceToman)) continue
        when (lot.side) {
            LotSide.BUY -> {
                qty += lot.amount
                cost += lot.amount * lot.unitPriceToman
            }
            LotSide.SELL -> {
                if (qty <= EPS) continue
                val avg = cost / qty
                val sold = minOf(lot.amount, qty)
                realized += sold * (lot.unitPriceToman - avg)
                cost -= sold * avg
                qty -= sold
                if (qty <= EPS) { qty = 0.0; cost = 0.0 }
            }
        }
    }
    return LotSummary(qty, cost, realized)
}

/**
 * Everything the Market row and the Asset screen print about one holding.
 *
 * Nullable means unknown and is drawn as a dash:
 * - [priceToman]/[valueToman]: no rate for the asset.
 * - [averageCostToman]: no buy recorded (every holding from before lots existed).
 * - [costBasisToman]/[pnlToman]/[pnlPercent]: the lots do not cover [amount] — some of what
 *   she holds was never given a price — or there is no rate to compare against.
 * - [dayChangePercent]: no stored close from a recent earlier day.
 */
data class HoldingPosition(
    val key: String,
    val typeId: String,
    val amount: Double,
    val priceToman: Double?,
    val valueToman: Double?,
    val averageCostToman: Double?,
    val costBasisToman: Double?,
    val pnlToman: Double?,
    val pnlPercent: Double?,
    val realizedPnlToman: Double,
    /** Units of [amount] no recorded buy accounts for. Zero when the cost is fully known. */
    val uncoveredAmount: Double,
    val dayChange: DayChange?,
    val lots: List<HoldingLot>,
) {
    val costKnown: Boolean get() = costBasisToman != null
    val dayChangePercent: Double? get() = dayChange?.percent
}

fun positionOf(
    holding: Holding,
    lots: List<HoldingLot>,
    priceToman: Double?,
    dayChange: DayChange? = null,
): HoldingPosition {
    val price = priceToman?.takeIf { it.isFinite() && it > 0.0 }
    val summary = summarizeLots(lots)
    val avg = summary.averageCostToman
    val amount = holding.amount
    val uncovered = (amount - summary.trackedAmount).coerceAtLeast(0.0).let { if (it <= 1e-7) 0.0 else it }
    // Covered: every unit she holds has a recorded price. If she typed the amount down below
    // what the lots hold, the average still prices what is left.
    val costBasis = if (avg != null && uncovered == 0.0) amount * avg else null
    val value = price?.let { amount * it }
    val pnl = if (costBasis != null && value != null) value - costBasis else null
    val pnlPct = if (pnl != null && costBasis != null && costBasis > 0.0) pnl / costBasis * 100.0 else null
    return HoldingPosition(
        key = holding.key,
        typeId = holding.typeId,
        amount = amount,
        priceToman = price,
        valueToman = value,
        averageCostToman = avg,
        costBasisToman = costBasis,
        pnlToman = pnl,
        pnlPercent = pnlPct,
        realizedPnlToman = summary.realizedPnlToman,
        uncoveredAmount = uncovered,
        dayChange = dayChange,
        lots = lots.sortedBy { it.atMs },
    )
}

// ── Daily rate history ──────────────────────────────────────────────────────────

/** Closes per asset: Tehran epoch day → the last Toman rate fetched that day. */
typealias DailyCloses = Map<Long, Double>

/** Five years of chart plus margin. */
const val DAILY_RATE_KEEP_DAYS = 5 * 366 + 30

/** The asset screen's period chips, in canvas order. [days] is the window the chart spans. */
enum class RatePeriod(val days: Int, val labelFa: String) {
    WEEK(7, "هفته"),
    MONTH(30, "ماه"),
    MONTH_3(91, "۳ ماه"),
    YEAR(365, "سال"),
    YEAR_5(5 * 365 + 1, "۵ سال"),
}

data class RatePoint(val day: Long, val priceToman: Double)

/**
 * The rate over one period, from stored closes only.
 *
 * [complete] is false when the stored history starts after the period does — the app was not
 * collecting rates yet. The points are then everything that exists, and [availableDays] says
 * how far back they reach, so the screen can say "فقط X روز سابقه داریم" instead of drawing
 * a short history stretched to look like a full one.
 */
data class RateSeries(
    val assetId: String,
    val period: RatePeriod,
    val points: List<RatePoint>,
    val complete: Boolean,
    val availableDays: Int,
) {
    val first: RatePoint? get() = points.firstOrNull()
    val last: RatePoint? get() = points.lastOrNull()
    val isEmpty: Boolean get() = points.isEmpty()

    /** Change from the first to the last point, or null with fewer than two points. */
    val changeToman: Double?
        get() = if (points.size >= 2) points.last().priceToman - points.first().priceToman else null
    val changePercent: Double?
        get() {
            val start = points.firstOrNull()?.priceToman ?: return null
            val delta = changeToman ?: return null
            return if (start > 0.0) delta / start * 100.0 else null
        }
    val highToman: Double? get() = points.maxOfOrNull { it.priceToman }
    val lowToman: Double? get() = points.minOfOrNull { it.priceToman }
}

/** A few days of grace at the start of a window: the app is not opened every single day. */
private const val PERIOD_GRACE_DAYS = 3

/**
 * [closes] cut to [period] ending on [today]. The base point is the newest close at or before
 * the period's first day (so a month chart starts where the month did), else the oldest close
 * inside the window. [livePriceToman], if given, is today's point — the rate on screen now.
 */
fun rateSeries(
    assetId: String,
    closes: DailyCloses,
    period: RatePeriod,
    today: Long,
    livePriceToman: Double? = null,
): RateSeries {
    val start = today - period.days
    val valid = closes.filter { (d, p) -> d <= today && p.isFinite() && p > 0.0 }.toMutableMap()
    livePriceToman?.takeIf { it.isFinite() && it > 0.0 }?.let { valid[today] = it }
    val base = valid.keys.filter { it <= start }.maxOrNull()
    val inside = valid.keys.filter { it > start }.sorted()
    val days = listOfNotNull(base) + inside
    val points = days.map { RatePoint(it, valid.getValue(it)) }
    val firstDay = points.firstOrNull()?.day
    val complete = firstDay != null && firstDay <= start + PERIOD_GRACE_DAYS
    val available = if (firstDay == null) 0 else (today - firstDay).toInt()
    return RateSeries(assetId, period, points, complete, available)
}

/** Today's rate against the stored close of [baseDay]. */
data class DayChange(val percent: Double, val changeToman: Double, val baseDay: Long, val basePriceToman: Double)

/**
 * The change against the most recent stored close before [today] — "vs yesterday". A close
 * older than [maxGapDays] is not yesterday's, so that is null rather than a week's move
 * presented as a day's. [currentPriceToman] defaults to today's stored close.
 */
fun dayChange(
    closes: DailyCloses,
    today: Long,
    currentPriceToman: Double? = null,
    maxGapDays: Int = 3,
): DayChange? {
    val current = (currentPriceToman ?: closes[today])?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val baseDay = closes.keys.filter { it < today && it >= today - maxGapDays }.maxOrNull() ?: return null
    val base = closes.getValue(baseDay).takeIf { it.isFinite() && it > 0.0 } ?: return null
    val delta = current - base
    return DayChange(delta / base * 100.0, delta, baseDay, base)
}

/** [closes] with [day]'s close set to [price], pruned to [DAILY_RATE_KEEP_DAYS]. */
fun recordClose(closes: DailyCloses, day: Long, price: Double): DailyCloses {
    if (!price.isFinite() || price <= 0.0) return closes
    val next = closes + (day to price)
    val floor = day - DAILY_RATE_KEEP_DAYS
    return if (next.keys.any { it < floor }) next.filterKeys { it >= floor } else next
}

/**
 * Closes from elsewhere (a restored backup) folded into [existing]. A day this phone already has
 * keeps its own close: both are real fetched rates, and the local one is the one the charts have
 * been drawing. Invalid prices are dropped and the result is pruned to [DAILY_RATE_KEEP_DAYS].
 */
fun mergeDailyCloses(existing: DailyCloses, incoming: DailyCloses): DailyCloses {
    val merged = incoming.filterValues { it.isFinite() && it > 0.0 } + existing
    if (merged.isEmpty()) return merged
    val floor = merged.keys.max() - DAILY_RATE_KEEP_DAYS
    return if (merged.keys.any { it < floor }) merged.filterKeys { it >= floor } else merged
}

/** Collapses timestamped observations to one close per Tehran day — the last one. */
fun closesFromObservations(observations: List<RateObservation>): DailyCloses =
    observations.filter { it.priceToman.isFinite() && it.priceToman > 0.0 }
        .sortedBy { it.timestampMs }
        .associate { tehranDay(it.timestampMs) to it.priceToman }

/**
 * Which assets get a daily close: every fixed asset in the catalogue plus whatever she holds
 * (coins, نماد). Not the Worker's whole payload: a few thousand نماد × five years would not
 * fit anywhere sensible, and nobody looks at the chart of an asset they neither hold nor see.
 */
fun dailyRateIds(holdings: List<Holding>): Set<String> =
    (STATIC_CATALOG.map { it.id } + holdings.map { it.typeId })
        .filterNot { it == TOMAN_ID || it == BANK_ID }
        .toSet()

/**
 * One small text file per asset ("day price" lines) under [dir]. Per asset so a refresh only
 * rewrites the assets whose close moved, and written to a temp file then renamed so a reader
 * never sees half a file.
 */
class DailyRateFiles(private val dir: File) {

    @Synchronized
    fun load(assetId: String): DailyCloses {
        val file = fileOf(assetId)
        if (!file.exists()) return emptyMap()
        return runCatching {
            file.readLines().mapNotNull { line ->
                val parts = line.trim().split(' ')
                if (parts.size != 2) return@mapNotNull null
                val day = parts[0].toLongOrNull() ?: return@mapNotNull null
                val price = parts[1].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
                day to price
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    @Synchronized
    fun loadAll(): Map<String, DailyCloses> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(SUFFIX) } ?: return emptyMap()
        return files.associate { f ->
            val id = URLDecoder.decode(f.name.removeSuffix(SUFFIX), "UTF-8")
            id to load(id)
        }.filterValues { it.isNotEmpty() }
    }

    /**
     * Sets [day]'s close for every asset in [rates] that [ids] allows. Returns the full closes
     * of the assets that changed, for the caller to merge into what it shows.
     */
    @Synchronized
    fun record(rates: Map<String, Double>, day: Long, ids: Set<String>): Map<String, DailyCloses> {
        val changed = mutableMapOf<String, DailyCloses>()
        for ((id, price) in rates) {
            if (id !in ids || !price.isFinite() || price <= 0.0) continue
            val old = load(id)
            if (old[day] == price) continue
            val next = recordClose(old, day, price)
            write(id, next)
            changed[id] = next
        }
        return changed
    }

    /**
     * One-time import of the intraday observations the older store kept (real fetched rates,
     * at most 200 per asset), so the first charts start from what was already on the phone.
     * Never overwrites a day this store already has.
     */
    @Synchronized
    fun seedOnce(observations: Map<String, List<RateObservation>>) {
        val marker = File(dir, SEED_MARKER)
        if (marker.exists()) return
        dir.mkdirs()
        for ((id, obs) in observations) {
            if (id == TOMAN_ID || id == BANK_ID) continue
            val seeded = closesFromObservations(obs)
            if (seeded.isEmpty()) continue
            val old = load(id)
            val merged = seeded + old
            if (merged != old) write(id, merged.filterKeys { it >= (merged.keys.max() - DAILY_RATE_KEEP_DAYS) })
        }
        runCatching { marker.writeText("1") }
    }

    /**
     * Folds [closes] into the stored history per asset (see [mergeDailyCloses]; days already on
     * this phone win). Returns the full closes of the assets that changed.
     */
    @Synchronized
    fun merge(closes: Map<String, DailyCloses>): Map<String, DailyCloses> {
        val changed = mutableMapOf<String, DailyCloses>()
        for ((id, incoming) in closes) {
            if (id == TOMAN_ID || id == BANK_ID || incoming.isEmpty()) continue
            val old = load(id)
            val next = mergeDailyCloses(old, incoming)
            if (next == old) continue
            write(id, next)
            changed[id] = next
        }
        return changed
    }

    private fun write(assetId: String, closes: DailyCloses) {
        dir.mkdirs()
        val target = fileOf(assetId)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(closes.entries.sortedBy { it.key }.joinToString("\n") { "${it.key} ${it.value}" })
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    private fun fileOf(assetId: String) = File(dir, URLEncoder.encode(assetId, "UTF-8") + SUFFIX)

    private companion object {
        const val SUFFIX = ".rates"
        const val SEED_MARKER = ".seeded"
    }
}

/**
 * Saves today's close for the rates just fetched. Called on every successful fetch (app and
 * the daily worker), so a day the phone is online is a day of history. [updatedAt] is the
 * rates' own timestamp: cached rates replayed later must not become a later day's close.
 */
fun persistDailyRates(
    files: DailyRateFiles,
    rates: Map<String, Double>,
    updatedAt: Long,
    holdings: List<Holding>,
): Map<String, DailyCloses> {
    if (updatedAt <= 0L) return emptyMap()
    return runCatching { files.record(rates, tehranDay(updatedAt), dailyRateIds(holdings)) }
        .onFailure { android.util.Log.w("muchtoman", "daily rates write failed: $it") }
        .getOrDefault(emptyMap())
}

// ── Read side for the Market and Asset screens ──────────────────────────────────

/** Lots recorded for the holding [key], oldest first. */
fun UiState.lotsOf(key: String): List<HoldingLot> = holdingLots[key].orEmpty().sortedBy { it.atMs }

/** Stored daily closes for one asset. */
fun UiState.closesOf(typeId: String): DailyCloses = dailyRates[typeId].orEmpty()

/** Today's change for an asset vs the last stored close of an earlier day, or null. */
fun UiState.dayChangeOf(typeId: String, nowMs: Long = System.currentTimeMillis()): DayChange? =
    dayChange(closesOf(typeId), tehranDay(nowMs), effective[typeId])

/** The holding [key] with value, cost, P&L and day change worked out; null if no such row. */
fun UiState.positionOf(key: String, nowMs: Long = System.currentTimeMillis()): HoldingPosition? {
    val holding = holdings.firstOrNull { it.key == key } ?: return null
    return positionOf(holding, lotsOf(key), effective[holding.typeId], dayChangeOf(holding.typeId, nowMs))
}

/** Every visible holding (archived ones excluded, bank row included), as positions. */
fun UiState.positions(nowMs: Long = System.currentTimeMillis()): List<HoldingPosition> =
    listHoldings.map { h -> positionOf(h, lotsOf(h.key), effective[h.typeId], dayChangeOf(h.typeId, nowMs)) }

/** The rate chart for [typeId] over [period], today's point being the rate on screen now. */
fun UiState.rateSeriesOf(
    typeId: String,
    period: RatePeriod,
    nowMs: Long = System.currentTimeMillis(),
): RateSeries = rateSeries(typeId, closesOf(typeId), period, tehranDay(nowMs), effective[typeId])

/** A Market-row sparkline: the week's stored closes plus today's live rate. */
fun UiState.sparklineOf(typeId: String, nowMs: Long = System.currentTimeMillis()): List<Double> =
    rateSeriesOf(typeId, RatePeriod.WEEK, nowMs).points.map { it.priceToman }
