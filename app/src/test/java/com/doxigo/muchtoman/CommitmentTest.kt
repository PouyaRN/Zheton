package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitmentTest {

    @Test
    fun `Commitment calculates progress percent correctly`() {
        val commitment = Commitment(
            id = "c1",
            title = "اقساط اسنپ‌پی",
            provider = "اسنپ‌پی",
            totalAmountRial = 40_000_000L, // 4M toman
            remainingAmountRial = 20_000_000L, // 2M toman
            installmentCount = 4,
            paidInstallmentCount = 2,
        )

        assertEquals(4_000_000L, commitment.totalAmountToman)
        assertEquals(2_000_000L, commitment.remainingAmountToman)
        assertEquals(0.5f, commitment.progressPercent, 0.01f)
    }

    @Test
    fun `Commitment discounts future installments with inflation rate`() {
        val commitment = Commitment(
            id = "c2",
            title = "وام بانکی",
            provider = "بانک ملت",
            totalAmountRial = 30_000_000L * 10L, // 30M toman
            remainingAmountRial = 30_000_000L * 10L,
            installmentCount = 10,
            paidInstallmentCount = 0,
        )

        // At 12 months with 45% annual inflation:
        // PV = 30M / 1.45 ≈ 20,689,655
        val pv12m = commitment.discountedPresentValueToman(12, 0.45)
        assertTrue(pv12m in 20_600_000L..20_700_000L)

        val relief12m = commitment.inflationReliefToman(12, 0.45)
        assertEquals(commitment.remainingAmountToman - pv12m, relief12m)
        assertTrue(relief12m > 9_000_000L)
    }
}
