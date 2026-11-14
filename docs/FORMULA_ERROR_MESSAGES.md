# Errori delle formule EN/IT — follow-up EPIC #165

Tracking: [EPIC #165](https://github.com/KeyserDSoze/Android.DiceThrower/issues/165).
Integrato in `main` da `fix/workshop-formula-error-messages`, per la release **v0.0.28**.
Esiti della pubblicazione: [FORMULA_ERROR_RELEASE_QA.md](FORMULA_ERROR_RELEASE_QA.md).

## Ambito

- `DiceFormulaError` espone il codice stabile `dice-formula-invalid`, uno dei **24 motivi**
  e parametri strutturati: limite, posizione, facce del dado o nome della variabile.
- Il messaggio inglese resta disponibile ai chiamanti non UI. Il formatter EN/IT usa motivo
  e parametri, senza interpretare o confrontare il testo inglese dell'eccezione.
- Save, Share, Export JSON, import di file e suggerimento per l'handoff Android mostrano i
  messaggi delle formule nella lingua dell'interfaccia. Cambiare lingua riformatta anche
  un errore già visibile, senza rieseguire la validazione o scartare la bozza.
- I parametri sono primitivi, copiati e congelati: soli interi non negativi sicuri e nomi
  limitati a 160 caratteri. Non conservano formula completa, documento o dati estranei.
  La UI li rende come testo React, mai come HTML. Alert e suggerimenti vanno a capo anche
  con nomi lunghi senza spazi, invece di essere tagliati dal contenitore della pagina.
- Motivi sconosciuti hanno un fallback localizzato; errori di storage, link e altre
  validazioni mantengono la gestione precedente.

Non cambiano grammatica, budget, risultati, RNG, formati o dati salvati. L'import non valido
lascia intatto il documento aperto e quello già presente in IndexedDB. Nessuna migrazione.
La copertura riguarda gli errori del validatore di formule: non completa la traduzione di
tutti gli errori strutturali né le 38 lingue web che continuano a usare il fallback inglese.

## Prove

- **237 test web**, inclusi tutti i 92 casi condivisi con Android e le verifiche di codice,
  parametri e testo EN/IT per tutti i 24 motivi emessi dai percorsi di validazione reali.
- **405 test JVM**, build Android debug/lint, build Vite e verifica delle localizzazioni.
- Smoke Playwright in quattro context nuovi: EN/IT × desktop 1440×1100 scuro / mobile
  390×844 chiaro. Verificati cambio lingua con lo stesso errore visibile, Save/Share/Export
  non validi, messaggi di import, export valido, record conservato e correzione della bozza.
- Nomi lunghi limitati, parametri simili a HTML mostrati come testo, nessun elemento HTML
  creato né dialogo eseguito; nessun errore JS/console/HTTP o overflow orizzontale.

Report e screenshot della sessione: `/tmp/opencode/dice-formula-error-qa/localized/`.
I browser di test sono isolati dai profili e dai dati personali dell'utente.

Da `src/overviewapp`:

```bash
npm test
npm run build
```

Da `src`:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

L'EPIC resta aperta per accettazione fisica, App Links verificati e gli altri follow-up.
