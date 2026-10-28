# Development

```
app/      Android app (Kotlin, Jetpack Compose, Room)
worker/   Cloudflare Worker: prices, public-wallet balances, release info and APK download
sync/     Cloudflare Worker + Durable Objects: encrypted family sync and backup vault
pwa/      Browser companion (Vite + TypeScript), served by sync/
tools/    make-font.py, which prepares the Modam font file
```

## Building

Needs JDK 21 and an Android SDK (Android Studio or standalone).

```bash
./gradlew assembleFullDebug        # debug APK, full edition
./gradlew installFullDev           # side-by-side dev install with its own data
```

Build types: `debug`, `dev` (`.dev` suffix, installs beside the released app) and `release`
(R8-minified). `versionCode` is `2_000_001_000 + git rev-list --count HEAD`, so every build
installs over the previous one; see
[ADR 0005](docs/adr/0005-in-place-upgrade-signing-and-glance-widgets.md).

### Editions

Two flavours, `full` and `lite`, so every task name carries one (`assembleLiteRelease`,
`installFullDev`). `assembleRelease` builds both.

- `full` keeps the package `com.doxigo.muchtoman`, so older installs upgrade in place.
- `lite` (`com.doxigo.muchtoman.lite`) is only the دارایی tab: holdings and what they are worth.
  It can be installed beside `full`.

The only difference is `BuildConfig.LITE`, read in one place (`tabs` in `TabBar.kt`). Nothing
else should branch on the edition, so that every parser and bank fix lands in both APKs.

### Pointing at local Workers

Both builds use the deployed Workers by default. To use local ones:

```bash
cd worker && npx wrangler dev --port 8787
./gradlew installFullDebug -Pmuchtoman.ratesUrl=http://10.0.2.2:8787/rates
```

`10.0.2.2` reaches the host from the emulator. On a phone use `adb reverse tcp:8787 tcp:8787`
and `http://localhost:8787/rates`. `-Pmuchtoman.syncUrl` does the same for the sync Worker.
Cleartext is allowed only for these two debug hosts.

## Tests

```bash
./gradlew testFullDebugUnitTest testLiteDebugUnitTest   # JVM tests, no device
cd worker && npm ci && npm run check
cd pwa    && npm ci && npm run check   # includes the shared SMS corpus check
cd sync   && npm ci && npm run check   # real Durable Objects under workerd
```

The golden SMS corpus is `app/src/test/resources/sms/*.json`: real bank messages with the
reading each must produce. Both the Kotlin and the TypeScript parser read it, so they cannot
drift on shared cases. CI ([check.yml](.github/workflows/check.yml)) runs all of the above plus
lint on every push and pull request.

## Signing

Release builds are signed only when a gitignored `keystore.properties` exists in the repo root:

```properties
storeFile=muchtoman-release.jks
storePassword=...
keyAlias=muchtoman
keyPassword=...
```

Without it the release build is left unsigned; it never falls back to the debug key. When the
file is present, debug builds use the same key so they can replace an installed release.
**Back up the keystore**: without it no future build can install over an existing one.

Create a keystore once with:

```bash
keytool -genkeypair -v -keystore muchtoman-release.jks -alias muchtoman \
  -keyalg RSA -keysize 4096 -validity 10000
```

## Releasing

Push an annotated `vX.Y.Z` tag. [release.yml](.github/workflows/release.yml) then:

1. runs the unit tests and lint and builds both release APKs with a throwaway key;
2. installs both on an emulator and fails if either crashes or never reaches the foreground;
3. rebuilds with the real key from repo secrets and publishes a GitHub Release with
   `muchtoman-vX.Y.Z.apk`, `muchtoman-lite-vX.Y.Z.apk` and their R8 `mapping.txt` files.

```bash
git tag -a v1.1.0 -m 'یک خط فارسی برای هر تغییر' && git push origin v1.1.0
```

Write the tag message in Persian: it is copied into the release body between `<!--fa-->`
markers and shown on the in-app update sheet (up to six lines). `versionName` comes from the
tag. Keep `mapping.txt` to retrace stack traces (`retrace mapping.txt trace.txt`).

Required repo secrets: `KEYSTORE_B64` (`base64 -i muchtoman-release.jks | gh secret set KEYSTORE_B64`),
`KEYSTORE_STORE_PASSWORD`, `KEYSTORE_KEY_ALIAS`, `KEYSTORE_KEY_PASSWORD`.

