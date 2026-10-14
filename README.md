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
5. Catalogo condiviso di stili dei dadi, indipendente dai personaggi, con materiali, due colori, anteprima 3D e assegnazioni per personaggio/Roll Part.
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

Nella pagina **Personalizza i dadi**, i dieci preset di sistema immutabili sono elencati in una colonna con campioni colore, materiale e preview 3D ravvicinata. Duplicare un preset crea un custom globale modificabile. Nel **personaggio → Stili dadi**, la sezione è chiusa per impostazione predefinita e permette di selezionare lo stile principale o quello per il candidato B. Il pulsante Aggiungi mostra le scelte di sistema e custom solo su richiesta.

## Personalizzazione dei dadi (catalogo iniziale)

Dalla **lista dei personaggi → Personalizza i dadi** puoi gestire i tuoi stili anche senza aver creato personaggi. Dieci preset premium restano immutabili; gli stili custom si possono creare, modificare, duplicare, riordinare ed eliminare. I loro ID globali sono riutilizzabili in più personaggi e Roll Parts. Eliminare uno stile in uso riassegna automaticamente tutti i riferimenti a un altro custom disponibile, altrimenti a un preset di sistema.


Nel doppio tiro con più Parts, quando è attiva l'assegnazione di stili a ogni Part, il candidato B **conserva la palette specifica della Part** e la combina con l'accento/materiale del secondo tiro. Non trasforma più tutti i dadi B in un'unica grafica identica: si distinguono sia la Part sia il gruppo A/B. La scelta resta esclusivamente grafica e non cambia i valori estratti.

## Esperienza cinematica del Roll

In **Impostazioni → Effetti visivi per tutti i tiri** scegli **un solo profilo valido per tutti i Roll**: **Bilanciato** (default consigliato), **Discreto** oppure **Spento**. Puoi poi personalizzare separatamente badge del nome effetto, due aree alto/basso per i candidati del doppio tiro, glow del candidato scelto, aura runica, particelle (scintille per Bonus, cenere per Malus), effetti per tipo di azione, animazione numerica dei risultati e lieve impatto della camera. Modificare un interruttore crea un profilo Personalizzato. La preferenza globale per le animazioni ha precedenza e non viene sovrascritta.

Nel BEST/WORST i gruppi A/B restano in una zona centrale più compatta del tavolo (eventualmente in due corsie se attivate nel Roll). In **Modifica personaggio → Stili dadi** si sceglie il look del secondo tiro: per impostazione predefinita è **Contrasto automatico**, con colori e materiale diversi anche sui personaggi nuovi senza stili personalizzati; in alternativa si seleziona uno stile di sistema o un custom del catalogo globale. Il primo tiro conserva gli override visivi del Roll. Non appaiono più etichette A/B/Chosen sul tavolo; **durante il volo tutti i dadi restano luminosi**. Solo dopo l'assestamento il gruppo scelto brilla e quello non scelto si attenua. Il motore sceglie sempre i numeri, indipendentemente dal renderer; BEST/WORST/Normale non possono essere toccati durante un tiro. Ogni effetto attivato mostra un singolo simbolo e il proprio nome, mentre rune, flash soft e particelle caratterizzano ADD/SUBTRACT/MULTIPLY/REPLACE/REROLL/ROLL_AFTER. Un effetto aritmetico può mostrare la transizione originale → finale della Part, senza un secondo tiro numerico.

I controlli cinematici globali sono salvati in **AppSettings** e inclusi in backup/restore JSON con default sicuro Bilanciato per vecchie installazioni. Valgono su tutto il dispositivo, anche per i Roll già creati; come l'interruttore generale delle animazioni, **non** partecipano al roaming Settings di Google Drive (le preferenze visuali sono locali). I vecchi campi per-Roll continuano a essere letti nei backup esistenti ma non prevalgono più sul profilo globale, evitando cancellazioni dei dati. Anche **Modifica Roll**, sia dal tavolo sia dal personaggio/gruppo, ora usa uno schermo opaco con tema dark/light, header e navigazione coerenti. Le nuove stringhe sono presenti per le 40 lingue Android. Gli effetti grafici non richiedono una connessione.

