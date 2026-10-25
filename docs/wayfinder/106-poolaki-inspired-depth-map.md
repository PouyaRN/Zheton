# Wayfinder map #106 — Poolaki-Inspired Depth

> Local mirror of the GitHub map issue (canonical: https://github.com/securityvisions/Dakh-o-kharj/issues/106).
> Tracker: `securityvisions/Dakh-o-kharj` (private), GitHub Issues.

## Destination

هفت قابلیت منتخب از تحلیل رقیب (پولکی) تصمیم‌گذاری و به اسپک اجرایی قابل‌تحویل تبدیل شوند:
**SMS Inbox Picker، نوتیفیکیشن/سررسید، پوشش ۲۴–۳۵ بانک، آنبوردینگ+قیف مجوز،
ابزارهای ماشین‌حساب PWA، گزارش PDF/Excel، و شبکهٔ ایمنی داده (بکاپ رمزدار + vault اختیاری)**
— بدون اکانت، بدون تلهمتری، بدون کپی کد رقیب.

## Notes

- Domain: Kotlin/Compose Android app + Cloudflare Worker + PWA.
- Competitor analysis source (local, private): `/home/parsavisions/Projects/poolaki-re/docs/`.
- Knowledge, not code: Poolaki SMS patterns / `.plk` schema are re-implemented independently.
- Principles: no account, no telemetry, Room migrations, IRR storage → Toman display, undo for batch actions.
- Skills: `grilling`, `domain-modeling`, `design-taste-frontend`, `prototype`.

## Tickets (children) — live on GitHub

- [#107](https://github.com/securityvisions/Dakh-o-kharj/issues/107) — Bank Coverage research
- [#108](https://github.com/securityvisions/Dakh-o-kharj/issues/108) — Data Safety Net
- [#109](https://github.com/securityvisions/Dakh-o-kharj/issues/109) — SMS Inbox Picker
- [#110](https://github.com/securityvisions/Dakh-o-kharj/issues/110) — Reminders & Due Dates
- [#111](https://github.com/securityvisions/Dakh-o-kharj/issues/111) — Onboarding & Permission (blocked by #109)
- [#112](https://github.com/securityvisions/Dakh-o-kharj/issues/112) — Poolaki Import (blocked by #108)
- [#113](https://github.com/securityvisions/Dakh-o-kharj/issues/113) — PDF/Excel Reports
- [#114](https://github.com/securityvisions/Dakh-o-kharj/issues/114) — PWA Calculators
- [#115](https://github.com/securityvisions/Dakh-o-kharj/issues/115) — SMS Evidence Intake (task)
- [#116](https://github.com/securityvisions/Dakh-o-kharj/issues/116) — Non-Transaction Guard (grilling)

## Ticket table

| # | Ticket | Type | Frontier |
|---|--------|------|----------|
| 107 | Bank Coverage research | research | **closed** |
| 108–116 | implemented | mixed | **implemented** @ `abf6d3f` |
| 117 | Ship wave: merge, vault deploy, device QA | task | frontier |
| 108 | Data Safety Net: encrypted backup + E2E vault + recovery code | grilling | yes |
| 109 | SMS Inbox Picker: load all SMS, user chooses | prototype | yes |
| 110 | Reminders & Due Dates: commitment/cheque notifications | grilling | yes |
| 111 | Onboarding & Permission Funnel | prototype | blocked by #109 |
| 112 | Poolaki Import: `.plk` v9 → ledger | grilling | blocked by #108 |
| 113 | Reports: PDF & Excel export | grilling | yes |
| 114 | PWA Calculators on live rates | prototype | yes |

## Decisions so far

- **Wave implemented** on `feat/poolaki-inspired-depth` @ `abf6d3f`: backup/vault (#108), SMS picker (#109), reminders (#110), PDF/Excel (#113), PWA calculators (#114), notice guard (#116); 931 JVM + 57 PWA tests green, debug APK built. Release/QA follow-up: #117.
- **#107 Bank Coverage** (closed): شکاف دقیقاً ۱۰ بانک؛ پولکی سرشماره ندارد (۲۴ پارسر، thread انتخابی کاربر)؛ موتور چندتومان قوی‌تر (balance anchoring)؛ ریسک اصلی FP روی senderهای موجود → #115 (شواهد) + #116 (گارد) قبل از پارسرهای جدید. سند: `poolaki-re/docs/BANK_COVERAGE.md`.

## Not yet specified (fog)

- shared family reminders / cross-member notification
- Glance widget for upcoming installments/cheques
- multi-language onboarding + final Persian copy / accessibility
- funnel measurement without telemetry (local counters)
- shared family backup vs per-person backup, conflict policy with current sync

## Out of scope

- copying Poolaki code/parsers, further RE of its cloud
- accounts/login/telemetry/ads
- Play/Bazaar distribution + monetization (separate effort)
- native iOS / Compose Multiplatform

### Research artifact (final)

`/home/parsavisions/Projects/poolaki-re/docs/BANK_COVERAGE.md` — **549 lines**, every claim cited.
Key findings: Poolaki ships no sender numbers (24 parsers, user-picked threads); chandtoman reads
**14** banks/36 headers (README says 11); one-way gap = 10 banks; **7 of 10 need no parser work**
(sender + fixture suffice); biggest risk is a false positive on senders we already read —
`یادآوری بازپرداخت … مبلغ 4,000,000 ریال` is parsed as a **withdrawal** today (`پرداخت` ⊂
`بازپرداخت`) and `خوش آمدید … موجودی …` would anchor a balance → #116 carries the failing fixture.
