# 01: Currency Lens and Public Privacy Mode

**What to build:** An interactive top-level multi-currency lens (Toman, 18k Gold Grams, USDT, Emami Gold Coin) that dynamically re-denominates portfolio and balance figures across the UI, paired with an instant public privacy toggle (and two-finger gesture) that blurs all numeric figures with soft Gaussian blurring to prevent shoulder surfing.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] Add `CurrencyDenominator` enum (`TOMAN`, `GOLD_18K`, `USD_TETHER`, `EMAMI_COIN`) with Persian labels and symbols.
- [ ] Implement `formatInDenominator(amountToman, denominator, effectiveRates)` conversion helper with Persian digit formatting.
- [ ] Add `Modifier.privacyBlur(enabled)` supporting Gaussian blur and accessibility descriptions.
- [ ] Wire the multi-currency selector pill and eye/privacy toggle in the app bar.
- [ ] Add unit tests in `CurrencyLensTest.kt` covering all denominator conversions and edge cases (zero rate fallback).
