# Preview JSON Android su IO — prima tranche

Tracking: [EPIC #165](https://github.com/KeyserDSoze/Android.DiceThrower/issues/165).
Branch `perf/async-character-file-preview`, destinato alla release **v0.0.29**.

## Modifiche

- Il risultato del selettore documenti non legge più lo stream né decodifica JSON sul main thread.
  `CharacterFileImportViewModel` usa `Dispatchers.IO` e restituisce lo stato sul main thread.
- Il ViewModel mantiene caricamento, preview ed errore attraverso la ricreazione dell'Activity;
  non conserva Activity, View, Bitmap o stream aperti come stato della preview.
- `CharacterFileReader` riusa lo stesso limite di **128 KiB** del codec portabile e chiude lo
  stream su successo, errore, dimensione eccessiva o cancellazione cooperativa.
- Una nuova selezione, un nuovo link riconosciuto o un annullamento invalidano il lavoro
  precedente. Risultati/errori tardivi non possono riaprire una preview scartata.
- Indicatore Material indeterminato durante il caricamento; stessi testi d'errore esistenti
  nelle 40 localizzazioni. Nessuna nuova permission o modifica al protocollo.

La lettura controlla la cancellazione tra i blocchi e prima del parsing. Un provider bloccato
in una `read()` può ritardare la chiusura fino al ritorno della chiamata: la UI resta libera e
il risultato non può più essere applicato se la richiesta è stata superata.

## Confini intenzionali

- Nessuna scrittura o rigenerazione ID durante la preview. Il percorso di conferma resta quello
  esistente, sotto lock, ed esegue ancora lavoro sincrono: questa non è l'intera migrazione IO.
- Anche decodifica dei link, impostazioni e separazione dello storico restano lavori distinti.
- Il caricamento/preview è mantenuto nella stessa sessione di processo, non dopo la morte del
  processo. In quel caso il file va selezionato di nuovo; non si genera un import automatico.
- Nessun grant persistente aggiunto né ripristino di URI dopo reboot. I test non automatizzano
  il selettore SAF reale o provider cloud: richiedono ancora accettazione manuale.

## Prove locali

**411 test JVM e 62 strumentali API 35**, build debug/AndroidTest e lint riusciti.

`CharacterFileReaderTest` copre Unicode spezzato in piccoli blocchi, esattamente il limite,
un byte oltre, JSON malformato, errore del provider e cancellazione, controllando la chiusura.
`CharacterFileImportTest` verifica con gate osservabili, senza pause arbitrarie:

1. decoder su thread diverso dal main, stesso ViewModel e un solo caricamento attraverso una
   vera ricreazione dell'Activity mentre la lettura è ancora bloccata;
2. selezione nuova conservata dopo il completamento tardivo di una lettura annullata;
3. Cancel senza successivi errori o riaperture della preview;
4. errori di lettura e nesting profondo gestiti senza crash;
5. file reale nella cache dell'app → preview in MainActivity → ricreazione → Cancel → nuova
   ricreazione: nessun import e dati locali identici allo snapshot iniziale.

I test usano un AVD sacrificabile separato da quello con l'APK ufficiale e lo storico di upgrade.
Suite: `bash scripts/run-android-ui-tests.sh`; report Gradle nelle directory documentate in
[ANDROID_E2E_TESTS.md](ANDROID_E2E_TESTS.md).
