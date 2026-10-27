# Research: Iranian Bank Cards System & Android Glance Widget Suite UI/UX

**Document Version:** 1.0.0  
**Target Platform:** Android (Jetpack Compose & Jetpack Glance Material 3) / Web PWA  
**Focus:** High-Fidelity Bank Card UI/UX, Shetab Interoperability, and Desktop-Grade Android Home Screen Widgets  

---

## Executive Summary & Problem Diagnosis

This research investigates the ergonomic and technical failures in the current ChandToman card and widget implementations, benchmarked against leading Iranian fintechs (BluBank, Vipod/Pasargad, Bankino, Digipay) and international standards (Apple Card, Revolut, Monzo).

### Current Pain Points in Codebase:
1. **RTL Digit Scrambling in PAN Entry:**
   In `Ui.kt` (lines 2968 & 5649) and `BankCard3D.kt` (line 510), card numbers are rendered inside RTL layout trees without explicit `LayoutDirection.Ltr` or bidirectional markers (`\u202A ... \u202C`). When 16-digit PANs are formatted with spaces (`۶۰۳۷  ۹۹۱۲  ۳۴۵۶  ۷۸۹۰`), RTL text shaping renders the first chunk on the **far right** and the last chunk on the **far left**, producing a backwards card layout.
2. **Broken Mask Typing Mechanics:**
   In `Ui.kt` (line 5650), when `revealPan` is false, typing into a masked draft (`•••• •••• •••• 1234`) appends characters to bullet glyphs, corrupting the stored value.
3. **Absence of Standalone / Manual Cards:**
   `ADR-0004` tightly coupled cards to SMS-tracked accounts (`one account = one card`). Users who need to store cards for quick copy/sharing (family members, non-SMS savings accounts, Sheba numbers for receiving pay) cannot register a card without receiving a parsed bank SMS.
4. **Widget Limitations:**
   `GlanceWidget.kt` and `Widget.kt` provide only a basic total net worth widget with static styling and no modularity for safe spending, quick expense recording, card sharing, or market tracking.

---

## Part 1: Bank Card System (کارت‌های بانکی شتاب)

### 1.1 Shetab Card Topology & Formatting Standards

Iranian bank debit cards follow ISO/IEC 7812 standards:
- **Length:** Exactly 16 decimal digits.
- **Segmentation:** 4 blocks of 4 digits (`XXXX  XXXX  XXXX  XXXX`).
- **Visual Directionality:** **Always Left-to-Right (LTR)** regardless of language or app RTL setting. The first 6 digits (Bank Identification Number - BIN) must appear on the left.
- **Checksum:** Luhn Algorithm (Mod-10).

```
   [ BIN (6 Digits) ]     [ Account & Type (9 Digits) ]   [ Check ]
   ------------------     -----------------------------   ---------
     6 0 3 7   9 9             1 2   3 4 5 6   7 8 9          0
   [  Block 1  ]           [  Block 2  ]    [  Block 3  ]   [Block 4]
```

### 1.2 Canonical Iranian Bank BIN Table & Brand Color Standards

