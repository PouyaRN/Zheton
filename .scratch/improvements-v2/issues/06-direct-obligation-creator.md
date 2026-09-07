# 06: Direct Obligation & Installment Creator

**What to build:** An "Add Obligation" action on the Obligations card and screen to manually register loans, installment purchases, or post-dated cheques (title, installment amount, due day, total count) without requiring an incoming bank SMS, auto-generating payment schedules and due countdown badges.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] Add Obligation dialog/sheet with provider/title, installment amount, count, and due date.
- [ ] ViewModel method to persist `InstallmentObligation` and scheduled `InstallmentPayment` records.
- [ ] Obligation appears in the Obligations card with calculated due date urgency badges.
- [ ] Unit tests for manual obligation generation and payment schedules.
