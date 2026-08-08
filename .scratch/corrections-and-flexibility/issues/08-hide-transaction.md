# 08 — Hide a transaction (undoable delete)

**What to build:** The primary "delete" for a wrong transaction: a durable hide decision removes the row from the timeline, reports, and totals — restorable later. This is the manual fix for the double-counted Maskan transfer and any other mistaken entry.

**Blocked by:** 05 (recompute — hiding must change the visible totals).

**Status:** done

- [ ] A durable hide decision is written when the user deletes a transaction
- [ ] Hidden rows are excluded from the timeline, reports, and totals; totals update per the recompute (05)
- [ ] A "show hidden" toggle reveals them rendered struck-through; tapping a hidden row restores it
- [ ] Hides survive re-derive and family sync (they are decisions in the durable store)