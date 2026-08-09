# 09 — Per-bank family publish scope

**What to build:** Behind the existing SMS-sharing switch in the family screen, the user can choose **which banks** their transactions publish from. Each account a bank is a toggle: sends or doesn't send to the household. Turning a bank off stops its transactions being shared (they stay on the phone and in the local ledger); turning it back on resumes once the sharing switch is also on. Bank-level only — no per-category or per-merchant granularity in this ticket.

**Blocked by:** 08 (the renamed family screen this control lives on).

**Status:** done

- [ ] Under the SMS-sharing switch, each tracked bank has a publish/mute control.
- [ ] A muted bank's transactions never appear in the shared household ledger; the local ledger is unaffected.
- [ ] Re-enabling publishes that bank's future transactions; previously muted rows are not retroactively dug up past the switch-on.
- [ ] Turning the master SMS-sharing switch off still stops everything, as today.