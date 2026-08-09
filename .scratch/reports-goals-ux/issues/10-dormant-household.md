# 10 — Dormant-household pruning

**What to build:** The unreachable family screen, pending-pairing handling, and the family sync no-op via the existing unlinked guard are removed from the app. The sync store, worker, and PWA remain in the repo unshipped.

**Blocked by:** 01 (the tab swap removes the family route).

**Status:** ready-for-agent

- [ ] The Companion screen and deep-link pairing flow are removed.
- [ ] The sync scheduler path no-ops gracefully.
- [ ] The sync store, worker, and PWA files are left in the repo unchanged.