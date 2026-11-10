package com.doxigo.muchtoman.features.smspicker

import com.doxigo.muchtoman.Bank
import com.doxigo.muchtoman.RawSms
import com.doxigo.muchtoman.legacySmsKey
import com.doxigo.muchtoman.senderKey
import com.doxigo.muchtoman.smsKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picker's promises, pinned to code.
 *
 * The corpus tests already prove the parser reads a message correctly. What is not covered
 * anywhere else is the *selection* layer: that the same inbox row cannot be offered twice, that
 * a sender the app does not read stays visible but unselectable, that "select all" cannot sweep
 * either of those into a batch, and that the batch is in the order the balance fold needs.
 */
class SmsPickerStateTest {

    private val blu = "0999 998 7641"

    private fun deposit(at: Long, amount: String = "1,000,000", balance: String = "5,000,000") = RawSms(
        from = blu,
        body = "بلو\nواریز پول\nسیدسهیل عزیز، $amount ریال به حساب شما نشست.\nموجودی: $balance ریال",
        at = at,
    )

    private fun withdrawal(at: Long, amount: String = "250,000", balance: String = "2,000,000") = RawSms(
        from = blu,
        body = "بلو\nبرداشت پول\nسیدسهیل عزیز، $amount ریال از حساب شما پرید.\nموجودی: $balance ریال",
        at = at,
    )

    @Test
    fun `an empty inbox has nothing to select and nothing to confirm`() {
        val state = SmsPickerState.load(emptyList())

        assertTrue(state.threads.isEmpty())
        assertTrue(state.messages.isEmpty())
        assertTrue(state.batch.isEmpty())
        assertEquals(SmsBatchPreview.EMPTY, state.preview)
        assertFalse(state.preview.hasEntries)

        // "Select all" on nothing is still nothing, not a crash or a phantom key.
        assertEquals(state, state.selectAllVisible())
        assertEquals(state, state.selectAllUnprocessed())
    }

    @Test
    fun `an unknown sender is a visible thread but can never be ticked`() {
        val state = SmsPickerState.load(
            listOf(
                RawSms(from = "0900 000 0003", body = "موجودی: 500,000 ریال", at = 10),
                RawSms(from = "bank advert", body = "تخفیف ویژهٔ سپرده", at = 11),
            ),
        )

        assertEquals(2, state.threads.size)
        assertTrue(state.threads.all { it.bank == null })
        assertTrue(state.threads.all { it.eligibleCount == 0 })
        assertTrue(state.threads.all { it.messages.single().read == null })
        val phoneNumberThread = state.threads.first { it.sender.startsWith("0900") }
        assertTrue(phoneNumberThread.title.contains("ناشناس"))

        // The whole point: she can see the thread, she cannot book from it.
        assertTrue(state.selectAllVisible().selected.isEmpty())
        assertTrue(state.selectAllUnprocessed().selected.isEmpty())
        assertEquals(0, state.preview.entryCount)
    }

    @Test
    fun `a stranger whose body names a bank offers a suggestion but stays untickable`() {
        val sms = RawSms(
            from = "0900 000 0000",
            body = "بانک سامان\nواریز: 1,000,000 ریال\nمانده: 5,000,000 ریال",
            at = 80,
        )
        val stranger = SmsPickerState.load(listOf(sms)).threads.single()

        assertNull(stranger.bank)
        assertEquals(Bank.SAMAN, stranger.guessedBank)
        assertEquals(0, stranger.eligibleCount)

        // Confirming the number is what turns the same bytes into a readable message.
        val confirmed = SmsPickerState.load(
            listOf(sms),
            extra = mapOf(senderKey("09000000000") to Bank.SAMAN),
        ).threads.single()

        assertEquals(Bank.SAMAN, confirmed.bank)
        assertEquals(1, confirmed.eligibleCount)
    }

    @Test
    fun `one sender written three ways is one thread`() {
        val state = SmsPickerState.load(
            listOf(
                RawSms(from = "+989999987641", body = deposit(100).body, at = 100),
                RawSms(from = "9999987641", body = deposit(101).body, at = 101),
                RawSms(from = "0999 998 7641", body = deposit(102).body, at = 102),
            ),
        )

        assertEquals(1, state.threads.size)
        val thread = state.threads.single()
        assertEquals(3, thread.count)
        assertEquals("0999 998 7641", thread.sender) // the newest spelling, kept verbatim
        assertEquals(Bank.BLU, thread.bank)
    }

