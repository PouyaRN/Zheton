package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization test asserting the public contract of UiState, Tab destinations,
 * and key feature interactions before decomposing Ui.kt and AppVm.
 */
class CharacterizationUiContractTest {

    @Test
    fun `default UiState contains expected core structures and lazy story`() {
        val state = UiState()
        assertEquals(emptyList<Holding>(), state.holdings)
        assertEquals(emptyList<BankAccount>(), state.bankAccounts)
        assertEquals(emptyList<Commitment>(), state.commitments)
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertEquals(CycleMode.JALALI_MONTH, state.cycleMode)
        assertEquals(1, state.cycleAnchorDay)
        assertNotNull("Story is lazily created without error", state.story)
        assertEquals(0.0, state.totals.toman, 0.001)
    }

    @Test
    fun `Tab enumeration covers the 5 canvas destinations`() {
        val tabs = Tab.entries
        val names = tabs.map { it.name }
        assertTrue("Contains HOME", names.contains("HOME"))
        assertTrue("Contains ASSETS / Portfolio", names.contains("ASSETS"))
        assertTrue("Contains CARDS", names.contains("CARDS"))
        assertTrue("Contains LEDGER", names.contains("LEDGER"))
        assertTrue("Contains REPORTS", names.contains("REPORTS"))
        assertEquals(5, tabs.size)
    }

    @Test
    fun `DecisionKind constants define valid user overrides`() {
        assertEquals("category", DecisionKind.CATEGORY)
        assertEquals("hide", DecisionKind.HIDE)
        assertEquals("direction", DecisionKind.DIRECTION)
        assertEquals("note", DecisionKind.NOTE)
        assertEquals("worth_it", DecisionKind.WORTH_IT)
        assertEquals("account", DecisionKind.ACCOUNT)
        assertEquals("exclude", DecisionKind.EXCLUDE)
    }
}
