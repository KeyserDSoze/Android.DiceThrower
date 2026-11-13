# Test Android end-to-end

L'app dispone di test nativi con un flusso simile a Playwright: aprono l'Activity reale,
navigano, compilano campi, fanno click, aspettano condizioni osservabili e verificano
interfaccia e dati salvati. Non richiedono un server Appium o Maestro.

## Stack e file

- **Compose UI Test**: selettori semantici, input e asserzioni; per i nuovi E2E si usa l'API JUnit v2.
- **ActivityScenario**: avvio e ricreazione dell'Activity. Il test di orientamento cambia anche
  realmente portrait/landscape, verificando che Android abbia creato un'altra Activity.
- **Espresso**: Back di sistema, non soltanto il pulsante Back dell'app.
- **UiAutomation**: screenshot dell'intero display e diagnostici nativi, inclusa la Surface OpenGL.

Scenari: `src/app/src/androidTest/java/com/keyserdsoze/dicethrower/e2e/RollJourneyE2ETest.kt`.
Fixture e diagnostici: `NativeAppE2ERule.kt` nella stessa directory.
Runner locale/CI: `scripts/run-android-ui-tests.sh`.

## Scenari coperti

1. Home → personaggio → gruppo → tiro normale → ricreazione: stesso risultato e una sola voce
   nello storico. Soltanto il rilancio esplicito aggiunge un'altra voce.
2. Rotazione portrait/landscape: risultato preservato; icone di sistema chiare sul tavolo scuro
   anche con il tema chiaro, poi ripristinate al ritorno al gruppo.
3. Best/Worst, tema chiaro e italiano: confronti corretti, danno campionato una sola volta,
   risultati indipendenti delle Parts e dado di supporto generato dall'effetto.
4. Ricreazione durante la fase animata del dado aggiuntivo: finale già campionato, nessuna
   seconda esecuzione dell'effetto e nessun nuovo log.
5. Statistiche → ricreazione → aumento di livello → Back di Android: il foglio resta utilizzabile,
   il vecchio risultato viene invalidato e Back segue la gerarchia foglio → tavolo → gruppo.
6. Editor aperto dal tavolo: bozza invalida preservata alla ricreazione; Cancel lascia invariati
   definizione salvata, risultato e storico.

Gli altri test strumentali continuano a coprire editor, import da link, temi, gesture,
concorrenza della persistenza e ripristino delle bozze.

I selettori `testTag` non dipendono dalla lingua dell'interfaccia. Le attese usano la presenza,
l'abilitazione e i valori dei nodi, non pause arbitrarie. Per il tavolo il test aspetta anche
`RollTableRenderedKey`: frame OpenGL e texture effettivamente inviati al renderer, con un
secondo frame prima della conferma. La sola presenza del totale nei nodi Compose non basta.

## Esecuzione

Prerequisiti: JDK 17, Gradle 9.6, SDK/build tools configurati per il progetto e `adb` nel PATH.
Il collaudo di riferimento usa un **emulatore telefonico API 35**, ad esempio Pixel 6.

**Usare un emulatore o un profilo di test sacrificabile, non l'installazione personale.**
La fixture E2E preserva e ricontrolla dati, impostazioni, journal, scelta account e lingua
precedenti; salta gli scenari su APK non debug, account Google collegati o immagini personali.
Questo non rende innocua l'intera suite: alcuni test preesistenti azzerano i dati di test,
e il runner Android installa/rimuove gli APK.

Dalla root del repository, con l'emulatore già avviato:

```bash
# Tutti i test strumentali e raccolta dei diagnostici, anche se un test fallisce.
bash scripts/run-android-ui-tests.sh

# Soltanto i sei scenari del tavolo.
bash scripts/run-android-ui-tests.sh \
  -Pandroid.testInstrumentationRunnerArguments.class=com.keyserdsoze.dicethrower.e2e.RollJourneyE2ETest
```

Il runner abilita gli screenshot dei checkpoint. Per lanciare direttamente Gradle, da `src`:

```bash
gradle :app:connectedDebugAndroidTest

# Un singolo scenario, con screenshot dei checkpoint.
gradle :app:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.keyserdsoze.dicethrower.e2e.RollJourneyE2ETest#normalRollSurvivesRecreationAndOnlyExplicitRerollAddsHistory' \
  -Pandroid.testInstrumentationRunnerArguments.e2eScreenshots=true
```

I test dell'orchestrazione del runner non richiedono SDK o emulatore:

```bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py'
```

## Screenshot, log e CI

Ogni scenario scrive `steps.txt` e `device.txt`. Un errore produce, per quanto disponibile:

- `failure.png`: display completo, inclusi tavolo OpenGL e finestre native;
- `failure.txt`: asserzione o eccezione originale;
- `semantics.txt`: gerarchia di tutti i root Compose, inclusi dialoghi e fogli modali;
- `logcat.txt`: tag diagnostici limitati (`AndroidRuntime`, `Dice3DScene`, `TestRunner`),
  non un dump generale dei log di autenticazione.

Gli screenshot dei checkpoint sono facoltativi; quelli di errore vengono tentati comunque.
I diagnostici vengono copiati prima del teardown in
`/sdcard/dice-thrower-e2e-artifacts/<scenario>-<timestamp>`, perché UTP rimuove le directory
private dell'APK. Il runner li scarica in `src/app/build/outputs/e2e-artifacts`.
Senza il runner si possono recuperare con:

```bash
adb pull /sdcard/dice-thrower-e2e-artifacts ./android-e2e-artifacts
```

Il report Gradle è in `src/app/build/reports/androidTests/connected/debug/index.html`.
La workflow `.github/workflows/ci.yml` carica report, XML e diagnostici per 14 giorni anche in
caso di errore. Il codice d'uscita resta quello di Gradle: una copia degli screenshot riuscita
non può nascondere test falliti, né una copia fallita può sostituirne il codice d'errore.
L'action dell'emulatore esegue un unico script Bash, senza affidarsi a variabili condivise
tra righe interpretate in shell separate.

Gli artefatti possono contenere il testo visualizzato dall'app: non pubblicare screenshot o
gerarchie di un dispositivo con dati sensibili. Nessuna permission di storage viene aggiunta
all'APK di produzione per questa diagnostica.

## Limiti del collaudo

- La ricreazione conserva la sessione **in memoria nel ViewModel**. Se avviene durante un lancio,
  mostra il finale già calcolato: non tenta di riprendere la stessa posizione fisica al fotogramma.
- Dopo la morte del processo i log già salvati restano disponibili, ma la sessione live non è
  ricostruita e non viene generato automaticamente un nuovo tiro.
- I test non leggono via OCR i numeri sulle facce OpenGL e non fanno golden-image matching.
  Orientamento delle facce e fisica hanno test JVM dedicati; qualità artistica, GPU, fluidità
  e accessibilità richiedono ancora un passaggio su dispositivi reali.
- Consenso Google reale, sync su due dispositivi e scansione QR con la fotocamera non sono
  simulati da questa suite offline.
