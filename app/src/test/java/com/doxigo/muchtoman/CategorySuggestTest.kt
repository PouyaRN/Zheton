package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySuggestTest {

    private var n = 0
    private val cats = BUILTIN_CATEGORIES
    private val day0 = tehranDayStart(20_000)

    /** Tehran wall-clock time on some day. */
    private fun at(hour: Int, minute: Int = 0, dayOffset: Int = 0): Long =
        day0 + dayOffset * DAY_MS + (hour * 60L + minute) * 60_000L

    private fun entry(
        category: String,
        amount: Long = 1_000_000,
        time: Long = at(12),
        merchant: String = "",
        account: String = "MELLAT",
        direction: String = "out",
        needsReview: Boolean = false,
        duplicate: Boolean = false,
    ): LedgerEntry {
        n++
        val ref = "s:%04d:0".format(n)
        val txn = Txn(
            ref = ref, srcHash = ref, seq = 0, at = time, day = tehranDay(time),
            bank = account, accountId = account, direction = direction,
            amountRial = amount, signedRial = if (direction == "in") amount else -amount,
            balanceRial = null, feeRial = null, mask = "", instrument = "unknown",
            merchant = merchant, merchantNorm = merchantNorm(merchant), refNo = "",
            printedAt = "", channel = "unknown", unitPrinted = "none", inferred = false,
            parserVer = PARSER_VERSION,
        )
        return LedgerEntry(
            txn = txn, categoryId = category, categoryFa = "", confidence = 95,
            needsReview = needsReview, duplicate = duplicate, transfer = false,
        )
    }

    private fun suggest(
        entries: List<LedgerEntry>,
        amount: Long? = 1_850_000,
        account: String? = "MELLAT",
        time: Long = at(10, 42, dayOffset = 30),
        merchant: String? = null,
        note: String? = null,
        notes: Map<String, String> = emptyMap(),
    ) = suggestCategory(entries, cats, amount, account, time, merchant, note, notes)

    @Test
    fun `no history means no suggestion and no reason`() {
        val s = suggest(emptyList())
        assertNull(s.top)
        assertNull(s.reason)
        assertTrue(s.next.isEmpty())
    }

    @Test
    fun `unconfirmed guesses, duplicates and uncategorised rows are not history`() {
        val s = suggest(
            listOf(
                entry("cat_dining", needsReview = true),
                entry("cat_dining", duplicate = true),
                entry(CAT_UNCATEGORISED),
                entry("cat_income", direction = "in"),
            ),
        )
        assertNull(s.top)
        assertNull(s.reason)
    }

    @Test
    fun `same merchant beats time of day and frequency`() {
        val history = List(5) { entry("cat_groceries", time = at(10, 30, it)) } +
            entry("cat_dining", time = at(20), merchant = "کافه لمیز")
        val s = suggest(history, merchant = "  کافه   لمیز ")
        assertEquals("cat_dining", s.top?.id)
        assertEquals("cat_groceries", s.next.first().id)
        assertEquals("به‌نظر رستوران و کافه؛ قبلاً هم همین‌جا رو همین زدی", s.reason)
    }

    @Test
    fun `merchant matches the alias she renamed it to`() {
        // applyMerchantAliases rewrites merchant, keeps merchantNorm as the source name.
        val aliased = entry("cat_dining", merchant = "شاپرک").let {
            it.copy(txn = it.txn.copy(merchant = "کافه لمیز"))
        }
        val s = suggest(listOf(entry("cat_groceries"), entry("cat_groceries"), aliased), merchant = "کافه لمیز")
        assertEquals("cat_dining", s.top?.id)
        assertTrue(SuggestEvidence.SAME_PLACE in s.evidence)
    }

    @Test
    fun `same place at this hour gives the canvas line`() {
        val history = listOf(
            entry("cat_dining", time = at(10, 0, 1), merchant = "کافه لمیز"),
            entry("cat_dining", time = at(11, 15, 2), merchant = "کافه لمیز"),
            entry("cat_groceries", time = at(19, 0, 3)),
        )
        val s = suggest(history, merchant = "کافه لمیز")
        assertEquals("cat_dining", s.top?.id)
        assertEquals("به‌نظر رستوران و کافه؛ معمولاً این ساعت همین‌جا خرج می‌کنی", s.reason)
    }

    @Test
    fun `without a merchant, time window and amount band beat raw frequency`() {
        val history = List(6) { entry("cat_groceries", amount = 9_000_000, time = at(19, 0, it)) } +
            listOf(
                entry("cat_dining", amount = 1_500_000, time = at(10, 0, 10)),
                entry("cat_dining", amount = 2_000_000, time = at(11, 30, 11)),
            )
        val s = suggest(history)
        assertEquals("cat_dining", s.top?.id)
        assertEquals("به‌نظر رستوران و کافه؛ معمولاً این ساعت همین‌قدر خرج می‌کنی", s.reason)
    }

    @Test
    fun `time window wraps around midnight`() {
        val history = listOf(
            entry("cat_transport", amount = 50_000_000, time = at(23, 40, 1)),
            entry("cat_transport", amount = 50_000_000, time = at(0, 10, 2)),
            entry("cat_groceries", amount = 50_000_000, time = at(12, 0, 3)),
            entry("cat_groceries", amount = 50_000_000, time = at(13, 0, 4)),
            entry("cat_groceries", amount = 50_000_000, time = at(14, 0, 5)),
        )
        val s = suggest(history, amount = null, time = at(0, 30, 30))
        assertEquals("cat_transport", s.top?.id)
        assertEquals("به‌نظر حمل و نقل؛ معمولاً این ساعت خرجت همینه", s.reason)
    }

    @Test
    fun `amount band alone`() {
        val history = listOf(
            entry("cat_bills", amount = 1_800_000, time = at(3, 0, 1)),
            entry("cat_bills", amount = 1_900_000, time = at(4, 0, 2)),
            entry("cat_groceries", amount = 90_000_000, time = at(5, 0, 3)),
            entry("cat_groceries", amount = 90_000_000, time = at(6, 0, 4)),
            entry("cat_groceries", amount = 90_000_000, time = at(7, 0, 5)),
        )
        val s = suggest(history, time = at(20, 0, 30))
        assertEquals("cat_bills", s.top?.id)
        assertEquals("به‌نظر قبض‌ها؛ خرج‌های همین‌قدری‌ات معمولاً همینه", s.reason)
    }

    @Test
    fun `falls back to overall frequency and says so`() {
        val history = listOf(
            entry("cat_health", amount = 90_000_000, time = at(3, 0, 1)),
            entry("cat_groceries", amount = 90_000_000, time = at(4, 0, 2)),
            entry("cat_groceries", amount = 90_000_000, time = at(5, 0, 3)),
        )
        val s = suggest(history, time = at(20, 0, 30))
        assertEquals("cat_groceries", s.top?.id)
        assertEquals(listOf(SuggestEvidence.FREQUENCY), s.evidence.toList())
        assertEquals("به‌نظر خواربار؛ بیشتر خرج‌هات همینه", s.reason)
    }

    @Test
    fun `a single stray hit is not called usual`() {
        val history = listOf(
            entry("cat_health", amount = 90_000_000, time = at(10, 30, 1)),
        )
        val s = suggest(history)
        assertEquals("cat_health", s.top?.id)
        assertEquals(setOf(SuggestEvidence.FREQUENCY), s.evidence)
    }

    @Test
    fun `note stands in for the place when no merchant was typed`() {
        val a = entry("cat_gifts", amount = 90_000_000, time = at(3, 0, 1))
        val history = listOf(a, entry("cat_groceries"), entry("cat_groceries"))
        val s = suggest(history, note = "تولد سارا", notes = mapOf(a.txn.ref to "تولد  سارا"))
        assertEquals("cat_gifts", s.top?.id)
        assertEquals("به‌نظر هدیه و نیکوکاری؛ قبلاً هم با همین یادداشت همین رو زدی", s.reason)
    }

    @Test
    fun `row holds the next three, padded from the category list, never the top`() {
        val history = listOf(entry("cat_dining"), entry("cat_dining"), entry("cat_groceries"))
        val s = suggest(history)
        assertEquals("cat_dining", s.top?.id)
        assertEquals(3, s.next.size)
        assertEquals("cat_groceries", s.next[0].id)
        assertTrue(s.next.none { it.id == "cat_dining" || it.id == CAT_UNCATEGORISED })
        assertEquals(s.next.map { it.id }.distinct(), s.next.map { it.id })
    }

    @Test
    fun `same account breaks a tie`() {
        val history = listOf(
            entry("cat_health", amount = 90_000_000, time = at(3, 0, 1), account = "SAMAN"),
            entry("cat_beauty", amount = 90_000_000, time = at(3, 0, 2), account = "MELLAT"),
        )
        assertEquals("cat_beauty", suggest(history, time = at(20, 0, 30)).top?.id)
    }

    @Test
    fun `archived or hidden categories are never suggested`() {
        val history = listOf(entry("cat_atina"), entry("cat_atina"), entry("cat_dining"))
        val s = suggestCategory(
            history, cats, 1_000_000, null, at(12), hidden = setOf("cat_atina"),
        )
        assertEquals("cat_dining", s.top?.id)
        assertTrue(s.next.none { it.id == "cat_atina" })
    }

    @Test
    fun `income looks only at money that came in`() {
        val history = listOf(
            entry("cat_dining"), entry("cat_dining"),
            entry("cat_loan_back", direction = "in"),
        )
        val s = suggestCategory(history, cats, 1_000_000, null, at(12), direction = "in")
        assertEquals("cat_loan_back", s.top?.id)
        assertTrue(s.next.all { it.kind == CategoryKind.INCOME })
    }
}
