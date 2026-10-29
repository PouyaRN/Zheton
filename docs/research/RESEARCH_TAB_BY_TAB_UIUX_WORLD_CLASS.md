# World-Class Fintech UI/UX Architectural Blueprint: Tab-by-Tab Modernization for Chandtoman (چند تومان)

**Author:** Principal Mobile Fintech Product Designer & Staff Android/Compose Systems Architect  
**Document ID:** `RES-CHANDTOMAN-TABS-2026-V1`  
**Status:** Canonical Design & Architecture Reference  
**Target Artifact:** `docs/research/RESEARCH_TAB_BY_TAB_UIUX_WORLD_CLASS.md`  
**Target Codebase:** Android 14+ / Jetpack Compose / Material 3 (`com.doxigo.muchtoman`)  
**Global Benchmarks Analyzed:** Revolut 10, Apple Card & Apple Wallet, Monzo Trends, Robinhood, Copilot Money, Monarch Money, N26

---

## 1. Executive Summary & Design Vision

### 1.1 The Challenge & The Vision
Chandtoman (چند تومان) operates in one of the most volatile and complex personal financial environments in the world. An Iranian user manages multiple asset classes to hedge against inflation: Physical Gold (طلای ۱۸ عیار، سکه امامی، آب‌شده), Fiat Foreign Exchange (دلار، یورو), Cryptocurrencies (USDT, BTC), Tehran Stock Exchange (بورس تهران / TSE), multiple Shetab bank accounts (کارت‌های شتاب), and modern BNPL installments (اسنپ‌پی، دیجی‌پی، وام‌های خرد).

While Chandtoman possesses a remarkably powerful engine—offline SMS ingest, 3D card physics, Glance widgets, and mathematical portfolio aggregation—its UI/UX has organically grown into high-density "card soup" and competing visual hierarchies. 

The goal of this research is to establish a **world-class, forensic UI/UX redesign blueprint for every single tab**, matching the sleekness, emotional resonance, and frictionless ergonomics of global leaders (**Revolut 10**, **Apple Wallet**, **Monzo Trends**, and **Robinhood**), tailored specifically to Persian RTL ergonomics and Iranian financial realities.

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                              THE 5-PILLAR ARCHITECTURE                                      │
├─────────────────┬──────────────────┬──────────────────┬──────────────────┬──────────────────┤
│  1. تب فرمان    │  2. تب بازار     │  3. تب حساب‌ها   │  4. تب دفتر      │  5. تب دیده‌بان  │
│  (Home/Command) │  (Market/Assets) │  (Cards/BNPL)    │  (Transactions)  │  (Reports/Trends)│
├─────────────────┼──────────────────┼──────────────────┼──────────────────┼──────────────────┤
│ Revolut 10 Hub  │ Robinhood +      │ Apple Wallet     │ Monzo Feed +     │ Monzo Trends +   │
│ + Apple Wallet  │ TradingView      │ + Revolut Vault  │ Copilot Money    │ Copilot Money    │
├─────────────────┼──────────────────┼──────────────────┼──────────────────┼──────────────────┤
│ Net Worth Hero  │ Watchlist vs     │ 3D Fluid Fan-out │ Sticky Date Feed │ Burn-down Curve  │
│ Left-to-Spend   │ Portfolio Split  │ Quick PAN/IBAN   │ Instant Filter   │ Interactive      │
│ Clean Bento     │ Live Ticker Tape │ BNPL Timeline    │ Smart Merchant   │ Category Sunburst│
│ Smart Insights  │ Scrubbable Chart │ Health Score     │ Swipe Actions    │ MoM Analytics    │
└─────────────────┴──────────────────┴──────────────────┴──────────────────┴──────────────────┘
```

---

## 2. Foundations: Iranian Fintech Ergonomics & Visual Physics

### 2.1 Color Physics: Semantic Hierarchy on OLED Obsidian
World-class fintech apps avoid generic Material Design saturated primaries. They use deep dark canvases with precision semantic emissive accents.

| Token | Hex Value | Semantic Meaning | Usage in Chandtoman |
| :--- | :--- | :--- | :--- |
| `Canvas.Base` | `#08090A` | Deep Obsidian Base | Main app background (true black / low OLED power) |
| `Surface.Level1` | `#111315` | Elevated Card/Container | Bento cards, list item containers (1px hairline border) |
| `Surface.Level2` | `#181B1E` | High Elevation / Dialogs | Modals, bottom sheets, active tab bar pill |
| `Border.Hairline` | `rgba(255,255,255,0.08)` | Structural Precision | 1px border on all interactive surfaces |
| `Brand.Gold` | `#E5A93C` | "The Sovereign Answer" | App brand, net worth totals, gold bullion, prime highlights |
| `Semantic.Inflow` | `#2E9E5B` | Gain / Cash Inflow | Portfolio positive yield, salary income, savings |
| `Semantic.Outflow` | `#E05252` | Loss / Cash Outflow | Expenses, portfolio drops, debt/installments due |
| `Text.Primary` | `#F4F5F6` | Critical Readout | Large balance numbers, merchant names |
| `Text.Secondary` | `#8C929A` | Supporting Metadata | Timestamps, card masks, percentage deltas |

