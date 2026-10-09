# Effects Engine — Release Sign-off Matrix

Tracks EPIC [#102](https://github.com/KeyserDSoze/Android.DiceThrower/issues/102), release gate [#111](https://github.com/KeyserDSoze/Android.DiceThrower/issues/111), 3D-device sign-off [#110](https://github.com/KeyserDSoze/Android.DiceThrower/issues/110) and signed Google Drive test [#29](https://github.com/KeyserDSoze/Android.DiceThrower/issues/29).

**A green emulator CI is necessary, not sufficient, for a public release.** Record an actual device / Android version / theme / reviewer and attach test results before checking a manual item below. Do not close #110, #111 or announce a release while any required gate is unchecked.

## Automated release gates

- [ ] GitHub Actions `testDebugUnitTest` green on the final merged commit
- [ ] `assembleDebug`, release build, Android lint and Compose emulator instrumentation green
- [ ] Overview site Vite build green
- [ ] All 40 Android locales contain all Effects strings; `python3 scripts/validate_localizations.py` green **without an allowlist**, no `tools:ignore=MissingTranslation`
- [ ] Engine tests: value scopes, AND/OR, exclusivity, floor/ceil/round, reroll/roll-after cycles and RNG/render separation
- [ ] Backup and migration: existing v1/v2 backups and data v12 decode with empty Effects; v13 preserves original/final Part results and trace
- [ ] Duplicate/delete character with Effects: all IDs remapped, alias tokens rewritten, original and duplicate independent
- [ ] Offline-first two-device sync: effects and trace persist on both; concurrent edits cause explicit conflict rather than silent overwrite
- [ ] No accidental overall result presented by adding unrelated Parts; BEST/WORST compares only individually opted-in Parts

## Manual on-device UI and GPU gates

Test on **at least one real Android device** in light and dark themes, preferably more than one GPU/Android version. Test ten table themes with both a fresh Roll and a migrated Roll.

- [ ] New Roll enables double-roll by default, selects only its first Part, and respects later per-Part opt-in edits
- [ ] Swipe **right** = BEST, **left** = WORST, **up** = NORMAL; footer BEST/WORST icons appear next to the normal roll icon, with the normal icon at the rightmost ergonomic position
- [ ] Disabling swipe in Settings removes gesture activation but retains explicit footer controls
- [ ] If multiple Parts opt in, group comparison uses their combined values; statistics still identify their independent Part outcomes and all losing dice
- [ ] Losing dice remain visible at reduced brightness; winning face values and numbers remain readable in light/dark
- [ ] Bonus/Malus editor: create, reorder priority, disable, stop-following, add AND and OR groups, change scope and source, edit valid/invalid formulas
- [ ] Part rename retains correct target and `{partId:...}` reference; ambiguous or deleted target is rejected
- [ ] A natural d20 18 with +2 does **not** trigger dice-only >=20 but triggers total >=20
- [ ] BONUS/MALUS arithmetic adjusts the correct Part with original -> final stats; default floor and explicit `f/c/r` work for positive and negative values
- [ ] REROLL dims the superseded dice while new dice enter; ROLL_AFTER renders previously calculated additional dice only after the first throw settles
- [ ] Active bonus is distinguishable from active malus by **label/icon as well as color**, no unreadable text against themed table imagery
- [ ] Effect chain with stop-following suppresses lower-priority effects; otherwise both apply in explicit order; no infinite animation loop
- [ ] Turning animations **off** immediately displays already sampled result; turning them on produces stages without any extra logical dice samples
- [ ] Pause/resume, rotate, navigate Back, open/close statistics, double-tap overlay and device sleep do not duplicate throws or write repeated logs
- [ ] Verify small screens, 12+ generated dice and reduced-power mode show safe fallback, no clipping or runaway GPU use
- [ ] Native speaker pass on new Effects UI labels in Arabic/Hebrew RTL and a representative sample of CJK, Indic and Latin locales

## Backup, optional Google and compliance

- [ ] Export a character with Effects, custom Part names and post-roll trace; restore to a clean install and check all references and face values
- [ ] Import a legacy backup with no Effects; editing and throwing must work with safe defaults
- [ ] Disconnect from Google: all Effects and historical traces remain available standalone
- [ ] With a correctly signed OAuth-enabled app, run the separate [#29](https://github.com/KeyserDSoze/Android.DiceThrower/issues/29) Google account and Drive `appDataFolder` end-to-end smoke tests
- [ ] Check actual app behavior against README, privacy policy, Data Safety, Play listings, Terms and the multilingual website before publication
- [ ] Confirm no feature relies on a login and that no new analytics, ad, or tracking service was introduced

## Sign-off evidence

| Environment / reviewer | Test date | Theme / locale / GPU | Outcome and evidence |
| --- | --- | --- | --- |
| GitHub Actions (final SHA) | TBD | Unit / build / lint / emulator | Pending |
| Physical device 1 | TBD | TBD | Pending |
| Physical device 2 (if available) | TBD | TBD | Pending |
| Signed Google Drive OAuth | TBD | Online / offline / conflict | Pending |

Release version bump and GitHub release are intentionally **out of scope** until this matrix has been reviewed and the blocking issues closed.
