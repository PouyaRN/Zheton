# Spec: Corrections That Stick — durable deletes & hides, and undo for bulk actions

**Status:** ready-for-agent

## Problem Statement

The correction features added in tickets 01–14 can be **undone by the world around the app**:

1. **A permanent delete is a memory, not a decision.** `deletePermanently` (ticket 09) removes the raw
   message from `sms_source` and clears its decisions, but nothing durable remembers the row ever
   existed. The next `rewindIngest`/rescan reads the inbox from the horizon again, and because the
   inbox message itself is still there, `insertAll` (with its IGNORE upsert) accepts the row back
   into `sms_source`. The ledger rebuilds and the dead transaction is alive again — exactly what
   "permanent" promised not to do.

2. **A hide is only honoured late.** `hiddenRefs` (ticket 08) is consulted by the *renderers* —
   `ledgerEntries`, `allBalances`, `balanceOf`. The **fold** is not a renderer: `runScan` walks the
   inbox itself, applies each parsed `BankSms` via `applyBankSms` straight into `bankAccounts`, and
   keys deduplication off `seenSms` alone. It never reads `HIDE` decisions. So after a watermark
   rewind (or a fresh SMS re-auth which re-reads everything), a hidden transaction strides straight
   back into the account fold as if 08 never happened.

3. **Undo exists only for permanent delete.** Ticket 09 built the `pendingUndo` snackbar-with-door
   for deletes, because a delete without a mistap-and-regret story is cruel. Bulk hide (13) and bulk
   flip (13) skip straight to writing decisions with no window at all — a mis-tapped whole-batch
   flip of forty rows is forty manual flips back.

This is the audit backlog's top three recommendations (15-ux-audit-backlog: 1.3, 2.3 and 3.3).

## Solution

Make every correction durable at the **source**, not just at the surface:

1. **A durable tombstone for deletes.** Store the deleted source hashes in the durable store
   instead of relying on the row's absence. Ingest consults it before re-inserting a message —
   the fold and the derive both treat a tombstoned hash as "this message is gone for good". The
   decisions disappear as well, but the tombstone outlives any rewind.

2. **The fold honours `HIDE`.** `runScan` becomes an input consumer of the same hidden set the
   display path already reads: while it walks the inbox it skips any message whose source is
   hidden or tombstoned, so a rewind cannot re-fold a hidden transaction or a deleted one. The
   fold returns to being a plain fold of the messages that are *still true*.

3. **Undo parity for bulk actions.** The pending-undo infrastructure (10-second window, snackbar
   bar) is generalised so that bulk hide and bulk flip get the same one-tap undo window as
   permanent delete. When the window closes without an undo, the batch writes exactly as it
   would today; undo before the window closes is a no-op that keeps it all as it was. All batches
   re-derive exactly once on commit, so the recompute (05) re-runs once.

## User Stories

1. As a user who permanently deleted a transaction, I want nothing I do afterwards — a rescan, a
   rewind, a reinstall-with-backup-restore, an inbox re-read — to bring that transaction back, so
   that "delete" really means deleted.
2. As a user who re-confirmed a bank shortcode (which rewinds the inbox watermark), I want my
   deleted transactions to stay deleted, so that an operation about *other* messages cannot
   resurrect the ones I already removed.
3. As a user who deleted a transaction from a bank that no longer sends me statements, I want the
   deletion to survive a fresh install and restored backup, so that my corrected ledger is the one
   that comes back.
4. As a user who hides a transaction, I want the transaction to stay hidden even if the same
   message gets re-read from the inbox, so that my corrected ledger survives any inbox re-read.
5. As a user who hid a transaction and then rescanned the whole inbox, I want my hidden
   transaction to not re-enter my account balances or timeline, so that the hide is a real
   correction and not a UI trick.
6. As a user who flips the direction of a batch of transactions, I want the same "undo" window
   permanent delete gives me, so that a whole-batch mis-flip is a single tap to reverse.
7. As a user who hides a batch of transactions, I want the same undo window, so that a batch-hide
   mistake is not forty individual restore taps.