### 2.2 Typography & Persian Number Physics
* **Typeface:** *Modam* (custom fintech weight) for Persian text; *Aeonik* / *SF Pro Display* / *Inter* for Latin codes (e.g. `USDT`, `BTC`, `IR...`).
* **Tabular Figures (`tnum`):** All financial numbers must enforce fixed-width numerals to eliminate jitter during counter animations (Persian Odometer).
* **Elimination of Rial/Toman Cognitive Friction:**
  Iranian bank SMS feeds operate in **Rial**, while human thought operates in **Toman**. 
  * Rule: **Zero Rial displays on primary user interfaces.** Every amount is displayed in Toman.
  * Compact Unit Pill: Use `ت` with low opacity or a dedicated unit badge, rather than repeating `تومان` in large font sizes.

### 2.3 Motion & Tactile Haptic Hierarchy
Fintech micro-interactions must feel physical, like a finely crafted mechanical safe:
1. **Light Haptic Tick (10ms low amplitude):** Triggered on scrolling past date headers or scrubbing across price charts.
2. **Medium Detent (25ms medium):** Triggered on tab switching, toggling switches, expanding an accordion card.
3. **Double Confirmation (Success):** Triggered on copying card number (PAN), marking an installment paid, or manual transaction save.
4. **Spring Curves:**
   ```kotlin
   val FintechSpring = spring<Float>(
       dampingRatio = Spring.DampingRatioMediumBouncy, // ~0.75
       stiffness = Spring.StiffnessMediumLow          // ~400f
   )
   ```

---

## 3. Global Navigation Shell & Floating Tab Bar

### 3.1 Current Implementation Audit (`TabBar.kt`)
* **Strengths:** Floating island design (`Surface` with 22dp corner radius); gold indicator under active tab; central `+` FAB for quick transaction entry.
* **UX Friction Points:**
  1. **Bottom Content Clashing:** On screens with scrolling lists, the floating tab bar often occludes bottom list items or buttons unless exact padding (`calculateBottomPadding() + 80.dp`) is manually hardcoded across every screen.
  2. **Tab Transition Hard Cut:** Switching tabs is an instantaneous recomposition swap without spatial continuity. There is no subtle horizontal slide or shared element fade.
  3. **Visual Density on Narrow Displays (360dp):** 5 tabs plus a center FAB results in 6 interactive touch targets squeezed into ~340dp width, making accidental taps common.

### 3.2 World-Class Redesign Blueprint (Revolut 10 + Apple Tab Bar)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   WORLD-CLASS FLOATING NAV SHELL                            │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │   [ ⊞ فرمان ]    [ ☵ بازار ]    ( + )    [ 💳 حساب‌ها ]   [ ≡ دفتر ]   │  │
│  │   ══════════                                                          │  │
│  │   (Active Pill with Fluid Morphing Spring)                            │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Key Architecture Upgrades:
1. **Fluid Sliding Indicator:** Instead of static background styling per tab, use an animated floating pill indicator (`Modifier.offset` animated via `Spring`) that slides smoothly underneath the active icon.
2. **Smart Scroll Reactivity:** On high-speed downward scroll in list views (`LazyListState.isScrollInProgress`), the TabBar collapses by 60% into a compact frosted glass indicator; on upward scroll, it instantly expands back.
3. **Haptic Detent:** When moving from tab to tab, invoke `fintechHaptics.tick()` to simulate a physical mechanical selector switch.
4. **Contextual Badge System:** A discrete glowing dot badge (e.g. over "حساب‌ها" when an installment is due in <48h, or over "دفتر" for unverified SMS).

---

## 4. Tab 1: تب فرمان (Home / Command Center)

### 4.1 Current Implementation Audit (`Home.kt` & `Ui.kt`)
* **Current Elements:** `HeroField` (Total net worth in Toman / USD) -> `HomeBentoCockpit` (4 mini tiles) -> `BankCardDeck` (3D stacked cards) -> `QuickActionsDock` -> `CompactFinancialHealthBanner` -> `ContextualPriorityCard` -> `MonthFlow` bar -> `WidgetEngine` slots.
* **UX Friction Points:**
  * **Severe "Card Soup" & Visual Fatigue:** Too many competing elements stacked vertically. The user sees 6 different cards and banners before reaching actual content.
  * **Duplicate Card Deck:** The 3D Bank Card Deck appears on both Tab 1 (Home) and Tab 3 (Cards), diluting the identity of both tabs.
  * **Passive Net Worth Hero:** The big net worth number does not show historical trajectory (no sparkline) or quick toggle between lenses (Toman vs Gold vs Tether).

### 4.2 Benchmark: Revolut 10 Hub + Apple Card + Monzo Payday

