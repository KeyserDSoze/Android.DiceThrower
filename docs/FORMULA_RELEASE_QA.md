# Verifica release formule v0.0.27

## Ambito

- Codice del validatore integrato in `main`: [`9446b9a`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/9446b9a77511df4cfc0181a8891df0fd117f6166),
  comprendente [`5f74312`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/5f74312).
- Release [`v0.0.27`](https://github.com/KeyserDSoze/Android.DiceThrower/releases/tag/v0.0.27),
  Android `versionCode=27`, pubblicata il 13 novembre 2026 alle 21:58:15 UTC sul commit
  [`5b3fdb5`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/5b3fdb50d168b3259a791cdf8497e20f23caf66d).
- Note: [releases/0.0.27.md](releases/0.0.27.md); ambito tecnico:
  [FORMULA_VALIDATION_PARITY.md](FORMULA_VALIDATION_PARITY.md).
- Comportamento Android invariato rispetto alla v0.0.26; aggiunta soltanto lettura interna
  della sessione attiva per sincronizzare un test di ricreazione. Nessuna modifica a RNG,
  risultati logici, durata delle animazioni o formati persistiti.

## Verifiche

| Controllo | Evidenza | Stato |
| --- | --- | --- |
| Locale prima del bump | 405 JVM, 184 web, corpus condiviso di 92 casi, build/lint e smoke Playwright desktop/mobile | Riuscito |
| Build e test locali alla versione 0.0.27 | 405 JVM, 184 web, 57 strumentali API 35, runner 3/3, lint, APK/AAB, build Vite e 40 lingue × 387 stringhe | Riuscito; suite ripetuta anche dopo la correzione del test |
| Primo preflight Android `9446b9a` | [CI #43508614791](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43508614791), job `test` riuscito; 56/57 strumentali | Fallito un test sensibile alla durata dell'animazione; corretto in `0bfcd83` |
| Verifica della correzione del test | [CI #43512139937](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43512139937) sul commit [`0bfcd83`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/0bfcd833d9851c259fbc664fecf9de2fad09da0a); XML remoto: 57/57, zero errori o test saltati | Riuscita |
| Preflight e deploy Pages `9446b9a` | [Pages #43508614793](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43508614793), 184 test web, build e deploy | Riuscito |
| Pages del commit di versione | [Pages #43514874547](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43514874547), inclusa gestione errori Export JSON | Riuscito |
| Smoke Workshop live finale | Desktop 1440×1100 scuro e mobile 390×844 chiaro, context isolati e IndexedDB reale | Riuscito sul sito distribuito |
| Export JSON e gestione errori | Locale e live: errore visibile, nessuna eccezione non gestita, JSON valido scaricato e ricontrollato | Verificato nei due viewport |
| Release firmata v0.0.27 | [Release #43514874636](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43514874636), tag sul commit esatto `5b3fdb5` | Riuscita |
| CI del commit di versione | [CI #43514874543](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43514874543), job `test` e `instrumentation`; XML remoto 57/57, zero errori o test saltati | Riuscita |
| Firma e checksum degli artefatti scaricati | APK, AAB e certificato pubblico confrontati con checksum/digest GitHub e firma della v0.0.26 | Verificati |
| Aggiornamento dalla v0.0.26 | `adb install -r` dell'APK ufficiale; nome, livello, gruppo, tiro e singola voce di storico conservati | Verificato su API 35 |
| Google Play Internal | Job `publish-play-internal` nel run della release | Saltato; nessuna pubblicazione Play verificata |

Le prove live usano browser/context nuovi: non inserire fixture nel profilo browser personale
né sovrascrivere record dell'utente. Scaricare e verificare gli artefatti ufficiali; per lo
smoke di aggiornamento usare un AVD distinto da quello sacrificabile dei test strumentali.

## Artefatti ufficiali e aggiornamento firmato

| Artefatto | SHA-256 |
| --- | --- |
| `dice-thrower-v0.0.27.apk` | `027011a957f6f149c3fc89226509e89f869c0383f35d2f67dc019d2d84c2f7c8` |
| `dice-thrower-v0.0.27.aab` | `245dd5779ce65fe09e1e0d32e2cae85a08956205e27ce36172c9cd6cf03ea9f1` |

Asset scaricati dalla release pubblicata e confrontati sia con i file `.sha256` sia con i
digest degli asset GitHub. Firma APK verificata con `apksigner`, AAB con `jarsigner`/`keytool`,
PEM con `keytool`. Certificato SHA-256 comune a v0.0.26, APK/AAB v0.0.27 e PEM:
`19c7eead47c614a11820cce43e900d19ab4d59617000c146bd8595c4e706dbb2`.
APK non debuggabile; package `com.keyserdsoze.dicethrower`, `versionName=0.0.27`, `versionCode=27`.
Le note pubblicate coincidono con il file versionato.

Sul precedente AVD firmato, aggiornamento ufficiale v0.0.26 → v0.0.27 con `adb install -r`,
senza disinstallare o azzerare dati. `firstInstallTime` invariato: `2026-11-13 18:34:18`.
Controllati tramite UI: `Upgrade25 QA`, livello **7**, tag `Release upgrade check`, gruppo
`Upgrade group`, tiro `Upgrade d20`, formula `1d20+3`. Ancora **una sola voce** di storico:
totale **7**, dettaglio `1d20[4] +3`, timestamp `11/13/26 6:37 PM`. Nessun rilancio attivato.
L'AVD è stato spento conservandone i dati.

L'XML remoto della CI di versione è stato recuperato dall'artefatto ID `14080223715` e ne è
stato verificato il digest SHA-256 esposto dall'API GitHub, prima di leggere i 57 risultati.
Confermata anche la riuscita del test sostitutivo di ricreazione nella fase generata.
Report, checksum/firme, XML e screenshot: `/tmp/opencode/dice-release-qa/v0.0.27/`
(`verification.json`, `upgrade-smoke.json`, `stage-entry-ci-results.json`, `release-ci-results.json`).

### Verifica live del Workshop

Su [Workshop pubblicato](https://keyserdsoze.github.io/Android.DiceThrower/workshop/), con profili
di test nuovi: record legacy `1d7` conservato dopo il rifiuto di Save, overflow rifiutato da
Share, correzione `2×1d6` salvata e condivisa, ulteriore bozza invalida senza sovrascrittura,
rimozione di link/app-link superati e formula corretta conservata al reload. Nessun errore
JS/console/HTTP né overflow orizzontale nei due viewport. Verificato anche Export JSON:
errore di formula mostrato nell'interfaccia, record precedente intatto, download valido con
formula `2×1d6` ricontrollata nel file. Report e screenshot finali:
`/tmp/opencode/dice-release-qa/v0.0.27/site-final/`; il precedente smoke è in `site/`.

### Correzione della sincronizzazione del test di fase generata

L'XML del primo preflight identifica soltanto
`RollJourneyE2ETest.rotationDuringGeneratedDiceStageDoesNotExecuteTheEffectAgain`: il test
aspettava il badge dell'effetto e poi verificava un pulsante ancora bloccato, ma sul runner
remoto la fase era già terminata prima dell'asserzione.

Il test sostitutivo registra un osservatore `snapshotFlow` sul main thread **prima del tiro**,
verifica fase generata ancora pendente e controlli bloccati, richiede subito `Activity.recreate()`
e aspetta una nuova Activity reale. Nessuna pausa, rallentamento dell'animazione o retry.
Il ViewModel espone soltanto lettura interna della sessione: nessun setter esterno o hook
che alteri logica, RNG o renderer. Il checkpoint viene acquisito dopo il ripristino, non prima
dell'interruzione da verificare.

Dopo la correzione: 57/57 strumentali e **10/10 esecuzioni consecutive** dello scenario,
con trace `stage=1, revealed=false, controlsReady=false` in tutte le richieste di ricreazione.
Diagnostici del run fallito: `/tmp/opencode/dice-release-qa/v0.0.27/preflight-failed-ci/`;
stress: `stage-entry-stress/` nella stessa directory. La nuova CI è riuscita; ricontrollato
anche l'XML del run remoto, incluso il test sostitutivo. Report:
`stage-entry-ci-results.json`. L'esito storico del primo run resta documentato.

### Export JSON con bozza non valida

Lo smoke sul sito ha riprodotto un'eccezione non gestita cliccando Export JSON con `1d7`:
nessun alert UI, bozza conservata. Diagnostico precedente:
`/tmp/opencode/dice-formula-error-qa/export-before.json`.
Il gestore dell'azione ora intercetta l'errore e lo mostra come Save/Share, senza cambiare
serializzazione o validazione. Smoke locale desktop/mobile riuscito anche su export errato
e download JSON valido (formula `2×1d6` ricontrollata nel file):
`/tmp/opencode/dice-release-qa/v0.0.27/export-local/`. La verifica è riuscita anche sul sito
pubblicato dopo il deploy del commit di versione, come registrato in `site-final/`.

## Accettazione ancora aperta

- [ ] GPU/dispositivi reali, fluidità, TalkBack, pause/resume e scuotimento.
- [ ] Fotocamera → QR → Workshop → import Android e fallback JSON su due telefoni.
- [ ] Consenso Google reale, sync su due dispositivi, conflitti, disconnessione e cancellazione cloud.
- [ ] App Links HTTPS verificati e localizzazioni web oltre la copertura attuale.

L'EPIC [#165](https://github.com/KeyserDSoze/Android.DiceThrower/issues/165) resta aperta.
Né l'emulatore né la pubblicazione GitHub provano quei percorsi o una pubblicazione Play.
