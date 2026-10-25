package com.doxigo.muchtoman.features.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The primitives, pinned against published answers rather than against themselves.
 *
 * The PBKDF2 vectors below are the widely published PBKDF2-HMAC-SHA256 vectors (the same
 * `password`/`salt` inputs as RFC 6070, with SHA-256 instead of SHA-1). They are the only way
 * to know that a hand-written construction — necessary because API 24 has no
 * `PBKDF2WithHmacSHA256` — is actually RFC 8018 and not merely consistent with itself.
 */
class BackupCryptoTest {

    private fun hex(bytes: ByteArray) = BackupCrypto.toHex(bytes)

    @Test
    fun `pbkdf2 matches published hmac-sha256 vectors`() {
        val password = "password".toByteArray(Charsets.UTF_8)
        val salt = "salt".toByteArray(Charsets.UTF_8)
        assertEquals(
            "120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
            hex(BackupCrypto.pbkdf2(password, salt, 1)),
        )
        assertEquals(
            "ae4d0c95af6b46d32d0adff928f06dd02a303f8ef3c251dfd6e2d85a95474c43",
            hex(BackupCrypto.pbkdf2(password, salt, 2)),
        )
        assertEquals(
            "c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
            hex(BackupCrypto.pbkdf2(password, salt, 4096)),
        )
    }

    @Test
    fun `pbkdf2 output longer than one block matches the published vector`() {
        val derived = BackupCrypto.pbkdf2(
            "passwordPASSWORDpassword".toByteArray(Charsets.UTF_8),
            "saltSALTsaltSALTsaltSALTsaltSALTsalt".toByteArray(Charsets.UTF_8),
            4096,
            40,
        )
        assertEquals(
            "348c89dbcbd32b2f32d814b8116e84cf2b17347ebc1800181c4e2a1fb8dd53e1c635518c7dac47e9",
            hex(derived),
        )
    }

    @Test
    fun `kdf is deterministic for the same salt and iteration count`() {
        val salt = ByteArray(16) { it.toByte() }
        val a = BackupCrypto.deriveKey("رمز عبور من".toCharArray(), salt, 2_000)
        val b = BackupCrypto.deriveKey("رمز عبور من".toCharArray(), salt, 2_000)
        assertArrayEquals(a, b)
        assertEquals(32, a.size)
    }

    @Test
    fun `kdf differs for salt, passphrase, and iteration count`() {
        val salt = ByteArray(16) { it.toByte() }
        val base = BackupCrypto.deriveKey("correct horse".toCharArray(), salt, 1_000)
        assertFalse(
            base.contentEquals(
                BackupCrypto.deriveKey("correct horse".toCharArray(), salt.copyOf().also { it[0] = 9 }, 1_000)
            )
        )
        assertFalse(base.contentEquals(BackupCrypto.deriveKey("correct horsE".toCharArray(), salt, 1_000)))
        assertFalse(base.contentEquals(BackupCrypto.deriveKey("correct horse".toCharArray(), salt, 1_001)))
    }

    @Test
    fun `aes-gcm round trip needs the same aad`() {
        val key = ByteArray(32) { (it * 7).toByte() }
        val nonce = ByteArray(12) { it.toByte() }
        val aad = "header-bytes".toByteArray(Charsets.UTF_8)
        val plaintext = "دفتر من".toByteArray(Charsets.UTF_8)

        val sealed = BackupCrypto.seal(plaintext, key, nonce, aad)
        assertArrayEquals(plaintext, BackupCrypto.open(sealed, key, nonce, aad))
        assertNull(BackupCrypto.open(sealed, key, nonce, "other-header".toByteArray()))
        assertNull(BackupCrypto.open(sealed, key, nonce.copyOf().also { it[0] = 1 }, aad))
        assertNull(BackupCrypto.open(sealed, ByteArray(32) { 1 }, nonce, aad))
    }

    @Test
    fun `a changed ciphertext byte is refused`() {
        val key = ByteArray(32) { 3 }
        val nonce = ByteArray(12) { 4 }
        val sealed = BackupCrypto.seal("hello".toByteArray(), key, nonce, ByteArray(0))
        sealed[sealed.size / 2] = (sealed[sealed.size / 2].toInt() xor 1).toByte()
        assertNull(BackupCrypto.open(sealed, key, nonce, ByteArray(0)))
    }

    @Test
    fun `a fresh nonce produces different ciphertext for the same plaintext`() {
        val key = ByteArray(32) { 5 }
        val plaintext = "same".toByteArray()
        val a = BackupCrypto.seal(plaintext, key, BackupCrypto.randomBytes(12), ByteArray(0))
        val b = BackupCrypto.seal(plaintext, key, BackupCrypto.randomBytes(12), ByteArray(0))
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `passphrase cannot be empty and salt must be sixteen bytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.deriveKey(CharArray(0), ByteArray(16), 1_000)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.deriveKey("long enough".toCharArray(), ByteArray(8), 1_000)
        }
    }

    @Test
    fun `iteration count is bounded`() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.pbkdf2("password".toByteArray(), "salt".toByteArray(), 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.pbkdf2(
                "password".toByteArray(),
                "salt".toByteArray(),
                BackupCrypto.KDF_MAX_ITERATIONS + 1,
            )
        }
    }

    @Test
    fun `a short key is never accepted`() {
        val key = ByteArray(16) { 1 }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.seal("x".toByteArray(), key, ByteArray(12), ByteArray(0))
        }
        assertNull(BackupCrypto.open("x".toByteArray(), key, ByteArray(12), ByteArray(0)))
    }

    @Test
    fun `sha256 is the ordinary digest`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            hex(BackupCrypto.sha256(ByteArray(0))),
        )
        assertNotNull(BackupCrypto.randomBytes(16))
        assertEquals(16, BackupCrypto.randomBytes(16).size)
        assertTrue(BackupCrypto.KDF_ITERATIONS >= 600_000)
    }
}
