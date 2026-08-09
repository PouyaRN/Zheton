# 06 — All-history filtering beyond the timeline window

**What to build:** Makes the filter (04/05) search the **whole stored ledger**, not the newest-300 visible window. Today the timeline window holds the newest few hundred rows; the filter querying only those would miss a historic «اسنپ» from last year. This ticket moves the match source to the complete transaction store: a filter result can count—and select—matches older than the visible window, with a lazy sample rendered in the list. No new SMS permission is needed (the data is already local); the recompute that follows a bulk action re-derives once for the whole batch as today.

**Blocked by:** 04 (the filter sheet and selection model it feeds).

**Status:** ready-for-agent

- [ ] A filter can match and select transactions far older than the newest-300 window.
- [ ] «select all» and the batch-bar count reflect the full ledger, not the rendered sample.
- [ ] The timeline still renders lazily; an enormous match-set (hundreds) works without stalling.
- [ ] Bulk actions on an all-history match still confirm with the count and re-derive once.