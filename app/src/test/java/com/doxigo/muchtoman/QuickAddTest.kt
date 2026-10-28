package com.doxigo.muchtoman

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickAddTest {

    @Test fun keypadIgnoresLeadingZeroAndCaps() {
        assertEquals("", quickAddDigit("", 0))
        assertEquals("", quickAddThousand(""))
        var d = quickAddDigit("", 1)
        d = quickAddDigit(d, 8)
        d = quickAddDigit(d, 5)
        d = quickAddThousand(d)
        assertEquals("185000", d)
        assertEquals("18500", quickAddBackspace(d))
        assertEquals("", quickAddBackspace(""))
        val full = "123456789012"
        assertEquals(full, quickAddDigit(full, 3))
        assertEquals("1234567890", quickAddThousand("1234567890"))
        assertEquals("123456789000", quickAddThousand("123456789"))
    }

    @Test fun whenLabelUsesTehranClock() {
        // 2026-10-26 07:12 UTC is 10:42 in Tehran.
        val at = 1_792_998_720_000L
        assertEquals("امروز · ۱۰:۴۲", quickAddWhen(at, at + 60_000L))
        assertEquals("دیروز · ۱۰:۴۲", quickAddWhen(at, at + DAY_MS))
    }

    private val accounts = listOf(
        QuickAddAccount("MELLAT", "ملت ۶۴۶۰", "م", Color.Red),
        QuickAddAccount(MANUAL_ACCOUNT_ID, "نقد", "ن", Color.Gray),
    )

    @Test fun expenseIsOneLegOnTheChosenAccount() {
        val legs = quickAddLegs(
            QuickAddDraft(QuickAddKind.EXPENSE, 1_850_000L, "MELLAT", categoryId = "cat_dining", at = 5L),
            accounts,
        )
        assertEquals(listOf(ManualLeg(1_850_000L, true, "cat_dining", "پرداخت", 5L, "MELLAT")), legs)
    }

    @Test fun transferIsTwoLegsMarkedAsTransfer() {
        val legs = quickAddLegs(
            QuickAddDraft(QuickAddKind.TRANSFER, 10_000L, "MELLAT", toAccountId = MANUAL_ACCOUNT_ID, at = 9L),
            accounts,
        )
        assertEquals(2, legs.size)
        assertTrue(legs.all { it.categoryId == CAT_TRANSFER && it.amountRial == 10_000L && it.at == 9L })
        assertEquals("MELLAT" to true, legs[0].accountId to legs[0].isExpense)
        assertEquals(MANUAL_ACCOUNT_ID to false, legs[1].accountId to legs[1].isExpense)
        assertEquals("انتقال به نقد", legs[0].merchant)
        assertEquals("انتقال از ملت ۶۴۶۰", legs[1].merchant)
    }

    @Test fun transferToItselfWritesNothing() {
        assertTrue(
            quickAddLegs(QuickAddDraft(QuickAddKind.TRANSFER, 10L, "MELLAT", toAccountId = "MELLAT", at = 1L), accounts).isEmpty(),
        )
    }

    @Test fun accountsEndWithCash() {
        val list = quickAddAccounts(listOf(BankAccount(bank = "MELLAT", mask = "6104****6460")))
        assertEquals(listOf("MELLAT", MANUAL_ACCOUNT_ID), list.map { it.id })
        assertEquals("ملت ۶۴۶۰", list[0].label)
    }
}
