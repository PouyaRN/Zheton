# 05: Failed POS Transaction Watchdog and Reversal Tracker

**What to build:** An automated watchdog detecting bank SMS alerts linked to failed POS transactions ("تراکنش ناموفق" / "مغایرت") that automatically registers a 72-hour reversal countdown and checks incoming credit SMS for matching refund amounts.

**Blocked by:** Ticket 04: Recurring Subscriptions and Card BIN Intelligence.

**Status:** ready-for-agent

- [ ] Implement `detectFailedTransaction(txn)` parser flagging failed/discrepancy debit alerts.
- [ ] Implement `reconcileReversals(failedTxns, inboundTxns)` matching refund transactions by amount and card mask within the 72h window.
- [ ] Build `FailedTxnWatchdogCard` indicating remaining hours and status (waiting, refunded, overdue alert).
- [ ] Wire the watchdog into the Ledger timeline header when active failed transactions exist.
- [ ] Add unit tests in `FailedTxnWatchdogTest.kt` covering detection, successful refund reconciliation, and expired alerts.
