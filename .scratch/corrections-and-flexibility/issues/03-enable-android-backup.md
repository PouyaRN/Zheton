# 03 — Enable Android backup

**What to build:** Reinstall (uninstall/reinstall) no longer destroys the durable message store, user decisions, notes, categories, and balances — they are restorable through Android's backup mechanism.

**Blocked by:** None — can start immediately.

**Status:** done

- [ ] Android backup is enabled (`allowBackup`) with rules that include the durable database and user decisions
- [ ] Fold caches and volatile state are excluded from backup
- [ ] Verified: a restore path returns the durable data after reinstall