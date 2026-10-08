# Dice Thrower

**Dice Thrower** è un'app Android offline-first e game-agnostic per creare personaggi e collegare a ciascuno una dashboard di tiri di dado configurabili.

## Obiettivo

Dice Thrower non implementa le regole di un gioco specifico. Un personaggio contiene nome, immagine opzionale, tag libero, livello, modificatori, gruppi ordinabili, tiri parametrizzabili e storico locale.

Esempi di tiro: `6d6+6`, `4d3+3d6+10`, `1d20+{Intelligenza}`.

Dadi supportati nella prima versione: **d2, d3, d4, d6, d8, d10, d12, d20, d100**.

## Esperienza d'uso

1. Home con lista personaggi, immagine, tag e livello.
2. Modalità **Use** con dashboard composta dall'utente.
3. Gruppi espandibili e tiri senza gruppo avviabili direttamente.
4. Modalità **Edit** con long-press + drag per riordinare dashboard e contenuti dei gruppi.
5. Libreria di stili dei dadi per personaggio con materiali, due colori, stile predefinito e anteprima 3D live.
6. Aspetto dei singoli tiri configurabile: predefinito, uniforme, per dado, casuale uniforme o casuale per dado, con pool opzionale.
7. Builder guidato dei tiri con parti nominate (per esempio colpire/danni), dadi, costanti, variabili, moltiplicatori e modalità formula avanzata.
8. Lancio con icona sempre disponibile e gesture configurabili separatamente per primo tiro e rilancio: tap sul tavolo/dadi, swipe verso l’alto e movimento del telefono. Dopo un tiro completato, doppio tap sul tavolo per aprire le statistiche.
9. Tavolo OpenGL ES full-screen con dieci preset grafici premium, preview visuale, foto personale opzionale, dadi numerati, collisioni fisiche e statistiche separate.
10. Log locale con retention configurabile.
11. Backup e restore locale validato tramite Android Storage Access Framework.

Nel tavolo V2 l’icona del dado resta sempre disponibile nel footer. Le gesture aggiuntive sono opzionali e vengono configurate in modo indipendente per il primo tiro e per i rilanci; le preferenze di interazione restano locali al dispositivo. A lancio completato, un doppio tap sul tavolo apre lo stesso pannello delle statistiche accessibile dal footer; un altro doppio tap sul pannello lo chiude. Il comando originale resta sempre disponibile e la nuova scorciatoia è attiva per impostazione predefinita, ma disattivabile dalle Impostazioni. Il doppio tap non deve attivare un rilancio involontario.

## Livelli, modificatori e tiri parametrici

Ogni personaggio può avere un livello e modificatori interi con nomi liberi, per esempio `Intelligenza = 4` o `Forza = -2`.

Le espressioni possono usare le variabili tra parentesi graffe:

- `1d20+{Intelligenza}`
- `2d6+{Forza}+{level}`

`{level}` è sempre disponibile. I tiri possono inoltre avere regole additive di progressione:

- **dal livello N** aggiungi un'espressione una volta;
- **ogni N livelli** aggiungi l'espressione una volta per ogni intervallo raggiunto.

Salire di livello non riscrive i tiri: la formula effettiva viene risolta al momento del lancio, quindi tutti i tiri parametrizzati si aggiornano automaticamente.

Il builder può dividere lo stesso tiro in parti nominate, riordinarle e combinarle con `+` o `−` nello stesso totale. Dadi, costanti, variabili e moltiplicatori possono essere aggiunti senza digitare la formula. La stringa matematica resta la rappresentazione canonica e può essere modificata direttamente in modalità avanzata; tornando alla modalità guidata, una modifica valida viene preservata come gruppo modificabile. Sono supportate parentesi e moltiplicazioni per fattori scalari, ad esempio `(1d6+2)*3`; le regole periodiche coprono anche espressioni negative. In modalità aspetto per dado, ogni sottogruppo può avere uno stile di base distinto e il singolo dado può ancora sovrascriverlo. Il livello è modificabile direttamente oppure con i controlli level up/down.

## Gestione personaggi

La logica dati supporta duplicazione e cancellazione sicura del personaggio:

- la duplicazione copia livello, tag, stili dei dadi, modificatori, gruppi, tiri e regole di livello;
- tutti gli ID e i riferimenti tra stili, default, gruppi e tiri vengono rimappati;
- lo storico non viene copiato nel duplicato;
- la cancellazione rimuove a cascata stili, modificatori, gruppi, tiri e log appartenenti al personaggio e ricompatta l'ordine dei personaggi rimasti.

