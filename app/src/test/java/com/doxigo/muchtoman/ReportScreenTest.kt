package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportScreenTest {

    private fun cat(id: String, now: Long, before: Long, total: Long) = CategoryChange(
        categoryId = id, nameFa = id, spentRial = now, previousRial = before,
        share = now.toDouble() / total,
        change = if (before > 0) (now - before).toDouble() / before else null,
    )

    @Test
    fun `five or fewer categories are all shown with real shares`() {
        val cats = listOf(cat("a", 60, 50, 100), cat("b", 40, 0, 100))
        val parts = splitParts(cats)
        assertEquals(listOf("a", "b"), parts.map { it.categoryId })
        assertEquals(0.6, parts[0].share, 1e-9)
    }

    @Test
    fun `more than five merges the tail into one rest part`() {
        val total = 280L
        val cats = (1..7).map { cat("c$it", (80 - it * 10).toLong(), 10, total) }
        val parts = splitParts(cats)
        assertEquals(5, parts.size)
        assertNull(parts.last().categoryId)
        assertEquals(REST_NAME, parts.last().nameFa)
        assertEquals(30L + 20L + 10L, parts.last().spentRial)
        assertEquals(1.0, parts.sumOf { it.share }, 1e-9)
        // 60 now against 30 before: +100%.
        assertEquals(1.0, parts.last().change!!, 1e-9)
    }

    @Test
    fun `change label is signed, rounded and silent for none`() {
        assertEquals("+۱۴٪ از شهریور", changeLabel(0.14, "شهریور"))
        assertEquals("−۳۱٪ از شهریور", changeLabel(-0.312, "شهریور"))
        assertNull(changeLabel(null, "شهریور"))
        assertNull(changeLabel(0.001, "شهریور"))
    }

    @Test
    fun `privacy hides money but keeps percents`() {
        val s = "مهر رو با ۴٫۴ میلیون پس‌انداز بستی. خرج رستوران ۳۱٪ کم شد"
        val masked = maskMoneyInSentence(s, privacy = true)
        assertTrue(masked.contains("••••"))
        assertTrue(!masked.contains("میلیون"))
        assertTrue(masked.contains("۳۱٪"))
        assertEquals(s, maskMoneyInSentence(s, privacy = false))
    }

    @Test
    fun `caption uses ungrouped Persian year`() {
        assertEquals("مهر ۱۴۰۵ در یک نگاه", reportCaption(ReportMonth(1405, 7)))
    }

    @Test
    fun `month ordinal round-trips across a year`() {
        val m = ReportMonth(1405, 12)
        assertEquals(m.next().ordinal(), m.ordinal() + 1)
        val o = m.next().ordinal()
        assertEquals(ReportMonth(1406, 1), ReportMonth(o / 12, o % 12 + 1))
    }
}
