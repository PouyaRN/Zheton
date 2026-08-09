package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 16 — "corrections that stick". The pure seams are what carry the guarantee: a tombstone
 * set that is a fixpoint, a ref→hash recovery that never mis-reads, and the fold skip function
 * that both the ledger and the fold listen to. No test here cares how the stone is stored.
 */
class CorrectionsStickTest {

    @Test
    fun `the tombstone set is a fixpoint - adding the same hash twice is harmless`() {
        val a = deletedSourceHashes("abc\n")
        val again = deletedSourceHashes("abc\nabc\ndef\n")
        assertEquals(setOf("abc"), a)
        assertEquals(setOf("abc", "def"), again)
        // Re-adding a hash already present yields the same set.
        assertEquals(again, again + setOf("abc"))
    }

    @Test
    fun `an empty or missing key reads as the empty set, not a crash`() {
        assertEquals(emptySet<String>(), deletedSourceHashes(null))
        assertEquals(emptySet<String>(), deletedSourceHashes("\n\n"))
        assertEquals(emptySet<String>(), deletedSourceHashes(""))
    }

    @Test
    fun `a ref recovers exactly the source hash it was built from`() {
        val hash = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1b2"
        assertEquals(hash, srcHashOfRef("s:$hash:0"))
        assertEquals(hash, srcHashOfRef("s:$hash:3"))
        assertNull(srcHashOfRef(""))
        assertNull(srcHashOfRef("m:some-manual-id"))
        assertNull(srcHashOfRef("txn:other"))
    }

    @Test
    fun `refs are never mis-parsed even when the hash contains no colon`() {
        // sha256 hashes have no ':', but the recovery must not assume anything about length.
        val hash = "abc123"
        assertEquals("abc123", srcHashOfRef("s:abc123:0"))
    }

    @Test
    fun `the skip set is tombstones plus every hidden ref's source`() {
        val tomb = setOf("tombstone-hash")
        val hides = setOf("s:hidden-a:0", "s:hidden-b:3", "s:hidden-a:55")
        val skips = sourceSkips(hides, tomb)
        assertEquals(setOf("tombstone-hash", "hidden-a", "hidden-b"), skips)
    }

    @Test
    fun `the fold skip predicate denies each kind of re-entry`() {
        val tomb = setOf("dead-as-a-doornail")
        val hides = setOf("s:hide-me:0")
        val skips = sourceSkips(hides, tomb)

        // An ordinary message never seen before is allowed.
        assertFalse("fresh-hash" in skips)
        // The tombstoned message stays forbidden no matter how often the inbox delivers it.
        assertTrue("dead-as-a-doornail" in skips)
        // The hidden transaction's message is forbidden too.
        assertTrue("hide-me" in skips)
        // A different seq of the same hidden source is still the same source.
        assertEquals(setOf("hide-a", "hide-b"), sourceSkips(setOf("s:hide-a:0", "s:hide-b:44")))
    }

    @Test
    fun `empty inputs keep the seeding empty - the fold stays silent`() {
        assertEquals(emptySet<String>(), sourceSkips(emptySet()))
    }

    @Test
    fun `a tombstone survives a rewind of the same message`() {
        // The two-step replay that used to resurrect a row: ingest(A) → derive → delete →
        // rewind → ingest again. The pure predicate is the seam: the second ingest must see
        // nothing to re-admit.
        val sender = "98912"
        val body = "برداشت از حساب شما مبلغ 500000 ریال"
        val at = 1_700_000_000_000L
        val hash = srcHash(sender, body, at)
        val skips = sourceSkips(emptySet(), deletedSourceHashes("$hash\n"))
        assertTrue(hash in skips)
    }
}