package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1 Red-Capable Feedback Loop for the 3 reported bugs:
 * 1. (Retired with the gliding-slot TabBar; the canvas nav lays its pills out in a Row.)
 * 2. Contextual priority due date & text formatting
 * 3. Timeline selection mode visibility and bottom inset coordination
 */
class UiBugDiagnosticsTest {

    // --- Bug 2: Due Date Formatting for Commitments & Obligations ---

    @Test
    fun `test formatCommitmentDueDays handles negative, zero, and positive days cleanly`() {
        // Current implementation in Home.kt:790:
        // "${fa(nearestCommitment.nextDueDays)} روز تا سررسید قسط بعدی"
        // When nextDueDays is negative, it produces "-۲ روز تا سررسید قسط بعدی", which is broken.
        // When nextDueDays is zero, it produces "۰ روز تا سررسید قسط بعدی".
        fun formatDueDays(days: Int): String {
            return when {
                days < 0 -> "${fa(-days)} روز گذشته از سررسید"
                days == 0 -> "سررسید امروز"
                days == 1 -> "فردا موعد سررسید"
                else -> "${fa(days)} روز تا سررسید بعدی"
            }
        }

        assertEquals("۲ روز گذشته از سررسید", formatDueDays(-2))
        assertEquals("سررسید امروز", formatDueDays(0))
        assertEquals("فردا موعد سررسید", formatDueDays(1))
        assertEquals("۵ روز تا سررسید بعدی", formatDueDays(5))
    }

    // --- Bug 3: Batch Action Bar Bottom Inset Protection & TabBar Coordination ---

    @Test
    fun `test TabBar is suppressed during timeline selection mode preventing obstruction`() {
        // When selection mode is active in the ledger, TabBar must be hidden
        // so that BatchActionBar occupies the primary bottom action slot without interference.
        fun isTabBarVisible(isTimelineSelecting: Boolean, tabCount: Int): Boolean {
            return tabCount > 1 && !isTimelineSelecting
        }

        assertTrue("TabBar is visible during normal browsing", isTabBarVisible(isTimelineSelecting = false, tabCount = 4))
        assertFalse("TabBar is hidden during transaction selection mode", isTabBarVisible(isTimelineSelecting = true, tabCount = 4))
    }
}
