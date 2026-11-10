package com.doxigo.muchtoman

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RatesSignatureAndContractTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun repoRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return if (File(cwd, "settings.gradle.kts").exists()) cwd else cwd.parentFile ?: cwd
    }

    @Test
    fun `golden rates fixture verifies against embedded k1 ECDSA P-256 public key`() {
        val root = repoRoot()
        val payloadFile = File(root, "rates-pipeline/fixtures/rates.golden.json")
        val sigFile = File(root, "rates-pipeline/fixtures/rates.golden.json.sig")
        assertTrue("Golden payload must exist", payloadFile.exists())
        assertTrue("Golden signature must exist", sigFile.exists())

        val bytes = payloadFile.readBytes()
        val sigB64 = sigFile.readText(Charsets.UTF_8).trim()

        assertTrue(
            "Valid IEEE-P1363 signature on golden fixture must verify",
            RatesSignatureVerifier.verify(bytes, sigB64, "k1"),
        )
    }

    @Test
    fun `tampered payload or unknown kid is rejected by RatesSignatureVerifier`() {
        val root = repoRoot()
        val payloadFile = File(root, "rates-pipeline/fixtures/rates.golden.json")
        val sigFile = File(root, "rates-pipeline/fixtures/rates.golden.json.sig")
        val bytes = payloadFile.readBytes()
        val sigB64 = sigFile.readText(Charsets.UTF_8).trim()

        val tampered = bytes.clone()
        tampered[tampered.size / 2] = (tampered[tampered.size / 2].toInt() xor 0x01).toByte()
        assertFalse(
            "Tampered payload bytes must fail ECDSA verification",
            RatesSignatureVerifier.verify(tampered, sigB64, "k1"),
        )

        assertFalse(
            "Unknown key id must fail verification",
            RatesSignatureVerifier.verify(bytes, sigB64, "k99"),
        )

        assertFalse(
            "Malformed signature must fail verification cleanly",
            RatesSignatureVerifier.verify(bytes, "not-valid-base64!!!", "k1"),
        )
    }

    @Test
    fun `golden rates fixture satisfies Android Rates contract and sanitizeRates`() {
        val root = repoRoot()
        val payloadText = File(root, "rates-pipeline/fixtures/rates.golden.json").readText(Charsets.UTF_8)
        val raw = json.decodeFromString<Rates>(payloadText)
        val nowMs = raw.updatedAt + 60_000L
        val sanitized = sanitizeRates(raw, "https://pouyarn.github.io/Zheton/rates.json", nowMs)

        val requiredKeys = listOf(
            "usd", "eur", "gbp", "nok", "try", "aed", "cad",
            "gold18", "gold_mesghal", "silver_999", "silver_925",
            "coin_emami", "coin_bahar", "coin_nim", "coin_rob", "coin_gerami",
            "btc", "eth", "usdt",
        )
        for (key in requiredKeys) {
            val price = sanitized.toman[key]
            assertTrue("Expected positive rate for '$key', got $price", price != null && price > 0.0)
        }
        assertEquals(raw.updatedAt, sanitized.updatedAt)
        assertTrue("Expected non-empty coins catalogue", sanitized.coins.isNotEmpty())
    }

    @Test
    fun `replay protection and freshness thresholds behave as specified`() {
        assertTrue(isRatesReplaySafe(1_762_800_000L, 1_762_700_000L))
        assertTrue(isRatesReplaySafe(1_762_800_000L, 1_762_800_000L))
        assertFalse(isRatesReplaySafe(1_762_600_000L, 1_762_700_000L))

        val nowMs = 1_762_800_000_000L
        assertEquals(RatesFreshness.FRESH, evaluateRatesFreshness(nowMs - 3_600_000L, nowMs))
        assertEquals(RatesFreshness.STALE_WARNING, evaluateRatesFreshness(nowMs - 50L * 3_600_000L, nowMs))
        assertEquals(RatesFreshness.EXPIRED, evaluateRatesFreshness(nowMs - 8L * 24 * 3_600_000L, nowMs))
    }
}
