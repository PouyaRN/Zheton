# 13 — Bulk hide / delete / flip direction

**What to build:** When selection mode is active, the batch bar supports three bulk actions across all selected transactions: Hide, Delete permanently (with the undo window), and Flip direction — so large mistaken batches are corrected in one action instead of one by one.

**Blocked by:** 06 (selection), 08 (hide), 09 (permanent delete), 10 (direction override).

**Status:** ready-for-agent

- [ ] Batch "Hide" writes the hide decision for every selected row
- [ ] Batch "Delete" deletes every selected row with a single undo window (09)
- [ ] Batch "Flip direction" toggles direction on all selected rows, with one confirmation
- [ ] After the batch, the recompute (05) updates balances and totals once