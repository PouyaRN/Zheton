# 07: Pull-to-Refresh & Text Search in Ledger Timeline

**What to build:** Pull-to-refresh swipe gesture in the Ledger tab triggering an immediate SMS rescan, along with an instant search bar to filter transactions by merchant name, note, or raw SMS content.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Pull-to-refresh wrapped around the timeline triggering `vm.scanSms()` and `vm.refreshAll()`.
- [ ] Search input bar in the Ledger filtering transactions dynamically by merchant, note, or text.
- [ ] Filter reset bar reflects active search query.
- [ ] Unit tests for query matching and filter state.
