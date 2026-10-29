package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallmentsTest {

    @Test
    fun `buildObligation creates monthly payments correctly`() {
        val now = 1750000000000L
        val (obligation, payments) = buildObligation(
            id = "ob_1",
            provider = "دیجی‌پی",
            totalRial = 12_000_000L,
            downPaymentRial = 2_000_000L,
            installmentCount = 5,
            installmentRial = 2_000_000L,
            startDate = now,
            notes = "خرید گوشی",
            linkedTxnRef = "s:123:0",
            now = now,
        )

        assertEquals("ob_1", obligation.id)
        assertEquals("دیجی‌پی", obligation.provider)
        assertEquals(5, payments.size)
        assertEquals(1, payments[0].installmentNumber)
        assertEquals(now, payments[0].dueDate)
        assertEquals(5, payments[4].installmentNumber)
        assertEquals(now + 4L * 30L * 24 * 60 * 60 * 1000, payments[4].dueDate)
    }

    @Test
    fun `installmentDueBadge categorizes overdue, due-today, approaching, and normal`() {
        val now = 1750000000000L // arbitrary fixed time
        val oneDayMs = 24L * 60 * 60 * 1000

        // 10 days in the future
        val normalDue = now + 10 * oneDayMs
        val normalBadge = installmentDueBadge(normalDue, now)
        assertEquals(UrgencyLevel.NORMAL, normalBadge.level)
        assertTrue(normalBadge.daysLeft in 9..11)
        assertTrue(normalBadge.text.contains("روز مانده"))

        // 3 days in the future
        val approachingDue = now + 3 * oneDayMs
        val approachingBadge = installmentDueBadge(approachingDue, now)
        assertEquals(UrgencyLevel.WARNING, approachingBadge.level)
        assertTrue(approachingBadge.daysLeft in 2..4)
        assertTrue(approachingBadge.text.contains("روز مانده"))

        // Today
        val todayBadge = installmentDueBadge(now, now)
        assertEquals(UrgencyLevel.WARNING, todayBadge.level)
        assertEquals(0L, todayBadge.daysLeft)
        assertEquals("امروز سررسید است", todayBadge.text)

        // 4 days in the past (overdue)
        val overdueDue = now - 4 * oneDayMs
        val overdueBadge = installmentDueBadge(overdueDue, now)
        assertEquals(UrgencyLevel.OVERDUE, overdueBadge.level)
        assertTrue(overdueBadge.daysLeft in -5..-3)
        assertTrue(overdueBadge.text.contains("سررسید گذشته"))
    }
}
