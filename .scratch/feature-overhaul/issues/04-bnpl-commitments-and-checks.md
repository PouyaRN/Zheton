# 04: BNPL, Sayad Checks & Financial Commitment Hub

**What to build:**
Create an offline-first Commitment & BNPL hub tracking recurring installments (SnappPay, DigiPay, bank loans) and Sayad checks. Provide automatic calculation of inflation-discounted installment relief (e.g. "This 3M Toman installment due in 6 months loses 700k Toman of real value under current inflation"). Display upcoming due dates with countdown badges and allow manual creation and marking as paid.

**Blocked by:** 01 (Layout Clearance).

**Status:** completed

- [x] Data model for `FinancialCommitment` supporting BNPL, Loans, and Sayad Checks.
- [x] Due date countdown and status badges (active, paid, overdue).
- [x] Inflation-discounted present value calculation and psychological relief insight.
- [x] Interactive UI hub and sheet to view, add, and pay installments.
