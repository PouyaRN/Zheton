# Real-Time Market Intelligence & Dynamic Purchasing Optimization Engine
## Iranian High-Inflation Wealth Defense: Live APIs, Asset Analysis, and Capital Allocation

**Author:** MuchToman Architecture & Financial Intelligence Group  
**Status:** Canonical Engineering Specification & Primary Source Research  
**Target Repository:** `MuchToman` (Android, Kotlin Jetpack Compose, Cloudflare Worker)  
**Date:** September 2026 / شهریور ۱۴۰۵  

---

## 1. Executive Summary & Problem Formulation

In an economy experiencing structural 40%–60%+ annualized currency debasement and sudden episodic currency devaluations (جهش‌های ارزی), Iranian citizens face rapid purchasing power destruction. When the US Dollar / Tether exchange rate surges in Tehran:

1. **Uninformed Retail Trap:** Retail individuals panic-buy physical gold coins (specifically سکه ربع and سکه امامی) at the height of the frenzy, paying astronomical **bubble premiums (حباب سکه) of 25% to 50%+** over intrinsic gold melt value. When the exchange rate stabilizes or corrects, bubble deflation wipes out capital even if spot gold remains constant.
2. **Capital Mismatch:** Users with 2 million to 20 million Tomans feel excluded from wealth defense, believing only physical coins or real estate provide protection, unaware of zero-bubble, fractional instruments like Gold ETFs (صندوق‌های طلا در بورس کالا) or digital stablecoins.
3. **Lagged Real Estate Arbitrage:** Real estate prices operate with a 3-to-6 month structural lag behind exchange rate surges. During rapid FX devaluations, property values in hard-currency terms (USD/sqm) hit cyclical troughs, creating generational wealth opportunities for medium-to-large capital pools that retail investors fail to identify.

**The Solution:** A production-grade, local-first **Dynamic Wealth Defense Engine** for MuchToman that:
- Connects directly to verified, live Iranian primary source APIs with sub-second failover.
- Eliminates speculative bubbles by steering users to optimal asset classes matching their exact liquid budget.
- Integrates gold, gold funds (صندوق‌های طلا), foreign exchange (USDT & Paper FX), and real estate indices (شاخص مسکن و متراژ تهران و حومه).

---

## 2. Primary Source API Analysis (Verified Endpoints & Payloads)

Below are the primary source platforms verified through live probing, examining their schemas, latency, availability inside/outside Iran, and failure profiles.

### 2.1. Live Foreign Exchange & OTC Tether (تتر و ارز)

#### A. Tetherland (تترلند) — Iranian OTC Reference
- **Role:** The benchmark OTC desk for Tether-to-Toman (USDT/IRT) liquidity in Iran.
- **Endpoint:** `GET https://api.tetherland.com/currencies`
- **Authentication:** Public (no API key required).
- **Latency:** ~120ms – 250ms.
- **Status Verified:** `HTTP 200 OK`.
- **Response Schema:**
```json
{
  "status": 200,
  "data": {
    "currencies": {
      "USDT": {
        "price": 226700,
        "sell_price": 226700,
        "buy_price": 226700,
        "diff24d": "0.42",
        "diff7d": "9.28",
        "diff30d": "21.49",
        "last24h": 225750,
        "last24hMin": 224600,
        "last24hMax": 229100
      }
    }
  }
}
```
- **Strengths:** Directly provides sell and buy spreads in Toman, alongside 24-hour, 7-day, and 30-day percentage rate-of-change metrics (`diff24d`, `diff7d`, `diff30d`). Crucial for momentum detection during dollar surges.

#### B. Wallex (والکس) — High-Volume P2P Order Book
- **Role:** Deepest Iranian crypto market maker; accurately reflects retail bid/ask pressure.
- **Endpoint:** `GET https://api.wallex.ir/v1/markets`
- **Authentication:** Public.
- **Latency:** ~150ms.
- **Status Verified:** `HTTP 200 OK` (385 active pairs).
- **Target Symbol:** `USDTTMN`
```json
{
  "result": {
    "symbols": {
      "USDTTMN": {
        "symbol": "USDTTMN",
        "baseAsset": "USDT",
        "quoteAsset": "TMN",
        "faName": "تتر - تومان",
        "stats": {
          "bidPrice": "226728.0",
          "askPrice": "226730.0",
          "lastPrice": "226730.0",
          "24h_ch": 0.12,
          "7d_ch": 8.98,
          "24h_volume": "3229629.70",
          "24h_quoteVolume": "732483283972.52",
          "24h_highPrice": "229990.0",
          "24h_lowPrice": "224803.0"
        }
      }
    }
  }
}
```
- **Strengths:** 2 Toman spread (`226728` vs `226730`), exact 24h trading volume in Toman (`732 billion TMN`), high precision.

