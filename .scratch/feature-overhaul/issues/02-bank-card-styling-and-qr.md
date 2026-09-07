# 02: Bank Card BIN Matching, 4-Digit Spacing, Dormant Sorting & Instant QR

**What to build:**
Enhance the Bank Card engine to recognize Iranian bank BIN prefixes (first 6 digits) and automatically apply distinct brand color themes (Melli, Mellat, Tejarat, Saman, Pasargad, Blu, Resalat, Sepah, Keshavarzi, etc.). Format all PAN card numbers into standard 4-digit separated blocks. Add an instant QR code sheet to share the card number or IBAN via a tap. Detect dormant accounts (inactive > 45 days) and place them at the end of the carousel with a soft "Dormant" badge.

**Blocked by:** None (can start immediately).

**Status:** completed

- [x] BIN matching recognizes leading 6 digits for top Iranian banks.
- [x] Card PANs are formatted into 4-digit groups everywhere.
- [x] Tap on card QR micro-icon displays instant scannable QR sheet for card/IBAN.
- [x] Inactive cards (>45 days without transactions) are sorted to the end with a dormant indicator.
