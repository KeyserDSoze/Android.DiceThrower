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
7. Builder dei tiri con Roll Parts indipendenti (per esempio colpire e danni), espressione testuale validata per ogni parte e Formula Composer guidato sincronizzato, dadi, costanti, variabili e moltiplicatori.
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

Il builder può dividere lo stesso tiro in **Roll Parts indipendenti**, riordinarle e definire per ogni parte un'espressione con dadi, costanti, variabili, parentesi e moltiplicatori. Ogni parte ha il proprio risultato: attacco e danno **non** vengono sommati in un totale globale. La formula si modifica direttamente dentro la singola Roll Part; il Formula Composer visuale si aggiorna subito per le espressioni valide. Selezionando un singolo termine (es. `3d6`) i controlli del builder mostrano quantità, tipo di dado/costante e segno: cambiando un valore valido, la formula testuale viene aggiornata in tempo reale, senza aggiungere un nuovo termine. Senza selezione gli stessi controlli servono ad aggiungere termini; la selezione multipla resta riservata ai raggruppamenti. I colori di testi e icone seguono automaticamente il tema chiaro/scuro. Espressioni non valide impediscono il salvataggio senza cancellare la bozza. L'espressione canonica generale viene generata automaticamente solo per storage, backup e risoluzione dei tiri, senza un secondo campo editabile. Le regole periodiche supportano anche espressioni negative e ogni Roll Part può mantenere il proprio stile visivo dei dadi.

## Personalizzazione dei dadi (catalogo iniziale)

Dalla **lista dei personaggi → Personalizza i dadi** puoi aprire un punto unico di gestione, scegliere il personaggio e vedere/modificare i suoi stili. Sono disponibili **10 preset premium** con materiali e colori differenti: Blu Arcano, Forgia Infernale, Etere Smeraldo, Stella Viola, Runa di Ossidiana, Gelo Lunare, Ottone Solare, Perla Celestiale, Oceano Profondo e Rame Antico. Il preset scelto viene copiato come stile **modificabile**, con ID indipendente; è immediatamente disponibile per la scelta nelle singole Roll Parts e per il dado del candidato B. Puoi anche crearne da zero.

