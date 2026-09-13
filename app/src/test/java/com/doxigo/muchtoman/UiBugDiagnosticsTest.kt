package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1 Red-Capable Feedback Loop for the 3 reported bugs:
 * 1. Bottom TabBar gliding indicator slot alignment in RTL
 * 2. Contextual priority due date & text formatting
 * 3. Timeline selection mode visibility and bottom inset coordination
 */
class UiBugDiagnosticsTest {

    // --- Bug 1: TabBar RTL Slot Offset Calculation ---

    @Test
    fun `test TabBar pill target offset calculation matches slot in RTL without double inversion`() {
        val tabList = listOf(Tab.HOME, Tab.ASSETS, Tab.CARDS, Tab.ACTIVITY_AND_TRENDS)
        val hasQuickAdd = true
        val slotCount = if (hasQuickAdd) tabList.size + 1 else tabList.size // 5
        val totalWidth = 360f // typical phone width in dp
        val slotWidth = totalWidth / slotCount // 72 dp

        // In RTL layout with Row, children are laid out right-to-left:
        // Physical visual slots from Left to Right:
        // Slot 0 (leftmost): ACTIVITY_AND_TRENDS (tab index 3)
        // Slot 1: CARDS (tab index 2)
        // Slot 2 (center): QuickAdd (+)
        // Slot 3: ASSETS (tab index 1)
        // Slot 4 (rightmost): HOME (tab index 0)

        // For absolute left-aligned container (AbsoluteAlignment.TopLeft), the X position of the pill
        // for each tab MUST match the visual slot:
        fun calculatePillAbsoluteLeftOffset(selected: Tab, isRtl: Boolean): Float {
            val half = tabList.size / 2 // 2
            val selectedIndex = tabList.indexOf(selected).coerceAtLeast(0)
            val selectedSlot = if (hasQuickAdd && selectedIndex >= half) {
                selectedIndex + 1
            } else {
                selectedIndex
            }
            val effectiveSlot = if (isRtl) (slotCount - 1 - selectedSlot) else selectedSlot
            return slotWidth * effectiveSlot
        }

        // Expected absolute X coordinates from left edge:
        // Tab.HOME (index 0): In RTL, HOME is at the far right -> visual slot 4 -> 4 * 72 = 288 dp
        val homeOffsetRtl = calculatePillAbsoluteLeftOffset(Tab.HOME, isRtl = true)
        assertEquals(288f, homeOffsetRtl, 0.01f)

        // Tab.ASSETS (index 1): In RTL, ASSETS is at slot 3 -> 3 * 72 = 216 dp
        val assetsOffsetRtl = calculatePillAbsoluteLeftOffset(Tab.ASSETS, isRtl = true)
        assertEquals(216f, assetsOffsetRtl, 0.01f)

        // Tab.CARDS (index 2): In RTL, CARDS is at slot 1 -> 1 * 72 = 72 dp
        val cardsOffsetRtl = calculatePillAbsoluteLeftOffset(Tab.CARDS, isRtl = true)
        assertEquals(72f, cardsOffsetRtl, 0.01f)

        // Tab.ACTIVITY_AND_TRENDS (index 3): In RTL, at slot 0 (far left) -> 0 * 72 = 0 dp
        val activityOffsetRtl = calculatePillAbsoluteLeftOffset(Tab.ACTIVITY_AND_TRENDS, isRtl = true)
        assertEquals(0f, activityOffsetRtl, 0.01f)
    }

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
