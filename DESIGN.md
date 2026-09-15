# Chandtoman (چندتومان) Design System (DESIGN.md)

## Overview

Chandtoman is a high-clarity, editorial financial design system built for live tracking of currencies, gold, coins, and crypto. It merges the tactile paper warmth of Davvvat with the modular bento logic of Dastyar, optimized for ultra-fast visual scanning of numbers and market trends. It is cross-platform ready (Jetpack Compose / Kotlin, Flutter, Web).

---

## Colors

### Light Palette (Day Market)
- **Primary Accent** (`0xFFF26430` / `#F26430`): Primary actions, active market filters
- **Gold Accent** (`0xFFE5A93C` / `#E5A93C`): Gold, coin, and bullion highlights
- **Canvas Base** (`0xFFF6F1E9` / `#F6F1E9`): Warm cream background reducing eye fatigue
- **Surface Pure** (`0xFFFFFFFF` / `#FFFFFF`): Price cards, bento blocks, bottom sheets
- **Surface Muted** (`0xFFEDE7DC` / `#EDE7DC`): Search bars, toggle containers, inactive pills
- **Text Primary** (`0xFF18130E` / `#18130E`): Big price figures, primary headers
- **Text Secondary** (`0xFF4E4A43` / `#4E4A43`): Currency symbols, subtitle labels
- **Text Muted** (`0xFF9E9B93` / `#9E9B93`): Last updated timestamps, source names
- **Border Subtle** (`0x1418130E` / `#18130E14`): 1dp hairline separators

### Dark Palette (Night Trading)
- **Canvas Dark** (`0xFF121624` / `#121624`): Deep midnight blue canvas
- **Surface Dark** (`0xFF1A2035` / `#1A2035`): Elevated price cards
- **Surface Dark Variant** (`0xFF232B45` / `#232B45`): Input containers, search fields
- **Text Light** (`0xFFE2E5F0` / `#E2E5F0`): Primary price figures
- **Text Light Muted** (`0xFF8892B0` / `#8892B0`): Secondary market labels
- **Border Dark Subtle** (`0x1A94A3FF` / `#94A3FF1A`): 1dp dark card borders

### Market Indicators (Strict Semantics)
- **Market Up** (`0xFF0CBC8D` / `#0CBC8D`): Positive profit delta, green sparkline
- **Market Up Tint** (`0x1A0CBC8D` / `#0CBC8D1A`): 10% opacity pill background for profit
- **Market Down** (`0xFFDC2626` / `#DC2626`): Negative loss delta, red sparkline
- **Market Down Tint** (`0x1ADC2626` / `#DC26261A`): 10% opacity pill background for loss
- **Market Stable** (`0xFF6B7280` / `#6B7280`): Zero change neutral badge

---

## Typography

- **Primary Iranian Font**: `Estedad` (with baked-in Persian digits `FaNum`)
- **Number Fallback / Western**: Space Mono or Roboto Mono

- **Price Hero**: Estedad 32sp, Weight: 800 (Extrabold), LineHeight: 38sp
- **Price Card Value**: Estedad 22sp, Weight: 800 (Extrabold), LineHeight: 28sp
- **Section Heading**: Estedad 18sp, Weight: 700 (Bold), LineHeight: 24sp
- **Body Label**: Estedad 14sp, Weight: 500 (Medium), LineHeight: 20sp
- **Delta & Timestamp**: Estedad 12sp, Weight: 700 (Bold), LineHeight: 16sp

---

## Spacing

Base unit: 4dp
- **sp-1**: 4dp
- **sp-2**: 8dp
- **sp-3**: 12dp
- **sp-4**: 16dp
- **sp-5**: 24dp
- **sp-6**: 32dp

---

## Border Radius

- **radius-pill** (999dp): Delta pills (`+۲.۴٪`), search capsule, category chips
- **radius-sm** (12dp): Action buttons, calculator inputs
- **radius-md** (18dp): Standard asset price cards (dollar, coin, tether)
- **radius-lg** (28dp): Hero featured asset (e.g. Gold ounce / Dollar banner)
- **radius-xl** (36dp): Bento summary containers, bottom drawer sheets

---

## Elevation & Shadows

- **shadow-flat**: 0dp elevation, relying exclusively on 1dp `BorderSubtle`.
- **shadow-card**: `0dp 2dp 8dp rgba(24, 19, 14, 0.06)` — light mode cards.
- **shadow-floating**: `0dp 4dp 16dp rgba(242, 100, 48, 0.18)` — converter floating button.

---

## Components

### 1. Price Bento Card (Core Unit)
- **Background**: `#FFFFFF` (Dark: `#1A2035`), Shape: `RoundedCornerShape(18.dp)`.
- **Border**: 1dp `BorderSubtle`.
- **Top Row**: Asset icon (36dp rounded squircle), Asset Title (16sp Bold), Market Code (12sp Muted).
- **Middle**: Sparkline mini-trend chart (height 32dp, stroke 2dp, no axes).
- **Bottom Row**: Large formatted price (22sp Extrabold) aligned with Delta Pill (`+۱.۸٪` in 999dp pill with matching 10% tinted background).

### 2. Market Delta Pill
- **Shape**: `RoundedCornerShape(999.dp)`.
- **Padding**: 3dp vertical, 8dp horizontal.
- **Positive**: Text `#0CBC8D`, Fill `0x1A0CBC8D`, arrow-up icon (10dp).
- **Negative**: Text `#DC2626`, Fill `0x1ADC2626`, arrow-down icon (10dp).

### 3. Category Filter Tabs
- **Height**: 40dp capsule row.
- **Active Tab**: Background `#F26430`, Text `#FFFFFF`, shape 999dp.
- **Inactive Tab**: Background transparent or `#EDE7DC`, Text `#4E4A43`.

### 4. Fast Currency Calculator Input
- **Background**: `#EDE7DC` (Dark: `#232B45`), Shape: 14dp radius.
- **Input Text**: 20sp Bold, right-to-left numeral entry.
- **Suffix Badge**: 999dp pill showing selected source currency (e.g. USD / IRT).

---

## Do's and Don'ts

1. **Do** use Persian numerals exclusively (`۱۲۳,۴۵۰`) via `FaNum` font variants.
2. **Don't** use sterile pure white (`#FFFFFF`) for screen canvases; always use `#F6F1E9`.
3. **Do** tint delta pills with 10% opacity rather than solid loud fills to keep numbers readable.
4. **Don't** use sharp 0dp edges anywhere; financial tools must feel approachable and calm.
5. **Do** keep timestamps visible on every card (e.g. `۵ دقیقه پیش`) in `TextMuted` 12sp.
