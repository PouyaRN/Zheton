package com.doxigo.muchtoman.features.backup

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The versioned binary container a backup travels in: `*.mtbackup`.
 *
 * ```
 * offset  size  field
 *      0     4   magic "MTBK"
 *      4     1   envelope version (1)
 *      5     1   KDF id (1 = PBKDF2-HMAC-SHA256, 2 reserved for Argon2id)
 *      6     4   KDF iterations, unsigned big-endian
 *     10    16   salt
 *     26    12   GCM nonce
 *     38     4   ciphertext length, unsigned big-endian
 *     42     n   AES-256-GCM ciphertext ‖ 128-bit tag
 *   42+n    32   SHA-256 of the plaintext
 * ```
 *
 * The **26 bytes up to and including the salt are the header, and they are the AES-GCM
 * additional authenticated data**. Everything that influences how the key is derived is
 * therefore covered by the tag: an attacker cannot weaken a file by rewriting its iteration
 * count, and cannot make a file be opened with a different KDF, without the decrypt failing.
 *
 * The trailing SHA-256 is not what makes the file safe to open — GCM already refuses a forgery
 * — but it makes "the bytes are the bytes" checkable before the JSON parser sees them, and it
 * turns a stopped clock or a half-written transfer into a precise error instead of a confusing
 * parse failure.
 *
 * A byte array is used throughout rather than a stream because a backup is small (worst case:
 * thirteen months of bank SMS, a few megabytes) and an import must be validated as a whole
 * before any of it is applied.
 */
object BackupFile {

    const val EXTENSION: String = ".mtbackup"

    val MAGIC: ByteArray = byteArrayOf('M'.code.toByte(), 'T'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())

    const val ENVELOPE_VERSION: Int = 1
    const val HEADER_BYTES: Int = 26
    const val NONCE_OFFSET: Int = 26
    const val CIPHER_LENGTH_OFFSET: Int = 38
    const val CIPHER_OFFSET: Int = 42

    /**
     * A hard ceiling on what the importer will even read into memory. The codec caps the
     * plaintext at 64 MiB; 80 MiB leaves room for the envelope's own overhead without ever
     * letting a hostile file make the phone allocate without bound.
     */
    const val MAX_ENVELOPE_BYTES: Int = 80 * 1024 * 1024

    /** Minimum passphrase length this app will create a backup with. Imported files may be shorter. */
    const val MIN_PASSPHRASE_CHARS: Int = 8

    private const val CIPHER_LENGTH_BYTES = 4

    class BackupEnvelopeException(message: String) : Exception(message)

    data class EnvelopeHeader(
        val envelopeVersion: Int,
        val kdfId: Int,
        val iterations: Int,
        val salt: ByteArray,
    )

    sealed class OpenResult {
        data class Success(val plaintext: ByteArray) : OpenResult()

        /**
         * Wrong passphrase *or* a changed byte. One answer on purpose: distinguishing them
         * would turn this function into an oracle for either.
         */
        data object WrongPassphraseOrTampered : OpenResult()

        /** Structurally impossible: bad magic, future version, bad lengths, digest mismatch. */
        data class Invalid(val reason: String) : OpenResult()
    }