#### C. Ramzinex (رمزینکس) — Order Book Confirmation
- **Endpoint:** `GET https://api.ramzinex.com/exchange/api/v1.0/exchange/pairs`
- **Status Verified:** `HTTP 200 OK` (615 pairs).
- **Target Pair ID 11 (`usdtirr`):** Returns bid/ask and 24h volume in Rial.

---

### 2.2. Gold, Coins, and Cash Dollar (طلا، سکه، و ارز کاغذی)

#### TGJU (شبکه اطلاع‌رسانی طلا و ارز)
- **Role:** The undisputed market standard for Tehran Grand Bazaar (سبزه میدان) gold and physical coin spot pricing.
- **Endpoint:** `GET https://call.tgju.org/ajax.json`
- **Headers:** `User-Agent: Mozilla/5.0`
- **Authentication:** Public.
- **Payload Size:** ~176 KB JSON (963 tickers).
- **Currency Unit:** Iranian Rial (**divide by 10 for Toman**).
- **Key Identifiers:**

| App ID | TGJU Key | Description | Market Unit |
| :--- | :--- | :--- | :--- |
| `usd_cash` | `price_dollar_rl` | Tehran Free Market Cash USD (دلار آزاد) | Rial / 1 USD |
| `eur_cash` | `price_eur` | Euro Free Market (یورو) | Rial / 1 EUR |
| `gbp_cash` | `price_gbp` | British Pound (پوند) | Rial / 1 GBP |
| `gold18` | `geram18` | 18 Karat Gold Gram (طلای ۱۸ عیار) | Rial / Gram |
| `gold24` | `geram24` | 24 Karat Pure Gold Gram (طلای ۲۴ عیار) | Rial / Gram |
| `mesghal` | `mesghal` | Mithqal of Gold (مثقال طلا = 4.6083g 17k) | Rial / Mithqal |
| `coin_emami` | `sekeb` | Emami Coin / New Design (سکه تمام طرح جدید) | Rial / Coin |
| `coin_bahar` | `sekee` | Bahar Azadi / Old Design (سکه تمام طرح قدیم) | Rial / Coin |
| `coin_nim` | `nim` | Half Coin (نیم سکه بهار آزادی) | Rial / Coin |
| `coin_rob` | `rob` | Quarter Coin (ربع سکه بهار آزادی) | Rial / Coin |
| `coin_gerami` | `gerami` | One-Gram Central Bank Coin (سکه گرمی) | Rial / Coin |
| `gold_ounce` | `ons` | Global Gold Spot Ounce (انس جهانی طلا) | USD / Troy Ounce |

- **Sample Live Raw Data Extracted:**
  ```json
  "geram18": { "p": "233,536,000", "h": "238,480,000", "l": "231,186,000", "ts": "2026-09-06 00:00:00" },
  "sekeb":   { "p": "2,292,300,000", "h": "2,333,100,000", "l": "2,290,000,000", "ts": "2026-09-06 00:00:00" },
  "price_dollar_rl": { "p": "2,230,000", "h": "2,287,200", "l": "2,229,600", "ts": "2026-09-06 00:00:00" }
  ```

---

### 2.3. Gold ETFs & Commodity Certificates (صندوق‌های طلا و شمش در بورس کالا)

#### TSETMC (شرکت مدیریت فناوری بورس تهران)
- **Role:** Live exchange prices for regulated Gold Funds and Gold Bullion Deposit Certificates (گواهی سپرده شمش طلا).
- **Endpoint:** `GET https://old.tsetmc.com/tsev2/data/MarketWatchInit.aspx?h=0&r=0`
- **Compression:** `Accept-Encoding: gzip` (binary stream unpacked via gzip decompressor).
- **Delimiters:** 5 `@` separated segments; segment 2 contains semicolon-delimited instruments.
- **Key Gold Instruments on Farabourse:**

