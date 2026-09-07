# 06: Behavioral Life-Energy and 48-Hour Cooling Wishlist

**What to build:** Psychological and behavioral spending tools: translating purchase amounts into active labor hours ("Life-Energy") based on user wage settings, and a 48-hour cooling-off wishlist to counteract impulse buying.

**Blocked by:** Ticket 01: Currency Lens and Public Privacy Mode.

**Status:** ready-for-agent

- [ ] Implement `calculateLifeEnergy(amountToman, monthlyWageToman, monthlyHours)` returning hours and minutes of active labor.
- [ ] Render labor hours badge on `TransactionScreen` and transaction details.
- [ ] Build `WishlistSheet` for recording planned purchases with 48-hour countdown, status (cooling, reconsidered, purchased), and total money saved through impulse restraint.
- [ ] Add unit tests in `BehavioralFinanceTest.kt` verifying labor math and wishlist elapsed time calculations.
