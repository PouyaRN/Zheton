package com.doxigo.muchtoman

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountFlowTest {

    private var n = 0
    private val today = 20_000L
    private val noon = tehranDayStart(today) + 12 * 3_600_000L

    private fun entry(
        account: String,
        day: Long,
        signedRial: Long?,
        at: Long = tehranDayStart(day) + 3_600_000L,
        transfer: Boolean = false,
        duplicate: Boolean = false,
        source: String = "sms",
    ): LedgerEntry {
        n++
        val ref = "s:%04d:0".format(n)
        return LedgerEntry(
            txn = Txn(
                ref = ref, srcHash = ref, seq = 0, at = at, day = day,
                bank = account, accountId = account,
                direction = signedRial?.let { if (it > 0) "in" else "out" },
                amountRial = signedRial?.let { kotlin.math.abs(it) }, signedRial = signedRial,
                balanceRial = null, feeRial = null, mask = "", instrument = "unknown",
                merchant = "", merchantNorm = "", refNo = "", printedAt = "",
                channel = "unknown", unitPrinted = "none", inferred = false,
                parserVer = PARSER_VERSION, sourceKind = source,
            ),
            categoryId = "cat_x", categoryFa = "x", confidence = 95,
            needsReview = false, duplicate = duplicate, transfer = transfer,
        )
    }

    @Test
    fun `series is thirty days with zero days present and today last`() {
        val flow = accountFlow(listOf(entry("SAMAN", today - 3, 50_000)), setOf("SAMAN"), today)
        assertEquals(30, flow.days.size)
        assertEquals(today - 29, flow.days.first().day)
        assertEquals(today, flow.days.last().day)
        assertEquals((0 until 30).map { today - 29 + it }, flow.days.map { it.day })
        assertEquals(5_000L, flow.days[26].inToman)
        assertEquals(29, flow.days.count { it.inToman == 0L && it.outToman == 0L })
    }

    @Test
    fun `deposits go up, withdrawals down, in toman, totals from the series`() {
        val rows = listOf(
            entry("SAMAN", today, 1_000_000),
            entry("SAMAN", today, 200_000),
            entry("SAMAN", today, -300_000),
            entry("SAMAN", today - 29, -70_000),
        )
        val flow = accountFlow(rows, setOf("SAMAN"), today)
        assertEquals(FlowDay(today, 120_000, 30_000), flow.days.last())
        assertEquals(FlowDay(today - 29, 0, 7_000), flow.days.first())
        assertEquals(120_000L, flow.totalInToman)
        assertEquals(37_000L, flow.totalOutToman)
        assertEquals(flow.days.sumOf { it.inToman }, flow.totalInToman)
    }

    @Test
    fun `own transfers, duplicates, undirected and out-of-window rows are left out`() {
        val rows = listOf(
            entry("SAMAN", today, -5_000_000, transfer = true),
            entry("BLU", today, 5_000_000, transfer = true),
            entry("SAMAN", today, -100_000, duplicate = true),
            entry("SAMAN", today, null),
            entry("SAMAN", today - 30, 999_000),
            entry("SAMAN", today + 1, 999_000),
            entry("SAMAN", today, -10_000),
        )
        val flow = accountFlow(rows, setOf("SAMAN", "BLU"), today)
        assertEquals(0L, flow.totalInToman)
        assertEquals(1_000L, flow.totalOutToman)
    }

    @Test
    fun `one account versus all accounts`() {
        val rows = listOf(
            entry("SAMAN", today, 100_000),
            entry("BLU", today - 1, 200_000),
            entry("MANUAL", today, 400_000, source = "manual"),
        )
        assertEquals(10_000L, accountFlow(rows, setOf("SAMAN"), today).totalInToman)
        assertEquals(30_000L, accountFlow(rows, setOf("SAMAN", "BLU"), today).totalInToman)
    }

    @Test
    fun `freshness is the newest sms row per account with a persian ago`() {
        val accounts = listOf(
            BankAccount(bank = "SAMAN", updatedAt = 1L),
            BankAccount(bank = "BLU", updatedAt = noon - 3 * 3_600_000L),
            BankAccount(bank = "MELLAT"),
        )
        val rows = listOf(
            entry("SAMAN", today, -1, at = noon - 2 * 60_000L),
            entry("SAMAN", today, -1, at = noon - 5 * 3_600_000L),
            entry("SAMAN", today, -1, at = noon - 60_000L, source = "manual"),
        )
        val f = smsFreshness(accounts, rows, noon)
        assertEquals(listOf("SAMAN", "BLU", "MELLAT"), f.map { it.accountKey })
        assertEquals(noon - 2 * 60_000L, f[0].lastSmsAt)
        assertEquals("۲ دقیقه پیش", f[0].agoFa)
        // No SMS row yet: the balance fold's own timestamp.
        assertEquals("۳ ساعت پیش", f[1].agoFa)
        assertEquals(0L, f[2].lastSmsAt)
        assertEquals("هنوز به‌روز نشده", f[2].agoFa)
    }

    @Test
    fun `ui state exposes the flow for one and all accounts`() {
        val now = tehranDay(System.currentTimeMillis())
        val rows = listOf(entry("SAMAN", now, 100_000), entry("BLU", now, -50_000))
        val state = UiState(
            bankAccounts = listOf(BankAccount(bank = "SAMAN"), BankAccount(bank = "BLU")),
            disabledBanks = setOf("BLU"),
            ledger = LedgerView(entries = rows, allEntries = rows),
        )
        assertEquals(10_000L, state.accountsFlow.totalInToman)
        assertEquals(0L, state.accountsFlow.totalOutToman)
        assertEquals(5_000L, state.accountFlow("BLU").totalOutToman)
        assertEquals(2, state.smsFreshness().size)
    }
}