| Symbol (نماد) | Instrument Code | Fund Manager | Backed By |
| :--- | :--- | :--- | :--- |
| `طلا` | `IRTKLOTF0001` | Lotus Parsian Gold Fund | Physical Gold Bullion & Coins |
| `عیار` | `IRTKAYAR0001` | Mofid Ayar Gold Fund | 99.5% Gold Bullion Bars |
| `کهربa` | `IRTKARAF0001` | Karafarin Kahroba Fund | Gold Bullion Deposit Certificates |
| `زر` | `IRTKAGAH0001` | Agah Zarrin Fund | Bullion & Coins |
| `گوهر` | `IRTKGHAR0001` | Kian Gohar Fund | Bullion & Coins |
| `زرفام` | `IRTKZARF0001` | Zarfam Omid Gold Fund | Gold Bullion |

- **Why Gold Funds Outperform Physical Coins for Retail Investors:**
  1. **Zero Bubble Risk:** Traded strictly around Net Asset Value (NAV). Bubble is typically 0% to 2% versus 25% to 50% for physical coins.
  2. **Micro-Fractions:** Minimum order is 100,000 Tomans (~$0.50). Any user can allocate loose cash immediately.
  3. **Zero Storage & Counterfeit Risk:** Completely electronic, regulated by the Securities and Exchange Organization (سازمان بورس و اوراق بهادار), immune to theft or fake coin scams.
  4. **Immediate T+1 Liquidity:** Can be liquidated during market hours at market price with low brokerage commissions (0.15% vs 1-3% coin shop spreads).

---

### 2.4. Real Estate Market Intelligence (داده‌ها و شاخص‌های املاک و مسکن)

#### Primary Data Sources in Iran:
1. **Central Bank of Iran (CBI / بانک مرکزی) — Monthly Tehran Housing Report:**
   - *Publication:* Monthly report: "گزارش تحولات بازار معاملات مسکن شهر تهران".
   - *Coverage:* Official registration of deeds across all 22 municipal districts of Tehran.
   - *Key Metric:* Average Price Per Square Meter (متوسط قیمت یک متر مربع زیربنای واحدهای مسکونی معامله شده).
   - *Historical Benchmark Data:*
     - Tehran Median: ~88M – 125M Toman/sqm (wide variance across districts).
     - **District 1 (منطقه ۱ - شمال تهران):** ~180M – 260M Toman/sqm.
     - **District 5 (منطقه ۵ - قطب معاملات غرب):** ~110M – 145M Toman/sqm.
     - **District 18 & 20 (جنوب تهران):** ~45M – 65M Toman/sqm.
     - **Satellite High-Growth Commuter Cities:**
       - **Pardis (پردیس - فاز ۸ و ۱۱):** ~28M – 45M Toman/sqm.
       - **Parand (پرند):** ~20M – 35M Toman/sqm.
       - **Andisheh (اندیشه):** ~30M – 50M Toman/sqm.
2. **Real Estate Investment Funds (REITs / صندوق‌های املاک و مستغلات بورس):**
   - Regulated property funds traded on TSETMC (e.g. `صبا`, `ارزش مسکن`).
   - Enables users with budgets under 50 million Tomans to gain direct percentage exposure to commercial and residential real estate appreciation.
3. **Divar Open Platform & Market Statistics:**
   - Aggregates live asking prices and demand heatmaps per neighborhood.

---

## 3. Macroeconomic Dynamics: The Iranian Dollar Surge Transmission Matrix

Understanding the order of operations when the US Dollar surges is critical for algorithmic asset allocation:

```
                  ┌───────────────────────────────┐
                  │    Forex Shock / News Event   │
                  │  (Geopolitical / Money Supply) │
                  └───────────────┬───────────────┘
                                  │ (T+0 min)
                                  ▼
                  ┌───────────────────────────────┐
                  │   Tether (USDT) on Exchanges  │
                  │ 24/7 Liquidity, Leads Pricing │
                  └───────────────┬───────────────┘
                                  │ (T+15 min to T+1 hour)
                                  ▼
                  ┌───────────────────────────────┐
                  │   Bazaar Cash USD (سبزه میدان) │
                  │  Physical Paper Currency Rate │
                  └───────────────┬───────────────┘
                                  │ (T+30 min to T+2 hours)
         ┌────────────────────────┴────────────────────────┐
         ▼                                                 ▼
┌─────────────────────────────────┐       ┌─────────────────────────────────┐
│  Physical Coins (امامی / ربع)    │       │ Gold ETFs & Bullion (صندوق طلا) │
│ • Massive Speculative Bubble    │       │ • Zero/Low Bubble (< 2%)        │
│ • Bubble expands up to 40-50%   │       │ • Instant tracking of 18k spot  │
│ • HIGH RISK for late retail     │       │ • OPTIMAL DEFENSE for retail    │
└─────────────────────────────────┘       └─────────────────────────────────┘
                                                           │
                                                           │ (T+3 to T+6 months)
                                                           ▼
                                          ┌─────────────────────────────────┐
                                          │      Real Estate (مسکن و املاک)  │
                                          │ • 3-6 month transmission lag    │
                                          │ • Real prices in USD drop early │
                                          │ • MASSIVE OPPORTUNITY for macro │
                                          └─────────────────────────────────┘
```

