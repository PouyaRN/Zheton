# 15 — UX audit and prioritized backlog

**Status:** published-for-approval

The correction and customization suite (01–14) is now implemented:
- 01 release signing, 02 no more SMS-schema wipes, 03 Android backup, 04 global OTP filter
- 05 balance recompute, 06 selection mode + bulk category, 07 hide built-in categories
- 08 hide a transaction, 09 permanent delete with undo window, 10 direction override
- 11 merchant display-name override, 12 bulk assign, 13 bulk hide/delete/flip
- 14 delete a category with transfer-to before allowing the row to die

This pass re-checks what those features were meant to fix, then ranks what is left.

---

## Priority 1 — data risk and correctness

### 1.1 The offline desync between BalanceAnchor and stated balances has no in-app explanation
When the fused balance (05) corrects an account after a wrong wrote-back delay, the figure moves
with **no visible reason**. Users querying the old number from her bank's own app will see the
discrepancy. Add a "چرا این عدد عوض شد؟" affordance on the account row that shows what corrected
it (per message or per typed balance) and how to flip it back.

### 1.2 Permanent delete needs a visible countdown, not just a snackbar
The 10-second snackbar is the only place that communicates the permanence. A snackbar that gets
hidden (by a notification, a rotated screen, the app acting up) silently commits the delete.
Better: also render the countdown inside the transaction sheet (a "حذف در ۰۷ ثانیه" line) so the
window is visible even while the snackbar is gone.

### 1.3 Sync + permanent delete don't coordinate
A permanently deleted message on this phone — the durable `sms_source` row — can still be
published to the family on the next sync (the row is gone but the link/decision metadata might
hang lazily) or will be re-read if the inbox is rescanned. Hardening: a durable tombstone list
(`permanently_deleted` hashes) kept out of the inbox fold, so a rescan/resync of the inbox cannot
resurrect a deleted message. Proof this is a real scenario: 04's OTP filter deliberately skips
only bodies, the fold still keeps them flagged seen.

### 1.4 The OTP filter can eat a legitimate bank statement
04 exempts `isMaskanHint` bodies but drops any *other* bank message whose card-to-card reference
contains a standalone رمز. A bank that writes "رمز خرید" in a statement, not a two-factor code,
would be fully suppressed. Audit the corpus once real production messages land; add a
"moved to hidden" bucket rather than hard-null when in doubt.

---

## Priority 2 — high impact

### 2.1 The recompute (05) still needs a log on the bank row
When several transactions are overridden in one afternoon, the user cannot see what she did at a
glance. Decide a "دو بخش آخر" per-account (last message, last decision) view.

### 2.2 Bulk actions don't write a per-item summary
Bulk hide/flip/delete show "N items" and one confirmation, but nothing lists *which*. If she
mixed a search filter and still selected a row that fell outside the filter, the row silently
drops out of the selection. Show the affected refs (or at least the range of dates) in the
confirm dialog before applying.

### 2.3 Hiding one transaction then re-reading the inbox re-folds only the seen-set
08 hides via a `HIDE` decision. If the same message is re-ingested (rollback/watermark rewind),
the row comes back **unhidden** — the fold never consults `HIDE`. The entry decision should be
consulted as a fold-input input, not just at display.

### 3.4 The timeline's «جهت مبلغ» chip may contradict the underlying message
The direction chip in TransactionScreen writes a durable direction decision, which is correct,
but retains the message-filed mixed direction text? No — the txn row was filed by the parser
with a direction, the chip moves the whole row. That's fine; the leftover is that the
`جهت مبلغ` row still reads "بازگشت به خودکار" even after a manual reset. Mark it "خودکار" so the
state is legible.

---

## Priority 3 — polish

### 3.1 Selection mode long-press has no discoverability affordance
The design note in ticket 06 says "a long press" enters selection, but there is no hint on the
row at all. Add a small "انتخاب" text button beside the search clear icon even when not
selecting so the mode is discoverable without experimentation.

### 3.2 Hidden built-ins in categories screen collapse
Ticket 07 hides built-ins from chips/pickers but the Categories screen still shows them greyed
out. Decide: either remove them there too (with a "نمایش مخفی" toggle) or keep the row but
mark "مخفی". Current state is inconsistent between the two surfaces.

### 3.3 Undo for bulk flip has no heat
09 grants 10s undo for permanent delete. Bulk flip (13) has no undo — an accidental batch flip
of 40 rows would require flipping each back by hand. Cheap add: reuse the pending-undo
mechanism for bulk flip and bulk hide (the transactions are all still there, just differential
re-derive).

### 3.4 Export does not mention hidden/kept rows
The export path skips hidden rows (correct), but the user has no signal that the file is
incomplete. Add a footer line "N row پنهان شده" in the exported CSV meta header.

### 3.5 Install/update flow: the release sign-off is manual
01 puts signing in the build, but no CI runs it and the keystore file is local. Actionable:
write a shell script `scripts/release-build.sh` that preflights keystore.properties existence
and version bump, and document it in the repo root.

---

## Prioritized order for implementation

| # | Item | Impact | Effort | Notes |
|---|------|--------|--------|-------|
| 1 | 1.3 Durable tombstone so deleted rows can't come back | High | Med | Correctness of 09 |
| 2 | 3.3 Undo for bulk flip/hide | High | Low | reuse 09 pending-delete state |
| 3 | 2.3 HIDE consulted at fold time | High | Med | closes a resurrection hole |
| 4 | 2.1 Recompute log on account row | Med | Med | transparency |
| 5 | 3.1 discoverability of selection | Med | Low | discoverability |
| 6 | 1.2 countdown visible in transaction sheet | Lo | Low | safety communication |
| 7 | 3.5 release script | Med | Low | ops ergonomics |
| 8 | 2.4 reverse-direction state label | Low | Low | clarity |
| 9 | 3.4 export footer | Low | Low | |
| 10 | 3.2 hidden-categories consistency | Low | Med | |
| 11 | 1.4 OTP filter keep-instead-of-drop | Med | Med | wait for real inbox |

## Anything auto-implemented here?
None. Items above are ranked candidates; nothing was built without explicit approval per the
ticket's block.

**Proposed next:** approve items 1–3 (the two resurrection/correctness holes and the bulk-undo
uniformity) as the next working set; the rest can stage later.