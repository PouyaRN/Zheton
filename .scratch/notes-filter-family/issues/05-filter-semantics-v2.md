# 05 — Filter semantics v2: negation, ranges, raw regex

**What to build:** Extends the filter sheet so a query can express what ticket 04 cannot: 「اسنپ، اما نه اسنافود و نه اسنپپی». New tokens: `not-contains` (exclude a substring), numeric range tokens (amount min/max), and a date range. A raw-regex view applies a pasted expression to the merchant text for exact shapes a token list cannot write. Tokens still AND-compose; the regex slot is scoped to merchant text only.

**Blocked by:** 04 (builds the sheet and the AND-composed token model it extends).

**Status:** done

- [ ] A filter like «merchant contains اسن، merchant not-contains اسنافود، merchant not-contains اسنپپی» returns only the intended rows.
- [ ] Amount min/max and a date-range token work in the sheet.
- [ ] The raw-regex slot matches against merchant text only, and comports with the token set (AND).
- [ ] The selection-count confirm behavior from 04 still holds with the wider syntax.