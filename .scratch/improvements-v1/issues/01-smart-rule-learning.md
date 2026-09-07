# 01 — Smart Rule-Learning for Merchants

**What to build:** When categorizing a transaction that has a known merchant name (`txn.merchantNorm.isNotBlank()`), default `learnSimilar` to enabled so the user does not have to manually toggle it every time. When categorizing, automatically insert a persistent `Rule` into `DurableDb` to classify all future transactions from that merchant. In `bulkCategorise`, automatically extract distinct merchants and generate corresponding user merchant rules.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] `TransactionScreen` initializes `learnSimilar` to true if `txn.merchantNorm.isNotBlank()`.
- [ ] Categorizing with `learnSimilar = true` persists an exact-merchant `Rule` in Room.
- [ ] `bulkCategorise` generates persistent rules for distinct merchants in the batch.
- [ ] Future transactions from the same merchant automatically receive the learned category.
- [ ] Unit test verifies rule persistence and derivation.
