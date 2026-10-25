package com.doxigo.muchtoman.features.backup

/**
 * The orchestration between the screen and the three pure pieces (codec, crypto, envelope).
 *
 * Keeping this out of the Activity is what makes the risky path testable without Android:
 * `export → inspect → restore` is exercised end to end on the JVM with a fake
 * [BackupSource]/[BackupTarget], at a deliberately low iteration count so the test suite is
 * not paying 600,000 PBKDF2 rounds per case. The production default is always
 * [BackupCrypto.KDF_ITERATIONS] and no caller in the app passes anything else.
 */
sealed class BackupInspect {
    data class Ready(val payload: BackupPayload, val byteSize: Long) : BackupInspect()

    /**
     * The file is a valid envelope this build understands, but the passphrase (or a changed
     * byte) does not open it. Deliberately one answer — see [BackupCrypto.open].
     */
    data object WrongPassphrase : BackupInspect()

    /** Bad magic, a future container version, an impossible length, a broken schema. */
    data class Invalid(val reason: String) : BackupInspect()
}

class BackupController(
    private val codec: BackupCodec = BackupCodec(),
    private val iterations: Int = BackupCrypto.KDF_ITERATIONS,
) {

    /**
     * Plaintext → JSON → AES-256-GCM → `.mtbackup`.
     *
     * The passphrase must be at least [BackupFile.MIN_PASSPHRASE_CHARS]; the UI checks first
     * so it can say so in Persian, and this check is the backstop for any other caller.
     */
    fun export(payload: BackupPayload, passphrase: CharArray): ByteArray {
        require(passphrase.size >= BackupFile.MIN_PASSPHRASE_CHARS) {
            "passphrase must be at least ${BackupFile.MIN_PASSPHRASE_CHARS} characters"
        }
        val plaintext = codec.encode(payload)
        return try {
            BackupFile.create(plaintext, passphrase, iterations)
        } finally {
            plaintext.fill(0)
        }
    }

    /** Opens and validates a file without applying anything. */
    fun inspect(
        bytes: ByteArray,
        passphrase: CharArray,
        now: Long = System.currentTimeMillis(),
    ): BackupInspect {
        if (bytes.isEmpty()) return BackupInspect.Invalid("empty file")
        if (bytes.size > BackupFile.MAX_ENVELOPE_BYTES) return BackupInspect.Invalid("file is too large")
        return when (val opened = BackupFile.open(bytes, passphrase)) {
            is BackupFile.OpenResult.Success -> try {
                BackupInspect.Ready(codec.decode(opened.plaintext, now), bytes.size.toLong())
            } catch (e: BackupFormatException) {
                BackupInspect.Invalid(e.message ?: "invalid backup")
            } finally {
                opened.plaintext.fill(0)
            }

            BackupFile.OpenResult.WrongPassphraseOrTampered -> BackupInspect.WrongPassphrase
            is BackupFile.OpenResult.Invalid -> BackupInspect.Invalid(opened.reason)
        }
    }

    suspend fun restore(
        payload: BackupPayload,
        target: BackupTarget,
        options: BackupRestoreOptions = BackupRestoreOptions(),
    ): BackupRestoreReport = target.restore(payload, options)

    /** What the confirmation sheet prints, from a file that has just been opened. */
    fun describe(inspect: BackupInspect): BackupSummary? =
        (inspect as? BackupInspect.Ready)?.let { it.payload.summary(it.byteSize) }
}
