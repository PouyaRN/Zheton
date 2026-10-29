package com.doxigo.muchtoman.features.backup

import com.doxigo.muchtoman.Holding
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupControllerTest {

    private val now = 1_700_000_000_000L
    private val passphrase = "correct horse battery".toCharArray()

    /** Low iterations: this file tests the pipeline, not the KDF's cost. */
    private val controller = BackupController(iterations = 100)

    private fun payload(): BackupPayload = BackupPayload(
        createdAt = now,
        appVersion = "1.0.5",
        includesMessages = false,
        prefs = BackupPrefs(name = "مریم", themeMode = "DARK"),
        ledger = BackupLedger(
            holdings = listOf(Holding(typeId = "usd", amount = 100.0, id = "h1")),
            history = mapOf(now / 86_400_000L - 1 to 42.0),
        ),
        tables = BackupTables(
            decisions = listOf(
                BackupTxnDecision(
                    id = "d1", ref = "s:${"a".repeat(64)}:0", kind = "category",
                    value = "cat_food", createdAt = now, updatedAt = now,
                )
            ),
            manualTxns = listOf(
                BackupManualTxn(id = "m1", at = now, day = now / 86_400_000L, amountRial = -1_000, createdAt = now, updatedAt = now)
            ),
        ),
    )

    private class RecordingTarget : BackupTarget {
        var calls = 0
        var payload: BackupPayload? = null
        var options: BackupRestoreOptions? = null

        override suspend fun restore(payload: BackupPayload, options: BackupRestoreOptions): BackupRestoreReport {
            calls += 1
            this.payload = payload
            this.options = options
            return BackupRestoreReport(
                manualTransactions = payload.tables.manualTxns.size,
                decisions = payload.tables.decisions.size,
                preferencesRestored = options.restorePreferences,
            )
        }
    }

    @Test
    fun `export then inspect round trips the payload and the size`() {
        val original = payload()
        val bytes = controller.export(original, passphrase)

        assertEquals("MTBK", String(bytes.copyOfRange(0, 4), Charsets.US_ASCII))
        val inspect = controller.inspect(bytes, passphrase, now + 1_000)
        assertTrue(inspect is BackupInspect.Ready)
        val ready = inspect as BackupInspect.Ready
        assertEquals(original, ready.payload)
        assertEquals(bytes.size.toLong(), ready.byteSize)

        val summary = controller.describe(inspect)
        assertNotNull(summary)
        assertEquals(1, summary!!.manualTransactions)
        assertEquals(1, summary.decisions)
        assertEquals(0, summary.messages)
        assertEquals(bytes.size.toLong(), summary.bytes)
    }

    @Test
    fun `the exported file has no plaintext in it`() {
        val bytes = controller.export(payload(), passphrase)
        val asLatin = String(bytes, Charsets.ISO_8859_1)
        assertFalse(asLatin.contains("schemaVersion"))
        assertFalse(asLatin.contains("cat_food"))
        assertFalse(asLatin.contains("مریم"))
        assertFalse(asLatin.contains(String(passphrase)))
    }

    @Test
    fun `a wrong passphrase is reported as one refusal`() {
        val bytes = controller.export(payload(), passphrase)
        assertEquals(BackupInspect.WrongPassphrase, controller.inspect(bytes, "wrong horse battery".toCharArray()))
    }

    @Test
    fun `a changed byte is not silently opened`() {
        val bytes = controller.export(payload(), passphrase)
        bytes[BackupFile.CIPHER_OFFSET + 4] = (bytes[BackupFile.CIPHER_OFFSET + 4].toInt() xor 1).toByte()
        assertEquals(BackupInspect.WrongPassphrase, controller.inspect(bytes, passphrase))
    }

    @Test
    fun `a future schema version is refused with a readable reason`() {
        val futureJson = """{"schemaVersion":${BACKUP_SCHEMA_VERSION + 1},"createdAt":0}""".toByteArray()
        val envelope = BackupFile.create(futureJson, passphrase, iterations = 100)
        val inspect = controller.inspect(envelope, passphrase)
        assertTrue(inspect is BackupInspect.Invalid)
        assertTrue((inspect as BackupInspect.Invalid).reason.contains("${BACKUP_SCHEMA_VERSION + 1}"))
    }

    @Test
    fun `junk and empty input are invalid, not crashes`() {
        assertTrue(controller.inspect(ByteArray(0), passphrase) is BackupInspect.Invalid)
        assertTrue(controller.inspect("hello".toByteArray(), passphrase) is BackupInspect.Invalid)
        val plain = """{"schemaVersion":1}""".toByteArray()
        assertTrue(controller.inspect(plain, passphrase) is BackupInspect.Invalid)
    }

    @Test
    fun `a short passphrase is refused at export`() {
        assertThrows(IllegalArgumentException::class.java) {
            controller.export(payload(), "short".toCharArray())
        }
    }

    @Test
    fun `restore hands the payload and options to the target`() = runBlocking {
        val target = RecordingTarget()
        val options = BackupRestoreOptions(restorePreferences = false, restoreMessages = false)
        val report = controller.restore(payload(), target, options)

        assertEquals(1, target.calls)
        assertEquals(options, target.options)
        assertEquals(payload(), target.payload)
        assertEquals(1, report.manualTransactions)
        assertEquals(1, report.decisions)
        assertFalse(report.preferencesRestored)
    }

    @Test
    fun `describe is null until something has been opened`() {
        assertNull(controller.describe(BackupInspect.WrongPassphrase))
        assertNull(controller.describe(BackupInspect.Invalid("x")))
    }

    @Test
    fun `a utf8 passphrase is the same passphrase`() {
        val password = "رمز عبورِ من ۱۲۳".toCharArray()
        val bytes = controller.export(payload(), password)
        assertTrue(controller.inspect(bytes, password) is BackupInspect.Ready)
        assertTrue(controller.inspect(bytes, "رمز عبورِ من ۱۲۴".toCharArray()) is BackupInspect.WrongPassphrase)
    }
}
