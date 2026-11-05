# Subito+ — manuale d'uso

**Versione 1.0.3** — 30 agosto 2026
Android 10 o successivi · installazione manuale · uso personale

---

## Cos'è

Un'app che cerca su **Subito e Vinted insieme**, senza pubblicità, e in cui ogni
annuncio scartato resta scartato anche quando la ricerca si ripete.

Non sostituisce i due siti: li usa. Toccando un risultato si apre la scheda vera,
con la tua sessione, dove compri, vendi e scrivi al venditore come sempre.

---

## Le due schermate

L'icona con la **lente** in alto a destra alterna:

| | |
|---|---|
| **Ricerca** | la lista unificata delle due fonti |
| **Navigazione** | il sito dentro l'app, con la pubblicità tolta |

Il tasto **indietro** da una scheda riporta ai risultati.

---

## Cercare

Scrivi e premi invio. I risultati delle due fonti arrivano insieme e si
mescolano; ogni riga porta la sua provenienza — `SUBITO` in rosso, `VINTED` in
verde-azzurro — il prezzo, dove si trova la merce e la data di pubblicazione.

Su Subito il luogo è il comune, su Vinted il paese del venditore: quella
piattaforma è costruita sulla spedizione e la città non la espone. Sapere che
una cosa parte dalla Germania conta quanto sapere che è a Bari.

Scorrendo, le pagine successive arrivano da sole.

### Filtri

Il pulsante **Filtri** apre il pannello dal basso:

- **fonti** — cercare su una sola delle due
- **ordine** — più recenti, prezzo crescente, prezzo decrescente
- **età** — più vecchi o più recenti di N giorni
- **prezzo** — minimo e massimo
- **venduti** — di serie restano fuori

I filtri attivi restano visibili come etichette sopra i risultati: toccarne una
la toglie.

> **Un limite da conoscere.** I filtri agiscono su ciò che è già stato
> scaricato, non sulla richiesta alle fonti. Scorrendo si allarga l'insieme su
> cui lavorano.

### Nascondere un annuncio

La **✕** su ogni riga. Compare un avviso con «Annulla» per qualche secondo; poi
quell'annuncio non ricompare più, in nessuna ricerca, nemmeno dopo aver riavviato
il telefono.

Menu → **Annunci nascosti** per rivederli e ripristinarli, uno o tutti.

---

## Ricerche salvate e avvisi

Sopra i risultati, **Salva ricerca**: memorizza la domanda con i suoi filtri, e
considera «già visto» tutto quello che è in lista in quel momento.

Menu → **Ricerche salvate**:

- accendi i **controlli automatici** (il telefono chiederà il permesso per gli avvisi)
- scegli ogni quanto: **30 minuti, 1 ora, 3 ore, 12 ore**
- **Controlla ora** per una passata immediata
- la lente accanto a una ricerca la rilancia; la ✕ la elimina

Arriva un avviso solo per gli annunci **nuovi, non scartati e non venduti**.
Toccandolo, l'app si apre già su quella ricerca.

> **Perché non si scende sotto la mezz'ora.** Richieste a intervalli regolari e
> fitti sono la firma che distingue un programma da una persona. I controlli
> partono con uno scarto casuale, una fonte per volta, con pause fra l'una e
> l'altra, e leggono solo la prima pagina.

---

## Navigazione

Il sito dentro l'app, senza pubblicità: banner, riquadri sponsorizzati e
annunci in mezzo ai risultati non vengono nemmeno scaricati.

Nella barra: **ricarica**, e nel menu **condividi la pagina** e **apri nel
browser** — quest'ultimo è la via d'uscita se un pagamento o una verifica
d'identità non si completa nel WebView. I cookie sono condivisi con il browser
di sistema, quindi la sessione regge.

Nel menu ci sono anche:

- **Blocco pubblicità** — spegnilo per confrontare
- **Script nella pagina** — spegne ciò che l'app inietta: serve a capire se una
  lentezza è dell'app o del sito
- **Vista compatta** — foto piccola a sinistra, testo a destra
- **Sito: Subito / Vinted** — cambia il sito aperto

Toccando un link di subito.it o vinted.it in un'altra app, il sistema offre
anche Subito+.

---

## Backup

Menu → **Salva backup**: un file in `Download/SubitoPlus` con gli annunci
scartati, le ricerche salvate e le regole personalizzate. Non contiene sessioni
né credenziali.

