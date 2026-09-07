# 04: Recurring Subscriptions and Card BIN Intelligence

**What to build:** An automated engine that scans ledger history to cluster recurring monthly bills and subscriptions (telecom, internet, gym, fixed rent) without manual tagging, alongside an offline 6-digit Card BIN lookup engine mapping card numbers to Iranian issuing banks and logos.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] Implement `detectRecurringExpenses(entries)` clustering outbound transactions by merchant/description and inter-arrival intervals ($28 \le \Delta days \le 32$ or monthly cadence).
- [ ] Implement `lookupBankByCardBin(panOrPrefix)` covering all 30+ Iranian banks with bank enum and Persian display names.
- [ ] Display detected bank logo / badge in transaction details and card lists.
- [ ] Add `RecurringExpensesSheet` accessible from the Ledger / Home screen to review and confirm detected recurring expenses.
- [ ] Add unit tests in `RecurringAndBinTest.kt` verifying BIN matches, unknown prefix handling, and clustering accuracy.
