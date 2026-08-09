# 10 — Remove a family member

**What to build:** The family owner can remove another member from the household. Removal is a membership act, not a data deletion: the removed member's already-shared transactions stay in everyone's ledger (no tombstone storm), and only new rows from them stop arriving once the removal takes effect. The owner cannot remove themselves through this control; the UI asks for confirmation before removing.

**Blocked by:** 08 (the renamed family screen the member list and controls live on).

**Status:** ready-for-agent

- [ ] A member row exposes a «حذف»/remove action for the owner, with a confirmation before it applies.
- [ ] Removing a member stops new transactions from them appearing in the household ledger.
- [ ] Previously shared transactions from that member remain visible in every family ledger.
- [ ] The owner cannot remove their own membership through this screen.
- [ ] Sync continues working after a removal without errors.