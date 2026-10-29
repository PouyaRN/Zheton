package com.doxigo.muchtoman.features.backup

import com.doxigo.muchtoman.DailyCloses
import com.doxigo.muchtoman.HoldingLot
import com.doxigo.muchtoman.LotSide
import com.doxigo.muchtoman.mergeDailyCloses
import com.doxigo.muchtoman.features.pricealerts.AlertDirection
import com.doxigo.muchtoman.features.pricealerts.PriceAlert
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Schema version 2: lots, budget, price alerts and daily rates in the backup. */
class BackupMapStoresTest {

    private val now = 1_700_000_000_000L
    private val day = now / 86_400_000L
    private val codec = BackupCodec()

    private class FakeStores(
        override var holdingLots: Map<String, List<HoldingLot>> = emptyMap(),
        override var monthlyBudgetToman: Long = 0L,
        var alerts: List<PriceAlert> = emptyList(),
        var rates: MutableMap<String, DailyCloses> = mutableMapOf(),
        var current: Map<String, Double> = emptyMap(),
    ) : MapStores {
        var alertWrites = 0
        override fun priceAlerts() = alerts
        override fun replacePriceAlerts(alerts: List<PriceAlert>) {
            alertWrites += 1
            this.alerts = alerts
        }
        override fun dailyRates(): Map<String, DailyCloses> = rates.toMap()
        override fun mergeDailyRates(closes: Map<String, DailyCloses>) {
            for ((id, incoming) in closes) rates[id] = mergeDailyCloses(rates[id].orEmpty(), incoming)
        }
        override fun currentRates() = current
    }

    private fun alert(id: String, target: Double, direction: AlertDirection, armed: Boolean = true) = PriceAlert(
        id = id, assetId = "gold18", assetName = "طلای ۱۸ عیار", target = target,
        direction = direction, armed = armed, createdAt = now - 5_000,
    )

    private fun populated() = FakeStores(
        holdingLots = mapOf(
            "gold18" to listOf(
                HoldingLot("l1", LotSide.BUY, 10.0, 2_500_000.0, now - 90_000_000),
                HoldingLot("l2", LotSide.SELL, 2.5, 2_700_000.0, now - 10_000),
            ),
            "usd" to listOf(HoldingLot("l3", LotSide.BUY, 300.0, 61_250.5, now - 1_000)),
        ),
        monthlyBudgetToman = 40_000_000L,
        alerts = listOf(
            alert("a1", 3_000_000.0, AlertDirection.ABOVE),
            alert("a2", 2_000_000.0, AlertDirection.BELOW, armed = false).copy(firedAt = now - 2_000),
        ),
        rates = mutableMapOf(
            "gold18" to mapOf(day - 2 to 2_600_000.0, day - 1 to 2_650_000.5, day to 2_640_000.0),
            "usd" to mapOf(day to 61_000.0),
        ),
        current = mapOf("gold18" to 2_640_000.0),
    )

    private fun snapshot(stores: MapStores) =
        stores.snapshotInto(BackupPayload(createdAt = now, appVersion = "test"))

    /** A version-1 file as an older build wrote it: none of the version-2 keys at all. */
    private fun versionOneBytes(): ByteArray {
        val v2 = Json.parseToJsonElement(String(codec.encode(snapshot(populated())), Charsets.UTF_8)).jsonObject
        fun JsonObject.without(vararg keys: String) = JsonObject(this - keys.toSet())
        val v1 = JsonObject(
            v2.without("dailyRates").toMutableMap().apply {
                put("schemaVersion", JsonPrimitive(1))
                put("prefs", v2["prefs"]!!.jsonObject.without("monthlyBudgetToman"))
                put("ledger", v2["ledger"]!!.jsonObject.without("holdingLots", "priceAlerts"))
            },
        )
        val text = v1.toString()
        assertFalse(text.contains("holdingLots") || text.contains("dailyRates") || text.contains("monthlyBudget"))
        return text.toByteArray(Charsets.UTF_8)
    }

    // ──────────────────────────── round trip ────────────────────────────

    @Test
    fun `the new sections survive encode and decode unchanged`() {
        val payload = snapshot(populated())
        assertEquals(2, payload.schemaVersion)
        val decoded = codec.decode(codec.encode(payload), now + 1_000)
        assertEquals(payload, decoded)
        assertEquals(40_000_000L, decoded.prefs.monthlyBudgetToman)
        assertEquals(3, decoded.ledger.holdingLots!!.values.sumOf { it.size })
        assertEquals(2, decoded.ledger.priceAlerts!!.size)
        assertEquals(2_650_000.5, decoded.dailyRates!!["gold18"]!![day - 1]!!, 0.0)
    }

