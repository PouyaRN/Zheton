# Spec: MuchToman v3 Redesign & Inflation Defense Suite

## Problem Statement

Users of personal finance apps in Iran confront extreme, continuous currency depreciation (40–60% annual inflation) where nominal numbers actively deceive them. An Iranian user might see their nominal bank balance grow by 20% while their real purchasing power collapsed by 30%. Furthermore:
- Looking at accounts in public transit or shared spaces creates anxiety around prying eyes.
- Fixed debt and installment plans feel burdensome, but their real cost is actually declining due to inflation erosion.
- Recurring bills (internet bundles, gym memberships, subscriptions) slip under the radar without clear foresight of whether month-end cash is safe to spend.
- Small uninvested cash piles quietly lose purchasing power every single day without the user realizing the exact daily burn rate.
- Iranian card numbers, failed POS debits, and coin premiums lack transparency, leaving users without actionable guidance.

## Solution

A high-taste, privacy-first redesign of MuchToman that equips the user with an intuitive **Inflation Defense & Wealth Intelligence Suite**:
1. **Multi-Currency Lens & Public Privacy Mode:** A prominent top-level lens switching the entire app valuation between Tomans, 18k Gold Grams, USDT, and Emami Gold Coins. A quick toggle and gesture to instantly blur all sensitive financial figures in public spaces.
2. **Purchasing Power & Daily Cash Burn:** Real-time visibility into the daily purchasing power erosion of liquid cash balances, paired with a calculated Safe-to-Spend monthly runway.
3. **Gold Coin & ETF Bubble Calculator:** Mathematical breakdown of physical gold coins (intrinsic gold value vs. speculative premium) and gold mutual funds (Ayar, Tala, Kahroba).
4. **Smart Pattern Intelligence:** Automated detection of recurring subscriptions and fixed expenses from bank SMS, coupled with 6-digit Iranian Card BIN auto-identification.
5. **Failed Transaction Watchdog:** Automated tracking of failed POS transactions with a 72-hour countdown for bank reversal reconciliation.
6. **Behavioral Finance Engine:** Life-Energy labor hours calculation on purchases and a 48-hour cooling-off wishlist to counteract impulse buying.

## User Stories

1. As a user checking my wealth in a cafe, I want to toggle a multi-currency lens to see my net worth in grams of 18k gold and USDT, so that I know whether my real purchasing power increased or decreased.
2. As a commuter on the metro, I want to quickly enable privacy mode with a tap or gesture, so that nearby passengers cannot see my account balances and transaction numbers.
3. As an account holder with cash in a checking account, I want to see my daily cash burn rate from inflation, so that I am motivated to allocate excess idle cash into inflation hedges.
4. As a salary earner, I want to know my exact "Safe-to-Spend" cash and daily allowance after deducting upcoming installments and recurring commitments, so that I never accidentally overdraft before the next paycheck.
5. As an investor considering buying physical gold coins or gold funds, I want to see the real-time bubble/premium percentage of Emami coins and gold ETFs, so that I do not overpay for speculative premiums.
6. As a bank customer receiving SMS alerts, I want my recurring bills (e.g. Irancell, Shatel, gym) to be automatically detected and grouped, so that I can anticipate upcoming fixed commitments without manual spreadsheet entry.
7. As a debit card user, I want the app to recognize the issuing bank and logo from the card's first 6 digits, so that transactions and accounts are clearly identified.
8. As a shopper who experienced a failed POS card swipe ("تراکنش ناموفق"), I want the app to track the transaction and start a 72-hour countdown, so that I am alerted if the bank fails to reverse the debit.
9. As a mindful consumer, I want to see how many hours of my active labor a purchase represents, so that I have an intuitive psychological gut check against impulse spending.
10. As someone trying to cut down on impulse shopping, I want to place items into a 48-hour cooling-off wishlist, so that I can evaluate whether I truly need the item once the impulse fades.

## Implementation Decisions

1. **Design System & Taste:**
   - Palette: Obsidian black (`0xFF121212`) background with warm, burnished Iranian gold (`0xFFD4A373`) accents, soft muted stone (`0xFF2C2A26`) for elevated cards, and Iranian emerald green (`0xFF4EBA6F`) for positive purchasing power growth.
   - Typography: 100% Persian Modam typeface with dedicated stylistic sets, Persian numerals (`۰-۹`), proper RTL flow, and balanced letter-spacing.
   - Micro-interactions: Spring physics on toggles, tactile haptic feedback, and smooth animated crossfades between currency lenses.

2. **Multi-Currency Lens State Machine:**
   - Expose `CurrencyDenominator` enum (`TOMAN`, `GOLD_18K`, `USD_TETHER`, `EMAMI_COIN`) in `UiState`.
   - All monetary display cards transform their primary figure through a functional converter using existing `rates_cache` and live rates.

3. **Public Privacy Mode:**
   - Global Compose state `privacyModeEnabled: Boolean`.
   - Reusable Compose modifier `Modifier.privacyBlur(enabled)` applying `Modifier.blur(16.dp)` and accessibility masking.

4. **Cash Flow & Burn Rate Reducers:**
   - Pure domain functions `calculateDailyCashBurn(liquidCashRial, annualInflationPercent)` and `calculateSafeToSpend(liquidCashRial, unpaidObligationsRial, recurringBillsRial, reservationsRial)`.
   - Integrated directly into `HomeOverviewCard` and `ReportsTab`.

5. **Gold Bubble & Intrinsic Value Formula:**
   - Emami Coin: $8.133\text{g} \times 0.900 \times \frac{\text{SpotUSD} \times \text{SpotUSDIRR}}{31.1035} \times 1.09$.
   - Bubble $\% = \frac{P_{market} - \text{Intrinsic}}{P_{market}} \times 100$.

6. **Card BIN Directory:**
   - High-performance, memory-efficient compiled lookup table covering all 30+ Iranian banking institutions.

7. **Recurring Bill & Watchdog Heuristics:**
   - Heuristics operating directly on local SQLite transactions without network requests or cloud dependencies.

## Testing Decisions

- Only test external behavior and observable state transformations, never private implementation internals.
- Test suites:
  - `CurrencyLensTest`: verifies correct unit conversions across Toman, Gold 18k, Emami coin, and USDT under various spot prices.
  - `CashBurnAndRunwayTest`: tests daily inflation erosion math and safe-to-spend boundary conditions (zero obligations, overdue installments, negative balance guards).
  - `GoldBubbleTest`: tests intrinsic coin math, gold ETF premium math, and division-by-zero resilience.
  - `CardBinTest`: validates 6-digit BIN lookups across all Iranian banks and fallback behavior for unmapped cards.
  - `RecurringDetectorTest`: verifies period clustering and amount variance threshold filters on synthetic SMS transaction streams.

## Out of Scope

- Remote cloud backups or syncing to third-party proprietary servers (MuchToman remains strictly local-first).
- Interactive bank logins or scraping Iranian bank web portals (violates security and user privacy; bank SMS and manual logging are the source of truth).
- Automated trading execution on TSE or crypto exchanges (MuchToman is an informational intelligence tool, not a custodial broker).

## Further Notes

All screens and components will be tested against the existing test suite to ensure 100% backwards compatibility and zero regressions across existing ledger and portfolio holdings.