    @Test
    fun `the same row delivered twice is one message`() {
        val sms = deposit(110)
        val state = SmsPickerState.load(listOf(sms, sms))

        assertEquals(1, state.threads.single().count)
        assertEquals(1, state.selectAllVisible().selected.size)
    }

    @Test
    fun `a message already on file is shown but never enters the batch`() {
        val old = deposit(120)
        val fresh = withdrawal(121)
        // Whichever of the app's identities is on file: the ledger stores the source hash.
        val processed = setOf(SmsMessageIds.of(old).srcHash)

        val state = SmsPickerState.load(listOf(old, fresh), processed)
        val thread = state.threads.single()

        assertEquals(2, thread.count)
        assertEquals(1, thread.eligibleCount)
        assertEquals(1, thread.processedCount)
        val oldMessage = thread.messages.first { it.at == 120L }
        assertTrue(oldMessage.alreadyProcessed)
        assertFalse(oldMessage.selectable)

        val selected = state.selectAllVisible()
        assertEquals(listOf(fresh), selected.batch)
        assertEquals(1, selected.preview.entryCount)
    }

    @Test
    fun `a message the balance fold saw under its pre-sender key is still history`() {
        val sms = deposit(130)
        val state = SmsPickerState.load(listOf(sms), setOf(legacySmsKey(sms.body, sms.at)))

        assertEquals(0, state.threads.single().eligibleCount)
        assertTrue(state.selectAllVisible().batch.isEmpty())
    }

    @Test
    fun `a message the fold counted under the current key is history too`() {
        val sms = deposit(131)
        val state = SmsPickerState.load(listOf(sms), setOf(smsKey(sms.from, sms.body, sms.at)))

        assertEquals(0, state.threads.single().eligibleCount)
    }

    @Test
    fun `select all takes only what can become a row`() {
        val good = deposit(140)
        val otp = RawSms(from = blu, body = "رمز: 482913", at = 141)
        val processed = withdrawal(142)

        val state = SmsPickerState.load(
            listOf(good, otp, processed),
            setOf(SmsMessageIds.of(processed).smsKey),
        )
        val selected = state.selectAllVisible()

        assertEquals(1, selected.selected.size)
        assertEquals(listOf(good), selected.batch)
        assertEquals(1, selected.preview.entryCount)
        assertEquals(0, selected.preview.hintCount)
    }

    @Test
    fun `the batch is oldest first because the balance fold reads it that way`() {
        val first = deposit(150)
        val second = withdrawal(151)

        // The inbox arrives oldest first, but the picker must not depend on that.
        val state = SmsPickerState.load(listOf(second, first)).selectAllVisible()

        assertEquals(listOf(first, second), state.batch)
    }

    @Test
    fun `a sender checkbox ticks every readable message and a partial thread reports itself`() {
        val first = deposit(160)
        val second = withdrawal(161)
        var state = SmsPickerState.load(listOf(first, second))
        val thread = state.threads.single()

        state = state.toggleThread(thread)
        assertEquals(SenderSelection.ALL, state.selectionOf(thread))
        assertEquals(2, state.batch.size)

        state = state.toggleMessage(thread.messages.first())
        assertEquals(SenderSelection.SOME, state.selectionOf(thread))
        assertEquals(1, state.batch.size)

        // A second tap on a partly ticked sender completes it rather than clearing it.
        state = state.toggleThread(thread)
        assertEquals(SenderSelection.ALL, state.selectionOf(thread))
        assertEquals(2, state.batch.size)

        state = state.toggleThread(thread)
        assertEquals(SenderSelection.NONE, state.selectionOf(thread))
        assertTrue(state.batch.isEmpty())
    }

    @Test
    fun `ticking a message that cannot be read changes nothing`() {
        val otp = RawSms(from = blu, body = "رمز: 482913", at = 170)
        val state = SmsPickerState.load(listOf(otp))
        val message = state.threads.single().messages.single()

        assertFalse(message.selectable)
        assertEquals(state, state.toggleMessage(message))
        assertEquals(state, state.toggleThread(state.threads.single()))
    }

    @Test
    fun `search matches body and sender and never drops a tick`() {
        val incoming = deposit(180)
        val outgoing = withdrawal(181)
        var state = SmsPickerState.load(listOf(incoming, outgoing)).selectAllVisible()
        assertEquals(2, state.selected.size)

        state = state.withQuery("پرید")
        val visible = state.visibleThreads().single()
        assertEquals(1, visible.messages.size)
        assertEquals(181L, visible.messages.single().at)
        assertTrue(visible.totalCount == 2)
        // Filtering is a view; the two ticks are still there underneath it.
        assertEquals(2, state.selected.size)
        assertEquals(2, state.batch.size)

        // A sender-number search keeps every message of the thread.
        state = state.withQuery("0999")
        assertEquals(2, state.visibleThreads().single().messages.size)

        state = state.withQuery("هیچ‌چیز")
        assertTrue(state.visibleThreads().isEmpty())
    }

