# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased] - 1.1.0

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

[Unreleased]: https://github.com/securityvisions/Andis/compare/v1.0.4...HEAD
[1.0.4]: https://github.com/securityvisions/Andis/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/securityvisions/Andis/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/securityvisions/Andis/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/securityvisions/Andis/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/securityvisions/Andis/releases/tag/v1.0.0
