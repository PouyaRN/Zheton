# 10 — Direction override and unpin to auto

**What to build:** From the transaction detail, change a transaction's direction (incoming / outgoing). Flipping reverses the sign, changes the offered categories, and — via the recompute (05) — moves the totals. This fixes the historical "deposit read as withdrawal" class of errors. Also, a manually pinned category can be unpinned back to automatic classification.

**Blocked by:** 05 (recompute, so the total follows the flip).

**Status:** ready-for-agent

- [ ] A durable direction decision overrides the parsed direction and sign for that transaction
- [ ] The visible amount sign and category choices update to match the chosen direction
- [ ] Flipping direction keeps the existing category (no silent reset) per the agreed design
- [ ] A "return to automatic" (unpin) control clears a manual category decision back to rule-based classification
- [ ] The recompute (05) updates balances and home total after any flip