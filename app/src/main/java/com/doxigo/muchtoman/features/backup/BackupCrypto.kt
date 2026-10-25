package com.doxigo.muchtoman.features.backup

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The two cryptographic primitives the backup needs, and nothing else.
 *
 * ## Passphrase → key
 *
 * PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte random salt, 32-byte output.
 *
 * **Deviation from the plan, deliberate and documented:** the wayfinder spec asks for
 * Argon2id. There is no Argon2id implementation on Android without adding a Gradle dependency,
 * and `BackupCrypto` may not add one. Argon2id's advantage over PBKDF2 is memory-hardness —
 * GPU/ASIC attackers get less of a discount. PBKDF2 at 600k iterations is the strongest
 * parameter we can ship without a dependency; it is what OWASP's current guidance names as the
 * PBKDF2-HMAC-SHA256 floor, and 600k is the number they give for that construction. If an
 * Argon2id implementation ever lands, [KDF_ID_ARGON2ID] is reserved for it and the envelope
 * carries the algorithm id, so old files keep opening.
 *
 * The PBKDF2 loop is written out over `Mac HmacSHA256` rather than calling
 * `SecretKeyFactory("PBKDF2WithHmacSHA256")`, because that algorithm name is only present on
 * API 26+ and this app's `minSdk` is 24. The hand-written loop is the RFC 8018 construction
 * and is pinned against published PBKDF2-HMAC-SHA256 vectors in `BackupCryptoTest`.
 *
 * ## Plaintext → ciphertext
 *
 * AES-256-GCM, 128-bit tag, a fresh 12-byte random nonce per file, and the 26-byte envelope
 * header as additional authenticated data. Binding the header means the KDF id, iteration
 * count and salt in a file cannot be swapped without the tag failing — the parameters are
 * authenticated rather than merely read.
 *
 * A passphrase is never stored, never written to a log line, and never leaves the process.
 * Callers own the `CharArray` and should clear it once the export/import has finished; this
 * object cannot do it for them because the bytes have to exist here while the KDF runs.
 */
object BackupCrypto {

    const val KDF_ID_PBKDF2_SHA256: Int = 1
    /** Reserved; not produced by this build. See the class comment. */
    const val KDF_ID_ARGON2ID: Int = 2

    const val KDF_NAME_PBKDF2_SHA256: String = "pbkdf2-hmac-sha256"
    const val KDF_ITERATIONS: Int = 600_000
    /**
     * Upper bound accepted from a file. A crafted backup could otherwise name four billion
     * iterations and make the app chew battery for an hour opening it. The bound is not a
     * security parameter for our own exports: `BackupFile.create` always writes [KDF_ITERATIONS].
     */
    const val KDF_MAX_ITERATIONS: Int = 2_000_000

    const val SALT_BYTES: Int = 16
    const val NONCE_BYTES: Int = 12
    const val KEY_BYTES: Int = 32
    const val TAG_BITS: Int = 128
    const val SHA256_BYTES: Int = 32

    private val random by lazy { SecureRandom() }

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also { random.nextBytes(it) }

    /**
     * PBKDF2-HMAC-SHA256. Kept public so the KDF itself can be pinned against published test
     * vectors, which is the only way to know a hand-rolled construction is the real one.
     */
    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, outputBytes: Int = KEY_BYTES): ByteArray {
        require(iterations in 1..KDF_MAX_ITERATIONS) { "bad iteration count" }
        require(outputBytes in 1..1024) { "bad output length" }
        require(password.isNotEmpty()) { "empty password" }
        require(salt.isNotEmpty()) { "empty salt" }

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))

        val blockCount = (outputBytes + SHA256_BYTES - 1) / SHA256_BYTES
        val out = ByteArray(blockCount * SHA256_BYTES)
        val counter = ByteArray(4)

        for (block in 1..blockCount) {
            counter[0] = (block ushr 24).toByte()
            counter[1] = (block ushr 16).toByte()
            counter[2] = (block ushr 8).toByte()
            counter[3] = block.toByte()

            mac.update(salt)
            mac.update(counter)
            var u = mac.doFinal()
            val t = u.copyOf()
            for (round in 2..iterations) {
                // doFinal() resets the Mac to its initialised state, so this is the
                // RFC 8018 XOR accumulation without re-initialising per round.
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            System.arraycopy(t, 0, out, (block - 1) * SHA256_BYTES, SHA256_BYTES)
        }
        return out.copyOf(outputBytes)
    }

    /** The passphrase is UTF-8 encoded, once, here. Never logged, never persisted. */
    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = KDF_ITERATIONS): ByteArray {
        require(passphrase.isNotEmpty()) { "empty passphrase" }
        require(salt.size == SALT_BYTES) { "salt must be $SALT_BYTES bytes" }
        val password = String(passphrase).toByteArray(Charsets.UTF_8)
        try {
            return pbkdf2(password, salt, iterations, KEY_BYTES)
        } finally {
            password.fill(0)
        }
    }

    /**
     * AES-256-GCM with [aad] authenticated alongside the ciphertext.
     *
     * The returned bytes are ciphertext followed by the 16-byte tag, which is what
     * `Cipher.doFinal` produces and what the envelope stores.
     */
    fun seal(plaintext: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "key must be $KEY_BYTES bytes" }
        require(nonce.size == NONCE_BYTES) { "nonce must be $NONCE_BYTES bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, nonce),
        )
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(plaintext)
    }

    /**
     * Null means "this key does not open this ciphertext", which is the ordinary answer for a
     * wrong passphrase and for a byte that has been changed in transit. The two are
     * deliberately indistinguishable: a caller who can tell them apart is a caller who can be
     * used as a decryption oracle.
     */
    fun open(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray): ByteArray? {
        if (key.size != KEY_BYTES || nonce.size != NONCE_BYTES) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, nonce),
            )
            if (aad.isNotEmpty()) cipher.updateAAD(aad)
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    /** Lowercase hex. Local to this package so the backup format has no formatting surprises. */
    fun toHex(bytes: ByteArray): String {
        val hex = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            for (byte in bytes) {
                val value = byte.toInt() and 0xff
                append(hex[value ushr 4])
                append(hex[value and 0x0f])
            }
        }
    }
}
