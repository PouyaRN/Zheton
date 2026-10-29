# ADR 0001: Separation of Analytical Jargon from Core Dashboard & Visual Layout Hierarchy

## Status
Accepted

## Context
User feedback highlighted cognitive overload on the Home and Reports screens. When opening the personal finance app, the primary cognitive desire in the first 2 seconds is knowing "How much do I have?" and "How much did I spend today?". Multiple advanced analytical widgets (Inflation Immunity, Arbitrage Radar, Tether Spread, Daily Bleed) packed onto the home screen dilute visual hierarchy and create financial anxiety. Furthermore, the bottom navigation bar previously had the potential to overlap content on scrollable screens without sufficient bottom padding.

## Decision
1. **Streamline Home Screen**: Keep the primary Home dashboard focused on:
   - Total Liquid Balance & Today's Net Spend (with high-legibility typographic contrast and privacy eye toggle).
   - The interactive 3D Bank Card Deck (with 4-digit grouped PANs, quick copy, BIN-driven bank colors, and Dormant card sorting).
   - High-priority daily alerts (Upcoming Installment countdown, Daily Safe Spend pacing).
   - Quick Action FAB (+ floating action button) for rapid transaction logging in under 5 seconds.
2. **Move Advanced Market & Arbitrage Tools**: Consolidate Arbitrage Radar, Tether/Cash USD spread, Gold bubble metrics, and detailed inflation simulation into dedicated tabs/sections within "Markets" and "Reports" (Smart Insights) with horizontal chip segmentation (`[All]`, `[Currencies]`, `[Gold & Coins]`, `[Stock Funds]`, `[Crypto]`).
3. **Safe Insets & Navigation Clearance**: Enforce `contentPadding = PaddingValues(bottom = 96.dp)` across all scrollable lists and lazy columns, guaranteeing zero occlusion by the floating frosted glass bottom navigation bar.

## Consequences
- The home dashboard becomes breathable, immediate, and free from cognitive clutter.
- Power users retain instant access to deep market arbitrage and portfolio tracking within dedicated views.
