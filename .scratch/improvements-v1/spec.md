# Spec: Smart Rule-Learning, Comprehensive CSV Export, AMOLED Theme, and Obligation Due Indicators

## Problem Statement

Users managing their personal finances in MuchToman face several key friction points:

1. **Repetitive Manual Categorization:** When users manually categorize transactions from frequent merchants (e.g., Snapp, Digikala, local bakeries, hypermarkets), the toggle to "remember for future transactions" is turned off by default, requiring repetitive manual filing for every new SMS. Furthermore, bulk-categorizing past transactions does not teach rules for future incoming SMS.
2. **Truncated and Garbled CSV Export:** The existing CSV export only dumps the currently loaded 300 entries instead of the full ledger history, omits transaction notes, lacks Toman conversions, and lacks a UTF-8 Byte Order Mark (BOM), causing Persian text to display as unreadable garbage characters when opened in Microsoft Excel.
3. **Missing AMOLED Deep Black Mode:** Users with OLED/AMOLED mobile screens want a true pitch-black (`#000000`) theme for maximum battery savings and enhanced contrast at night, beyond standard dark grey.
4. **Lack of Cheque and Obligation Due Date Urgency:** Iranian users heavily rely on Sayadi cheques and monthly installment loans. The obligations screen does not clearly highlight imminent deadlines (e.g., due in 3 days or overdue) with visual urgency.

## Solution

1. **Smart Rule-Learning:** Default `learnSimilar` to enabled whenever a transaction carries a recognized merchant name. Ensure that selecting a category automatically creates a persistent `Rule` for future SMS and optionally updates previous occurrences. Support rule generation during bulk categorization.
2. **Full-Ledger UTF-8 BOM CSV Export:** Export all non-duplicate ledger entries across the entire database history, including both Toman and Rial amounts, Jalali dates and times, merchant names, bank names, directions, and transaction notes. Prepend `\uFEFF` UTF-8 BOM so Excel opens Persian text cleanly out-of-the-box.
3. **AMOLED Theme Mode:** Introduce `ThemeMode.AMOLED` with pure `#000000` background, calibrated contrast containers, and integrate it into the theme selector in settings.
4. **Obligation Due Date Urgency Badges:** Display calculated Jalali countdowns on installments and cheques with color-coded badges for due soon (amber) and overdue (error) states.

## User Stories

1. As a user categorizing an expense from a known store, I want the app to automatically remember this store for future transactions by default, so that I do not have to classify the same store repeatedly.
2. As a user bulk-categorizing multiple transactions from a merchant, I want the option to have the app learn the rule for future incoming SMS, so that my ledger stays organized automatically.
3. As a user exporting my financial transactions to Excel, I want the export to include my entire transaction history, so that I have a complete archive of all my data.
4. As a user opening the CSV export in Microsoft Excel, I want the Persian letters and numbers to display without encoding corruption, so that I can inspect and analyze my transactions immediately.
5. As a user reviewing exported spreadsheets, I want both Toman and Rial figures alongside custom notes, so that my accounting matches both bank statements and colloquial prices.
6. As a user with an AMOLED smartphone, I want a pure black background theme option, so that I can maximize battery life and minimize eye strain at night.
7. As a user paying monthly loan installments or issuing Sayadi cheques, I want to see clear days-remaining countdowns and overdue warnings, so that I never incur penalties or damage my credit history.
8. As a user filtering the timeline, I want a visible active filter indicator with a one-tap clear button, so that I know why items are filtered and can return to the full list instantly.

## Implementation Decisions

- **Rule Learning:**
  - In `Timeline.kt` (`TransactionScreen`), initialize `learnSimilar` to `true` when `txn.merchantNorm.isNotBlank()`.
  - In `MainActivity.kt` (`bulkCategorise`), extract unique merchants from the categorized batch and insert corresponding `Rule` records with `Priority.USER_EXACT_MERCHANT` into `DurableDb`.
- **CSV Export:**
  - In `MainActivity.kt` (`exportCsv`), read `_state.value.ledger.allEntries`, fetch notes from `_state.value.ledger.notes`, calculate both Toman and Rial figures, format Jalali date and time, and prepend `\uFEFF` before writing to cache and sharing via Android `ACTION_SEND` Intent.
- **AMOLED Theme:**
  - In `Theme.kt`, add `AMOLED("آمولد")` to `ThemeMode`.
  - Define `AmoledDarkColors` using `darkColorScheme` with `background = Color.Black`, `surfaceContainerLowest = Color.Black`, and tailored high-contrast container surfaces.
  - In `Lock.kt`, update `ThemePicker` to include the `AMOLED` choice with appropriate Persian label.
- **Obligation Urgency Badges:**
  - In `ObligationsUi.kt` (or obligation cards), calculate days remaining relative to Tehran today (`tehranDay`), rendering an amber badge for payments due within 5 days and an error badge for overdue unpaid installments.

## Testing Decisions

- Unit tests must verify:
  - Rule creation and predicate matching for learned merchants.
  - CSV output contains `\uFEFF`, header rows, correct Jalali date conversions, both Toman and Rial, and notes.
  - Theme mode serialization and resolution in preferences.
  - Days-left calculation for Jalali obligation due dates.
- All existing tests in `testFullDebugUnitTest` and `testLiteDebugUnitTest` must pass without regression.
- Both debug and release builds must pass with zero lint errors.

## Out of Scope

- Cloud sync or external server integrations (app remains 100% offline and privacy-first).
- Destructive Room schema migrations (database schema remains backward-compatible).

## Further Notes

All changes maintain complete data persistence and compatibility with existing databases and backups.
