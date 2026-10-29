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

## 6. Premium Neobank & Fintech Architecture (Revolut / N26 / Monzo Model)

### **Neobank Visual Paradigm (الگوی نئوبانک‌های پیشرو)**
- The design philosophy inspired by Revolut 10, N26, Monzo, and Apple Card: deep obsidian background (`#070A0F`), layered midnight navy surfaces (`#0F1622`, `#162030`), electric cyan/blue primary accents (`#00D2FF` / `#38BDF8`), sophisticated emerald gains (`#10B981`), and soft coral/red expenses (`#F43F5E`).

### **Financial Hero Display (نمایشگر مرکزی دارایی)**
- Clean, prominent balance header displaying net worth in bold Persian numerals (`ModamFigures`, `tnum`), period performance pill, and high-priority quick action controls (افزودن, انتقال, کارت‌ها, گزارش).

### **Hub & Pocket System (سیستم جیب‌ها و فضاهای مالی / Pots & Spaces)**
- Structured separation between operational liquid cash, bank card instruments, reserved goals (Pots), and investment allocations.

### **Transaction Stream Ergonomics (ارگونومی جریان تراکنش‌ها)**
- High-contrast, clean chronological stream with circular merchant/bank avatars, bilingual categorization, clear green/white amounts, and instant 1-tap detail drill-down.

### **Bento Cockpit (کابین بنتو / داشبورد فشرده)**
- High-density 2-second glance grid on the primary Home fold displaying real-time 18K gold benchmark anchor, monthly burn-rate pacing, and daily liquidity alerts, balancing immediacy with zero cognitive exhaustion.

### **Apple Wallet Accordion Deck (دک آکاردئونی کارت‌های بانکی)**
- Dedicated vertical stacking presentation of bank cards in Tab 3, where cards overlap dynamically and expand smoothly with spring physics upon interaction, providing instant 1-tap PAN/SHEBA copying and metallic 3D styling without cluttering the Home dashboard.

---

## 7. Unified Navigation & Screen Boundaries

### **Quad-Tab Architecture (معماری ۴ تب پایدار)**
- **Tab 1: CommandCenter (فرمان)**: Operational liquidity cockpit, daily safe spend, quick bank card triggers, and the 5 most recent activities. Primary focal number is **Liquid Cash** with Net Worth as a dignified secondary subtitle.
- **Tab 2: Market & Portfolio (بازار و دارایی)**: Comprehensive gold, currency, and fund pricing alongside asset allocation breakdown.
- **Tab 3: Bank Instruments (حساب‌ها و تعهدات)**: The single source of truth for bank cards (Apple Wallet accordion), SHEBA/PAN clipboard export, and scheduled commitments (BNPL & installments) positioned as a smart summary tile below the deck.
- **Tab 4: Activity & Trends (دفتر و تحلیل)**: Consolidated chronological ledger and executive analytical trends (Payday burn curve, 50/30/20 balance) toggled via a frictionless horizontal pager and top segmented pill (`ریزتراکنش‌ها` | `دیده‌بان و بودجه`).

### **Discretion & Privacy Mode (حالت حریم خصوصی و استتار مبالغ)**
- Instant masking of all financial figures (`••••••`) triggered via an eye icon next to the primary hero or a subtle device shake gesture, ensuring privacy in public transit and shared environments.

---

## 8. Ingestion & Tactile Ergonomics

### **Instant Numeric Keypad (صفحه‌کلید عددی برق‌آسای تراکنش)**
- Zero-friction bottom sheet triggered by the primary floating `+` action. Promotes amount-first entry via custom large tactile keypad, immediate 3-way toggle (`هزینه` / `درآمد` / `انتقال`), horizontal category chips, and automated card pairing completed in < 3 seconds.

### **Calendar Stream & Swipe Ergonomics (جریان تقویمی و ژست‌های حرکتی)**
- Transactions grouped under Shamsi day anchors (`امروز`, `دیروز`, date) displaying net daily burn in the header. Supports directional swipes: swipe-left for deletion with undo toast; swipe-right for rapid 50/30/20 necessity reclassification (`نیاز` / `خواسته` / `پس‌انداز`).

### **4-Tier Tactile Haptics (سامانه بازخورد لمسی ۴ سطحی)**
- `Chart Scrub`: 10ms micro-tick during slider/drag operations.
- `Tab Switch`: 25ms tactile detent when navigating root views.
- `Clipboard Export`: Double confirmation pulse upon copying PAN or SHEBA.
- `Commitment Settlement`: 55ms affirmative click on debt payment or transaction save.






