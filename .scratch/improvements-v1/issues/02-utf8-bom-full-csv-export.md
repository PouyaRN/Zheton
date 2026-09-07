# 02 — Comprehensive Full-Ledger CSV Export with UTF-8 BOM

**What to build:** Upgrade the CSV export functionality to dump the complete ledger history (`ledger.allEntries` rather than just the visible 300 items), with columns for Jalali Date, Jalali Time, Amount (Toman), Amount (Rial), Direction, Category, Merchant, Bank, and User Note. Prepend `\uFEFF` UTF-8 Byte Order Mark so Microsoft Excel on all platforms renders Persian characters flawlessly without mojibake/garbled text.

**Blocked by:** None (can start immediately).

**Status:** closed

- [x] Export exports `allEntries` (excluding duplicates and hidden rows).
- [x] Output CSV begins with `\uFEFF` UTF-8 BOM.
- [x] Columns include Jalali Date, Time, Toman, Rial, Direction, Category, Merchant, Bank, and Note.
- [x] Notes are populated from `ledger.notes[txn.ref]`.
- [x] Unit test verifies CSV formatting, BOM presence, and Persian text preservation.
