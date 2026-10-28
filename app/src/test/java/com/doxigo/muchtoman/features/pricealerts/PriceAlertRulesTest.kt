package com.doxigo.muchtoman.features.pricealerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceAlertRulesTest {

    private fun alert(
        target: Double,
        direction: AlertDirection,
        id: String = "a",
        assetId: String = "gold18",
        armed: Boolean = true,
    ) = PriceAlert(id, assetId, "طلای ۱۸ عیار", target, direction, armed = armed)

    @Test fun aboveFiresAtOrPastTarget() {
        val a = alert(2_600_000.0, AlertDirection.ABOVE)
        assertFalse(PriceAlertRules.crossed(a, 2_599_999.0))
        assertTrue(PriceAlertRules.crossed(a, 2_600_000.0))
        assertTrue(PriceAlertRules.crossed(a, 2_700_000.0))
    }

    @Test fun belowFiresAtOrPastTarget() {
        val a = alert(2_000_000.0, AlertDirection.BELOW)
        assertFalse(PriceAlertRules.crossed(a, 2_000_001.0))
        assertTrue(PriceAlertRules.crossed(a, 2_000_000.0))
        assertTrue(PriceAlertRules.crossed(a, 1_900_000.0))
    }

    @Test fun missingOrBrokenRateNeverFires() {
        val below = alert(2_000_000.0, AlertDirection.BELOW)
        assertFalse(PriceAlertRules.crossed(below, null))
        assertFalse(PriceAlertRules.crossed(below, 0.0))
        assertFalse(PriceAlertRules.crossed(below, -5.0))
        assertFalse(PriceAlertRules.crossed(below, Double.NaN))
    }

    @Test fun disarmedAlertNeverFires() {
        assertFalse(PriceAlertRules.crossed(alert(1.0, AlertDirection.ABOVE, armed = false), 10.0))
    }

    @Test fun checkFiresOnceAndDisarms() {
        val alerts = listOf(
            alert(2_600_000.0, AlertDirection.ABOVE, id = "up"),
            alert(2_000_000.0, AlertDirection.BELOW, id = "down"),
            alert(100_000.0, AlertDirection.ABOVE, id = "usd", assetId = "usd"),
        )
        val first = PriceAlertRules.check(alerts, mapOf("gold18" to 2_650_000.0), now = 42L)
        assertEquals(listOf("up"), first.fired.map { it.id })
        assertEquals(listOf("up", "down", "usd"), first.alerts.map { it.id })
        val up = first.alerts.first { it.id == "up" }
        assertFalse(up.armed)
        assertEquals(42L, up.firedAt)
        assertTrue(first.alerts.first { it.id == "down" }.armed)
        assertTrue(first.alerts.first { it.id == "usd" }.armed)

        val second = PriceAlertRules.check(first.alerts, mapOf("gold18" to 2_700_000.0), now = 43L)
        assertTrue(second.fired.isEmpty())
    }

    @Test fun defaultDirectionFollowsCurrentRate() {
        assertEquals(AlertDirection.ABOVE, PriceAlertRules.defaultDirection(3_000_000.0, 2_500_000.0))
        assertEquals(AlertDirection.BELOW, PriceAlertRules.defaultDirection(2_000_000.0, 2_500_000.0))
        assertEquals(AlertDirection.ABOVE, PriceAlertRules.defaultDirection(2_000_000.0, null))
    }

    @Test fun messageIsPersian() {
        assertEquals(
            "طلای ۱۸ عیار به ۲٬۶۰۰٬۰۰۰ تومان رسید",
            PriceAlertRules.message(alert(2_600_000.0, AlertDirection.ABOVE)),
        )
    }
}
