# 09 — Permanent delete with undo window

**What to build:** An "irreversible" delete that actually removes the durable source row (and its decisions), preceded by a short undo window (snackbar, ~10 seconds) so a mis-tap is not catastrophic.

**Blocked by:** 08 (hide is the safe default this escalates from).

**Status:** done

- [ ] A "Delete permanently" action exists in the transaction detail (separate from Hide)
- [ ] It shows a snackbar with an undo action for about 10 seconds
- [ ] After the window, the durable source row and its decisions are removed
- [ ] Undo restores the row completely; totals were already restored via 05