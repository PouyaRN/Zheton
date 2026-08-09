# 07 — Filter sheet keyboard fix

**What to build:** Typing in a filter token keeps the keyboard open: the row identity is independent of the typed value (so the field never dismounts), and the sheet resizes correctly around the keyboard like other sheets in the app.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Typing a character into a filter value keeps the keyboard open.
- [ ] The sheet resizes around the keyboard (imePadding applied).
- [ ] The sheet is not left in a partially expanded drag state (skipPartiallyExpanded applied).