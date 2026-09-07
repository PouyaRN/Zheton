# ADR 0003: 50/30/20 Structural Budget Classification, Idle Cash Sweep, and Gold Spend Benchmarks

## Status
Accepted

## Context
In high-inflation economies like Iran (>40% annual CPI erosion), nominal Toman bookkeeping creates two primary distortions:
1. **Nominal Money Illusion & Delayed Reckoning**: Users evaluate purchases in nominal figures without realizing the erosion of their purchasing power or the opportunity cost relative to hard assets (gold, currencies).
2. **Idle Cash Decay**: Keeping excess liquid cash in zero-interest checking accounts leads to significant, silent wealth loss.
3. **Lack of Structural Budgeting**: Raw merchant categorization (supermarket, cafe, bills) does not inform users whether their overarching cash outflows fit sustainable financial structures.

## Decision
1. **50/30/20 Structural Necessity Classification**:
   - Introduced `NecessityType` enum (`NEED` 50%, `WANT` 30%, `SAVINGS` 20%).
   - Auto-classified categories into their structural buckets (e.g. food/bills to `NEED`, entertainment to `WANT`, investments/gold to `SAVINGS`).
   - Built `BudgetRule503020Summary` to calculate actual percentage allocations against the golden rules.
   - Rendered an interactive `Budget503020Card` in the Reports tab and displayed live category necessity hints during manual transaction logging.

2. **Idle Cash Sweep & Inflation Opportunity Loss**:
   - Built `IdleCashAnalyzer` evaluating liquid checking account balances against a 1.5x monthly operational burn buffer.
   - Any surplus exceeding 5M Toman generates an immediate warning card (`IdleCashSweepCard`) detailing projected annual purchasing power loss (`surplus * 45%`) and recommending conversion into Gold ETFs (`AssetClass.GOLD_ETF`) or physical melted gold (`AssetClass.MELTED_GOLD_18K`).

3. **Gold Spend Benchmark**:
   - Implemented `calculateGoldSpendBenchmark` translating debit transaction costs into equivalent physical grams of 18K gold (`طلا ۱۸ عیار`) using spot market quotes.
   - Displayed the physical gold benchmark alongside the working life energy duration on transaction detail cards, anchoring purchasing decisions to physical reality.

## Consequences
- **Positive**: Users gain immediate structural visibility into spending discipline and unhedged cash drag without requiring external cloud analysis.
- **Privacy**: 100% offline-first; all calculations occur on-device using local rates and Room database entries.
