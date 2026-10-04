# Dice Thrower

**Dice Thrower** è un'app Android offline-first e game-agnostic per creare personaggi e collegare a ciascuno una dashboard di tiri di dado configurabili.

## Obiettivo

Dice Thrower non implementa le regole di un gioco specifico. Un personaggio contiene nome, immagine opzionale, tag libero, livello, modificatori, gruppi ordinabili, tiri parametrizzabili e storico locale.

Esempi di tiro: `6d6+6`, `4d3+3d6+10`, `1d20+{Intelligenza}`.

Dadi supportati nella prima versione: **d2, d3, d4, d6, d10, d12, d20, d100**.

## Esperienza d'uso

1. Home con lista personaggi, immagine, tag e livello.
2. Modalità **Use** con dashboard composta dall'utente.
3. Gruppi espandibili e tiri senza gruppo avviabili direttamente.
4. Modalità **Edit** con long-press + drag per riordinare dashboard e contenuti dei gruppi.
5. Editor visuale delle formule con chip per inserire `{level}` e i modificatori del personaggio nel punto del cursore.
6. Lancio con pulsante configurabile oppure scuotendo il telefono.
7. Animazione OpenGL ES dei dadi 3D, seguita da dettaglio dei singoli risultati e totale.
8. Log locale con retention configurabile.

Il pulsante di lancio può essere nascosto oppure posizionato in alto/basso a sinistra, centro o destra.

## Livelli, modificatori e tiri parametrici

Ogni personaggio può avere un livello e modificatori interi con nomi liberi, per esempio `Intelligenza = 4` o `Forza = -2`.

Le espressioni possono usare le variabili tra parentesi graffe:

- `1d20+{Intelligenza}`
- `2d6+{Forza}+{level}`

`{level}` è sempre disponibile. I tiri possono inoltre avere regole additive di progressione:

- **dal livello N** aggiungi un'espressione una volta;
- **ogni N livelli** aggiungi l'espressione una volta per ogni intervallo raggiunto.

Salire di livello non riscrive i tiri: la formula effettiva viene risolta al momento del lancio, quindi tutti i tiri parametrizzati si aggiornano automaticamente.

## Visual design

L'identità di Dice Thrower usa una palette **deep navy + electric cyan/blue + violet + gold**. L'app usa Material 3 con superfici leggibili e accenti arcane/cosmic, mentre launcher e materiali store possono essere più cinematografici.

Sono presenti:

- adaptive launcher icon Android 8+;
- themed/monochrome icon Android 13+;
- artwork launcher condiviso dall'app;
- sorgente vettoriale 1024×500 per la feature graphic Play Store;
- script ripetibile per generare gli asset raster dello store;
- overview site coerente con la stessa identità visiva.

Vedi `play/assets/README.md`.

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

È predisposto lo stesso set di 40 lingue di Screen Lock. Inglese e italiano sono già le traduzioni di riferimento complete; le altre lingue condividono il registry e verranno completate quando il vocabolario UI sarà stabilizzato.

## Tema

L'app supporta **Sistema / Chiaro / Scuro**. Anche il sito GitHub Pages usa tema chiaro/scuro e memorizza localmente la preferenza.

## Dadi 3D

Il risultato numerico viene sempre deciso dal motore di dadi prima della grafica. Un renderer **OpenGL ES 2.0** mostra poi l'animazione 3D senza poter alterare il risultato, mantenendo log e test deterministici.

Le forme visuali includono geometrie dedicate per d2, d3, d4, d6, d10, d12, d20 e d100. Il d10/d100 usa una forma trapezoedrica e il d12 viene costruito come vero dodecaedro, duale dell'icosaedro. L'overlay mostra anche i risultati individuali e il totale.

## Sviluppo

```bash
cd src
gradle :app:testDebugUnitTest :app:assembleDebug
```

La CI pubblica inoltre l'APK debug installabile come artifact GitHub Actions per 14 giorni.

## Sito

GitHub Pages è distribuito automaticamente dalla workflow dedicata:

- https://keyserdsoze.github.io/Android.DiceThrower/
- https://keyserdsoze.github.io/Android.DiceThrower/privacy/
- https://keyserdsoze.github.io/Android.DiceThrower/terms/
- https://keyserdsoze.github.io/Android.DiceThrower/contact/

## Licenza

MIT.
