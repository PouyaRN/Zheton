# Technical Research & Architectural Audit: Radical Simplification & World-Class UI/UX for ChandToman (چندتومان)

**Document ID:** `RES-CHANDTOMAN-SIMPLIFICATION-2026-V1`  
**Status:** Approved Architectural Audit & Actionable Blueprint  
**Target Codebase:** Android 14+ / Jetpack Compose / Material 3 (`com.doxigo.muchtoman`)  
**Target Output File:** `docs/RESEARCH_APP_RADICAL_SIMPLIFICATION_AND_WORLD_CLASS_UIUX.md`  
**Benchmarked Global Leaders:** Revolut 10, Apple Card / Apple Wallet, Monzo Trends, Linear, Robinhood  

---

## 1. Executive Summary & Forensic Inventory

### 1.1 The Neobank Paradox: Feature Saturation vs. Cognitive Clarity
ChandToman (چندتومان) possesses a sophisticated fintech engine: zero-permission local SMS parsing, offline double-entry ledgering, real-time gold mark-to-market valuations, 3D card physics, and multi-asset portfolio accounting. However, rapid iterative feature additions have caused the UI to suffer from severe **cognitive bloat**, **redundant data displays**, and **nested modal disorientation**.

Opening the application exposes users to multiple competing numbers, redundant action triggers, overlapping charts, and nested sheets. World-class financial applications (Revolut 10, Apple Card, Monzo, Linear) adhere to the **2-Second Rule**:
> *When a user opens their financial terminal, they must grasp their exact net position and safe spending velocity within 2 seconds, without scanning through visual noise.*

---

### 1.2 Comprehensive Inventory of Screens, Tabs, Sheets, and Widgets

Below is the exhaustive inventory of all visual components currently rendered in the codebase:

| Surface / Destination | Component Name | Source File & Lines | Render Trigger / Location |
| :--- | :--- | :--- | :--- |
| **Tab 1: فرمان (Home)** | `HeroField` | `Ui.kt:905`, `Ui.kt:1311-1650` | Primary top fold on Home tab |
| **Tab 1: فرمان (Home)** | `HomeBentoCockpit` | `Ui.kt:927`, `Home.kt:902-986` | 4-tile grid (Bank, Gold, Due, Month) |
| **Tab 1: فرمان (Home)** | `QuickActionsDock` | `Ui.kt:944`, `widget/primitives/FintechPrimitives.kt:254-325` | 4-button pill bar (Add, Transfer, Cards, Analytics) |
| **Tab 1: فرمان (Home)** | `LeftToSpendDial` | `Ui.kt:956`, `Home.kt:104-235` | Circular pacing & safe daily burn dial |
| **Tab 1: فرمان (Home)** | `CompactFinancialHealthBanner` | `Ui.kt:970`, `UiIntelligence.kt:100-240` | Financial resilience score banner |
| **Tab 1: فرمان (Home)** | `ContextualPriorityCard` | `Ui.kt:986`, `Home.kt:701-899` | Combined commitments & obligations card |
| **Tab 1: فرمان (Home)** | `recentEntries` Card | `Ui.kt:1012-1070` | 4-item transaction feed |
| **Tab 1: فرمان (Home)** | `QuietStart` / `InsightCard` | `Ui.kt:1084-1114`, `Home.kt:378-493` | Empty state / 2-card analytical story |
| **Tab 1: فرمان (Home)** | `HomeQuickAction` Row | `Ui.kt:1125-1144` | Secondary 2-button row (ثبت دارایی، مدیریت اقساط) |
| **Tab 1: فرمان (Home)** | `holdingSections` | `Ui.kt:1152-1200` | Full portfolio asset rows (if portfolio active) |
| **Tab 2: بازار (Market)** | `MarketScreenHeader` | `MarketUi.kt:326`, `MarketUi.kt:656-708` | Title & refresh button |
| **Tab 2: بازار (Market)** | Segmented Mode Pill | `MarketUi.kt:334-389` | Switcher: سبد دارایی من vs دیده‌بان بازار |
| **Tab 2: بازار (Market)** | `PortfolioOverviewCard` | `MarketUi.kt:394`, `MarketUi.kt:1656-1785` | Portfolio P&L and total asset valuation |
| **Tab 2: بازار (Market)** | `HoldingMarketRow` | `MarketUi.kt:435-472`, `MarketUi.kt:1346-1434` | List of user assets & holdings |
| **Tab 2: بازار (Market)** | `MarketGlanceBar` | `MarketUi.kt:499`, `MarketUi.kt:710-767` | 4-tile live ticker rates bar |
| **Tab 2: بازار (Market)** | `MarketFluctuationCard` | `MarketUi.kt:511`, `MarketUi.kt:807-940` | Interactive spline chart with timeframe pills |
| **Tab 2: بازار (Market)** | `GoldCoinBubbleGauge` | `MarketUi.kt:522`, `MarketUi.kt:1530-1654` | Emami, Nim, Rob coin bubble visualizer |
| **Tab 2: بازار (Market)** | Market Category Filter Chips | `MarketUi.kt:532-556` | LazyRow (All, Watchlist, Currency, Gold, Crypto, Bourse) |
| **Tab 2: بازار (Market)** | `MarketArbitrageRadar` | `MarketUi.kt:560`, `MarketUi.kt:1436-1528` | Tether/Cash USD spread & gold fund arbitrage |
| **Tab 2: بازار (Market)** | `MarketAssetRow` | `MarketUi.kt:600-650`, `MarketUi.kt:1230-1344` | Full market ticker feed with mini-sparklines |
| **Tab 3: حساب‌ها (Cards)** | `AppleWalletAccordionDeck` | `Ui.kt:781`, `ui/cards/AppleWalletDeck.kt:67-297` | Stacked vertical accordion cards with 3D expand |
| **Tab 3: حساب‌ها (Cards)** | "موجودی حساب‌های متصل" Card | `Ui.kt:794-848` | Static card repeating all bank accounts & balances |
| **Tab 3: حساب‌ها (Cards)** | `CommitmentsCard` | `Ui.kt:853`, `CommitmentsUi.kt:61-205` | Active loans and installments list |
| **Tab 4: دفتر (Ledger)** | `TimelineScreen` Header & Actions | `Timeline.kt:271-308` | Title, CSV share, selection button, hidden chips |
| **Tab 4: دفتر (Ledger)** | Search & Filter Token Bar | `Timeline.kt:312-343` | `OutlinedTextField` and "فیلتر" button |
| **Tab 4: دفتر (Ledger)** | Category Chips Row | `Timeline.kt:359-382` | LazyRow of category filters |
| **Tab 4: دفتر (Ledger)** | `DailyOutflowSparkCard` | `Timeline.kt:385-390`, `Timeline.kt:2604-2750` | 14-day spending outflow sparkline chart |
| **Tab 4: دفتر (Ledger)** | `FailedTxnWatchdogBanner` | `Timeline.kt:392-402`, `Timeline.kt:2512-2583` | Warning banner for failed/reversed POS transactions |
| **Tab 4: دفتر (Ledger)** | Active Filter Counter Banner | `Timeline.kt:405-433` | Match count + reset button |
| **Tab 4: دفتر (Ledger)** | `DayStickyHeader` & `TimelineRow` | `Timeline.kt:999-1380` | Daily transaction groups, avatars, benchmarks |
| **Tab 4: دفتر (Ledger)** | `BatchActionBar` | `Timeline.kt:724-997` | Multi-select action bottom bar |
| **Tab 4: دفتر (Ledger)** | `TransactionScreen` | `Timeline.kt:1585-2180` | Full-screen detail view with receipt & life energy |
| **Tab 5: دیده‌بان (Reports)** | `WindowSelector` | `ReportsUi.kt:220`, `ReportsUi.kt:441-470` | Time horizon pills (Month, 3M, 6M, 1Y, All) |
| **Tab 5: دیده‌بان (Reports)** | `PaydayBurnDownCard` | `ReportsUi.kt:231`, `ReportsUi.kt:2066-2245` | Monzo-style income vs burn-down curve |
| **Tab 5: دیده‌بان (Reports)** | `ExecutiveCashFlowCard` | `ReportsUi.kt:245`, `ReportsUi.kt:472-695` | Net cash flow, income, and expense readout |
| **Tab 5: دیده‌بان (Reports)** | `BankFeesCard` | `ReportsUi.kt:258`, `ReportsUi.kt:1573-1691` | Bank fee and card-to-card tax analytics |
| **Tab 5: دیده‌بان (Reports)** | `SpendingHeatmapCard` | `ReportsUi.kt:266`, `ReportsUi.kt:1693-1852` | Time-of-day & day-of-week spend matrix |
| **Tab 5: دیده‌بان (Reports)** | `WealthDefenseGaugeCard` | `ReportsUi.kt:273`, `ReportsUi.kt:770-898` | Hedged vs unhedged wealth gauge |
| **Tab 5: دیده‌بان (Reports)** | `IdleCashSweepCard` | `ReportsUi.kt:280`, `ReportsUi.kt:900-991` | Idle checking balance sweep recommendation |
| **Tab 5: دیده‌بان (Reports)** | `Budget503020Card` | `ReportsUi.kt:288`, `ReportsUi.kt:993-1146` | Needs (50%), Wants (30%), Savings (20%) breakdown |
| **Tab 5: دیده‌بان (Reports)** | `MicroLeakCard` | `ReportsUi.kt:296`, `ReportsUi.kt:1296-1410` | Micro-transaction tracker (<50k Toman) |
| **Tab 5: دیده‌بان (Reports)** | `CategoryDonutCard` | `ReportsUi.kt:304`, `ReportsUi.kt:1854-2064` | Interactive SVG donut chart of category shares |
| **Tab 5: دیده‌بان (Reports)** | `OpportunityCostSimulatorCard` | `ReportsUi.kt:317`, `ReportsUi.kt:1412-1571` | Regret metric (checking balance vs gold yield) |
| **Tab 5: دیده‌بان (Reports)** | `AssetAllocationDonutCard` | `ReportsUi.kt:324`, `ReportsUi.kt:1148-1294` | Asset class donut (Gold, Currency, Stocks, Cash) |
| **Tab 5: دیده‌بان (Reports)** | `CategorySpendRow` | `ReportsUi.kt:340-352`, `ReportsUi.kt:697-768` | Linear category list with percentages |
| **Tab 5: دیده‌بان (Reports)** | `ChangeFigure` & `HistoryChart` | `ReportsUi.kt:355-426` | Wealth trend chart with touch scrubbing |
| **Tab 5: دیده‌بان (Reports)** | `CompositionBar` | `ReportsUi.kt:432` | Multi-segmented asset horizontal strip |
| **Modal Sheets (Ui.kt)** | `DirectObligationSheet` | `Ui.kt:499` | Installment linking sheet |
| **Modal Sheets (Ui.kt)** | `GoldBubbleSheet` | `Ui.kt:533` | Gold and coin bubble inspection sheet |
| **Modal Sheets (Ui.kt)** | `RecurringExpensesSheet` | `Ui.kt:539` | Subscriptions and recurring bills |
| **Modal Sheets (Ui.kt)** | `WishlistSheet` | `Ui.kt:559` | Savings wishlist |
| **Modal Sheets (Ui.kt)** | `ManualTxnSheet` | `Ui.kt:566`, `Timeline.kt:2360-2510` | Manual transaction logging bottom sheet |
| **Modal Sheets (Ui.kt)** | `CommitmentsHubSheet` | `Ui.kt:577`, `CommitmentsUi.kt:207-317` | Full installment & loan hub modal |
| **Modal Sheets (Ui.kt)** | `AddCommitmentSheet` | `Ui.kt:588`, `CommitmentsUi.kt:438-584` | New loan/installment creation modal |
| **Modal Sheets (Ui.kt)** | `ExecutiveDeckSheet` | `Ui.kt:598-624` | **Anti-Pattern Sheet**: Modal opening other modals |
| **Modal Sheets (Ui.kt)** | `BankSheet` | `Ui.kt:1229`, `ui/cards/BankSheet.kt:251-380` | Bank account management bottom sheet |
| **Modal Sheets (Ui.kt)** | `CardEditorSheet` | `Ui.kt:1249` | PAN, IBAN, bank color editor |
| **Modal Sheets (Ui.kt)** | `PickTypeSheet` | `Ui.kt:1262`, `Ui.kt:2810-3010` | Asset type picker sheet |
| **Modal Sheets (Ui.kt)** | `EditSheet` | `Ui.kt:1274`, `Ui.kt:3293-3450` | Holding balance editor sheet |
| **Modal Sheets (Ui.kt)** | `UpdateSheet` | `Ui.kt:2164`, `Ui.kt:2176-2240` | In-app APK update notification sheet |
| **Settings & Mgmt** | `SettingsScreen` | `Ui.kt:365`, `Settings.kt:1-750` | App preferences, lock, SMS rescan |
| **Settings & Mgmt** | `CategoriesScreen` | `Ui.kt:398`, `ui/categories/CategoriesScreen.kt:59-240` | Category reordering and creation |
| **Orphaned / Dormant** | `CommandCenterScreen` | `widget/engine/CommandCenterScreen.kt:87-687` | **Dead Code**: 688-line unreferenced dashboard |
| **Orphaned / Dormant** | `GoalsScreen` & `GoalCard` | `GoalsUi.kt:67-382` | **Dead Code**: 382-line unreferenced goals UI |
| **Orphaned / Dormant** | `CompanionScreen` & Family | `Family.kt:77-680`, `MainActivity.kt:1356-1359` | **Dormant Code**: Unshipped sync & PWA |