### The Three Critical Market Distortions:
1. **The Quarter-Coin Trap (تله حباب ربع سکه):** Small retail savers buy Quarter Coins because they are nominally the cheapest Central Bank coin (~65M Toman vs ~230M for Emami). Speculation drives the bubble to 45%+ of its total price. When USD consolidates, the bubble collapses from 45% to 20%, erasing up to a quarter of the buyer's savings.
2. **The "Old Dollar" Real Estate Window (پنجره طلایی مسکن):** When the exchange rate jumps by 30% in two weeks, property sellers do not immediately increase asking prices by 30%. For 60 to 90 days, real estate is priced in "cheaper dollars". Investors with large capital can lock in real property at historic lows in foreign currency terms.
3. **The Melted Gold Assay Advantage (طلای آب‌شده):** 18k melted gold carries almost 0% bubble and 0% manufacture fee (اجرت ساخت), compared to jewelry which adds 15%–30% in useless labor costs that evaporate upon resale.

---

## 4. The Dynamic Purchasing & Budget Allocation Engine

We now formalize the recommendation engine that evaluates the user's liquid budget against real-time market conditions.

### 4.1. Input Parameters
- $B$: User's liquid budget in Toman (e.g., from Safe-to-Spend runway or manual input).
- $P_{USDT}$: Current Tether price in Toman (via Tetherland / Wallex).
- $P_{G18}$: 18k gold gram price in Toman (via TGJU).
- $B_{coin}$: Live bubble percentage for Emami, Bahar, Nim, Rob (via `evaluateCoinBubble`).
- $P_{RE}$: Average real estate price per square meter in target zone (Tehran average or suburban satellite cities like Pardis/Parand).

### 4.2. Algorithmic Budget Brackets & Decision Rules

```mermaid
graph TD
    A[User Budget B in Toman] --> B{Budget Bracket}
    
    B -->|Micro: B < 15M| C[Tier 1: Fractional Wealth Defense]
    B -->|Small: 15M ≤ B < 80M| D[Tier 2: Gold Bullion & Spot Defense]
    B -->|Medium: 80M ≤ B < 300M| E[Tier 3: Bullion Certificates & Old-Coin]
    B -->|Large: 300M ≤ B < 1.5B| F[Tier 4: Balanced Anti-Inflation Basket]
    B -->|Macro: B ≥ 1.5B| G[Tier 5: Real Estate & Land Acquisition]

    C --> C1[1. Gold ETFs: عیار / طلا / کهربا 70%<br>2. USDT: 30%<br>AVOID: Physical coins]
    D --> D1[1. Gold ETFs or 18k Melted Gold 75%<br>2. USDT 25%<br>AVOID: Rob/Nim if Bubble > 15%]
    E --> E1[1. Bourse Gold Bullion CD or Bahar Coin 60%<br>2. Gold ETFs 25%<br>3. USDT 15%]
    F --> F1[1. Gold Bullion / Bahar 50%<br>2. REIT Property Fund 25%<br>3. USDT 25%]
    G --> G1[1. Physical Property in Pardis/Chitgar/Tehran 70%<br>2. Gold Bullion 20%<br>3. Liquid USDT 10%]
```

#### Detailed Strategy Breakdown by Tier:

#### Tier 1: Micro Budget ($B < 15,000,000$ Toman / $\approx \$10 - \$75$)
- **Primary Recommendation:** **Gold ETFs on Bourse (صندوق‌های طلا: عیار، طلا، کهربا)**
  - *Why:* 100% backed by gold bullion, 0% coin bubble, 0% jewelry labor fees, minimum ticket size of 100,000 Toman.
- **Secondary Recommendation:** **Tether (USDT) on Tetherland / Wallex**
  - *Why:* Instant 24/7 liquidity, global purchasing power hedge.
- **Strict Anti-Recommendation (هشدار اکید):** Do NOT purchase physical Quarter Coins (ربع سکه) or Parsian coins from shops. The 30%–45% bubble and retail markup will instantly destroy micro-savings upon liquidation.

