# 11 — Merchant name override

**What to build:** From the transaction detail, correct a transaction's merchant/description. The corrected name is used for display and search, and survives re-derive; it does not feed the auto-classification rules.

**Blocked by:** None beyond the correction layer (05 recompute is not needed for display-only changes, but consistency with the correction stack applies).

**Status:** done

- [ ] A durable merchant override replaces the parsed merchant for display and search
- [ ] The override is not treated as a rule/classifier input
- [ ] The change survives re-derive and restart; the original parsed name is retained underneath