    @Test
    fun `write, read and restore onto an empty phone gives back the same stores`() {
        val original = populated()
        val decoded = codec.decode(codec.encode(snapshot(original)), now + 1_000)
        val fresh = FakeStores(current = original.current)

        val result = restoreMapStores(decoded, BackupRestoreOptions(), fresh, now)

        assertEquals(original.holdingLots, fresh.holdingLots)
        assertEquals(original.monthlyBudgetToman, fresh.monthlyBudgetToman)
        assertEquals(original.alerts, fresh.alerts)
        assertEquals(original.rates, fresh.rates)
        assertEquals(3, result.holdingLots)
        assertTrue(result.monthlyBudgetRestored)
        assertEquals(2, result.priceAlerts)
        assertEquals(0, result.alertsAlreadyReached)
        assertEquals(2, result.dailyRateAssets)
    }

    @Test
    fun `summary counts the new sections`() {
        val summary = snapshot(populated()).summary()
        assertEquals(3, summary.holdingLots)
        assertEquals(2, summary.priceAlerts)
        assertEquals(2, summary.dailyRateAssets)
    }

    // ──────────────────────────── version 1 compatibility ────────────────────────────

    @Test
    fun `a version 1 file still decodes, with the new sections absent`() {
        val decoded = codec.decode(versionOneBytes(), now + 1_000)
        assertEquals(BACKUP_SCHEMA_VERSION, decoded.schemaVersion)
        assertNull(decoded.prefs.monthlyBudgetToman)
        assertNull(decoded.ledger.holdingLots)
        assertNull(decoded.ledger.priceAlerts)
        assertNull(decoded.dailyRates)
        assertEquals("test", decoded.appVersion)
    }

    @Test
    fun `restoring a version 1 file leaves the new stores exactly as they were`() {
        val decoded = codec.decode(versionOneBytes(), now + 1_000)
        val phone = populated()
        val before = populated()

        val result = restoreMapStores(decoded, BackupRestoreOptions(), phone, now)

        assertEquals(before.holdingLots, phone.holdingLots)
        assertEquals(before.monthlyBudgetToman, phone.monthlyBudgetToman)
        assertEquals(before.alerts, phone.alerts)
        assertEquals(before.rates, phone.rates)
        assertEquals(0, phone.alertWrites)
        assertEquals(MapStoresRestore(), result)
    }

    @Test
    fun `a version 2 file with empty sections does replace, because it says so`() {
        val empty = codec.decode(codec.encode(snapshot(FakeStores())), now + 1_000)
        assertEquals(emptyMap<String, Any>(), empty.ledger.holdingLots)
        val phone = populated()

        restoreMapStores(empty, BackupRestoreOptions(), phone, now)

        assertTrue(phone.holdingLots.isEmpty())
        assertEquals(0L, phone.monthlyBudgetToman)
        assertTrue(phone.alerts.isEmpty())
        // Daily rates only ever merge: an empty history in the file deletes nothing.
        assertEquals(populated().rates, phone.rates)
    }

    // ──────────────────────────── options and merging ────────────────────────────

    @Test
    fun `without preferences only the daily rates come back`() {
        val decoded = codec.decode(codec.encode(snapshot(populated())), now + 1_000)
        val phone = FakeStores(monthlyBudgetToman = 5L)

        val result = restoreMapStores(decoded, BackupRestoreOptions(restorePreferences = false), phone, now)

        assertTrue(phone.holdingLots.isEmpty())
        assertEquals(5L, phone.monthlyBudgetToman)
        assertTrue(phone.alerts.isEmpty())
        assertEquals(populated().rates, phone.rates)
        assertNull(result.holdingLots)
        assertEquals(2, result.dailyRateAssets)
    }

    @Test
    fun `daily rates merge and a day already on the phone keeps its own close`() {
        val decoded = codec.decode(codec.encode(snapshot(populated())), now + 1_000)
        val phone = FakeStores(rates = mutableMapOf("gold18" to mapOf(day to 9.0, day + 1 to 10.0)))

        restoreMapStores(decoded, BackupRestoreOptions(), phone, now)

        assertEquals(
            mapOf(day - 2 to 2_600_000.0, day - 1 to 2_650_000.5, day to 9.0, day + 1 to 10.0),
            phone.rates["gold18"],
        )
        assertEquals(mapOf(day to 61_000.0), phone.rates["usd"])
    }

