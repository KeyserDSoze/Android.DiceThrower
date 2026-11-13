# Formule Workshop / Android — follow-up EPIC #165

Tracking: [EPIC #165](https://github.com/KeyserDSoze/Android.DiceThrower/issues/165).
Codice integrato in `main` (`9446b9a`), proveniente da `fix/web-native-formula-validation`.
Sito e release firmata v0.0.27 pubblicati e verificati, inclusi Export JSON e aggiornamento
dalla v0.0.26: [FORMULA_RELEASE_QA.md](FORMULA_RELEASE_QA.md).
L'EPIC resta aperta: questo intervento non completa QR fisico, App Links verificati, Drive web
o tutte le localizzazioni.

## Ambito

Il Workshop non usa più una whitelist/regex come validatore completo delle formule dei tiri.
`src/overviewapp/src/workshop/dice-formula.js` rispecchia senza RNG:

- grammatica di `DiceExpression`: soli interi ASCII, dadi supportati, `+`, `-`, `*`/`x`/`X`/`×`,
  parentesi e segni unari; niente divisione, decimali o prodotto tra due espressioni con dadi;
- 100 dadi per termine, 500 per espressione, 1000 termini, 4096 caratteri e 32 livelli di nesting;
- limiti conservativi degli interi intermedi, inclusi overflow prima di cancellazioni/moltiplicazioni per zero;
- risoluzione di livello, modificatori e milestone `levelstep`, moltiplicazione implicita adiacente
  alle variabili e normalizzazione dei segni come in `RollFormulaResolver`;
- budget effettivo del tiro espanso al livello corrente, comprese regole periodiche con start/cap.

Formula principale, Parts dormienti e regole inattive devono avere template validi. Soltanto
le Parts iniziali e le regole attive entrano nel budget del tiro espanso. Le formule degli
effetti aritmetici (`f/c/r`, divisione, decimali e riferimenti alle Parts) hanno una grammatica
nativa separata: questo intervento non la sostituisce con quella dei dadi né implementa
l'esecuzione degli effetti nel browser.

## Dati e compatibilità

- La validazione non tira dadi e non modifica documenti, formule originali, RNG, storico o formati.
- Salvataggio, condivisione, handoff Android e import di pacchetti rifiutano formule incompatibili.
- Le vecchie voci IndexedDB rimangono leggibili/modificabili; non vengono eliminate, migrate o
  sovrascritte automaticamente. Prima di salvarle/condividerle bisogna correggere la formula.
- I tentativi di salvataggio non validi lasciano intatto il record precedente, anche dopo reload.
- `includeInNormalRoll` mancante continua a significare `true`, come nel decoder Android.

## Prove

Il corpus unico `src/app/src/test/resources/dice-expression-parity.json` contiene **92 casi**:
56 numerici, 24 template e 12 espansioni di Roll. Viene eseguito sia da
`DiceExpressionParityTest.kt`, usando i parser nativi di produzione, sia da
`src/overviewapp/src/workshop/dice-formula.test.mjs`. La CI Pages si avvia anche quando cambia
soltanto il corpus. Non si introduce un secondo motore dei tiri Android.

Verificati localmente: **405 test JVM, 184 test web**, build Android debug/lint e build Vite.
Lo smoke Playwright usa browser/context vuoti, un record legacy sintetico e veri IndexedDB/UI:
desktop 1440×1100 scuro e mobile 390×844 chiaro. Verifica blocco di Save/Share per d7 e overflow,
record originale conservato, correzione con `2×1d6`, link valido, rimozione dei link superati
dopo un'altra modifica invalida e persistenza al reload, senza errori JS/console/HTTP.
Report e screenshot locali: `/tmp/opencode/dice-formula-qa/`.

Dalla directory `src/overviewapp`:

```bash
npm test
npm run build
```

Dalla directory `src`:

```bash
gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```