```
┌──────────────────────────────────────────────────────────────────────────┐
│                   BENCHMARK: REVOLUT 10 HOME COCKPIT                     │
├──────────────────────────────────────────────────────────────────────────┤
│  [User Avatar / Level]        [Search]          [Notifications / Bell]   │
│                                                                          │
│                              کل دارایی خالص                              │
│                            ۴۸۵,۴۵۰,۰۰۰ تومان                             │
│                  +۴.۲٪ (۱۲,۳۰۰,۰۰۰+) در ۳۰ روز گذشته  ↗                  │
│                     ~~~~~~~~~~~~~~~~~~~~~~~~~~ (Sparkline)               │
│                                                                          │
│       ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐     │
│       │  + واریز   │  │  → انتقال  │  │  ⇄ تبدیل   │  │   ··· بیشتر│     │
│       └────────────┘  └────────────┘  └────────────┘  └────────────┘     │
│                                                                          │
│  ─── وضعیت ماهانه (MONZO LEFT-TO-SPEND) ──────────────────────────────  │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  سقف امن خرج تا حقوق بعدی (۹ روز مانده)         ۱۴,۲۰۰,۰۰۰ تومان   │  │
│  │  [████████████████████░░░░░░░░] ۷۲٪ مصرف شده                       │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ─── بنتو ویجت‌های پین‌شده (PINNED BENTO) ──────────────────────────  │
│  ┌─────────────────────────────┐   ┌──────────────────────────────────┐  │
│  │ ⏳ سررسید قسط اسنپ‌پی        │   │ 🥇 بهترین بازدهی امروز           │  │
│  │ ۳ روز دیگر · ۲,۱۵۰,۰۰۰ تومان│   │ طلای ۱۸ عیار (+۲.۸٪)             │  │
│  └─────────────────────────────┘   └──────────────────────────────────┘  │
│                                                                          │
│  ─── آخرین فعالیت‌ها (RECENT TRANSACTIONS) ───────────────────────────  │
│  (🛍️) دیجی‌کالا                         امروز، ۱۴:۲۰        -۴۸۰,۰۰۰ تومان │
│  (☕) کافه لمیز                          امروز، ۱۰:۱۵         -۹۵,۰۰۰ تومان │
└──────────────────────────────────────────────────────────────────────────┘
```

### 4.3 Redesign Blueprint for Chandtoman: Tab 1

