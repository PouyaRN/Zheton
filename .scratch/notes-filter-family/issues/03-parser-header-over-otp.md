# 03 — Fixed parser: the header figure wins over the OTP tail

**What to build:** A fused bank message that carries both a transaction header («برداشت: 10,009,000 ریال», «خريداينترنتي:1,100,000-») and an OTP-style tail («مبلغ 10,000,000», «مبلغ 1,100,000») must record the **header** figure. Today the amount chain reads «مبلغ»/«مقدار» first, so the OTP tail (e.g. 10,000,000 — without the bank's fee) silently overrides the real debit (10,009,000), leaving the ledger inconsistent with the stated balance deltas. The fix flips the order for all banks: the direction keyword's figure is preferred when a body carries both a header and a tail. Real, verbatim messages (Tejarat fused debit + OTP, Bank Melli «خریا اینترنتی: مثبت» shapes) are added to the SMS corpus with a `why` so the behavior can't regress. This is the fee-inclusive, evidence-backed fix and does not change any other parsing behavior.

**Blocked by:** none — the parser code and the corpus are self-contained.

**Status:** done

- [ ] A message with both «برداشت: 10,009,000» and «مبلغ 10,000,000» records amount 10,009,000 (rate, header reading), not the tail figure.
- [ ] Bank Melli «خریا اینترنتی: <amount>±» messages (using the «مقدار» tail) record the header amount.
- [ ] The corpus gains verbatim fixture(s) for both shapes with explanations; both parsers (Kotlin and the synced PWA side that shares the shapes) stay in agreement.
- [ ] All existing corpus fixtures keep passing; the «টি» feature is unchanged.
- [ ] A stated balance on the final message still anchors the account exactly as today.