---

## 2. The Bloat & Clutter Audit: Cognitive Fatigue Analysis

### 2.1 The Quadruple "Add" Action & Quadruple Bank List Redundancy
A critical symptom of uncoordinated growth in the codebase is action duplication.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       ACTION & DISPLAY DUPLICATION CRISIS                   │
├─────────────────────────────────────────────────────────────────────────────┤
│ 1. "ثبت دارایی / افزودن" (Add Action) exists in FOUR distinct places:        │
│    • HeroField icon button [Ui.kt:1317]                                     │
│    • QuickActionsDock pill button [Ui.kt:944]                               │
│    • HomeQuickAction bottom button [Ui.kt:1132]                             │
│    • TabBar central floating FAB [TabBar.kt:185]                            │
├─────────────────────────────────────────────────────────────────────────────┤
│ 2. "حساب‌ها و کارت‌ها" (Bank Account Data) is displayed FOUR separate times:  │
│    • Home Bento Cockpit tile [Home.kt:905]                                  │
│    • Tab 3 AppleWalletAccordionDeck [Ui.kt:781]                             │
│    • Tab 3 "موجودی حساب‌های متصل" static card directly below it [Ui.kt:794] │
│    • BankSheet modal containing BankCardDeck + BankAccountRows [Ui.kt:1229] │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Code Evidence:
- **Tab 3 Self-Duplication:** In `Ui.kt:781`, `AppleWalletAccordionDeck` renders interactive cards. Immediately below it, lines `794-848` render another `Card` titled *"موجودی حساب‌های متصل"* which loops through `state.bankAccounts` and prints the exact same bank names, PANs, SHEBAs, and balances.
- **BankSheet Re-duplication:** Tapping a card in the accordion triggers `onCardClick = { banks = true }` (`Ui.kt:785`), which pops `BankSheet` (`ui/cards/BankSheet.kt:251`). Inside `BankSheet`, lines `288-295` render `BankCardDeck` (a horizontal 3D pager), followed by lines `298-311` which render `BankAccountRow` items. The user is forced to see their bank accounts rendered in four completely different layouts.

---

