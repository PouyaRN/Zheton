# Spec: Category Management, Transaction Notes, Stability Fixes, and Bank Support

## Problem Statement

The MuchToman (چقدر تومن) Android app had several usability and reliability issues:

1. **Obligations and reservations data missing on cold start.** The home screen's obligations card and reservations card only appeared after performing an explicit action (family sync, save, etc.), not when the app first opened — even when the data existed in the database.

2. **Race conditions on rapid taps.** Tapping "آزاد" (release) or "ذخیره" (save) in quick succession caused state corruption or crashes because concurrent operations on obligations/reservations were not serialized.

3. **UI jumps on the home screen.** The LazyColumn conditionally inserted/removed the obligations card and reservations card items based on data emptiness, causing layout shifts and scroll position jumps when data loaded or changed.

4. **Transaction notes not persisted visually.** The `getNote()` function always returned an empty string (placeholder code), so saving a note via "ذخیره یادداشت" appeared to work but the note vanished on recomposition. Notes were also invisible on the timeline — users had to tap into each transaction to see if one existed.

5. **No category management.** Categories were entirely hardcoded (27 builtins). Users could assign categories to transactions but could never create, rename, or delete their own categories.

6. **Bank Maskan SMS parsing unsupported.** Transactions from Bank Maskan (بانک مسکن) were not parsed, leaving those users with no automatic transaction detection.

7. **Bank Sepah SMS parsing unsupported.** Transactions from Bank Sepah (بانک سپه, sender `6715000`) were not parsed. Sepah messages use a compact format with short date stamps (`M/D-HH:mm`) and group payment messages that require special direction handling.

8. **Bank Tejarat SMS parsing unsupported.** Transactions from Bank Tejarat (بانک تجارت, sender ID `TejaratBank`) were not parsed. Tejarat sends two messages per transaction: a balance-bearing message and an OTP/confirmation message — both would create duplicate ledger entries. Its `نوع تراکنش:` field needs explicit direction mapping.

## Solution

Address all six issues through bug fixes, new features, and a new bank parser:

1. Load obligations, payments, and reservations during the ledger pipeline so they appear on the first frame.
2. Guard obligation/reservation state refreshes with a `Mutex` so concurrent callers queue.
3. Always render the obligations and reservations cards (showing empty state text when no data) to keep the LazyColumn structure stable.
4. Load notes from the `txn_decision` table into `LedgerView.notes` and read from that map in `getNote()`.
5. Add a full category CRUD system: DAO methods (`put`, `archive`), ViewModel operations (`addCategory`, `editCategory`, `deleteCategory`), and a management screen accessible from Settings.
6. Display saved notes as a subtitle on the timeline card under the merchant name.
7. Register Bank Maskan sender variants, parse its debit SMS, detect merchant hints from OTP messages, and correlate them with confirmations.
8. Register Bank Sepah (sender `6715000`) and Bank Tejarat (sender ID `TejaratBank`) as built-in banks with parser overrides for group payment direction, OTP filtering, and transaction-type direction parsing.
9. Bump `SMS_SCHEMA` from 11 to 12 to force a full re-parse with the new bank support.

## User Stories