| Bank Name (Farsi) | Bank Name (En) | Primary BIN | Secondary / Other BINs | Brand Color Primary | Brand Color Secondary / Gradient | Accent |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **بانک ملی ایران** | Bank Melli | `603799` | - | `#0D47A1` (Royal Blue) | `#1976D2` | `#FFC107` (Gold) |
| **بانک ملت** | Bank Mellat | `610433` | `991975` | `#B71C1C` (Mellat Red) | `#880E4F` | `#E0E0E0` (Silver) |
| **بلو بانک (سامان)** | BluBank | `621986` (Blu sub-range) | `8600**` | `#1A237E` (Dark Navy) / `#E53935` / `#43A047` | `#000000` / `#E0E0E0` | `#00E676` / `#FF3D00` |
| **بانک سامان** | Bank Saman | `621986` | - | `#0091EA` (Sky Blue) | `#01579B` | `#FFFFFF` |
| **بانک پاسارگاد** | Bank Pasargad | `502229` | `639347` | `#212121` (Charcoal Black) | `#424242` | `#FFD700` (Pasargad Gold) |
| **بانک تجارت** | Bank Tejarat | `627353` | `585983` | `#1565C0` (Blue) | `#0D47A1` | `#4CAF50` (Tejarat Teal) |
| **بانک صادرات ایران** | Bank Saderat | `603769` | - | `#1A237E` (Deep Blue) | `#283593` | `#90CAF9` |
| **بانک سپه** | Bank Sepah | `589210` | `627381` (Ansar), `639599` (Ghavamin), `505801` (Kosar) | `#D32F2F` (Crimson) | `#B71C1C` | `#FFD54F` |
| **بانک کشاورزی** | Keshavarzi | `603770` | `639217` | `#2E7D32` (Forest Green) | `#1B5E20` | `#FBC02D` |
| **بانک مسکن** | Bank Maskan | `628023` | - | `#E65100` (Maskan Orange) | `#BF360C` | `#FFE082` |
| **بانک آینده** | Ayandeh | `636214` | - | `#4A148C` (Purple) | `#311B92` | `#FFB300` |
| **بانک شهر** | Shahr | `502806` | - | `#C62828` (Dark Red) | `#B71C1C` | `#E0E0E0` |
| **بانک رفاه کارگران** | Refah | `589463` | - | `#0277BD` (Refah Cyan) | `#01579B` | `#FF8F00` |
| **بانک رسالت** | Resalat | `504172` | - | `#00695C` (Teal) | `#004D40` | `#80CBC4` |
| **بانک مهر ایران** | Mehr Iran | `606373` | - | `#00897B` (Jade Green) | `#004D40` | `#FFCA28` |
| **بانک اقتصاد نوین** | Eghtesad Novin | `627412` | - | `#4E342E` (Espresso) | `#3E2723` | `#FFA000` |
| **پست بانک ایران** | Post Bank | `627760` | - | `#2E7D32` (Leaf Green) | `#1B5E20` | `#FFFFFF` |
| **بانک پارسیان** | Parsian | `622106` | `639194` | `#880E4F` (Wine Red) | `#4A148C` | `#FFD700` |

### 1.3 Material 3 Custom Card Themes & Finishes

Beyond standard bank colors, modern financial apps provide luxury material finishes:
1. **Obsidian Matte (آبسیدین مات):** Deep charcoal `#121214` with subtle radial metallic sheen, brushed aluminum highlights, and gold foil text.
2. **24K Gold Bar (شمش طلا):** Dual-layer brushed linear gradient (`#E5C07B` -> `#D19A66` -> `#F0D394`), subtle specular reflection angle.
3. **Cyber Hologram (هولوگرافیک):** Iridescent duotone gradient (`#8A2387` -> `#E94057` -> `#F27121`) with prismatic shimmer angle reacting to device gyroscope.
4. **Emerald Velvet (زمرد سلطنتی):** Rich deep emerald (`#064e3b` -> `#022c22`) with chamfered gold borders.
5. **Titanium Frost (تیتانیوم روشن):** Frosted glassmorphic texture (`#2C3E50` -> `#3498DB` -> `#BDC3C7`).

### 1.4 Sheba (IBAN) & QR Code Architecture
- **Structure:** `IR` + 2 check digits + 22 account/branch identification digits = 26 alphanumeric chars.
- **Copy/Share Mechanics:**
  - One-tap button copies clean alphanumeric string (e.g. `IR820170000000123456789012`).
  - Formatted display: `IR82 - 0170 - 0000 - 0012 - 3456 - 7890 - 12`.
  - Dynamic QR Generator: Encodes standard `IR:IR8201700...` or banking deep links compatible with Iranian mobile banking apps.

---

## Part 2: Android Jetpack Glance Widget Suite

### 2.1 The 6-Pillar Widget Architecture

```
                               ChandToman Glance Widget Suite
      ┌───────────────────────────────────────┬──────────────────────────────────────┐
      │                                       │                                      │
1. Safe Spend (خرج امن)            2. Liquid & Gold (نقدینگی و طلا)        3. Quick Add (ثبت سریع)
   - Sizes: 2x1, 2x2                  - Sizes: 2x1, 2x2                     - Sizes: 1x1, 2x1
   - Daily burn allowance             - Operational cash in Tomans          - Instant 1-tap categories
   - Days remaining to payday         - Grams of 18K Gold benchmark         - Food, Cafe, Fuel, Market
   - Real-time cycle gauge            - Daily inflationary bleed            - Direct launcher action
      │                                       │                                      │
4. Commitments (اقساط و چک)         5. Quick Cards (کارت‌ها و شبا)          6. Market Ticker (نرخ‌ها)
   - Sizes: 4x2, 4x3                  - Sizes: 4x2, 4x1                     - Sizes: 2x1, 4x1
   - BNPL maturity countdown          - Horizontal card deck                - Gold 18K, Emami Coin
   - Sayad check status & due date    - 1-tap copy PAN / Sheba to clip      - USDT / Tether & USD Free
   - Total monthly liabilities        - Mask reveal & Bank branding         - Live +/- % delta badges
```

