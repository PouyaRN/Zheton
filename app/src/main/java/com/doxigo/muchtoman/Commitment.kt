package com.doxigo.muchtoman

import kotlinx.serialization.Serializable
import kotlin.math.pow

/**
 * Types of financial obligations and deferred commitments in Iran.
 */
enum class CommitmentKind(val fa: String) {
    BNPL("الان بخر، بعداً پرداخت کن (BNPL)"),
    LOAN("وام بانکی"),
    CHECK("چک صیادی"),
    INSTALLMENT("خرید اقساطی کالا"),
}

/**
 * An active, completed, or upcoming credit liability or registered Sayad check.
 */
@Serializable
data class Commitment(
    val id: String,
    val title: String,
    val provider: String, // e.g. "اسنپ‌پی", "دیجی‌پی", "بانک ملت", "چک صیادی"
    val kind: CommitmentKind = CommitmentKind.BNPL,
    val totalAmountRial: Long,
    val remainingAmountRial: Long,
    val installmentCount: Int = 1,
    val paidInstallmentCount: Int = 0,
    val nextDueTimestamp: Long = 0L,
    val note: String = "",
    val isSettled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val totalAmountToman: Long get() = totalAmountRial / 10L
    val remainingAmountToman: Long get() = remainingAmountRial / 10L

    val progressPercent: Float
        get() = if (installmentCount > 0) {
            (paidInstallmentCount.toFloat() / installmentCount.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val nextDueDays: Int
        get() {
            if (nextDueTimestamp <= 0L) return 0
            val diffMs = nextDueTimestamp - System.currentTimeMillis()
            return (diffMs / (24 * 3600 * 1000L)).toInt().coerceAtLeast(0)
        }

    /**
     * Estimates the real purchasing power of this future installment discounted by inflation.
     * At 45% annual baseline inflation in Iran:
     * Discount factor over n months: (1 + 0.45)^(months / 12)
     */
    fun discountedPresentValueToman(monthsInFuture: Int, annualInflationRate: Double = 0.45): Long {
        if (monthsInFuture <= 0) return remainingAmountToman
        val years = monthsInFuture.toDouble() / 12.0
        val discountFactor = (1.0 + annualInflationRate).pow(years)
        return (remainingAmountToman / discountFactor).toLong()
    }

    /**
     * Psychological relief metric: how much nominal value is effectively eroded away by inflation.
     */
    fun inflationReliefToman(monthsInFuture: Int, annualInflationRate: Double = 0.45): Long {
        val pv = discountedPresentValueToman(monthsInFuture, annualInflationRate)
        return (remainingAmountToman - pv).coerceAtLeast(0L)
    }
}
