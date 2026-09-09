# ChandToman Domain Context Glossary

This document defines the canonical terms and bounded context concepts for ChandToman (چندتومان), the Iranian personal finance and real-time purchasing power copilot.

---

## 1. Core Financial Entities & Balances

### **Liquid Balance (موجودی نقد عملیاتی)**
- The immediate aggregate purchasing power residing in active checking/debit bank accounts in Iranian Rials / Tomans.
- Distinct from total Net Worth and excluded from frozen or collateralized deposit certificates.

### **Net Worth (ارزش خالص دارایی)**
- The combined mark-to-market valuation of Liquid Rial cash, Gold assets (18K gold, Melted Gold / طلای آب‌شده, Gold Coin / سکه امامی), Foreign Currencies (USD, USDT / تتر), and Investment Funds (صندوق‌های طلا و درآمد ثابت), minus outstanding debt obligations and BNPL liabilities.

### **Idle Cash (نقدینگی راکد)**
- Rial balances exceeding the rolling 30-day operating expenditure ceiling that sit without earning yield, subjecting the capital to daily purchasing power loss under inflation.

### **Inflationary Bleed (خونریزی تورمی نقدینگی)**
- The quantitative daily depreciation of idle Rial cash benchmarked against the gold price trajectory and inflation metrics.

### **Daily Safe Spend / Daily Burn Rate (خرج امن روزانه / نرخ سوخت روزانه)**
- The maximum discretionary spending amount permitted per remaining day in the active financial cycle to prevent balance exhaustion before the next anticipated cash injection.

### **Financial Cycle (چرخهٔ مالی ماه)**
- The income-aligned window (Jalali calendar month, or anchored to the user's payday via «مدیریت دوره‌ها → چرخهٔ مالی ماه») over which monthly income, expenses, burn rate, and runway are computed. Configured in Settings as `CycleSpec(mode, anchorDay)`; anchor days cap to the real month length (e.g. anchor 30 in Esfand clamps to the 29th).
- **Known boundary:** Goals category caps (GoalKind.CAP) and the Savings goals' `startsOn` reset/count from the Jalali calendar month, not the anchored cycle. The 50/30/20 rule follows the cycle (it reads the Reports MONTH window). Making CAP caps cycle-aware is a deliberate open follow-up.

---

## 2. Bank Cards & Payment Identity

### **Bank Card (کارت بانکی)**
- The presentable face of a [Bank Account](#bank-account-mask-شناسه-ماسک-کارت): one card per SMS-tracked account, never a standalone record. Decorative and identity fields (full PAN, SHEBA, expiry, nickname, visual theme) attach to the account; the account's parsed balance and transactions stay the source of truth for money math.
- Rendered strictly Left-to-Right (LTR) for PAN and SHEBA blocks regardless of UI layout direction, conforming to physical Iranian bank card standards.
- The CVV2 is deliberately never stored, shown, or asked for. PAN and expiry render masked by default and reveal only with privacy mode off.

### **Bank Account Mask (شناسه ماسک کارت)**
- The 16-digit or masked PAN (Primary Account Number) of an Iranian debit/credit card conforming to Shetab (شتاب) BIN standards.
- Displayed Left-to-Right with standard 4-digit grouping (`xxxx  xxxx  xxxx  xxxx`) and normalized to ASCII digits when copied to clipboard.

### **SHEBA / IBAN (شماره شبا)**
- The 26-character International Bank Account Number starting with `IR` followed by 24 digits, utilized for SATNA and PAYA interbank clearing. Attaches to the Bank Account for rapid clipboard export.

### **BIN Matching (تطبیق پیش‌شماره بانکی)**
- Algorithmic recognition of the issuing Iranian bank based on the first 6 digits of the PAN (e.g., `603799` for Bank Melli, `610433` for Bank Mellat, `621986` for Bank Saman, `502229` for Pasargad, `622106` for BluBank).

### **Dormant Account (حساب راکد / کم‌گردش)**
- An account with zero SMS or ledger transaction activity for more than 45 days, visually subdued in the deck to prioritize active daily spending accounts.

---

## 3. Commitments, Loans & BNPL

### **BNPL (Buy Now Pay Later / الان بخر، بعداً پرداخت کن)**
- Short-term installment credit facilities provided by platforms such as SnappPay (اسنپ‌پی), DigiPay (دیجی‌پی), Tara (تارا), or bank micro-loans.

### **Financial Commitment / Installment (قسط و تعهد مالی)**
- A scheduled future cash outflow with an explicit Shamsi (Solar Hijri) maturity date, installment index (e.g., installment 2 of 4), and remaining balance.

### **Sayad Check (چک صیادی)**
- A registered electronic or physical promissory note within the Central Bank of Iran's Sayad ecosystem, tracking issuer, receiver, Sayad ID, maturity date, and clearing status.

---

## 4. Transaction Ingestion & Intelligence

### **Self-Transfer / Internal Reconciliation (انتقال بین‌بانکی خودی)**
- Correlated debit and credit transactions between the user's own accounts occurring within a tight time window (≤ 2 minutes) with identical amounts, classified as neutral cash-flow transfers rather than genuine spending or income.

### **Refund (استرداد وجه)**
- Reversals of prior expenditures (e.g., canceled Digikala order or Snapp ride) credited back to the original account, neutralizing the specific category's spending rather than exaggerating gross income.

### **Necessity Tagging (طبقه‌بندی ۵۰/۳۰/۲۰ - ضرورت، تفریح، پس‌انداز)**
- Structural financial classification applied alongside merchant categories:
  - **Need (نیازهای اساسی)**: 50% target (housing, groceries, health, basic bills)
  - **Want (خواسته‌ها و تفریحات)**: 30% target (cafes, leisure, dining out)
  - **Savings & Investment (پس‌انداز و سرمایه‌گذاری)**: 20% target (gold funds, fixed-income, reserve)

### **Impulse Cluster (تجمع خریدهای تکانشی)**
- Three or more distinct debit transactions occurring within a 2-hour window across different merchant terminals, signaling emotional or accelerated spending behavior.

---

## 5. Market Context & Opportunity Cost

### **Gold Spend Benchmark (معادل طلایی خرید)**
- The real-time equivalence of a transaction converted into grams of 18K gold based on the exact market quote at the transaction timestamp.

### **Opportunity Cost Index / Regret Metric (هزینه فرصت خرید)**
- The counterfactual valuation of a discretionary expenditure had that capital been allocated to Gold ETFs (e.g., Ayar / Kahroba) or US Dollar reserves over a 3- to 6-month horizon.

---

## 6. ANDIS Financial OS & Widget Architecture

### **ANDIS (اندیس)**
- The financial operating system identity uniting Nothing OS dot-matrix aesthetics, Swiss grid systems, and high-density financial terminal design over Iranian rial/toman economics.

### **Financial Widget (ویجت مالی)**
- A self-contained, composable financial visual unit supporting three layout densities (`compact`, `standard`, `expanded`) within a responsive grid (4-column mobile, 8-column tablet, 12-column desktop).

### **Widget Grid Manifest (مانیفست چینش ویجت‌ها)**
- The persisted user configuration governing widget ordering, visibility, pinning, and slot sizing across dashboard surfaces.

### **Functional Neon Lime (`#D4FF00`)**
- The high-contrast accent signal reserved strictly for positive financial delta, active states, progress completion, and critical interactive metrics—never applied decoratively.

### **Dot-Matrix Semantic Language (زبان نقطه‌ای)**
- Geometric dot arrays and segmented indicators utilized for progress bars, loading skeletons, category allocations, and status beacons.

