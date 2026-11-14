# Verifica release v0.0.28 e pulizia branch

- Release [v0.0.28](https://github.com/KeyserDSoze/Android.DiceThrower/releases/tag/v0.0.28),
  `versionCode=28`, pubblicata il 14 novembre 2026 alle 11:17:30 UTC sul commit `6e9f52a`.
- Include il follow-up [`6b75081`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/6b75081),
  integrato da `fix/workshop-formula-error-messages` in `main`.
- Dettagli: [FORMULA_ERROR_MESSAGES.md](FORMULA_ERROR_MESSAGES.md);
  note: [releases/0.0.28.md](releases/0.0.28.md).
- Nessuna modifica al comportamento Android, a RNG, risultati o formati persistiti.

| Verifica | Evidenza | Stato |
| --- | --- | --- |
| Locale prima del bump | 405 JVM, 237 web, build/lint, 92 casi condivisi e Playwright EN/IT × desktop/mobile | Riuscito |
| Build e test locali v0.0.28 | 405 JVM, 237 web, runner 3/3, lint, APK debug/release, AAB, Vite e 40 lingue × 387 stringhe | Riuscito |
| CI del commit di versione | [CI #43589067343](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43589067343), job test/instrumentation, XML remoto 57/57 senza errori o test saltati | Riuscita |
| Pages e smoke live EN/IT | [Pages #43589067331](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43589067331); quattro context nuovi EN/IT × desktop/mobile, dati sintetici e IndexedDB reale | Riusciti |
| Release firmata e checksum | [Release #43589067399](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43589067399), APK/AAB/PEM scaricati e ricontrollati | Verificati |
| Aggiornamento dalla v0.0.27 | APK ufficiale, `adb install -r`, dati di prova conservati | Verificato su API 35 |
| Google Play Internal | Job publish-play-internal | Saltato; nessuna pubblicazione Play verificata |

## Artefatti e aggiornamento

- APK SHA-256: `a7214fc020bb914ea5e4e390b96b10763921a870a751d1852e4f5d3991eb9cd3`.
- AAB SHA-256: `4bc9d54560add405df8d3a771ebd924670bcb1289363607384b6d7c0d83c9487`.
- Certificato SHA-256, invariato dalla v0.0.27:
  `19c7eead47c614a11820cce43e900d19ab4d59617000c146bd8595c4e706dbb2`.

Checksum confrontati con i file pubblicati e i digest GitHub; firme verificate con apksigner,
jarsigner e keytool. Package invariato, APK non debuggabile, versione 0.0.28/code 28, tag sul
commit esatto e note identiche al file versionato.

Aggiornamento ufficiale v0.0.27 → v0.0.28 senza disinstallare o azzerare dati: conservati
`Upgrade25 QA`, livello 7, tag `Release upgrade check`, gruppo `Upgrade group`, tiro
`Upgrade d20` / `1d20+3` e una sola voce di storico (totale 7, `1d20[4] +3`,
`11/13/26 6:37 PM`). `firstInstallTime` invariato: `2026-11-13 18:34:18`; nessun rilancio.

Smoke live: cambio lingua con lo stesso errore visibile, Save/Share/Export/import non validi,
export valido, dati conservati, nomi lunghi non tagliati e input simile a HTML non interpretato.
Nessun errore JS/console/HTTP. Report, screenshot e XML verificato tramite digest dell'artefatto
GitHub ID `14111349254`: `/tmp/opencode/dice-release-qa/v0.0.28/`.

## Pulizia branch

Il remoto conteneva già soltanto `main`. Dopo il push sono stati eliminati con `git branch -d`
gli otto branch locali seguenti, tutti antenati del `main` pubblicato e non usati da worktree:

| Branch eliminato | Ultimo commit, ancora raggiungibile da main |
| --- | --- |
| feat/async-persistence-rotation | e3180c1 |
| feat/premium-dice-physics-graphics | 59ff832 |
| fix/android-review-findings | c3922e3 |
| fix/percentile-rendering-and-effects | ede2a93 |
| fix/sync-integrity-round3 | 3de4ec3 |
| fix/web-native-formula-validation | 9446b9a |
| fix/web-review-findings | d0b61dd |
| fix/workshop-formula-error-messages | a75a5c9 |

Nessuna cancellazione forzata, tag rimosso o commit unico scartato. Al termine della pulizia:
solo `main` locale e remoto. Audit con SHA completi: `branch-cleanup.json` nella directory QA.

## Accettazione ancora aperta

Dispositivi/GPU reali, TalkBack, fotocamera/QR su due telefoni, App Links verificati e account
Google/Drive reali non sono dichiarati verificati. L'EPIC #165 rimane aperta; la pubblicazione
GitHub non dimostra una pubblicazione sul Play Store.
