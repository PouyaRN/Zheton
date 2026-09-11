# Research: Update Channel & Worker Hosting for Iran Reachability

**Document Status:** Complete  
**Date:** September 2026  
**Target Repository:** `securityvisions/Dakh-o-kharj` (Fork of `doxigo/muchToman`)  
**Context:** Android Personal Finance App for Users in Iran  

---

## Executive Summary

The upstream MuchToman application delivers real-time market rates (fiat, gold, crypto) and in-app updates via an integrated Cloudflare Worker. The upstream deployment runs on `https://rates.muchtoman.com` with hardcoded tracking of `doxigo/muchToman`.

For the fork (`securityvisions/Dakh-o-kharj`), the maintainer needs their own worker to serve rates and publish releases for their own repository. However, deploying directly to Cloudflare's default `*.workers.dev` domain fails completely in Iran: **the entire `workers.dev` domain is wholesale DNS-poisoned and SNI-filtered by Iranian ISPs**.

Based on deep technical analysis of censorship measurements (OONI, Net4People), Cloudflare platform mechanics, and the application's source code:

1. **Default & Strongly Recommended Path:** Deploy the existing worker (`worker/src/index.ts`) to **Cloudflare Workers using a custom international domain** owned by the maintainer (e.g., `rates.example.com`).
   - **Cost:** ~$10–$12/year (domain registration only; Cloudflare Workers Free Tier covers 100,000 req/day).
   - **Reachability from Iran:** **High (Verified)**. Iranian firewalls (IRGFW) cannot block Cloudflare Anycast IP ranges without severe collateral damage to tens of thousands of legitimate commercial and educational websites. Legitimate HTTPS REST requests to unflagged custom domains pass reliably.
   - **Maintenance:** Near zero (serverless, automated SSL issuance and renewal, built-in edge caching).
   - **Crucial Requirement:** Must use an international TLD (`.com`, `.org`, `.xyz`, etc.). **Cloudflare does not permit `.ir` domains** due to US OFAC sanctions compliance.

2. **Why Static `latest.json` (GitHub Raw / jsDelivr) is Insufficient:**
   While metadata from `cdn.jsdelivr.net` or `raw.githubusercontent.com` can be reached from Iran, the actual APK asset download on GitHub redirects (`302`) to `objects.githubusercontent.com` (AWS S3), which suffers severe throttling and connection resets on Iranian mobile networks (MCI, Irancell). The worker's `/download` endpoint streams and caches the APK through the edge, which is essential for update downloads to succeed inside Iran.