1. As a user, I want obligations and reservations to appear on the home screen immediately when I open the app, so that I can see my financial commitments without performing any action first.
2. As a user, I want to tap "ذخیره" or "آزاد" rapidly without the app crashing or showing wrong data, so that I can manage my finances quickly.
3. As a user, I want the home screen layout to remain stable when data loads or changes, so that I don't lose my scroll position or see content jumping around.
4. As a user, I want to save a note on a transaction and see it persist after I leave and return to the transaction detail screen, so that I can record context about my spending.
5. As a user, I want to see my saved notes directly on the timeline card (as a subtitle under the merchant name), so that I can quickly identify which transactions have notes without opening each one.
6. As a user, I want to create new categories for transactions that don't fit the built-in ones, so that I can organize my spending in a way that matches my life.
7. As a user, I want to rename a category I created, so that I can correct typos or update the name as my needs change.
8. As a user, I want to delete (archive) a category I created, so that I can remove categories I no longer use.
9. As a user, I want built-in categories to be protected from editing or deletion, so that the app's core taxonomy remains intact.
10. As a user, I want to manage categories from the Settings screen, so that I have a clear, dedicated place for this configuration.
11. As a Bank Maskan customer, I want my Maskan transactions to be automatically parsed from SMS, so that I don't have to enter them manually.
12. As a Bank Maskan customer, I want the merchant name to be detected from Maskan OTP messages, so that my transactions show meaningful descriptions.
13. As a user, I want my existing notes to be visible when I open the transaction detail screen, so that I don't have to re-enter them.
14. As a user, I want the "ذخیره یادداشت" button to appear only when the note text has changed, so that I know when there's something new to save.
15. As a user, I want empty categories to have a fallback icon (three dots), so that newly created categories still look consistent in the UI.
16. As a Bank Sepah customer, I want my Sepah transactions to be automatically parsed from SMS, so that I don't have to enter them manually.
17. As a Bank Sepah customer, I want group payment messages (`پرداخت گروهي` + `واريز گروهي`) to be treated as incoming money, so that loan disbursements and group transfers appear correctly in my ledger.
18. As a Bank Sepah customer, I want non-transaction notifications (such as "ورود به موبايل بانک") to be ignored, so that my ledger is not cluttered with login alerts.
19. As a Bank Tejarat customer, I want my Tejarat transactions to be automatically parsed from SMS, so that I don't have to enter them manually.
20. As a Bank Tejarat customer, I want OTP/confirmation messages (containing `رمز` but no `مانده`) to be filtered out, so that I don't see duplicate ledger entries for the same transaction.
21. As a Bank Tejarat customer, I want the `نوع تراکنش:` field to determine transaction direction, so that `بازپرداخت/واريز` is correctly classified as income even though it contains the substring `پرداخت`.
22. As a Bank Parsian customer, I want the app to recognise messages from the `PARSIANBANK` sender, so that my Parsian transactions reach the parser (even though full corpus coverage awaits a real SMS sample).

## Implementation Decisions

### Category CRUD

- `CategoryDao` gains `put(row)` (single insert via `@Insert(onConflict = REPLACE)`) and `archive(id, updatedAt)` (soft-delete via `UPDATE category SET archived = 1, updated_at = :updatedAt WHERE id = :id`).
- `AppVm` gains `addCategory(nameFa, kind)`, `editCategory(id, newNameFa)`, `deleteCategory(id)` — all guard against editing/deleting builtin categories by checking `existing.builtin` before proceeding.
- New categories use IDs prefixed with `cat_user_` followed by a UUID7 timestamp to ensure uniqueness and chronological ordering.
- `seedBuiltins()` continues to upsert all builtins on every launch via `putAll`; user-created rows (with `builtin = false`) are never overwritten by this path.
- Categories sort after builtins by appending 10 to the current max sort value.

### Category Management UI

- `CategoriesScreen` composable renders a `LazyColumn` of all categories, each showing: `CategoryIcon`, Persian name, kind label (خرج / درآمد / انتقال), and for non-builtin categories, edit and delete icon buttons.
- Three dialogs: add (text field for name + `FilterChip` selector for expense/income), edit (text field pre-filled with current name), delete (confirmation with category name).
- Accessible from Settings screen via a clickable row labeled "مدیریت دسته‌بندی‌ها" with a forward-arrow icon — uses a plain `Row` with `clickable`, not a `SettingCard` (which always renders a `Switch`).
- Navigation uses a `categoriesManaged` boolean state in `AppRoot`. Critical: the `onCategoriesManage` callback sets both `settings = false` and `categoriesManaged = true` to ensure the settings screen unmounts before the categories screen mounts.

### Transaction Notes

- `LedgerView` gains a `notes: Map<String, String>` field (transaction ref → note text), populated in `ledgerView()` by querying `txn_decision` for `kind = "note"` decisions and filtering to non-null values.
- `getNote(ref)` reads synchronously from `_state.value.ledger.notes[ref].orEmpty()` — no DB query on the UI thread.
- `saveNote()` immediately updates the notes map in state (`_state.update { it.copy(ledger = it.ledger.copy(notes = it.ledger.notes + (ref to note))) }`) before the full ledger rebuild, so `getNote()` returns the new value on the very next composition.
- `TimelineRow` renders the note as 12sp muted text (`onSurfaceVariant` at 0.7 alpha) below the merchant name, max 1 line with `TextOverflow.Ellipsis`, only when `state.ledger.notes[txn.ref]` is non-null and non-blank.

