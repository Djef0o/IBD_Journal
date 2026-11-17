# IBD Journal

[English](../../README.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · [Español](README.es.md) · **Italiano** · [Português](README.pt.md)

Un diario Android per chi convive con la malattia di Crohn o la colite ulcerosa. Registra evacuazioni e sintomi in pochi tocchi, segui le terapie e i risultati degli esami del sangue, prepara gli esami e porta al medico un report chiaro.

I tuoi dati sanitari restano sul tuo telefono.

<p>
  <img src="../screenshots/home.png" width="200" alt="Schermata Oggi">
  <img src="../screenshots/history.png" width="200" alt="Calendario storico">
  <img src="../screenshots/trends.png" width="200" alt="Andamenti">
  <img src="../screenshots/care.png" width="200" alt="Scheda Cure">
</p>

## Funzionalità

### Diario quotidiano
- **Evacuazioni in due tocchi**: scala di Bristol (tipo 0 = nessuna evacuazione quel giorno), sangue, muco, dolore e affaticamento (0–5), urgenza, evacuazioni notturne.
- **Falso stimolo**: urgenza di andare in bagno senza evacuare, frequente durante le riacutizzazioni. Registrato a parte, non conteggiato come evacuazione.
- **Pasti**: foto o scansione del codice a barre (dettagli del prodotto da Open Food Facts).
- **Promemoria serale**: una sola notifica per registrare tipo di evacuazione, dolore e affaticamento senza aprire l'app. Resta finché non hai risposto a tutte e tre le voci.

<img src="../screenshots/notification.png" width="400" alt="Notifica serale interattiva">

### Storico e andamenti
- Calendario mensile colorato in base alla consistenza delle evacuazioni.
- Evacuazioni al giorno, 30 giorni / 90 giorni / tutto lo storico, giorni con ciascun sintomo confrontati con il periodo precedente.
- **Report per il medico (PDF)**: riepilogo del periodo, sintomi, terapie con aderenza, ultimi esami del sangue con gli intervalli di riferimento del laboratorio.

### Cure
- **Terapie**: farmaci standard per le MICI, promemoria, "assunto / saltato" dalla notifica, aderenza, avvisi di rifornimento.
- **Esami del sangue e delle feci** programmati con promemoria.
- **Colonscopia e rettosigmoidoscopia**: data dell'esame, dieta senza scorie e dosi di preparazione intestinale come prescritte dal medico, visita anestesiologica. Un promemoria per ogni passaggio.
- **Risultati di laboratorio**: 43 esami comuni (PCR, calprotectina fecale, emocromo, profilo del ferro, reni, fegato, lipidi…), con l'intervallo di riferimento riportato dal tuo laboratorio, grafici e una tabella degli esami del sangue. L'app non fornisce valori normali propri.
- **Importazione dei referti di laboratorio** (PDF di laboratorio, anche più di uno alla volta): il testo del PDF viene letto sul telefono, i valori vengono convertiti nelle unità dell'app e nulla viene salvato prima della tua verifica. I documenti scansionati non sono supportati.

<p>
  <img src="../screenshots/labs.png" width="200" alt="Risultati di laboratorio per esame">
  <img src="../screenshots/lab-panels.png" width="200" alt="Tabella degli esami del sangue">
  <img src="../screenshots/lab-chart.png" width="200" alt="Grafico della PCR con l'intervallo del laboratorio">
</p>

### Altro
- Promemoria come notifiche dell'app o come eventi nel calendario del telefono.
- Backup e ripristino cifrati con password, su un file che conservi tu.
- Primo avvio guidato, temi chiaro e scuro, diverse palette di colori.
- Disponibile in inglese, francese, tedesco, spagnolo, italiano e portoghese.

## Privacy

- Il diario, le terapie e i risultati di laboratorio sono memorizzati in un database locale sul telefono. Non esistono account né server dell'app.
- Nessun servizio Google e nessun tracker: i referti di laboratorio vengono letti dal testo del PDF stesso (PdfBox-Android) e i codici a barre sono scansionati con ZXing, interamente sul telefono.
- L'accesso alla rete serve solo per la ricerca dei codici a barre (Open Food Facts). Nessun backup nel cloud: il backup automatico Google di Android è disattivato per l'app.
- Le esportazioni sono cifrate con una password scelta da te (AES-256-GCM, derivazione della chiave con PBKDF2).
- La sincronizzazione del calendario è disattivata per impostazione predefinita. Attivandola, i nomi di terapie ed esami vengono scritti nel calendario del telefono, che può essere sincronizzato con un account online; l'app avvisa prima di attivarla.

## Compilazione

Requisiti: Android Studio (versione recente), JDK 17, Android SDK 37. L'app funziona su Android 8.0 (API 26) e successivi.

```bash
./gradlew :app:installDebug          # compila e installa su un dispositivo collegato
./gradlew :shared:testAndroidHostTest # test unitari del modulo shared
```

La firma di rilascio legge `keystore.properties` (non incluso nel repository).

## Struttura del progetto

| Modulo | Contenuto |
|---|---|
| `shared/` | Kotlin Multiplatform: modelli dei dati, database Room, analisi, parser dei referti di laboratorio, pianificazioni di promemoria ed esami. Compila per Android e iOS. |
| `app/` | App Android: interfaccia Jetpack Compose (Material 3), Hilt, notifiche, sincronizzazione del calendario, report PDF, estrazione del testo dai PDF (PdfBox-Android), scansione dei codici a barre (ZXing). |

## Avvertenza

IBD Journal è un diario personale. Registra e mostra ciò che inserisci; non interpreta i tuoi risultati né suggerisce decisioni mediche, e non è un dispositivo medico. Non sostituisce il parere medico: segui sempre le indicazioni del tuo medico, soprattutto per la preparazione agli esami e per le terapie.

Strumenti di IA sono stati utilizzati per scrivere il codice e creare le icone della scala di Bristol.

## Licenza

[GNU AGPL-3.0](../../LICENSE). Puoi usare, studiare, modificare e condividere l'app; chi distribuisce una versione modificata, o la offre come servizio online, deve pubblicarne il codice sorgente con la stessa licenza.
