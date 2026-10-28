# اندیس — Andis

An offline-first Android app for Persian-speaking households: a ledger built from bank SMS,
and the value of everything you own at Tehran free-market rates, in Toman.

[![check](https://github.com/securityvisions/Andis/actions/workflows/check.yml/badge.svg)](https://github.com/securityvisions/Andis/actions/workflows/check.yml)
[![release](https://github.com/securityvisions/Andis/actions/workflows/release.yml/badge.svg)](https://github.com/securityvisions/Andis/actions/workflows/release.yml)
[![latest release](https://img.shields.io/github/v/release/securityvisions/Andis?sort=semver)](https://github.com/securityvisions/Andis/releases/latest)
![minSdk 24](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84)
[![license](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

<p>
  <img src="docs/screenshots/home-dark.webp" width="160" alt="Home (dark)">
  <img src="docs/screenshots/ledger-dark.webp" width="160" alt="Ledger">
  <img src="docs/screenshots/assets-dark.webp" width="160" alt="Assets">
  <img src="docs/screenshots/accounts-dark.webp" width="160" alt="Accounts">
  <img src="docs/screenshots/report-dark.webp" width="160" alt="Report">
  <img src="docs/screenshots/home-light.webp" width="160" alt="Home (light)">
</p>

<sub>Demo data. Home, Ledger, Assets, Accounts and Report in dark mode, then Home in light mode.</sub>

The interface is Persian and right-to-left, with Persian digits and large type. Large amounts
are written the way people say them ("۴٫۷ میلیارد تومان"), and displayed figures truncate rather
than round, so the number shown is never larger than the real one.

## Features

- **SMS ledger.** Bank SMS are read from the phone's inbox and parsed on the device into a
  transaction ledger with balances per account. Corrections, categories and notes are kept and
  replayed when the parser improves. Manual entries cover cash and anything without an SMS.
- **Assets at live Tehran rates.** Toman, foreign currencies, 18k gold, Iranian coins,
  سکه پارسیان, silver, Tehran Stock Exchange symbols and the top 250 cryptocurrencies, valued at
  free-market rates (the official peg is never used). Public wallet addresses can be tracked
  read-only. Holdings can record purchase lots for cost basis and profit/loss.
- **Budget and daily share.** Set a monthly budget and the Home tab shows how much is safe to
  spend today for the rest of the month, with month-to-date progress.
- **Price alerts.** Per-asset alerts checked in the background, delivered as local notifications.
- **Reports.** Month-over-month spending against income, category shares, and PDF/Excel export.
- **Backup.** Android backup for the durable data, plus an optional encrypted backup vault keyed
  by a 12-word recovery code.
- **Extras.** Home-screen widgets, a quick-add tile, optional app lock (fingerprint or device
  PIN), privacy mode that masks amounts, optional end-to-end encrypted family sync, light and
  dark themes following the system.

### Supported banks

بلو بانک، سامان، رفاه، پاسارگاد، اقتصاد نوین، خاورمیانه، صادرات، رسالت، پارسیان، ملت، ملی،
مسکن، سپه، تجارت. A message is read only if it comes from one of the bank's known senders
(`Bank` in [`Sms.kt`](app/src/main/java/com/doxigo/muchtoman/Sms.kt)); a new sender shows up as
a suggestion you confirm. Bank-app push notifications are not read, only SMS.

### Editions

| APK | Package | Contents |
| --- | --- | --- |
| `muchtoman-vX.Y.Z.apk` | `com.doxigo.muchtoman` | Full app: ledger, budget, reports, assets |
| `muchtoman-lite-vX.Y.Z.apk` | `com.doxigo.muchtoman.lite` | Assets only: what you own and what it is worth |

Both are built from the same source and can be installed side by side.

## Install

Download the APK from [GitHub Releases](https://github.com/securityvisions/Andis/releases).
Requires Android 7.0 (API 24) or newer. A newer full APK installs over an older one and keeps
its data.

## Build from source

Requirements: JDK 21 and the Android SDK.

```bash
./gradlew assembleFullDebug              # app/build/outputs/apk/full/debug/app-full-debug.apk
./gradlew testFullDebugUnitTest          # JVM unit tests
```

Release builds are signed when a `keystore.properties` file (gitignored) exists in the repo
root:

```properties
storeFile=muchtoman-release.jks
storePassword=...
keyAlias=muchtoman
keyPassword=...
```

Without it, `assembleRelease` produces unsigned APKs. See [DEVELOPMENT.md](DEVELOPMENT.md) for
build types, local Workers, the rates sources and adding banks or currencies.

## Release process

Push an annotated `vX.Y.Z` tag (message in Persian; it becomes the in-app update note). The
[release workflow](.github/workflows/release.yml) runs the unit tests and lint, installs and
launches both release APKs on an emulator, then builds them with the release key and publishes a
GitHub Release with both APKs and their R8 mapping files.

Required repository secrets: `KEYSTORE_B64` (base64 of the keystore), `KEYSTORE_STORE_PASSWORD`,
`KEYSTORE_KEY_ALIAS`, `KEYSTORE_KEY_PASSWORD`.

`versionName` comes from the tag. `versionCode` is `2_000_001_000 + git rev-list --count HEAD`,
so each build installs over the previous one.

## Project structure

```
app/      Android app (Kotlin, Jetpack Compose, Room): everything that runs on the phone
pwa/      Browser companion (Vite + TypeScript): family ledger view and Persian finance calculators
sync/     Cloudflare Worker + Durable Objects: end-to-end encrypted family sync and backup vault
worker/   Cloudflare Worker: rates, public-wallet balances, release info and APK download
docs/     ADRs, design canvas, SMS intake notes, agent docs, screenshots
tools/    Font preparation script
```

Design system: [DESIGN.md](DESIGN.md). Domain glossary: [CONTEXT.md](CONTEXT.md).
Changes: [CHANGELOG.md](CHANGELOG.md).

## Privacy

- No account, no login, no analytics.
- SMS are read only after you enable bank messages (`READ_SMS`; there is no SMS receiver). Only
  messages from known bank senders are parsed, on the device. Raw SMS text is never uploaded.
- The ledger, holdings and settings stay on the phone. Android's own backup includes the durable
  database and preferences, following your device's backup settings.
- Network use: rates and coin icons from the project's Worker; stock prices directly from TSETMC
  only when you hold a stock or open the stock picker; wallet balances only for addresses you
  add (the public address goes to the Worker and a public blockchain API).
- Family sync and the backup vault are optional and encrypted on the device; the servers store
  ciphertext and routing metadata only. Family SMS sharing starts off and shares parsed fields
  (amount, direction, time, bank, merchant, category), not message text.

## License

[MIT](LICENSE). The Modam font in `app/src/main/res/font/` is a commercial font from
fontiran.com and is not covered by the MIT license.