### Stability Fixes

- `ledgerMutex: Mutex()` added to `AppVm` as a private field.
- `refreshObligations(durable)` wrapped in `ledgerMutex.withLock { ... }` so concurrent callers queue instead of racing on `_state.update`.
- `runLedgerPipeline()` calls `refreshObligations(durable)` at the end (after `ledgerView` update), loading obligations/reservations on every ledger run including cold start.
- `requestFamilySync()` replaces its inline obligations/reservations loading (10 lines) with a single call to `refreshObligations(durable)`.
- LazyColumn items for obligations and reservations are always rendered — no conditional `if (state.X.isNotEmpty())` guards around the `item(key = ...)` blocks.
- `ObligationsCard` and `ReservationsCard` check emptiness internally: when empty, they render a `Card` with muted placeholder text ("اقساط فعالی نیست" / "رزرو فعالی نیست") instead of calling `return` early.

### Bank Maskan SMS

- Three issue specs completed under `.scratch/maskan-sms/issues/`: sender registry, merchant hint detection, merchant correlation.
- `maskan.json` test fixture added for corpus testing.
- `PARSER_VERSION` bumped to 3.

### Bank Sepah & Bank Tejarat SMS

- `SEPAH("بانک سپه", listOf("6715000"))` and `TEJARAT("بانک تجارت", listOf("TejaratBank"))` added to the `Bank` enum in `Sms.kt`.
- Both get `BankLogo` entries mapping to existing drawable assets (`ic_bank_sepah.xml`, `ic_bank_tejarat.xml`).
- The parser body is bank-agnostic after the sender gate, so both banks inherit the standard Persian vocabulary matching (`واریز`, `برداشت`, `خرید`, `مانده`, etc.).

**Sepah parser overrides** (inside `parseBankSms()`):

- **Group payment direction**: when the normalised text contains both `پرداخت گروهی` and `واریز گروهی`, force `deposit = true; withdrawal = false`. Without this override, `پرداخت` (in `OUT_WORDS`) and `واریز` (in `IN_WORDS`) both match, producing `direction = null`. The compound phrase is the reliable signal that the money is incoming.
- **Non-transaction messages** (e.g. `ورود به موبايل بانک`): naturally ignored because they contain no amount figure and no balance — `parseBankSms` returns null when both `delta` and `balance` are null.
- **Unit behaviour**: Sepah amounts without explicit "ریال" suffix (like `برداشت:376,420`) are treated as Toman by the parser's fallback divisor (10.0). The `Figure.rial()` function then multiplies by `(10.0 / 10.0) = 1.0`, so `amountRial` equals the raw figure. Deposit amounts with explicit "ریال" suffix (like `واريز:16,932,000ريال`) get the correct ÷10 conversion.
- **Direction null on deposits**: Sepah deposit messages often end with `انتقال وجه پايا`, which contains `انتقال` (in `OUT_WORDS`). This conflicts with `واریز` (in `IN_WORDS`), producing `direction = null`. The balance anchor still records correctly; only the sign is unknown.

**Tejarat parser overrides** (inside `parseBankSms()`):

- **OTP filter** (early return): if `bank == Bank.TEJARAT` and the normalised text contains `رمز` but not `مانده`, return null. This filters out the OTP/confirmation messages that accompany each transaction.
- **Direction from `نوع تراکنش:`**: when the normalised text contains `نوع تراکنش:`, extract up to 60 characters of the label after it and use it to override the standard `IN_WORDS`/`OUT_WORDS` direction:
  - `بازپرداخت` or `واریز` → `deposit = true; withdrawal = false` (credit)
  - `خرید` or `انتقال` or `برداشت` → `deposit = false; withdrawal = true` (debit)
