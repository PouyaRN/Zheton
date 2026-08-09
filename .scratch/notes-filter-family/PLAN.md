# Plan: Four tracks — auto-save notes, parser amount fix, bulk filter/select/edit, family

Status: **tickets published** — see `issues/01..10`.

Decision tree recorded during grilling; each leaf is a ticket.

## Track 1 — Notes & merchant auto-save (tiny)

- Remove «ذخیره یادداشت» and «ذخیره نام»; both fields autosave on ~600 ms debounce.
- Transient «ذخیره شد» on land; flush on leaving the screen.
- Clearing the note tombstones it (deleted decision).
- Notes stay device-local (never pushed to family; sync stays as-is).
- Merchant keeps **global-alias** semantics (renames every row of that merchant) + scope hint.
- Consistency: member-name/settings forms keep their explicit save buttons.
- Tickets: 01 (note), 02 (merchant).

## Track 2 — Parser: header figure beats OTP tail (Tejarat first, Melli too)

- Bug (proven via real SMS): fused message's `مبلغ …` OTP tail (10,000,000) wins over the header «برداشت: 10,009,000» because AMOUNT_WORDS is checked first — a 9,000-rial fee silently drops. Fix prefers the header figure, all-bank generic.
- No fee-splitting; no speculative hardening (مانده/موجودی guard, same-ms tie) this round.
- Verbatim Teja + Melli fixtures in the corpus with `why`.
- Ticket: `03`.

## Track 3 — Bulk search / filter / select / edit (big)

- Search opens a filter sheet: structured tokens (merchant, category, bank, direction; then amounts/date/refNo + not-contains + raw regex on merchant), all AND-composed.
- All-history query (not the newest-300 window); selection and count track the full match set; editing a criterion clears the selection.
- Batch bar acts on every batch with a confirm-with-count; adds note & merchant set/clear verbs.
- Merchant bulk = global alias (same semantics as Track 1); note bulk = tombstones.
- Tickets: `04` (sheet v1), `05` (semantics v2), `06` (all-history), `07` (batch note/merchant).

## Track 4 — Family vs «همراه» discovery and scoping

- Rename tab to «خانواده»; first-open welcome + permanent «این تب یعنی چی؟» explaining the household ledger, SMS-sharing-off default, and the second phone/browser end.
- Remove a member = membership act (history stays; no tombstones).
- Per-bank publish scope behind the sharing switch.
- Tickets: `08` (rename + explainer), `09` (per-bank scope), `10` (remove member).

## Execution priority

Smallest-and-evidence-first: `03`, then `01`+`02` (parallel-safe), then `04→05/06/07`, then `08→09/10`.