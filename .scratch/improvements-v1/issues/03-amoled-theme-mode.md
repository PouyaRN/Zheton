# 03 — AMOLED True Black Theme Mode

**What to build:** Add a dedicated AMOLED theme option that sets background to pitch black (`#000000`) and uses crisp, high-contrast surface containers for deep battery savings on OLED devices and zero-glow viewing at night. Add AMOLED as an option in the settings theme picker.

**Blocked by:** None (can start immediately).

**Status:** closed

- [x] `ThemeMode` includes `AMOLED("آمولد")`.
- [x] `AmoledDarkColors` palette defined with `#000000` background and high-contrast containers.
- [x] Settings `ThemePicker` displays AMOLED alongside System, Light, and Dark.
- [x] Theme selection persists to `SharedPreferences` and survives app restarts.
- [x] Unit test verifies `ThemeMode` enum parsing and persistence fallback.