### 2.2 Wireframes & Layout Specs for Key Widgets

#### Widget 1: Daily Safe Spend & Runway (2x2)
```
+-------------------------------------------------------------+
|  [Logo] خرج امن امروز                         ۲۴ اسفند ۱۴۰۴ |
|                                                             |
|  ۶۵۰,۰۰۰ تومان                                               |
|  نرخ سوخت مجاز تا پایان چرخه                                |
|                                                             |
|  [=======================>..........]  ۶۸٪ مصرف شده          |
|                                                             |
|  سقف باقیمانده: ۴,۲۰۰,۰۰۰ تومان         ۷ روز تا واریز حقوق |
+-------------------------------------------------------------+
```

#### Widget 2: Liquid Balance & Gold Benchmark (2x1)
```
+-------------------------------------------------------------+
|  موجودی آزاد نقد                       معادل طلای ۱۸ عیار   |
|  ۲۸,۴۵۰,۰۰۰ تومان                      ۴.۲۶ گرم             |
|  (خونریزی تورمی روزانه: ۳۸,۰۰۰- ت)      (نرخ روز: ۶.۶۸ م)   |
+-------------------------------------------------------------+
```

#### Widget 3: Quick Expense Add (2x1)
```
+-------------------------------------------------------------+
|  ثبت سریع هزینه:                                            |
|  [ + سوپرمارکت ]  [ + کافه/رستوران ]  [ + بنزین/حمل ]  [ + سایر ]
+-------------------------------------------------------------+
```

#### Widget 4: Quick Cards Carousel & Instant Copy (4x2)
```
+-------------------------------------------------------------+
|  کارت‌های شتاب شما                              [+ افزودن]  |
|  +---------------------------+ +--------------------------+ |
|  | [لوگو ملی] کارت خرید روزمره | | [بلو] کارت حقوق          | |
|  | ۶۰۳۷  ۹۹۱۸  ۲۳۴۵  ۶۷۸۹    | | ۶۲۱۹  ۸۶۱۰  ۹۹۸۸  ۱۱۲۲   | |
|  | [کپی شماره]   [کپی شبا]   | | [کپی شماره]   [کپی شبا]  | |
|  +---------------------------+ +--------------------------+ |
+-------------------------------------------------------------+
```

---

## Part 3: Concrete Implementation Recommendations

### 3.1 Resolving ADR-0004 for Manual Cards
We propose updating ADR-0004 or adopting ADR-0005:
- **Card Entity Evolution:** Allow cards to exist in two states:
  1. `LinkedToAccount`: Connected to an SMS-tracked `BankAccount` (maintains automated ledger balance).
  2. `ManualCard`: User-created card record holding `fullPan`, `sheba`, `bankName`, `nickname`, `expiry`, `themePreset`, and `balance` (optional manual figure).
- When an SMS arrives matching a `ManualCard`'s PAN/mask, automatically suggest or execute linking.

### 3.2 True LTR Masked Input Component (`ShetabPanInput`)
Implement a dedicated Compose component with:
- `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`
- `VisualTransformation`: `ShetabPanVisualTransformation(splitCharacter = "   ")`
- Seamless backspace handling without corrupted mask characters.
- Live BIN lookup to automatically update bank icon and suggested card theme.

### 3.3 Modular Glance Receivers & GlanceAppWidget Architecture
Separate the monolith into dedicated `GlanceAppWidget` implementations:
- `SafeSpendWidget.kt` -> registered in `AndroidManifest.xml`
- `LiquidPegWidget.kt`
- `QuickAddWidget.kt`
- `CardStackWidget.kt`
- `MarketTickerWidget.kt`

Every widget interacts with `DerivedDb` and `Store` via lightweight coroutine snapshot queries and handles Android 12+ dynamic colors and widget resizing curves.