```
┌──────────────────────────────────────────────────────────────────────────┐
│              PROPOSED HOME ARCHITECTURE (COMMAND CENTER)                 │
├──────────────────────────────────────────────────────────────────────────┤
│  [ پروفایل و امنیت ]                             [ وضعیت همگام‌سازی 🟢 ] │
│                                                                          │
│  [ZONE 1: HERO WEALTH COCKPIT]                                           │
│  دارایی کل (خالص)                                                        │
│  ۴۸۵,۲۵۰,۰۰۰ ت                                                           │
│  [+۳.۴٪ در ۷ روز گذشته]  ·  [تومان ▼ | طلا | تتر]                        │
│  ─────────────────╱╲───╱╲─────── (Interactive Scrubbable Sparkline)      │
│                                                                          │
│  [ZONE 2: VELOCITY & ACTION DOCK]                                        │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌────────────────┐   │
│  │  (+) ثبت خرج │ │  (⇄) تبدیل   │ │  (📊) گزارش  │ │  (🔒) حالت امن  │   │
│  └──────────────┘ └──────────────┘ └──────────────┘ └────────────────┘   │
│                                                                          │
│  [ZONE 3: SMART SPENDING DIAL (LEFT-TO-SPEND BUFFER)]                    │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  🛡️ بودجه امن روزانه: ۸۵۰,۰۰۰ ت   │  خرج تا این لحظه: ۱۲.۴ م       │  │
│  │  [||||||||||||||||||||||||||||........] ۸ روز تا سیکل حقوق بعدی    │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  [ZONE 4: MODULAR BENTO INTELLIGENCE (2x2 GRID)]                         │
│  ┌─────────────────────────────┐   ┌──────────────────────────────────┐  │
│  │ ⚡ قسط فوری: اسنپ‌پی         │   │ 📈 سهم طلا در سبد                │  │
│  │ ۱,۴۵۰,۰۰۰ ت (پس‌فردا)       │   │ ۴۲٪ (حفاظت عالی در برابر تورم)   │  │
│  └─────────────────────────────┘   └──────────────────────────────────┘  │
│  ┌─────────────────────────────┐   ┌──────────────────────────────────┐  │
│  │ 💳 حساب جاری فعال           │   │ 💡 پیشنهاد هوشمند                │  │
│  │ بانک سامان: ۱۸,۲۰۰,۰۰۰ ت    │   │ موجودی راکد ریالی بالا است!      │  │
│  └─────────────────────────────┘   └──────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

#### Technical Compose Specifications:
1. **Interactive Scrubbable Sparkline on Hero:** 
   * As the user drags across the 30-day sparkline, the balance smoothly updates to that point in time with haptic tick feedback (`FintechHaptics.tick()`).
   * Lens toggle enables viewing wealth in **Toman**, **Grams of 18k Gold**, or **USDT**.
2. **Decluttering Action:** Remove `BankCardDeck` entirely from Tab 1. Reserve card presentation exclusively for Tab 3 (`CARDS`), resolving visual redundancy.
3. **Monzo-Inspired "Left-to-Spend" Dial:** Replace passive `MonthFlow` with active daily guidance: `(Remaining Budget) / (Days remaining until Payday Anchor Day)`.

---

## 5. Tab 2: تب بازار و دارایی‌ها (Market & Assets / Portfolio)

### 5.1 Current Implementation Audit (`MarketUi.kt`)
* **Current Elements:** Market rates (Gold 18k, Dollar, Coins, Crypto, TSE Stocks) and user personal holdings displayed in an alternating list. Filter chips at the top.
* **UX Friction Points:**
  1. **Blurring of Market vs Personal Holdings:** Users who open the tab to quickly check the price of Gold or Dollar are confronted with their own portfolio balances and holding cards, and vice versa.
  2. **Passive Charts:** Charts are either static paths or require cumbersome navigation to view timeframes (1D, 1W, 1M, 1Y).
  3. **Lack of Performance Benchmarking:** The user cannot immediately answer: *"Did my portfolio beat Gold or USD inflation this month?"*

### 5.2 Benchmark: Robinhood + TradingView + Binance Lite

```
┌──────────────────────────────────────────────────────────────────────────┐
│                   BENCHMARK: ROBINHOOD ASSET HUB                         │
├──────────────────────────────────────────────────────────────────────────┤
│  [ SEGMENTED CONTROL:  (●) دارایی‌های من    |    (○) بازار و دیده‌بان ]  │
│                                                                          │
│  ارزش سبد دارایی                                                         │
│  ۳۴۰,۱۵۰,۰۰۰ تومان                                                       │
│  +۱۸,۴۰۰,۰۰۰ تومان (+۵.۷٪)  [۱ ماه اخیر]                                 │
│                                                                          │
│   1D    1W    1M    3M    1Y    ALL                                      │
│  ───────────────────────────────────                                     │
│                ╭──────╮                                                  │
│        ╭───────╯      ╰───────── (Robinhood Smooth Neon Area Chart)      │
│  ──────╯                                                                 │
│                                                                          │
│  ─── ترکیب سبد سرمایه‌گذاری (ASSET ALLOCATION) ───────────────────────── │
│  [ 🥇 طلا ۵۵٪ ] [ 💵 ارز ۲۵٪ ] [ 🪙 کریپتو ۱۵٪ ] [ 📈 بورس ۵٪ ]          │
│                                                                          │
│  ─── دارایی‌های شما (YOUR HOLDINGS) ───────────────────────────────────  │
│  🥇 طلای ۱۸ عیار         ۳۴.۵ گرم          ۱۸۵,۲۰۰,۰۰۰ ت  (+۲.۴٪) ↗     │
│  🌕 تمام سکه امامی        ۲ عدد             ۱۰۸,۰۰۰,۰۰۰ ت  (+۱.۱٪) ↗     │
│  💎 تتر (USDT)           ۷۵۰ واحد          ۴۸,۲۰۰,۰۰۰ ت   (+۰.۲٪) ↗     │
└──────────────────────────────────────────────────────────────────────────┘
```

### 5.3 Redesign Blueprint for Chandtoman: Tab 2

```
┌──────────────────────────────────────────────────────────────────────────┐
│              PROPOSED MARKET & ASSETS TAB ARCHITECTURE                   │
├──────────────────────────────────────────────────────────────────────────┤
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │         [ ● سبد دارایی من (Portfolio) ]   [ ○ دیده‌بان بازار (Live) ] │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ─── حالت ۱: سبد دارایی من (PORTFOLIO VIEW) ───────────────────────────  │
│  ارزش کل دارایی‌ها: ۳۴۲,۸۰۰,۰۰۰ ت  [+۳۸.۴ م سود کل]                      │
│  شاخص برتری بر تورم: +۷.۲٪ بالاتر از بازدهی طلا 🛡️                       │
│                                                                          │
│  [ ترکیب دارایی: نوار متناسب ]                                           │
│  [████████████ طلای ۱۸ و سکه ۵۲٪ ][████ تتر ۲۲٪ ][██ بورس ۱۶٪ ][█ نقد ۱۰٪]│
│                                                                          │
│  [فیلتر سریع]: [ همه ] [ 🥇 طلا و سکه ] [ 💎 کریپتو ] [ 📈 بورس ]       │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ 🥇 طلای ۱۸ عیار                                                     │  │
│  │ دارایی شما: ۲۴.۲۰۰ گرم                                             │  │
│  │ ارزش روز: ۱۳۲,۵۰۰,۰۰۰ ت                      سود/زیان: +۱۸,۲۰۰,۰۰۰ │  │
│  │ قیمت لحظه‌ای: ۵,۴۷۰,۰۰۰ ت (+۱.۸٪)            میانگین خرید: ۴.۷ م   │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ─── حالت ۲: دیده‌بان بازار زنده (LIVE MARKET VIEW) ───────────────────  │
│  [ نوار تیکر متحرک با ضربان زنده / Live Ticker Tape ]                    │
│  دلار آزاد: ۹۲,۴۰۰ ت (+۰.۸٪) | تتر: ۹۳,۱۰۰ ت | طلای ۱۸: ۵,۴۷۰,۰۰۰ ت     │
│                                                                          │
│  [ کارت مقایسه حباب سکه و طلا (Gold Coin Bubble Gauge) ]                 │
│  حباب سکه امامی: ۱۹.۴٪ (ریسک بالا)  |  حباب ربع سکه: ۴۱.۲٪ (بسیار پرریسک)│
└──────────────────────────────────────────────────────────────────────────┘
```

#### Key Functional Enhancements:
1. **Strict Segmented Pill:** Eliminate visual confusion by introducing a top-level segmented toggle:
   * **سبد دارایی من (My Portfolio):** Aggregated valuation, P&L (سود/زیان), average purchase price, allocation bar.
   * **دیده‌بان بازار (Live Market):** Real-time rates, 24h delta, interactive candle/line chart, Gold Coin Bubble gauge (حباب‌سنج سکه).
2. **Gold Coin Bubble Visual Gauge:** Essential for the Iranian market. Visual gauge showing whether Emami, Nim, or Rob coins have high premiums over melt value.
3. **Scrubbable Compose Chart with Mechanical Haptics:**
   ```kotlin
   Modifier.pointerInput(Unit) {
       detectDragGestures { change, _ ->
           val index = calculateNearestIndex(change.position.x)
           if (index != currentIndex) {
               currentIndex = index
               fintechHaptics.tick() // Subtle mechanical vibration
           }
       }
   }
   ```

---

## 6. Tab 3: تب حساب‌ها و تعهدات (Accounts, Cards & Commitments)

### 6.1 Current Implementation Audit (`BankCard3D.kt` & `Ui.kt`)
* **Current Elements:** Iranian bank cards rendered via Canvas 3D rotation with gyro sensor effect. Below it, a list of accounts and balances.
* **UX Friction Points:**
  1. **Underutilized 3D Card:** While visually stunning, interacting with the card (e.g. copying PAN, viewing CVV2, checking expiry date) requires cumbersome tapping or modal navigation.
  2. **Disjointed Commitments/Installments:** Bank loans and BNPL (اسنپ‌پی، دیجی‌پی) are housed in separate sheets or pushed down beneath the cards, making it hard to see total upcoming cash outflow.
  3. **No Apple Wallet Accordion Fan-out:** Cards are rendered either stacked or horizontally swiped, but cannot be expanded into an interactive fan-out deck.

### 6.2 Benchmark: Apple Wallet + Revolut Card Management + Kaspi.kz

```
┌──────────────────────────────────────────────────────────────────────────┐
│                   BENCHMARK: APPLE WALLET FAN-OUT                        │
├──────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   ┌──────────────────────────────────────────────────────────────────┐   │
│   │ 🟦 بانک سامان (Saman Blu)                     •••• 8421          │   │
│   │ موجودی: ۲۴,۵۰۰,۰۰۰ تومان                                         │   │
│   └──────────────────────────────────────────────────────────────────┘   │
│         ┌────────────────────────────────────────────────────────────┐   │
│         │ 🟥 بانک ملت (Mellat Mellat)              •••• 1920         │   │
│         │ موجودی: ۸,۱۰۰,۰۰۰ تومان                                    │   │
│         └────────────────────────────────────────────────────────────┘   │
│               ┌──────────────────────────────────────────────────────┐   │
│               │ 🟨 بانک رسالت (Resalat Qardh)            •••• 4410   │   │
│               │ موجودی: ۱,۲۰۰,۰۰۰ تومان                              │   │
│               └──────────────────────────────────────────────────────┘   │
│                                                                          │
│   [ When a card is tapped, it slides to the top; others smoothly fan ]   │
│   [ down with spring physics. Card shows copy buttons for PAN/Shaba. ]   │
│                                                                          │
│   ─── جزئیات کارت انتخاب‌شده (ACTIVE CARD ACTIONS) ───────────────────   │
│   [ 📋 کپی ۱۶ رقم کارت ]     [ 🏛️ کپی شماره شبا ]     [ 👁️ نمایش رمزها ]  │
│                                                                          │
│   ─── تراکنش‌های اخیر این کارت (RECENT TRANSACTIONS) ────────────────    │
│   پذیرنده: فروشگاه جانبو                   امروز، ۱۱:۳۰     -۳۴۰,۰۰۰ ت   │
│   پذیرنده: سوپرمارکت یاران                 دیروز، ۲۱:۰۵     -۱۲۰,۰۰۰ ت   │
└──────────────────────────────────────────────────────────────────────────┘
```

### 6.3 Redesign Blueprint for Chandtoman: Tab 3

```
┌──────────────────────────────────────────────────────────────────────────┐
│              PROPOSED CARDS & COMMITMENTS ARCHITECTURE                   │
├──────────────────────────────────────────────────────────────────────────┤
│  مجموع موجودی بانک‌ها: ۴۸,۸۵۰,۰۰۰ ت  ·  تعهدات پیش‌رو: ۶,۴۰۰,۰۰۰ ت      │
│                                                                          │
│  [SECTION 1: APPLE WALLET INTERACTIVE CARD FAN-OUT]                      │
│  (Cards vertically overlap; tapping any card expands it with spring)     │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ 🔷 بلو بانک (سامان)                        IR74 0560 ...           │  │
│  │ ۶۲۱۹-۸۶۱۰-••••-۴۳۱۲                                                │  │
│  │ موجودی: ۲۸,۴۰۰,۰۰۰ تومان                    انقضا: ۰۶/۰۷           │  │
│  │ [ 📋 کپی کارت (با بازخورد هپتیک) ]   [ 🏛️ کپی شبا ]   [ ⚙️ تنظیمات ]│  │
│  └────────────────────────────────────────────────────────────────────┘  │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ 🔶 بانک پاسارگاد                           •••• ۵۵۴۱               │  │
│  │ موجودی: ۲۰,۴۵۰,۰۰۰ تومان                                            │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  [SECTION 2: INTEGRATED BNPL & LOAN TIMELINE (اسنپ‌پی و وام‌های بانکی)]  │
│  ─── تعهدات و اقساط سررسید نزدیک ──────────────────────────────────────  │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ ⚡ اسنپ‌پی (قسط ۲ از ۴)                         ۲ روز مانده 🔴     │  │
│  │ مبلغ: ۱,۲۵۰,۰۰۰ تومان                                              │  │
│  │ [████████████████████░░░░░░░░░░░░] ۵۰٪ تسویه شده                   │  │
│  │ [ دکمه: ثبت تسویه شد ✓ ]                                           │  │
│  ├────────────────────────────────────────────────────────────────────┤  │
│  │ 🏦 وام ازدواج / قرض‌الحسنه رسالت               ۱۴ روز مانده 🟢     │  │
│  │ مبلغ: ۳,۱۰۰,۰۰۰ تومان (قسط ۲۴ از ۶۰)                               │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

