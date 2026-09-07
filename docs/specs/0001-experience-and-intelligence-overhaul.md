# Feature Specification: ChandToman Experience, Intelligence & Aesthetics Overhaul

## Problem Statement
The current experience has significant friction points:
1. **Cognitive Overload**: The Home screen contains dense analytical widgets (spreads, arbitrage, WDI) which creates cognitive fatigue. Users need to immediately see their liquid balance and today's spending without visual distraction.
2. **Visual Hierarchy & Navigation Issues**: The bottom navigation bar floats directly over content at the end of lists without sufficient padding. Card numbers are sometimes shown unformatted, degrading legibility.
3. **Missing Critical Iranian Financial Realities**: BNPL services (SnappPay, DigiPay) and Sayad checks represent major credit obligations that were unmodeled until direct debits hit.
4. **Data Sync & Chart Legibility**: Ledger spending bars lacked visual ceiling guides and axis marks. Market asset sparklines needed strict green/red directionality matching Iranian financial conventions.
5. **Privacy & Offline Resilience**: Users need an app-wide Ghost Mode (privacy blur/masking) for public transit, quick transaction entry via FAB, and reliable offline-first persistence.

## Solution
An end-to-end design and engineering overhaul providing:
1. **Breathable Dashboard**: Clean primary view with high-contrast typography, 3D card deck, today's spending, and safe-spend pacing. Advanced analytical tools are relocated to dedicated tabs.
2. **Card Personalization & High-Frequency Tools**: BIN matching for Iranian banks, 4-digit grouped PANs, QR code modal for instant card/IBAN sharing, custom labels/accents, and dormant card subdual (>45 days inactive).
3. **Commitment & BNPL Hub**: Full lifecycle tracking for SnappPay, DigiPay, bank loans, and Sayad checks, including inflation-discounted installment relief calculations and Shamsi maturity schedules.
4. **Portfolio & Anti-Inflation Tracker**: Asset weight breakdown (Rials, Gold, USD, Funds), idle cash sweep warnings with real-time conversion recommendations, and opportunity cost regret metrics.
5. **High-Velocity Quick FAB & 50/30/20 Tagging**: Under-5-second expense logging, multi-tagging (`#Work`, `#SideProject`), necessity classification (Need, Want, Investment).
6. **Chart Guides & Visual Fidelity**: Ceiling lines and peak date highlights on ledger spending charts; dynamic green/red sparklines for market quotes.
7. **Global Ghost Mode & Bottom Nav Inset Protection**: Global privacy toggle blurring/masking sensitive amounts, plus 96.dp bottom navigation clearance across all screens.

## User Stories
1. As a user, I want to open the app and instantly see my total liquid balance and today's spend in 2 seconds without analytical jargon.
2. As a user, I want my bank cards to display card numbers formatted in standard 4-digit groups (e.g., `6037  9918  2736  4512`) so they are easy to read.
3. As a user, I want the bank card color to match the issuing bank's brand identity automatically based on the card's 6-digit BIN prefix.
4. As a user, I want to tap a QR code button on my card to show a QR code for my card number or IBAN so someone else can scan it immediately.
5. As a user, I want to toggle a global "Ghost Mode" eye icon to mask all balances and card numbers with asterisks when using the app in public.
6. As a user, I want to scroll all the way to the bottom of the Home, Ledger, and Markets screens without content hiding beneath the bottom navigation bar.
7. As a user, I want a Quick Action Floating Action Button (+) on the bottom navigation to log a new expense in under 5 seconds.
8. As a user, I want to track my BNPL installments (SnappPay, DigiPay) and bank loans in a dedicated hub so I know when payments are due.
9. As a user, I want to see the inflation-discounted real value of future loan installments to understand how inflation reduces my debt burden.
10. As a user, I want to see visual guides, peak spend markers, and ceiling lines on the 14-day spending chart in the Ledger tab.
11. As a user, I want negative market price movements to be rendered in clear red and positive in green across all sparklines.
12. As a user, I want to segment expenses into Need (50%), Want (30%), and Savings/Investment (20%) to understand my spending balance.
13. As a user, I want idle cash warnings when checking account balances exceed operating needs, with real-time conversion suggestions into Gold or Funds.
14. As a user, I want cards inactive for over 45 days to be moved to the end with a soft "Dormant" indicator so active cards stay front and center.
15. As a user, I want Sayad checks to be trackable with maturity dates and clearing status so my future liquidity is accurate.

## Implementation Decisions
1. **Model & Architecture**:
   - Add `Commitment` data class to `FinancialDomain.kt` supporting `BNPL`, `LOAN`, `CHECK`, and `MORTGAGE`, with Shamsi due dates, total/remaining payments, and inflation-adjusted present value methods.
   - Add `GhostMode` state flow to `FinancialViewModel` to allow global balance/mask blurring across all composables.
   - Add `NecessityType` enum (`NEED`, `WANT`, `SAVINGS`) to categories/entries for 50/30/20 reporting.
2. **Bank Card & QR Code**:
   - Provide BIN prefix matching in `BankCardThemes` for top Iranian banks.
   - Implement custom QR code matrix rendering via Canvas without external heavy dependencies.
   - Implement Dormant account detection (>45 days without transactions).
3. **UI Layout & Navigation**:
   - Apply `contentPadding = PaddingValues(bottom = 100.dp)` to all LazyColumns and scrollable containers.
   - Add a central Quick Entry FAB to the floating navigation dock.
   - Add horizontal market category filter chips (`All`, `Currencies`, `Gold`, `Funds`, `Crypto`).
4. **Ledger Chart Guides**:
   - Draw dashed reference guide lines, maximum ceiling markers, and day labels on the 14-day spending bar chart.

## Testing Decisions
- Unit test BIN matching, 4-digit formatting, and ASCII normalization.
- Unit test `Commitment` inflation-adjusted present value calculations.
- Unit test 50/30/20 necessity aggregation and dormant account detection.
- Unit test spending chart guide scale and boundary calculations.
- Unit test full flavor release builds (`testFullDebugUnitTest`).

## Out of Scope
- Direct root/hardware NFC card emulation.
- Cloud-based account syncing (retaining 100% offline-first privacy).
