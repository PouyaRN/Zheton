# ژتون — Zheton

An offline-first Android personal finance assistant for Persian-speaking households: an intelligent ledger built automatically from bank SMS, real-time portfolio valuation at Tehran free-market rates, and Iranian financial calculators — all in Toman.

[![check](https://github.com/PouyaRN/Zheton/actions/workflows/check.yml/badge.svg)](https://github.com/PouyaRN/Zheton/actions/workflows/check.yml)
[![release](https://github.com/PouyaRN/Zheton/actions/workflows/release.yml/badge.svg)](https://github.com/PouyaRN/Zheton/actions/workflows/release.yml)
[![latest release](https://img.shields.io/github/v/release/PouyaRN/Zheton?sort=semver)](https://github.com/PouyaRN/Zheton/releases/latest)
![minSdk 24](https://img.shields.io/badge/Android-7.0%2B%20(API%2024)-3DDC84)
[![license](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

<p align="center">
  <img src="docs/screenshots/home-dark.webp" width="160" alt="Home (Dark)">
  <img src="docs/screenshots/ledger-dark.webp" width="160" alt="Ledger">
  <img src="docs/screenshots/assets-dark.webp" width="160" alt="Assets">
  <img src="docs/screenshots/accounts-dark.webp" width="160" alt="Accounts">
  <img src="docs/screenshots/report-dark.webp" width="160" alt="Report">
  <img src="docs/screenshots/home-light.webp" width="160" alt="Home (Light)">
</p>

<p align="center">
  <sub>Lithe Vitality UI: Home, Ledger, Assets, Accounts, and Report in dark mode, plus Home in light mode.</sub>
</p>

The interface is native Persian and right-to-left, with Persian typography and natural numeral formatting. Large amounts are displayed the way Iranians say them (e.g. «۴٫۷ میلیارد تومان»), and figures truncate rather than round so numbers are always honest and exact.

---

## 🌟 Key Features

- **📱 Smart On-Device SMS Ledger:** Reads incoming and existing bank SMS from your phone inbox and parses them completely on-device. Extracts amounts, account balance, direction (deposit/withdrawal), and counterparty. Corrections, custom categories, and notes are preserved across updates.
- **🚀 Clean Starter Onboarding:** Brand-new onboarding greeting and intro flow without intrusive mock/fake data or placeholder commitments. Every user starts with their own fresh, personalized ledger.
- **💎 Assets & Live Tehran Rates:** 
  - 18k Gold, Mesghal, and Silver
  - Iranian Coins: Emami, Bahar Azadi, Half, Quarter, and Parsian coins
  - Currencies: US Dollar (USD), Euro (EUR), UAE Dirham (AED) at open-market rates
  - Tehran Stock Exchange (TSETMC) symbols and top cryptocurrencies
  - Coin Bubble Radar: live calculation of coin intrinsic gold value vs. market price
- **💳 Shetab Card Deck & Commitments:**
  - Interactive Shetab bank cards with Luhn verification and one-touch IBAN (شبا) copying
  - Track loans, installments, BNPL services (SnappPay, DigiPay, Tara), and Sayadi checks with smart reminder alerts
- **📊 50/30/20 Budgeting & Daily Safe Spend:**
  - `SafeSpendEngine` calculates how much you can safely spend today, weighting weekends (Thursday/Friday) and subtracting monthly obligations
  - Category budget caps with 50% Needs, 30% Wants, 20% Savings breakdown
- **🧮 7 Specialized Iranian Economic Calculators (PWA Companion):**
  1. Gold Coin Bubble Calculator
  2. Bank Loan & Installment Calculator (PMT formula with Iranian banking interest)
  3. Toman Inflation & Purchasing Power (Official CBI CPI index from 1380 to 1405)
  4. Real-time Dowry Valuation (قوه قضائیه & Gold equivalent)
  5. Delayed Payment Penalty (ماده ۵۲۲ آیین دادرسی مدنی)
  6. Salary Purchasing Power Tracker
  7. Compound Daily Habit Cost Analyzer
- **🔒 Privacy & Local-First:** No accounts, no phone numbers, no tracking, no ads. All transaction data stays on your device.

---

### 🏦 Supported Iranian Banks

بلو بانک (Blu Bank)، سامان (Saman)، رفاه (Refah)، پاسارگاد (Pasargad)، اقتصاد نوین (EN Bank)، خاورمیانه (Middle East)، صادرات (Saderat)، رسالت (Resalat)، پارسیان (Parsian)، ملت (Mellat)، ملی (Melli)، مسکن (Maskan)، سپه (Sepah)، تجارت (Tejarat)، شهر (Shahr) و تمامی بانک‌های عضو شتاب.

> [!NOTE]
> پیامک‌ها تنها از فرستنده‌های رسمی و تأییدشده بانکی خوانده می‌شوند. پیامک‌های متفرقه یا اعلان‌های برنامه‌ها خوانده نمی‌شوند و هیچ پیامی هرگز به اینترنت ارسال نمی‌گردد.

---

## 📥 Download & Install

Download the latest signed release APK from [GitHub Releases](https://github.com/PouyaRN/Zheton/releases):

- **[دانلود مستقیم zheton-v1.0.0.apk](https://github.com/PouyaRN/Zheton/releases/download/v1.0.0/zheton-v1.0.0.apk)** (~12.9 MB)
- **Minimum Requirement:** Android 7.0 (API 24) or higher.

---

## 🛠️ Build from Source

Requirements: JDK 21 and the Android SDK (API 36).

```bash
# Clone the repository
git clone https://github.com/PouyaRN/Zheton.git
cd Zheton

# Run unit tests
./gradlew testDebugUnitTest

# Assemble Release APK
./gradlew assembleRelease
```

The compiled APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`

---

## 📂 Project Architecture

```text
Zheton/
├── app/       # Native Android Application (Kotlin, Jetpack Compose, Room, KSP, Coroutines)
├── pwa/       # Web Companion & Calculators (React 18, Vite, TypeScript, Tailwind CSS)
├── sync/      # Encrypted Family Sync Service (Cloudflare Worker + Durable Objects)
├── worker/    # Live Rates & TSETMC API Engine (Cloudflare Worker)
└── docs/      # Design canvas, ADRs, and App Screenshots
```

---

## 🛡️ Privacy & Security

- **No Remote Storage of Personal Data:** All financial ledger entries, bank accounts, and notes stay strictly on your phone.
- **Local-Only SMS Parsing:** The `READ_SMS` permission is used strictly to read bank notifications on the device. Raw SMS text is never transmitted over the network.
- **End-to-End Encryption (E2EE):** Optional family sync and backup vault use device-level `AES-GCM` encryption. The relay server never sees cleartext financial information.
- **Open Source:** Full code transparency under the MIT license.

---

## 📄 License

Distributed under the [MIT](LICENSE) License.
