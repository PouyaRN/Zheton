# Design system

Andis uses one design language, the **canvas**: a black (or near-white) ground, one saffron
light behind the top of each page, large Persian figures, 26dp cards with a 1px edge, and a
floating pill nav. The approved artboards are in [`docs/design/canvas/project`](docs/design/canvas/project)
(`Main.dc.html` for night, `HomeLight.dc.html` for day, one file per screen). Code that
implements a screen cites its artboard.

## Colour tokens

Defined in [`Theme.kt`](app/src/main/java/com/doxigo/muchtoman/Theme.kt) as `CanvasTokens`,
read through `CanvasTheme.tokens`. Material's `ColorScheme` is mapped from these values, but
canvas components read the tokens directly, because Material has no role for "card edge",
"nav glass" or "saffron as a fill but ochre as text".

| Role | Dark (`CanvasDark`) | Light (`CanvasLight`) | Use |
| --- | --- | --- | --- |
| `ground` | `#000000` | `#F6F6F7` | Page background |
| `card` | `#0F0F11` | `#FFFFFF` | Cards |
| `cardBorder` | white 7% | black 6% | 1px card edge |
| `ink` | `#F5F5F4` | `#0B0B0C` | Primary text and figures |
| `inkSecondary` | `#B5B5B8` | `#3E3E44` | Captions |
| `muted` | `#8E8E93` | `#5E5E66` | Dates, units, idle nav icons |
| `accent` | `#F5B840` | `#E9A425` | Saffron fill: progress, today's dot, glow |
| `accentText` | `#F5B840` | `#8A5A00` | Saffron as text: links, section actions |
| `accentWell` | `#1C1608` | `#FBEFD6` | Tinted well behind a saffron glyph |
| `gain` / `gainWell` | `#3FD98B` / 12% | `#0A6B42` / `#E2F5EB` | Income, positive change |
| `loss` | `#FF7B6B` | `#C0392B` | Spending, negative change |
| `track` | `#1C1C1F` | `#E4E4E7` | Empty part of a bar |
| `navFill` / `navBorder` | `#1C1C1E` 86% / white 9% | white 92% / black 8% | Floating nav glass |
| `primaryAction` | saffron | ink | The one filled round action (ثبت خرج) |

`ThemeMode.SYSTEM` follows the phone between the two. `DARK` and `ANDIS_TERMINAL` use the dark
tokens, `LIGHT` the light ones. The other legacy themes in `ThemeMode` get the same roles
derived from their own colour scheme (`getCanvasTokens`), so every layout works under every
theme.

Layout constants: `CanvasTheme.gutter` = 20dp page gutter, `CanvasTheme.cardRadius` = 26dp.

## Shared components

[`ui/components/CanvasKit.kt`](app/src/main/java/com/doxigo/muchtoman/ui/components/CanvasKit.kt):

- `CanvasPage`: the ground plus `SaffronGlow`, the radial light behind the top of a page.
- `CanvasPageHeader`: 11sp caption over a 40sp ExtraBold title, round controls on the end edge.
- `CanvasCard`: card fill, 1px edge, 26dp corners, 16dp padding; optionally clickable.
- `RoundAction`: 56dp round action with a label (Home's row of four). One is primary.
- `OutlineIconButton`: 40/44dp outlined round control for page headers.
- `PillChip`, `TonePill`: filter chips and small tinted badges.
- `CanvasGlyph`: stroked icons drawn from 24-unit SVG path data.
- `CanvasNavBar`: the floating nav.

## Navigation

Five tabs (`Tab` in [`TabBar.kt`](app/src/main/java/com/doxigo/muchtoman/TabBar.kt)): امروز
(Home), دفتر (Ledger), دارایی (Assets), حساب‌ها (Accounts), گزارش (Report). The nav is a 66dp
pill inset 20dp from both edges, floating 10dp above the system inset, with a ground-coloured
scrim so content fades under it. The selected tab is an inverted pill with icon and label; the
others are icon-only. The lite edition has only دارایی and draws no nav.

## Type

The app is set in **Modam** (variable font, `app/src/main/res/font/modam.ttf`, axes `wght`
200–900 and `wdth` 70–100). Text uses the `Modam` family. Figures use `ModamFigures`: the same
font at 90% width with proportional digits (`pnum`), so long Toman totals fit without negative
tracking, which would break Persian joins. All digits are Persian, the layout is RTL, and
displayed amounts truncate rather than round. See [DEVELOPMENT.md](DEVELOPMENT.md#typography)
for how the font file is prepared and the licensing note.
