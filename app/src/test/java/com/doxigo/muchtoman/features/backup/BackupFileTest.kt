package com.doxigo.muchtoman.features.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFileTest {

    private val passphrase = "correct horse battery".toCharArray()
    private val plaintext = """{"schemaVersion":1,"note":"دفتر"}""".toByteArray(Charsets.UTF_8)
    private val fixedSalt = ByteArray(BackupCrypto.SALT_BYTES) { (it + 1).toByte() }
    private val fixedNonce = ByteArray(BackupCrypto.NONCE_BYTES) { (it + 11).toByte() }

    /** Low iteration count: these tests exercise the envelope, not the KDF's cost. */
    private fun envelope(): ByteArray = BackupFile.create(
        plaintext = plaintext,
        passphrase = passphrase,
        iterations = 1_000,
        salt = fixedSalt.copyOf(),
        nonce = fixedNonce.copyOf(),
    )

    @Test
    fun `envelope starts with the magic and reports its header`() {
        val bytes = envelope()
        assertTrue(BackupFile.isEnvelope(bytes))
        assertArrayEquals("MTBK".toByteArray(), bytes.copyOfRange(0, 4))

        val header = BackupFile.readHeader(bytes)
        assertEquals(BackupFile.ENVELOPE_VERSION, header.envelopeVersion)
        assertEquals(BackupCrypto.KDF_ID_PBKDF2_SHA256, header.kdfId)
        assertEquals(1_000, header.iterations)
        assertArrayEquals(fixedSalt, header.salt)
        assertEquals(BackupFile.HEADER_BYTES, header.salt.size + 10)
    }

    @Test
    fun `envelope length is header, nonce, length, ciphertext and digest`() {
        val bytes = envelope()
        val cipherLength = readInt(bytes, BackupFile.CIPHER_LENGTH_OFFSET)
        assertEquals(plaintext.size + 16, cipherLength) // plaintext + GCM tag
        assertEquals(
            BackupFile.CIPHER_OFFSET + cipherLength + BackupCrypto.SHA256_BYTES,
            bytes.size,
        )
    }

    @Test
    fun `round trip with the right passphrase`() {
        val result = BackupFile.open(envelope(), passphrase)
        assertTrue(result is BackupFile.OpenResult.Success)
        assertArrayEquals(plaintext, (result as BackupFile.OpenResult.Success).plaintext)
    }

    @Test
    fun `wrong passphrase is a single refusal`() {
        assertEquals(
            BackupFile.OpenResult.WrongPassphraseOrTampered,
            BackupFile.open(envelope(), "wrong horse battery".toCharArray()),
        )
    }

    @Test
    fun `any changed ciphertext byte is refused`() {
        val bytes = envelope()
        val cipherLength = readInt(bytes, BackupFile.CIPHER_LENGTH_OFFSET)
        val index = BackupFile.CIPHER_OFFSET + cipherLength / 2
        bytes[index] = (bytes[index].toInt() xor 0x01).toByte()
        assertEquals(
            BackupFile.OpenResult.WrongPassphraseOrTampered,
            BackupFile.open(bytes, passphrase),
        )
    }

    @Test
    fun `a changed header byte is refused because the header is authenticated`() {
        val bytes = envelope()
        // Flip one salt byte. That changes both the derived key and the AAD, so neither the
        // key nor the tag can rescue it.
        bytes[10] = (bytes[10].toInt() xor 0x01).toByte()
        val result = BackupFile.open(bytes, passphrase)
        assertTrue(result is BackupFile.OpenResult.WrongPassphraseOrTampered)
    }

    @Test
    fun `a changed stored digest is reported as invalid rather than undecryptable`() {
        val bytes = envelope()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        val result = BackupFile.open(bytes, passphrase)
        assertTrue(result is BackupFile.OpenResult.Invalid)
        assertTrue((result as BackupFile.OpenResult.Invalid).reason.contains("digest"))
    }

    @Test
    fun `magic, container version, and ciphertext length are checked`() {
        val notABackup = envelope().also { it[0] = 'X'.code.toByte() }
        assertTrue(BackupFile.open(notABackup, passphrase) is BackupFile.OpenResult.Invalid)

        val futureContainer = envelope().also { it[4] = 2 }
        assertTrue(BackupFile.open(futureContainer, passphrase) is BackupFile.OpenResult.Invalid)

        val unknownKdf = envelope().also { it[5] = 99 }
        assertTrue(BackupFile.open(unknownKdf, passphrase) is BackupFile.OpenResult.Invalid)

        val truncated = envelope().let { it.copyOf(it.size - 1) }
        assertTrue(BackupFile.open(truncated, passphrase) is BackupFile.OpenResult.Invalid)

        val trailingGarbage = envelope() + byteArrayOf(0)
        assertTrue(BackupFile.open(trailingGarbage, passphrase) is BackupFile.OpenResult.Invalid)
    }

    @Test
    fun `an impossible iteration count is refused before the kdf runs`() {
        val bytes = envelope()
        // 3,000,000, above BackupCrypto.KDF_MAX_ITERATIONS.
        bytes[6] = 0
        bytes[7] = 0x2D
        bytes[8] = 0xC6.toByte()
        bytes[9] = 0xC0.toByte()
        val result = BackupFile.open(bytes, passphrase)
        assertTrue(result is BackupFile.OpenResult.Invalid)
        assertEquals(3_000_000, readInt(bytes, 6))
    }

    @Test
    fun `too short and too large files are refused`() {
        assertTrue(BackupFile.open(ByteArray(3), passphrase) is BackupFile.OpenResult.Invalid)
        assertFalse(BackupFile.isEnvelope(ByteArray(3)))
        assertThrows(IllegalArgumentException::class.java) {
            BackupFile.create(
                plaintext = plaintext,
                passphrase = "short".toCharArray(),
                iterations = 1_000,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupFile.create(ByteArray(0), passphrase, 1_000)
        }
    }

    @Test
    fun `two exports of the same payload differ because salt and nonce are random`() {
        val a = BackupFile.create(plaintext, passphrase, iterations = 1_000)
        val b = BackupFile.create(plaintext, passphrase, iterations = 1_000)
        assertFalse(a.contentEquals(b))
        assertTrue(BackupFile.isEnvelope(a))
    }

    @Test
    fun `suggested file name carries the extension and a sortable stamp`() {
        val name = BackupFile.suggestedFileName(1_700_000_000_000)
        assertTrue(name.startsWith("muchtoman-"))
        assertTrue(name.endsWith(BackupFile.EXTENSION))
        assertEquals(32, name.length)
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)
}