#### Tier 2: Small Capital ($15,000,000 \le B < 80,000,000$ Toman / $\approx \$75 - \$400$)
- **Primary Recommendation:** **18K Melted Gold (طلای آب‌شده نقدی بدون اجرت)** or **Gold ETFs**
  - *Why:* With 15M+ Toman, the user can buy certified 18k melted gold bars with a recognized assay mark (شماره انگ آزمایشگاه ری‌گیری) directly at spot price, eliminating manufacturing fees and coin bubbles.
- **Secondary Recommendation:** **USDT Staking / Cold Storage**
- **Coin Rule:** If the user insists on physical Central Bank coins, verify `evaluateCoinBubble`. If Quarter Coin bubble $> 20\%$, flag as **DANGEROUS** and redirect to Gold ETFs or Melted Gold.

#### Tier 3: Medium Capital ($80,000,000 \le B < 300,000,000$ Toman / $\approx \$400 - \$1,500$)
- **Primary Recommendation:** **Gold Bullion Certificates (گواهی سپرده شمش طلا بورس کالا)** or **Bahar Azadi Coin (سکه تمام بهار آزادی طرح قدیم)**
  - *Why:* Bahar Azadi has the exact same gold content as Emami (8.133g of 22k gold = 7.32g pure gold) but consistently trades at a **5% to 10% lower bubble** than Emami due to lower speculative retail hype.
- **Real Estate Exposure:** Allocate 15%–20% to **Real Estate Investment Funds (صندوق‌های سرمایه‌گذاری املاک و مستغلات)** to capture property inflation without buying a full building.

#### Tier 4: Large Capital ($300,000,000 \le B < 1,500,000,000$ Toman / $\approx \$1,500 - \$7,500$)
- **Primary Recommendation:** **Strategic Anti-Inflation Basket**
  - 50% Gold Bullion (شمش طلا) & Gold ETFs (immediate liquidity + zero bubble).
  - 25% Real Estate Investment Funds (REITs) or Pre-sale fractional equity.
  - 25% USDT / Foreign FX.
- **Used Vehicle Benchmark:** In this bracket, certain high-liquidity used Iranian vehicles (کوئیک، ساینا، پژو ۲۰۶ تیپ ۲) historically function as consumer capital goods that track USD with high liquidity.

#### Tier 5: Macro Capital ($B \ge 1,500,000,000$ Toman / $\approx \$7,500+$)
- **Primary Recommendation:** **Physical Real Estate (مسکن و زمین ملکی)**
  - *Entry Points:*
    - **1.5B – 3B Toman:** 1-to-2 bedroom apartments in high-growth satellite commuter hubs:
      - **Pardis (پردیس فاز ۱۱ و ۸):** Rapid transit link to Tehran, high rental demand from young workforce.
      - **Parand (پرند):** Connected via Metro Line 1, lowest entry ticket per square meter.
      - **Andisheh (اندیشه فاز ۱ تا ۴):** Established rental market, strong title deed liquidity (سند تک‌برگ).
    - **3B – 7B+ Toman:** Residential units in Western/Eastern Tehran (District 5, 22 Chitgar, District 4/8).
  - *Execution Timing:* During the first 90 days of an aggressive USD run-up, real estate offers the highest risk-adjusted real return as property owners have not yet repriced listings to the new FX plateau.

---

## 5. Technical Implementation Specification for MuchToman

### 5.1. Multi-Tiered Real-Time Price Aggregator (Worker Architecture)

The Cloudflare Worker (`worker/src/index.ts`) will orchestrate primary source fallback chains:

```typescript
// Proposed Enhancement for worker/src/index.ts

interface RealtimeMarketSnapshot {
  updatedAt: number;
  rates: {
    usd_tether: number;      // via Tetherland -> Wallex -> Ramzinex
    usd_free_bazaar: number; // via TGJU (price_dollar_rl / 10)
    gold18: number;          // via TGJU (geram18 / 10)
    gold_ounce: number;      // via TGJU (ons)
    coin_emami: number;      // via TGJU (sekeb / 10)
    coin_bahar: number;      // via TGJU (sekee / 10)
    coin_nim: number;        // via TGJU (nim / 10)
    coin_rob: number;        // via TGJU (rob / 10)
    coin_gerami: number;     // via TGJU (gerami / 10)
  };
  gold_etfs: {
    symbol: string;
    name: string;
    price: number;
    nav: number;
  }[];
  real_estate_indices: {
    tehran_avg_sqm: number;
    district1_sqm: number;
    district5_sqm: number;
    district22_sqm: number;
    pardis_sqm: number;
    parand_sqm: number;
  };
}
```

