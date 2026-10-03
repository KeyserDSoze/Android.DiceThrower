# Dice Thrower

**Dice Thrower** è un'app Android offline-first e game-agnostic per creare personaggi e collegare a ciascuno una dashboard di tiri di dado configurabili.

## Obiettivo

Dice Thrower non implementa le regole di un gioco specifico. Un personaggio contiene solo nome, immagine opzionale, tag libero, gruppi ordinabili, tiri ordinabili e storico locale.

Esempi di tiro: `6d6+6`, `4d3+3d6+10`, `1d20+8`.

Dadi supportati nella prima versione: **d2, d3, d4, d6, d10, d12, d20, d100**.

## UX prevista

1. Home con lista personaggi.
2. Creazione personaggio: nome, immagine e tag.
3. Modalità **Edit**: gestione gruppi e tiri.
4. Modalità **Use**: dashboard ordinata dall'utente.
5. Tocco su un tiro: schermata pronta al lancio.
6. Lancio con pulsante configurabile oppure scuotendo il telefono.
7. Risultato con dettaglio dei singoli dadi e totale.
8. Log locale con retention configurabile.

Il pulsante di lancio può essere nascosto oppure posizionato in alto/basso a sinistra, centro o destra.

## Privacy e storage

La prima versione è completamente locale: nessun account, analytics, tracking, backend o permesso Internet. Personaggi, configurazione e log restano sul dispositivo.

## Stack

La baseline segue la struttura di Android.ScreenLock:

- sorgenti Android in `src/`;
- package `com.keyserdsoze.dicethrower`;
- Kotlin + Jetpack Compose + Material 3;
- compile/target SDK 37;
- Android Gradle Plugin 9.4.0;
- Gradle 9.6.0;
- Kotlin 2.4.10;
- Compose BOM 2026.09.00;
- JDK 17;
- minSdk 26;
- overview site React/Vite in `src/overviewapp/`;
- GitHub Actions per build/test e GitHub Pages.

## Localizzazione

È predisposto lo stesso set di 40 lingue di Screen Lock. Il bootstrap include inglese e italiano completi e il registry delle 40 lingue; le altre traduzioni verranno completate mantenendo le stesse chiavi.

## Tema

L'app supporta **Sistema / Chiaro / Scuro**. Anche il sito GitHub Pages usa tema chiaro/scuro e memorizza localmente la preferenza.

## Dadi 3D

Il dominio e la schermata di tiro sono separati dal renderer. La prima baseline usa una visualizzazione Compose 2D; un renderer 3D potrà essere aggiunto senza cambiare parser, log o modello dati.

## Sviluppo

```bash
cd src
gradle :app:testDebugUnitTest :app:assembleDebug
```

## Sito

Quando GitHub Pages sarà abilitato sul repository:

- https://keyserdsoze.github.io/Android.DiceThrower/
- https://keyserdsoze.github.io/Android.DiceThrower/privacy/
- https://keyserdsoze.github.io/Android.DiceThrower/terms/
- https://keyserdsoze.github.io/Android.DiceThrower/contact/

## Licenza

MIT.
