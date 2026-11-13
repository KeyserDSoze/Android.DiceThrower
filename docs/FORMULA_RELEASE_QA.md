# Verifica release formule v0.0.27

## Ambito

- Codice del validatore integrato in `main`: [`9446b9a`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/9446b9a77511df4cfc0181a8891df0fd117f6166),
  comprendente [`5f74312`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/5f74312).
- Versione prevista `0.0.27`, Android `versionCode=27`.
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
| Smoke Workshop live | Desktop 1440×1100 scuro e mobile 390×844 chiaro, context isolati e IndexedDB reale | Riuscito |
| Export JSON e gestione errori | Fix verificato localmente su desktop/mobile: errore visibile, nessuna eccezione non gestita, JSON valido scaricato e ricontrollato | Locale riuscito; live da verificare sul commit di versione |
| Release firmata v0.0.27 | Da avviare con commit `release:` su `VERSION` dopo il preflight | Non ancora pubblicata |
| CI del commit di versione | Da registrare | Non ancora avviata |
| Firma e checksum degli artefatti scaricati | APK, AAB e certificato pubblico | Non ancora verificati |
| Aggiornamento dalla v0.0.26 | Installazione ufficiale firmata con personaggio e storico di prova | Non ancora verificato |
| Google Play Internal | Esito da riportare separatamente | Non ancora noto |

Le prove live usano browser/context nuovi: non inserire fixture nel profilo browser personale
né sovrascrivere record dell'utente. Scaricare e verificare gli artefatti ufficiali; per lo
smoke di aggiornamento usare un AVD distinto da quello sacrificabile dei test strumentali.

### Verifica live del Workshop

Su [Workshop pubblicato](https://keyserdsoze.github.io/Android.DiceThrower/workshop/), con profili
di test nuovi: record legacy `1d7` conservato dopo il rifiuto di Save, overflow rifiutato da
Share, correzione `2×1d6` salvata e condivisa, ulteriore bozza invalida senza sovrascrittura,
rimozione di link/app-link superati e formula corretta conservata al reload. Nessun errore
JS/console/HTTP né overflow orizzontale nei due viewport. Report e screenshot:
`/tmp/opencode/dice-release-qa/v0.0.27/site/`.

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
`/tmp/opencode/dice-release-qa/v0.0.27/export-local/`.

## Accettazione ancora aperta

- [ ] GPU/dispositivi reali, fluidità, TalkBack, pause/resume e scuotimento.
- [ ] Fotocamera → QR → Workshop → import Android e fallback JSON su due telefoni.
- [ ] Consenso Google reale, sync su due dispositivi, conflitti, disconnessione e cancellazione cloud.
- [ ] App Links HTTPS verificati e localizzazioni web oltre la copertura attuale.

L'EPIC [#165](https://github.com/KeyserDSoze/Android.DiceThrower/issues/165) resta aperta.
Né l'emulatore né la pubblicazione GitHub provano quei percorsi o una pubblicazione Play.
