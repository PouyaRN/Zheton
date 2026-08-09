# 05 — Goals: proper cap math

**What to build:** Budget progress respects the full scoping: a category cap counts **the category and all its children** (a «حمل و نقل» cap honours «حمل و نقل/اسنپ»), caps **recur monthly** at the Jalali month start (savings stay one-shot), progress counts against the **full store** rather than the newest-300 window, and a **pace estimate** ("at this rate you'll pass the cap on the ۲۵ام") appears once usage passes ~80%.

**Blocked by:** 04 (scope persisted).

**Status:** ready-for-agent

- [ ] A category cap counts transactions in the category and its children.
- [ ] A merchant cap counts only that merchant identity.
- [ ] Caps reset at Jalali month start; savings goals use their one-shot window.
- [ ] Progress reads the whole stored ledger.
- [ ] A pace estimate renders within ~80% of the cap.