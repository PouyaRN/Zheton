# 01 — Release signing for in-place upgrades

**What to build:** Installing a newer version upgrades over the existing install — no uninstall, no data loss. Today release builds are unsigned, so an overwrite install fails signature match and forces a fresh install that wipes user data.

**Blocked by:** None — can start immediately.

**Status:** done

- [ ] A stable release keystore and its properties file exist and are wired into the build (gitignored, never committed)
- [ ] `assembleRelease` produces a signed APK and `versionCode` is bumped for each release
- [ ] Installing the new signed APK over the previous one keeps all durable data intact
- [ ] A plain in-place upgrade never requires uninstall