Questa prima implementazione conserva gli stili associati a ciascun personaggio, così come il backup e la sincronizzazione esistenti. La **libreria condivisa di stili utente con ID globali e assegnazioni A/B avanzate** è uno sviluppo distinto tracciato dalla [EPIC #138](https://github.com/KeyserDSoze/Android.DiceThrower/issues/138), non ancora completato. Le grafiche non influiscono sull'estrazione dei numeri.

Nel doppio tiro con più Parts, quando è attiva l'assegnazione di stili a ogni Part, il candidato B **conserva la palette specifica della Part** e la combina con l'accento/materiale del secondo tiro. Non trasforma più tutti i dadi B in un'unica grafica identica: si distinguono sia la Part sia il gruppo A/B. La scelta resta esclusivamente grafica e non cambia i valori estratti.

## Esperienza cinematica del Roll

In **Modifica Roll → Effetti visivi del tiro** puoi scegliere un profilo specifico per ogni tiro: **Bilanciato** (default consigliato), **Discreto** oppure **Spento**. Puoi poi personalizzare separatamente badge del nome effetto, due aree alto/basso per i candidati del doppio tiro, glow del candidato scelto, aura runica, particelle (scintille per Bonus, cenere per Malus), effetti per tipo di azione, animazione numerica dei risultati e lieve impatto della camera. Modificare un interruttore crea un profilo Personalizzato. La preferenza globale per le animazioni ha precedenza e non viene sovrascritta.

Nel BEST/WORST i gruppi A/B restano in una zona centrale più compatta del tavolo (eventualmente in due corsie se attivate nel Roll). In **Modifica personaggio → Stili dadi** si sceglie il look del secondo tiro: per impostazione predefinita è **Contrasto automatico**, con colori e materiale diversi anche sui personaggi nuovi senza stili personalizzati; in alternativa si seleziona uno stile già creato per il personaggio. Il primo tiro conserva gli override visivi del Roll. Non appaiono più etichette A/B/Chosen sul tavolo; **durante il volo tutti i dadi restano luminosi**. Solo dopo l'assestamento il gruppo scelto brilla e quello non scelto si attenua. Il motore sceglie sempre i numeri, indipendentemente dal renderer; BEST/WORST/Normale non possono essere toccati durante un tiro. Ogni effetto attivato mostra un singolo simbolo e il proprio nome, mentre rune, flash soft e particelle caratterizzano ADD/SUBTRACT/MULTIPLY/REPLACE/REROLL/ROLL_AFTER. Un effetto aritmetico può mostrare la transizione originale → finale della Part, senza un secondo tiro numerico.

Queste preferenze sono incluse nel backup JSON v15, nella duplicazione del personaggio e nella sincronizzazione Drive facoltativa. I Roll provenienti da backup precedenti ricevono automaticamente il profilo Bilanciato e mantengono il contenuto; il loro hash di sync resta invariato se non personalizzati. Tutte le nuove etichette sono disponibili nelle 40 lingue dell'app. Gli effetti grafici sono ottimizzati con limiti per le fasi e non richiedono una connessione.

L'editor degli effetti ora presenta un flusso semplice **QUANDO → ALLORA** per le regole comuni con una condizione e un'azione. La Part che attiva l'effetto e la Part modificata si scelgono separatamente, insieme ad ambito (solo dadi, modificatori o totale), operatore e valore. La sezione **Opzioni avanzate** rimane disponibile; le regole preesistenti con gruppi AND/OR, più azioni, variabili o formule complesse si aprono direttamente nella vista avanzata, senza perdere dati. Il confronto `Solo dadi` su una Part con più dadi è ancora la loro **somma**, non una condizione sulla singola faccia: il relativo rework è in #145.

## Double Roll ed Effects (bonus/malus)

Ogni Roll può abilitare il **doppio tiro Best/Worst**; alla creazione la prima Roll Part è selezionata per il confronto, mentre le altre sono opzionali e si attivano individualmente. Quando più Parts sono selezionate, il confronto migliore/peggiore usa **la loro somma per scegliere il gruppo vincente**, ma i risultati delle singole Parts rimangono visibili e distinti. Swipe a destra = Best, swipe a sinistra = Worst, swipe verso l'alto = tiro normale. I relativi pulsanti del footer permettono di scegliere esplicitamente la modalità anche senza gesture; il normale pulsante del dado resta all'estremità destra. I dadi non selezionati per il risultato rimangono consultabili e sono visivamente attenuati.

Per il caso comune del critico, quando un Roll ha almeno due Parts è disponibile **Colpo critico (configurazione rapida)**: crea una condizione sul risultato dei **soli dadi della prima Part** uguale a 20 e moltiplica per 2 **solo i dadi della seconda Part**, lasciando invariati i modificatori. Esempio: Colpire `1d20+5` mostra 20 naturale; Danno `2d6+4` con dadi somma 7 diventa `14+4=18`. Soglia, Parts coinvolte e azione sono modificabili nell'editor. Se la prima Part contiene più dadi, la condizione attuale confronta la loro **somma**, non la singola faccia del d20: evitare di interpretarla come critico su una singola faccia; il filtro per faccia individuale è previsto nel rework [#139](https://github.com/KeyserDSoze/Android.DiceThrower/issues/139).

Nell'editor del Roll, sotto le Parts, si possono aggiungere **Effects di tipo Bonus e Malus** senza imporre le regole di un gioco specifico. Ogni effetto ha un ordine, può bloccare quelli successivi quando si attiva, e supporta gruppi di condizioni in OR con condizioni interne in AND. Le soglie possono usare valori dei soli dadi, dei soli modificatori o del totale, confronti `>=`, `<=`, `>`, `<`, `=` e `!=`, e riferimenti stabili alle Parts. Le azioni supportano somma, sottrazione, moltiplicazione, sostituzione, reroll e Roll After; le formule comprendono `f(x)` (floor), `c(x)` (ceil), `r(x)` (arrotondamento dei mezzi lontano dallo zero) e le variabili del personaggio.

I numeri vengono sempre stabiliti dal **motore logico**, non dalla fisica 3D. Durante le animazioni, gli eventuali dadi di Reroll/Roll After entrano sul tavolo dopo quelli originali. Lo storico locale registra per ogni effetto le condizioni, i passaggi e i risultati originali/finali delle Parts: un malus non altera in retrospettiva il lancio originale. Il backup JSON e la sync Drive opzionale conservano anche gli Effects e le loro tracce, mantenendo compatibilità con gli archivi precedenti. La schermata e le sue istruzioni sono localizzate nelle 40 lingue Android supportate.

## Roll disponibili dal livello minimo

Nell'editor di ogni Roll puoi impostare **Dal livello** (da 1 a 9999). Il valore predefinito è 1, così tutti i Roll precedenti mantengono lo stesso comportamento. Un Roll preparato per un livello futuro viene **nascosto solo in modalità Usa**, sia in dashboard sia nei gruppi, fino al raggiungimento della soglia. Salendo o scendendo di livello compare o scompare automaticamente; non viene mai cancellato. In **Modifica** resta sempre presente e modificabile. La disattivazione manuale del Roll è indipendente dal livello, e le regole di progressione che cambiano i dadi restano invariate.

La soglia è inclusa nei backup locali, nei dati sincronizzati opzionalmente via Google Drive e nella duplicazione del personaggio; i vecchi backup senza il campo vengono interpretati come livello minimo 1. Il cambio di livello che rende il tiro corrente non disponibile riporta l'utente alla schermata del personaggio senza eseguire tiri nascosti.

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

Il risultato numerico viene sempre deciso dal motore di dadi prima della grafica. Un renderer **OpenGL ES 2.0** mostra poi dadi numerati su un tavolo scelto per personaggio, con collisioni tra dadi/bordi, senza poter alterare il risultato. Durante l'ultima fase di assestamento il renderer orienta progressivamente la faccia già scelta dal motore, così non avviene più uno snap visivo del numero al termine. I dieci preset inclusi hanno preview visuale; i sei temi fantasy più recenti sono disegnati a risoluzione nativa 1080×1920 (con preview leggere 540×960), con un piano di gioco incassato e una cornice rialzata specifica per ciascun tema, e non utilizzano più piccole miniature ingrandite; il tavolo può anche usare una foto/immagine personale (anche verticale), portabile tramite backup e sync e ritagliata senza deformazioni. La schermata di tiro usa il tavolo a pieno schermo con back, statistiche e reroll nel footer ergonomico; anche il tasto Back di Android segue la stessa gerarchia di navigazione dell'app. Per un tiro con più Roll parts compaiono soltanto i risultati nominati di ciascuna parte (es. colpire e danno), non una somma priva di significato; le singole parti sono conservate anche nei nuovi log e nel backup. Le formule accettano `2x{level}` oltre a `2*{level}`; anche il builder guidato consente di moltiplicare. Il selettore dei tavoli è ora un accordion che mostra il tavolo scelto senza occupare l'intera pagina. Il dettaglio resta disponibile nelle statistiche, da cui si può anche aprire direttamente la modifica del Roll oppure aumentare/diminuire il livello del personaggio senza uscire dal tavolo.

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


### Double-roll controls (development branch / #107)

New Rolls enable optional double rolls with only the first Part selected initially. In the Roll editor each additional Part can be included or excluded. On the dice table use **left swipe for WORST**, **right swipe for BEST**, and **up swipe for NORMAL**; the directional shortcuts can be switched off in Settings. The bottom controls keep NORMAL at the far right, with colored BEST and WORST dice beside it. BEST/WORST compare the summed *complete* values of the participating Parts across the two candidate groups, while other Parts roll once; the result view keeps each Part separate and the statistics show both combinations, with the alternative dimmed.
