# ADR 0005: In-Place Upgrades, Deterministic Monotonic Versioning, and Glance Widget Architecture

## Status
Accepted

## Context
ChandToman operates as an offline-first, privacy-respecting financial suite sideloaded by users and shared within families. In Android 14+ (e.g. Nothing OS 2.x, Google Pixel, Samsung OneUI 6), system package installers enforce strict runtime constraints:
1. **Version Code Monotonicity:** Android OS immediately aborts installation with `INSTALL_FAILED_VERSION_DOWNGRADE` if an incoming APK has `versionCode <= installedVersionCode`. In Android 14, this error is displayed generically as `"App not installed as package appears to be invalid"`.
2. **Signature Lineage & Key Consistency:** Attempting to update an installed application signed with a different key (e.g. switching between CI test keys, debug keys, or developer release keys) triggers `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, similarly masked as an invalid package.
3. **Target SDK Downgrade Prevention:** Android 14 prohibits reducing `targetSdkVersion` below the version declared in the active installation.
4. **RemoteViews Compatibility:** `AppWidgetProvider` layouts (such as `initialLayout` and `previewLayout`) are evaluated at package parse time by `AppWidgetService`. Referencing unsupported View attributes (e.g. `android:paddingHorizontal`, `android:paddingVertical`, or custom `@font/...` references) causes runtime inflation failures that trigger `INSTALL_PARSE_FAILED_MANIFEST_MALFORMED`, aborting package installation.
5. **Widget Custom Typography & Cross-Process Isolation:** Android home screen launchers inflate widget RemoteViews in the launcher process, which cannot resolve APK font assets directly.

## Decisions

### 1. Deterministic Monotonic Versioning Scheme
- In `app/build.gradle.kts`, `versionCode` defaults to:
  `2_000_000_500 + gitRevCount`
- Because the base code (`2,000,000,500`) is strictly higher than previous experimental builds, and `gitRevCount` increases monotonically on every commit, every subsequent build (local or release) is guaranteed to increment cleanly without requiring manual edits or risking downgrade errors.

### 2. Canonical Release Signing
- The local keystore `release.jks` with alias `dakh` and SHA-256 fingerprint `97:E9:E6:73:06:05:E5:12:89:6B:27:E4:6C:14:5F:5D:D8:C8:DA:30:D8:F6:E3:96:10:D6:26:AA:7B:8F:BB:76` is the canonical production signing key.
- Both `release` and `debug` build types automatically attach to `signingConfigs.getByName("release")` whenever `keystore.properties` is present, guaranteeing consistent in-place upgrades.
- APK Signing Scheme v1 (JAR signing) and v2 (APK Signature Scheme v2) are maintained for compatibility across Android 7.0 (API 24) through Android 15 (API 35+).

### 3. Glance Bitmap Typography & High-Density Vector Iconography
- Custom Modam font rendering in Jetpack Glance widgets operates via `GlanceTypographyEngine`:
  - Directly loads TrueType font `R.font.modam` (`modam.ttf`) without intermediate XML wrappers.
  - Dynamically configures OpenType variable font axes on `Paint` (`'wght'` from 400 to 900, `'wdth'` at 90 or 100).
  - Explicitly assigns target display metrics (`bitmap.density = densityDpi`) and applies exact dp bounds (`modifier.width().height()`) to eliminate scaling artifacts on high-DPI launchers.
- Rich vector iconography integrates authentic Iranian bank SVGs (`ic_bank_*`) and currency/crypto markers (`ic_toman_gold`, `ic_network_tron`, `ic_network_bitcoin`) directly into Glance composables using `ImageProvider`.
- Preview layouts (`widget_*_preview.xml`) strictly avoid `@font/...` and modern padding shorthands (`paddingHorizontal`/`paddingVertical`), adhering to RemoteViews-safe standard attributes.

## Consequences
- Users can update their application in-place with 100% data preservation and zero signature mismatch errors.
- Widgets on the home screen render with authentic Modam typography, tabular non-jittering figures, luxury Obsidian surfaces, and bank/crypto vector branding matching the in-app experience.
