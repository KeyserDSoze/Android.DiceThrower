# Verifica release premium v0.0.25

## Pubblicazione

- Codice integrato e inviato a `main`: [`59ff832`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/59ff832858e2cfe032556c866912f6494a06c0ba).
- La versione viene portata a `0.0.25` (`versionCode=25`) dopo il preflight remoto.
- Note di rilascio: [releases/0.0.25.md](releases/0.0.25.md).
- Il workflow esistente mantiene chiave/firma stabile, verifica la configurazione OAuth e
  pubblica APK, AAB e checksum. Non dichiarare quei passaggi riusciti prima del termine del run.

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
| Release firmata v0.0.25 | Workflow avviato dal successivo commit `release:` su `VERSION` | Non ancora pubblicata |
| Checksum e firma degli artefatti scaricati | Da registrare dopo la pubblicazione | Non ancora verificati |

Sito pubblicato: [keyserdsoze.github.io/Android.DiceThrower](https://keyserdsoze.github.io/Android.DiceThrower/).
L'HTML live espone il titolo `Dice Thrower — dice that feel real` e il nuovo bundle
`assets/main-CwFFZS0F.js`.

## Compatibilità da verificare sugli artefatti pubblicati

- [ ] APK installabile come aggiornamento della v0.0.24 con lo stesso certificato.
- [ ] Nome package invariato: `com.keyserdsoze.dicethrower`, `versionCode=25`.
- [ ] SHA-256 di APK e AAB corrispondenti ai file `.sha256` della release.
- [ ] Avvio e personaggi/storico conservati nell'aggiornamento su emulatore.

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
