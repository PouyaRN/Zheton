package com.doxigo.muchtoman.features.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultRecoveryCodeTest {

    @Test
    fun `the wordlist is fixed, unique, lowercase and exactly 256 words`() {
        val words = VaultRecoveryCode.WORDS
        assertEquals(256, words.size)
        assertEquals(256, words.toSet().size)
        assertTrue(words.all { it.matches(Regex("[a-z]{3,8}")) })
        // Pinned by value, not just by size. The index is the wire format: reordering this list
        // silently turns every code already written on paper into a different key.
        assertEquals("acorn", words.first())
        assertEquals("ivory", words.last())
        assertEquals("apple", words[11])
    }

    @Test
    fun `a code parses back to the same key it was encoded from`() {
        val entropy = ByteArray(VaultRecoveryCode.ENTROPY_BYTES) { (it * 17 + 3).toByte() }
        val code = VaultRecoveryCode.encode(entropy)
        assertEquals(VaultRecoveryCode.WORD_COUNT, code.split(' ').size)

        val parsed = VaultRecoveryCode.parse(code)
        assertNotNull(parsed)
        assertArrayEquals(VaultRecoveryCode.keyFor(entropy).key, parsed!!.key)
        assertEquals(VaultRecoveryCode.idFor(parsed.key), parsed.id)
        assertTrue(parsed.id.matches(Regex("[a-f0-9]{64}")))
    }

    @Test
    fun `the same code with different case and spacing is the same key`() {
        val code = VaultRecoveryCode.encode(ByteArray(VaultRecoveryCode.ENTROPY_BYTES) { 7 })
        val messy = "  " + code.uppercase().replace(" ", "   ") + "\n"
        val a = VaultRecoveryCode.parse(code)
        val b = VaultRecoveryCode.parse(messy)
        assertNotNull(b)
        assertEquals(a, b)
        assertEquals(a!!.hashCode(), b!!.hashCode())
    }

    @Test
    fun `a mistyped word is rejected by the checksum`() {
        val entropy = ByteArray(VaultRecoveryCode.ENTROPY_BYTES)
        val words = VaultRecoveryCode.encode(entropy).split(' ').toMutableList()

        // Change the first word: different entropy, so the stored 12th word is now wrong.
        words[0] = VaultRecoveryCode.WORDS[1]
        val wrongEntropy = ByteArray(VaultRecoveryCode.ENTROPY_BYTES).also { it[0] = 1 }
        val expectedChecksum = BackupCrypto.sha256(wrongEntropy)[0].toInt() and 0xff
        val storedChecksum = VaultRecoveryCode.WORDS.indexOf(
            VaultRecoveryCode.encode(entropy).split(' ')[VaultRecoveryCode.ENTROPY_BYTES]
        )
        assertNotEquals("test premise: the typo must change the checksum", expectedChecksum, storedChecksum)
        assertNull(VaultRecoveryCode.parse(words.joinToString(" ")))
    }

    @Test
    fun `a wrong checksum word is rejected`() {
        val words = VaultRecoveryCode.encode(ByteArray(VaultRecoveryCode.ENTROPY_BYTES) { 5 })
            .split(' ')
            .toMutableList()
        val stored = VaultRecoveryCode.WORDS.indexOf(words[VaultRecoveryCode.ENTROPY_BYTES])
        words[VaultRecoveryCode.ENTROPY_BYTES] = VaultRecoveryCode.WORDS[(stored + 1) % 256]
        assertNull(VaultRecoveryCode.parse(words.joinToString(" ")))
    }

    @Test
    fun `wrong length, unknown words, and empty input are rejected`() {
        val code = VaultRecoveryCode.encode(ByteArray(VaultRecoveryCode.ENTROPY_BYTES) { 9 })
        val words = code.split(' ')
        assertNull(VaultRecoveryCode.parse(words.dropLast(1).joinToString(" ")))
        assertNull(VaultRecoveryCode.parse((words + words.first()).joinToString(" ")))
        assertNull(VaultRecoveryCode.parse(words.toMutableList().also { it[3] = "zzzz" }.joinToString(" ")))
        assertNull(VaultRecoveryCode.parse(""))
        assertNull(VaultRecoveryCode.parse("   "))
    }

    @Test
    fun `generate produces a code that parses and differs between calls`() {
        val a = VaultRecoveryCode.generate()
        val b = VaultRecoveryCode.generate()
        assertEquals(VaultRecoveryCode.WORD_COUNT, a.split(' ').size)
        assertNotNull(VaultRecoveryCode.parse(a))
        assertFalse(a == b)
    }

    @Test
    fun `the key object never prints its key material`() {
        val entropy = ByteArray(VaultRecoveryCode.ENTROPY_BYTES) { 42 }
        val parsed = VaultRecoveryCode.parse(VaultRecoveryCode.encode(entropy))!!
        val text = parsed.toString()
        assertEquals("VaultKey(${parsed.shortId}…)", text)
        assertFalse(text.contains(BackupCrypto.toHex(parsed.key)))
    }
}