    fun suggestedFileName(now: Long = System.currentTimeMillis()): String {
        val format = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Tehran")
        }
        return "muchtoman-${format.format(Date(now))}$EXTENSION"
    }

    fun isEnvelope(bytes: ByteArray): Boolean =
        bytes.size >= HEADER_BYTES && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun readHeader(bytes: ByteArray): EnvelopeHeader {
        if (bytes.size < HEADER_BYTES) throw BackupEnvelopeException("file is too short to be a backup")
        if (bytes.size > MAX_ENVELOPE_BYTES) throw BackupEnvelopeException("file is larger than this app will open")
        if (!bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BackupEnvelopeException("not a muchtoman backup file")
        }
        val version = bytes[4].toInt() and 0xff
        if (version != ENVELOPE_VERSION) {
            throw BackupEnvelopeException("backup container version $version is not supported by this build")
        }
        val kdfId = bytes[5].toInt() and 0xff
        if (kdfId != BackupCrypto.KDF_ID_PBKDF2_SHA256) {
            throw BackupEnvelopeException("backup uses an unknown key-derivation function")
        }
        val iterations = readInt(bytes, 6)
        if (iterations !in 1..BackupCrypto.KDF_MAX_ITERATIONS) {
            throw BackupEnvelopeException("backup names an iteration count this app will not run")
        }
        if (bytes.size < HEADER_BYTES) throw BackupEnvelopeException("truncated header")
        val salt = bytes.copyOfRange(10, 10 + BackupCrypto.SALT_BYTES)
        return EnvelopeHeader(version, kdfId, iterations, salt)
    }

    /**
     * Encrypt [plaintext] under a key derived from [passphrase].
     *
     * [iterations], [salt] and [nonce] are parameters so tests can run a real envelope round
     * trip without paying 600,000 PBKDF2 iterations per case. Production callers use the
     * defaults, which are [BackupCrypto.KDF_ITERATIONS] and fresh random bytes.
     */
    fun create(
        plaintext: ByteArray,
        passphrase: CharArray,
        iterations: Int = BackupCrypto.KDF_ITERATIONS,
        salt: ByteArray = BackupCrypto.randomBytes(BackupCrypto.SALT_BYTES),
        nonce: ByteArray = BackupCrypto.randomBytes(BackupCrypto.NONCE_BYTES),
    ): ByteArray {
        require(plaintext.isNotEmpty()) { "refusing to write an empty backup" }
        // The importer refuses anything above this, so writing it would produce a file this
        // app could not read back. Fail at export, where the screen can say so, instead.
        require(plaintext.size <= MAX_PAYLOAD_BYTES) { "backup payload is too large" }
        require(passphrase.size >= MIN_PASSPHRASE_CHARS) { "passphrase must be at least $MIN_PASSPHRASE_CHARS characters" }
        require(iterations in 1..BackupCrypto.KDF_MAX_ITERATIONS) { "bad iteration count" }
        require(salt.size == BackupCrypto.SALT_BYTES) { "salt must be ${BackupCrypto.SALT_BYTES} bytes" }
        require(nonce.size == BackupCrypto.NONCE_BYTES) { "nonce must be ${BackupCrypto.NONCE_BYTES} bytes" }

        val header = buildHeader(iterations, salt)
        val key = BackupCrypto.deriveKey(passphrase, salt, iterations)
        try {
            val ciphertext = BackupCrypto.seal(plaintext, key, nonce, header)
            val digest = BackupCrypto.sha256(plaintext)

            val out = ByteArrayOutputStream(header.size + nonce.size + CIPHER_LENGTH_BYTES + ciphertext.size + digest.size)
            out.write(header)
            out.write(nonce)
            writeInt(out, ciphertext.size)
            out.write(ciphertext)
            out.write(digest)
            return out.toByteArray()
        } finally {
            key.fill(0)
        }
    }

    /**
     * Open a file. Structural problems are reported as [OpenResult.Invalid]; a cryptographically
     * valid file the passphrase cannot open is [OpenResult.WrongPassphraseOrTampered].
     */
    fun open(bytes: ByteArray, passphrase: CharArray): OpenResult {
        val header = try {
            readHeader(bytes)
        } catch (e: BackupEnvelopeException) {
            return OpenResult.Invalid(e.message ?: "invalid backup")
        }
        val key = try {
            BackupCrypto.deriveKey(passphrase, header.salt, header.iterations)
        } catch (_: IllegalArgumentException) {
            return OpenResult.Invalid("empty passphrase")
        }
        try {
            val cipherLength = readInt(bytes, CIPHER_LENGTH_OFFSET)
            if (cipherLength <= 0) return OpenResult.Invalid("empty ciphertext")
            val expected = CIPHER_OFFSET + cipherLength + BackupCrypto.SHA256_BYTES
            if (bytes.size != expected) return OpenResult.Invalid("file length does not match its header")

            val nonce = bytes.copyOfRange(NONCE_OFFSET, NONCE_OFFSET + BackupCrypto.NONCE_BYTES)
            val ciphertext = bytes.copyOfRange(CIPHER_OFFSET, CIPHER_OFFSET + cipherLength)
            val storedDigest = bytes.copyOfRange(CIPHER_OFFSET + cipherLength, bytes.size)
            val aad = bytes.copyOfRange(0, HEADER_BYTES)

            val plaintext = BackupCrypto.open(ciphertext, key, nonce, aad)
                ?: return OpenResult.WrongPassphraseOrTampered
            if (!MessageDigest.isEqual(BackupCrypto.sha256(plaintext), storedDigest)) {
                // Authenticated decryption succeeded, so this cannot happen for a file this
                // app wrote; it means the format was produced by something else.
                return OpenResult.Invalid("plaintext digest mismatch")
            }
            return OpenResult.Success(plaintext)
        } finally {
            key.fill(0)
        }
    }

    private fun buildHeader(iterations: Int, salt: ByteArray): ByteArray {
        val header = ByteArray(HEADER_BYTES)
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.size)
        header[4] = ENVELOPE_VERSION.toByte()
        header[5] = BackupCrypto.KDF_ID_PBKDF2_SHA256.toByte()
        header[6] = (iterations ushr 24).toByte()
        header[7] = (iterations ushr 16).toByte()
        header[8] = (iterations ushr 8).toByte()
        header[9] = iterations.toByte()
        System.arraycopy(salt, 0, header, 10, BackupCrypto.SALT_BYTES)
        return header
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        if (offset < 0 || offset + 4 > bytes.size) {
            throw BackupEnvelopeException("truncated header")
        }
        return ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xff)
        out.write((value ushr 16) and 0xff)
        out.write((value ushr 8) and 0xff)
        out.write(value and 0xff)
    }
}