8. As a user who bulk-flips or bulk-hides with the undo window open, I want the countdown visible
   and the undo action on one affordance, so I can act before the window closes.
9. As a user who undoes a bulk action before its window closes, I want my balances on the home
   screen to recompute once per batch — not once per row — so the totals stay coherent and the
   undo feels instant.
10. As a user who deletes, hides, or flips in any combination, I want all three to be consistent
    with one another: a hidden row's deletions still tombstone, a deleted row's hide is irrelevant
    afterwards, and the fold, the ledger, the home total, and the family sync all tell the same
    story.
11. As a member of a household, I want my hidden and deleted transactions to stay hidden for other
    members too — the family ledger reads the same durable decisions the home does.
12. As a user who permanently deletes a transaction, I want the durable store to record the
    deleted source hash exactly once, idempotently, so that repeated deletes of the same source
    are harmless.
13. As an engineer, I want the tombstone check to live at the **first place a message can re-enter**
    (the fold, the ingest, the derive) rather than added to every consumer, so that a future path
    that picks messages up can't accidentally grow a path that resurrects deleted data.
14. As an engineer, I want to be able to say "undo a batch" is exactly "don't run the batch", so
    the semantics require no undo-decision or inverse-derivative machinery.

## Implementation Decisions

### 1. A durable tombstone set for deleted sources

- Add a durable key to the existing `durable_meta` key/value store: a `permanently_deleted` key
  whose value is the set of deleted source hashes (one per line). The storage lives in
  `Ledger.kt`'s durable DAO set (already used for `SOURCE_SCANNED_TO`, `ANCHORS_MIGRATED`, and
  sync meta).

