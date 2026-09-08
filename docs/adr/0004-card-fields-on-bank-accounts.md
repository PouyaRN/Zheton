# ADR 0004: Card fields attach to bank accounts; CVV2 is never stored

Date: 2026-09-08
Status: Accepted

## Context

The 3D card on Home was pure decoration over an SMS-tracked bank account. The user asked for
"comprehensive card customization": card number, expiration, nickname, visual theme, copy
actions. The design question was whether a "card" is its own record or a face over the account.

## Decision

1. **A card is the presentable face of a bank account, never a standalone record.** New fields
   (`expiry`, `themePreset`, custom gradient stops) live on `BankAccount` beside the existing
   `nickname` and `fullPan`. One account = one card. A card with no SMS-tracked account behind
   it cannot exist.
2. **CVV2 is never stored, shown, or asked for.** The local store is plain-text JSON in shared
   prefs; storing CVV multiplies the blast radius of any device compromise for a field the app
   never needs. PAN and expiry render masked; full PAN reveals only in the editor or with
   privacy mode off, behind the existing two-tap reveal.
3. **Full PAN is optional and user-entered.** The SMS pipeline only ever sees the bank's own
   masked form; the full number arrives only if she types it, for copy-to-clipboard use.

## Consequences

- No duplicate-account model to reconcile; deleting an account removes its card with it.
- Anyone with device access can read the stored PAN in plaintext; accepted because the whole
  ledger already lives that way and privacy mode + app lock gate the UI paths.
- Adding a standalone card later (credit card, no SMS) means revisiting this ADR, not
  stretching this one.