## Backup e ripristino

Dice Thrower dispone di un formato backup JSON versionato e validato. Il backup contiene personaggi, immagini portabili, stili dei dadi, modificatori, gruppi, tiri, regole di livello, storico, metadati di revisione e impostazioni. L'ID casuale dell'installazione corrente resta invece nello storage locale e non viene trasferito come identità del nuovo dispositivo durante un restore.

La schermata **Backup & Restore** è raggiungibile anche dal long-press sull'icona launcher dell'app. Export e import usano esclusivamente il document picker Android: l'utente sceglie dove salvare o da dove leggere il file e l'app non richiede permessi storage generali. Queste operazioni non usano la connessione Google opzionale.

Prima del restore vengono verificati ID duplicati, riferimenti tra personaggi/gruppi/tiri, nomi dei modificatori e formule parametrizzate. Il ripristino dei dati e delle impostazioni viene quindi scritto nello storage locale in una singola operazione.

Il formato backup v2 incorpora le immagini app-owned in Base64 e ne verifica ID, dimensione e SHA-256 prima del restore, così un personaggio ripristinato su un altro dispositivo conserva il ritratto. I backup v1 restano leggibili; i loro URI Android legacy vengono migrati in modo lazy quando il documento sorgente è ancora accessibile.

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

## Privacy, account e storage

Dice Thrower resta completamente utilizzabile in modalità **standalone** senza account: personaggi, configurazione, immagini e log restano sul dispositivo e backup/restore sono operazioni esplicitamente avviate dall'utente verso un documento scelto tramite Android. Non sono presenti pubblicità, analytics o tracking.

Al primo avvio l'utente sceglie tra standalone e **Continua con Google**. La connessione Google è opzionale e può essere aggiunta o rimossa in seguito dalle Impostazioni senza cancellare i dati locali. L'autenticazione usa Android Credential Manager; l'autorizzazione ai dati Drive è una richiesta separata e limitata al solo scope privato `drive.appdata`. Dice Thrower conserva nel `noBackupFilesDir` soltanto l'identità necessaria alla UI (ID account, email e nome visualizzato) e lo stato di connessione: password, ID token, access token e credenziali raw non vengono salvati.

La configurazione OAuth non è versionata nel repository. Le build abilitate alla connessione Google devono impostare `DICETHROWER_GOOGLE_WEB_CLIENT_ID` con il Web Client ID del progetto Google Auth Platform; senza questa variabile la modalità standalone resta disponibile e il tentativo Google mostra un errore di configurazione sicuro.

Le release usano una chiave di firma stabile dedicata a Dice Thrower (alias `dicethrower`). Il keystore non viene versionato: vedere [docs/SIGNING.md](docs/SIGNING.md) per i secret richiesti, la verifica della firma e la gestione del certificato OAuth.

Quando l'utente sceglie un'immagine, Dice Thrower ne copia i byte nello storage privato dell'app (massimo 10 MiB), calcola SHA-256 e riutilizza lo stesso asset per contenuti identici. Gli asset orfani vengono eliminati solo dopo una finestra di sicurezza di 7 giorni.

Per la sincronizzazione multi-dispositivo, ogni grafo personaggio possiede un `updatedAt`, una revisione SHA-256 del contenuto canonico, l'ID casuale non personale dell'ultimo writer e l'eventuale revisione remota comune. Questi metadati funzionano anche in modalità standalone. Un nuovo collegamento Google viene marcato come `initialReconciliationPending`: la prima sync confronta sempre dati locali e Drive prima di considerare completata la riconciliazione.

Il livello di persistenza remota usa Google Drive API v3 e soltanto l'`appDataFolder` nascosto: nessuna cartella o file Dice Thrower è visibile nel Drive dell'utente. Il formato cloud v1 separa manifest, documenti per-personaggio e immagini portabili; il manifest contiene anche tombstone delle cancellazioni e le sole preferenze scelte come roaming (visibilità/posizione del pulsante di lancio e retention del log). Tema, shake e animazioni restano specifici del dispositivo.

