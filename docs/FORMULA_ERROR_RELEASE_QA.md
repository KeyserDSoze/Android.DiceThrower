# Verifica release v0.0.28 e pulizia branch

- Versione prevista `0.0.28`, `versionCode=28`.
- Include il follow-up [`6b75081`](https://github.com/KeyserDSoze/Android.DiceThrower/commit/6b75081),
  integrato da `fix/workshop-formula-error-messages` in `main`.
- Dettagli: [FORMULA_ERROR_MESSAGES.md](FORMULA_ERROR_MESSAGES.md);
  note: [releases/0.0.28.md](releases/0.0.28.md).
- Nessuna modifica al comportamento Android, a RNG, risultati o formati persistiti.

| Verifica | Evidenza | Stato |
| --- | --- | --- |
| Locale prima del bump | 405 JVM, 237 web, build/lint, 92 casi condivisi e Playwright EN/IT × desktop/mobile | Riuscito |
| Build e test locali v0.0.28 | 405 JVM, 237 web, runner 3/3, lint, APK debug/release, AAB, Vite e 40 lingue × 387 stringhe | Riuscito |
| CI del commit di versione | Da registrare | Da avviare |
| Pages e smoke live EN/IT | Context nuovi e dati sintetici, senza toccare profili personali | Da verificare |
| Release firmata e checksum | APK, AAB e certificato pubblico | Da verificare |
| Aggiornamento dalla v0.0.27 | APK ufficiale, `adb install -r`, dati di prova conservati | Da verificare |
| Google Play Internal | Esito da riportare separatamente | Non ancora noto |

## Pulizia branch

Il remoto conteneva già soltanto `main`. I branch locali da eliminare devono essere antenati
del `main` pubblicato e non essere usati da worktree. Niente cancellazioni forzate, tag
rimossi o commit unici scartati. L'esito effettivo viene registrato dopo il push.

## Accettazione ancora aperta

Dispositivi/GPU reali, TalkBack, fotocamera/QR su due telefoni, App Links verificati e account
Google/Drive reali non sono dichiarati verificati. L'EPIC #165 rimane aperta; la pubblicazione
GitHub non dimostra una pubblicazione sul Play Store.
