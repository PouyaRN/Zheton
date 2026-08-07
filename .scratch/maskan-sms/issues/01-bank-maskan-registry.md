# 01 — Bank Maskan: registry, parser improvements, corpus fixture

**What to build:** A user who receives a Bank Maskan debit SMS sees their balance update in the app with the printed date (`0516-14:06`) and account number (`4111100067462`) captured. The PWA paste parser also captures the date. Maskan transfers parse correctly too. Invisible LRM/RLM characters are stripped during normalisation so they can never split a number.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [x] `Bank.MASKAN` registered with sender variants (`Bank Maskan`, `MaskanBank`, `Maskan Bank`)
- [x] `BankLogo` renders `ic_bank_maskan` for the new enum entry
- [x] LRM (U+200E) and RLM (U+200F) stripped in `normalise`
- [x] `PRINTED_AT` regex extended for `0516-14:06` (MMDD-HH:MM) on Android and PWA
- [x] `labeledAccount` captures bare account numbers after `حساب:` (label must start the line)
- [x] `maskan.json` corpus fixture with debit confirmation, merchant hint, standalone debit, and transfer cases
- [x] All edits verified by reading modified files