#### Key Implementation Details:
1. **One-Tap PAN & Shaba Copy with Tactile Confirmation:**
   ```kotlin
   IconButton(onClick = {
       clipboardManager.setText(AnnotatedString(account.pan))
       fintechHaptics.doubleSuccess() // Apple Card tactile feedback
       showToast("شماره کارت با موفقیت کپی شد")
   })
   ```
2. **Unified Bank-to-Transaction Filter:** Tapping a bank card filters Tab 4 (`LEDGER`) to display only transactions belonging to that specific card mask!

---

## 7. Tab 4: تب دفتر تراکنش‌ها (Ledger / Timeline)

### 7.1 Current Implementation Audit (`Timeline.kt`)
* **Current Elements:** `TimelineScreen` with lazy column of daily grouped transactions, category chips, search bar, and bulk edit sheet.
* **UX Friction Points:**
  1. **Cryptic Merchant Names:** Iranian POS terminals output names like `خريد-شاپرک-پايانه 9823412` or `فروشگاه 4410`. Currently, unless manually aliased, they clutter the feed.
  2. **Lack of Inline Gesture Categorization:** To change an incorrect category, the user must open a modal sheet instead of a quick swipe-to-categorize action.
  3. **No Visual Daily Net Flow:** Daily headers show dates, but don't clearly summarize: *"Today you spent 450,000 Toman across 3 purchases."*