    @Test
    fun `hiding processed rows leaves the thread and drops only the old messages`() {
        val old = deposit(190)
        val fresh = withdrawal(191)
        var state = SmsPickerState.load(
            listOf(old, fresh),
            setOf(SmsMessageIds.of(old).smsKey),
        )

        state = state.withShowProcessed(false)
        val visible = state.visibleThreads().single()

        assertEquals(1, visible.messages.size)
        assertEquals(191L, visible.messages.single().at)
        assertEquals(2, visible.totalCount)
    }

    @Test
    fun `the preview splits incoming from outgoing and counts the senders`() {
        val incoming = deposit(200, amount = "1,000,000", balance = "9,000,000")
        val outgoing = withdrawal(201, amount = "250,000", balance = "8,750,000")
        val preview = SmsPickerState.load(listOf(incoming, outgoing)).selectAllVisible().preview

        assertEquals(2, preview.messageCount)
        assertEquals(2, preview.entryCount)
        assertEquals(1_000_000L, preview.creditRial)
        assertEquals(250_000L, preview.debitRial)
        assertEquals(1, preview.senderCount)
        assertEquals(200L, preview.earliestAt ?: -1L)
        assertEquals(201L, preview.latestAt ?: -1L)
    }

    @Test
    fun `a maskan purchase hint is selectable but is never counted as a row of its own`() {
        // Real shapes from the Maskan corpus: the hint names the merchant, the debit that
        // follows carries the money. Ticking both must create one transaction, not two.
        val hint = RawSms(
            from = "Bank Maskan",
            body = "بانک مسکن\nخرید\nدیجی پی\nمبلغ: 32252775\nرمز: 079292",
            at = 240,
        )
        val debit = RawSms(
            from = "Bank Maskan",
            body = "خرید: -32,252,775\nحساب:4111100067462\nمانده:561,311,672\n0516-14:06",
            at = 241,
        )

        val state = SmsPickerState.load(listOf(hint, debit)).selectAllVisible()
        assertEquals(2, state.selected.size)
        assertEquals(1, state.preview.entryCount)
        assertEquals(1, state.preview.hintCount)

        val hintMessage = state.threads.single().messages.first { it.at == 240L }
        assertTrue(hintMessage.hint)
        assertTrue(hintMessage.selectable)
        assertFalse(hintMessage.createsEntry)
    }

    @Test
    fun `a reload drops ticks that no longer exist or are no longer readable`() {        val sms = deposit(210)
        var state = SmsPickerState.load(listOf(sms)).selectAllVisible()
        assertEquals(1, state.selected.size)

        // Same inbox, but the message has since been booked: the stale tick must not survive.
        state = state.reloaded(listOf(sms), setOf(SmsMessageIds.of(sms).srcHash))
        assertTrue(state.selected.isEmpty())

        // A message that vanished from the inbox takes its tick with it.
        state = state.reloaded(emptyList())
        assertTrue(state.selected.isEmpty())
        assertTrue(state.threads.isEmpty())
    }

    @Test
    fun `search folds the Persian letter variants and digit sets`() {
        assertEquals("خرید از فروشگاه 123", foldForSearch("خريد از فروشگاه ۱۲۳"))

        val sms = RawSms(from = blu, body = "خرید از فروشگاه ۱۲۳", at = 220)
        val state = SmsPickerState.load(listOf(sms)).withQuery("123")
        assertEquals(1, state.visibleThreads().size)
    }

    @Test
    fun `a row with no address is not a conversation`() {
        val state = SmsPickerState.load(
            listOf(
                RawSms(from = "", body = "پیام سیم‌کارت", at = 230),
                RawSms(from = "  ", body = "پیام سیم‌کارت دیگر", at = 231),
            ),
        )
        assertTrue(state.threads.isEmpty())
    }

    @Test
    fun `the privacy line still says what was promised`() {
        assertEquals(
            "پردازش روی همین گوشی؛ هیچ پیامکی جایی فرستاده نمی‌شود",
            SMS_PICKER_PRIVACY_LINE,
        )
    }
}
