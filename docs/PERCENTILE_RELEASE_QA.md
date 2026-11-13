# Verifica release percentili v0.0.26

## Ambito

- Codice integrato in `main`: [`ede2a93`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/ede2a93fb832af859f884cc59676b6368af20f4a),
  comprendente il fix [`c95cf8f`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/c95cf8f).
- Release [`v0.0.26`](https://github.com/KeyserDSoze/Android.DiceThrower/releases/tag/v0.0.26),
  Android `versionCode=26`, pubblicata il 13 novembre 2026 alle 20:22:56 UTC sul commit
  [`17f78ef`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/17f78ef62a3708bf90b37a436f27974b4e2e9a8a).
- Note: [releases/0.0.26.md](releases/0.0.26.md).
- Nessuna modifica a RNG, risultati logici, persistenza o formati di backup/trasferimento/sync.

## Verifiche

| Controllo | Evidenza | Stato |
| --- | --- | --- |
| Locale prima del bump | 401 test JVM, 57 strumentali API 35, lint e build debug/release del follow-up | Riuscito |
| Build e test locali alla versione 0.0.26 | 401 JVM, 57 strumentali API 35, 3 test del runner, lint, APK debug/release, AAB e 40 lingue × 387 stringhe | Riuscito dopo riavvio AVD; primo run interrotto da System UI |
| Preflight remoto `ede2a93` | [CI #43497715783](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43497715783), job `test` e `instrumentation`; XML remoto ricontrollato: 57/57, zero errori o test saltati | Riuscito |
| Release firmata v0.0.26 | [Release #43500220247](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43500220247), tag sul commit esatto `17f78ef` | Riuscita |
| CI del commit di versione | [CI #43500220397](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43500220397), job `test` e `instrumentation`; XML remoto: 57/57, zero errori o test saltati | Riuscita |
| Firma e checksum degli artefatti scaricati | APK, AAB e certificato pubblico confrontati con checksum/digest GitHub e firma della v0.0.25 | Verificati |
| Aggiornamento dalla v0.0.25 | `adb install -r` dell'APK ufficiale; nome, livello, gruppo, tiro e singola voce di storico conservati | Verificato su API 35 |
| Google Play Internal | Job `publish-play-internal` nel run della release | Saltato; nessuna pubblicazione Play verificata |

## Artefatti ufficiali e firma

| Artefatto | SHA-256 |
| --- | --- |
| `dice-thrower-v0.0.26.apk` | `5f25dc1622d2c8bf18df32ed9f5d4dbddbf1e835f4400b622735b8009d7a2c96` |
| `dice-thrower-v0.0.26.aab` | `85779c634bfcfe70ef3f74c28de48f0326d418306a8b16175ad45c959a93f23b` |

Asset scaricati dalla release pubblicata, confrontati sia con i file `.sha256` sia con i digest
degli asset GitHub. Firma APK verificata con `apksigner`, AAB con `jarsigner`/`keytool`, PEM
pubblico con `keytool`. Certificato SHA-256 comune a v0.0.25, APK/AAB v0.0.26 e PEM:
`19c7eead47c614a11820cce43e900d19ab4d59617000c146bd8595c4e706dbb2`.

APK non debuggabile; package `com.keyserdsoze.dicethrower`, `versionName=0.0.26`,
`versionCode=26`. Note pubblicate identiche al file versionato. La continuità del certificato
è stata verificata anche installando l'APK ufficiale come aggiornamento, non solo leggendo il PEM.

## Aggiornamento firmato senza perdita dei dati di prova

Sul precedente AVD firmato, senza disinstallare o azzerare l'app, aggiornamento ufficiale
v0.0.25 → v0.0.26 con `adb install -r`. `firstInstallTime` invariato: `2026-11-13 18:34:18`.
Conservati e controllati tramite UI:

- personaggio `Upgrade25 QA`, livello **7**, tag `Release upgrade check`;
- gruppo `Upgrade group`, tiro `Upgrade d20`, formula `1d20+3`;
- **una sola voce** di storico, totale **7**, dettaglio `1d20[4] +3`, timestamp `11/13/26 6:37 PM`.

Nessun rilancio è stato attivato. L'AVD è stato spento conservandone i dati. Report JSON,
firme/versione, XML remoti e screenshot prima/dopo sono conservati in
`/tmp/opencode/dice-release-qa/v0.0.26/` (`verification.json`, `upgrade-smoke.json`,
`preflight-results.json`, `release-ci-results.json`). I test strumentali sono stati eseguiti
su un AVD separato, non sull'installazione firmata con lo storico da preservare.

### Incidente dell'emulatore nel primo run locale

Il primo run della v0.0.26 ha eseguito 57 test, con due errori Espresso di mancato focus
(`statisticsLevelEditsAndSystemBackFollowTheExpectedHierarchy` e
`restorationDoesNotRegenerateTheNewRollPartId`). Lo screenshot e `dumpsys window` mostrano
il dialogo **System UI isn't responding** di `com.android.systemui` sopra l'app: non un ANR
di Dice Thrower. Diagnostici conservati in
`/tmp/opencode/dice-release-qa/v0.0.26/local-systemui-anr/`.

Riavviato l'AVD e verificato il focus del launcher: la suite completa è poi riuscita con
**57/57**, senza modificare codice/test, introdurre retry automatici o escludere scenari.
Il runner ha mantenuto correttamente l'esito negativo del primo run e raccolto i suoi artefatti.

## Accettazione fisica — ancora da eseguire

- [ ] GPU/dispositivi reali: fluidità, antialiasing, materiali e leggibilità.
- [ ] TalkBack, risparmio energetico, pause/resume e scuotimento reale.
- [ ] Fotocamera → QR → Workshop → import Android, anche tramite JSON.
- [ ] Consenso Google reale e sync su due dispositivi, conflitti, disconnessione e cancellazione cloud.

Gli screenshot e i test su emulatore non sostituiscono queste prove. La pubblicazione GitHub
non dimostra una pubblicazione sul Play Store.
