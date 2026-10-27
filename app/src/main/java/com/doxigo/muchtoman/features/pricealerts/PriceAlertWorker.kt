package com.doxigo.muchtoman.features.pricealerts

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.doxigo.muchtoman.BuildConfig
import com.doxigo.muchtoman.Store
import com.doxigo.muchtoman.effectiveRates
import com.doxigo.muchtoman.fetchRates
import com.doxigo.muchtoman.fetchTse
import com.doxigo.muchtoman.isStockId
import com.doxigo.muchtoman.mergeRates
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * The background rate refresh for price alerts. The daily snapshot runs once a day, which is too
 * coarse for "tell me when gold reaches X", so while at least one alert is armed this fetches the
 * same rates every [INTERVAL_MINUTES] and checks them. With nothing armed it is not scheduled at
 * all: no alert, no background traffic.
 */
class PriceAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext
        return try {
            val alerts = PriceAlerts.all(app).filter { it.armed }
            if (alerts.isEmpty()) {
                sync(app)
                return Result.success()
            }
            val store = Store(app)
            fetchRates(BuildConfig.RATES_URL).onSuccess {
                store.cachedRates = mergeRates(it, store.cachedRates)
            }
            if (alerts.any { isStockId(it.assetId) }) {
                fetchTse().onSuccess { store.cachedStocks = it }
            }
            PriceAlerts.check(app, effectiveRates(store.cachedRates, store.overrides, store.cachedStocks))
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The next period tries again; a retry storm buys nothing.
            Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "price-alerts"
        const val INTERVAL_MINUTES = 30L

        /** Schedule the periodic check while an alert is armed, cancel it when none is. Idempotent. */
        fun sync(context: Context) {
            val app = context.applicationContext
            val manager = runCatching { WorkManager.getInstance(app) }.getOrNull() ?: return
            if (PriceAlerts.hasArmed(app)) {
                manager.enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<PriceAlertWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
                        .setConstraints(
                            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                        )
                        .build(),
                )
            } else {
                manager.cancelUniqueWork(WORK_NAME)
            }
        }
    }
}
