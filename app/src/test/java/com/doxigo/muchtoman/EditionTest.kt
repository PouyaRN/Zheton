package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tabs verification test.
 */
class EditionTest {

    @Test
    fun `every edition has دارایی, because that is the app lite is`() {
        assertTrue("دارایی missing from ${tabs.map { it.fa }}", Tab.ASSETS in tabs)
    }

    @Test
    fun `the tab the app opens on is one it has`() {
        assertTrue("no tabs at all", tabs.isNotEmpty())
        assertTrue("opens on a tab outside its own bar", tabs.first() in tabs)
    }

    @Test
    fun `the edition is the one this variant was built for`() {
        if (BuildConfig.LITE) {
            assertEquals(listOf(Tab.ASSETS), tabs)
        } else {
            assertEquals(Tab.entries, tabs)
            assertEquals(Tab.HOME, tabs.first())
        }
    }
}
