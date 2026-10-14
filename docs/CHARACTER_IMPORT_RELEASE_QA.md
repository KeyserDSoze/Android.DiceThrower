# Character Workshop QR / Android v0.0.24 release verification

Tracking: [#202](https://github.com/KeyserDSoze/Android.DiceThrower/issues/202), [EPIC #165](https://github.com/KeyserDSoze/Android.DiceThrower/issues/165).

## Intended flow

1. Android **Share character** creates a QR containing the GitHub Pages Workshop **HTTPS URL**. It does **not** launch the app directly from the camera; there is no built-in scanner or verified HTTPS Android App Link.
2. Phone camera/browser opens Workshop. Player reviews character name, level, groups and rolls.
3. User explicitly taps **Open in Android app**. The browser requests `dicethrower://import?data=...`, registered by Android's `MainActivity` for **v0.0.24+**.
4. Android validates the portable packet and shows a confirmation dialog. **Only confirmation** creates a new independent local character and remaps group, roll, Part, style and effect references. Existing characters are not overwritten.
5. Oversized presets are handled through **JSON export/import with Storage Access Framework**, not a potentially unreliable long app-link. Browser v1/v2 Wizard share recipes remain pinned; Wizard v3 capped level rules use a new transfer envelope version 2 so older installed apps fail safely.

## Automated acceptance

- [x] Web unit tests and Pages build/deploy on most recent web-source commit (release PR made no web changes).
- [x] Android `testDebugUnitTest`, `assembleDebug`, `assembleRelease`, `lintDebug` and instrumentation APK build.
- [x] Emulator `CharacterDeepLinkImportTest`: registered VIEW URI, preview (no mutation until confirm), safe copy including groups/rolls, invalid link rejection.
- [x] Release workflow builds *signed* APK/AAB, verifies signature/production OAuth config, generates sha256 and publishes tagged v0.0.24 artifacts.

## Physical device acceptance — pending, not automatic

- [ ] Scan a real QR from a device using the stock camera and review Workshop content.
- [ ] Tap web **Open in Android app** with app closed and with it already running, accept and decline separately.
- [ ] Confirm name/level edit, preservation of RollGroups, parts, effects and old characters; import twice for independent IDs.
- [ ] Test large Wizard transfer through JSON SAF; capped `EVERY_LEVELS` rules and level 20.
- [ ] Confirm offline use, both themes and Android back navigation.

Physical tests must be recorded with device/OS/date/result in **#165**; do **not** mark them verified from emulator CI.

## Evidence

| Gate | Evidence |
|---|---|
| PR CI | [Android CI #38633787642](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/38633787642): test + emulator **success** |
| Main CI and Pages | [Android CI main #38635000079](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/38635000079): test + emulator **success**; [last website Pages #38623751420](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/38623751420): **success** (no web source changes since) |
| Signed GitHub Release v0.0.24 | [GitHub Release](https://github.com/KeyserDSoze/Android.DiceThrower/releases/tag/v0.0.24), signed APK+AAB+SHA256; [Release workflow #38635000084](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/38635000084) **success**; Google Play internal **skipped** |
| Real camera → site → app | Pending (#165) |