The emulator smoke test exists because 1.0.2 shipped an APK that crashed on start (R8 removed
a constructor WorkManager uses by reflection). Unit tests and lint cannot see release-only
crashes; only running the built APK can.

## Data on the phone

Two Room databases:

| | `durable.db` | `derived.db` |
| --- | --- | --- |
| holds | stored SMS messages and every user decision (categories, notes, corrections) | everything the parser computed |
| migrations | hand-written and tested; destructive fallback forbidden | version bump drops and rebuilds |

Holdings, preferences, hand-entered balances and the rate cache live in the `muchtoman`
SharedPreferences file. Android backup and device transfer include `durable.db` and that
preferences file (see `res/xml/backup_rules.xml`), not `derived.db`.

Money in the ledger is `Long` Rial, never Toman and never `Double`. Toman is a display
transform.

**Shipping a parser fix:** bump `PARSER_VERSION` in `Derived.kt`. The next launch re-derives
every transaction from the stored messages and replays the user's corrections. It does not
read the inbox or need the SMS permission.

## The rates Worker

```bash
cd worker && npm ci && npm run check && npm run deploy
```

Served from `rates.muchtoman.com`. Do not point the app at the `workers.dev` address: that
domain is DNS-filtered in Iran, and the failure is quiet (bundled assets lose their prices,
crypto disappears).

- `GET /rates`: Toman per unit for every asset id, plus the coin catalogue and wallet networks.
- `POST /wallet-balance`: read-only balance of one public address (Bitcoin, Ethereum/ERC-20,
  Solana, TRON/TRC-20, EVM tokens on BSC, Arbitrum, Polygon, Optimism, Avalanche).
- `GET /download`: proxies the latest release APK. The release repository is `REPO` in
  `worker/src/index.ts` (currently `doxigo/muchToman`); it is also where the in-app update
  note comes from.

Sources, each with a fallback because they fail independently: bonbast then tgju for fiat,
gold and coins; tgju pages for silver and سکه پارسیان; bitpin then tetherland for Toman crypto
prices, CoinGecko then Binance for USD. TSETMC rejects Cloudflare, so the phone fetches stock
prices directly, and only when a stock is held or the stock picker is open.

## The sync Worker and PWA

```bash
cd pwa  && npm run build    # sync/ serves pwa/dist as its static assets
cd sync && npm run deploy
```

Served from `sync.muchtoman.com` (custom domain only, for the same reason as above). Records
are encrypted on the device; the Worker stores routing metadata and ciphertext.

## Adding things

- **Crypto:** nothing to do; CoinGecko's top 250 are in the picker.
- **Fiat currency:** one entry in `STATIC_CATALOG` in `Catalog.kt` and one in `BONBAST_MAP` /
  `TGJU_MAP` in `worker/src/index.ts`. Check both sources quote the same unit (bonbast quotes
  JPY and AMD per 10, IQD per 100).
- **Bank:** one entry in `Bank` in `Sms.kt` (name and sender ids) and one in `BankLogo`. Phone
  numbers match on their last ten digits; lettered sender ids match case- and space-insensitively.
  Add corpus cases for its messages.

## Typography

The app is set in Modam, one variable font at `app/src/main/res/font/modam.ttf` (`wght`
200–900, `wdth` 70–100). The foundry's file defaults to ExtraLight Condensed, and Compose
applies variation settings only on API 26+, so `tools/make-font.py` retargets the default
instance to Regular at normal width:

```bash
python3 tools/make-font.py "/path/to/ModamVF.ttf"
```

The PWA uses the same file as WOFF2, generated from the app's copy:

```bash
python3 -c "from fontTools.ttLib import TTFont; f=TTFont('app/src/main/res/font/modam.ttf'); f.flavor='woff2'; f.save('pwa/public/modam.woff2')"
```

> **Licensing.** Modam is a commercial font from fontiran.com. Embedding it in the APK,
> committing it to a repository and serving it as a webfont are separate permissions. Check
> the licence covers each before publishing.

Modam has no `·`, `≈`, `…` or `▲`; UI separators use `•`.

## Implementation notes

- Displayed figures truncate, never round. Spelled-out Persian amounts are never abbreviated.
- Latin tickers are wrapped in Unicode bidi isolates so they do not swap places with numbers.
- `FLAG_SECURE` is set while the app lock is on, so `adb screencap` returns black.
- Home-screen widgets render every line as a bitmap in Modam, because the launcher cannot load
  the APK's fonts. Their layout is picked from the widget's size in dp (`WidgetFaceTest`).
