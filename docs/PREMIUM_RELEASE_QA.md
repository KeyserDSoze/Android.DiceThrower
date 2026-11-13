# Verifica release premium v0.0.25

## Pubblicazione

- Codice integrato e inviato a `main`: [`59ff832`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/59ff832858e2cfe032556c866912f6494a06c0ba).
- Release [`v0.0.25`](https://github.com/KeyserDSoze/Android.DiceThrower/releases/tag/v0.0.25),
  `versionCode=25`, pubblicata dal commit [`97b0215`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/97b02158e42e12c2783316ffccb4dfe7abca7087)
  il 13 novembre 2026 alle 18:45:51 UTC, dopo il preflight remoto.
- Note di rilascio: [releases/0.0.25.md](releases/0.0.25.md).
- Il workflow ha verificato firma stabile e configurazione OAuth e ha pubblicato APK, AAB,
  certificato pubblico e checksum. Gli artefatti scaricati sono stati ricontrollati separatamente.

## Verifiche automatiche

- [x] Locale: 381 unit test Android.
- [x] Locale: 56 test strumentali su emulatore API 35, inclusi sei percorsi E2E.
- [x] Locale: 3 test dell'orchestrazione del runner (errore Gradle non mascherato dalla copia artefatti).
- [x] Locale: 85 test web e build Vite.
- [x] Locale: build debug/release, lint e 40 lingue × 387 stringhe.

### Evidenze remote

| Controllo | Evidenza | Stato |
| --- | --- | --- |
| Preflight Android `59ff832` | [CI #43481812469](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43481812469), job `test` e `instrumentation` | Riuscito |
| Sito sullo stesso commit | [Pages #43481812262](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43481812262), build e deploy | Riuscito |
| Smoke sito live | Chromium/Playwright: desktop 1440×1100 e mobile 390×844, tavolo 3D, rilancio e apertura Workshop | Riuscito |
| CI del commit di versione `97b0215` | [CI #43485072291](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43485072291): job `test` riuscito; 55/56 test strumentali riusciti, inclusi tutti i 6 E2E | Fallito un test di ripristino editor; correzione in verifica |
| Release firmata v0.0.25 | [Release #43485072265](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43485072265), tag sul commit esatto `97b0215` | Riuscita |
| Google Play Internal | Job `publish-play-internal` nello stesso run | Saltato; nessuna pubblicazione Play verificata |
| Checksum e firma degli artefatti scaricati | APK/AAB, certificato esportato, note e versione ricontrollati | Verificati |

Sito pubblicato: [keyserdsoze.github.io/Android.DiceThrower](https://keyserdsoze.github.io/Android.DiceThrower/).
L'HTML live espone il titolo `Dice Thrower — dice that feel real` e il nuovo bundle
`assets/main-CwFFZS0F.js`.
Lo smoke test del sito non ha rilevato errori JavaScript/console, risposte HTTP fallite o
overflow orizzontale nelle due pagine e nei due viewport provati. Screenshot e report locali:
`/tmp/opencode/dice-release-qa/site/` (artefatti della sessione, non inclusi nel repository).

## Compatibilità verificata sugli artefatti pubblicati

- [x] APK installabile come aggiornamento della v0.0.24 con lo stesso certificato.
- [x] Nome package invariato: `com.keyserdsoze.dicethrower`, `versionName=0.0.25`, `versionCode=25`; APK non debuggabile.
- [x] SHA-256 di APK e AAB corrispondenti ai file `.sha256` della release e ai digest degli asset GitHub.
- [x] Avvio e personaggio/storico di prova conservati nell'aggiornamento su emulatore API 35.

| Artefatto | SHA-256 |
| --- | --- |
| `dice-thrower-v0.0.25.apk` | `f2b0f87df8690735bb9b5d655ef1ae066016ae8d3b4af82dc954a4f44ab954ad` |
| `dice-thrower-v0.0.25.aab` | `d5b80077822ae29ddbbb02d37c13b0003d6e56b0960067d5c897d02ac1082e8f` |

Certificato SHA-256 comune a v0.0.24, APK v0.0.25, AAB e PEM esportato:
`19c7eead47c614a11820cce43e900d19ab4d59617000c146bd8595c4e706dbb2`.
Verificati con `apksigner`, `jarsigner` e `keytool`; note pubblicate identiche al file versionato.

### Aggiornamento firmato senza reinstallazione

Installata l'APK ufficiale v0.0.24, importato il personaggio sintetico `Upgrade25 QA`, quindi
aggiornato con l'APK ufficiale v0.0.25 mediante `adb install -r`, senza disinstallare o azzerare
i dati. `firstInstallTime` è rimasto `2026-11-13 18:34:18`.

Conservati e controllati tramite UI: livello **7**, tag `Release upgrade check`, gruppo
`Upgrade group`, tiro `Upgrade d20`, formula `1d20+3`. Lo storico contiene ancora **una sola voce**:
totale **7**, dettaglio `1d20[4] +3`, timestamp `11/13/26 6:37 PM`. Nessun rilancio è stato eseguito.

Report JSON, output di verifica firme/versione e screenshot prima/dopo sono in
`/tmp/opencode/dice-release-qa/v0.0.24/` e `v0.0.25/` (artefatti locali della sessione).
L'emulatore firmato è stato spento conservandone i dati; i successivi test usano un AVD separato.

### Errore intermittente del test editor nel run di versione

L'XML remoto identifica soltanto
`EditorStateRestorationTest.restorationDoesNotRegenerateTheNewRollPartId`: dopo lo scroll il
selettore `Save` non trova più il nodo. Il logcat mostra richieste asincrone di apertura IME
nello stesso intervallo. Tutti i sei scenari E2E, inclusi editor e rotazione, sono riusciti.

Il test originario è passato in 15 esecuzioni locali consecutive: il difetto è intermittente.
La correzione del test termina esplicitamente il focus di editing, chiude la tastiera prima
dello scroll, verifica che Save sia visibile/abilitato e sincronizza la lettura del callback.
Non aggiunge retry, pause arbitrarie o esclusioni e non modifica il codice dell'APK firmata.
Dopo la correzione: **56/56** test strumentali locali riusciti sul nuovo AVD API 35 e **20/20**
esecuzioni consecutive del test interessato (15 con scala animazioni 0, 5 con scala 1).
Passano anche i 3 test del runner. Report di stress:
`/tmp/opencode/dice-release-qa/editor-restoration-after-fix/results.json`.
La nuova verifica remota resta da registrare al completamento, senza cancellare l'esito
storico. Diagnostici remoti scaricati in `/tmp/opencode/dice-release-qa/failed-ci/`.

Le vecchie versioni non verificano il nuovo hash dei personaggi modificati con limiti delle
regole periodiche: aggiornare insieme i dispositivi sincronizzati. Le espressioni legacy che
superano i nuovi limiti devono essere corrette prima di esportarle/sincronizzarle.

## Accettazione fisica — ancora da eseguire

- [ ] Fluidità del primo lancio, materiali, antialiasing e leggibilità su GPU reali.
- [ ] Temi chiaro/scuro, risparmio energetico, pause/resume e TalkBack.
- [ ] Scuotimento reale del telefono e gesture durante l'uso.
- [ ] Fotocamera → QR → workshop → import Android, anche con personaggi grandi via JSON.
- [ ] Consenso Google reale, sync su due dispositivi, conflitti, disconnessione e cancellazione cloud.

Nessun test locale o su emulatore equivale a queste prove. La pubblicazione GitHub non prova
una pubblicazione sul Play Store: il job Play Internal resta subordinato alla configurazione
del repository e il suo esito va riportato separatamente.
