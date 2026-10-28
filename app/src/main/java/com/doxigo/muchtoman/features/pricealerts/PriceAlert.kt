package com.doxigo.muchtoman.features.pricealerts

import com.doxigo.muchtoman.faNumber
import kotlinx.serialization.Serializable

/** Which side of the target the rate has to reach. */
@Serializable
enum class AlertDirection(val fa: String) {
    ABOVE("بالاتر از"),
    BELOW("پایین‌تر از"),
}

/**
 * One price alert on one asset: "tell me once when [assetId] reaches [target] Toman".
 *
 * [assetName] is the name she saw when she set it, kept so the notification can be written from
 * a background worker that has no catalogue at hand. [armed] goes false the moment the alert
 * fires; a fired alert is kept (not deleted) so the sheet can still show it as «رسید».
 */
@Serializable
data class PriceAlert(
    val id: String,
    val assetId: String,
    val assetName: String,
    val target: Double,
    val direction: AlertDirection,
    val armed: Boolean = true,
    val createdAt: Long = 0L,
    val firedAt: Long = 0L,
)

/** The result of checking a set of alerts against one set of rates. */
data class AlertCheck(
    /** Alerts that crossed on this check, as they were before disarming. */
    val fired: List<PriceAlert>,
    /** Every alert, with the fired ones disarmed. Same order as the input. */
    val alerts: List<PriceAlert>,
)

/**
 * The crossing logic, pure and JVM-testable.
 *
 * An armed alert fires when the rate is at or past its target on its own side: ABOVE fires at
 * `rate >= target`, BELOW at `rate <= target`. A missing, zero, negative or non-finite rate is
 * "no data", never a crossing — a failed fetch must not read as gold falling to zero.
 */
object PriceAlertRules {

    fun crossed(alert: PriceAlert, rate: Double?): Boolean {
        if (!alert.armed) return false
        if (rate == null || !rate.isFinite() || rate <= 0.0) return false
        return when (alert.direction) {
            AlertDirection.ABOVE -> rate >= alert.target
            AlertDirection.BELOW -> rate <= alert.target
        }
    }

    fun check(alerts: List<PriceAlert>, rates: Map<String, Double>, now: Long): AlertCheck {
        val fired = ArrayList<PriceAlert>()
        val updated = alerts.map { alert ->
            if (crossed(alert, rates[alert.assetId])) {
                fired += alert
                alert.copy(armed = false, firedAt = now)
            } else {
                alert
            }
        }
        return AlertCheck(fired, updated)
    }

    /**
     * The direction a new alert should default to: a target above today's rate is waiting for a
     * rise, one below it for a fall. Without a current rate, ABOVE.
     */
    fun defaultDirection(target: Double, currentRate: Double?): AlertDirection =
        if (currentRate != null && currentRate > 0.0 && target < currentRate) {
            AlertDirection.BELOW
        } else {
            AlertDirection.ABOVE
        }

    /** «طلای ۱۸ عیار به ۲٬۶۰۰٬۰۰۰ تومان رسید» */
    fun message(alert: PriceAlert): String =
        "${alert.assetName} به ${faNumber(alert.target)} تومان رسید"
}
