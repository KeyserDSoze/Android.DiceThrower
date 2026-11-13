# Verifica release percentili v0.0.26

## Ambito

- Codice integrato in `main`: [`ede2a93`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/ede2a93fb832af859f884cc59676b6368af20f4a),
  comprendente il fix [`c95cf8f`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/c95cf8f).
- Versione prevista `0.0.26`, Android `versionCode=26`.
- Note: [releases/0.0.26.md](releases/0.0.26.md).
- Nessuna modifica a RNG, risultati logici, persistenza o formati di backup/trasferimento/sync.

## Verifiche

| Controllo | Evidenza | Stato |
| --- | --- | --- |
| Locale prima del bump | 401 test JVM, 57 strumentali API 35, lint e build debug/release del follow-up | Riuscito |
| Build e test locali alla versione 0.0.26 | 401 JVM, 57 strumentali API 35, 3 test del runner, lint, APK debug/release, AAB e 40 lingue × 387 stringhe | Riuscito dopo riavvio AVD; primo run interrotto da System UI |
| Preflight remoto `ede2a93` | [CI #43497715783](https://github.com/KeyserDSoze/Android.DiceThrower/actions/runs/43497715783), job `test` e `instrumentation`; XML remoto ricontrollato: 57/57, zero errori o test saltati | Riuscito |
| Release firmata v0.0.26 | Da avviare con commit `release:` su `VERSION` dopo il preflight | Non ancora pubblicata |
| CI del commit di versione | Da registrare | Non ancora avviata |
| Firma e checksum degli artefatti scaricati | APK, AAB e certificato pubblico | Non ancora verificati |
| Aggiornamento dalla v0.0.25 | Installazione ufficiale firmata con personaggio e storico di prova | Non ancora verificato |
| Google Play Internal | Esito da riportare separatamente | Non ancora noto |

Non dichiarare riuscita una verifica remota prima del completamento del run. Gli artefatti
vanno scaricati dalla release pubblicata e confrontati con i relativi checksum; firma e
continuità del certificato vanno verificate anche sul dispositivo con `adb install -r`.

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
