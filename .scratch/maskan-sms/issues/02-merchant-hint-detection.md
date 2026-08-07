# 02 — Merchant-hint detection and extraction

**What to build:** The parser can identify a Maskan purchase-initiation message (the one with the OTP and merchant name) and extract the merchant name from it. The hint is detected but not yet correlated — the merchant doesn't appear on the debit confirmation yet.

**Blocked by:** 01 — bank must be registered; corpus fixture must exist.

**Status:** ready-for-agent

- [x] `isMaskanHint` returns true only for the exact shape: `خرید` + `مبلغ` + `رمز` + no `مانده`
- [x] `isMaskanHint` returns false for `انتقال:+500,000,000` and other non-hint Maskan messages
- [x] `merchantInHint` extracts the merchant name (e.g. `دیجی پی`) from the line after `خرید`
- [x] Corpus case for the hint message in `maskan.json` asserting parser output (delta present, no balance, merchant empty)