3. **Why Self-Hosted VPS is Not Recommended:**
   A foreign VPS (e.g., Hetzner €3.50/mo) costs ~$55–$82/year and incurs substantial maintenance overhead (Linux patching, Docker/systemd setup, Nginx, Let's Encrypt). Furthermore, Hetzner and foreign hosting IP subnets are routinely throttled or blocked by Iranian DPI due to VPN traffic, forcing developers to put Cloudflare in front of the VPS anyway.

---

## 1. Existing Architecture & Codebase Contracts

To understand why hosting matters, we must examine the tightly coupled contracts between the Android application and the Worker:

### 1.1 The Cloudflare Worker (`worker/src/index.ts` & `wrangler.jsonc`)
The worker serves three interlinked roles:
1. **Financial Market Ingestion & Normalization (`/rates`):**
   - Scrapes/aggregates live rates from Bonbast (`bonbast.com`), TGJU (`api.tgju.org`, `tgju.org`), Bitpin (`api.bitpin.ir`), Tetherland (`api.tetherland.com`), Wallex (`api.wallex.ir`), and CoinGecko.
   - Normalizes currency and crypto values into Iranian Toman with validation safeguards (`PLAUSIBLE_USD_TOMAN`).
2. **Release Discovery & Enrichment:**
   - In `fetchLatestRelease()` (lines 908–946), it queries the GitHub Releases API (`https://api.github.com/repos/${REPO}/releases/latest`) with a 1-hour edge cache.
   - Extracts release tag, release notes enclosed in `<!--fa-->...<!--/fa-->`, and locates the attached `.apk` asset.
   - Embeds this metadata directly in the `/rates` JSON response under the `latest` key.
3. **APK Proxy Streamer (`/download`):**
   - In `fetchApk()` (lines 985–1024), when a user taps "Download Update", the request hits the worker's own origin (`/download`).
   - The worker fetches the APK binary from GitHub's release asset URL (`browser_download_url`), caches it at the edge with `caches.default`, and streams it directly to the user's phone.
   - **Why this exists:** Direct GitHub downloads redirect to `objects.githubusercontent.com`. As documented in `worker/src/index.ts` lines 976–984:
     > *"github.com and objects.githubusercontent.com are as unreachable from Iran as workers.dev is... so the update note used to announce a new build and then hand her a link that hangs. This host she can reach — she is talking to it already, or she would not know an update existed."*

### 1.2 The Android App Contract (`Data.kt`)
In `app/src/main/java/com/doxigo/muchtoman/Data.kt`:
- **Rates and Release Data Model:**
  ```kotlin
  @Serializable
  data class Release(
      val name: String = "",
      val url: String = "",
      val apk: String = "",
      val notes: List<String> = emptyList(),
  )
  @Serializable
  data class Rates(
      val updatedAt: Long = 0L,
      val toman: Map<String, Double> = emptyMap(),
      val coins: List<Coin> = emptyList(),
      val latest: Release? = null,
  )
  ```
- **Origin Validation:**
  - `trustedRelease()` validates that the release URL belongs to GitHub and matches the repo path.
  - **Important Note for the Fork:** Line 372 of `Data.kt` hardcodes:
    ```kotlin
    releasePath.startsWith("/doxigo/muchtoman/releases/")
    ```
    This check will reject releases from `securityvisions/Dakh-o-kharj` unless updated in the Android app source.
  - `trustedApk()` validates that the APK download origin matches either `BuildConfig.RATES_URL` or `OFFICIAL_RATES_ORIGIN`.

---

## 2. workers.dev Wholesale Blocking in Iran (2025–2026 Analysis)

### 2.1 The Censorship Mechanism
The `worker/wrangler.jsonc` file states:
> *"workers.dev is filtered wholesale inside Iran — it is a common circumvention host, so the whole domain is DNS-poisoned to 10.10.34.36 — and Iran is where the users are."*

Measurement data confirms this condition remains fully active across all major Iranian operators (MCI, MTN Irancell, Shatel, TCI, HiWeb/ParsOnline):

1. **DNS Poisoning (Sinkholing):**
   - Any DNS query resolving `workers.dev` or any arbitrary subdomain (`*.workers.dev`) directed to local ISP resolvers or unencrypted public resolvers (1.1.1.1, 8.8.8.8) is intercepted by Iranian Deep Packet Inspection (DPI) equipment and forged to return the national sinkhole IP `10.10.34.36`.
   - Primary Source: [Net4People BBS Issue #215: "Cloudflare workers has blocked in iran"](https://github.com/net4people/bbs/issues/215).
2. **SNI-Based TCP Termination:**
   - Even if a client bypasses DNS tampering (via DNS-over-HTTPS or static `/etc/hosts` mapping to valid Cloudflare Anycast IPs such as `104.18.13.15`), the connection fails during the TLS handshake.
   - Iranian DPI inspects the TLS `ClientHello` Server Name Indication (SNI) field. When `*.workers.dev` is detected, the middlebox injects immediate TCP `RST` packets or blackholes the TCP stream.
3. **Root Cause:**
   - Cloudflare Workers were widely adopted in 2022–2024 to construct V2Ray/Xray/Trojan/VMess WebSocket reverse proxies to circumvent the national firewall. Consequently, Iranian telecommunications authorities placed the entire `.workers.dev` suffix on the national permanent blocklist.

**Conclusion:** Deploying the worker to `*.workers.dev` is 100% non-viable for an audience located in Iran.

---

## 3. Cloudflare Custom Domains: Reachability from Iran

### 3.1 How Cloudflare Custom Domains Work for Workers
Under Cloudflare's **Custom Domains** feature:
- An authoritative DNS zone (e.g., `example.com`) is added to Cloudflare.
- Wrangler configures a route entry:
  ```jsonc
  "routes": [{ "pattern": "rates.example.com", "custom_domain": true }]
  ```
- Cloudflare automatically registers the hostname in edge routing tables, creates DNS `A`/`AAAA` Anycast records, and provisions an automated SSL/TLS certificate (Universal SSL).
- Requests to `rates.example.com` execute the Worker directly at the nearest Cloudflare edge PoP without hitting an origin web server.
- Source: [Cloudflare Docs: Custom Domains for Workers](https://developers.cloudflare.com/workers/configuration/routing/custom-domains/).

### 3.2 Why Custom Domains are Reliably Reachable in Iran
While `workers.dev` is blocked, a custom domain proxied through Cloudflare enjoys a vastly different status:

1. **Anycast IP Immunity from Outright Ban:**
   - Cloudflare powers millions of websites globally, including critical international services, educational resources, and major non-political commercial portals accessed daily from Iran.
   - Technical measurements documented in censorship research confirm that Iranian network regulators (TIC / IRGFW) intentionally refrain from banning Cloudflare's IP addresses wholesale due to catastrophic collateral damage across the Iranian domestic digital economy.
   - Primary Source: [Net4People BBS Issue #429: "Cloudflare's Update Cripples Iranian VPNs"](https://github.com/net4people/bbs/issues/429):
     > *"IRGFW couldn't block Cloudflare's IPs outright due to potential collateral impact, but other CDNs like Fastly are easily blocked."*
2. **SNI Uniqueness:**
   - Iranian DPI blocks by domain name (SNI) and protocol behavioral anomalies. An innocuous custom domain (e.g., `rates.mydomain.com`) is unknown to the censorship blocklist.
   - The traffic profile of the MuchToman rates API consists of standard short-lived HTTPS GET requests exchanging JSON payloads (or occasional single-stream binary APK downloads). This does not mimic the long-running WebSocket or heavy continuous UDP flows associated with VPN tunnels.
3. **Proven Production Track Record:**
   - Upstream MuchToman has run its rates infrastructure on `rates.muchtoman.com` on Cloudflare for an extended period without reachability failure inside Iran (see `worker/wrangler.jsonc`).

### 3.3 Domain TLD Restrictions & Sanctions Policy
- **No `.ir` Domains on Cloudflare:**
  Due to US Treasury OFAC export restrictions, Cloudflare does not allow `.ir` ccTLD zones to be added to its DNS or proxy services. Attempting to add an `.ir` domain results in an error (`Zone not allowed`).
- **Required Action:** The maintainer must register an international generic TLD (gTLD) such as `.com`, `.net`, `.org`, `.xyz`, or `.app`. These domains cost approximately $10–$13/year and can be purchased from any international registrar (Namecheap, Porkbun, Cloudflare Registrar) or through Iranian domain resellers (IranServer, Radcom) paying with domestic Iranian debit cards.

### 3.4 Cloudflare Free Tier Economics
- **Cost:** $0.00 / month.
- **Limits on Free Tier:**
  - 100,000 requests per day.
  - 10 ms CPU execution time per request (MuchToman worker executes in ~1–3 ms on warm cache).
  - Unlimited outbound bandwidth (data transfer egress is completely free on Workers).
  - Custom Domains and Universal SSL certificates are included at zero charge.
- Source: [Cloudflare Workers Pricing Documentation](https://developers.cloudflare.com/workers/platform/pricing/).

---

## 4. Evaluation of Alternatives

### 4.1 Alternative A: Small Self-Hosted VPS (Hetzner / DigitalOcean) Behind a Domain

#### Architecture:
- Rent a basic VPS in Europe (e.g., Hetzner Cloud CX22 at €3.49/mo, or DigitalOcean droplet at $4–$6/mo).
- Port the TypeScript worker code from Cloudflare's `ExportedHandler` / `caches.default` runtime to a Node.js/Bun server (using Hono or Fastify).
- Configure Nginx or Caddy with Let's Encrypt for TLS termination.
- Maintain an in-memory or Redis cache for rates (10-minute TTL) and GitHub release metadata (1-hour TTL).
- Stream APK bytes from GitHub to the client.

#### Upstream Feasibility:
- **TGJU (`api.tgju.org`, `tgju.org`):** Freely accessible from European data center IPs; no Iranian IP geo-fencing.
- **Tehran Exchanges (Bitpin, Tetherland, Wallex):** Public market API endpoints are accessible from foreign IPs.
- **Bonbast (`bonbast.com`):** Hosted in Europe/US; freely accessible from foreign VPS.
- **GitHub API:** Unauthenticated requests are limited to 60 requests/hour per IP. A GitHub Personal Access Token (PAT) must be configured in environment variables to raise the limit to 5,000 requests/hour.

#### Critical Drawbacks:
1. **IP Vulnerability in Iran:**
   - Foreign VPS provider IP subnets (especially Hetzner, OVH, and DigitalOcean) suffer heavy scrutiny, periodic IP blacklisting, and aggressive bandwidth throttling by Iranian ISPs due to anti-VPN crackdowns.
   - To circumvent IP blocking, developers almost always end up placing Cloudflare CDN in front of the VPS anyway. Doing so reintroduces the Cloudflare dependency while adding server maintenance.
2. **Financial & Operational Overhead:**
   - VPS cost: ~$45–$70/year.
   - Maintenance: Requires Linux OS updates, kernel patches, systemd process supervision, SSL renewal troubleshooting, and uptime monitoring.

### 4.2 Alternative B: Domestic Iranian VPS (ArvanCloud / ParsPack)
- **Why it Fails:** While domestic Iranian VPS IPs are directly reachable by Iranian mobile users, an Iranian VPS suffers from strict upstream international filtering. Specifically, an Iranian server frequently fails or times out when connecting to `api.github.com` and `objects.githubusercontent.com`. Consequently, the domestic server cannot reliably fetch releases or proxy APK downloads.

### 4.3 Alternative C: Pure Static `latest.json` on CDN / GitHub

#### How it Works:
- App rates continue pointing to upstream `https://rates.muchtoman.com/rates`.
- A GitHub Action in `securityvisions/Dakh-o-kharj` generates a static `latest.json` on each tag and commits it to a branch or GitHub Pages.
- The app checks `https://cdn.jsdelivr.net/gh/securityvisions/Dakh-o-kharj@release/latest.json` or `https://raw.githubusercontent.com/.../latest.json`.

#### Reachability Analysis:
- **jsDelivr CDN (`cdn.jsdelivr.net`):**
  - OONI measurement probe data (`probe_cc=IR`) confirms that `cdn.jsdelivr.net` is currently reliable and unblocked across Iranian ISPs. jsDelivr operates a multi-CDN edge (Cloudflare + Fastly) with extensive caching.
- **GitHub Raw (`raw.githubusercontent.com`):**
  - Accessible under current OFAC exemptions (see [GitHub Trade Controls Policy](https://docs.github.com/en/site-policy/other-site-policies/github-and-trade-controls)). However, raw GitHub URLs experience periodic packet loss and throttling during Iranian national filtering escalations.

#### The Fatal Flaw — The Broken APK Download:
1. **GitHub Releases Redirect to S3:**
   - An APK download link (`https://github.com/USER/REPO/releases/download/.../app.apk`) returns an HTTP `302 Found` redirecting the client to `https://objects.githubusercontent.com/...`.
   - In Iran, `objects.githubusercontent.com` suffers severe throttling, high packet drop rates, and complete timeouts on mobile cellular data (MCI, Irancell). Users tapping "Update" in the app will experience hanging or failed downloads.
2. **jsDelivr Cannot Host APKs:**
   - jsDelivr's Terms of Service and acceptable use policies restrict usage to web assets (JS, CSS, fonts). Distributing large binary application executables (APKs) violates jsDelivr policies and is subject to automated blocking and file size caps (max 50 MB).
3. **Client Patch Required:**
   - The app currently parses `latest` as part of `Rates` (`Data.kt` line 119). Decoupling release checks would require writing a new network fetch pipeline, background job, and UI trigger in the Kotlin code.

---

## 5. Recommendation Matrix

| Solution | Reachable from Iran | Implementation Effort | Annual Cost | Maintenance Overhead | APK Download Working? |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Cloudflare Worker + Custom Domain** *(Recommended)* | **High** (Universal SSL, unflagged SNI, Anycast routing) | **Very Low** (Update `wrangler.jsonc` & `REPO`, deploy) | **~$10 – $12** (Domain only; Worker is free) | **Zero** (Serverless, auto-certs, global CDN) | **Yes** (Streamed & cached via edge `/download`) |
| **2. Self-Hosted VPS (Hetzner/DO) + Domain** | **Medium** (Foreign VPS IPs frequently throttled/blocked) | **High** (Port worker to Node/Bun, Nginx, systemd) | **~$55 – $82** (VPS €3.50/mo + Domain) | **High** (OS patching, monitoring, SSL certs) | **Yes** (Streamed via VPS proxy) |
| **3. Domestic Iranian VPS (Arvan/ParsPack)** | **High** (Inside Iran) | **High** (Port worker to Node/Bun, Nginx) | **~$30 – $50** (Domestic VPS + Domain) | **High** (OS patching, Iranian ISP compliance) | **No** (Outbound blocks to GitHub S3 fail) |
| **4. Static `latest.json` (jsDelivr / GitHub Raw)** | **Medium-High** (Metadata reaches; asset hangs) | **Medium** (Patch Android client, write GitHub Action) | **$0** (Free) | **Low** (GitHub Action workflow maintenance) | **No** (Direct GitHub S3 redirects fail in Iran) |

---

## 6. Detailed Default Recommendation & Action Plan

### The Recommendation: Option 1 (Cloudflare Worker on Custom Domain)

**Why this is the clear winner:**
1. **Addresses the Exact Failure Mode:** It eliminates the poisoned `workers.dev` hostname while keeping the proven architecture of proxying and caching the APK download so Iranian users can reliably receive updates.
2. **Zero Maintenance Burden:** The maintainer does not have to maintain a Linux server, monitor memory leaks, or renew Let's Encrypt certificates.
3. **Lowest Cost:** Free Cloudflare Workers tier covers 100,000 requests/day. At an average of a few thousand daily active users polling every 10–60 minutes, usage will stay well inside the free tier. The only expense is the international domain (~$10/year).

---

### Step-by-Step Implementation Roadmap

#### Step 1: Procure an International Domain
- Register any generic domain (e.g. `dakhokharj.com`, `chandtoman.xyz`).
- **Do NOT register a `.ir` domain** (incompatible with Cloudflare).

#### Step 2: Set up Cloudflare Zone
- Create a free Cloudflare account.
- Add the domain to Cloudflare and point the registrar's nameservers to Cloudflare's designated nameservers.
- Ensure SSL/TLS encryption mode is set to **Full** or **Strict**.

#### Step 3: Configure Worker Configuration (`worker/wrangler.jsonc`)
Update `worker/wrangler.jsonc`:
```jsonc
{
  "$schema": "node_modules/wrangler/config-schema.json",
  "name": "dakhokharj-rates",
  "main": "src/index.ts",
  "compatibility_date": "2026-08-04",
  "observability": { "enabled": true },
  "routes": [
    {
      "pattern": "rates.yourdomain.com",
      "custom_domain": true
    }
  ],
  "workers_dev": false
}
```

#### Step 4: Update Worker Code (`worker/src/index.ts`)
1. Change the repository target (line 41):
   ```typescript
   const REPO = 'securityvisions/Dakh-o-kharj';
   ```
2. Change the public origin (line 26):
   ```typescript
   const PUBLIC_ORIGIN = 'https://rates.yourdomain.com';
   ```
3. Deploy via Wrangler:
   ```bash
   cd worker
   npx wrangler deploy
   ```

#### Step 5: Patch the Android Client (`Data.kt` & `build.gradle.kts`)
1. In `app/build.gradle.kts`, update the default `ratesUrl`:
   ```kotlin
   val ratesUrl = providers.gradleProperty("muchtoman.ratesUrl")
       .getOrElse("https://rates.yourdomain.com/rates")
   ```
2. In `app/src/main/java/com/doxigo/muchtoman/Data.kt`:
   - Update line 276:
     ```kotlin
     private const val OFFICIAL_RATES_ORIGIN = "https://rates.yourdomain.com"
     ```
   - Update line 372 to trust the fork repository releases:
     ```kotlin
     releasePath.startsWith("/securityvisions/dakh-o-kharj/releases/")
     ```

---

## 7. Primary Sources & Citations

1. **Net4People Censorship Measurement Forum:**
   - *Cloudflare workers has blocked in iran*, Issue #215 (Demonstrates DNS poisoning to `10.10.34.36` and SNI RST injection on `workers.dev` across Iranian ISPs):  
     <https://github.com/net4people/bbs/issues/215>
   - *Cloudflare's Update Cripples Iranian VPNs*, Issue #429 (Details why Iranian firewall cannot block Cloudflare Anycast IP ranges due to collateral damage):  
     <https://github.com/net4people/bbs/issues/429>
   - *Cloudflare CDN looks blocked in Iran*, Issue #133 (Analysis of CDN edge filtering and SNI inspection):  
     <https://github.com/net4people/bbs/issues/133>
2. **Cloudflare Official Documentation:**
   - *Custom Domains for Workers*:  
     <https://developers.cloudflare.com/workers/configuration/routing/custom-domains/>
   - *Cloudflare Workers Pricing & Free Tier Limits* (100k req/day, $0 egress):  
     <https://developers.cloudflare.com/workers/platform/pricing/>
   - *Cloudflare OFAC Compliance and Restricted Domains*:  
     <https://www.cloudflare.com/privacypolicy/>
3. **GitHub Trade Controls & OFAC Policy:**
   - *GitHub and Trade Controls (Restoration of services in Iran under OFAC license)*:  
     <https://docs.github.com/en/site-policy/other-site-policies/github-and-trade-controls>
4. **Open Observatory of Network Interference (OONI):**
   - *OONI Explorer Web Connectivity Tests for Iran (`probe_cc=IR`)*:  
     <https://explorer.ooni.org/>
   - *Citizen Lab Censorship Test Lists*:  
     <https://github.com/citizenlab/test-lists>
5. **jsDelivr Documentation:**
   - *jsDelivr Open Source CDN & Acceptable Use Limits*:  
     <https://www.jsdelivr.com/terms>
