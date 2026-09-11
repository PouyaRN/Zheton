package com.doxigo.muchtoman

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerEngineTest {

    @Test
    fun `DecisionKind constants accurately define transaction override kinds`() {
        assertEquals("category", DecisionKind.CATEGORY)
        assertEquals("direction", DecisionKind.DIRECTION)
        assertEquals("hide", DecisionKind.HIDE)
        assertEquals("note", DecisionKind.NOTE)
        assertEquals("worth_it", DecisionKind.WORTH_IT)
    }

    @Test
    fun `DecisionKind matches active tombstone rule for hide overrides`() {
        // A HIDE decision with deleted = false is an active tombstone that hides the transaction
        val activeHide = TxnDecision(
            id = "test_hide_1",
            ref = "s:hash1:0",
            kind = DecisionKind.HIDE,
            value = "1",
            createdAt = 1000L,
            updatedAt = 1000L,
            deleted = false,
        )
        assertTrue(activeHide.active)
    }
}
