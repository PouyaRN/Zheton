package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The حبه compose/split round-trips exactly; nothing here may drift by a milligram. */
class GoldInputTest {

    @Test
    fun `kernels and grams compose`() {
        assertEquals(0.36, GoldInput.totalGrams("0", "3")!!, 1e-9)
        assertEquals(0.41, GoldInput.totalGrams("0.05", "3")!!, 1e-9)
        assertEquals(1.2, GoldInput.totalGrams("1.2", null)!!, 1e-9)
        assertEquals(1.2, GoldInput.totalGrams(null, "10")!!, 1e-9)
    }

    @Test
    fun `persian digits and separators parse like the money fields`() {
        assertEquals(0.12, GoldInput.totalGrams("٫۱۲", null)!!, 1e-9) // persian decimal only
        assertEquals(1.68, GoldInput.totalGrams("۱٫۲", "۴")!!, 1e-9) // 1.2 + 4×۰٫۱۲
    }

    @Test
    fun `blank fields count as zero not null`() {
        assertEquals(0.24, GoldInput.totalGrams("", "2")!!, 1e-9)
        assertEquals(0.0, GoldInput.totalGrams(null, null)!!, 1e-9)
    }

    @Test
    fun `garbage is rejected`() {
        assertNull(GoldInput.totalGrams("abc", null))
        assertNull(GoldInput.totalGrams("1", "x"))
    }

    @Test
    fun `split round-trips`() {
        val (kernels, grams) = GoldInput.split(2.54)
        assertEquals(21, kernels)
        assertEquals(0.02, grams, 1e-9)
        assertEquals(2.54, GoldInput.totalGrams("0.02", "21")!!, 1e-9)
    }

    @Test
    fun `split of exact multiples leaves no dust`() {
        val (kernels, grams) = GoldInput.split(2.4)
        assertEquals(20, kernels)
        assertEquals(0.0, grams, 1e-9)
    }

    @Test
    fun `persian summary reads naturally`() {
        assertEquals("۲۱ حبه و ۰٫۰۲ گرم", GoldInput.splitFa(2.54))
        assertEquals("۲۰ حبه", GoldInput.splitFa(2.4))
    }
}
