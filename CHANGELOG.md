# Monkey — changelog

Versione documento: **1.1 — 2026-11-05 16:00** (allineato all'app v1.67.0)

Fonte: `Changelog.kt` (voci mostrate in app dalla 1.36.0). Le versioni fino alla 1.35.0 non hanno voce nel codice: la loro storia e' nei commenti datati dei sorgenti (vedi appendice di `Monkeymemoriacontesto.md`). Piu' recente in cima.

Storico documento:
- 1.1 (2026-11-05) — aggiunta v1.67.0.
- 1.0 (2026-11-05) — prima stesura, ricavata da `Changelog.kt` (1.36.0 → 1.66.0).

## v1.67.0 (versionCode 91)
- Cambiata la chiave di firma (la vecchia era esposta nel repo pubblico). Non si installa sopra le versioni precedenti: backup, disinstallare, reinstallare. Chiave e password fuori dal repo (`keystore/keystore.properties`).

## v1.66.0 (versionCode 90)
- Galleria foto Vinted: ora fino a 20 foto (prima solo 5).

## v1.65.0 (versionCode 89)
- Corretto: la data vera degli annunci Vinted ("Caricato")
- ora si legge davvero e sostituisce la stima.

## v1.64.0 (versionCode 88)
- Nuovo: in una pagina annuncio Vinted, toccando le foto si
- apre una galleria a schermo intero: scorri col dito,
- pizzica o tocca due volte per ingrandire.

## v1.63.0 (versionCode 87)
- Nuovo: se l'app non è più collegata a Subito o a Vinted
- compare un avviso in home con il tasto Accedi.

## v1.62.0 (versionCode 86)
- Corretto: la data "Caricato" degli annunci Vinted non
- compariva più — lo scaricamento si fermava prima
- del punto della pagina in cui si trova.

## v1.61.0 (versionCode 85)
- Meno dati: della pagina di un annuncio Vinted si scarica
- solo la parte con data e descrizione.
- Data stimata più precisa: ogni data letta dagli annunci
- che scorri corregge la stima degli altri.

## v1.60.0 (versionCode 84)
- Corretto: data "Caricato" e descrizione degli annunci
- Vinted — Vinted ha chiuso la via usata finora, ora
- si leggono dalla pagina pubblica dell'annuncio.

## v1.59.0 (versionCode 83)
- Nuovo: per gli annunci Vinted si legge la data vera dal
- campo "Caricato" dell'annuncio e compare sulla
- riga appena arriva (senza ≈). Finché non arriva
- resta la stima.

## v1.58.0 (versionCode 82)
- Corretto: molti annunci Vinted (es. orologi, abbigliamento)
- non mostravano il prezzo e avevano un titolo
- lunghissimo — ora si leggono anche taglia e altri
- campi.
- Nuovo: la data degli annunci Vinted non è più sempre
- "oggi" — viene stimata dal numero dell'annuncio
- e mostrata con ≈ davanti.

## v1.57.0 (versionCode 81)
- Corretto: per gli annunci Vinted senza marca dichiarata
- (spesso quelli esteri) non comparivano più né
- condizione né prezzo.
- Corretto: scorrendo indietro nei giorni gli annunci Vinted
- sparivano dalla lista — la stima di data usata al
- posto di quella vera (che Vinted non fornisce più
- in questa pagina) non avanzava tra una pagina e
- l'altra.

## v1.56.0 (versionCode 80)
- Corretta la ricerca su Vinted, che dava errore 404 da
- qualche giorno: Vinted ha ritirato la vecchia via
- per ottenere l'elenco degli annunci, ora si legge
- direttamente dalla pagina vera. "Venduto" non si
- riconosce ancora in elenco, e lo scorrimento oltre
- la prima pagina di risultati è da confermare.

## v1.55.0 (versionCode 79)
- Solo diagnostica, nessun cambiamento visibile: trovato il
- contenitore vero di ogni annuncio su Vinted (96
- trovati sulla stessa pagina) e dove sono scritti
- titolo, marca, condizione e prezzo. Questa versione
- verifica che il formato sia lo stesso su più
- annunci, prima di scrivere il lettore definitivo.

## v1.54.0 (versionCode 78)
- Solo diagnostica, nessun cambiamento visibile: la caccia
- all'elenco annunci Vinted (404 dalla v1.52.0) cambia
- strategia. Confermato che non c'è nessun JSON
- nascosto da nessuna parte nella pagina — ora la
- sonda guarda la struttura vera della pagina invece
- che il traffico di rete.

## v1.53.0 (versionCode 77)
- Solo diagnostica, nessun cambiamento visibile: la sonda
- della versione precedente per il 404 su Vinted aveva
- trovato un falso positivo invece dei dati veri. Questa
- versione la corregge con marcatori più precisi e un
- controllo in più nel punto dove Vinted ora trasmette
- i dati della pagina.

## v1.52.0 (versionCode 76)
- Solo diagnostica, nessun cambiamento visibile: la ricerca
- su Vinted dà errore 404 perché la rotta usata finora
- per ottenere l'elenco degli annunci è sparita. Questa
- versione aggiunge una sonda per capire dove sono
- finiti i risultati sulla pagina vera di Vinted.

## v1.51.0 (versionCode 75)
- Corretta una regressione della versione precedente: il
- testo dell'annuncio, su Vinted in vista estesa, era
- sparito del tutto invece di comparire tradotto. Ora
- torna a comparire (in lingua originale, come prima
- della 1.50.0) — la traduzione resta un problema
- aperto, da risolvere in altro modo.

## v1.50.0 (versionCode 74)
- Trovata e corretta la vera causa della traduzione mancante
- su Vinted, in vista estesa: la richiesta partiva a
- sessione ferma sulla pagina iniziale del sito invece
- che su quella dell'annuncio, e Vinted rispondeva
- sempre con il testo originale. Ora la sessione apre
- davvero l'annuncio prima di chiedere il testo,
- esattamente come già succede per il cuoricino.

## v1.49.0 (versionCode 73)
- Solo diagnostica, nessun cambiamento visibile: la v1.48.0
- non ha risolto la traduzione mancante su Vinted e i
- log finora non dicevano perché — ora "Esporta
- diagnostica" scrive anche cosa risponde davvero la
- chiamata quando "riesce" ma porta il testo sbagliato,
- non solo quando fallisce del tutto.

## v1.48.0 (versionCode 72)
- Corretto: la traduzione della descrizione Vinted (v1.47.0)
- non compariva sugli annunci già visti prima — restava
- in cache il testo originale e non veniva mai
- richiesto di nuovo. Ora sì.

## v1.47.0 (versionCode 71)
- Il testo dell'annuncio su Vinted, in vista estesa, ora è
- quello vero mostrato dalla scheda: se l'annuncio è in
- lingua straniera e Vinted lo traduce, compare la
- traduzione invece del testo originale.

## v1.46.0 (versionCode 70)
- Se Vinted risponde male perché sei uscito dall'account, ora
- l'app se ne accorge davvero e te lo dice chiaramente,
- invece di un generico "risposta 403".
- Nuova finestra al primo avvio che spiega come accedere con
- il tuo account su Subito e Vinted (tocca ⋮ → Sito), al
- posto del vecchio avviso che si scorreva via troppo
- facilmente.

## v1.45.0 (versionCode 69)
- Corretto l'errore 403 sulla pagina dei preferiti Vinted:
- mancavano le stesse intestazioni anti-CSRF gia'
- usate per il cuoricino su un singolo annuncio.

## v1.44.0 (versionCode 68)
- Corretto l'errore 500 di Vinted durante la ricerca: gli
- annunci per pagina raddoppiati della versione 1.41.0
- erano un valore che Vinted non regge (Subito si',
- Vinted no) — Vinted torna a 30 per pagina, Subito
- resta a 60.

## v1.43.0 (versionCode 67)
- Il testo dell'annuncio ora compare davvero anche su Vinted,
- in vista estesa: la sonda della versione precedente ha
- trovato dove sta per davvero, dentro la pagina
- dell'annuncio invece che in una chiamata a parte.

## v1.42.0 (versionCode 66)
- Solo diagnostica per il testo degli annunci Vinted, nessun
- cambiamento visibile: il tentativo precedente si e' rivelato
- un endpoint inesistente ed e' sospeso, in attesa di una sonda
- che dica davvero dove sta quel testo.

## v1.41.0 (versionCode 65)
- Corretto il rimescolamento durante lo scorrimento: tornando su
- dopo aver visto gli annunci piu' vecchi non compaiono piu'
- annunci "nuovi" al posto di quelli gia' visti.
- Annunci per pagina raddoppiati: si scorre meno per vedere piu'
- risultati.
- Il filtro "entro 60 giorni" parte gia' attivo: si toglie dal
- foglio filtri quando serve vedere l'archivio intero.

## v1.40.0 (versionCode 64)
- In vista estesa ora compare il testo dell'annuncio anche su
- Vinted, non solo su Subito: l'elenco di Vinted non lo
- porta, si chiede a parte e arriva un attimo dopo la foto.
- Si chiede solo per gli annunci che stai davvero guardando, uno
- alla volta: nessuna raffica di richieste a ogni pagina di
- risultati.
- Se la colonna del testo resta vuota su Vinted, esporta la
- diagnostica (Opzioni): questa versione ci scrive dentro cosa
- ha risposto la piattaforma.

## v1.39.0 (versionCode 63)
- Confermato: il controllo automatico e "Controlla ora" delle
- ricerche salvate funzionano, le notifiche arrivano.
- Toccando una di queste notifiche, ora la ricerca si apre con il
- filtro "solo nuovi" gia' attivo, invece di mostrare tutto
- mescolato.

## v1.38.0 (versionCode 62)
- Corretto per davvero il controllo automatico/"Controlla ora"
- delle ricerche salvate: nella v1.37.0 il sospetto (WebView
- senza genitore) era giusto ma non bastava — le vere chiamate
- al WebView giravano ancora su un thread di sfondo, dove non
- sono permesse, e fallivano in silenzio.
- Ricomparse in Opzioni "Esporta diagnostica" e "Azzera contatori":
- non erano state tolte, restavano solo fuori dall'area visibile
- dell'elenco, troppo corta da quando si e' aggiunta "Cerca
- aggiornamenti".

## v1.37.0 (versionCode 61)
- Corretto l'aggiornamento del controllo automatico delle ricerche
- salvate: il WebView usato in background non aveva mai un
- genitore vero, nemmeno invisibile — probabile causa del
- "non trova mai nulla", sia da solo che con "Controlla ora".
- Corretto: aprire una ricerca salvata mentre si scorreva un'altra
- lasciava la lista a meta', con annunci vecchi e nuovi mescolati
- invece di tornare in cima.
- La campanella delle ricerche salvate ora ha una piccola lente
- sovrapposta, per ricordare a cosa serve.
- Tolta l'icona lente accanto alla campanella (passava fra ricerca e
- navigazione del sito): si entra in navigazione dal menu ⋮, se ne
- esce con l'icona home o "indietro", come già capitava anche prima.
- L'icona vista compatta/estesa si è spostata sotto le ricerche recenti.