- This override is necessary because `بازپرداخت/واريز` contains both `پرداخت` (OUT) and `واریز` (IN), which the generic matching would classify as unknown.

**Schema bump**: `SMS_SCHEMA` incremented from 11 to 12 in `Data.kt`. This clears `bankAccounts`, `seenSms`, `smsScannedTo`, `unknownSenders`, and `strangers` from SharedPreferences, forcing a full re-read and re-parse of all SMS on next app launch.

**Parsian coverage**: `parsian.json` added with 1 synthetic test case (clearly marked `"SYNTHETIC — no real Parsian SMS available yet"`). The `PARSIAN` enum entry and logo already existed; this closes the corpus coverage gap without inventing verified behaviour.

## Testing Decisions

- **Unit tests** for the derive/fold logic and money formatting already exist (`MoneyTest`, `DeriveTest`, etc.) and were not modified.
- **Category CRUD** is testable at the ViewModel level: `addCategory` creates a non-builtin row visible via `categories().all()`, `editCategory` rejects builtins (no state change), `deleteCategory` sets `archived = 1` (row excluded from `categories().all()`).
- **Notes persistence** is testable by calling `saveNote`, then verifying `getNote` returns the saved text from `_state.value.ledger.notes`.
- **Mutex serialization** is hard to unit-test deterministically; the existing integration coverage (app opens, obligations appear) serves as the regression surface.
- **Maskan SMS parsing** has a dedicated corpus test (`SmsCorpusTest`) with the `maskan.json` fixture — this is the established pattern for bank parser tests.
- **Sepah corpus** (`sepah.json`): 37 cases covering `برداشت:` (expense), `خريد اينترنتي:` (expense), `خريد پايانه فروش:` (expense), `واريز:` with explicit ریال suffix (income), `پرداخت گروهي` + `واريز گروهي` (income via override), and `ورود به موبايل بانک` (null — non-transaction). Key testing insight: amounts without explicit "ریال" are treated as Toman by the parser, so `amountRial` equals the raw figure (÷10 ×10 = same). Balances without "ریال" follow the same pattern.
- **Tejarat corpus** (`tejarat.json`): 8 cases covering `خريد شتابي` (debit), `انتقال به شتابي` (debit), `بازپرداخت/واريز` (credit via `نوع تراکنش:` override), `برداشت` with masked card (debit), and OTP messages with `رمز` but no `مانده` (null — filtered).
- **Parsian corpus** (`parsian.json`): 1 synthetic case, clearly marked. Validates that the `PARSIANBANK` sender gate reaches the parser body. Real-world coverage pending a genuine SMS sample.
- **MoneyTest update**: the `collapseAccounts` test now expects `TEJARAT` to be kept (not dropped) because it has a recognised enum entry. The `bankTotal` assertion updated to account for TEJARAT's large balance no longer being filtered out.
- Prior art: the codebase uses Room in-memory databases for DAO tests and a JSON corpus approach for SMS parsing tests.
- Good tests in this codebase test external behavior (does the SMS parse produce the right `Txn`?), not implementation details (was the mutex acquired?).

## Out of Scope

- Subcategories (the `parentId` field exists on `Category` but is unused — always null).
- Category reordering / drag-to-sort.
- Category icons for user-created categories beyond the fallback `CategoryGlyph.DOTS` (three dots).
- Bulk category reassignment (moving all transactions from one category to another).
- CSV export of notes (currently stubbed as `""` in `exportCsv`).
- Search across notes (currently search only covers merchant, category name, and amount).
- The family sync path for categories (categories are local-only, not synced between family members).
- Tag system independent of categories (tags and categories are the same concept in this app).

## Further Notes

- This spec covers work already implemented and pushed to `securityvisions/Dakh-o-kharj` in commits `958a93d`, `6b3fa02`, and `4263067`.
- All `.scratch/maskan-sms/issues/` specs are completed and resolved.
- The DB schema is at version 8; no further migrations were needed for these features (notes use the existing `txn_decision` table, categories use the existing `category` table).
- The `SettingCard` composable in `Lock.kt` always renders a `Switch`; the categories entry uses a plain clickable `Row` instead to avoid a misleading toggle.
