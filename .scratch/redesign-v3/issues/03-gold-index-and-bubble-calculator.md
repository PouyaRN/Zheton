# 03: Gold Purchasing Power Index and Bubble Calculator

**What to build:** An interactive bubble and premium calculator for physical Iranian gold coins (Emami, Bahar Azadi, Nim, Rob) and gold mutual funds (Ayar, Tala, Kahroba), comparing intrinsic gold content against live market prices to expose speculative bubbles.

**Blocked by:** Ticket 01: Currency Lens and Public Privacy Mode.

**Status:** ready-for-agent

- [ ] Implement `calculateGoldCoinIntrinsicValue(coinWeightGrams, purity, spotGoldUsdOunce, spotUsdIrr)`.
- [ ] Implement `calculateBubblePercent(marketPriceToman, intrinsicPriceToman)`.
- [ ] Implement `calculateEtfPremium(marketPrice, nav)`.
- [ ] Build `GoldBubbleSheet` bottom sheet with quick selection between Emami, Half Coin, Quarter Coin, and Gold Funds with color-coded bubble meters.
- [ ] Add unit tests in `GoldBubbleTest.kt` verifying intrinsic math, coin weights, and bubble percentages against official TGJU models.
