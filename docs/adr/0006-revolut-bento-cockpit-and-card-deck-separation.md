# ADR 0006: Revolut Bento Cockpit Architecture & Apple Wallet Card Deck Separation

## Status
Accepted (Amends and supersedes Section 1 of ADR 0001)

## Context
ADR 0001 originally restricted the Home screen exclusively to Liquid Balance, Today's Net Spend, the 3D Bank Card Deck, and Daily Alerts, banning all gold, market, and monthly analytical metrics from the primary fold to prevent clutter.
However, empirical user workflows in Iran's high-inflation economy demand immediate 2-second visibility into two existential metrics:
1. **18K Gold Rate / Inflation Hedge ("طلای ۱۸ عیار / سپر ضد تورم")**: Serving as the real-time monetary anchor against Rial depreciation.
2. **Monthly Cashflow Velocity ("جریان مخارج / تحلیل ماه جاری")**: Instant burn-rate context against the user's active Jalali/Payday financial cycle.

Concurrently, rendering both the full horizontal `BankCardDeck` on the Home dashboard and having a dedicated Cards tab created redundant UI real estate and visual bloat.

## Decision
1. **Adopt Revolut 10 Bento Cockpit on Home**:
   - Integrate a compact, 2-column Bento Cockpit on the Home screen providing a 2-second glance into:
     - 18K Gold real-time market benchmark and spread.
     - Monthly spending progress and safe daily burn rate pacing.
     - Upcoming commitments and daily liquidity alerts.
   - Maintain strict visual hierarchy: Net Worth / Liquid Hero -> Quick Actions Dock -> Bento Cockpit -> Recent Transactions stream.
2. **Strict Separation of Bank Cards to Tab 3 (Cards)**:
   - Completely remove `BankCardDeck` from the primary Home dashboard scroll fold.
   - Re-architect Tab 3 into a high-fidelity **Apple Wallet Accordion Fan-out**: cards stack with vertical spring overlapping, expanding on tap with fluid spring physics, exposing 1-tap PAN/SHEBA copying, and quick balance editing.
3. **Eliminate Ephemeral Layout Jiggle**:
   - Enforce a deterministic, responsive Bento layout; remove transient in-memory jiggle edit modes that add visual clutter without persistent storage.

## Consequences
- The Home dashboard delivers immediate high-value purchasing power intelligence (Bento Cockpit) in under 2 seconds without cognitive exhaustion.
- Home screen vertical height is significantly reduced by relocating physical card management entirely to Tab 3.
- Tab 3 gains an immersive, native Apple Wallet experience worthy of tier-1 fintech benchmarks.
