# ADR 0002: Offline-First BNPL, Sayad Checks, and Credit Liability Engine

## Status
Accepted

## Context
Standard personal finance trackers in Iran capture only direct debit SMS messages, leaving BNPL purchases (e.g. SnappPay, DigiPay, Tara) invisible until monthly installments arrive. Additionally, physical and electronic Sayad checks (چک صیادی) represent major future liquidity obligations or receivables that are crucial for true Net Worth and runway calculations.

## Decision
1. **Dedicated Commitment Engine (`Commitment` Model)**:
   - Introduce an offline-first liability registry supporting BNPL installments, bank loans, and Sayad checks.
   - Each entry tracks principal, total installments, paid installments, next due date (Shamsi timestamp), provider/merchant, interest rate, and penalty rate.
2. **Double-Entry Liability Segregation**:
   - Liquid operational balance reflects actual bank balances.
   - Net Worth and Safe Runway deduct active due installments within the current 30-day window.
3. **Psychological Inflation Relief**:
   - Compute real present-value discounting on future installments:
     $$PV = \frac{FV}{(1 + i)^n}$$
   - Display the purchasing power erosion of future installments (e.g., "این قسط ۳ میلیونی ۶ ماه دیگر معادل ۲.۳ میلیون تومان ارزش خواهد داشت").

## Consequences
- Prevents sudden liquidity crunches from unexpected installment debits.
- Gives users complete transparency on total debt liabilities without cloud dependencies.
