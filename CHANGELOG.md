# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [1.3.0] - 2026-11-18

### Added

- Cycle-aware `GoalKind.CAP` caps and `startsOn` in Android (`Goals.kt`, `Derived.kt`, `MainActivity.kt`) and `cycleWindow.ts` in PWA so monthly budgets align with payday anchors.
- Neobank Dark Mode with WCAG AA contrast and persistent theme toggle in the PWA (`index.css`, `tailwind.config.js`, `App.tsx`).
- Portable AES-256-GCM + PBKDF2 (600k iterations, AAD header, SHA-256) encrypted offline `.zheton` backup/restore in PWA (`backupService.ts`, `BackupModal.tsx`).
- Extended bank SMS parser support for `POL` instant transfer rail (`Channel.POL`), `PAYA`/`SATNA`, explicit fee separation, and purchase refunds (`Sms.kt`, `smsParser.ts`).
- Automated Android Lint gate (`app/lint.xml`, `./gradlew lintDebug`) and R8 minification/resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`) with hardened ProGuard keep rules.
- Modularized `UiBankLogos.kt`, `MarketDrawer.tsx`, and `BackupModal.tsx`, plus 3 new Vitest files and 3 new Cypress E2E specs (`rates-refresh`, `theme-toggle`, `backup-export-import`).

### Changed

- Updated `SITE_ORIGIN`, `canonical`, `robots.txt`, and `sitemap.xml` URLs to `https://pouyarn.github.io/Zheton`.
- Patched Node dependencies (`vite` 7.3.8, `vitest` 4.1.12, `undici` 7.30.1, `sharp` 0.35.5, `source-map-js` 1.2.2) across workspace lockfiles to eliminate Dependabot security advisories.
- Bumped `kotlin` to `2.2.21`, `kotlinx-serialization-json` to `1.8.1`, and `zxing-core` to `3.5.4`.

## [1.2.0] - 2026-11-10

### Added

- Offline SVG icon system (`iconMap.ts`, 14 Android `VectorDrawable`s) with zero external font CDNs and zero Unicode emojis.
- Cryptographically signed live market rates pipeline (`rates-pipeline/`) using ECDSA P-256 (`SHA-256` IEEE-P1363) verified by both Android (`RatesSignatureVerifier`) and PWA (`WebCrypto`).
- Headless Electron Cypress E2E test suite (11 specs) and Android security hardening tests (`SecurityHardeningTest.kt`).

## [1.1.0] - 2026-10-30

### Added

- Monthly budget and daily share: Home shows how much is safe to spend today. (#3)
- Category suggestion in quick add, learned from the ledger history. (#5)
- Per-bank SMS freshness and 30-day in/out flow on the Accounts screen. (#8)
- Purchase lots for holdings, with cost basis, profit/loss and stored daily rate history. (#10)
- Per-asset price alerts, checked in the background and delivered as notifications. (#11)
- Month-over-month report data. (#14)
- Search, category chips, bubble gauge and arbitrage radar on the full rate list.
- SMS ledger with a durable message store and re-derivation on parser updates, manual entries,
  filters and bulk edits, goals, commitments (BNPL, installments, Sayad checks).
- Two editions from one source: `full` and `lite` (assets only), installable side by side.
- Optional end-to-end encrypted family sync and encrypted backup vault (`sync/`), browser
  companion (`pwa/`), PDF/Excel report export, reminders, home-screen widgets and a quick-add
  tile.

### Changed

- New design: the canvas design system, with shared tokens and components (`CanvasKit`), a
  five-tab floating nav, and rebuilt Home (dark and light), Ledger, Assets, Asset detail,
  Accounts, Quick add and Report screens. (#2, #4, #6, #7, #9, #12, #13, #15, #16)
- Product renamed to Andis.
- `versionCode` is now `2_000_001_000 + commit count`, above every APK already shipped.

### Fixed

- Backups now include purchase lots, budget, price alerts and daily rates (backup schema 2). (#17)
- Day change is no longer shown when only cash is priced.
- Readable contrast in every theme palette.

## [1.0.4] - 2026-08-04

### Changed

- The in-app update downloads the APK directly instead of linking to github.com.
- Better Refah Bank detection.

### Fixed

- Release builds hardened; untrusted data is validated.
- Coin letters no longer show through transparent logos while scrolling.

## [1.0.3] - 2026-08-04

### Fixed

- 1.0.2 crashed on start because R8 removed a constructor WorkManager needs.
- Bank inbox is read off the UI thread, one scan at a time.
- Confirming a bank's new sender no longer drops hand-set balances.
- Typed input survives rotation and theme changes.

### Added

- The release workflow launches the APK on an emulator before publishing.

## [1.0.2] - 2026-08-03

### Added

- Tehran Stock Exchange symbols priced from TSETMC.
- Silver, and سکه پارسیان by size.
- Home-screen widget with its own lock, and a daily background snapshot of the total.
- Mellat, Resalat banks; crypto network icons; a `dev` build type.

### Changed

- Rates served from `rates.muchtoman.com`, since `workers.dev` is filtered in Iran.

## [1.0.1] - 2026-08-01

### Added

- Balances from public wallet addresses, and custom labels for holdings.
- Lettered SMS sender ids, more banks and their logos.

### Changed

- Redesigned UI, set in the Modam variable font.

## [1.0.0] - 2026-07-27

Initial release.

[1.3.0]: https://github.com/PouyaRN/Zheton/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/PouyaRN/Zheton/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/PouyaRN/Zheton/compare/v1.0.4...v1.1.0
[1.0.4]: https://github.com/PouyaRN/Zheton/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/PouyaRN/Zheton/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/PouyaRN/Zheton/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/PouyaRN/Zheton/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/PouyaRN/Zheton/releases/tag/v1.0.0
