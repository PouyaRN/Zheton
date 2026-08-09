# 02 — Auto-save the merchant display name

**What to build:** The merchant name field on a transaction joins the auto-save model from ticket 01 — same debounce, same «ذخیره شد» confirmation, same flush-on-leave, no «ذخیره نام» button. The edit keeps the existing merchant-alias semantics: it renames the display name for **every** transaction of that merchant (keyed by merchant norm), past and future, so fixing a merchant name once fixes the whole merchant. A small hint appears under the field («برای همهٔ تراکنشهای همین فروشگاه اعمال میشود») so the scope is not a surprise. Clearing the field unpins the alias (blank display) and the name falls back to the parser's reading.

**Blocked by:** 01 (builds on the same debounce/flush/confirm helper).

**Status:** done

- [ ] «ذخیره نام» button is gone; the merchant field autosaves on the same debounce.
- [ ] Renaming a merchant on one transaction renames every transaction of that merchant in the timeline.
- [ ] The scope hint is visible under the field.
- [ ] Clearing the field unpins the alias and the parser's original name returns.
- [ ] The autosave writes the same durable merchant-alias record the old button wrote.