### 7.2 Benchmark: Monzo Activity Feed + Copilot Money

```
┌──────────────────────────────────────────────────────────────────────────┐
│                   BENCHMARK: MONZO SMART ACTIVITY FEED                   │
├──────────────────────────────────────────────────────────────────────────┤
│  [ 🔍 جستجو در پذیرندگان و یادداشت‌ها... ]           [ فیلتر پیشرفته ⚙️ ] │
│  [ همه ] [ 🍔 خوراک ] [ 🚕 حمل‌ونقل ] [ 🛒 سوپرمارکت ] [ ⚠️ بدون دسته ]   │
│                                                                          │
│  ─── امروز، دوشنبه ۱۴ آبان  (مجموع خرج: ۵۴۰,۰۰۰ تومان) ────────────────  │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ (🛍️) دیجی‌کالا                                   ۱۶:۳۰              │  │
│  │      لوازم جانبی موبایل · بانک سامان              -۴۲۰,۰۰۰ ت       │  │
│  ├────────────────────────────────────────────────────────────────────┤  │
│  │ (☕) کافه ویونا                                  ۱۰:۱۵              │  │
│  │      خوراک و کافه · اسنپ‌پی                      -۱۲۰,۰۰۰ ت         │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ─── دیروز، یکشنبه ۱۳ آبان  (مجموع خرج: ۸۵,۰۰۰ تومان | درآمد: ۳.۵ م) ─  │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ (💼) واریز حقوق / پروژه فری‌لنس                   ۱۴:۰۰             │  │
│  │      درآمد متفرقه · بانک پاسارگاد                 +۳,۵۰۰,۰۰۰ ت ↗   │  │
│  ├────────────────────────────────────────────────────────────────────┤  │
│  │ (🚕) اسنپ                                       ۰۸:۴۵              │  │
│  │      تاکسی اینترنتی · بانک ملت                   -۸۵,۰۰۰ ت          │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

### 7.3 Redesign Blueprint for Chandtoman: Tab 4

```
┌──────────────────────────────────────────────────────────────────────────┐
│              PROPOSED TIMELINE ARCHITECTURE                              │
├──────────────────────────────────────────────────────────────────────────┤
│  [STICKY TOP CONTROL BAR]                                                │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │  🔍 جستجوی هوشمند تراکنش، پذیرنده، شماره شبا...           [ فیلتر ]│  │
│  └────────────────────────────────────────────────────────────────────┘  │
│  [ فیلترهای سریع ]: (● همه) (🚨 نیاز به بررسی ۵) (🍔 خوراک) (⚡ اقساط)    │
│                                                                          │
│  [STICKY DATE HEADER]                                                    │
│  امروز · ۱۴ آبان ۱۴۰۴                         مجموع خرج: ۵۶۰,۰۰۰ ت 🔴     │
│                                                                          │
│  [SWIPEABLE TRANSACTION ROW]                                             │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ [SWIPE RIGHT -> تایید یا مخفی]   [SWIPE LEFT -> تغییر سریع دسته]   │  │
│  │                                                                    │  │
│  │  (🛒) فروشگاه هایپراستار (افق کوروش)             ۱۲:۴۵              │  │
│  │       سوپرمارکت و خواربار  ·  سامان •••• ۴۳۱۲      -۳۴۰,۰۰۰ ت       │  │
│  │       [ برچسب: خرید منزل ]                                         │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  [UNPARSED / STRANGE SMS ALERT BANNER]                                   │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ ⚠️ ۳ پیامک بانکی بدون دسته‌بندی قطعی است.                            │  │
│  │ با یک لمس هوشمند دسته‌بندی کنید [بررسی سریع >]                     │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