Menu → **Importa file…** riconosce da solo cosa gli dai: un backup completo, un
archivio di nascosti, o un file di regole.

### Le regole di blocco

`regole.json` decide cosa fermare. Si può modificare a mano e ricaricare
dall'app, senza ricompilare nulla:

- `bloccaHost` — domini fermati
- `bloccaPercorsi` — percorsi fermati, anche su domini permessi
- `consentiSempre` — ha la precedenza: qui stanno il CDN delle foto, l'antifrode,
  la chat e le piattaforme di consenso, che togliendoli romperebbero l'app
- `cssNascondi` — cosa nascondere di ciò che resta nella pagina
- `interfaccia` — sezione iniziale della home, comportamento del pulsante Cerca

---

## Aggiornare

Le nuove versioni si installano sopra la precedente: la firma è la stessa e i
dati restano. **Conserva il keystore** (`keystore/subitoplus.jks` nei sorgenti):
senza quello, un aggiornamento futuro richiederebbe di disinstallare e perdere
scartati e ricerche salvate.

---

## Modalità sonda

Serve solo quando qualcosa va storto. Accendendola, l'app registra le richieste
di rete e i campioni delle risposte; **Esporta diagnostica** scrive i file in
`Download/SubitoPlus`.

Tienila spenta nell'uso normale: per funzionare sostituisce alcune funzioni
interne del browser, ed è la traccia più riconoscibile che l'app possa lasciare.

---

## Cosa mettere in conto

L'app blocca la pubblicità e automatizza le ricerche: entrambe le cose vanno con
ogni probabilità contro i termini di servizio di Subito e Vinted, anche se non
toccano il servizio né gli altri utenti. Resta sul tuo telefono, usa la tua
sessione e non viene distribuita. Nel caso limite la contromisura sarebbe la
sospensione dell'account.

Il nome «Subito+» va bene per un'app personale; sarebbe da cambiare se un giorno
decidessi di distribuirla.

---

## Changelog

| Versione | Cosa |
|---|---|
| **1.0.3** | Luogo degli annunci Vinted: su quella piattaforma è il paese del venditore, ed è utile quanto il comune su Subito |
| 1.0.2 | Correzione: gli annunci nascosti non sopravvivevano alla chiusura dell'app. Data degli annunci Vinted ricavata dall'ora della foto. Zoom sbloccato nelle pagine |
| 1.0.1 | Icona nuova: il setaccio, ricomposto dentro l'area sicura dell'icona adattiva, con la sua versione monocromatica per i launcher a tema |
| 1.0.0 | Backup completo in un file, import che riconosce da solo il formato, manuale |
| 0.7.0 | Scaricamenti, fotocamera e posizione per il sito, link `_blank`, condividi e apri nel browser, titolo della pagina in barra, apertura dei link subito.it e vinted.it |
| 0.6.0 | Ricerche salvate, controlli periodici in background, avvisi sui soli annunci nuovi |
| 0.5.2 | Schermata di ricerca in Compose: filtri nel foglio dal basso, scheletri, stati vuoti, errore per fonte con «riprova», paginazione automatica, annulla lo scarto |
| 0.5.1 | Colori dei marchi, luogo e dettaglio separati, esclusione dei venduti, vista compatta corretta, interruttore degli script di pagina |
| 0.5.0 | Material 3 con colore dinamico, tema chiaro e scuro, icona propria, schermata di avvio, barra dei comandi |
| 0.4.6 | Foto in elenco: resa da elenco invece dell'originale, coda a pila, cache su disco |
| 0.4.5 | Script iniettati messi a dieta: niente lavoro sulle schede di dettaglio |
| 0.4.4 | Barra di avanzamento |
| 0.4.3 | Filtro prezzo, pannello filtri richiudibile |
| 0.4.2 | Data di inserimento, filtro per età, invio da tastiera |
| 0.4.1 | Apertura schede: indirizzi relativi di Vinted, cronologia |
| 0.4.0 | Ricerca unificata Subito + Vinted |
| 0.3.2 | Ponte JS anonimo e non enumerabile, sonda spenta di serie |
| 0.3.1 | Sonda delle risposte API, cambio fonte |
| 0.3.0 | Nascondi annuncio, archivio locale, vista compatta |
| 0.2.0 | Blocco pubblicità con regole in JSON |
| 0.1.0 | Sonda diagnostica |
