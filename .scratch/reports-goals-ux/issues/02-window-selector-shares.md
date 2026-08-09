# 02 — Window selector drives category shares

**What to build:** The Reports tab gains a segmented window selector — «این ماه / ۳ ماه / ۶ ماه / ۱ سال / همهٔ» — that drives the spend-by-category breakdown. The year and "all" windows count the entire stored ledger, not the newest-300 window.

**Blocked by:** 01 (the reports tab).

**Status:** ready-for-agent

- [ ] The selector renders and switches the category-share view to the chosen window.
- [ ] Year / "all" windows count the whole store, matching rows older than the default timeline window.
- [ ] The this-month default remains month-to-date and live.