# 07 — Batch note & merchant set/clear in the batch bar

**What to build:** The batch bar gains two free-text bulk actions — `note` and `merchant` — each offered as a **set** (write one value to every selected transaction) and a **«پاک کردن»** (clear for all). Merchant clearing reuses the alias semantics from ticket 02: clearing writes the blank display name so the parser's original name returns everywhere. Note clearing writes tombstones per row so no note re-appears. Both route through the same confirm-with-count dialog the other batch actions gained in 04, and the batch recomputes once.

**Blocked by:** 04 (the batch bar and confirm-with-count pattern). Uses the merchant-alias semantics established in 02.

**Status:** done

- [ ] The batch bar offers «یادداشت» (set and پاک کردن) and «فروشگاه» (set and پاک کردن) actions.
- [ ] Set writes the same durable note/alias records a per-row edit would; clear does the inverse (tombstone note, blank alias).
- [ ] Each action first confirms «اعمال به N مورد؟» and clears the selection after applying.
- [ ] The one-time recompute and family sync fire once per batch, not once per row.