    @Test
    fun `merged closes are pruned to the five year window`() {
        val merged = mergeDailyCloses(mapOf(10_000L to 1.0), mapOf(1L to 2.0, 9_999L to 3.0, 9_998L to -1.0))
        assertEquals(mapOf(9_999L to 3.0, 10_000L to 1.0), merged)
    }

    // ──────────────────────────── alerts are re-checked before arming ────────────────────────────

    @Test
    fun `an armed alert whose target the current rate already reached comes back disarmed`() {
        val source = FakeStores(
            alerts = listOf(
                alert("up-reached", 2_500_000.0, AlertDirection.ABOVE),
                alert("up-waiting", 3_000_000.0, AlertDirection.ABOVE),
                alert("down-reached", 2_800_000.0, AlertDirection.BELOW),
            ),
        )
        val decoded = codec.decode(codec.encode(snapshot(source)), now + 1_000)
        val phone = FakeStores(current = mapOf("gold18" to 2_640_000.0))

        val result = restoreMapStores(decoded, BackupRestoreOptions(), phone, now)

        val byId = phone.alerts.associateBy { it.id }
        assertFalse(byId.getValue("up-reached").armed)
        assertEquals(now, byId.getValue("up-reached").firedAt)
        assertTrue(byId.getValue("up-waiting").armed)
        assertFalse(byId.getValue("down-reached").armed)
        assertEquals(2, result.alertsAlreadyReached)
        assertEquals(1, phone.alertWrites)
    }

    @Test
    fun `without a current rate an armed alert stays armed for the next refresh`() {
        val source = FakeStores(alerts = listOf(alert("a", 1.0, AlertDirection.ABOVE)))
        val decoded = codec.decode(codec.encode(snapshot(source)), now + 1_000)
        val phone = FakeStores(current = emptyMap())

        restoreMapStores(decoded, BackupRestoreOptions(), phone, now)

        assertTrue(phone.alerts.single().armed)
    }

    @Test
    fun `an unknown lot side or alert direction drops that row, not the backup`() {
        val payload = snapshot(populated()).let {
            it.copy(
                ledger = it.ledger.copy(
                    holdingLots = mapOf("gold18" to listOf(BackupHoldingLot("x", "GIFT", 1.0, 1.0, now))),
                    priceAlerts = listOf(BackupPriceAlert("y", "gold18", "طلا", 1.0, "SIDEWAYS")),
                ),
            )
        }
        val phone = populated()
        restoreMapStores(codec.decode(codec.encode(payload), now + 1_000), BackupRestoreOptions(), phone, now)
        assertTrue(phone.holdingLots.isEmpty())
        assertTrue(phone.alerts.isEmpty())
    }

    // ──────────────────────────── validation ────────────────────────────

    @Test
    fun `implausible values in the new sections are refused`() {
        val base = snapshot(populated())
        val bad = listOf(
            base.copy(prefs = base.prefs.copy(monthlyBudgetToman = -1)),
            base.copy(ledger = base.ledger.copy(holdingLots = mapOf("k" to listOf(BackupHoldingLot("l", "BUY", -1.0, 1.0, now))))),
            base.copy(ledger = base.ledger.copy(priceAlerts = listOf(BackupPriceAlert("a", "gold18", "", -5.0, "ABOVE")))),
            base.copy(dailyRates = mapOf("gold18" to mapOf(day + 400 to 1.0))),
            base.copy(dailyRates = mapOf("gold18" to mapOf(day to 0.0))),
        )
        for (payload in bad) {
            assertThrows(BackupFormatException::class.java) { codec.decode(codec.encode(payload), now + 1_000) }
        }
    }

    @Test
    fun `the full export path carries the new sections through encryption`() {
        val payload = snapshot(populated())
        val passphrase = "a long enough passphrase".toCharArray()
        val envelope = BackupFile.create(codec.encode(payload), passphrase.copyOf(), iterations = 100)
        val result = BackupFile.open(envelope, passphrase)
        assertTrue(result is BackupFile.OpenResult.Success)
        val opened = codec.decode((result as BackupFile.OpenResult.Success).plaintext, now + 1_000)
        assertEquals(payload, opened)
    }
}
