# 05: Manual Cash and Off-SMS Transaction Entry

**What to build:** An end-to-end flow in the Ledger tab allowing users to log cash or off-SMS income and expenses (amount in Toman/Rial, Jalali date, direction, category, merchant, note), persisting to `ManualTxn` in `DurableDb`, updating derived ledger state, and displaying in the timeline immediately.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Manual transaction input sheet with amount (Toman/Rial toggle), direction (expense/income), category picker, merchant, Jalali date, and note.
- [ ] ViewModel method to insert into `DurableDb.manual()` and update ledger derived state.
- [ ] Manual transactions display correctly in the timeline with edit/delete actions.
- [ ] Unit tests for manual transaction creation and mapping.