#### Key Architecture Upgrades:
1. **Swipe-to-Categorize Motion:** Swiping a transaction row to the left reveals 4 quick category pills (`خوراک`, `خرید`, `حمل‌ونقل`, `قسط`) without leaving the timeline.
2. **Intelligent Persian Merchant Sanitization:** Replace raw bank text (`خريد از پايانه 441098`) with verified clean merchant entities (`اسنپ`, `تپسی`, `دیجی‌کالا`, `افق کوروش`, `هایپراستار`, `کافه‌بازار`).
3. **Sticky Day Balance Summaries:** As the user scrolls, each day header pins to the top showing the net spend of that calendar day.

---

## 8. Tab 5: تب دیده‌بان و گزارش‌ها (Reports & Analytics)

### 8.1 Current Implementation Audit (`ReportsUi.kt`)
* **Current Elements:** Window selector (این ماه، ۳ ماه، ۶ ماه، ۱ سال، همه), category donut chart, income/expense/net stats, historical net worth line chart.
* **UX Friction Points:**
  1. **Passive Donut Chart:** The donut chart is static and does not allow interactive multi-level drill-down (e.g. clicking "خوراک" to inspect sub-spending on cafes vs groceries).
  2. **Absence of Predictive "Burn-Down" Curve:** The user cannot visualize whether their spending rate will deplete their balance before the next payday.
  3. **No Month-over-Month (MoM) Delta Insights:** Does not highlight: *"You spent 34% more on dining out compared to last month."*

### 8.2 Benchmark: Monzo Trends + Revolut Analytics + Copilot Money

```
┌──────────────────────────────────────────────────────────────────────────┐
│                   BENCHMARK: MONZO TRENDS BURN-DOWN                      │
├──────────────────────────────────────────────────────────────────────────┤
│  سیکل مالی: ۱ آبان تا ۳۰ آبان  (روز ۲۰ از ۳۰)                             │
│                                                                          │
│  خرج کل تا این لحظه: ۱۸,۴۰۰,۰۰۰ تومان    (پیش‌بینی پایان ماه: ۲۳,۵۰۰,۰۰۰)│
│                                                                          │
│   تومان                                                                  │
│  ۳۰م ┬                                                                   │
│      │                                    ╭┈┈┈ (مسیر پیش‌بینی خطرناک)   │
│  ۲۰م │                           ╭────────╯                              │
│      │                  ╭────────╯                                       │
│  ۱۰م │         ╭────────╯ ── ── ── ── ── ── (مسیر بودجه مطلوب)           │
│      │ ╭───────╯                                                         │
│   ۰م ┴─┴────┴────┴────┴────┴────┴────┴────┴────                          │
│        ۱    ۵   ۱۰   ۱۵   ۲۰   ۲۵   ۳۰ آبان                              │
│                                                                          │
│  ─── مقایسه با ماه قبل (MONTH-OVER-MONTH DELTA) ──────────────────────── │
│  🍔 خوراک و رستوران      ۸,۴۰۰,۰۰۰ ت      [ +۱۸٪ بیشتر از مهر ] 🔺       │
│  🚕 تاکسی و حمل‌ونقل     ۲,۱۰۰,۰۰۰ ت      [ -۵٪ کمتر از مهر ]   🔻       │
│  🛍️ خرید البسه           ۳,۸۰۰,۰۰۰ ت      [ +۴۲٪ بیشتر از مهر ] 🔺       │
└──────────────────────────────────────────────────────────────────────────┘
```

### 8.3 Redesign Blueprint for Chandtoman: Tab 5

```
┌──────────────────────────────────────────────────────────────────────────┐
│              PROPOSED REPORTS & ANALYTICS ARCHITECTURE                   │
├──────────────────────────────────────────────────────────────────────────┤
│  [CYCLE SELECTOR:  (● سیکل حقوق: ۲۵م تا ۲۵م)   |   (○ ماه‌های شمسی) ]    │
│                                                                          │
│  [SECTION 1: PREDICTIVE BURN-DOWN TRAJECTORY (منحنی پیش‌بینی مصرف)]       │
│  خرج تا الان: ۱۴,۲۰۰,۰۰۰ ت   │  بودجه باقی‌مانده: ۵,۸۰۰,۰۰۰ ت            │
│  وضعیت: 🟢 در محدوده سبز (روزانه تا ۵۸۰,۰۰۰ ت مجاز هستید)                │
│  [نمودار اسپیلاین دوگانه: خط مصرف واقعی در برابر خط بودجه ایده‌آل]        │
│                                                                          │
│  [SECTION 2: INTERACTIVE DRILL-DOWN SUNBURST / DONUT]                    │
│  (لمس هر قطاع دونات، زیردسته‌های آن را باز می‌کند)                       │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │   ◎ دونات تعاملی مرکزی                                             │  │
│  │   دسته انتخاب‌شده: 🍔 خوراک (۴۲٪ از کل مخارج)                       │  │
│  │   مبلغ: ۵,۹۰۰,۰۰۰ تومان                                            │  │
│  │   زیردسته‌ها:                                                      │  │
│  │   • سوپرمارکت و خواربار: ۳,۸۰۰,۰۰۰ ت (۶۴٪)                          │  │
│  │   • رستوران و کافه: ۱,۸۰۰,۰۰۰ ت (۳۰٪)                              │  │
│  │   • سفارش آنلاین: ۳۰۰,۰۰۰ ت (۶٪)                                   │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  [SECTION 3: CASHFLOW SANKEY / WATERFALL SUMMARY (جریان نقدینگی)]        │
│  درآمد کل (+۳۲ م)  ─►  اقساط و تعهدات (-۶.۴ م)  ─►  مخارج روزمره (-۱۴ م) │
│                   └──► پس‌انداز و طلای خریداری‌شده (+۱۱.۶ م / ۳۶٪) 🥇    │
└──────────────────────────────────────────────────────────────────────────┘
```

