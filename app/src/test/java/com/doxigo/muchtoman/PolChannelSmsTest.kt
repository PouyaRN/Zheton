package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PolChannelSmsTest {

    @Test
    fun `detects Pol instant transfer channel without false positives on similar words`() {
        val polSms = parseBankSms(
            sender = "0999 998 7641",
            body = "انتقال پل به مبلغ 12,000,000 ریال انجام شد\nمانده: 50,000,000 ریال",
            at = 1_760_000_000_000L,
        )
        assertNotNull(polSms)
        assertEquals(Channel.POL, polSms!!.channel)

        val systemPolSms = parseBankSms(
            sender = "6037",
            body = "سامانه پل: واریز 5,000,000 ریال\nمانده: 45,000,000 ریال",
            at = 1_760_000_001_000L,
        )
        assertNotNull(systemPolSms)
        assertEquals(Channel.POL, systemPolSms!!.channel)

        val applePurchaseSms = parseBankSms(
            sender = "0999 998 7641",
            body = "خرید از فروشگاه اپل پلاس مبلغ 2,500,000 ریال\nمانده: 20,000,000 ریال",
            at = 1_760_000_002_000L,
        )
        assertNotNull(applePurchaseSms)
        assertEquals(Channel.POS, applePurchaseSms!!.channel)
    }
}
