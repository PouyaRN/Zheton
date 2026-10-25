# SMS intake — the ten banks chandtoman cannot read yet

Status: **open intake**, no code. This table is the prerequisite for adding any new bank parser
(wayfinder ticket #115, research #107). Its rule is simple: **a bank is added when we have a real
message body and its sender, not when a generic parser is believed to handle it.**

Why this exists: the app decides a message is a bank message by its **sender**, not by its words
(`Sms.kt`, `Bank.numbers`; README §"Banks it reads"). A parser with no verified sender can never
fire. The competitor analysis (`poolaki-re/docs/BANK_COVERAGE.md`, §1.4) shows the same wall from
the other side: Poolaki's 24 parsers ship **no** sender numbers — its users pick a thread and the
address is stored on the phone — so nothing about the missing banks' senders can be learned from
it. Only field evidence closes this.

## The gap

chandtoman ships **14** banks (`Bank` enum in `Sms.kt`), a subset of Poolaki's 24. Ten are missing;
`AYANDEH` (بانک آینده) is a deliberate, correct refusal (its balance-shaped notices are not
transactions) and is **not** in this intake.

| # | Bank id | نام | Wave | Sender (verified on 2 phones) | واریز | برداشت/خرید | فقط مانده | OTP/تبلیغ | Corpus row |
|---|---------|-----|------|-------------------------------|-------|-------------|-----------|-----------|------------|
| 1 | `KESHAVARZI` | بانک کشاورزی | 1 (HIGH) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| 2 | `SHAHR` | بانک شهر | 1 (HIGH) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| 3 | `MEHR_IRANIAN` | بانک قرض الحسنه مهر ایران | 1 (HIGH) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| 4 | `SINA` | بانک سینا | 2 (MED) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| 5 | `KARAFARIN` | بانک کارآفرین | 2 (MED) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| 6 | `MELLAL` | اعتباری ملل | 2 (MED) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| 7 | `TOSETAVON` | بانک توسعه تعاون | 3 (LOW) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| 8 | `GARDESHGARI` | بانک گردشگری | 3 (LOW) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| 9 | `DAY` | بانک دی | 3 (LOW) | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |

Wave order is **inferred** from bank popularity; it is not measured. The only evidence-based way
to reorder it is the app's own stranger-sender suggestion card (`Sms.kt`, `StrangeSender`): it
already fires on exactly these messages. A local count of suggestion cards per sender — kept on
the phone, never uploaded — turns this ranking from opinion into data.

## What to capture, per row

For each bank, ideally from a phone that actually banks there:

1. **Sender**, exactly as Android reports it — both `+98…` and `0…` forms if the bank sends both,
   and the lettered header if it uses one (Refah/Pasargad style). A sender is trusted only after
   it is confirmed from **two separate phones or two message sources**; one unverified shortcode
   is how a marketing SMS becomes a withdrawal.
2. Five bodies, verbatim:
   - a **deposit** (واریز/بستانکار/نشست),
   - a **card purchase or withdrawal** (خرید/برداشت),
   - a **balance-only** notice (مانده/موجودی) — this is the dangerous one, see below,
   - an **OTP / authorization** message (رمز پویا، کد تایید),
   - one **marketing / welcome** message (خوش آمدید، اعتبار تا).
3. The reading we expect: amount, direction (`income` / `expense` / `null`), balance
   (`null` unless the message really states it), printed date/time, card mask — the same keys
   `app/src/test/resources/sms/*.json` already uses (schema documented at the top of
   `SmsCorpusTest.kt`).
4. Only then: a `Bank` enum entry, the `BankLogo`/widget case (the compiler forces it), a
   `CARD_BIN_DIRECTORY` row, one `<bank>.json` corpus file, and the `MoneyTest` sender/negative
   cases. If the body prints no direction word **and** no `مانده`, a per-bank `SIGN_ONLY` mode is
   the only parser change allowed (never global).

## Privacy

Real bodies are checked in **only** after stripping beneficiary names and full card numbers to
the mask the message already prints (the corpus may keep masked PANs, never a full one). Amounts,
dates and senders are fine: they are the test. Nothing here is uploaded; the intake is a JSON
corpus in the repository, parsed only on-device.

## Definition of done for a row

- [ ] sender verified on two sources
- [ ] five message kinds captured (or explicitly marked "bank does not send this kind")
- [ ] expected reading recorded as a corpus case, **including at least one `expect: null`**
      negative (marketing/OTP) so a future parser cannot silently widen
- [ ] `MoneyTest` registry + negative sender case
- [ ] logo/widget case compiles (drawable exists? ملل has no `ic_bank_mellal` yet — report it)

New rows are added bottom-up; the table is the single place to see what blocks which bank.
