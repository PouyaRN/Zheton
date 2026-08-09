# 04 — Goals: make a budget mean something

**What to build:** Creating a cap goal now **persists a scope** — `category:<id>` or `merchant:<merchantNorm>` — picked in the goal UI (merchant from a recent-merchants picker, bound to the stable merchant identity, display label separate). The cap then sums her **real spending** for that scope: the «$0 used on a ۲٬۰۰۰٬۰۰۰ cap» bug is dead.

**Blocked by:** None — can start immediately.

**Status:** ready-for-agent

- [ ] Creating a cap stores the selected scope (not null).
- [ ] The scope can be a category or a merchant (picked from the ledger's distinct merchants).
- [ ] The cap progress sums actual matched spending.
- [ ] Merchant scope binds to the stable merchant identity so a display re-name doesn't break the budget.
- [ ] Existing goals without a scope degrade gracefully.