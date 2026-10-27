package com.doxigo.muchtoman

/**
 * What the Accounts screen says about each account's freshness and its money in and out.
 *
 * Pure functions over the ledger so they test on a JVM; [UiState] exposes them as
 * [UiState.smsFreshness], [UiState.accountsFlow] and [UiState.accountFlow].
 */

/** How many days the in/out chart covers, today included. */
const val FLOW_DAYS = 30

/** One account's last parsed bank SMS, and the light Persian line saying how long ago. */
data class SmsFreshness(
    val accountKey: String,
    /** Epoch millis of the newest SMS-sourced transaction, or 0 when none was ever read. */
    val lastSmsAt: Long,
    /** "۳ ساعت پیش", from [faAgo]; "هنوز به‌روز نشده" when [lastSmsAt] is 0. */
    val agoFa: String,
)

/** One Tehran day of money moving, in whole toman. Both figures are non-negative. */
data class FlowDay(val day: Long, val inToman: Long, val outToman: Long)

/** A run of [FlowDay]s, oldest first and today last, with zero days present. */
data class AccountFlow(val days: List<FlowDay>) {
    val totalInToman: Long get() = days.sumOf { it.inToman }
    val totalOutToman: Long get() = days.sumOf { it.outToman }
}

/**
 * The newest SMS-read transaction per account. A ledger that has not loaded yet, or an account
 * with no SMS row in it, falls back to [BankAccount.updatedAt], which only the SMS fold sets.
 */
fun smsFreshness(
    accounts: List<BankAccount>,
    entries: List<LedgerEntry>,
    now: Long,
): List<SmsFreshness> {
    val newest = HashMap<String, Long>()
    for (e in entries) {
        if (e.txn.sourceKind != "sms") continue
        val prev = newest[e.txn.accountId] ?: 0L
        if (e.txn.at > prev) newest[e.txn.accountId] = e.txn.at
    }
    return accounts.map { a ->
        val at = newest[a.key] ?: a.updatedAt
        SmsFreshness(a.key, at, faAgo(at, now))
    }
}

/**
 * Daily deposits and withdrawals for the [FLOW_DAYS] days ending on [today], limited to
 * [accountIds]. Transfers between her own accounts and the later leg of a duplicate are left
 * out of both sides; a row whose direction the bank did not state cannot be placed and is too.
 */
fun accountFlow(
    entries: List<LedgerEntry>,
    accountIds: Set<String>,
    today: Long,
    days: Int = FLOW_DAYS,
): AccountFlow {
    val first = today - (days - 1)
    val inRial = LongArray(days)
    val outRial = LongArray(days)
    for (e in entries) {
        if (e.transfer || e.duplicate) continue
        if (e.txn.accountId !in accountIds) continue
        val signed = e.txn.signedRial ?: continue
        val i = (e.txn.day - first).toInt()
        if (e.txn.day < first || e.txn.day > today) continue
        if (signed > 0) inRial[i] += signed else outRial[i] += -signed
    }
    return AccountFlow(
        List(days) { i -> FlowDay(first + i, inRial[i] / 10, outRial[i] / 10) },
    )
}