Quando Google è collegato, il motore offline-first sincronizza in modo non bloccante all'avvio/ripresa e su **Sincronizza ora**. Gli edit locali vengono salvati immediatamente e restano pending se Drive non è disponibile; revisioni uguali evitano download/upload inutili. Le cancellazioni si propagano solo tramite tombstone legati alla revisione nota. Una vera modifica concorrente viene stabilita tramite revisione/base comune e, per impostazione predefinita, apre un confronto locale/Drive con scelta esplicita della versione da mantenere. In alternativa ogni dispositivo può abilitare **Usa sempre la più recente**: solo dopo aver rilevato il conflitto il motore confronta `updatedAt`, poi revisione e writer ID come tie-break deterministici. Le Impostazioni mostrano stato, modifiche pending e ultima sync riuscita; **Scollega Google** conserva sia locale sia remoto, mentre **Elimina dati cloud di Dice Thrower** è un'azione distruttiva separata e confermata che rimuove solo i file appDataFolder gestiti dall'app mantenendo la copia locale. Una revoca Drive esterna riporta automaticamente alla modalità standalone. In standalone il gate rifiuta le operazioni prima di acquisire token o accedere a Drive.

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

L'app include lo stesso set di 40 lingue di Screen Lock. Tutte le locale dichiarate dispongono di risorse Android complete; inglese e italiano restano le traduzioni di riferimento per l'evoluzione del vocabolario UI. La CI verifica automaticamente che ogni locale mantenga la stessa copertura del catalogo predefinito.

## Tema

L'app supporta **Sistema / Chiaro / Scuro**. Anche il sito GitHub Pages usa tema chiaro/scuro e memorizza localmente la preferenza.

## Dadi 3D

Il risultato numerico viene sempre deciso dal motore di dadi prima della grafica. Un renderer **OpenGL ES 2.0** mostra poi dadi numerati su un tavolo scelto per personaggio, con collisioni tra dadi/bordi, senza poter alterare il risultato. Durante l'ultima fase di assestamento il renderer orienta progressivamente la faccia già scelta dal motore, così non avviene più uno snap visivo del numero al termine. I dieci preset inclusi hanno preview visuale; i sei temi fantasy più recenti sono disegnati a risoluzione nativa 1080×1920 (con preview leggere 540×960), con un piano di gioco incassato e una cornice rialzata specifica per ciascun tema, e non utilizzano più piccole miniature ingrandite; il tavolo può anche usare una foto/immagine personale (anche verticale), portabile tramite backup e sync e ritagliata senza deformazioni. La schermata di tiro usa il tavolo a pieno schermo con back, statistiche e reroll nel footer ergonomico; anche il tasto Back di Android segue la stessa gerarchia di navigazione dell'app. Per un tiro con più Roll parts compaiono soltanto i risultati nominati di ciascuna parte (es. colpire e danno), non una somma priva di significato; le singole parti sono conservate anche nei nuovi log e nel backup. Le formule accettano `2x{level}` oltre a `2*{level}`; anche il builder guidato consente di moltiplicare. Il selettore dei tavoli è ora un accordion che mostra il tavolo scelto senza occupare l'intera pagina. Il dettaglio resta disponibile nelle statistiche.

Le forme visuali includono geometrie dedicate per d2, d3, d4, d6, d10, d12, d20 e d100. Il d10/d100 usa una forma trapezoedrica e il d12 viene costruito come vero dodecaedro, duale dell'icosaedro. L'overlay mostra anche i risultati individuali e il totale.

In modalità **Edit**, ogni personaggio dispone di una libreria ordinabile di stili riutilizzabili. Uno stile combina materiale (resina lucida/opaca, metallo o gemma), colore primario e secondario; l'editor mostra un d20 3D aggiornato in tempo reale e permette di scegliere lo stile predefinito del personaggio. Rinomina e duplicazione mantengono indipendenti gli stili, mentre la cancellazione pulisce in sicurezza eventuali riferimenti salvati.

Gli stili possono anche essere copiati in blocco da un altro personaggio. La copia mantiene materiale/colori e, su richiesta, il ruolo di stile predefinito, ma assegna sempre nuovi ID al personaggio di destinazione: modifiche o cancellazioni successive non collegano mai le due librerie.

Ogni tiro può usare il predefinito del personaggio, un singolo stile per tutti i dadi, assegnazioni per dado oppure una scelta casuale per tutti i dadi insieme/per ciascun dado. Le modalità casuali possono usare tutta la libreria o un pool esplicito. Gli slot per dado seguono i componenti dell'espressione risolta: quando la formula viene modificata, le assegnazioni ancora compatibili vengono conservate e quelle obsolete eliminate, senza consumare mai il generatore casuale del risultato numerico.

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