### 2.2 Home Screen Cognitive Overload (Card Soup)
The Home tab currently stacks **10 vertical layers**:
1. `HeroField` (`Ui.kt:905`)
2. `HomeBentoCockpit` (`Ui.kt:927`)
3. `QuickActionsDock` (`Ui.kt:944`)
4. `LeftToSpendDial` (`Ui.kt:956`)
5. `CompactFinancialHealthBanner` (`Ui.kt:970`)
6. `ContextualPriorityCard` (`Ui.kt:986`)
7. `recentEntries` Card (`Ui.kt:1012`)
8. `InsightCard` Story row (`Ui.kt:1092`)
9. `HomeQuickAction` row (`Ui.kt:1125`)
10. `holdingSections` full asset list if portfolio toggle is active (`Ui.kt:1152`)

#### Impact on the User:
- Stacking `HomeBentoCockpit` (which already shows Monthly Spent and Commitments Due) directly above `QuickActionsDock`, `LeftToSpendDial`, and `ContextualPriorityCard` forces the user to digest the exact same commitment and burn-rate data across three adjacent cards.
- The `portfolio` toggle in `Ui.kt:1145-1200` dumps every single dollar, gold, and crypto holding on the Home screen, rendering Tab 2 (`Tab.ASSETS`) completely redundant.

---

### 2.3 The "Sheet-on-Sheet" Navigation Trap
In `Ui.kt:597-624`, `CompactFinancialHealthBanner` provides an `onOpenDetails` trigger that launches `ExecutiveDeckSheet`:

```kotlin
// Ui.kt:597-624: The Sheet-on-Sheet Anti-Pattern
if (showExecutiveDeckSheet) {
    ModalBottomSheet(
        onDismissRequest = { showExecutiveDeckSheet = false },
    ) {
        // ...
        OutlinedButton(onClick = {
            showExecutiveDeckSheet = false
            showGoldBubbleSheet = true  // Dismisses sheet to open another sheet
        })
        OutlinedButton(onClick = {
            showExecutiveDeckSheet = false
            showCommitmentsHubSheet = true  // Dismisses sheet to open another sheet
        })
        OutlinedButton(onClick = {
            showExecutiveDeckSheet = false
            showWishlistSheet = true  // Dismisses sheet to open another sheet
        })
    }
}
```

A modal bottom sheet must be a terminal interaction leaf (e.g., an input form or single-purpose detail inspection). Using a modal bottom sheet as an intermediate navigation router that dismisses itself to launch other modal bottom sheets violates Material 3 guidelines and Android navigation ergonomics.

---

### 2.4 Tab 4 (Ledger) Vertical Space Cannibalization
In `Timeline.kt:271-433`, before the user can see a single transaction, the screen renders:
1. Header row with 4 buttons: "انتخاب", "+", "CSV Share", "پنهان‌شده" (`Timeline.kt:271-308`)
2. Search Bar + "فیلتر" button (`Timeline.kt:312-343`)
3. Horizontal Category Chips (`Timeline.kt:359-382`)
4. 14-day spending outflow sparkline card (`Timeline.kt:385-390`, `Timeline.kt:2604-2750`)
5. Failed transaction watchdog banner (`Timeline.kt:392-402`)
6. Active filter match count banner (`Timeline.kt:405-433`)

