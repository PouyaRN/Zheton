# 03: Global Ghost Mode (App-Wide Privacy Blur & Masking)

**What to build:**
Implement an app-wide Ghost Mode (Privacy Masking) toggle accessible directly from the top bar on the Home screen. When Ghost Mode is active, all liquid balances, account masks, transaction values, and daily totals across Home, Ledger, Markets, and Reports are masked with asterisks or obscured with a blur effect, protecting user confidentiality in public transit or shared spaces.

**Blocked by:** None (can start immediately).

**Status:** completed

- [x] Global `isGhostMode` state in ViewModel/UI.
- [x] Eye icon in TopBar toggles Ghost Mode on/off.
- [x] Balances, card numbers, transaction amounts, and dashboard values display as masked (`••••••`) or blurred when active.
