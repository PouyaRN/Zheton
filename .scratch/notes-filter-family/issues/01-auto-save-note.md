# 01 — Auto-save the transaction note

**What to build:** The note field on a transaction stops needing a «ذخیره یادداشت» button. Typing a note saves automatically: a short debounce (~600 ms after the last keystroke) writes the note decision, a small «ذخیره شد» confirms the write, and any pending edit is flushed when the user leaves the screen — so nothing typed is ever lost. Clearing the field writes a deleted/tombstoned note decision, so the note reads as empty everywhere (timeline subtitle, transaction detail) and an earlier note cannot ghost back. Notes stay device-local: they are not sent to the family ledger.

**Blocked by:** None — can start immediately.

**Status:** done

- [ ] The «ذخیره یادداشت» button is gone from the transaction detail; typing the note requires no button press.
- [ ] Saving happens ~600 ms after typing stops, and a subtle «ذخیره شد» appears when the write lands.
- [ ] Leaving the screen mid-debounce flushes the pending note; nothing is lost.
- [ ] Clearing the note removes it everywhere (no stale text reappears); the durable note is tombstoned, not deleted-and-resurrected.
- [ ] Notes are not pushed to the family ledger (the existing sync payload for notes stays unpopulated).