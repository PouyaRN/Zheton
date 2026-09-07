# Spec: Comprehensive Financial Input, Interactive Analytics, and UX Modernization

## Problem Statement

MuchToman users experience operational and analytical gaps across daily finance workflows:

1. **Inability to Log Cash and Non-SMS Spending:** All transactions currently originate exclusively from SMS messages parsed from bank alerts. Cash expenses, in-person cash purchases of gold or foreign currency, payments made via third-party fintech apps without incoming SMS alerts, and cash incomes cannot be logged, leading to severe asset balance drift.
2. **Missing Obligation / Installment Creation Entry Point:** Although the Home screen prominently displays an "Obligations" card, users have no way to manually add a loan, installment plan, or post-dated cheque. Obligations can only be inferred if a bank SMS matches an installment pattern.
3. **Passive Wealth Trend Chart:** The wealth trend visualization in the Reports tab is static and non-interactive. Users cannot scrub or touch along the curve to inspect historic portfolio valuations on specific Jalali dates.
4. **Disconnected Category and Merchant Reporting:** Tapping on top spending categories or high-frequency merchants in the Reports tab does not drill down into the underlying transactions, forcing users to manually remember the category and search for it in the Ledger.
5. **Missing Pull-to-Refresh in the Ledger:** While the Home tab supports swipe-to-refresh to fetch live rates, the Ledger tab does not allow pull-to-refresh to trigger a rescan of incoming bank SMS messages.
6. **Difficult Asset Symbol Discovery:** In the Add Asset sheet, hundreds of Iranian stock tickers and cryptocurrency tokens are rendered in a flat list without an instant search filter, making finding specific stocks cumbersome.
7. **No Transaction Receipt Sharing or Quick Copy:** In the transaction details view, users cannot easily copy payment reference details or share a formatted Persian receipt to messaging applications.

## Solution

1. **Manual Transaction Entry:** Introduce a dedicated Manual Transaction modal sheet/dialog in the Ledger tab allowing users to record off-SMS cash income and expenses with amount in Toman or Rial, Jalali date picker, category, merchant name, and custom note.
2. **Direct Obligation Creation:** Add an "Add Obligation" action on the Obligations card and screen, enabling users to enter loan/cheque title, installment amount, monthly due day, and total number of installments.
3. **Interactive Wealth Chart Scrubbing:** Add interactive pointer/touch drag handling to the wealth trend chart, rendering an anchored tooltip displaying the exact Toman figure and formatted Jalali date.
4. **Drill-Down from Reports to Filtered Ledger:** Enable tap gestures on report category and merchant breakdown rows that navigate directly to the Ledger tab with the corresponding filter pre-applied.
5. **Pull-to-Refresh in Ledger:** Wrap the Ledger timeline in `PullToRefreshBox` to trigger a background rescan of SMS messages and refresh transaction history.
6. **Live Search Filter in Add Asset Sheet:** Add a search input field with instant filtering for Iranian stock tickers, crypto tokens, and asset names.
7. **Transaction Sharing & Quick Copy:** Provide copy-to-clipboard and Android system share intent actions on transaction details with formatted Persian receipts.

## User Stories