#### Key Analytical Upgrades:
1. **Payday Cycle Alignment (`CycleSpec`):** Iranians frequently get paid on the 25th or 30th of the solar month. The burn-down chart calculates the window from Payday-to-Payday rather than strictly 1st-to-30th.
2. **Interactive Donut Drill-Down:** Clicking a category in the donut smoothly scales the donut segments and morphs the subtitle into the category's merchant breakdown with zero page reloads.
3. **Cashflow Waterfall (Sankey Concept):** Clear visual separation showing: Inflow -> Involuntary Commitments (Rent, Loans) -> Discretionary Living Expenses -> Net Wealth Generated.

---

## 9. Comparative Architecture: Current vs World-Class

| Dimension | Chandtoman Current State | World-Class Redesign Target | Global Benchmark Reference |
| :--- | :--- | :--- | :--- |
| **Nav & Tabs** | Static floating capsule; 5 tabs + center FAB; instant recomposition cut | Spring-driven sliding pill indicator; smart auto-hide on scroll; haptic detents | Revolut 10 / Apple Tab Bar |
| **Tab 1: Home** | High-density card soup (Hero + 4-tile Bento + 3D Card + Health Banner + Dock) | 3-Zone Hierarchy: Hero with scrubbable sparkline -> Action Dock -> Left-to-Spend -> 2x2 Bento | Revolut 10 Hub + Monzo Payday |
| **Tab 2: Assets** | Combined holdings and market prices in one list; passive sparklines | Strict Segmented Control: `My Portfolio` vs `Live Market`; Coin Bubble Gauge; Scrubbable charts | Robinhood + TradingView |
| **Tab 3: Cards** | Isolated 3D card tilt; list of bank accounts below; commitments in separate sheets | Apple Wallet Accordion Fan-out; 1-tap PAN/Shaba copy with haptics; Integrated BNPL timeline | Apple Card + Kaspi.kz |
| **Tab 4: Ledger** | Standard vertical list with daily dates; modal categorization sheet | Sticky date headers with daily net sum; swipe-to-categorize gestures; merchant logo clearing | Monzo Activity + Copilot Money |
| **Tab 5: Reports** | Static category donut; fixed month windows; historic net worth line | Predictive burn-down curve; Payday-anchored cycles; interactive donut drill-down; MoM deltas | Monzo Trends + Revolut Analytics |

---

## 10. Phased Implementation Roadmap

### Phase 1: High-Impact Polish & Friction Removal (Week 1–2)
1. **Clean up Tab 1 (Home):** Remove redundant `BankCardDeck` from Home (keep it exclusively on Tab 3 `CARDS`). Consolidate competing banners into a focused 2x2 Bento.
2. **Tab 3 One-Tap Copy:** Implement one-tap copy buttons for Card PAN (16 digits) and Shaba (IBAN) directly on the cards with dual-tick haptics (`FintechHaptics.doubleSuccess()`).
3. **TabBar Spring Physics:** Replace the discrete selected tab box with a smooth sliding indicator pill using Compose `animateDpAsState` and `fintechHaptics.tick()`.

### Phase 2: Core Tab Structural Redesigns (Week 3–5)
1. **Tab 2 Segmented Control:** Separate `سبد دارایی من` from `دیده‌بان بازار زنده`. Add Iranian Gold Coin Bubble gauge.
2. **Tab 3 Apple Wallet Accordion Fan-out:** Implement vertical card stack fan-out on tap using `Modifier.offset` spring animations.
3. **Tab 4 Swipe Gestures:** Implement swipe-to-categorize on transaction rows using `SwipeToDismissBox` or custom pointer input.

### Phase 3: Advanced Analytics & Interactive Visualizations (Week 6–8)
1. **Tab 5 Predictive Burn-down Chart:** Canvas-rendered daily spend trajectory with ideal pace line based on `CycleSpec` (anchor payday).
2. **Interactive Donut Drill-down:** Canvas animated sweep-angle transitions when tapping categories.
3. **Scrubbable Area Charts:** Add drag-to-scrub with haptic ticks across both Tab 1 (Net worth sparkline) and Tab 2 (Market asset charts).

---

## 11. Conclusion
By transforming Chandtoman from a passive balance recorder into an **interactive, predictive, and tactile financial cockpit**, the app can achieve the precision and aesthetic maturity of **Revolut 10** and **Apple Card**, while deeply honoring the unique high-inflation and multi-asset dynamics of the Iranian user.