### 5.2. Kotlin Data Structures for Android Client (`Intelligence.kt`)

```kotlin
package com.doxigo.muchtoman

/**
 * Recommended wealth defense asset class.
 */
enum class AssetClass(val titleFa: String, val riskLevelFa: String) {
    GOLD_ETF("صندوق طلای بورس (عیار/طلا)", "ریسک بسیار پایین - بدون حباب"),
    MELTED_GOLD_18K("طلای آب‌شده ۱۸ عیار", "ریسک پایین - بدون اجرت"),
    GOLD_COIN_BAHAR("سکه تمام بهار آزادی", "ریسک متوسط - حباب کمتر از امامی"),
    USDT_STABLECOIN("تتر (دلار دیجیتال)", "ریسک نوسان ارزی پایین"),
    REAL_ESTATE_FUND("صندوق املاک و مستغلات بورس", "ریسک پایین - ضد تورم مسکن"),
    PHYSICAL_PROPERTY("ملک مسکونی / آپارتمان", "سرمایه‌گذاری بنیادی بلندمدت")
}

data class AllocationSlice(
    val assetClass: AssetClass,
    val percentage: Int,
    val amountToman: Long,
    val rationaleFa: String,
    val actionableStepsFa: String
)

data class BestBuyAdvice(
    val userBudgetToman: Long,
    val primaryRecommendation: AssetClass,
    val headlineFa: String,
    val summaryFa: String,
    val allocations: List<AllocationSlice>,
    val warningsFa: List<String>,
    val realEstateContextFa: String?
)

object PurchasingOptimizationEngine {

    fun computeBestBuy(
        budgetToman: Long,
        usdtToman: Double,
        gold18Toman: Double,
        emamiCoinToman: Double,
        robCoinToman: Double
    ): BestBuyAdvice {
        val robBubble = evaluateCoinBubble(CoinType.ROB, robCoinToman, gold18Toman)
        val emamiBubble = evaluateCoinBubble(CoinType.EMAMI, emamiCoinToman, gold18Toman)

        val warnings = mutableListOf<String>()
        if (robBubble.bubblePercent > 20.0) {
            warnings.add("هشدار حباب: ربع سکه دارای ${faDigits(String.format("%.1f", robBubble.bubblePercent))}٪ حباب سفته‌بازی است. خرید آن پیشنهاد نمی‌شود.")
        }
        if (emamiBubble.bubblePercent > 18.0) {
            warnings.add("حباب سکه امامی (${faDigits(String.format("%.1f", emamiBubble.bubblePercent))}٪) بالاست؛ گواهی شمش یا سکه بهار ترجیح دارد.")
        }

        return when {
            budgetToman < 15_000_000L -> {
                // Tier 1: Micro
                val etfPart = (budgetToman * 0.70).toLong()
                val usdtPart = budgetToman - etfPart
                BestBuyAdvice(
                    userBudgetToman = budgetToman,
                    primaryRecommendation = AssetClass.GOLD_ETF,
                    headlineFa = "بهترین خرید: صندوق‌های طلای بورس (بدون حباب)",
                    summaryFa = "با بودجه کمتر از ۱۵ میلیون تومان، خرید سکه فیزیکی به دلیل حباب بالا موجب زیان فوری می‌شود. صندوق‌های طلا (مانند عیار و طلا) امکان خرید کسری طلا را با حباب صفر فراهم می‌کنند.",
                    allocations = listOf(
                        AllocationSlice(AssetClass.GOLD_ETF, 70, etfPart, "پوشش مستقیم تورم با شمش طلای ۹۹.۵٪", "از طریق کارگزاری بورسی نمادهای 'عیار' یا 'طلا' را جستجو کنید."),
                        AllocationSlice(AssetClass.USDT_STABLECOIN, 30, usdtPart, "نقدینگی دلاری و مصونیت از افت ریال", "خرید تتر از صرافی‌های معتبر تترلند یا والکس و نگهداری در کیف پول امن.")
                    ),
                    warningsFa = warnings,
                    realEstateContextFa = "متوسط هر متر مربع مسکن در پرند حدود ۲۵ تا ۳۰ میلیون تومان است؛ با صندوق‌های املاک بورس می‌توانید متراژ کسری پس‌انداز کنید."
                )
            }
            budgetToman < 80_000_000L -> {
                // Tier 2: Small
                val goldPart = (budgetToman * 0.75).toLong()
                val usdtPart = budgetToman - goldPart
                BestBuyAdvice(
                    userBudgetToman = budgetToman,
                    primaryRecommendation = AssetClass.MELTED_GOLD_18K,
                    headlineFa = "بهترین خرید: طلای آب‌شده نقدی یا صندوق طلا",
                    summaryFa = "با این بودجه می‌توانید قطعات طلای آب‌شده دارای کد انگ آزمایشگاهی یا واحدهای صندوق طلا تهیه کنید که برخلاف سکه و طلافروشی، اجرت ساخت و حباب ندارد.",
                    allocations = listOf(
                        AllocationSlice(AssetClass.MELTED_GOLD_18K, 75, goldPart, "حفظ مستقیم ارزش دارایی بدون هزینه اضافه", "از طلافروشی معتبر دارای جواز با دریافت فاکتور رسمی و استعلام کد انگ ری‌گیری."),
                        AllocationSlice(AssetClass.USDT_STABLECOIN, 25, usdtPart, "تثبیت دلاری سبد سرمایه", "حفظ بخش نقدی به صورت دلار دیجیتال برای استفاده در فرصت‌های بازار.")
                    ),
                    warningsFa = warnings,
                    realEstateContextFa = "این بودجه معادل پیش‌پرداخت حدود ۲ تا ۳ متر مربع مسکن در حومه تهران (پردیس/پرند) است."
                )
            }
            budgetToman < 300_000_000L -> {
                // Tier 3: Medium
                val baharPart = (budgetToman * 0.60).toLong()
                val etfPart = (budgetToman * 0.25).toLong()
                val usdtPart = budgetToman - baharPart - etfPart
                BestBuyAdvice(
                    userBudgetToman = budgetToman,
                    primaryRecommendation = AssetClass.GOLD_COIN_BAHAR,
                    headlineFa = "بهترین خرید: سکه طرح قدیم (بهار آزادی) و گواهی شمش",
                    summaryFa = "سکه تمام بهار آزادی دقیقاً هم‌وزن سکه امامی (۸.۱۳۳ گرم عیار ۹۰۰) است اما حباب قیمتی آن بسیار کمتر از امامی است. ترکیب آن با گواهی شمش طلا بالاترین بازدهی را دارد.",
                    allocations = listOf(
                        AllocationSlice(AssetClass.GOLD_COIN_BAHAR, 60, baharPart, "طلای مسکوک قانونی با کمترین حباب بازار", "خرید سکه تمام طرح قدیم و نگهداری در صندوق امانات بانکی."),
                        AllocationSlice(AssetClass.GOLD_ETF, 25, etfPart, "نقدشوندگی سریع در کمتر از یک دقیقه", "واحدهای صندوق طلا در تابلوی بورس کالا."),
                        AllocationSlice(AssetClass.USDT_STABLECOIN, 15, usdtPart, "توازن نقدینگی و دارایی‌های ارزی", "تتر جهت پوشش فوری شوک‌های ژئوپلیتیک.")
                    ),
                    warningsFa = warnings,
                    realEstateContextFa = "امکان ورود به پیش‌فروش‌های معتبر تعاونی یا صندوق‌های املاک و مستغلات بورس."
                )
            }
            budgetToman < 1_500_000_000L -> {
                // Tier 4: Large
                val goldPart = (budgetToman * 0.50).toLong()
                val reFundPart = (budgetToman * 0.25).toLong()
                val usdtPart = budgetToman - goldPart - reFundPart
                BestBuyAdvice(
                    userBudgetToman = budgetToman,
                    primaryRecommendation = AssetClass.REAL_ESTATE_FUND,
                    headlineFa = "بهترین خرید: سبد تدافعی طلا + صندوق املاک و مستغلات",
                    summaryFa = "در این محدوده سرمایه، ورود به بازار مسکن به صورت متری یا از طریق صندوق‌های ساختمانی بورس در کنار شمش طلا، بالاترین سپر تورمی را ایجاد می‌کند.",
                    allocations = listOf(
                        AllocationSlice(AssetClass.GOLD_ETF, 50, goldPart, "سپر تورمی آنی شمش طلا", "خرید گواهی شمش طلا و صندوق‌های طلا."),
                        AllocationSlice(AssetClass.REAL_ESTATE_FUND, 25, reFundPart, "استفاده از جهش قیمت مسکن در موج دوم تورم", "خرید نمادهای املاک و مستغلات در بورس."),
                        AllocationSlice(AssetClass.USDT_STABLECOIN, 25, usdtPart, "حفظ نقدینگی دلاری پرسرعت", "ذخیره در کیف پول سرد.")
                    ),
                    warningsFa = warnings,
                    realEstateContextFa = "معادل خرید یک واحد آپارتمان مسکن مهر فاز ۱۱ پردیس یا پرند با وام بانکی."
                )
            }
            else -> {
                // Tier 5: Macro (1.5B+)
                val propertyPart = (budgetToman * 0.70).toLong()
                val goldPart = (budgetToman * 0.20).toLong()
                val usdtPart = budgetToman - propertyPart - goldPart
                BestBuyAdvice(
                    userBudgetToman = budgetToman,
                    primaryRecommendation = AssetClass.PHYSICAL_PROPERTY,
                    headlineFa = "بهترین خرید: ملک مسکونی / زمین در قطب‌های توسعه",
                    summaryFa = "با جهش دلار، مسکن دچار تاخیر قیمتی ۳ تا ۶ ماهه می‌شود. در ۳ ماهه نخست جهش ارزی، قیمت دلاری هر متر مربع مسکن در کف قرار دارد و بهترین زمان خرید ملک است.",
                    allocations = listOf(
                        AllocationSlice(AssetClass.PHYSICAL_PROPERTY, 70, propertyPart, "انباشت ثروت پایدار با سند رسمی", "خرید آپارتمان سنددار در پردیس، اندیشه یا مناطق مستعد تهران (منطقه ۵ یا ۲۲)."),
                        AllocationSlice(AssetClass.GOLD_ETF, 20, goldPart, "نقدینگی پشتیبان با بازدهی طلا", "صندوق‌های طلای بدون حباب."),
                        AllocationSlice(AssetClass.USDT_STABLECOIN, 10, usdtPart, "تنخواه‌گردان دلاری", "تتر جهت پوشش هزینه‌های غیرمترقبه.")
                    ),
                    warningsFa = warnings,
                    realEstateContextFa = "خرید قطعی یک یا چند واحد مسکونی در پردیس، پرند، یا مناطق در حال رشد تهران."
                )
            }
        }
    }
}
```

