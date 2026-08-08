# 04 — Global OTP / non-transaction filter

**What to build:** Messages carrying a one-time code (`رمز`) with no stated balance (`مانده`) are ignored as non-transactions across all banks. This closes the double-counting bug where the Bank Maskan card-to-card transfer produced two ledger entries: the OTP/confirmation message (which contained «انتقال» but no balance) and the send-confirmation (which contained «انتقال» plus a balance).

**Blocked by:** None — can start immediately.

**Status:** done

- [ ] The Tejarat-only ignore-rule (رمز without مانده) applies to every bank in the parser
- [ ] Sender recognition is unchanged — filtered OTP messages are silently skipped, not surfaced as unknown senders
- [ ] Corpus fixtures cover the Maskan card-to-card pair and a cross-bank OTP shape
- [ ] Existing double-counted rows are not auto-modified; they stay until the user corrects them manually (hide/delete)