#### Measurement of Visual Waste:
On a standard 1080x2400 (6.5") viewport, these six stacked controls consume **over 360dp of vertical height** (~45% of the visible viewport). The user opens the Ledger to inspect recent spending but is greeted by three layers of filters, a sparkline chart, and banners before seeing their first transaction.

---

### 2.5 Tab 5 (Reports) Exhaustion: 14 Vertically Stacked Cards
`ReportsUi.kt:228-433` renders 14 heavy analytical modules in a single scrollable column:
- `PaydayBurnDownCard`
- `ExecutiveCashFlowCard`
- `BankFeesCard`
- `SpendingHeatmapCard`
- `WealthDefenseGaugeCard`
- `IdleCashSweepCard`
- `Budget503020Card`
- `MicroLeakCard`
- `CategoryDonutCard`
- `OpportunityCostSimulatorCard`
- `AssetAllocationDonutCard`
- `CategorySpendRow`
- `HistoryChart` (with scrub point)
- `CompositionBar`

#### Specific Redundancies:
1. **Asset Allocation Duplication:** `AssetAllocationDonutCard` (`ReportsUi.kt:324`), `CompositionBar` (`ReportsUi.kt:432`), and `WealthDefenseGaugeCard` (`ReportsUi.kt:273`) all calculate and display the same ratio: liquid checking cash vs hedged assets (gold, currency, stocks).
2. **Burn Rate Duplication:** `ExecutiveCashFlowCard` (`ReportsUi.kt:245`) and `PaydayBurnDownCard` (`ReportsUi.kt:231`) both chart and state monthly income and expenses.
3. **Micro-Features vs Neobank Simplicity:** `MicroLeakCard` (flags transactions < 50k Toman) and `BankFeesCard` (card-to-card transfer fees) belong in a deep-dive insights sheet or settings audit, not as prominent top-level cards in the monthly executive report.

---

### 2.6 Orphaned & Dormant Codebases
The codebase contains **1,750+ lines** of unmaintained, unreachable, or dead UI code:
1. `widget/engine/CommandCenterScreen.kt` (**688 lines**): An alternative dashboard containing `CommandCenterScreen`, `MonthlyCashFlowCard`, `ActiveBankCardsCard`, `CommitmentsSummaryCard`, `RecentTransactionsCard`, and `RevolutBalanceHero`. Zero callers in the entire repository.
2. `Family.kt` (**680 lines**): A full companion screen with QR code generation (`qrBitmap`), member pairing, and transaction syncing. Explicitly marked as dormant in `MainActivity.kt:1356-1359`:
   ```kotlin
   // MainActivity.kt:1356-1359
   * Family sync is dormant: the sync store, worker, and PWA remain in the repo unshipped.
   private fun requestFamilySync(silent: Boolean) { /* dormant — no-op */ }
   ```
3. `GoalsUi.kt` (**382 lines**): `GoalsScreen` and `GoalCard` are fully styled composables that are never connected to any navigation route or modal sheet.

---

## 3. Keep vs. Eliminate Matrix

Every screen, card, sheet, and major widget has been audited against the elite fintech benchmarks (Revolut 10, Apple Card, Monzo Trends, Linear).

| Component / Feature | Location in Code | Status | Architectural Rationale & Transformation Plan |
| :--- | :--- | :---: | :--- |
| **Hero Net Worth Display** | `Ui.kt:1311-1650` | `[KEEP - CORE]` | Primary anchor of truth. Polish typography with `tnum` and subtle ambient flare. |
| **Perspective Toggle Pill** | `Ui.kt:1416-1452` | `[MERGE/CONSOLIDATE]` | Merge `[کل دارایی]` vs `[نقدینگی آزاد]` directly into the Hero net worth card as an inline haptic segment. |
| **Home Bento Cockpit** | `Home.kt:902-986` | `[POLISH & ELEVATE]` | World-class 2-column bento (Gold anchor + Month burn pacing + Liquidity). Elevate with Double-Bezel containers. |
| **QuickActionsDock** | `FintechPrimitives.kt:254` | `[KEEP - CORE]` | Primary rapid touchpoints. Ensure button-in-button iconography and 12ms haptic detents. |
| **LeftToSpendDial** | `Home.kt:104-235` | `[MERGE/CONSOLIDATE]` | Redundant with Bento Cockpit tile 4. Merge its daily burn rate calculation into the Bento Cockpit monthly tile. |
| **CompactFinancialHealthBanner** | `UiIntelligence.kt:100` | `[MERGE/CONSOLIDATE]` | Remove standalone banner; embed score badge as an eyebrow tag in the Hero display. |
| **ContextualPriorityCard** | `Home.kt:701-899` | `[MERGE/CONSOLIDATE]` | Consolidate into Bento Cockpit's upcoming commitments tile. Eliminate standalone full-width card. |
| **Home QuickAction Row** | `Ui.kt:1125-1144` | `[ELIMINATE]` | Completely redundant with `QuickActionsDock` and the TabBar central FAB. Remove lines 1125-1144. |
| **Home Portfolio Holdings Band** | `Ui.kt:1152-1200` | `[ELIMINATE]` | Home must remain an operational cockpit. Remove full holding rows from Home; delegate entirely to Tab 2 (Market/Assets). |
| **CommandCenterScreen** | `CommandCenterScreen.kt` | `[ELIMINATE]` | Dead code (688 lines). Delete file to eliminate maintenance overhead. |
| **GoalsScreen & GoalCard** | `GoalsUi.kt:67-382` | `[ELIMINATE]` | Dead code (382 lines). Unreferenced anywhere in navigation or UI. Delete or stow until feature roadmap activates it. |
| **CompanionScreen & Family** | `Family.kt:77-680` | `[ELIMINATE]` | Dormant unshipped sync code (680 lines). Strip out of primary UI bundle to reduce APK footprint. |
| **AppleWalletAccordionDeck** | `AppleWalletDeck.kt:67` | `[POLISH & ELEVATE]` | The signature feature of Tab 3. Polish spring curves (`dampingRatio = 0.75f`), add 1-tap PAN/SHEBA copy, and inline card edit. |
| **Tab 3 Static Accounts Card** | `Ui.kt:794-848` | `[ELIMINATE]` | Direct duplication of the accordion deck above it. Eliminate entirely. |
| **BankCardDeck (in BankSheet)** | `BankSheet.kt:288` | `[ELIMINATE]` | 3D pager inside a bottom sheet is redundant with the accordion deck. Strip out of `BankSheet`. |
| **ExecutiveDeckSheet** | `Ui.kt:598-624` | `[ELIMINATE]` | Anti-pattern sheet-on-sheet. Eliminate; route directly to dedicated sheets from respective tiles. |
| **Timeline Top Sparkline Card** | `Timeline.kt:385-390` | `[ELIMINATE]` | Consumes 120dp of vertical space in Tab 4. Outflow trends belong exclusively in Tab 5 (Reports). |
| **Timeline Multi-layer Filters** | `Timeline.kt:312-382` | `[MERGE/CONSOLIDATE]` | Merge text search, category pills, and advanced tokens into a unified Linear/Raycast-tier Omnibar. |
| **Timeline BatchActionBar** | `Timeline.kt:724-997` | `[POLISH & ELEVATE]` | Elevate to a floating glass island dock that animates smoothly from the bottom upon multi-selection. |
| **TransactionScreen Life Energy** | `Timeline.kt:1650-1653` | `[MERGE/CONSOLIDATE]` | Move "ارزش زمان کاری" into an optional collapsible metadata pill in the transaction receipt. |
| **PaydayBurnDownCard** | `ReportsUi.kt:2066` | `[KEEP - CORE]` | Monzo Trends-tier signature curve. Keep as the premier visualization in Tab 5. |
| **ExecutiveCashFlowCard** | `ReportsUi.kt:472` | `[MERGE/CONSOLIDATE]` | Merge net savings rate and income/expense totals into the header of `PaydayBurnDownCard`. |
| **MicroLeakCard & BankFeesCard** | `ReportsUi.kt:1296, 1573` | `[MERGE/CONSOLIDATE]` | Consolidate micro-leaks and bank fees into an expandable "اصطکاک‌های مالی" (Financial Friction) sub-card. |
| **Donut Charts vs Composition** | `ReportsUi.kt:304, 324, 432` | `[MERGE/CONSOLIDATE]` | Three charts displaying the same data. Standardize on one unified interactive category ring with segmented bar. |
| **OpportunityCostSimulatorCard** | `ReportsUi.kt:1412` | `[MERGE/CONSOLIDATE]` | Merge with `IdleCashSweepCard` into a single high-impact "سپر ضد تورم" (Inflation Defense) module. |
| **Market Segmented Mode Pill** | `MarketUi.kt:334-389` | `[POLISH & ELEVATE]` | Retain the clean separation between [سبد دارایی من] and [دیده‌بان بازار]. Elevate sliding spring indicator. |
| **GoldCoinBubbleGauge** | `MarketUi.kt:1530` | `[POLISH & ELEVATE]` | Essential Iranian market indicator. Elevate with high-precision bubble bars and Monospace Tabular figures. |

---

## 4. Tab & Navigation Streamlining

### 4.1 The 5-Tab Bar Dilemma & Redesign Proposal
`TabBar.kt:138` computes:
```kotlin
val slotCount = if (onQuickAdd != null) tabs.size + 1 else tabs.size // 5 + 1 = 6 slots
val slotWidth = totalWidth / slotCount // ~54dp on 360dp screens
```
Packing 5 text labels, 5 icons, and a prominent center FAB into a 68dp floating pill results in **cramped 54dp touch targets**, clipping text labels on smaller screens (Samsung Galaxy A-series, Xiaomi Redmi), and user mis-taps.

#### Streamlined 4-Tab Architecture (Revolut 10 / Apple Wallet Standard)
We propose consolidating the global navigation shell into **4 high-utility primary tabs**, freeing up comfortable **72dp+ touch targets** while preserving the central satellite FAB:

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                     PROPOSED STREAMLINED 4-TAB NAVIGATION ARCHITECTURE                  │
├──────────────────────────┬──────────────────────────┬───────────────────────────────────┤
│ Tab                      │ Primary Responsibility   │ Benchmark Inspiration             │
├──────────────────────────┼──────────────────────────┼───────────────────────────────────┤
│ **1. فرمان (Home)**      │ Operational Cash, Hero,  │ Revolut 10 Cockpit                │
│                          │ Bento, Recent Activity   │                                   │
├──────────────────────────┼──────────────────────────┼───────────────────────────────────┤
│ **2. بازار (Market)**    │ Multi-Asset Portfolio,   │ Robinhood + TradingView           │
│                          │ Live Gold/FX, Bubbles    │                                   │
├──────────────────────────┼──────────────────────────┼───────────────────────────────────┤
│ **( + ) FAB**            │ Instant Transaction      │ Raycast / Dynamic Action Island   │
│                          │ Entry & Quick Ingest     │                                   │
├──────────────────────────┼──────────────────────────┼───────────────────────────────────┤
│ **3. کارت‌ها (Cards)**    │ Apple Wallet Accordion,  │ Apple Wallet + Stripe Treasury    │
│                          │ 1-Tap PAN/IBAN, BNPL     │                                   │
├──────────────────────────┼──────────────────────────┼───────────────────────────────────┤
│ **4. دفتر و گزارش**      │ Unified Transaction      │ Monzo Trends + Copilot Money      │
│ **(Activity & Trends)**  │ Ledger + Monthly Trends  │                                   │
└──────────────────────────┴──────────────────────────┴───────────────────────────────────┘
```

#### Why Merging Ledger (دفتر) and Reports (دیده‌بان) into Tab 4 is Radical Elegance:
In global leaders like **Monzo** and **Copilot Money**, transactions and spending trends are not isolated across separate navigation tabs. Spending trends are the *macro view* of the transaction ledger, and transactions are the *micro drill-down* of the spending trends.
- Tab 4 features a top sliding pill: `[ ریزتراکنش‌ها (Ledger) ]` and `[ تحلیل و ترندها (Trends) ]`.
- Tapping any slice on the Category Donut or Burn-Down curve instantly filters the ledger below with smooth layout transition, eliminating the jarring tab-switch recomposition in `Ui.kt:718-721`.

---

### 4.2 Modal Sheet Architecture: From 13 Sheets to 5 Standardized Drawers
Currently, `Ui.kt` manages 13 distinct bottom sheets. This must be collapsed into **5 standardized, reusable sheets**:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                          UNIFIED MODAL SHEET TAXONOMY                                  │
├────────────────────────────┬───────────────────────────────────────────────────────────┤
│ 1. Transaction Sheet       │ Unified manual entry, editing, and SMS re-categorization. │
│    (`TransactionSheet`)    │ Replaces `ManualTxnSheet` + `DirectObligationSheet`.       │
├────────────────────────────┼───────────────────────────────────────────────────────────┤
│ 2. Holding Sheet           │ Asset creation, amount update, gold karat converter.      │
│    (`HoldingSheet`)        │ Replaces `PickTypeSheet` + `EditSheet`.                   │
├────────────────────────────┼───────────────────────────────────────────────────────────┤
│ 3. Card Manager Sheet      │ Bank account settings, SMS sender rules, card theme.      │
│    (`CardManagerSheet`)    │ Replaces `BankSheet` + `CardEditorSheet`.                 │
├────────────────────────────┼───────────────────────────────────────────────────────────┤
│ 4. Commitments Hub Sheet   │ BNPL installments, Sayad cheques, micro-loans.            │
│    (`CommitmentsSheet`)    │ Replaces `CommitmentsHubSheet` + `AddCommitmentSheet`.    │
├────────────────────────────┼───────────────────────────────────────────────────────────┤
│ 5. Market Deep-Dive Sheet  │ Coin bubble simulator, arbitrage spread, gold converter.  │
│    (`MarketAnalysisSheet`) │ Replaces `GoldBubbleSheet` + `ExecutiveDeckSheet`.        │
└────────────────────────────┴───────────────────────────────────────────────────────────┘
```

---

## 5. Elite Visual Design & Micro-interactions

To achieve the caliber of Apple Card, Revolut 10, and Linear, the application must adhere to strict visual physics and typography guardrails.

### 5.1 The "Double-Bezel" (Doppelrand) Nested Architecture
Standard flat cards with 1px borders look cheap and generic. All major bento tiles, cards, and modal containers must adopt the **Double-Bezel** pattern:

```kotlin
// Architectural Recipe for Double-Bezel Containers
Box(
    modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(28.dp))
        .background(Color(0xFF090C12)) // Vantablack Outer Shell
        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
        .padding(6.dp) // Outer Bezel Gutter
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF101520)) // Midnight Slate Inner Core
            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        // Content
    }
}
```

---

### 5.2 Persian Typography Physics & Jitter Elimination
1. **Mandatory Tabular Numerals (`tnum`):** All financial amounts, gold weights, percentages, and card numbers must be rendered using `ModamFigures` with OpenType tabular figures enabled (`font-feature-settings: 'tnum'`). This eliminates the jarring horizontal layout shake when digits update or stream in.
2. **Elimination of Currency Unit Redundancy:** Banish the repeated full-word `تومان` in large display headers. Display the bold Persian integer, accompanied by a subdued unit glyph `ت` (`fontSize = 13.sp`, `alpha = 0.7f`) aligned to the baseline.
3. **Persian Number Formatting Rule:** Enforce Persian numerals across all user-facing readouts using `faNumber()` and `faCompact()`, but preserve ASCII Latin digits in clipboard copy operations (`toAsciiDigits()`) for instant banking app compatibility.

---

### 5.3 Tactile Haptic Hierarchy (4-Tier Matrix)
Fintech interfaces must provide physical, mechanical reassurance. Replace generic Android clicks with the calibrated `LocalFintechHaptics`:

| Micro-interaction | Haptic Feedback Pattern | Target UI Trigger |
| :--- | :--- | :--- |
| **Chart Scrubbing** | 10ms micro-tick (amplitude 0.15) | Dragging finger across `MarketFluctuationCard` or `HistoryChart` |
| **Tab Navigation** | 25ms light detent (amplitude 0.40) | Switching tabs on the floating `TabBar` island |
| **PAN / SHEBA Copy** | Double pulse (20ms + 40ms) | Tapping the "کپی شماره کارت" or "کپی شبا" pill button |
| **Commitment Settled** | Heavy confirmation detent (55ms) | Marking an installment or loan payment as paid |

---

### 5.4 Fluid Spring Motion & GPU Constraints
1. **Banned Transitions:** Ban all standard `linear` and `ease-in-out` transitions. All modal slides, accordion expansions, and pill indicator glides must utilize high-damping spring physics:
   ```kotlin
   val FintechSpringSpec = spring<Float>(
       dampingRatio = 0.75f, // Zero awkward oscillation
       stiffness = 400f      // Crisp, authoritative snap
   )
   ```
2. **GPU Performance Guardrails:**
   - Restrict `graphicsLayer` animations strictly to `translationX`, `translationY`, `scaleX`, `scaleY`, and `alpha`.
   - Never animate layout properties (`height`, `width`, `padding`) directly inside list items.
   - Restrict heavy `blur` effects (`backdrop-blur`) exclusively to fixed navigation shells (`TabBar`) and modal sheet backgrounds; never apply blur filters to scrolling LazyColumn elements.

---

## 6. Traceable Primary Source Citations

For absolute forensic clarity, every finding and proposed refactoring in this audit corresponds to verified source code lines in the repository:

1. **Four-Way Add Duplication:**
   - `app/src/main/java/com/doxigo/muchtoman/Ui.kt:1317` (`HeroField` onAdd)
   - `app/src/main/java/com/doxigo/muchtoman/Ui.kt:944` (`QuickActionsDock` onAdd)
   - `app/src/main/java/com/doxigo/muchtoman/Ui.kt:1132` (`HomeQuickAction` "ثبت دارایی")
   - `app/src/main/java/com/doxigo/muchtoman/TabBar.kt:185-226` (Central action FAB)

2. **Four-Way Bank Account Duplication:**
   - `app/src/main/java/com/doxigo/muchtoman/Home.kt:905` (`HomeBentoCockpit` bank tile)
   - `app/src/main/java/com/doxigo/muchtoman/Ui.kt:781` (`AppleWalletAccordionDeck`)
   - `app/src/main/java/com/doxigo/muchtoman/Ui.kt:794-848` (`Card` "موجودی حساب‌های متصل")
   - `app/src/main/java/com/doxigo/muchtoman/ui/cards/BankSheet.kt:288-311` (`BankCardDeck` + `BankAccountRow`)

3. **Orphaned / Dead Code Assets:**
   - `app/src/main/java/com/doxigo/muchtoman/widget/engine/CommandCenterScreen.kt:87-687` (688 lines unreferenced)
   - `app/src/main/java/com/doxigo/muchtoman/GoalsUi.kt:67-382` (382 lines unreferenced)
   - `app/src/main/java/com/doxigo/muchtoman/Family.kt:77-680` (680 lines dormant companion sync)
   - `app/src/main/java/com/doxigo/muchtoman/MainActivity.kt:1356-1359` (No-op dormant family sync note)

4. **Sheet-on-Sheet Anti-Pattern:**
   - `app/src/main/java/com/doxigo/muchtoman/Ui.kt:597-624` (`ExecutiveDeckSheet` dismissing itself to trigger `showGoldBubbleSheet`, `showCommitmentsHubSheet`, `showWishlistSheet`)

5. **Timeline Vertical Clutter:**
   - `app/src/main/java/com/doxigo/muchtoman/Timeline.kt:271-308` (Header row with 4 action buttons)
   - `app/src/main/java/com/doxigo/muchtoman/Timeline.kt:312-343` (Search bar & filter button)
   - `app/src/main/java/com/doxigo/muchtoman/Timeline.kt:359-382` (Category chips LazyRow)
   - `app/src/main/java/com/doxigo/muchtoman/Timeline.kt:385-390` (`DailyOutflowSparkCard`)
   - `app/src/main/java/com/doxigo/muchtoman/Timeline.kt:392-402` (`FailedTxnWatchdogBanner`)
   - `app/src/main/java/com/doxigo/muchtoman/Timeline.kt:405-433` (Active filter banner)

6. **Reports Screen Saturation (14 Stacked Modules):**
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:220` (`WindowSelector`)
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:231` (`PaydayBurnDownCard`)
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:245` (`ExecutiveCashFlowCard`)
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:258` (`BankFeesCard`)
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:266` (`SpendingHeatmapCard`)
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:273` (`WealthDefenseGaugeCard`)
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:280` (`IdleCashSweepCard`)
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:288` (`Budget503020Card`)
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:296` (`MicroLeakCard`)
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:304` (`CategoryDonutCard`)
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:317` (`OpportunityCostSimulatorCard`)
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:324` (`AssetAllocationDonutCard`)
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:340-352` (`CategorySpendRow`)
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:355-426` (`HistoryChart`)
   - `app/src/main/java/com/doxigo/muchtoman/ReportsUi.kt:432` (`CompositionBar`)

---

## 7. Action Plan & Phased Implementation Roadmap

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                           PHASED IMPLEMENTATION BLUEPRINT                               │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ PHASE 1: Dead Code Removal & Redundancy Purge                                           │
│ • Delete `widget/engine/CommandCenterScreen.kt` (688 lines).                            │
│ • Stow/delete orphaned `GoalsUi.kt` (382 lines) and dormant `Family.kt` (680 lines).    │
│ • Remove duplicate bank account card in Tab 3 (`Ui.kt:794-848`).                        │
│ • Remove `HomeQuickAction` row on Home (`Ui.kt:1125-1144`).                             │
│ • Purge sheet-on-sheet anti-pattern (`Ui.kt:597-624`).                                  │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ PHASE 2: Screen Real Estate Reclamation & Decluttering                                  │
│ • Remove `DailyOutflowSparkCard` from Tab 4 (`Timeline.kt:385`).                        │
│ • Merge search bar, categories, and tokens into a clean Linear Omnibar.                 │
│ • Consolidate 14 cards in Tab 5 down to 4 focused executive modules.                    │
│ • Merge `LeftToSpendDial` and `ContextualPriorityCard` directly into Home Bento.        │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│ PHASE 3: Navigation & Interaction Elevation                                             │
│ • Streamline TabBar to 4 tabs + center FAB (increasing slot touch width to 72dp).      │
│ • Implement the Double-Bezel container pattern across Bento tiles and modal cards.      │
│ • Calibrate the 4-tier haptic feedback matrix with `LocalFintechHaptics`.               │
│ • Standardize tabular Persian typography (`tnum` on `ModamFigures`).                    │
└─────────────────────────────────────────────────────────────────────────────────────────┘
```

This research and architectural blueprint provides the definitive foundation for transforming ChandToman into a pristine, world-class neobank experience that matches the precision and elegance of Revolut 10, Apple Card, and Linear.