L'editor guidato degli Effects supporta anche **Ritira (Reroll)**, che rilancia davvero i dadi e sostituisce il risultato precedente, e **Tiro aggiuntivo (Roll After)**, che lancia altri dadi e li somma al bersaglio. In entrambi i casi, lasciando vuota l'espressione usa i dadi definiti nella Part selezionata; una formula specifica consente dadi diversi. **Sostituisci (Replace)**, invece, inserisce un valore numerico senza lanciare dadi nuovi. Bonus e Malus sono le due categorie degli Effects; il vecchio pulsante rapido «Colpo critico» è stato rimosso dalla UI, senza eliminare le regole esistenti o impedire di configurare manualmente un critico.

L'editor degli effetti ora presenta un flusso semplice **QUANDO → ALLORA** per le regole comuni con una condizione e un'azione. La Part che attiva l'effetto e la Part modificata si scelgono separatamente, insieme ad ambito (solo dadi, modificatori o totale), operatore e valore. La sezione **Opzioni avanzate** rimane disponibile; le regole preesistenti con gruppi AND/OR, più azioni, variabili o formule complesse si aprono direttamente nella vista avanzata, senza perdere dati. Il confronto `Solo dadi` su una Part con più dadi è ancora la loro **somma**, non una condizione sulla singola faccia: il relativo rework è in #145.

Nel modulo **Modifica Roll**, il comando Doppio tiro si trova nella scheda principale, prima del gruppo, con una spiegazione: i tiri BEST/WORST confrontano due esecuzioni delle Parts selezionate, mentre il tiro normale resta singolo. È una proprietà del Roll, non un'opzione nascosta vicino alla formula.

Ogni **Roll Part** può ora essere contrassegnata come **non inclusa nel tiro normale**. La Part resta configurata, con nome, espressione e stile, ma i suoi dadi non si lanciano all'inizio: una condizione Bonus/Malus può utilizzare **Ritira (Reroll)** o **Tiro aggiuntivo (Roll After)** sulla Part per generare quei dadi soltanto quando serve. Il tiro iniziale deve contenere almeno una Part attiva e solo quelle attive possono far parte del confronto A/B. Il risultato della Part di supporto compare nei risultati e nel log quando l'effetto si attiva, senza influenzare la casualità del tiro iniziale.

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
- eliminare un personaggio rimuove modificatori, gruppi, tiri e log di quel personaggio, ma non cancella gli stili custom globali usati altrove.

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

In modalità **Edit** ogni personaggio sceglie, mediante accordion, il proprio aspetto dei dadi da una libreria globale. Gli stili custom si modificano nella schermata **Personalizza i dadi** e si aggiornano visivamente ovunque siano assegnati.

Non è necessario copiare stili tra personaggi: l'identità del custom è condivisa, anche quando lo si assegna al tiro A/B o a una singola Roll Part.

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


### Catalogo dadi condiviso (vNext)

La schermata **Personalizza i dadi** gestisce gli stili personalizzati indipendentemente dai personaggi, anche quando non esistono personaggi. I dieci stili di sistema sono immutabili e utilizzabili direttamente; un preset può essere duplicato come nuovo stile personalizzato. Ogni personalizzazione salvata ha un ID stabile e può essere assegnata a più personaggi, al candidato B e alle singole Roll Parts senza copiarne la definizione. Una modifica ai colori/materiali si riflette su tutte le assegnazioni. Eliminando uno stile personalizzato vengono aggiornati atomicamente tutti i riferimenti al primo altro stile custom oppure al preset di sistema predefinito.

