# 05 — Corrections recompute the ledger balances

**What to build:** A single recompute path that, after any manual correction (direction, merchant, category, hide, delete), recomputes per-account balances and the home total from the corrected ledger — so repairing a "deposit read as withdrawal" or deleting a doubled transfer actually moves the user's visible money total.

**Blocked by:** None — can start immediately (shared mechanism; the correction tickets below consume it).

**Status:** done

- [ ] A single recompute entry point derives balances from the corrected ledger and updates both the fold and UI state
- [ ] Hand-typed balances with no SMS source are preserved, never overwritten
- [ ] Every correction feature in this spec routes through it; recompute is idempotent and survives re-derive
- [ ] After applying any correction, the home total and per-account balances reflect the change
- [ ] The recompute wants a unit test (correcting a direction flips the balance sign and total)