# 02 — Stop SMS schema data wipes

**What to build:** Updating the app no longer deletes bank account balances and SMS-scan state. Today the build-internal schema counter's change dumps the folded balance sheet, seen-SMS hash, and scan watermark from persisted preferences — silently losing figures a fresh scan cannot rebuild.

**Blocked by:** None — can start immediately.

**Status:** done

- [ ] The SMS-schema change no longer clears account balances, seen-SMS hashes, or the scan watermark
- [ ] Hand-typed balance anchors are stored in a place that survives any fold rebuild
- [ ] Verified: an upgrade over existing data keeps every balance and user decision