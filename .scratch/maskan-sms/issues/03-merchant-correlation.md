# 03 — Merchant correlation in derivation + PARSER_VERSION bump

**What to build:** When a Maskan debit confirmation arrives after a merchant-hint message (same amount, within 10 minutes), the merchant name from the hint appears on the transaction in the ledger instead of the fallback description. The hint message itself never produces a ledger entry. `PARSER_VERSION` is bumped so all transactions are re-derived.

**Blocked by:** 02 — needs `isMaskanHint` and `merchantInHint`.

**Status:** ready-for-agent

- [x] Hint messages filtered from the ledger (no `Txn` row created in `parseToRows`)
- [x] Hint messages scanned into a `PendingHint` buffer keyed by bank, with 10-minute TTL
- [x] Debit confirmation matching a pending hint (same bank + same amount + preceding timestamp within TTL) receives the hint's merchant name
- [x] Closest preceding hint consumed when multiple candidates exist
- [x] Consumed hints deleted from buffer (no reuse)
- [x] Unmatched/expired hints produce no ledger entries
- [x] `PARSER_VERSION` bumped to 3
