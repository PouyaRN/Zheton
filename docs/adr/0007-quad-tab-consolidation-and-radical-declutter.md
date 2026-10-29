# 0007. Quad-Tab Consolidation and Radical App Declutter

Date: 2025-09-11

## Status

Superseded by the canvas redesign (1.1.0): five-tab floating nav and the layouts in docs/design/canvas. Kept as a record. (Was: Accepted.)

## Context

The application suffered from severe visual and architectural bloat:
1. **5-Tab Navigation Overload**: Having 5 distinct tabs (`فرمان`, `بازار`, `حساب‌ها`, `دفتر`, `دیده‌بان`) created cramped navigation touch targets and divided closely coupled domains (transaction ledger and transaction analytical reports were in separate root tabs).
2. **Quadruple Redundancies**: The "Add" action was rendered in 4 places; bank account balances were rendered in 4 conflicting formats; portfolio holdings were duplicated across both Tab 1 and Tab 2.
3. **Tab 4 & 5 Cognitive Fatigue**: Tab 4 (Ledger) wasted 360dp of header vertical space before showing transactions. Tab 5 (Reports) stacked 14 separate analytical cards, exhausting users.
4. **Dead Code**: Several legacy screens (`CommandCenterScreen.kt`, unlinked `GoalsUi.kt`, dormant `Family.kt`) inflated maintenance overhead.

## Decision

1. **Adopt Quad-Tab Architecture (4 Tabs + Floating Action Button)**:
   - **Tab 1: فرمان (CommandCenter)**: Focused strictly on operational liquidity, today's safe spend, quick bank card triggers, and 5 recent transactions.
   - **Tab 2: بازار (Market & Assets)**: Exclusive destination for real-time gold/FX intelligence, arbitrage, and detailed portfolio asset breakdown.
   - **Tab 3: حساب‌ها (Cards & Commitments)**: Single source of truth for bank cards via the Apple Wallet accordion deck, PAN/SHEBA rapid export, and upcoming commitments/installments.
   - **Tab 4: دفتر و تحلیل (Activity & Trends)**: Unified transaction stream and executive analytics toggled seamlessly via a top segmented pill control (`ریزتراکنش‌ها` vs `دیده‌بان و بودجه`).
2. **Sheet Architecture Consolidation**: Collapse 13 ad-hoc bottom sheets and sheet-on-sheet modals into 5 canonical drawers: `TransactionSheet`, `HoldingSheet`, `CardManagerSheet`, `CommitmentsSheet`, and `MarketAnalysisSheet`.
3. **Purge Dead Code**: Safely decouple and eliminate obsolete orphaned files and duplicated card views.

## Consequences

- Navigation touch targets expand from 54dp to >72dp, vastly improving ergonomic accessibility.
- Zero cognitive overload upon app launch: immediate answer to "How much liquid money do I have right now, what can I spend today, and what were my last 5 purchases?"
- Significant reduction in recomposition overhead and code complexity.