1. As a user paying cash for groceries or taxi rides, I want to manually record a cash expense in the ledger, so that my tracked spending reflects my true financial activity.
2. As a user receiving cash income or off-system transfers, I want to manually log an income transaction, so that my cash reserves and total wealth remain accurate.
3. As a user buying gold coins or foreign currency with cash, I want to log the transaction with notes and custom merchant details, so that my portfolio history is consistent.
4. As a user taking out a bank loan or instalment plan, I want to directly create an obligation with monthly payment amounts and due days, so that I can track remaining payments even if the initial loan agreement was not received via SMS.
5. As a user issuing a post-dated Sayadi cheque, I want to record the cheque as an obligation, so that I receive visual urgency warnings before the payment date.
6. As a user reviewing my wealth history in the Reports tab, I want to drag my finger across the trend chart to view my net worth at any specific day, so that I can understand how my portfolio changed over time.
7. As a user inspecting my monthly expenses by category in the Reports tab, I want to tap on a category to see all transactions in that category within the Ledger, so that I can audit specific purchases without manual filtering.
8. As a user reviewing top merchants in the Reports tab, I want to tap a merchant to open the Ledger filtered by that merchant, so that I can quickly review my total spending at that store.
9. As a user waiting for an incoming bank SMS alert, I want to pull down on the Ledger screen to trigger an immediate rescan, so that newly arrived transactions appear without restarting the app.
10. As an investor adding Iranian stock holdings, I want to type the symbol name into a search bar, so that I can find "فولاد" or "خودرو" instantly instead of scrolling through hundreds of items.
11. As a cryptocurrency holder, I want to search for token symbols by name or ticker in the asset picker, so that I can quickly add my holdings.
12. As a user confirming a payment with a seller or friend, I want to copy the transaction reference number and details in one tap, so that I can send proof of payment without retyping.
13. As a user sharing financial records with family, I want to share a neatly formatted Persian receipt via messaging apps, so that they have full details of the transaction.

## Implementation Decisions

- **Manual Transactions:**
  - Utilize the existing `ManualTxn` entity in `DurableDb` and `manualToRow` bridge in `Derived.kt`.
  - Add an entry point in the Ledger header (Add button) opening an `AddManualTxnSheet` capturing amount, direction (expense/income), category, Jalali date, merchant, and note.
  - Expose `addManualTransaction` in `MainActivity` ViewModel inserting into `DurableDb.manual()` and updating derived state.
- **Direct Obligations:**
  - Add an "Add Obligation" button to `ObligationsCard` and `ObligationDetailScreen`.
  - Provide an `AddObligationSheet` with inputs for provider/title, total amount, down payment, installment count, installment amount, and start date.
  - Wire to `buildObligation` and persist to `InstallmentObligation` and `InstallmentPayment` tables.
- **Interactive Chart Scrubbing:**
  - Enhance `TrendChart` in `Report.kt` with pointer input / drag gestures.
  - Calculate nearest data point based on normalized touch X-coordinate and render an overlay indicator with Jalali date and formatted Toman amount.
- **Reports Drill-Down:**
  - Add tap callback to category rows in `Report.kt` that updates active filter state in `Timeline` and switches the current tab to `Tab.LEDGER`.
- **Ledger Pull-to-Refresh:**
  - Integrate `PullToRefreshBox` into `Timeline.kt` invoking `vm.refreshAll()` and `vm.scanSms()`.
- **Asset Picker Search:**
  - Add a search state and `OutlinedTextField` at the top of `AddSheet` filtering lists of stocks, coins, and crypto before rendering.
- **Transaction Sharing & Copy:**
  - Add action buttons in `TransactionScreen` invoking `ClipboardManager` and `Intent.ACTION_SEND` with formatted Persian transaction text.

## Testing Decisions

- Tests will focus exclusively on external observable behavior:
  - Unit tests for manual transaction mapping, reference generation, and signed amount calculations.
  - Unit tests for obligation schedule projection and due-date urgency calculations.
  - Unit tests for asset search filtering matching Persian names and Latin symbols.
  - Unit tests for formatted Persian receipt text generation.
  - Unit tests for chart data scrubbing coordinate-to-index mapping.
- Prior art: `LedgerTest.kt`, `InstallmentsTest.kt`, `TseParseTest.kt`, and `ReportsTest.kt`.
- Continuous validation: `testFullDebugUnitTest` and `testLiteDebugUnitTest`.

## Out of Scope

- Remote cloud syncing or server accounts (the application remains local-first and offline).
- Bank SMS sending or automated banking API transfers.

## Further Notes

All additions respect Iranian financial conventions (Persian numerals via Modam figures, Jalali calendar calculations, and Toman/Rial dual display).