---

## 6. UI/UX Blueprint: The "Wealth Defense Advisor" Card

To make this immediately accessible to Iranian users, a dedicated card will sit on the MuchToman Home Screen:

```
┌────────────────────────────────────────────────────────┐
│ 🛡️ دستیار هوشمند سپر تورمی               [بودجه: ۵۰ م تومن] │
├────────────────────────────────────────────────────────┤
│ بهترین خرید پیشنهادی برای شما:                           │
│ 🥇 طلای آب‌شده ۱۸ عیار نقدی یا صندوق طلا                   │
│                                                        │
│ 📊 نحوه تخصیص پیشنهادی:                                │
│ [█████████████████████████░░░░░░░░] 75% طلا | 25% تتر  │
│ • ۳۷,۵۰۰,۰۰۰ تومان: طلای آب‌شده یا صندوق عیار (بدون حباب) │
│ • ۱۲,۵۰۰,۰۰۰ تومان: تتر (USDT) با نرخ ۲۲۶,۷۰۰ تومان    │
│                                                        │
│ ⚠️ هشدارهای بازار:                                     │
│ • ربع سکه دارای ۳۸٪ حباب است؛ از خرید آن اجتناب کنید!   │
│ • دلار نسبت به مسکن جهش کرده؛ پنجره زمانی خرید ملک باز است.│
│                                                        │
│ [مشاهده جزئیات و تحلیل مسکن]   [تغییر بودجه مد نظر]    │
└────────────────────────────────────────────────────────┘
```

---

## 7. Next Steps & Implementation Roadmap

1. **Worker Integration:** Add Tetherland, Wallex, and TGJU direct bindings to `worker/src/index.ts` with automated health checks and caching.
2. **Engine Implementation:** Incorporate `PurchasingOptimizationEngine` into `Intelligence.kt` and add comprehensive unit tests in `IntelligenceTest.kt`.
3. **Composable Card:** Add `WealthDefenseAdvisorCard` to `UiIntelligence.kt` and integrate into the main feed in `Ui.kt`.
4. **Verification:** Run `./gradlew testFullDebugUnitTest` and assemble release APK.
