# 14 — Delete a category with reassignment

**What to build:** Deleting a user-created category that still contains transactions asks what happens to them: move them all to another category (e.g. «خرید») or choose a new category for each individually. A category with zero transactions deletes directly with no prompt.

**Blocked by:** the existing category CRUD (add/rename/archive) and 07 (hide built-ins) — plus the individual path can reuse the selection/search flow, so it benefits from 06.

**Status:** ready-for-agent

- [ ] Deleting an empty category: removed with no prompt
- [ ] Deleting a category with transactions: dialog offers «انتقال به…» (move all to a chosen target) or «یکبهیک» (choose individually)
- [ ] "Move all" rewrites the transactions' category decisions and re-derives
- [ ] "Choose individually" reuses the bulk-selection flow scoped to that category, letting the user assign each row
- [ ] Rules referencing the deleted category are reassigned or removed