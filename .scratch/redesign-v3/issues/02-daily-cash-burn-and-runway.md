# 02: Daily Inflation Cash Burn and Safe-to-Spend Runway

**What to build:** An intuitive, high-taste visual card showing the daily purchasing power erosion on uninvested Rial cash balances alongside a calculated "Safe-to-Spend" cash balance and daily spending allowance after factoring in upcoming debt installments and reservations.

**Blocked by:** Ticket 01: Currency Lens and Public Privacy Mode.

**Status:** ready-for-agent

- [ ] Implement `calculateDailyCashBurn(liquidCashToman, annualInflationPercent)` formula calculating continuous inflation drag.
- [ ] Implement `calculateSafeToSpend(liquidCashToman, upcomingObligationsToman, reservationsToman, daysRemainingInJalaliMonth)`.
- [ ] Design and build `CashBurnAndRunwayCard` with warm amber/flame and emerald indicators using Modam typography.
- [ ] Wire the card into `HomeOverview` and `ReportsTab`.
- [ ] Add unit tests in `CashBurnAndRunwayTest.kt` validating calculations, month boundaries, and zero/negative guards.
