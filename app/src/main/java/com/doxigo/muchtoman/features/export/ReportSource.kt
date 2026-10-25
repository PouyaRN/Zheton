package com.doxigo.muchtoman.features.export

import android.content.Context
import com.doxigo.muchtoman.DerivedDb
import com.doxigo.muchtoman.DurableDb
import com.doxigo.muchtoman.Store
import com.doxigo.muchtoman.catalog
import com.doxigo.muchtoman.effectiveRates
import com.doxigo.muchtoman.ledgerEntries
import com.doxigo.muchtoman.tehranDayStart

/**
 * The only device-facing part of report generation.
 *
 * It reads — never writes — through the public ledger/store API the rest of the app reads with:
 * [ledgerEntries] for the classified transactions, `DurableDb` for the installment schedule,
 * and [Store] for the manually tracked commitments and the holdings. Keeping this thin is the
 * point: [ReportBuilder] stays a pure function, and every Android call is in one place that a
 * reviewer can check for side effects.
 *
 * The derived ledger is read as it stands rather than re-derived. An export is launched from
 * inside the app, where the ledger is already current; forcing a re-derive here would make the
 * export slow and, worse, could change numbers between opening the screen and pressing the
 * button.
 */
class ReportSource(context: Context) {

    private val app: Context = context.applicationContext

    /** Reads everything [buildReport] needs for [range]. Call from a background dispatcher. */
    suspend fun inputs(range: DayRange, now: Long = System.currentTimeMillis()): ReportInputs {
        val durable = DurableDb.get(app)
        val derived = DerivedDb.get(app)
        val ledger = ledgerEntries(derived, durable, limit = Int.MAX_VALUE)
        val store = Store(app)

        // Same price map the home screen shows: cached Worker rates, cached bourse snapshot and
        // her own hand-typed overrides. No fetch — a report must work offline.
        val rates = effectiveRates(store.cachedRates, store.overrides, store.cachedStocks)
        val assets = assetLines(
            holdings = store.holdings,
            types = catalog(store.cachedRates.coins, store.cachedStocks.stocks),
            ratesToman = rates,
        )

        return ReportInputs(
            range = range,
            entries = ledger.entries,
            obligations = durable.obligations().active(),
            // One query for every unpaid installment up to the end of the window. The builder
            // applies the lower bound, so an early range is not expressed as a second SQL
            // predicate that could disagree with the day arithmetic.
            payments = durable.installmentPayments().unpaidDueBefore(tehranDayStart(range.until)),
            commitments = store.commitments,
            assets = assets,
            generatedAt = now,
        )
    }
}