I vecchi stili legati ai personaggi sono migrati senza cambiare ID; backup JSON v15 e successivi restano leggibili. La sincronizzazione opzionale salva una revisione/hash separata del catalogo in Drive appDataFolder; eventuali divergenze reali sono sottoposte alla policy di conflitto scelta. Il catalogo resta sempre disponibile offline e non influenza il RNG. Il QA visivo su dispositivo fisico rimane necessario prima della prossima release.


### Web character workshop and sharing

The [GitHub Pages character workshop](https://keyserdsoze.github.io/Android.DiceThrower/workshop/) provides a browser-based, local-first editor (IndexedDB, without account), ten optional starter characters including D&D 5e 2014 Fighter, Rogue, Wizard, Ranger, Cleric, Paladin and Bard presets, and explicit export/share actions. A link or QR contains a compressed, versioned copy of **one** character; it opens a web preview with an **Open in Android app** action for importing into Dice Thrower. Only the character, variables, groups, roll formulas/parts, level rules/effects and custom dice style data are shared, not roll history, cloud metadata or images. Treat links as public to recipients: anyone with the URL/QR can inspect the shared character. If the link is too big for reliable QR, export a JSON file instead. Browser data may be erased by clearing site storage. Google Drive synchronization for the web editor is planned, **not yet available**.

#### Share directly from Android
Open a character in Dice Thrower, tap the share icon in its top bar, then use the QR code, **Share link**, or **Copy link**. These contain a portable v1 character snapshot, not a hosted record, so recipients can preview it on the GitHub Pages workshop and explicitly import it on Android. For a character too large for a reliable QR, choose **Export character JSON**. Use **Import character** (document icon) in the Android Characters header to restore that one character from its JSON file; this is *not* the full app backup format. Existing characters are never overwritten. Photos, roll history, cloud metadata and credentials are deliberately excluded, and recipients can read any fields in the shared character.

#### Guided character rule editing on the website
The [web Character Workshop](https://keyserdsoze.github.io/Android.DiceThrower/workshop/) shows the same named Roll groups as the Android app, including a grouped QR preview and per-group editor; it lets players edit bonuses and maluses with activation groups (OR of AND conditions), Part/roll/variable comparisons, and add/subtract/multiply/replace/reroll/roll-after actions. It also offers per-Roll Part and per-die style assignments, all five native appearance modes, custom/system styles, and initial/double-roll (A/B) inclusion toggles. Advanced native JSON is still available under an optional collapsed expert section for compatibility with existing imported records. The logical dice result and effects remain owned by the Android engine; the browser is an offline-first configuration editor, not a dice evaluator.

**Starter character correction (D&D 5e):** The level-one Fighter template contains one **Longsword** Roll with separate **Attack** (`1d20 + Strength + Proficiency`) and **Damage** (`1d8 + Strength`) Parts. A natural 20 on the attack d20 (dice only, independent of modifiers) triggers **ROLL_AFTER 1d8** on the Damage Part, giving `2d8 + Strength` from two independent d8 samples. This follows both 2014 and 2024 D&D 5e critical-hit rules; it deliberately does not multiply the first die or Strength. The generic fantasy Ranger's Bow and Paladin's Sword are also organized as paired Parts without assuming D&D rules. Previously saved unmodified starter weapon pairs can be explicitly upgraded using **Update old starter weapon rolls** in the web editor; customized rules are never silently replaced. See the [2014 Basic Rules](https://www.dndbeyond.com/sources/dnd/basic-rules-2014/combat).

### Character link/QR preview and Android handoff
A QR or share URL opens the public **web Character Workshop** as an import preview, rather than silently adding data. Recipients see a card with the character's name, original level, groups, rolls and Roll Parts. Before confirming they can choose a different name and level; the sender's document never changes. **Add to my browser** saves a separate IndexedDB copy with fresh IDs. **Open in Android app** sends that chosen copy through `dicethrower://import?data=v1...`; the Android application presents its **own** import preview with an editable name/level and explicit confirmation. The web editor also exposes a direct Android button for a valid current character even when no QR has been generated. The Android JSON document picker follows the same approval path. Uninstalling the app or using an old version means the app handoff may not work; the JSON export/import remains available. HTTPS links/QR open the **web preview first** (we do not claim verified Android HTTPS App Links). No server-side character record, sign-in or automatic import is introduced.

#### Bard progression (D&D 5e 2014; character begins at level 1)

The opt-in **Orin · Level 1 Bard** 2014 preset includes a Rapier Attack/Damage pair with a dice-only d8 natural-20 critical, three example CHA-proficient skills, DEX/CHA proficient saves and automatically progressing proficiency. **Vicious Mockery** is a Wisdom save cantrip with 1d4/2d4/3d4/4d4 damage-only options unlocked at levels 1/5/11/17; there is **no** attack roll or critical. Only apply damage when the target fails the saving throw; its disadvantage rider is adjudicated by the player.

**Bardic Inspiration** appears as four mutually exclusive ally-only d6/d8/d10/d12 Rolls gated at levels 1/5/10/15, not an automatic bonus on the bard's normal attack or CHA modifier. **Song of Rest** uses a conditional extra d6/d8/d10/d12 healing Roll at levels 2/9/13/17, only when eligible creatures spend Hit Dice at a short rest. **Jack of All Trades** is a dedicated example of an *unproficient* WIS ability check; its half-proficiency rounded down scales at levels 2/9/17 and is never added to proficient checks. Optional **Healing Word** upcast examples (1–9) are provided with CHA added exactly once, but only if the player knows the spell and expends the slot.

The EN/IT 1–20 class timeline includes known spells/cantrips, slots, skill choices, College and Magical Secrets, which are always player-selected. Resource use and spell selection are manual. Saved generic adventure Bard documents are not automatically converted. Unmodified new Bard templates can use a version-pinned `#template=bard2014v1` share reference only when full QR is too large; customized ones must export full data or JSON. No Android model, backup or Drive changes. See [Bard 2014 SRD](https://5e.d20srd.org/srd/classes/bard.htm) and [Vicious Mockery](https://5e.d20srd.org/srd/spells/viciousMockery.htm).

#### Paladin progression (D&D 5e 2014; character begins at level 1)

The opt-in **Thane · Level 1 Paladin** 2014 preset contains a one-handed Longsword with paired Attack and Damage Parts (`1d20+Strength+Proficiency`, `1d8+Strength`) and an independent natural-20 dice-only critical. At **level 5**, a second Roll offers Extra Attack with two independent Attack/Damage pairs and individual natural-20 effects. At **level 11**, Improved Divine Smite adds `1d8` radiant per successful melee weapon hit, so the new paired Rolls use `2d8+Strength` per hit and reroll an additional **2d8**, never an extra Strength modifier, on a critical. Level 11 also has a single-attack variant. Earlier Roll variants remain available, and users should choose the highest relevant version.

**Divine Smite** is optional after a successful melee weapon hit (available from Paladin level 2), and costs an available spell slot. Prepared Rolls cover slot levels 1–5 at class levels 2/5/9/13/17: normal 2d8/3d8/4d8/5d8/5d8; vs. undead/fiend add another d8 (max 6d8); critical variants double **all smite dice**, not weapon dice again or Strength. Select **exactly one** conditional Divine Smite damage Roll per qualifying hit. Improved Divine Smite at level 11 already appears in the weapon presets: do not roll it a second time. The 20-row EN/IT table gives slot counts and Lay on Hands healing pool (5×class level) for manual resource tracking. Proficiency grows at 5/9/13/17. Fighting Style, class oath, ASI, aura effects, Lay on Hands allocation, spell preparation and slot spending are never automated.

Saved generic Paladin presets are not rewritten. Unmodified official Paladin examples can use a short version-pinned `#template=paladin2014v1` recipe for large QR payloads; edits must travel as full JSON or self-contained links. No model, backup, Drive, or renderer change. See the [2014 Paladin SRD](https://5e.d20srd.org/srd/classes/paladin.htm).

#### Cleric progression (D&D 5e 2014; character begins at level 1)

The optional **Sol · Level 1 Cleric** 2014 preset includes a **Mace** Attack/Damage pair with a dice-only natural-20 critical (one additional d6, not Strength), spell attack and proficient Wisdom/Charisma saves, and representative Insight/Religion/Medicine skills (Religion is Intelligence-based). Proficiency advances automatically at character levels 5/9/13/17.

**Sacred Flame** is a saving throw spell, not a spell attack: its 1d8/2d8/3d8/4d8 damage-only presets unlock at levels 1/5/11/17, and it **cannot critical**. Healing Word and Cure Wounds have preconfigured, optional spell-slot variants 1–9 available at class levels 1/3/5/7/9/11/13/15/17: slot level N heals `Nd4+Wisdom` or `Nd8+Wisdom`, respectively. These examples are not automatically chosen or prepared. The included optional **Guidance** and **Bless** d4 Rolls apply only if the player knows/prepares and uses the relevant spell in an eligible situation; they do not activate unconditionally. Spell save DC is 8 + Wisdom + proficiency, not an RNG check.

The bilingual level-1–20 Cleric timeline includes spell slots, cantrips known, Divine Domain, Channel Divinity and Divine Intervention reminders. Domain choices, spell preparation, resource expenditures and ability-score improvements are **not** automatically decided/tracked. Existing saved generic fantasy Clerics are not modified. Unedited official starter presets can use a version-pinned `#template=cleric2014v1` QR reference when necessary; custom versions require full share data or JSON export. No Android backup/storage migration. Reference: [2014 Cleric SRD](https://5e.d20srd.org/srd/classes/cleric.htm).

#### Ranger progression (D&D 5e 2014; character begins at level 1)

The optional **Bryn · Level 1 Ranger** 2014 starter includes a Bow Roll with Attack (`1d20+Dexterity+proficiency`) and Damage (`1d8+Dexterity`) Parts. A natural 20 adds one fresh d8 but never doubles Dexterity. **Extra Attack** unlocks a separate Bow Roll at **level 5** with two individual Attack/Damage pairs and independent natural-20 crits. The proficiency increase at levels 5/9/13/17 automatically affects sample skills, STR/DEX saves, bow attacks and spell attacks.

**Hunter's Mark** is shown as an optional `1d6` on an eligible hit or `2d6` on a critical hit (do not use both). It does *not* activate on every hit: the ranger must know and cast the spell, maintain concentration and hit its marked target. Spellcasting becomes available at level 2, and optional **Cure Wounds** examples are ready for slots 1–5 at Ranger levels 2/5/9/13/17, healing `Nd8+Wisdom` for slot level N. All spell choices, resources, concentration, Fighting Style, Favored Enemy, Natural Explorer, archetype and ASI remain player decisions. The bilingual timeline shows the official 2014 class features and spell-slot counts through level 20; the app is **not** a spell-slot tracker.

Existing saved generic fantasy Ranger documents, including historical weapon-pair migrations, are untouched. Like Fighter/Rogue/Wizard, a stock unchanged Ranger can be shared via a pinned `#template=ranger2014v1` QR recipe if the self-contained snapshot is oversized; user-edited presets never lose fields. No Android schema, backup or Drive migration is needed. Source: [Ranger 2014 SRD](https://5e.d20srd.org/srd/classes/ranger.htm).

#### Compact Wizard spell presets and bounded level progression (#200)

The optional new **Wizard v3** starter replaces repeated slot-by-slot entries for **Magic Missile, Burning Hands, Thunderwave, Fireball and Lightning Bolt** with a fixed **base spell** and one independent **highest unlocked spell-slot (optional)** Roll. For example, Magic Missile with a **1st-level slot is always 3 darts**, even for a level-20 Wizard. Its optional high-slot shortcut starts from 3d4+3 and adds an independently rolled 1d4+1 at Wizard levels 3/5/7/9/11/13/15/17, capped at **11 darts**. It does **not** spend a slot, select a target or imply that the highest slot is used. The player chooses the shortcut deliberately, or the original base spell, and handles other slot levels manually. Scorching Ray still has separate variants where necessary because each ray has its own attack and critical effect.

The generic `EVERY_LEVELS` rule now accepts optional `startLevel` and `maxRepeats` fields: one addition at its first specified level, then every N character levels, up to the configured maximum. Omitting both fields preserves the historical `floor(level / N)` behavior. Android and the web editor expose both optional parameters, and backup, QR, stored characters and optional sync use the same existing model/JSON envelope with default-safe decoding. `wizard2014v1` and `wizard2014v2` share recipes remain version-pinned to their original models; v3 uses `wizard2014v3`. Previously saved personalized Wizards are not replaced or migrated.

#### Wizard progression (D&D 5e 2014; character begins at level 1)

The optional **Lyra · Level 1 Wizard** 2014 starter preconfigures Wizard spellcasting examples through character level 20. **Fire Bolt** contains Attack/Damage Parts with separate tier-specific Rolls unlocked at levels **1/5/11/17** (`1d10/2d10/3d10/4d10`). Each version carries its own natural-20 effect that rolls another matching set of damage dice; there is no INT damage bonus for Fire Bolt. Select the highest available version at your current level (earlier variants remain in the list as alternatives). Proficient checks and saves use the game-neutral milestone proficiency expression.

For a second usable spell example, **Magic Missile** has nine optional Roll presets, gated when slot levels 1–9 are available (Wizard levels 1/3/5/7/9/11/13/15/17). Each has `slot level + 2` individually visible `1d4+1` Dart Parts, which can be assigned to targets; **Magic Missile never makes an attack roll and never crits**. Some 2014 tables resolve the darts using one shared d4 instead of separately rolled dice; the player should use one dart result and apply it to all darts under that ruling. Choosing/learning/preparing the spell is a player decision, as is spending slots. The EN/IT level-1–20 Wizard table shows proficiency, cantrips known, spell slots and class features; choices such as Arcane Tradition, ASI, Spell Mastery or Signature Spells are intentionally not fabricated.

**Wizard v2 groups and spell library:** New opt-in Lyra templates now contain the native group structure **Cantrips, Spell Lv 1–9, Skills & saves**. The 2014 SRD reference lists the complete official Wizard SRD spell names (not every proprietary PHB spell), while ready-to-use Rolls cover common dice-based cantrips and spells from levels 1 through 9. Attack spells have separate Attack/Damage Parts and dice-only criticals, saving-throw spells roll damage only (no critical), and spells such as Shield, Invisibility and Wish have **no invented numeric Rolls**. Common Fireball, Burning Hands, Thunderwave and Lightning Bolt upcasts have selectable slot variants; Scorching Ray has independent attack/damage/critical per ray. Casting a spell, choosing it for the spellbook/prepared list, choosing the saving-throw outcome, concentration and spending a slot always require explicit player decisions.

The web preview and editor use the exact same native `RollGroup` identifiers as Android, displaying collapsible sections with roll counts; users can add or reassign rolls to any group. The web provides a separate collapsible complete spell **reference catalogue** and the site uses an SVG favicon derived from the Android launcher symbol on all routes. The catalog is not persisted as fake Rolls. Saved generic Mage and Wizard v1 characters are not rewritten. Existing pinned `#template=wizard2014v1` URLs still instantiate the original version; a pristine new template uses `#template=wizard2014v2` if the snapshot exceeds QR capacity. The large current Wizard preset also exceeds the safe Android deep-link limit, so the site offers a **JSON file export/import** rather than an unsafe app-direct link. Customize manually, and use full v1 portable JSON to preserve independent backups. No Android storage/backup/Drive schema migration. Source: [2014 Wizard spell list](https://5e.d20srd.org/srd/spellLists/wizardSpells.htm).

#### Rogue progression (D&D 5e 2014; character begins at level 1)

The optional **Nim · Level 1 Rogue** D&D 5e (2014) preset is prepared for character levels **1–20**. Dagger is a single Roll containing **Attack** (`1d20 + Dexterity + proficiency scaling`) and **Damage** (`1d4 + Dexterity`). Only a natural d20 20 adds an independent `1d4` damage die; Dexterity is never doubled. Proficiency grows automatically at levels 5/9/13/17; skill and saving-throw bonuses follow the selected example proficiencies. **Sneak Attack** is a separate, deliberately optional damage-only Roll: 1d6 at level 1, +1d6 at levels 3/5/7/9/11/13/15/17/19, capped at 10d6 at level 19; a separate **Sneak Attack critical** Roll produces exactly twice the dice when the attack is both a critical hit **and** Sneak Attack is eligible. Do not throw both sneak rolls. These rolls never grant Sneak Attack eligibility or enforce its once-per-turn usage; the player adjudicates conditions, Expertise skills, ASIs, feat/subclass choices and Reliable Talent manually. The Wisdom saving throw gained through Slippery Mind is gated to level 15. A bilingual level-1–20 timeline explains feature unlocks without inventing dice for non-roll features.

Previously saved generic fantasy Rogue copies are **not automatically converted**; use the new opt-in preset to keep existing customizations. As with Fighter, oversized *unmodified* Rogue preset shares can use a pinned `#template=rogue2014v1` QR recipe; custom characters must use the complete token if it fits or JSON export otherwise. Native import/backup format remains v1, with no storage-schema migration. Reference: [2014 Rogue class](https://www.dndbeyond.com/classes/12-rogue).

#### Fighter progression (D&D 5e 2014; character begins at level 1)
The website's Fighter starter prepares the 2014 class's **20-level** roadmap and keeps existing local play standalone. The logical dice engine supports a reusable, game-agnostic numeric placeholder `{levelstep:5:9:13:17}` (0 at level 1–4; 1 at 5–8; 2 at 9–12; 3 at 13–16; 4 at 17+). This increases proficient attack and saving-throw formulas from a base +2 to +3/+4/+5/+6 on leveling automatically. Second Wind already scales as `1d10+{level}`. The template includes level-gated Longsword Attack action variants at level 5 (2 attacks), level 11 (3 attacks), and level 20 (4 attacks), with **each attack's own natural-20 critical** adding another independently sampled 1d8 to only that attack's damage. These are separate named roll configurations unlocked when the Fighter levels up; the player chooses the appropriate attack-action preset. Short/long-rest resource uses are not automatically tracked; subclass, Fighting Style, feat, and Ability Score Improvement choices are not invented. See the 2014 Fighter table in the [Wizards Basic Rules PDF](https://media.wizards.com/2014/downloads/dnd/PlayerDnDBasicRules_v0.2_PrintFriendly.pdf). Existing character data is not silently migrated.

**QR size handling for the full 20-level Fighter:** a standard single QR cannot reliably contain an arbitrarily large native character snapshot. For an **unchanged versioned official Fighter 2014 template** (recipient-selected name/level are supported), the workshop can emit a compact `#template=fighter2014v1&name=…&level=…` link. The HTTPS workshop locally reconstructs precisely this pinned starter model, shows a preview and opens the Android app with the normal full v1 import token after explicit user action. This requires the workshop's matching version to remain deployed; it is a **versioned template reference**, unlike the completely self-contained `#character=v1.…` links. Any customized rolls, rules, variables or styles fail the strict template fingerprint and are **never discarded** to fit a QR: use JSON file import for oversized custom characters. No Dice Thrower sharing backend, hosting, account or uploads are introduced.

**SRD 5.1 attribution (CC BY 4.0):** This work includes material taken from the System Reference Document 5.1 (“SRD 5.1”) by Wizards of the Coast LLC and available at https://dnd.wizards.com/resources/systems-reference-document. The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License available at https://creativecommons.org/licenses/by/4.0/legalcode.
