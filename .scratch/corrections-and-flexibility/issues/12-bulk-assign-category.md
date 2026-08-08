# 12 — Bulk assign category

**What to build:** In selection mode, a single pick assigns one category to every selected transaction — e.g. search «اسنپ», select all matches, assign «حمل و نقل» in one action.

**Blocked by:** 06 (selection mode).

**Status:** done

- [ ] After selecting rows (and "select all matches"), a category picker assigns that category to all selected transactions
- [ ] Each selected transaction's category decision is written durably; pinned categories are replaced
- [ ] The timeline and reports reflect the change immediately
- [ ] The operation shows a confirmation with the affected count before applying