package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the Accounts screen's copy pills copy, and when they have nothing to copy. */
class AccountsScreenTest {

    @Test
    fun `card copy takes the full PAN she entered, in ASCII digits`() {
        val a = BankAccount(bank = "MELLAT", mask = "6460", fullPan = "۶۱۰۴ ۳۳۷۵ ۹۱۲۶ ۴۶۰۷")
        assertEquals("6104337591264607", copyableCardNumber(a))
    }

    @Test
    fun `a mask that is only the last digits gives nothing to copy`() {
        assertNull(copyableCardNumber(BankAccount(bank = "MELLAT", mask = "6460")))
        assertNull(copyableCardNumber(BankAccount(bank = "SAMAN")))
    }

    @Test
    fun `a whole mask still counts as the card number`() {
        assertEquals("6219861012345678", copyableCardNumber(BankAccount(bank = "SAMAN", mask = "6219-8610-1234-5678")))
    }

    @Test
    fun `sheba is copied canonical and missing sheba gives nothing`() {
        assertEquals(
            "IR820540102680020817909002",
            copyableSheba(BankAccount(bank = "SAMAN", sheba = "ir82 0540 1026 8002 0817 9090 02")),
        )
        assertNull(copyableSheba(BankAccount(bank = "SAMAN")))
        assertNull(copyableSheba(BankAccount(bank = "SAMAN", sheba = "  ")))
    }

    @Test
    fun `list names drop the word bank`() {
        assertEquals("سامان", shortBankName(BankAccount(bank = "SAMAN")))
        assertEquals("ملی", shortBankName(BankAccount(bank = "MELLI")))
    }
}