- **When a source is deleted** (ticket 09's commit path, after the undo window elapses): the
  delete itself also adds the deleted row's `srcHash` into the tombstone set, in the same
  transaction as the source-row removal and the decision removal. Undoing within the window means
  the tombstone write never happens — there is nothing to undo separately.

- **When a message is considered for re-entry**, all three entry points count:
  - `ingestBankSms` (Ledger tier) skips any inbox row whose computed `srcHash` is tombstoned.
  - the fold (`runScan`) skips the same rows.
  - `sourceOf` / derive re-reads the durable rows; because tombstoned rows are never re-inserted,
    the derive sees nothing to resurrect. This is the input seam: the tombstone check is one
    function used at each point a message could come back, so future entry paths pick it up for
    free.

- **No new prefs sentinel.** The tombstone lives in the durable DB, which is covered by Android
  backup (ticket 03), so it survives reinstall/backup-restore along with the source rows
  themselves. If the whole durable DB is lost (backup disabled + fresh install) the source rows
  are lost too, which a fresh install legitimately is — nothing is ever wrong, just un-deleted.

- **Sync**: the tombstone need not sync as its own record — `outgoingRecords` already emits a
  record per entry; a tombstoned/source-gone entry produces no `Txn`, so it ceases to be
  published. Existing hide tombstones from 08 already cover family propagation. The tombstone is
  purely the local guard against re-ingest.

- The tombstone may later carry `deletedAt` (a `permanently_deleted_at` map) if pruning ever
  needs dates, but the key shape is a set of hashes — **not** a timestamp map — for v1.

### 2. The fold honours `HIDE` and the tombstone

- `runScan` builds one `skip` set before the loop: `seenSms` ∪ tombstoned source hashes ∪ source
  hashes of `HIDE` decisions that produce transaction refs `s:<hash>:…`. The hash prefix is
  derivable from the decision ref with the existing `refOf`/`srcHash` correspondence — no parser
  change, no new parse.
- The skip check runs at the same place `isMaskanHint` currently ignores messages
  (`runScan`), before `applyBankSms`. Hidden or tombstoned messages never touch `bankAccounts`,
  and they also enter `seenSms` (they've been genuinely dealt with; re-marking them seen keeps
  the fold silent on later reads of identical content).
- This is the **single seam**: `hiddenRefs` exists; the seam is narrow — the fold's loop just
  starts consulting it.
- The fold skip is a *pure* function: `sourceSkips(hideRefs) → Set<srcHash>` derived from the
  decision list — unit-testable without DB or inbox.

### 3. Generalised undo for bulk actions

- The `pendingUndo` state (state field + snackbar bar + delayed job from 09) is the existing
  door. Bulk hide and bulk flip reuse it instead of writing decisions immediately.
- The delayed job is parameterised by `kind`: the delete commitment (tombstone + remove + clear
  decisions), the bulk-hide commitment (write `HIDE` decisions), the bulk-flip commitment (write
  `DIRECTION` decisions). Undo = cancel the job; a cancelled job's commitments never ran, so there
  is **no inverse-decision**. This keeps "undo" trivially correct: it is "do nothing".
- Each batch still ends in `recomputeAccountBalancesFromLedger()` exactly once and a single
  re-`derive`, as ticket 13 already does on its batch paths.
- The snackbar-label count stays the rows; the `pendingUndo.count` and `label` fields already
  exist.

### No new schemas, no parser changes

- Uses only the existing `durable_meta`, `sms_source`, `txn_decision` and the prefs fold. No
  `DB` version bump, no `PARSER_VERSION` bump, no new migration.

## Testing Decisions

- **What makes a good test here:** external behaviour only — "does a tombstoned message ever come
  back?"/"does a hidden message re-fold?"/"does the undo window's cancellation leave every row
  exactly as it was?". No test should care *how* the tombstone is stored internally.
- The suite is plain JVM (no device); the code under test keeps pure: tombstone-set construction,
  the skip-criteria function for a message, and the batch commitments.

**Test modules and prior art:**

1. **A "tombstone set" test** in the `MoneyTest`-style (pure, no DB): adding the same hash twice
   is a no-op (the set is a fixpoint); hashes removed from the set are gone; decision refs map
   back to the source hash they came from.
2. **The "is this message still allowed in" predicate** — `DerivedTest`, same style as
   `applyDirectionOverride`: given (fold skip set, message hash, ref-prefix set) → message
   allowed / denied, with cases for seen/unknown/hidden/tombstoned.
3. **The two-step replay**: simulate the re-ingest-after-delete that today resurrects a row, then
   assert the tombstone holds — a pure seam test in `DeriveTest`:
   ```
   ingest(A) → derive → rows == 1 → deletePermanently(A) → tombstone(A) →
   rewindIngest → ingest again → derive → rows == 0
   ```
   Only the pure filter seams run on JVM; the inbox-read side stays a thin adapter asserted
   through the filter on both sides.
4. **Undo-window tests** asserting: `deletePermanently` before the window → full restore; bulk
   flip/hide before the window → entries unchanged and no decisions written; after the window →
   committed exactly once. These cover the pure planner where one exists, or a single VM smoke
   test where the harness allows.
5. **Corpus immunity:** all SMS corpus tests still pass unchanged — the new seams do not touch
   parsing.

## Out of Scope

- **Undo-after-commit**: no rollback once the window has closed and the write has run; undo only
  works "before the window". This sidesteps the entirely separate problem of undoing a message
  that has already hit the inbox again.
- **Chronology/audit of deletes** (deletedAt history) — not needed for the user story.
- **Server-side sync tombstone propagation mechanisms** beyond what decision-based family sync
  already does.
- **Confirmation UX changes from the audit (1.2 countdown, 2.2 per-item summaries)** — noted in
  the audit but separate from "corrections must stick".
- **Improvements to how permanently deleted transactions surface in exports** — deliberate,
  parity with existing hidden semantics.

## Further Notes

- The fold & ledger currently diverge in *where* they see truth (`runScan` vs `derive`). This spec
  makes the fold a thin observer of the same durability: both look through the tombstone + HIDE
  input. That is the whole point of item 1.3 + 2.3 being one design: they are one addition, the
  "skip" function, applied to the two places a message re-enters.
- 3.3 is deliberately the smallest item: it reuses machinery from 09; the actual new surface is
  "delay the write", not "undo a write that already happened".
- The audit's ranked table: this spec is items 1–3, with 2.1 (recompute log) and the rest queued
  for separate specs.