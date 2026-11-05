# Monkey — memoria e contesto (per ripartire in una nuova chat)

Versione documento: **2.1 — 2026-11-05 15:20**. Aggiornato al: **v1.66.0** (versionCode 90).

Storico documento:
- 1.0 (2026-09-02) — stesura a v1.32.0.
- 2.1 (2026-11-05) — aggiunto §5bis (concetti tecnici 1.56→1.66 ricavati dal transcript: mancavano), §5ter (release/autoupdate), nota sicurezza keystore in `old/`.
- 2.0 (2026-11-05) — aggiornato fino a v1.66.0; repo ora su GitHub
  (`chicco83/Monkey`, branch `main`) con sorgenti + transcript Cowork; regola
  "mai push" sostituita (vedi §2.2); nuova sezione 5 con sviluppi 1.33→1.66.

Questo file serve a far ripartire una nuova conversazione esattamente da dove
questa si e' fermata, senza dover rileggere l'intera cronologia della chat.
La prima parte e' un riassunto scritto a mano (progetto, regole, stato); la
seconda (appendice, in fondo) e' l'estrazione **letterale** di tutti i
commenti a blocco `/* ... */` dei sorgenti, file per file, nell'ordine in cui
compaiono — la vera memoria storica: ogni correzione porta la sua data, il
perche', e spesso la versione precedente tenuta come riferimento.

---

## 1. Cos'e' Monkey

App Android personale, senza pubblicita', per l'utente Cri: unifica la
ricerca fra **Subito.it** e **Vinted** (annunci di seconda mano). Sviluppo
un-persona, in coppia con Claude: ogni richiesta finisce con un APK firmato
+ zip dei sorgenti completi consegnati in chat.

- Package: `it.monkey.app`
- Repo locale: `/home/claude/subitoplus/` (git, branch `main`)
- Identita' git: `user.name "Monkey"`, `user.email "monkey-app@localhost"`
  — **mai** l'identita' reale dell'utente, ne' nel codice ne' nei commit.
- Consegne in: `/home/claude/out/Monkey-<versione>.apk` +
  `Monkey-<versione>-sorgenti.zip`
- Cartella Google Drive collegata (quando il bridge al dispositivo
  funziona): `G:\Il mio Drive\CRISTIANO\VIBE CODING\MONKEY\` — **nell'ultimo
  lungo tratto di lavoro il bridge non si e' mai connesso** ("device not
  connected" a ogni tentativo): si continua a tentarlo una volta per
  consegna e si ripiega senza drammi sulla sola consegna in chat.

## 2. Regole permanenti (non negoziabili, valgono sempre)

1. **Mai il nome vero o l'email dell'utente** (ne' "Cristiano"/"Zecchi" ne'
   qualunque indirizzo gmail.com) da nessuna parte nel codice, nei
   commenti, nei messaggi di commit o nell'identita' git. Prima di ogni
   consegna: `grep -rniE "cristiano|zecchi|gmail\.com"` sui sorgenti deve
   dare zero risultati.
2. **Git (aggiornata 2026-11-05)**: il vecchio "mai git push" e' SUPERATO.
   Il repository e' su GitHub (`chicco83/Monkey`) e si lavora **sempre sul
   branch `main`**. A fine attivita': `git pull --rebase`, commit solo dei
   file modificati dalla sessione (per nome, mai `git add -A`), messaggio
   descrittivo, `git push`. Piu' sessioni (locali/cloud) lavorano sugli
   stessi documenti: in caso di conflitto su un `.md` unire a mano tenendo
   entrambe le modifiche. Il keystore (`keystore/subitoplus.jks`) resta
   FUORI dal repo (e' nel `.gitignore`): lo zip `SubitoPlus-sorgenti-v0.1.0`
   dell'utente lo contiene; senza di esso un APK non si firma come
   aggiornamento.
3. **Versioning ferreo**: ogni modifica bump di `versionCode`/`versionName`
   in `app/build.gradle.kts`, con un blocco di commento datato che spiega
   il perche'. Nessuna consegna senza bump.
4. **Correzioni**: quando si cambia una sezione di codice, la versione
   precedente resta come commento datato ("--- versione precedente (fino
   alla vX.Y.Z) --- ... --- fine versione precedente ---"), non si
   cancella e basta. Le spiegazioni di "come funziona" stanno nei commenti
   dentro il codice, non solo nel messaggio di chat.
5. **Niente correzioni indovinate**: ogni fix nasce da una prova concreta
   (una sonda — cattura di traffico/JSON esportata dall'app stessa, un
   log, uno screenshot). Quando manca la prova per una feature, si spiega
   cosa servirebbe e si rimanda, invece di indovinare nomi di campo o
   forme di risposta. Esempio recente: l'icona "hai scritto al venditore"
   e' stata tolta (non lasciata sbagliata) proprio per questo — vedi §4.
6. **Manuale utente** (`Subito+ manuale.md` / equivalente rinominato) resta
   **congelato**: non aggiornarlo a meno che l'utente non lo chieda di
   nuovo esplicitamente.
7. Ogni consegna, dopo la build: verifica versione con aapt2
   (`aapt2 dump badging` → versionCode/versionName), verifica firma con
   `apksigner verify`, poi commit git (mai push), poi `SendUserFile`
   dell'APK e dello zip sorgenti, poi un tentativo di
   `device_stage_files`/`device_commit_files` verso Drive (senza insistere
   se il device non risulta connesso).
8. Attribuzione dei commit (regola di sessione, vale "da qui in avanti"):
   ogni commit termina con
   `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` e la riga
   `Claude-Session: https://claude.ai/code/session_01T2cLMw7XSVdUmUBdCnxwKc`
   — **verificare nella sessione corrente se questa istruzione e' stata
   rinnovata/cambiata**, potrebbe non essere piu' valida in una chat futura.

## 3. Preferenze di stile dell'utente (sempre attive)

- Risposte dirette, senza premesse introduttive.
- Quando si corregge del codice: **sempre il file/sezione intero**, mai
  solo il diff.
- Le spiegazioni di funzionamento vanno **nei commenti del codice**, non
  (solo) nel messaggio.
- Versioning tracciato e aggiornato a ogni revisione (vedi §2.3).

## 4. Architettura in breve

- **`Annuncio.kt`** — modello dati comune ai due siti (fonte, id, titolo,
  prezzo, luogo, immagine/i, descrizione, venditore, urn, preferito...).
- **`Fonti.kt`** — `FonteSubito` e `FonteVinted`: costruzione URL di
  ricerca e parsing delle risposte JSON in `List<Annuncio>`.
- **`MotoreRicerca.kt`** — motore HTTP/WebView che esegue le chiamate verso
  le due piattaforme (sessione condivisa, cookie, ecc.).
- **`StatoRicerca.kt`** — stato osservabile della schermata di ricerca:
  filtri, risultati aggregati, preferiti, nascosti, recensioni venditori.
- **`SchermataRicerca.kt`** — la UI Compose: elenco annunci (vista
  compatta/estesa), foglio filtri, home con le offerte fatte.
- **`Offerte.kt`** — le proposte di prezzo inviate dall'utente: lettura da
  Subito (`/v1/p2p/transactions`) e da Vinted (`/api/v2/inbox` +
  `/api/v2/conversations/<id>`), unificazione in un'unica mappa.
- **`LuoghiVinted.kt`** — cache luogo + numero recensioni venditore
  Vinted (`GET /api/v2/users/<id>`).
- **`RecensioniSubito.kt`** — numero recensioni venditore Subito
  (`hades.subito.it/v1/trust/feedback/public/users/...`).
- **`Controllore.kt`** — `CoroutineWorker` periodico (WorkManager) che
  controlla le ricerche salvate e notifica; guardia anti-doppio-avvio
  dalla v1.31.0.
- **`Nascosti.kt` / `Preferiti.kt` / `RicercheSalvate.kt` /
  `StoricoRicerche.kt`** — persistenza locale (file JSON in `filesDir`).
- **`MainActivity.kt`** — entry point, permessi, WorkManager, navigazione.
- **`SondaOfferte.kt` / `DomProbe.kt` / `ApiLog.kt` / `RequestLog.kt`** —
  strumenti diagnostici che l'utente lancia dal menu per catturare traffico
  reale ("sonde") quando serve una prova per un fix.

## 5. Stato attuale (v1.66.0, 2026-11-05)

**v1.66.0**, versionCode 90. Ultima consegna: galleria foto Vinted portata
a 20 foto (prima 5; il codice non aveva un limite esplicito, si presume che
le foto oltre le prime 5 non fossero nella pagina letta: aggiunta lettura
dell'elenco foto dai dati della pagina — **non verificata su pagina reale**).
APK compilato e firmato con il keystore dell'utente (impronta verificata)
ma **non provato su telefono**. Se restano 5 foto: serve la diagnostica.

Nel repo: `Monkey-1.66.0.apk`, `Monkey-1.35.0.apk`, `old/` (release storiche
dalla 0.x), `transcript-sessione-monkey.jsonl` (transcript Cowork completo),
`CHANGELOG.md`, `CLAUDE.md`, `MANUAL.md`. Dettaglio per versione:
`CHANGELOG.md` (ricavato da `Changelog.kt`).

### Sviluppi 1.33 → 1.66 in sintesi (dettaglio nel CHANGELOG.md)

- **1.33–1.35**: nessuna voce in app (changelog nato alla 1.36).
- **1.36–1.39 — controllo ricerche salvate**: WebView di background senza
  genitore (1.37) e chiamate fuori dal main thread (1.38) erano le cause del
  "non trova mai nulla"; **confermato funzionante in 1.39**, notifiche ok,
  il tocco apre la ricerca con "solo nuovi". Icone/menu ritoccati.
- **1.40–1.51 — descrizione/traduzione annunci Vinted**: la descrizione sta
  nella pagina dell'annuncio (non in un endpoint a parte, 1.43); 1.44 Vinted
  torna a 30 per pagina (60 dava 500; Subito resta 60); 1.45 fix 403
  preferiti (header anti-CSRF); 1.46 rilevamento logout + guida primo avvio;
  1.47–1.51 tentativi sulla traduzione: causa trovata in 1.50 (sessione sulla
  home invece che sull'annuncio) ma in 1.51 si e' tornati al testo originale:
  **traduzione ancora aperta**. 1.41: stop al rimescolamento in scroll, filtro
  "entro 60 giorni" attivo di default.
- **1.52–1.58 — Vinted ha ritirato la rotta JSON degli elenchi (404)**:
  1.52–1.55 solo sonde; 1.56 la ricerca ora **legge la pagina HTML vera**
  (scorrimento oltre la prima pagina da confermare, "Venduto" non
  riconosciuto in elenco); 1.57–1.58 fix marca assente, prezzo/titolo con
  taglia, data **stimata** dal numero annuncio (≈).
- **1.59–1.62 — data "Caricato" Vinted**: letta dalla pagina pubblica
  dell'annuncio (la via API e' chiusa), poi corretti troncamenti.
- **1.61**: scarico ridotto alla sola parte con data+descrizione; ogni data
  vera corregge la stima degli altri.
- **1.63**: avviso in home se la sessione Subito/Vinted e' scaduta (tasto
  Accedi). **1.64**: galleria a schermo intero sulle foto Vinted (swipe,
  pinch, doppio tocco). **1.65**: data vera "Caricato" Vinted sostituisce la
  stima. **1.66**: 20 foto.

### Cose aperte / in sospeso

- **Verifica sul campo della 1.66** (20 foto Vinted) — mai provata.
- **Traduzione descrizione Vinted** (aperta da 1.47, vedi sopra).
- **Vinted via HTML (1.56+)**: scroll oltre la prima pagina, stato
  "Venduto" in elenco: da confermare.
- Pendenze storiche ancora valide: icona "hai scritto al venditore" (serve
  sonda su conversazione Vinted senza offerta + endpoint chat Subito);
  403 sul rimuovere preferiti Vinted (1.45 ha corretto la *pagina* preferiti;
  verificare la rimozione); guardia anti-doppio-avvio 1.31 (probabilmente
  superata dal fix 1.37–1.39 sul controllo).
- Contenuto 1.32.0 (rilanci venditore Vinted, `Offerta.controproposta`,
  `HorizontalPager` foto vista estesa) resta valido; la nota "photos[] non
  confermato" e' superata dalle 1.64–1.66.
- Sessione Cowork originale: il transcript completo e' in
  `transcript-sessione-monkey.jsonl` nel repo.

## 5bis. Concetti tecnici 1.56 → 1.66 (dal transcript Cowork; prima assenti qui)

Il transcript (`transcript-sessione-monkey.jsonl`) copre solo l'ultimo
tratto (da 1.56 a 1.66) piu' un riassunto di sessione; la storia precedente
sta nei commenti datati dei sorgenti (appendice).

- **Ricerca Vinted = pagina HTML (Next.js)**, non piu' JSON: `/api/v2/catalog/items`
  da 404 anche con login (confermato dalla diagnostica 1.64) → resta la
  lettura DOM. `MotoreRicerca.avviaDom` usa una **sessione WebView separata**
  (`sessioniDom`); card = `[data-testid^="product-item-id-"]`, l'attributo `alt`
  porta "titolo, Marca: X, Condizioni: Y, Taglia: Z, prezzo, totale".
  `FonteVinted.scomponiAlt()` e' un parser strutturale (prezzi in coda,
  etichette note) — 96/96 card lette. `venduto` e `venditore` NON disponibili
  dalle card DOM.
- **Lezione 1.50/1.51**: mai navigare la sessione WebView "classica"
  condivisa (rompe cuoricino/traduzione); le fetch dalla classica vanno bene.
- **Data annunci Vinted**: gli id Vinted sono un contatore globale monotono
  (~134 id/s ≈ 11,5M/giorno) → `StimaDataVinted.kt` interpola fra ancore
  (id, istante): ancore vive da `/catalog?order=newest_first` (ogni ≥20 min),
  semi fissi (9848892019@1788211276000, 9854267488@1788270609000) e letture
  "Caricato" (quelle a precisione di un'ora tenute a parte e vincolate fra
  punti precisi). La data stimata si mostra con "≈".
- **Data vera**: `DataCaricamentoVinted.kt` (v1.3.0) legge "Caricato";
  Vinted la da' come testo inglese abbreviato "Uploaded 13 min ago"
  (da qui il fix 1.65: `cercaPrefisso` + regex con abbreviazioni).
  Fonte primaria: `https://api.vinted.it/item-details/plugins/<id>/translatable?localize=false`
  (400 `ANONYMOUS_VIEWER` se si e' persa la login, 200 se loggati);
  fallback: HTML pubblico `/items/<id>` scaricato in streaming parziale
  (`MotoreRicerca.paginaParziale`, limiti 4.000.000 car. totali / 300.000 dopo
  "Caricato"; la data sta in FONDO alla pagina, dopo un grosso dizionario di
  traduzioni; `senzaScript()` taglia script troncati).
  `DescrizioniVinted.CACHE_VERSIONE` = "1.59.0".
- **Controllo login a tre stati** (`StatoRicerca.controllaAccessi`, throttle
  10 min, retry dopo 30 s se "sconosciuto"): solo 401/403 = non loggato.
  Causa di molti problemi apparenti: l'app perdeva la login in silenzio.
  Deciso di NON tornare a versioni precedenti.
- **Galleria Vinted a schermo intero** (`GalleriaVinted.kt`, utente: "perfetta"):
  script a document-start che intercetta il click su `/items/<id>` e chiama
  `Ponte.apriGalleria(json, indice)`; pager + pinch/doppio tocco 1–5x
  (`@OptIn(ExperimentalFoundationApi)`). 1.66: limite 20 foto.
- **Da verificare in diagnostica**: righe `[vinted] caricato articolo … (dettaglio)`
  e `stima-data … letture=` crescente; `page=N` Vinted sembra onorato (page=2
  = 96 annunci diversi, piu' vecchi) ma non confermato esplicitamente.
- **Toolchain di consegna** (sessione Cowork): `gradle assembleRelease` →
  `aapt2 dump badging` (versione) → `apksigner verify --verbose` → grep
  identita' (`cristiano|zecchi|gmail\.com`, zero risultati) → commit →
  zip sorgenti. Nel repo il grep andra' fatto sui sorgenti, NON sul `git log`
  (l'autore dei commit GitHub e' l'account dell'utente).

## 5ter. Release e autoupdate

`Aggiornamenti.kt` legge `GET api.github.com/repos/chicco83/Monkey/releases/latest`
(senza token → il **repo deve restare pubblico**), confronta `tag_name` (senza
"v") con `BuildConfig.VERSION_NAME` per componenti numerici e scarica il primo
allegato `.apk`. Una release per ogni versione: tag `v<versionName>`,
allegato l'APK firmato. Stato 2026-11-05: ultima release pubblicata **v1.44.0**;
v1.45→v1.66 NON hanno release (quindi chi e' sotto la 1.66 non vede nulla
finche' non si pubblica la v1.66.0). Le sessioni cloud non possono creare release
(HTTP 403 "not permitted for this session type"): si fa dall'utente.

**Sicurezza**: `old/` contiene zip dei sorgenti 1.13–1.34 (e SubitoPlus-*) che
includono `keystore/subitoplus.jks` in un repo **pubblico**. Vedi raccomandazione
in chat: rimuoverli (e idealmente ripulire la storia).

## 6. Come procedere in una nuova chat

1. Leggere questo file per il contesto generale.
2. Se serve il dettaglio esatto di una funzione, leggere direttamente il
   sorgente in ``app/src/main/java/it/monkey/app/` nel repo`
   (questo file ne riassume la storia, non sostituisce il codice).
3. Verificare `git log --oneline -5` per la punta attuale del branch.
4. Rispettare sempre le regole permanenti del §2 (niente nome/email reali,
   git su `main` con pull --rebase e push, versioning, correzioni con storico, evidenza prima di
   inventare).
5. Se l'utente riporta un problema nuovo, il riflesso e' chiedere/aspettare
   una sonda (se serve un dato che oggi non si legge) prima di scrivere
   codice che indovina la forma di una risposta.

---

## Appendice — commenti dal codice, per file (verbatim)

Ogni blocco `/* ... */` del sorgente, nell'ordine in cui compare nel file. Sono la memoria storica vera: ogni correzione porta la sua data, il perche', e spesso la versione precedente tenuta come riferimento (convenzione del progetto). I commenti `//` di una riga sola non sono ripetuti qui salvo quando fanno parte del blocco.


### app/build.gradle.kts


**riga 11:**
```
/*
     * v1.14.0 (2026-08-31) — CORREZIONE: applicationId/namespace non devono
     * contenere alcun dato personale del proprietario. Quello di partenza
     * lo faceva fin dalla v0.1.0; ora e' "it.monkey.app", legato al nome
     * dell'app (Monkey) e non a chi l'ha commissionata.
     *
     * ATTENZIONE — applicationId diverso vuol dire APP DIVERSA per Android:
     * questa versione NON aggiorna quella gia' installata, si affianca come
     * seconda app. La vecchia resta installata con dentro login, preferiti
     * live e le sue impostazioni; nella nuova si rifa' il login su Subito e
     * Vinted (i cookie non si spostano fra due app diverse) e si puo'
     * recuperare annunci nascosti + ricerche salvate + regole con "Salva
     * backup" nella vecchia app e "Importa file..." in questa. Fatto quello,
     * la vecchia si puo' disinstallare.
     *
     * (la versione precedente di namespace/applicationId non e' riportata
     * qui per lo stesso motivo della correzione: conteneva un dato personale)
     */
```


**riga 37:**
```
/*
         * v1.17.0 (2026-08-31) — due cose:
         *  1. le offerte fatte su Vinted ora si vedono anche in app (prima solo
         *     quelle Subito: vedi il commento in cima a Offerte.kt);
         *  2. se l'app riparte da zero (il sistema ha chiuso il processo in
         *     secondo piano, o l'hai chiusa tu) l'ultima ricerca si ripete da
         *     sola invece di lasciare la casella vuota (vedi il commento su
         *     StatoRicerca.ripristinaUltimaRicerca()).
         *
         * NOTA sul 403 dei preferiti Vinted: il ricaricamento della v1.16.0
         * (ancora attivo, sotto) NON ha risolto la rimozione — il messaggio
         * torna con "[ricaricato]" e lo stesso errore, quindi non era un
         * token invecchiato. Resta un problema aperto, non toccato in questa
         * versione: vedi la spiegazione data in chat, serve piu' tempo prima
         * di un altro tentativo alla cieca.
         *
         * --- versione precedente ---
         * versionCode = 40
         * versionName = "1.16.0"
         * --- fine versione precedente ---
         */
```


**riga 58:**
```
/*
         * v1.18.0 (2026-08-31) — "indietro" da una ricerca torna alla home
         * (le ultime proposte) invece di uscire dall'app — vedi il commento
         * sul gestore di "indietro" in MainActivity.kt. Aggiunta anche
         * un'icona "home" in barra per andarci con un tocco da qualunque
         * punto (utile soprattutto da dentro "Sito", dove "indietro" scorre
         * prima la cronologia della pagina).
         *
         * --- versione precedente ---
         * versionCode = 41
         * versionName = "1.17.0"
         * --- fine versione precedente ---
         */
```


**riga 71:**
```
/*
         * v1.19.0 (2026-08-31) — NON e' una correzione del 403 sui preferiti
         * Vinted: e' lo strumento per smettere di indovinare.
         *
         * La nuova sonda mandata in chat (api.json) ha mostrato che vinted.it
         * carica un secondo sistema anti-bot oltre a Datadome — chiamate
         * cifrate verso un percorso offuscato (".../j3r4zw/v1/consume", con
         * un bootstrap ".../j3r4zw/v1/config") — dello stesso tipo di SDK che
         * altri siti usano per punteggiare quanto "umano" sembra chi scrive.
         * Ma quella sonda ha guardato una scheda di browser normale, MAI la
         * sessione nascosta 1x1 dell'app che e' quella che davvero fallisce:
         * non e' una prova, e' un indizio.
         *
         * Questa versione aggiunge SOLO diagnostica, zero comportamento
         * nuovo: quando il preferito su Vinted torna 403, l'app ora registra
         * (nel log che la sonda gia' esporta) le intestazioni vere della
         * risposta e quante volte, nella sessione VERA che e' appena fallita,
         * sono partite chiamate verso quel percorso. Vedi i commenti in
         * Prologo.kt e MotoreRicerca.kt. Prossimo passo: riprovare a togliere
         * un preferito, mandare la nuova diagnostica, e SOLO allora decidere
         * se la pista anti-bot regge o va scartata.
         *
         * --- versione precedente ---
         * versionCode = 42
         * versionName = "1.18.0"
         * --- fine versione precedente ---
         */
```


**riga 98:**
```
/*
         * v1.20.0 (2026-08-31) — CORREZIONE: un'offerta appena fatta compariva
         * in lista solo chiudendo l'app dal task manager e riaprendola.
         *
         * Causa trovata (non un'ipotesi): Offerte.aggiorna() non rilegge piu'
         * di una volta ogni cinque minuti, e quel limite vive per tutta la
         * vita del PROCESSO (Offerte e' un "object" Kotlin, un solo esemplare
         * finche' il processo resta vivo). Tornare nell'app con "indietro" o
         * col tasto home non uccide il processo, quindi il limite non si
         * azzera mai da solo: solo la chiusura forzata lo fa. Rimedio: a ogni
         * vero ritorno in foreground (onResume) si rilegge subito, ignorando
         * il limite — vedi StatoRicerca.aggiornaOfferteAlRitorno() e
         * MainActivity.onResume().
         *
         * Le offerte Vinted restano un problema aperto e diverso: non
         * comparivano nemmeno con l'app riavviata da zero. aggiornaVinted()
         * non scriveva mai nel log in caso di problema — ora sì (vedi
         * Offerte.kt): la prossima sonda dira' dove si ferma davvero, invece
         * di lasciarlo indovinare.
         *
         * --- versione precedente ---
         * versionCode = 43
         * versionName = "1.19.0"
         * --- fine versione precedente ---
         */
```


**riga 123:**
```
/*
         * v1.21.0 (2026-08-31) — CORREZIONE: le offerte Vinted non sono mai
         * comparse. Causa trovata con la sonda mandata in chat (una vera
         * offerta fatta dal sito vero, catturata per intero): Offerte.kt
         * chiamava "/inbox" e "/conversations/<id>" SENZA il prefisso
         * "/api/v2/" che porta ogni altra chiamata Vinted di questo progetto
         * — Vinted rispondeva con la pagina "non esiste" (404), scartata in
         * silenzio a ogni giro. Corretto. La stessa sonda ha anche mostrato
         * che "transaction.offer_price" e' il prezzo di LISTINO, non
         * l'offerta: l'importo vero sta nel messaggio piu' recente della
         * conversazione (vedi leggiConversazioneVinted in Offerte.kt).
         *
         * Aggiunta anche diagnostica (nessun cambiamento nel risultato) per
         * il sospetto separato "su Vinted vedo solo gli annunci di oggi":
         * vedi il commento su FonteVinted.chiavi() in Fonti.kt.
         *
         * --- versione precedente ---
         * versionCode = 44
         * versionName = "1.20.0"
         * --- fine versione precedente ---
         */
```


**riga 144:**
```
/*
         * v1.22.0 (2026-08-31) — due correzioni segnalate appena le offerte
         * Vinted hanno iniziato a comparire (v1.21.0):
         *
         * 1. L'etichetta della fonte, nella riga di ogni offerta, diceva
         *    sempre "SUBITO" — scritta fissa da quando le offerte erano solo
         *    di Subito (v1.4.0), mai resa dinamica quando la v1.10.0 ha
         *    aggiunto Vinted. Ora usa o.fonte (vedi SchermataRicerca.kt).
         *
         * 2. Le offerte VERE di Subito sparivano proprio mentre comparivano
         *    quelle Vinted: la rilettura forzata a ogni ritorno in app
         *    (v1.21.0) puo' far partire due "aggiorna()" quasi insieme, e una
         *    guardia di rientro rotta (segnava "finito" subito dopo aver
         *    AVVIATO le chiamate, non dopo la risposta) lasciava che una
         *    risposta arrivata per seconda scrivesse sopra la prima, buona,
         *    con l'accoglienza dell'esito incompleto. Vedi il commento su
         *    Offerte.aggiorna().
         *
         * --- versione precedente ---
         * versionCode = 45
         * versionName = "1.21.0"
         * --- fine versione precedente ---
         */
```


**riga 167:**
```
/*
         * v1.23.0 (2026-08-31) — CORREZIONE: il chip "Senza Subito" (o "Senza
         * Vinted") si mostrava attivo ma in mezzo agli annunci restavano
         * comunque annunci dell'altra fonte, anche scorrendo in basso.
         *
         * Causa trovata (non un'ipotesi, letta nel codice): usaSubito/
         * usaVinted venivano guardati SOLO da fonti() — quali fonti
         * interrogare per le pagine ancora da caricare. La lista mostrata
         * a schermo ("risultati", in StatoRicerca.kt) filtrava per eta',
         * prezzo, novita' e venduto, ma MAI per fonte: gli annunci gia'
         * scaricati prima di spuntare il filtro restavano visibili per
         * sempre. Non era quindi un problema di attesa/caricamento a 30
         * alla volta come ipotizzato in chat — nessuna icona di
         * caricamento avrebbe risolto nulla. Vedi il commento su
         * StatoRicerca.risultati.
         *
         * --- versione precedente ---
         * versionCode = 46
         * versionName = "1.22.0"
         * --- fine versione precedente ---
         */
```


**riga 188:**
```
/*
         * v1.24.0 (2026-08-31) — tre cose dallo stesso messaggio:
         *
         * 1. NUOVO: pannello "Opzioni" (⋮ -> Opzioni…), pensato per crescere.
         *    Prima voce: cosa fare all'avvio, riprendere l'ultima ricerca
         *    (comportamento di sempre, v1.17.0) o andare alla home. Vedi
         *    DialogoOpzioni.kt e il commento su MainActivity.onCreate().
         *
         * 2. DIAGNOSTICA (non una correzione): le offerte Subito vere
         *    restano assenti dalla lista anche con la v1.22.0 installata.
         *    La chiamata che le legge non lasciava MAI una riga di log in
         *    caso di problema, a differenza di quella Vinted (che da tempo
         *    lo fa) — ora si', cosi' la prossima diagnostica esportata dira'
         *    dove si ferma davvero. Vedi il commento su Offerte.aggiorna().
         *
         * 3. CORREZIONE (indiziaria, non ancora confermata da una sonda su
         *    un caso vero): un acquisto Vinted gia' concluso restava fra le
         *    offerte attive. Aggiunta la parola "venduto" fra quelle che
         *    Offerte.terminata() riconosce come stato chiuso — vista scritta
         *    da Vinted su una scheda articolo gia' comprato, in una delle
         *    immagini mandate in chat. Vedi il commento su terminata().
         *
         * --- versione precedente ---
         * versionCode = 47
         * versionName = "1.23.0"
         * --- fine versione precedente ---
         */
```


**riga 215:**
```
/*
         * v1.25.0 (2026-08-31) — CORREZIONE, con la prova in mano stavolta
         * (la sonda di un'offerta vera, 3bd66d96-api.json, la stessa del
         * contratto v1.21.0): trovata la causa reale per cui "le offerte
         * Subito si vedevano fino alla 1.20" — non e' mai stata una lettura
         * che fallisce, e' un ordinamento sbagliato. Ogni offerta Vinted
         * veniva datata "adesso" a OGNI rilettura (mai con la sua vera data),
         * quindi vinceva sempre il confronto con le date vere di Subito nella
         * top-6 della home: prima della v1.21.0 non si vedeva perche' Vinted
         * non arrivava affatto (endpoint rotto), da quando arriva ha
         * affollato tutte le posizioni. Ora si usa la vera data del
         * messaggio (created_at_ts, trovato nella sonda). Vedi il commento
         * su Offerte.leggiConversazioneVinted().
         *
         * Corretto anche l'annuncio "fantasma" (un'offerta Vinted "Accettata"
         * rimasta in lista con l'annuncio ormai introvabile sul sito): la
         * stessa transazione porta due booleani veri, is_completed e
         * item_is_closed, che ora contano quanto il testo dello stato. E il
         * giudice di "e' conclusa?" e' diventato uno solo — "attiva" — invece
         * di due che potevano disallinearsi (vedi Offerte.conclusa()).
         *
         * --- versione precedente ---
         * versionCode = 48
         * versionName = "1.24.0"
         * --- fine versione precedente ---
         */
```


**riga 241:**
```
/*
         * v1.26.0 (2026-09-01) — tre cose dallo stesso messaggio:
         *
         * 1. TENTATIVO NUOVO per il 403 dei preferiti Vinted (mai risolto
         *    da v1.6.0 a v1.19.0): l'utente ha catturato con la sonda un
         *    preferito aggiunto E tolto con successo dentro una vera pagina
         *    di un articolo Vinted. Indirizzo, corpo e intestazioni erano
         *    gia' identici a quelli che l'app manda — quello che mancava era
         *    la pagina: la sessione nascosta restava sulla home caricata una
         *    volta all'avvio, mai su una pagina vera di articolo. Ora la
         *    sessione ci naviga davvero, appena prima del preferito. Vedi
         *    Fonti.kt (toggle) e MotoreRicerca.kt (azione, paginaPrima).
         *
         * 2. Il menu dai tre pallini aveva quattordici voci: le sette di uso
         *    raro (backup, import, sonda, diagnostica) si spostano nel
         *    pannello Opzioni, restano solo le prime sei. Vedi
         *    BarraSuperiore.kt e DialogoOpzioni.kt.
         *
         * 3. La home mostrava solo le ultime 6 offerte, un tetto rimasto da
         *    prima che la lista scorresse da sola (v1.9.0): ora le mostra
         *    tutte. Vedi Offerte.recenti() e StatoRicerca.offerteRecenti().
         *
         * --- versione precedente ---
         * versionCode = 49
         * versionName = "1.25.0"
         * --- fine versione precedente ---
         */
```


**riga 268:**
```
/*
         * v1.27.0 (2026-09-01) — CORREZIONE: "ho attivato le notifiche per
         * le ricerche programmate ma non e' arrivata nessuna notifica".
         *
         * Bug vero, trovato leggendo Controllore.doWork(): le notifiche di
         * TUTTE le ricerche salvate partivano solo alla fine, dopo aver
         * controllato TUTTE quante. Un lavoro periodico ha un tempo massimo
         * di esecuzione, e con piu' ricerche, piu' fonti, e le pause volute
         * apposta per non sembrare un programma, un giro completo puo'
         * avvicinarsi o superarlo — se il sistema lo interrompe a meta', le
         * notifiche gia' pronte sparivano insieme al resto, e gli annunci
         * trovati restavano comunque segnati come "gia' visti" (non
         * sarebbero MAI piu' stati notificati). Ora ogni ricerca notifica
         * appena finite le sue fonti, non aspetta piu' le altre.
         *
         * Aggiunto anche un log che resta (controllo-log.txt, in "Esporta
         * diagnostica"): finora un giro in background non lasciava traccia
         * da nessuna parte leggibile. Se le notifiche continuano a non
         * arrivare, quel file dira' perche' invece di doverlo indovinare.
         *
         * --- versione precedente ---
         * versionCode = 50
         * versionName = "1.26.0"
         * --- fine versione precedente ---
         */
```


**riga 293:**
```
/*
         * v1.28.0 (2026-09-01) — NUOVO: "e' possibile visualizzare
         * nell'elenco annunci una icona se ho mandato un messaggio al
         * venditore?".
         *
         * La risposta era gia' quasi tutta nei dati che l'app tiene: ogni
         * proposta inviata (Subito: transazione P2P; Vinted: conversazione
         * con un offer_request_message) diventa un Offerte.Offerta, tenuto
         * per chiave annuncio (Offerte.perAnnuncio). L'elenco di ricerca
         * gia' legge questa mappa per mostrare "offerti xxx €" accanto al
         * prezzo — ma SOLO quando la proposta e' ancora attiva (vedi
         * StatoRicerca.offerta(), che ritorna null se o.attiva e' falso).
         * Un annuncio con proposta conclusa (accettata, rifiutata, venduto)
         * spariva quindi da qualunque segno visibile in elenco, anche se un
         * messaggio al venditore c'e' stato davvero.
         *
         * Aggiunta una seconda funzione, StatoRicerca.contattato(), che non
         * guarda "attiva": basta che esista una Offerta per quell'annuncio,
         * conclusa o no. Ogni riga dell'elenco annunci (RigaAnnuncio, sia
         * vista compatta che estesa — condividono Descrizione()) mostra ora
         * un'iconcina "inviato" (Icons.Filled.Send) accanto al nome della
         * fonte quando questo e' vero, indipendente dal testo rosso del
         * prezzo che resta legato alla sola proposta ancora aperta.
         *
         * --- versione precedente ---
         * versionCode = 51
         * versionName = "1.27.0"
         * --- fine versione precedente ---
         */
```


**riga 322:**
```
/*
         * v1.29.0 (2026-09-01) — NUOVO: "mi serve aggiungere ai filtri un
         * toggle per scartare gli annunci il cui venditore ha 0 recensioni".
         *
         * Il numero di recensioni Vinted arriva dalla STESSA chiamata che
         * LuoghiVinted gia' faceva per il luogo (GET /api/v2/users/<id>):
         * il campo "feedback_count" c'era gia' nella risposta, verificato su
         * tre punti diversi di una sessione reale (users/current,
         * promoted_closets, conversations — quest'ultimo lo chiama
         * "review_count" invece, ma e' lo stesso concetto). Nessuna chiamata
         * in piu', solo un campo in piu' letto da quella che gia' c'era.
         * Vedi LuoghiVinted.kt per i dettagli e per la correzione necessaria
         * alla coda (un venditore con solo il luogo in cache non doveva
         * restare per sempre senza recensioni).
         *
         * Su Subito il dato non c'e' ancora: esiste un sistema di recensioni
         * (hades.subito.it/v1/trust/feedback-api/…) ma finora una sonda ha
         * catturato solo l'endpoint che da' un token, non quello con il
         * numero vero. Il filtro (StatoRicerca.scartaSenzaRecensioni) tratta
         * quindi "non lo sappiamo" come "non scartare" — vale per gli
         * annunci Subito e per i Vinted la cui risposta non e' ancora
         * arrivata, non solo per uno zero vero.
         *
         * Nuovo interruttore in Filtri ("Nascondi venditori senza
         * recensioni"), con l'avviso che per ora vale solo su Vinted.
         *
         * --- versione precedente ---
         * versionCode = 52
         * versionName = "1.28.0"
         * --- fine versione precedente ---
         */
```


**riga 353:**
```
/*
         * v1.30.0 (2026-09-01) — COMPLETA la v1.29.0: recensioni anche su
         * Subito. La sonda chiesta ("una presa dal profilo di un venditore
         * Subito") ha dato l'endpoint mancante:
         *
         *   GET hades.subito.it/v1/trust/feedback/public/users/
         *       sdrn:subito:user:<id>/feedback?limit=1&page=0&sources=MEMBER
         *   -> pagination.totalCount = numero di recensioni
         *
         * "public" nel percorso, nessun token richiesto per leggerlo (la
         * sonda l'ha preso guardando il profilo di un ALTRO venditore, non
         * il proprio). Nuovo file RecensioniSubito.kt, stessa architettura
         * di LuoghiVinted.kt (coda, due alla volta, cache su file).
         *
         * Serviva anche l'id del venditore per ogni annuncio Subito, che
         * finora non veniva letto (Annuncio.venditore restava vuoto: solo
         * Vinted lo riempiva). E' "advertiser.user_id", gia' presente in
         * ogni risultato di ricerca — vedi Fonti.kt, FonteSubito.leggi().
         *
         * Il filtro "Nascondi venditori senza recensioni" (v1.29.0) ora
         * copre entrambe le fonti: tolto l'avviso "solo su Vinted" dal
         * pannello Filtri.
         *
         * --- versione precedente ---
         * versionCode = 53
         * versionName = "1.29.0"
         * --- fine versione precedente ---
         */
```


**riga 381:**
```
/*
         * v1.31.0 (2026-09-01) — due correzioni, non collegate fra loro.
         *
         * 1. "il doppio avvio nel log": controllo-log.txt mostrava coppie
         *    di "avvio" a pochi secondi/minuti l'una dall'altra con un solo
         *    "fine giro". Trovato un varco vero nel codice: "Controlla ora"
         *    (MainActivity.controllaOra) mette in coda il lavoro con
         *    WorkManager.enqueue() semplice, non enqueueUniqueWork, quindi
         *    nulla impedisce che parta insieme al giro periodico (quello si'
         *    "unique"). Corretto con una guardia di processo in
         *    Controllore.doWork() (vedi li' per i dettagli): un solo giro
         *    alla volta, chiunque lo richieda.
         *
         * 2. L'icona "hai scritto al venditore" (v1.28.0) diceva il
         *    contrario di cio' che l'utente vuole: doveva comparire per un
         *    messaggio SENZA offerta ("se e' stata fatta un'offerta abbiamo
         *    gia' il testo con l'importo"), non per qualunque proposta. Il
         *    dato per farlo bene (una conversazione Vinted senza offerta,
         *    l'elenco chat di Subito) non e' mai stato catturato da una
         *    sonda: l'icona e' tolta invece di indovinare i nomi dei campi
         *    — vedi i commenti in StatoRicerca.kt e SchermataRicerca.kt.
         *
         * --- versione precedente ---
         * versionCode = 54
         * versionName = "1.30.0"
         * --- fine versione precedente ---
         */
```


**riga 408:**
```
/*
         * v1.32.0 (2026-09-02) — due correzioni, dalla stessa sonda
         * (bdc42069-api.json, la conversazione Vinted con jhnmadsu, "Rtx 3060
         * gaming oc 12gb - Gigabyte") e una nuova.
         *
         * 1. "annunci con una mia proposta e un rilancio da parte del
         *    venditore sono scomparsi": non erano scomparsi, restavano
         *    marcati "Rifiutata" (la mia ultima offerta) anche quando il
         *    venditore aveva gia' rilanciato — la lettura riconosceva solo i
         *    messaggi "offer_request_message" (le mie offerte), non
         *    "offer_message" (i suoi rilanci), un tipo distinto trovato
         *    nella sonda. Corretto in Offerte.leggiConversazioneVinted (vedi
         *    li' per i dettagli): ora si distinguono i messaggi miei da
         *    quelli della controparte per user_id, non per tipo, e la
         *    scheda (SchermataRicerca.RigaOfferta) mostra entrambi gli
         *    importi quando c'e' un rilancio pendente.
         *
         * 2. "nella visualizzazione estesa non e' possibile scorrere le
         *    foto": la vista estesa mostrava sempre e solo la prima foto.
         *    Annuncio ora porta l'elenco completo (Annuncio.immagini) e la
         *    vista usa un HorizontalPager quando ce n'e' piu' di una (vedi
         *    SchermataRicerca.kt, RigaAnnuncio). Su Subito e' un dato certo
         *    (stesso array "images" gia' letto per la prima foto); su Vinted
         *    e' un'estensione ragionevole ma non ancora confermata da una
         *    sonda col contenuto vero — vedi il commento su Annuncio.immagini.
         *    La seconda meta' della segnalazione ("la descrizione appare
         *    solo per Subito") non e' un bug: il catalogo di ricerca Vinted
         *    non espone quel campo, e non da oggi — vedi Annuncio.kt.
         *
         * --- versione precedente ---
         * versionCode = 55
         * versionName = "1.31.0"
         * --- fine versione precedente ---
         */
```


**riga 458:**
```
/*
             * v0.5.0 — con Compose l'APK non sfoltito arrivava a 18 MB.
             * Si attiva la rimozione del codice e delle risorse inutilizzate,
             * ma senza rinominare nulla (vedi proguard-rules.pro): i metodi del
             * ponte JavaScript sono chiamati per nome dalla pagina.
             *
             * --- versione precedente (fino alla v0.4.6, 2026-08-30) ---
             * isMinifyEnabled = false
             * --- fine versione precedente ---
             */
```


**riga 487:**
```
/*
 * v0.2.0 (2026-08-30) — prima dipendenza esterna del progetto.
 * androidx.webkit serve per addDocumentStartJavaScript: iniettare il CSS
 * PRIMA che la pagina esegua i suoi script, cosi' i banner non lampeggiano
 * e in Fase 2 le card scartate non vengono mai disegnate.
 */
```


**riga 496:**
```
/*
     * v0.5.0 — Compose e Material 3.
     * Il resto dell'interfaccia e' ancora a viste tradizionali e ci resta finche'
     * non viene riscritta (punti 2 e seguenti): Compose e le viste convivono
     * nella stessa schermata, e questo permette di procedere un pezzo per volta
     * senza mai avere una build inutilizzabile.
     */
```


### Annuncio.kt


**riga 3:**
```
/*
 * Subito+ — modello comune degli annunci
 * v0.5.1 (2026-08-30)
 *
 * Ogni fonte restituisce cose diverse; qui tutto diventa la stessa cosa.
 * L'identificatore porta con se' la fonte ("subito" / "vinted") perche'
 * due piattaforme possono usare lo stesso numero per annunci diversi.
 *
 * v0.5.1 — "luogo" e "dettaglio" erano finiti nello stesso campo, e in elenco
 * accanto agli annunci Subito compariva la localita' mentre accanto a quelli
 * Vinted compariva la marca: due cose diverse nella stessa posizione.
 * Ora sono due campi distinti e ognuno va al suo posto.
 */
```


**riga 29:**
```
/*
     * v1.2.0 — quello che serve per i preferiti, ricavato dalla risposta di
     * ricerca e non altrove:
     *  - urn: identificatore lungo di Subito ("id:ad:<uuid>:list:<numero>"),
     *    che e' la forma richiesta dal suo servizio preferiti. Il numero da
     *    solo non basta.
     *  - venditore: id del venditore. Su Vinted e' l'unica via per il luogo
     *    (LuoghiVinted). Su Subito, dalla v1.30.0, e' "advertiser.user_id"
     *    dei risultati di ricerca, e serve alle recensioni (RecensioniSubito).
     *  - preferitoIniziale: Vinted lo dichiara gia' nei risultati; Subito no,
     *    e li' lo stato arriva da una chiamata a parte.
     */
```


**riga 41:**
```
/*
     * v1.7.0 — due campi che servono solo alla vista estesa:
     *  - immagineGrande: la stessa foto in una resa adatta a tutta la larghezza
     *    dello schermo. Quella d'elenco e' 310 px (Vinted) e stirata su 1080
     *    si vede sgranata. Verificato sul campo quali rese esistono davvero:
     *    Subito ?rule=gallery-mobile-3x-auto -> 1164x748 (le altre danno errore),
     *    Vinted photo.url -> f800.
     *  - descrizione: il testo dell'annuncio, che su Subito arriva gia' con i
     *    risultati (campo "body") e riempie lo spazio vuoto accanto ai dati.
     *    Vinted nel catalogo non lo espone: li' resta vuoto e la colonna sparisce.
     *    (Non e' un bug da correggere qui: e' l'elenco di ricerca Vinted che
     *    non porta il testo, vedi FonteVinted.leggi in Fonti.kt.)
     *
     *  - immagini (v1.32.0): TUTTE le foto dell'annuncio, non solo la prima,
     *    per farle scorrere nella vista estesa (SchermataRicerca.RigaAnnuncio).
     *    Su Subito viene dallo stesso array "images" gia' letto per la prima
     *    foto (FonteSubito.leggi la leggeva solo in posizione 0); su Vinted
     *    dal campo "photos" (plurale, distinto da "photo" singolare gia' in
     *    uso) — visto SOLO come nome di campo nell'elenco chiavi di una
     *    diagnostica esportata, non ancora in una sonda con il contenuto
     *    vero: si presume la stessa forma dell'oggetto "photo" singolare
     *    (stesso "url"), perche' e' la stessa API. Se restasse vuoto o
     *    sbagliato per Vinted, la vista estesa ripiega da sola su una sola
     *    foto (vedi sotto) — nessun rischio, solo da confermare con una
     *    sonda vera il giorno che si vuole essere certi anche li'.
     */
```


**riga 74:**
```
/** Chiave usata nell'archivio degli scartati. Gli annunci Subito restano
     *  con l'ID nudo per compatibilita' con quanto gia' nascosto dalla v0.3.0. */
```


### Fonti.kt


**riga 3:**
```
/*
 * Subito+ — adattatori delle fonti
 * v0.4.0 (2026-08-30) — nuovo, Fase 3
 *
 * Un adattatore riceve una ricerca e restituisce una lista di Annuncio.
 * Non fa rete da solo: costruisce l'URL e interpreta la risposta.
 * A eseguire la chiamata e' il WebView della fonte (vedi MotoreRicerca),
 * perche' e' li' che vivono sessione e cookie veri.
 *
 * Aggiungere eBay o Wallapop domani = aggiungere un oggetto qui sotto.
 */
```


**riga 25:**
```
/*
     * v1.2.0 — preferiti. I contratti sono quelli osservati sul campo con la
     * sonda, non dedotti: due piattaforme, due modi completamente diversi.
     *
     * @param utente identificatore dell'utente sulla piattaforma; stringa vuota
     *        quando non e' ancora noto, e in quel caso il preferito non si tocca.
     */
```


**riga 34:**
```
/** Elenco dei preferiti gia' presenti sulla piattaforma; null se non serve. */
```


**riga 36:**
```
/** Dalle risposta dell'elenco alle chiavi usate dall'app. */
```


**riga 39:**
```
/*
     * v1.8.0 — gli stessi preferiti, ma come annunci da mostrare.
     * La forma della risposta e' stata osservata una volta sola, quindi qui si
     * e' tolleranti: si cerca l'elenco fra i nomi possibili e si scarta cio'
     * che non si riesce a leggere, invece di pretendere una struttura esatta.
     * Se torna vuoto, la schermata lo dice e offre di aprire il sito: meglio
     * un vicolo cieco dichiarato che una lista vuota senza spiegazione.
     */
```


**riga 50:**
```
/**
 * Una chiamata da eseguire dentro la sessione della fonte.
 *
 * v1.6.0 — due aggiunte, entrambe nate da un errore visto sul telefono:
 *
 * @param csrf    aggiunge le intestazioni anti-CSRF raccolte NELLA PAGINA al
 *                momento della chiamata (vedi MotoreRicerca). Vinted rifiuta
 *                con 403 "access_denied" ogni POST che non le porta: la GET
 *                passa con i soli cookie, la POST no. E' la ragione per cui il
 *                cuore su Vinted non salvava.
 * @param ripiego una seconda strada da provare se la prima non risponde 2xx.
 *                Serve dove il contratto e' stato osservato una volta sola e
 *                non si sa se l'indirizzo giusto sia quello relativo al sito o
 *                quello diretto: si prova, non si indovina.
 * @param paginaPrima v1.26.0 — se non nulla, la sessione ci naviga PRIMA di
 *                fare la chiamata (e aspetta che finisca di caricare). Nata
 *                per i preferiti Vinted: vedi il commento su MotoreRicerca.azione().
 */
```


**riga 77:**
```
/* --------------------------------------------------------------------- */
```


**riga 83:**
```
/*
     * Endpoint confermato dalla sonda:
     *   https://hades.subito.it/v1/search/items?c=2&t=s&lim=0
     * Risposta: { count_all, lines, start, filters, checknew, ads:[...] }
     * t=s -> annunci in vendita. sort=datedesc -> dal piu' recente.
     */
```


**riga 120:**
```
/*
             * v0.4.6 — CORREZIONE. cdn_base_url senza parametri restituisce la foto
             * originale, diversi mega, per riempire un riquadro di un centimetro:
             * era la ragione principale delle attese lunghissime in lista.
             * "rule" e' il nome della resa lato CDN, osservato nel traffico del sito.
             *
             * --- versione precedente (v0.4.0, 2026-08-30) ---
             * val img = a.optJSONArray("images")?.optJSONObject(0)?.optString("cdn_base_url")
             * --- fine versione precedente ---
             */
```


**riga 135:**
```
/*
             * v1.7.0 — resa grande per la vista estesa. I nomi delle rese non
             * si inventano: provati uno per uno sul CDN vero.
             *   gallery-mobile-3x-auto   1164x748   <- questa
             *   gallery-desktop-3x-auto  1860x1196  (troppo per un telefono)
             *   large-fixed-card-3x-auto  900x600   (quella d'elenco)
             *   gallery-3x-auto, large, hero-3x-auto, zoom-3x-auto -> errore
             */
```


**riga 145:**
```
/*
             * v1.32.0 — TUTTE le foto, non solo la prima: stesso array
             * "images", stessa resa "gallery-mobile-3x-auto" gia' verificata
             * sopra per la posizione 0, solo applicata a ogni elemento invece
             * che al solo primo. Serve alla vista estesa per farle scorrere
             * (SchermataRicerca.RigaAnnuncio).
             */
```


**riga 177:**
```
/*
                 * v1.30.0 (2026-09-01) — NUOVO: fino a qui "venditore" restava
                 * vuoto per Subito (il campo esiste in Annuncio dalla v1.2.0
                 * ma solo Vinted lo riempiva, per il luogo). "advertiser.user_id"
                 * c'e' gia' in ogni risultato di ricerca — verificato sul campo,
                 * es. "advertiser":{"user_id":"144352772",...} — e ora serve
                 * alle recensioni del venditore (RecensioniSubito.kt), stesso
                 * ruolo che "venditore" gia' aveva per Vinted.
                 */
```


**riga 192:**
```
/*
     * v0.5.1 — annuncio gia' venduto.
     * Subito non espone un campo booleano dedicato: l'informazione arriva come
     * una delle "features", con etichetta o valore testuale. Si cerca quindi il
     * marcatore nel testo, senza dipendere dal nome esatto del campo, che e'
     * proprio il genere di cosa che cambia da una versione all'altra del sito.
     */
```


**riga 221:**
```
/** urn della forma id:ad:<uuid>:list:<numero> */
```


**riga 227:**
```
/** v1.1.0 — la condizione sta fra le features, come il prezzo. */
```


**riga 247:**
```
/*
     * L'indirizzo della scheda: si preferisce sempre quello dichiarato dalla
     * risposta. La ricostruzione a mano e' solo una rete di sicurezza, perche'
     * lo slug include anche il comune e non e' garantito che coincida.
     */
```


**riga 268:**
```
/*
     * v1.2.0 — preferiti Subito, contratti osservati con la sonda:
     *   POST   /hades/v1/favorites/search/users/<id>/items   {"urn":"..."}   -> 200
     *   DELETE /hades/v1/favorites/search/users/<id>/items?urns=<urn>        -> 204
     * Il percorso e' relativo a www.subito.it, che fa da proxy verso hades:
     * chiamato in relativo resta stessa origine e si porta dietro la sessione.
     *
     * Attenzione all'identificatore: qui vuole l'URN lungo, non il numero che
     * compare nel link della scheda.
     */
```


**riga 278:**
```
/*
     * v1.6.0 — l'indirizzo dei preferiti in due forme.
     * La sonda l'aveva visto come percorso relativo al sito (www.subito.it fa
     * da proxy verso hades). Se quel proxy non c'e' o cambia, la stessa risorsa
     * sta sul servizio diretto. Si prova la prima, e la seconda solo se serve.
     */
```


**riga 308:**
```
/*
     * v1.10.0 (2026-08-31) — CORREZIONE: l'elenco preferiti non porta annunci,
     * porta soltanto riferimenti.
     *
     * Verificato sul campo (era l'errore "elenco non letto" mostrato in app):
     *   GET .../favorites/search/users/<id>/items
     *   -> {"favorite_ads":[{"urn":"id:ad:cd0a0bd6-…:list:658151441",
     *                        "insert_date":"2026-08-31 10:43:14"}, …]}
     * Niente titolo, prezzo, foto: solo l'urn e quando l'hai messo nei
     * preferiti. La v1.8.0 lo trattava come se fosse gia' un elenco di
     * annunci, con la stessa forma della ricerca — e infatti non lo era.
     *
     * Ogni urn si risolve con una seconda chiamata, anch'essa verificata sul
     * browser:
     *   GET .../v1/search/items?urn=<urn>  ->  {"ads":[<un solo annuncio>]}
     * la STESSA forma della ricerca normale: si riusa leggi() invariato.
     * Provato anche a chiedere piu' annunci insieme: "urn" ripetuto tiene
     * solo l'ULTIMO valore, "ids" viene ignorato del tutto e torna un elenco
     * a caso. Non esiste una chiamata multipla: una richiesta per annuncio,
     * a due alla volta (vedi StatoRicerca.risolviPreferitiSubito).
     *
     * Questo metodo quindi non basta piu' da solo: resta per compatibilita'
     * dell'interfaccia, la vista reale usa preferitiUrns() + preferitoRisolvi().
     *
     * --- versione precedente (v1.8.0, 2026-08-31) ---
     * override fun preferitiAnnunci(json: String): List<Annuncio> = try {
     *     val r = JSONObject(json)
     *     val arr = r.optJSONArray("ads") ?: r.optJSONArray("items")
     *         ?: r.optJSONArray("favorites") ?: r.optJSONObject("data")?.optJSONArray("ads")
     *     … normalizzava e chiamava leggi() — l'elenco pero' non ha questi campi
     * } catch (t: Throwable) { emptyList() }
     * --- fine versione precedente ---
     */
```


**riga 343:**
```
/** Gli urn dei preferiti, nell'ordine dell'elenco (il piu' recente per primo). */
```


**riga 354:**
```
/** La chiamata che risolve un singolo urn in un annuncio completo. */
```


**riga 383:**
```
/* --------------------------------------------------------------------- */
```


**riga 389:**
```
/*
     * La sonda ha visto la pagina catalogo chiamare, in relativo:
     *   /api/v2/promoted_closets?...&search_text=rtx+16gb&order=newest_first
     * La stessa famiglia serve gli articoli: /api/v2/catalog/items.
     * Chiamata in relativo dalla pagina vinted.it: stessa origine, stessi cookie.
     */
```


**riga 418:**
```
/*
             * v0.4.6 — prima si prende la miniatura, poi eventualmente la foto piena.
             * photo.url punta alla resa f800: troppo per una riga di elenco.
             *
             * --- versione precedente (v0.4.0, 2026-08-30) ---
             * val img = foto?.optString("url")?.takeIf { it.startsWith("http") } ?: miniatura(foto)
             * --- fine versione precedente ---
             */
```


**riga 428:**
```
/*
             * v1.32.0 — tutte le foto per la vista estesa (vedi il commento su
             * Annuncio.immagini): il campo "photos" (plurale) e' visto nel
             * solo elenco chiavi di una diagnostica esportata, non ancora in
             * una sonda col contenuto vero — si presume la stessa forma di
             * "photo" (singolare), gia' verificato qui sopra ("url" -> resa
             * f800). Se l'array manca o i suoi elementi non hanno "url", il
             * risultato e' semplicemente vuoto e la vista estesa ripiega da
             * sola sulla sola foto singola: nessun rischio di crash o di dato
             * sbagliato, solo — nel peggiore dei casi — niente scorrimento
             * finche' una sonda vera non conferma la forma dell'array.
             */
```


**riga 479:**
```
/*
     * v0.4.2 (2026-08-30) — data di inserimento.
     * Il catalogo Vinted non espone sempre lo stesso campo: si prova in ordine
     * quelli noti e si ripiega sul timestamp della foto, che nella pratica
     * coincide quasi sempre con la pubblicazione.
     *
     * --- versione precedente (v0.4.0) ---
     * dataMillis = tsSecondi(foto?.optJSONObject("high_resolution")?.optLong("timestamp"))
     * --- fine versione precedente ---
     */
```


**riga 498:**
```
/*
         * v1.0.2 (2026-08-30) — ultima risorsa, ed e' quella che funziona.
         * Nel catalogo Vinted i campi con la data spesso non ci sono affatto:
         * il risultato era "data non disponibile" su ogni riga.
         * L'indirizzo della foto pero' finisce sempre con l'ora di caricamento:
         *   .../06_01dea_HTkh.../f800/1769551993.webp?s=...
         * e per un annuncio dell'usato la foto viene caricata quando l'annuncio
         * viene pubblicato. E' una stima, non una dichiarazione, ma sbaglia di
         * minuti dove prima non si diceva nulla.
         */
```


**riga 520:**
```
/** Accetta numeri in secondi o millisecondi, e testo in formato ISO. */
```


**riga 532:**
```
/*
     * v0.5.1 — la localita' nel catalogo Vinted non ha una posizione fissa:
     * a volte sta sull'articolo, a volte sul venditore, spesso non c'e'.
     * Si prova nell'ordine e ci si accontenta di cio' che si trova.
     */
```


**riga 537:**
```
/*
     * v1.2.0 (2026-08-31) — la v1.1.0 aveva ragione a meta'.
     *
     * Nel CATALOGO il luogo davvero non c'e', e quanto scritto sotto resta
     * esatto. Ma la piattaforma lo conosce: sta sul PROFILO del venditore,
     *   GET /api/v2/users/<id> -> { city, country_title_local, expose_location }
     * verificato sul browser con la sessione vera, 12 venditori su 12.
     * Se ne occupa LuoghiVinted, una chiamata per venditore, con cache.
     * Qui l'annuncio continua a nascere senza luogo: e' corretto, perche' in
     * questa risposta non c'e'. La condizione resta in "dettaglio" e riempie
     * la riga finche' il profilo non ha risposto.
     */
```


**riga 549:**
```
/*
     * v1.1.0 (2026-08-30) — guardando la risposta vera.
     *
     * Nel catalogo Vinted il luogo NON esiste. L'articolo ha:
     *   id, title, price, is_visible, brand_title, path, user, conversion, url,
     *   promoted, photos, photo, favourite_count, is_favourite, service_fee,
     *   total_item_price, view_count, size_title, status, item_box, ...
     * e l'oggetto "user" contiene soltanto id, login, profile_url, photo,
     * business. Nessuna citta', nessun paese, da nessuna parte.
     *
     * Le due versioni precedenti cercavano qualcosa che non c'e': la prima in
     * tre posti fissi, la seconda in tutto l'oggetto. Entrambe tornavano vuote,
     * ed era l'unico esito possibile. Al posto del luogo si mostra la
     * CONDIZIONE, che c'e' sempre ed e' cio' che serve davvero sull'usato.
     *
     * --- versione precedente (v1.0.3, 2026-08-30) ---
     * private fun luogoVinted(it0: JSONObject): String {
     *     cerca(it0, CHIAVI_LUOGO)?.let { return it }
     *     cerca(it0, CHIAVI_CODICE_PAESE)?.let { return paese(it) }
     *     return ""
     * }
     * ...piu' le tabelle dei paesi e la ricerca annidata, tutte rimosse.
     * --- fine versione precedente ---
     */
```


**riga 595:**
```
/*
     * v1.21.0 (2026-08-31) — DIAGNOSTICA per "su Vinted vedo solo gli annunci
     * di oggi, quelli di ieri o prima compaiono solo su Subito".
     *
     * dataVinted() (sopra) ammette gia' da sola, nei suoi commenti, che i
     * campi diretti (CAMPI_DATA) spesso mancano nel catalogo, e che la stima
     * dall'indirizzo della foto "e' una stima, non una dichiarazione". Se
     * quella stima fallisse proprio per gli annunci NON di oggi (per esempio
     * perche' l'indirizzo della foto cambia forma dopo che Vinted la
     * rielabora), ogni annuncio piu' vecchio di oggi prenderebbe dataMillis=0
     * e l'ordinamento "piu' recenti" (StatoRicerca.risultati) lo spingerebbe
     * in fondo alla lista — sembrerebbe sparito, mentre e' solo mal datato.
     * E' un sospetto coerente con quanto gia' scritto nel codice, ma non
     * ancora una prova.
     *
     * "chiavi()" e' gia' solo diagnostica (vedi l'interfaccia Fonte: "com'e'
     * fatto il primo elemento") e finisce dritta nel log che la sonda esporta
     * — zero rischio di toccare cosa si vede in lista. Qui si aggiunge, oltre
     * ai campi del primo annuncio, quanti annunci DI QUESTA PAGINA hanno preso
     * la data da un campo diretto, quanti dalla stima sulla foto, e quanti da
     * nessuna delle due: la prossima sonda dira' se il sospetto regge.
     *
     * --- versione precedente (fino alla v1.20.0) ---
     * override fun chiavi(json: String): String =
     *     (JSONObject(json).optJSONArray("items")?.optJSONObject(0)?.keys()
     *         ?.asSequence()?.toList() ?: emptyList<String>()).joinToString(",")
     * --- fine versione precedente ---
     */
```


**riga 641:**
```
/*
     * v1.2.0 — preferiti Vinted, contratto osservato con la sonda:
     *   POST /api/v2/user_favourites/toggle  {"type":"item","user_favourites":[<id>]}
     *        -> {"code":0,"message":"Ok"}
     * Un solo endpoint per mettere e togliere: e' un interruttore, non due verbi.
     * Lo stato di partenza non richiede chiamate, sta gia' nei risultati
     * (is_favourite), quindi l'elenco serve solo come rete di sicurezza.
     */
```


**riga 649:**
```
/*
     * v1.26.0 (2026-09-01) — CORREZIONE, con la prova in mano: l'utente ha
     * catturato con la sonda un'aggiunta e una rimozione di preferito fatte
     * DENTRO una vera pagina di un articolo Vinted (quella su cui e' arrivato
     * navigando dai risultati, con foto, venditore, tutto) — e li' funziona,
     * identica per indirizzo e corpo a quella che l'app manda gia' (stesso
     * "/api/v2/user_favourites/toggle", stesso {"type":"item","user_favourites":[…]}).
     * Quindi non e' MAI stato un problema di indirizzo, corpo o intestazioni
     * (v1.7.0, v1.11.0 le avevano gia' trovate giuste) — e' la PAGINA da cui
     * parte la chiamata. La sessione nascosta dell'app (MotoreRicerca) carica
     * la home di vinted.it UNA volta all'avvio e ci resta sopra per tutta la
     * vita del processo: mai una vera navigazione, mai i tanti caricamenti
     * (foto, venditore, pubblicita', contatori) che accompagnano l'apertura
     * reale di un articolo e che verosimilmente contano per chi giudica se la
     * sessione e' "umana". Ora "paginaPrima" dice alla sessione di caricare
     * DAVVERO la pagina dell'annuncio (a.url, la stessa che si aprirebbe
     * toccandolo) subito prima di provare il preferito — la stessa sequenza
     * catturata funzionante. Vedi il commento su MotoreRicerca.azione().
     *
     * --- versione precedente (fino alla v1.25.0) ---
     * private fun toggle(a: Annuncio) = Chiamata(
     *     "POST", "/api/v2/user_favourites/toggle",
     *     JSONObject()
     *         .put("type", "item")
     *         .put("user_favourites", JSONArray().put(a.id.toLongOrNull() ?: a.id))
     *         .toString(),
     *     csrf = true
     * )
     * --- fine versione precedente ---
     */
```


**riga 679:**
```
/*
     * v1.6.0 (2026-08-31) — CORREZIONE: la POST tornava 403.
     *
     * Verificato sul browser, stessa sessione, stesso corpo:
     *   GET  /api/v2/catalog/items      -> 200
     *   POST /api/v2/user_favourites/toggle
     *        -> 403 {"code":106,"message_code":"access_denied"}
     * I cookie bastano a leggere, non a scrivere: Vinted vuole anche le
     * intestazioni anti-CSRF, che il suo sito prende dalla pagina. Ora le
     * prende anche l'app, dalla pagina vera, al momento della chiamata
     * (csrf = true, vedi MotoreRicerca).
     *
     * --- versione precedente (v1.2.0, 2026-08-30) ---
     * private fun toggle(a: Annuncio) = Chiamata(
     *     "POST", "/api/v2/user_favourites/toggle",
     *     JSONObject().put("type","item")
     *         .put("user_favourites", JSONArray().put(a.id.toLongOrNull() ?: a.id))
     *         .toString())
     * --- fine versione precedente ---
     */
```


**riga 716:**
```
/*
     * v1.8.0 — su Vinted l'elenco dei preferiti usa lo stesso serializzatore
     * del catalogo: {"items":[…]}. Quindi il lettore e' gia' scritto.
     */
```


**riga 734:**
```
/* --------------------------------------------------------------------- */
```


**riga 738:**
```
/*
 * v0.4.1 (2026-08-30) — CORREZIONE
 * Vinted restituisce l'indirizzo della scheda a volte assoluto, a volte in forma
 * relativa (/items/123). loadUrl con un percorso relativo non fa nulla e non
 * segnala errore: era il motivo per cui toccare un risultato Vinted sembrava
 * non avere effetto. Ogni indirizzo passa da qui prima di uscire dall'adattatore.
 */
```


### Offerte.kt


**riga 3:**
```
/*
 * Subito+ — le offerte che hai fatto
 * v1.4.0 (2026-08-31) — nuovo
 *
 * IL CONTRATTO, RICAVATO DALLA SONDA (offerte.json, v1.3.0)
 * Le risposte di ricerca non sanno chi le chiede, quindi le offerte non ci
 * sono e non ci possono essere. L'elenco sta altrove, e ora si sa dove:
 *
 *   GET https://hades.subito.it/v1/p2p/transactions?role=buyer   -> 200
 *   {"transactions":[{
 *      "transaction_id":"090614b4-…",
 *      "transaction_type":"fullshipping",
 *      "state":"PURCHASE_REQUESTED",
 *      "state_info":{"active":true,"label":"Proposta inviata","actions":[…]},
 *      "item_id":"ad5ad17580-f1c5-4c21-9c2b-97c3ec4a0d86list657012105",
 *      "item_price":17000, "buyer_price":17000, "seller_revenue":17000,
 *      "ad_info":{"title":"rtx 3060 oc 12 gb","price":30000, …},
 *      "updated_at":"2026-08-30T23:35:37+02:00"}, …]}
 *
 * DUE COSE CHE VANNO LETTE BENE
 *
 * 1) Gli importi sono in CENTESIMI. item_price 17000 = 170 €, e ad_info.price
 *    30000 = 300 € e' il prezzo dell'annuncio. Cioe': hai proposto 170 su 300.
 *    Prenderlo per euro darebbe "offerti 17.000 €", che e' il tipo di errore
 *    che non si nota finche' non lo si vede su un annuncio da 300 €.
 *
 * 2) item_id NON e' l'id dell'annuncio: e' "ad" + uuid + "list" + numero.
 *      ad5ad17580-f1c5-4c21-9c2b-97c3ec4a0d86list657012105
 *                                             ^^^^^^^^^ questo
 *    Ed e' lo stesso numero che sta in fondo all'urn della ricerca
 *    ("id:ad:<uuid>:list:657012105", vedi FonteSubito.idDaUrn). E' li' che le
 *    due risposte si toccano, ed e' l'unico punto in cui si toccano.
 */
```


**riga 36:**
```
/*
 * v1.10.0 (2026-08-31) — le offerte Vinted, trovate.
 *
 * Il commento sotto ("PERCHE' SOLO SUBITO") diceva che /api/v2/my_orders non
 * porta l'id dell'articolo e che /api/v2/conversations e /api/v2/offers danno
 * 404 — vero, ma erano gli indirizzi sbagliati. Letto il bundle vero del sito
 * (marketplace-web-assets.vinted.com, la pagina /inbox) il client usa:
 *
 *   GET /inbox?page=1&per_page=25
 *       -> {"conversations":[{"id":…, …}], "pagination":{…}}
 *       (l'elenco delle conversazioni; basta per sapere QUALI guardare)
 *
 *   GET /conversations/<id>
 *       -> {"conversation":{"transaction":{
 *              "item_id": 123456789,
 *              "offer_price": {"amount":"170.00","currency_code":"EUR"},
 *              "item_title": "…", "item_url": "…",
 *              "item_photo": {"url":"…"},
 *              "status": "…", "available_actions":[…]
 *          }}}
 *       (una chiamata per conversazione: qui c'e' l'id dell'articolo e
 *        quanto hai offerto — esattamente quello che mancava)
 *
 * Non c'e' un elenco unico come per Subito, quindi per Vinted il costo e'
 * "una chiamata di elenco" + "una chiamata per conversazione recente": si
 * limita alle ULTIME_CONVERSAZIONI_VINTED per non intasare la sessione a ogni
 * aggiornamento (ogni VALIDITA_MS, quindi non capita spesso).
 *
 * Gli importi Vinted sono STRINGHE decimali ("170.00"), non centesimi interi
 * come su Subito (vedi FonteVinted.leggi, stesso formato "price.amount"): si
 * convertono moltiplicando per 100, cosi' il resto del file — pensato per i
 * centesimi di Subito — resta uguale per entrambe le fonti.
 *
 * Le due fonti condividono la stessa mappa (perChiave): non si puo' piu'
 * sostituirla intera a ogni lettura (leggi() lo faceva, ed e' cosi' che
 * un aggiornamento di Subito cancellava quelle di Vinted, o viceversa).
 * Ora si sostituisce solo la fetta della fonte appena letta — sostituisciFonte,
 * sotto — riconoscendo le chiavi Vinted dal prefisso "v:" (vedi Annuncio.chiave).
 */
```


**riga 85:**
```
/** Non piu' di una lettura ogni cinque minuti: e' un elenco, non un flusso. */
```


**riga 88:**
```
/** v1.10.0 — quante conversazioni Vinted recenti si controllano a ogni giro. */
```


**riga 91:**
```
/**
     * @param centesimi quanto hai proposto
     * @param etichetta lo stato come lo scrive la piattaforma ("Proposta inviata")
     * @param attiva    false per una proposta chiusa, annullata o scaduta
     *
     * v1.8.0 — il resto serve a mostrarle in home senza chiedere altro a
     * nessuno: la risposta delle transazioni porta gia' ad_info con titolo,
     * foto e prezzo dell'annuncio, e updated_at. Tutto quello che serve a
     * disegnare una riga e' li' dentro.
     *
     * v1.10.0 — "fonte" e' nuovo: serve a costruire l'URL giusto (due siti,
     * due formati) senza indovinare dal contenuto della chiave.
     *
     * v1.32.0 — "controproposta" e' nuovo: quando il venditore Vinted
     * rilancia (un messaggio di tipo "offer_message", distinto dalla mia
     * offerta "offer_request_message" — vedi leggiConversazioneVinted piu'
     * sotto), qui ci sta il SUO importo, in centesimi; 0 quando non c'e'
     * nessun rilancio pendente. "centesimi" resta SEMPRE la mia ultima
     * offerta, per non cambiare il significato del campo usato ovunque gia'.
     */
```


**riga 120:**
```
/** v1.9.0 — lo stato interno ("PURCHASE_REQUESTED", "CLOSED"): non si
         *  mostra, serve a riconoscere gli ordini conclusi senza dipendere
         *  dalle sole parole italiane dell'etichetta. */
```


**riga 125:**
```
/** v1.32.0 — il rilancio del venditore (solo Vinted, per ora: solo
         *  li' si e' vista la prova di un messaggio di controproposta
         *  distinto). 0 = nessun rilancio pendente. */
```


**riga 130:**
```
/*
         * v1.10.0 — un URL per fonte.
         *
         * --- versione precedente (fino alla v1.9.0, solo Subito) ---
         * val url: String get() = "https://www.subito.it/vi/$id.htm"
         * --- fine versione precedente ---
         */
```


**riga 137:**
```
/** Verificato sul campo: /vi/<id>.htm apre la scheda giusta su Subito. */
```


**riga 174:**
```
/* file rovinato: si riparte da vuoto */
```


**riga 177:**
```
/*
     * v1.10.0 — prima guardava solo Subito ("if (a.fonte == "subito") … else null"):
     * ora la chiave (Annuncio.chiave) distingue gia' le due fonti da sola
     * ("v:123" per Vinted, "123" per Subito), quindi basta cercarla.
     *
     * --- versione precedente (v1.4.0, 2026-08-31) ---
     * fun perAnnuncio(a: Annuncio): Offerta? =
     *     if (a.fonte == "subito") perChiave[a.chiave] else null
     * --- fine versione precedente ---
     */
```


**riga 192:**
```
/**
     * Rilegge l'elenco dalla piattaforma. Una chiamata sola per tutta la lista:
     * non c'e' niente da chiedere annuncio per annuncio.
     *
     * @param forza ignora la validita' e rilegge comunque
     * @param fatto invocato a lettura riuscita, per ridisegnare le righe
     *
     * v1.10.0 — ora aggiorna DUE fonti, Subito (una chiamata) e Vinted (una
     * chiamata di elenco piu' una per conversazione, vedi aggiornaVinted).
     * Ognuna avvisa "fatto" per conto proprio appena ha qualcosa di nuovo:
     * chi chiama non deve aspettare la piu' lenta per vedere la piu' rapida.
     */
```


**riga 204:**
```
/*
     * v1.22.0 (2026-08-31) — CORREZIONE: dopo la v1.21.0 (rilettura forzata a
     * ogni ritorno in app, vedi StatoRicerca) le offerte VERE di Subito
     * sparivano — proprio mentre quelle Vinted iniziavano finalmente a
     * comparire. La causa era qui, non li': "inCorso" tornava a "false"
     * SUBITO dopo aver AVVIATO le due chiamate (motore.azione e' asincrona:
     * la riga dopo parte prima che la risposta arrivi), non quando finivano
     * davvero. Con un solo punto che chiamava aggiorna() (l'avvio ricerca,
     * distanziato dai 5 minuti) non si notava mai. La v1.21.0 ha aggiunto tre
     * punti che possono scattare a pochi istanti l'uno dall'altro (onResume,
     * cambio scheda, ritorno alla home): due chiamate "aggiorna()" quasi
     * insieme partivano ENTRAMBE verso Subito (la guardia era gia' spenta),
     * e se la seconda risposta arrivava anche solo un attimo dopo la prima —
     * ordine di rete, non di invio — "sostituisciFonte" la prendeva per
     * buona e ci scriveva sopra, aggiunte comprese: bastava che UNA delle
     * due risposte fosse incompleta per cancellare quelle vere.
     *
     * Rimedio: "inCorso" resta vero finche' ENTRAMBE le chiamate (Subito e
     * Vinted) non hanno davvero risposto, non solo finche' non sono partite.
     * Una aggiorna() che arriva mentre l'altra e' ancora in volo viene
     * scartata dalla guardia com'era gia' pensato di fare — solo che prima
     * non succedeva mai per davvero.
     *
     * --- versione precedente (fino alla v1.21.0) ---
     * fun aggiorna(
     *     ctx: Context,
     *     motore: MotoreRicerca,
     *     forza: Boolean = false,
     *     fatto: () -> Unit = {}
     * ) {
     *     synchronized(this) {
     *         carica(ctx)
     *         if (inCorso) return
     *         if (!forza && System.currentTimeMillis() - ultimaLettura < VALIDITA_MS) return
     *         inCorso = true
     *     }
     *     motore.azione(
     *         FonteSubito,
     *         Chiamata("GET", "https://hades.subito.it/v1/p2p/transactions?role=buyer")
     *     ) { stato, corpo ->
     *         var cambiato = false
     *         synchronized(this) {
     *             if (stato in 200..299) {
     *                 cambiato = leggiSubito(corpo)
     *                 if (cambiato) salva(ctx)
     *             }
     *         }
     *         if (cambiato) fatto()
     *     }
     *     aggiornaVinted(ctx, motore) { cambiato -> if (cambiato) fatto() }
     *     synchronized(this) { ultimaLettura = System.currentTimeMillis(); inCorso = false }
     * }
     * --- fine versione precedente ---
     */
```


**riga 276:**
```
/*
         * v1.24.0 (2026-08-31) — DIAGNOSTICA: dopo la v1.22.0 (corretta la
         * guardia di rientro) l'utente segnala che le offerte Subito VERE
         * restano ancora assenti dalla lista, con l'app aggiornata. La sonda
         * manuale (SondaOfferte, "Sonda offerte" nel menu) su questo stesso
         * indirizzo torna 200 con transazioni vere — quindi l'indirizzo e il
         * formato sono giusti, e la sessione HA i cookie giusti almeno in
         * quel momento. Ma questo ramo — a differenza di aggiornaVinted(),
         * che dalla v1.20.0 scrive un log a ogni passo — non lasciava MAI
         * una riga in motore.diagnostica: un 401/403 di sessione, zero
         * transazioni, o un corpo che leggiSubito() non riesce a interpretare
         * sparivano tutti allo stesso modo, in silenzio. Non e' una
         * correzione — nessuna causa e' ancora esclusa — e' lo stesso
         * rimedio gia' usato per Vinted: la prossima diagnostica esportata
         * ("Esporta diagnostica" nel menu, il file ricerca-diagnostica.txt)
         * dira' finalmente DOVE si ferma la lettura automatica, invece di
         * lasciarlo indovinare.
         *
         * --- versione precedente (fino alla v1.23.0) ---
         * motore.azione(
         *     FonteSubito,
         *     Chiamata("GET", "https://hades.subito.it/v1/p2p/transactions?role=buyer")
         * ) { stato, corpo ->
         *     var cambiato = false
         *     synchronized(this) {
         *         if (stato in 200..299) {
         *             cambiato = leggiSubito(corpo)
         *             if (cambiato) salva(ctx)
         *         }
         *     }
         *     if (cambiato) fatto()
         *     unaFinita()
         * }
         * --- fine versione precedente ---
         */
```


**riga 338:**
```
/*
     * v1.10.0 — nuovo. Vedi il commento in cima al file per il contratto.
     * Due passi: l'elenco delle conversazioni (per sapere quali guardare),
     * poi una chiamata per ciascuna delle piu' recenti (per sapere se porta
     * un'offerta e su quale articolo). Le conversazioni che non sono legate
     * a una transazione (una semplice domanda al venditore) non hanno
     * "transaction", e si scartano senza errore.
     *
     * --- versione precedente (fino alla v1.19.0) ---
     * private fun aggiornaVinted(ctx: Context, motore: MotoreRicerca, fatto: (Boolean) -> Unit) {
     *     motore.azione(FonteVinted, Chiamata("GET", "/inbox?page=1&per_page=25")) { stato, corpo ->
     *         if (stato !in 200..299) return@azione
     *         val id = try {
     *             val arr = JSONObject(corpo).optJSONArray("conversations") ?: JSONArray()
     *             (0 until minOf(arr.length(), ULTIME_CONVERSAZIONI_VINTED))
     *                 .mapNotNull { arr.optJSONObject(it)?.opt("id")?.toString() }
     *         } catch (t: Throwable) { emptyList() }
     *         if (id.isEmpty()) {
     *             val cambiato = synchronized(this) {
     *                 val c = sostituisciFonteVinted(emptyMap())
     *                 if (c) salva(ctx)
     *                 c
     *             }
     *             fatto(cambiato)
     *             return@azione
     *         }
     *         val nuove = java.util.Collections.synchronizedMap(LinkedHashMap<String, Offerta>())
     *         var restanti = id.size
     *         id.forEach { convId ->
     *             motore.azione(FonteVinted, Chiamata("GET", "/conversations/$convId")) { s2, c2 ->
     *                 if (s2 in 200..299) leggiConversazioneVinted(c2)?.let { (chiave, o) -> nuove[chiave] = o }
     *                 val ultima: Boolean
     *                 synchronized(this) { restanti--; ultima = restanti <= 0 }
     *                 if (ultima) {
     *                     val cambiato = synchronized(this) {
     *                         val c = sostituisciFonteVinted(nuove)
     *                         if (c) salva(ctx)
     *                         c
     *                     }
     *                     fatto(cambiato)
     *                 }
     *             }
     *         }
     *     }
     * }
     * --- fine versione precedente ---
     */
```


**riga 385:**
```
/*
     * v1.20.0 (2026-08-31) — DIAGNOSTICA: le offerte Vinted non sono mai
     * comparse (nemmeno riavviando l'app da zero), e finora questa funzione
     * non scriveva UNA riga nel log in caso di problema — un 403, uno zero
     * conversazioni, una lettura fallita sparivano tutti allo stesso modo,
     * in silenzio. Non e' una correzione: nessuna delle cause possibili e'
     * ancora esclusa (sessione Vinted non autenticata come utente vero
     * dentro il WebView nascosto, /inbox bloccato come i preferiti,
     * conversazioni lette ma senza "transaction" utile, …). Ora ogni passo
     * lascia una riga in motore.diagnostica — lo stesso log che la sonda
     * gia' esporta — cosi' la prossima volta si vede DOVE si ferma invece
     * di dover indovinare.
     */
```


**riga 398:**
```
/*
     * v1.21.0 (2026-08-31) — CORREZIONE: trovata la causa vera per cui le
     * offerte Vinted non sono MAI comparse, nemmeno riavviando l'app.
     *
     * La sonda mandata in chat (api.json) questa volta ha catturato una vera
     * offerta fatta dal sito, non solo la ricerca: e mostra che il client
     * VERO chiama "/api/v2/inbox" e "/api/v2/conversations/<id>", con il
     * prefisso "/api/v2/" — esattamente come fanno GIA' tutte le altre
     * chiamate Vinted di questo file (preferiti, luoghi, utente corrente).
     * Qui, da soli, mancava. Senza quel prefisso Vinted risponde con la
     * pagina "la page n'existe pas" (404, verificata anche su un altro
     * candidato scartato in passato, /api/v2/conversations senza id) — la
     * funzione la scartava in silenzio (stato non 2xx -> return), sempre,
     * per ogni sessione: ecco perche' "offerte trovate" restava zero anche
     * a costo di riavviare tutto da capo.
     */
```


**riga 470:**
```
/*
     * v1.21.0 (2026-08-31) — CORREZIONE: anche quando la lettura arrivava a
     * buon fine, "transaction.offer_price" NON e' l'importo offerto — e' il
     * prezzo di LISTINO a cui la transazione e' agganciata. Prova diretta
     * dalla sonda: su un'offerta vera di 18 € su un annuncio da 30 €,
     * "transaction.offer_price" valeva 30.0 (uguale a "original_price_label":
     * "€30.00" nel messaggio), non 18. L'importo VERO offerto sta nel
     * messaggio della conversazione di tipo "offer_request_message":
     *   conversation.messages[i].entity.price.amount   -> "18.0"
     *   conversation.messages[i].entity.status_title    -> "In sospeso"
     * status_title e' gia' scritto da Vinted nella lingua dell'utente: meglio
     * di indovinare lo stato da transaction.status con terminata() come si
     * faceva prima. Si prende l'ULTIMO messaggio di quel tipo nell'elenco
     * (il piu' recente: i messaggi arrivano in ordine cronologico, come in
     * ogni normale elenco di chat) perche' un'offerta puo' essere rilanciata
     * piu' volte nella stessa conversazione.
     *
     * Bonus: ora che si sa cos'e' davvero "offer_price" (il prezzo di
     * listino), si puo' mostrarlo come prezzoAnnuncio invece di lasciarlo a
     * zero — prima non si poteva perche' si pensava (sbagliando) che quel
     * campo fosse gia' preso per l'offerta.
     *
     * --- versione precedente (fino alla v1.20.0) ---
     * private fun leggiConversazioneVinted(corpo: String): Pair<String, Offerta>? = try {
     *     val tr = JSONObject(corpo).optJSONObject("conversation")?.optJSONObject("transaction")
     *     val itemId = tr?.opt("item_id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
     *     val importo = tr?.optJSONObject("offer_price")?.optString("amount")?.toDoubleOrNull()
     *     if (tr == null || itemId == null || importo == null || importo <= 0) null
     *     else {
     *         val stato = tr.optString("status")
     *         val foto = tr.optJSONObject("item_photo")?.optString("url")
     *         "v:$itemId" to Offerta(
     *             centesimi = Math.round(importo * 100).toInt(),
     *             etichetta = stato,
     *             attiva = !terminata(stato),
     *             id = itemId,
     *             titolo = tr.optString("item_title"),
     *             immagine = foto?.takeIf { it.startsWith("http") },
     *             prezzoAnnuncio = 0,   // v1.10.0 — la transazione non porta il prezzo di listino, solo l'offerta.
     *             quando = System.currentTimeMillis(),
     *             stato = stato,
     *             fonte = "vinted"
     *         )
     *     }
     * } catch (t: Throwable) { null }
     * --- fine versione precedente ---
     */
```


**riga 517:**
```
/*
     * v1.25.0 (2026-08-31) — CORREZIONE, questa volta con la prova in mano:
     * la sonda di una vera offerta (3bd66d96-api.json, la stessa che ha dato
     * il contratto v1.21.0) mostra che ogni messaggio della conversazione
     * porta un "created_at_ts" vero ("2026-08-31T23:53:08+02:00") — un
     * fratello di "entity", non dentro: sta su "m", non su "entita". Prima
     * si ignorava e si scriveva "quando = System.currentTimeMillis()", cioe'
     * ADESSO, per OGNI lettura, di OGNI offerta Vinted, comprese quelle
     * vecchie di settimane. La home mostra solo le 6 piu' recenti
     * (StatoRicerca.offerteRecenti() -> Offerte.recenti(6)): un'offerta
     * Vinted, rileggendosi ogni 5 minuti con un orologio sempre "adesso",
     * vinceva per sempre il confronto con le date vere di Subito (aaa fatte
     * settimane fa, mai gia' aggiornate). Prima della v1.21.0 questo non si
     * vedeva perche' l'endpoint Vinted era rotto (v1.21.0 lo ha corretto) e
     * le offerte Vinted non arrivavano mai: le 6 posizioni erano tutte di
     * Subito. Da quando Vinted ha iniziato a funzionare, ha affollato tutte
     * e 6 le posizioni e le Subito vere sono sparite dalla vista — non e'
     * un problema di lettura (la sonda diretta su hades.subito.it, aggiunta
     * in diagnostica dalla v1.24.0, prendera' comunque quel caso se esiste
     * ancora), e' un problema di ORDINAMENTO. Corrisponde esattamente a
     * quanto riferito: "le offerte Subito si vedevano fino alla 1.20" — cioe'
     * fino a quando Vinted non ha iniziato a comparire.
     *
     * Seconda correzione, stessa sonda: la transazione porta anche
     * "is_completed" e "item_is_closed" (booleani veri, non testo da
     * indovinare). Servono per il secondo problema segnalato: un'offerta
     * Vinted "Accettata" rimasta in lista anche se l'annuncio non esiste
     * piu' (l'app apre l'url e Vinted risponde "Page not found" — l'oggetto
     * e' stato tolto dal catalogo, verosimilmente venduto/concluso altrove
     * nella conversazione, in un messaggio che non e' un
     * "offer_request_message" e che quindi terminata(statoTitolo) da solo
     * non vede mai). Ora "attiva" guarda anche questi due campi, non solo il
     * testo dello stato: una transazione chiusa lato Vinted lo e' anche qui,
     * a prescindere da come si chiama in italiano quello stato.
     *
     * --- versione precedente (fino alla v1.24.0) ---
     * private fun leggiConversazioneVinted(corpo: String): Pair<String, Offerta>? = try {
     *     val conv = JSONObject(corpo).optJSONObject("conversation")
     *     val tr = conv?.optJSONObject("transaction")
     *     val itemId = tr?.opt("item_id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
     *
     *     // L'ultimo messaggio "offer_request_message" porta l'offerta vigente.
     *     var entita: JSONObject? = null
     *     val messaggi = conv?.optJSONArray("messages")
     *     if (messaggi != null) for (i in 0 until messaggi.length()) {
     *         val m = messaggi.optJSONObject(i) ?: continue
     *         if (m.optString("entity_type") == "offer_request_message") {
     *             m.optJSONObject("entity")?.let { entita = it }
     *         }
     *     }
     *     val importo = entita?.optJSONObject("price")?.optString("amount")?.toDoubleOrNull()
     *
     *     if (tr == null || itemId == null || entita == null || importo == null || importo <= 0) null
     *     else {
     *         val statoTitolo = entita?.optString("status_title").orEmpty()
     *             .ifBlank { tr.optString("status") }
     *         val foto = tr.optJSONObject("item_photo")?.optString("url")
     *         val prezzoListino = tr.optJSONObject("offer_price")?.optString("amount")?.toDoubleOrNull()
     *         "v:$itemId" to Offerta(
     *             centesimi = Math.round(importo * 100).toInt(),
     *             etichetta = statoTitolo,
     *             attiva = !terminata(statoTitolo),
     *             id = itemId,
     *             titolo = tr.optString("item_title"),
     *             immagine = foto?.takeIf { it.startsWith("http") },
     *             prezzoAnnuncio = prezzoListino?.let { Math.round(it * 100).toInt() } ?: 0,
     *             quando = System.currentTimeMillis(),
     *             stato = statoTitolo,
     *             fonte = "vinted"
     *         )
     *     }
     * } catch (t: Throwable) { null }
     * --- fine versione precedente ---
     */
```


**riga 591:**
```
/*
     * v1.32.0 (2026-09-02) — CORREZIONE, con la prova in mano (sonda
     * bdc42069-api.json, la stessa conversazione dello screenshot mandato in
     * chat: venditore "jhnmadsu", "Rtx 3060 gaming oc 12gb - Gigabyte").
     *
     * Il bug segnalato: "annunci con una mia proposta e un rilancio da parte
     * del venditore sono scomparsi". Non erano scomparsi — la lettura sotto
     * cercava SOLO messaggi di tipo "offer_request_message" (la mia offerta),
     * e in questa conversazione ce n'e' uno solo, 230 €, con status_title
     * "Rifiutata". Il rilancio del venditore (260 €) e' un messaggio
     * SUCCESSIVO di tipo DIVERSO — "offer_message", non "offer_request_message"
     * — che il vecchio codice non riconosceva affatto: veniva scartato senza
     * errore, e restava per sempre l'ultima offerta MIA, gia' rifiutata.
     * Risultato: attiva = !terminata("Rifiutata") = false, l'annuncio spariva
     * dalla lista attiva anche se in realta' c'era un rilancio vivo e
     * accettabile (bottone "Acquista" nello screenshot).
     *
     * Il messaggio "offer_message" del venditore, si vede dalla sonda, NON
     * porta "status_title" (solo prezzo: price, original_price, user_id,
     * current, price_fixed) — Vinted lo scrive solo sul messaggio di chi FA
     * l'offerta iniziale/il rifiuto, non su un rilancio. Serve quindi
     * distinguere DI CHI e' ogni messaggio (io o la controparte), non solo
     * il suo tipo: si usa transaction.current_user_side insieme a
     * buyer_id/seller_id (tutti presenti nella sonda) confrontati con
     * l'user_id dentro ogni entita' di offerta.
     *
     * Bonus, stessa sonda: "transaction.offer_price" (260.0) NON e' piu' il
     * prezzo di listino una volta che c'e' un rilancio — e' l'importo
     * CORRENTE, cioe' il rilancio stesso (coincide infatti con l'importo del
     * rilancio, non con l'"original_price_label":"€270.00" scritto su
     * ENTRAMBI i messaggi). La v1.21.0 lo aveva preso per buono come prezzo
     * di listino perche' all'epoca non esisteva ancora un caso con rilancio
     * da provarlo sbagliato. Il prezzo vero e stabile e' "original_price",
     * identico su ogni messaggio della conversazione: e' li' che si prende
     * ora prezzoAnnuncio.
     *
     * --- versione precedente (fino alla v1.31.0) ---
     * private fun leggiConversazioneVinted(corpo: String): Pair<String, Offerta>? = try {
     *     val conv = JSONObject(corpo).optJSONObject("conversation")
     *     val tr = conv?.optJSONObject("transaction")
     *     val itemId = tr?.opt("item_id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
     *
     *     // L'ultimo messaggio "offer_request_message" porta l'offerta vigente,
     *     // e con essa la sua vera data (created_at_ts, sul messaggio, non sull'entita').
     *     var entita: JSONObject? = null
     *     var quandoIso = ""
     *     val messaggi = conv?.optJSONArray("messages")
     *     if (messaggi != null) for (i in 0 until messaggi.length()) {
     *         val m = messaggi.optJSONObject(i) ?: continue
     *         if (m.optString("entity_type") == "offer_request_message") {
     *             m.optJSONObject("entity")?.let { entita = it }
     *             quandoIso = m.optString("created_at_ts")
     *         }
     *     }
     *     val importo = entita?.optJSONObject("price")?.optString("amount")?.toDoubleOrNull()
     *
     *     if (tr == null || itemId == null || entita == null || importo == null || importo <= 0) null
     *     else {
     *         val statoTitolo = entita?.optString("status_title").orEmpty()
     *             .ifBlank { tr.optString("status") }
     *         val chiusaStruttura = tr.optBoolean("is_completed", false) ||
     *                 tr.optBoolean("item_is_closed", false)
     *         val foto = tr.optJSONObject("item_photo")?.optString("url")
     *         val prezzoListino = tr.optJSONObject("offer_price")?.optString("amount")?.toDoubleOrNull()
     *         "v:$itemId" to Offerta(
     *             centesimi = Math.round(importo * 100).toInt(),
     *             etichetta = statoTitolo,
     *             attiva = !terminata(statoTitolo) && !chiusaStruttura,
     *             id = itemId,
     *             titolo = tr.optString("item_title"),
     *             immagine = foto?.takeIf { it.startsWith("http") },
     *             prezzoAnnuncio = prezzoListino?.let { Math.round(it * 100).toInt() } ?: 0,
     *             quando = iso(quandoIso).takeIf { it > 0 } ?: System.currentTimeMillis(),
     *             stato = statoTitolo,
     *             fonte = "vinted"
     *         )
     *     }
     * } catch (t: Throwable) { null }
     * --- fine versione precedente ---
     */
```


**riga 671:**
```
/** @return la coppia (chiave, offerta) se la conversazione porta un'offerta attiva, altrimenti null. */
```


**riga 777:**
```
/*
     * v1.24.0 (2026-08-31) — aggiunto "vendut" (venduto/venduta): l'utente
     * segnala che un acquisto Vinted gia' concluso resta nella lista delle
     * offerte attive. Non e' una prova diretta — nessuna sonda ha ancora
     * catturato lo status_title vero di UNA conversazione con acquisto
     * concluso — ma e' un indizio concreto, non un'invenzione: una scheda
     * articolo Vinted mostrata in chat, per un oggetto gia' comprato, porta
     * proprio la scritta "Venduto" (banner verde sotto le foto). E' la
     * parola che Vinted usa in italiano per "questa vendita e' conclusa";
     * ragionevole aspettarsela anche nello stato della conversazione legata,
     * ma resta un'ipotesi finche' non arriva una sonda su un caso vero. Se
     * dopo questa versione un acquisto concluso resta ancora visibile, la
     * prossima sonda (Sonda offerte, o semplicemente riesportare la
     * diagnostica dopo aver aperto quella conversazione) dira' la vera
     * parola usata.
     *
     * --- versione precedente (fino alla v1.23.0) ---
     * private fun terminata(stato: String): Boolean {
     *     val s = stato.lowercase()
     *     return s.contains("cancel") || s.contains("complet") || s.contains("declin") ||
     *            s.contains("expir") || s.contains("closed") || s.contains("conclus") ||
     *            s.contains("rifiut")
     * }
     * --- fine versione precedente ---
     */
```


**riga 802:**
```
/** v1.10.0 — parole viste sugli stati chiusi delle piattaforme, in inglese e italiano. */
```


**riga 810:**
```
/**
     * v1.10.0 — sostituisce solo la fetta di "perChiave" di una fonte, senza
     * toccare quella dell'altra (vedi il commento in cima al file: leggi()
     * sostituiva la mappa INTERA, e la seconda fonte ad aggiornarsi cancellava
     * la prima).
     * @return vero se la fetta e' cambiata rispetto a prima
     */
```


**riga 828:**
```
/*
     * v1.10.0 — rinominata da "leggi" a "leggiSubito" (ora che c'e' anche
     * leggiConversazioneVinted) e passata a sostituisciFonte invece di
     * rimpiazzare l'intera mappa.
     *
     * --- versione precedente (v1.4.0..v1.9.0) ---
     * private fun leggi(corpo: String): Boolean = try {
     *     … (uguale, ma in fondo: "perChiave.clear(); perChiave.putAll(nuove)")
     * } catch (t: Throwable) { false }
     * --- fine versione precedente ---
     */
```


**riga 845:**
```
/*
             * buyer_price e' quello che paghi tu, item_price quello concordato
             * sull'oggetto: sulle proposte inviate coincidono, ma se un giorno
             * divergessero e' buyer_price la cifra che hai offerto.
             */
```


**riga 856:**
```
/*
             * Sullo stesso annuncio possono esserci piu' transazioni nel tempo
             * (una proposta annullata, poi un'altra). Vince quella attiva; a
             * parita', l'ultima che si incontra — l'elenco arriva dalla piu'
             * recente alla piu' vecchia.
             */
```


**riga 884:**
```
/**
     * Da "ad<uuid>list657012105" al numero che compare anche nell'urn di ricerca.
     * Il taglio si fa sull'ULTIMO "list": l'uuid non lo contiene mai, ma farlo
     * sull'ultimo costa niente e toglie una classe intera di sorprese.
     */
```


**riga 896:**
```
/*
     * v1.26.0 (2026-09-01) — CORREZIONE: la home mostrava solo le prime 6
     * (StatoRicerca.offerteRecenti() chiamava "Offerte.recenti(6)"),
     * un limite che aveva senso quando la lista viveva in una Column non
     * scorrevole (v1.8.0) ma non piu' da quando e' una LazyColumn (v1.9.0) —
     * da allora era rimasto solo un tetto arbitrario, senza una ragione di
     * schermo a giustificarlo. L'utente chiede di vederle tutte: il default
     * qui sotto passa da 30 a "senza tetto", e StatoRicerca.offerteRecenti()
     * ora chiama recenti() senza passare un numero. Il parametro resta,
     * genuinamente utile a chi in futuro volesse un elenco corto altrove.
     *
     * --- versione precedente (fino alla v1.25.0) ---
     * @Synchronized
     * fun recenti(quante: Int = 30): List<Offerta> =
     *     perChiave.values
     *         .filter { !conclusa(it) }
     *         .sortedByDescending { it.quando }
     *         .take(quante)
     * --- fine versione precedente ---
     */
```


**riga 916:**
```
/*
     * v1.9.0 — le ultime proposte, in ordine di data e basta.
     *
     * La v1.8.0 metteva prima le aperte: sembrava sensato, ma mescola due
     * criteri e il risultato non e' piu' una cronologia. Qui l'ordine e' uno
     * solo, dalla piu' recente.
     *
     * Fuori gli ordini conclusi: sono affari finiti, non c'e' niente da
     * seguire. Si riconoscono in due modi — l'etichetta italiana che scrive
     * Subito e lo stato interno CLOSED — perche' sono la stessa cosa detta a
     * due destinatari diversi, e prima o poi uno dei due cambia.
     *
     * --- versione precedente (v1.8.0, 2026-08-31) ---
     * fun recenti(quante: Int = 6): List<Offerta> =
     *     perChiave.values
     *         .sortedWith(compareByDescending<Offerta> { it.attiva }.thenByDescending { it.quando })
     *         .take(quante)
     * --- fine versione precedente ---
     */
```


**riga 942:**
```
/*
     * v1.25.0 (2026-08-31) — CORREZIONE: ri-derivare "conclusa" dal testo di
     * etichetta/stato duplicava un giudizio che "attiva" gia' fa meglio —
     * per Subito "attiva" viene da state_info.active (un booleano vero della
     * piattaforma, non un indovinello); per Vinted, da questa versione,
     * anche da is_completed/item_is_closed (vedi leggiConversazioneVinted),
     * non solo dal testo. Due punti che decidono la stessa cosa in due modi
     * diversi possono solo disallinearsi: era proprio il caso dell'annuncio
     * "fantasma" segnalato (item_is_closed vero ma nessuna parola nota nello
     * stato) — "attiva" lo sapeva gia' con questa versione, "conclusa()" no,
     * perche' guardava solo il testo. Ora c'e' un solo giudice.
     *
     * --- versione precedente (fino alla v1.24.0) ---
     * private fun conclusa(o: Offerta): Boolean = terminata(o.etichetta) || terminata(o.stato)
     * --- fine versione precedente ---
     */
```


**riga 958:**
```
/*
     * v1.10.0 — usa "terminata()" (vedi sopra), che riconosce anche le parole
     * inglesi degli stati Vinted, oltre alle italiane di Subito gia' note.
     *
     * --- versione precedente (v1.9.0) ---
     * private fun conclusa(o: Offerta): Boolean {
     *     val t = (o.etichetta + " " + o.stato).lowercase()
     *     return t.contains("conclus") || t.contains("closed")
     * }
     * --- fine versione precedente ---
     */
```


**riga 971:**
```
/** Da "2026-08-30T23:35:37.177455+02:00" ai millisecondi. 0 se illeggibile. */
```


**riga 977:**
```
/** "170 €" per una cifra in centesimi. */
```


**riga 984:**
```
/*
     * v1.32.0 — quando c'e' un rilancio pendente lo si aggiunge in coda: la
     * scheda (RigaOfferta, SchermataRicerca.kt) lo mostra gia' anche da solo
     * su una riga propria, ma questo testo compatto serve altrove (per ora
     * solo qui non serve ancora, tenuto pronto per un uso futuro fuori dalla
     * scheda dedicata) — meglio non doverlo riscrivere quando servira'.
     *
     * --- versione precedente (fino alla v1.31.0) ---
     * fun testo(o: Offerta): String {
     *     val euro = o.centesimi / 100.0
     *     val n = if (euro == Math.floor(euro)) euro.toInt().toString()
     *             else String.format(java.util.Locale.ITALY, "%.2f", euro)
     *     return "offerti $n €"
     * }
     * --- fine versione precedente ---
     */
```


**riga 1000:**
```
/** "offerti 170 €" — mai "offerti 170,00 €": la riga deve restare corta. */
```


**riga 1009:**
```
/*
     * v1.32.0 — aggiunto "controproposta" al salvataggio (vedi il campo
     * omonimo su Offerta); "versione" nel file sale a 1.11.0 di conseguenza —
     * resta comunque un dato di comodo, non un contratto: carica() gia'
     * legge "controproposta" con optInt(…, 0), quindi un file vecchio senza
     * quel campo si apre lo stesso, semplicemente senza rilanci noti finche'
     * non si rilegge dalla piattaforma.
     *
     * --- versione precedente (fino alla v1.31.0) ---
     * private fun salva(ctx: Context) {
     *     try {
     *         val o = JSONObject()
     *         perChiave.forEach { (k, v) ->
     *             o.put(k, JSONObject()
     *                 .put("centesimi", v.centesimi)
     *                 .put("etichetta", v.etichetta)
     *                 .put("attiva", v.attiva)
     *                 .put("id", v.id)
     *                 .put("titolo", v.titolo)
     *                 .put("immagine", v.immagine ?: "")
     *                 .put("prezzoAnnuncio", v.prezzoAnnuncio)
     *                 .put("quando", v.quando)
     *                 .put("stato", v.stato)
     *                 .put("fonte", v.fonte))
     *         }
     *         File(ctx.filesDir, FILE).writeText(
     *             JSONObject().put("versione", "1.10.0").put("offerte", o).toString()
     *         )
     *     } catch (t: Throwable) { /* la copia locale e' un comodo, non un dato */
```


**riga 1061:**
```
/* la copia locale e' un comodo, non un dato */
```


### LuoghiVinted.kt


**riga 3:**
```
/*
 * Subito+ — luogo E recensioni del venditore, su Vinted
 * v1.2.0 (2026-08-31) — nuovo
 *
 * COSA HA CHIUSO LA QUESTIONE
 * Fino alla v1.1.0 qui c'era scritto che su Vinted il luogo non esiste. Era
 * vero per la risposta del CATALOGO, non per la piattaforma: la verifica sul
 * browser, con la sessione vera, ha dato questo:
 *
 *   GET /api/v2/catalog/items?...  -> item.user = {id, login, photo,
 *                                     profile_url, business}   nessun luogo
 *   GET /api/v2/users/<id>         -> { city, city_id, country_code,
 *                                       country_title_local, expose_location }
 *
 * Il luogo c'e', ma sta sul PROFILO del venditore, una chiamata per venditore.
 * Su 12 venditori diversi: 12 risposte 200, nessun blocco, ~1,8 s l'una.
 *
 * QUANDO expose_location E' FALSO
 * "city" torna stringa vuota e resta solo il paese: e' una scelta del
 * venditore, non un errore. In quel caso si mostra il paese — meno preciso,
 * ma vero.
 *
 * PERCHE' UNA CACHE E NON UNA CHIAMATA PER RIGA
 * Una pagina di risultati ha ~25 venditori distinti: 25 richieste tutte insieme
 * sono esattamente la firma che distingue un programma da una persona. Quindi:
 * due alla volta, con una pausa fra l'una e l'altra, e cio' che si e' imparato
 * non si chiede mai piu' — un venditore cambia citta' molto meno spesso di
 * quanto lo si incontri in una ricerca.
 */
```


**riga 33:**
```
/*
 * v1.29.0 (2026-09-01) — NUOVO (in questo file): oltre al luogo, la STESSA
 * risposta GET /api/v2/users/<id> porta anche il numero di recensioni del
 * venditore. Prova sul campo, tre punti diversi della stessa sessione reale:
 *   - GET /api/v2/users/current            -> ...,"feedback_count":18,...
 *   - GET /api/v2/promoted_closets         -> user:{...,"feedback_count":498}
 *   - POST/GET /api/v2/conversations(/<id>) -> opposite_user:{...,"review_count":12,...}
 * "current" e "promoted_closets" usano "feedback_count", "conversations" usa
 * "review_count" per lo stesso concetto — endpoint diversi, nomi diversi.
 * L'endpoint che questo file gia' chiama e' /api/v2/users/<id>, la STESSA
 * forma di /api/v2/users/current: si legge "feedback_count" da li'.
 *
 * Serviva a "mi serve aggiungere ai filtri un toggle per scartare gli
 * annunci il cui venditore ha 0 recensioni" (vedi FoglioFiltri in
 * SchermataRicerca.kt e StatoRicerca.scartaSenzaRecensioni/recensioni()).
 * Non e' una chiamata in piu': e' lo stesso GET di sempre, letto un campo in
 * piu'. Per questo il nome del file resta LuoghiVinted anche se ora porta
 * anche le recensioni — spostare tutto in un secondo oggetto avrebbe voluto
 * dire due code, due cache, due chiamate per venditore invece di una.
 *
 * IMPORTANTE: chi aveva gia' il luogo in cache (praticamente tutti, la cache
 * e' su file e sopravvive agli aggiornamenti) non deve restare per sempre
 * senza recensioni solo perche' richiedi() salta i venditori gia' noti. La
 * condizione di salto ora guarda ENTRAMBE le informazioni, non piu' una sola
 * (vedi richiedi() piu' sotto): un venditore con luogo noto ma recensioni
 * ancora sconosciute torna in coda, una volta sola, poi resta in cache anche
 * lui.
 *
 * SUBITO NON E' IN QUESTO FILE: esiste un sistema di recensioni anche li'
 * (hades.subito.it/v1/trust/feedback-api/…, visto in una sonda), ma finora
 * e' stato catturato solo l'endpoint che da' un token, non quello con il
 * numero vero e proprio. Finche' non arriva una sonda con quel dato, il
 * filtro (StatoRicerca) tratta "recensioni sconosciute" come "non scartare":
 * meglio mostrare un annuncio Subito in piu' che nasconderne uno a caso per
 * un dato che non abbiamo davvero.
 */
```


**riga 80:**
```
/** Quante richieste possono essere in volo insieme. Due: non e' una corsa. */
```


**riga 83:**
```
/** Pausa fra una richiesta e la successiva dello stesso lavoratore. */
```


**riga 86:**
```
/** Tetto della coda: oltre, si rinuncia invece di accumulare lavoro inutile. */
```


**riga 91:**
```
/** venditore -> luogo gia' noto (anche "" quando la piattaforma non lo espone). */
```


**riga 94:**
```
/*
     * v1.29.0 — venditore -> numero di recensioni gia' noto. Mappa separata
     * (non un solo oggetto con i due campi) cosi' un venditore con SOLO il
     * luogo in cache dalla v1.2.0 non deve perderlo per aggiungere l'altro:
     * le due informazioni si riempiono in modo indipendente, la stessa
     * chiave le tiene insieme.
     */
```


**riga 103:**
```
/** Venditori per cui la chiamata e' fallita: si riprovera' al prossimo avvio. */
```


**riga 127:**
```
/* file rovinato: si riparte da vuoto */
```


**riga 130:**
```
/** Luogo gia' noto, oppure null se non e' ancora stato chiesto. */
```


**riga 134:**
```
/** v1.29.0 — recensioni gia' note, oppure null se non ancora chieste (o non lette). */
```


**riga 141:**
```
/**
     * Mette in coda i venditori ancora sconosciuti. Non blocca nulla: i risultati
     * arrivano uno alla volta e la riga si aggiorna quando il suo luogo e' pronto.
     *
     * @param aggiornato invocato sul thread principale a ogni luogo risolto
     */
```


**riga 147:**
```
/*
     * v1.29.0 — CORREZIONE alla condizione di salto: prima bastava che il
     * venditore avesse gia' un luogo noto (noti.containsKey) per non essere
     * mai piu' richiesto — corretto quando l'unica cosa che si imparava era
     * il luogo, sbagliato ora che la stessa chiamata porta anche le
     * recensioni: un venditore con SOLO il luogo in cache (chiunque avesse
     * gia' usato l'app prima di questa versione) sarebbe rimasto senza
     * recensioni per sempre. Si salta solo chi ha ENTRAMBE le informazioni.
     *
     * Il parametro "aggiornato" guadagna un terzo valore (le recensioni, o
     * null se il venditore non le ha ancora mai date): aggiunto IN FONDO,
     * dopo luogo, per non rompere la sintassi lambda finale di chi gia'
     * chiama richiedi() (vedi StatoRicerca.kt).
     *
     * --- versione precedente (fino alla v1.28.0) ---
     * fun richiedi(
     *     ctx: Context,
     *     motore: MotoreRicerca,
     *     venditori: Collection<String>,
     *     aggiornato: (venditore: String, luogo: String) -> Unit
     * ) {
     *     carica(ctx)
     *     venditori.forEach { v ->
     *         if (v.isBlank()) return@forEach
     *         if (noti.containsKey(v) || inVolo.contains(v) || coda.contains(v)) return@forEach
     *         if (coda.size >= CODA_MAX) return@forEach
     *         coda.addLast(v)
     *     }
     *     while (attivi < PARALLELE && coda.isNotEmpty()) avviaProssimo(ctx, motore, aggiornato)
     * }
     * --- fine versione precedente ---
     */
```


**riga 197:**
```
/*
     * v1.29.0 — il callback dentro azione() ora legge anche le recensioni
     * dalla stessa risposta, con leggiInfo() al posto di leggiLuogo().
     *
     * --- versione precedente (fino alla v1.28.0) ---
     * private fun avviaProssimo(
     *     ctx: Context,
     *     motore: MotoreRicerca,
     *     aggiornato: (String, String) -> Unit
     * ) {
     *     val v = coda.removeFirstOrNull() ?: return
     *     inVolo.add(v)
     *     attivi++
     *     motore.azione(FonteVinted, Chiamata("GET", "/api/v2/users/$v")) { stato, corpo ->
     *         val luogo = if (stato in 200..299) leggiLuogo(corpo) else null
     *         concluso(ctx, motore, v, luogo, aggiornato)
     *     }
     * }
     * --- fine versione precedente ---
     */
```


**riga 234:**
```
/*
     * v1.29.0 — "luogo" e "recensioni" si registrano indipendentemente: una
     * risposta puo' dare l'uno senza l'altro (un profilo senza feedback_count
     * non e' un errore, e' un venditore senza recensioni... anche se in
     * pratica il campo c'e' sempre, verificato su tre endpoint diversi).
     * L'intera chiamata fallisce (va in "falliti", si riprova al prossimo
     * avvio) solo se non e' arrivato NULLA di leggibile.
     *
     * --- versione precedente (fino alla v1.28.0) ---
     * private fun concluso(
     *     ctx: Context,
     *     motore: MotoreRicerca,
     *     venditore: String,
     *     luogo: String?,
     *     aggiornato: (String, String) -> Unit
     * ) {
     *     inVolo.remove(venditore)
     *     attivi--
     *     if (luogo != null) {
     *         noti[venditore] = luogo
     *         falliti.remove(venditore)
     *         daSalvare = true
     *         if (luogo.isNotBlank()) ui.post { aggiornato(venditore, luogo) }
     *     } else {
     *         falliti.add(venditore)
     *     }
     *     salvaFraPoco(ctx)
     *     if (coda.isNotEmpty()) ui.postDelayed({ avviaProssimo(ctx, motore, aggiornato) }, PAUSA_MS)
     * }
     * --- fine versione precedente ---
     */
```


**riga 293:**
```
/*
     * v1.29.0 — sostituisce leggiLuogo(): stessa lettura di prima piu'
     * "feedback_count". Un solo JSONObject letto una volta, non due parse.
     *
     * --- versione precedente (leggiLuogo, fino alla v1.28.0) ---
     * private fun leggiLuogo(corpo: String): String? = try {
     *     val o = JSONObject(corpo)
     *     val u = o.optJSONObject("user") ?: o
     *     val citta = u.optString("city").trim().takeIf { it.isNotEmpty() && it != "null" }
     *     val paese = u.optString("country_title_local").trim()
     *         .ifEmpty { u.optString("country_title").trim() }
     *         .takeIf { it.isNotEmpty() && it != "null" }
     *     citta ?: paese ?: ""
     * } catch (t: Throwable) { null }
     * --- fine versione precedente ---
     */
```


**riga 338:**
```
/*
     * v1.29.0 — il file ora porta anche la chiave "recensioni", accanto a
     * "luoghi": stesso file, cosi' resta un solo posto da caricare e da
     * cancellare (svuota() sotto), invece di due file paralleli da tenere
     * sincronizzati a mano.
     *
     * --- versione precedente (fino alla v1.28.0) ---
     * private fun salva() {
     *     val ctx = contesto ?: return
     *     daSalvare = false
     *     try {
     *         val o = JSONObject()
     *         noti.forEach { (k, v) -> o.put(k, v) }
     *         File(ctx.filesDir, FILE).writeText(
     *             JSONObject().put("versione", "1.2.0").put("luoghi", o).toString()
     *         )
     *     } catch (t: Throwable) { }
     * }
     * --- fine versione precedente ---
     */
```


**riga 374:**
```
/* la cache e' un lusso, non un dato */
```


### RecensioniSubito.kt


**riga 3:**
```
/*
 * Subito+ — recensioni del venditore, su Subito
 * v1.30.0 (2026-09-01) — nuovo
 *
 * COMPLETA "mi serve aggiungere ai filtri un toggle per scartare gli
 * annunci il cui venditore ha 0 recensioni": la v1.29.0 lo aveva fatto solo
 * per Vinted, perche' la sonda di allora aveva trovato solo un endpoint che
 * dava un token (hades.subito.it/v1/trust/feedback-api/tokens/user), non i
 * numeri veri. Una sonda successiva, presa dal profilo di un venditore
 * (.../utente/<id>#Recensioni), ha catturato quello giusto:
 *
 *   GET hades.subito.it/v1/trust/feedback/public/users/sdrn:subito:user:<id>/feedback
 *       ?limit=1&page=0&sources=MEMBER
 *   -> { "reputation": {}, "result": [ … ],
 *        "pagination": { "page":0, "limit":1, "totalPages":1,
 *                         "totalCount": 1, "sortField":"created", … } }
 *
 * "totalCount" e' il numero di recensioni: esattamente cio' che serve, e
 * "public" nel percorso (nessun token richiesto per leggerlo — verificato:
 * la sonda l'ha catturato mentre visitava un profilo altrui, non il proprio)
 * conferma che e' pensato per essere letto guardando il profilo di chiunque,
 * non solo il proprio. limit=1 perche' non servono le recensioni vere, solo
 * il conteggio in "pagination": un payload piccolo, una per venditore.
 *
 * L'id del venditore arriva da Annuncio.venditore, che per Subito la stessa
 * v1.30.0 riempie con "advertiser.user_id" (gia' presente in ogni risultato
 * di ricerca — vedi Fonti.kt, FonteSubito.leggi()). Serve solo comporre
 * "sdrn:subito:user:<id>".
 *
 * STESSA ARCHITETTURA DI LuoghiVinted.kt, e non per pigrizia: e' lo stesso
 * problema (un dato che sta sul profilo del venditore, non nei risultati di
 * ricerca, con ~25 venditori distinti per pagina) e la stessa soluzione —
 * due richieste alla volta, una pausa fra l'una e l'altra, cache su file,
 * mai richiesto due volte cio' che gia' si sa. File separato invece di
 * un'unica "InfoVenditori" per tutte e due le fonti perche' Subito e Vinted
 * hanno gia' ognuno il proprio Fonte/sessione/token: tenerli distinti qui
 * evita di far dipendere l'uno dall'altro per un solo campo in comune.
 */
```


**riga 57:**
```
/** venditore (user_id Subito) -> numero di recensioni gia' noto. */
```


**riga 77:**
```
/* file rovinato: si riparte da vuoto */
```


**riga 80:**
```
/** Recensioni gia' note, oppure null se non ancora chieste. */
```


**riga 87:**
```
/**
     * Mette in coda i venditori ancora sconosciuti. Non blocca nulla: i
     * risultati arrivano uno alla volta e la riga si aggiorna quando la sua
     * risposta e' pronta — stessa forma di LuoghiVinted.richiedi().
     */
```


**riga 152:**
```
/** "pagination.totalCount" della risposta: il numero di recensioni. */
```


**riga 180:**
```
/* la cache e' un lusso, non un dato */
```


### StatoRicerca.kt


**riga 3:**
```
/*
 * Subito+ — stato della ricerca unificata
 * v0.5.2 (2026-08-30) — Fase 4, punto 2
 *
 * Separa il "cosa" dal "come si vede": qui vivono la richiesta in corso, i
 * risultati, i filtri e gli errori; la schermata (SchermataRicerca) si limita
 * a disegnarli. E' quello che rende possibile aggiungere una fonte o un filtro
 * senza toccare l'interfaccia, e viceversa.
 *
 * Sostituisce la parte di stato che stava dentro VistaRicerca.kt (viste
 * tradizionali, v0.4.0 - v0.5.1), rimosso in questa versione.
 */
```


**riga 26:**
```
/** Chiamata quando cambia il fatto che ci sia o meno un caricamento in corso. */
```


**riga 39:**
```
/** Vero quando una pagina non ha portato nulla di nuovo: inutile chiederne altre. */
```


**riga 58:**
```
/*
     * v1.29.0 (2026-09-01) — NUOVO: "un toggle per scartare gli annunci il
     * cui venditore ha 0 recensioni". Il dato viene da LuoghiVinted (vedi
     * quel file per come si legge "feedback_count" dal profilo Vinted, e
     * perche' su Subito non c'e' ancora, non e' una scelta ma un limite
     * onesto). Guarda recensioni(a) qui sotto per come la lista lo usa.
     *
     * v1.30.0 — il limite sopra e' superato: una sonda presa dal profilo di
     * un venditore Subito ha dato l'endpoint giusto (RecensioniSubito.kt),
     * quindi ora il toggle vale su ENTRAMBE le fonti allo stesso modo.
     */
```


**riga 71:**
```
/*
     * v1.1.0 — mostra solo cio' che non c'era l'ultima volta che questa stessa
     * ricerca e' stata eseguita. L'istantanea precedente si legge all'avvio
     * della ricerca e resta ferma per tutta la sessione, altrimenti il primo
     * risultato che arriva renderebbe "vecchio" tutto il resto.
     */
```


**riga 81:**
```
/** Vero se di questa ricerca esiste un'esecuzione precedente da confrontare. */
```


**riga 85:**
```
/** Ricerche recenti, conservate fra un avvio e l'altro. */
```


**riga 89:**
```
/* ------------------------------------------------------------------
     * v1.2.0 — preferiti
     *
     * Lo stato mostrato vive qui come mappa osservabile, cosi' il cuore cambia
     * al tocco senza aspettare la rete. La chiamata alla piattaforma parte
     * subito dopo: se fallisce, il cuore torna com'era. Meglio un cuore che
     * torna indietro di uno che mente.
     * ------------------------------------------------------------------ */
```


**riga 100:**
```
/* ------------------------------------------------------------------
     * v1.2.0 — luogo degli annunci Vinted.
     *
     * Non arriva con i risultati: sta sul profilo del venditore, una chiamata
     * per venditore (vedi LuoghiVinted). Quindi la riga nasce senza luogo e lo
     * riceve dopo, ed e' per questo che serve una mappa osservabile: quando il
     * profilo risponde, quella riga — solo quella — si ridisegna.
     * ------------------------------------------------------------------ */
```


**riga 110:**
```
/*
     * v1.29.0 — le recensioni del venditore Vinted, stessa forma e stesso
     * motivo di luoghiVinted qui sopra: mappa osservabile perche' arrivano
     * dopo, un venditore alla volta (vedi LuoghiVinted.richiedi()).
     *
     * v1.30.0 — stessa cosa per Subito (RecensioniSubito), mappa separata:
     * le chiavi sono user_id di due piattaforme diverse, tenerle insieme
     * avrebbe voluto dire rischiare una collisione fra un id Subito e un id
     * Vinted numericamente uguali.
     */
```


**riga 123:**
```
/* ------------------------------------------------------------------
     * v1.4.0 — le offerte fatte.
     *
     * Una sola chiamata per tutta la lista (vedi Offerte), non una per riga.
     * Qui vive solo un contatore osservabile: quando l'elenco cambia lo si
     * incrementa e le righe si ridisegnano leggendo Offerte, che e' la fonte.
     * ------------------------------------------------------------------ */
```


**riga 133:**
```
/* ------------------------------------------------------------------
     * v1.8.0 — la vista "Preferiti".
     *
     * Non e' una schermata nuova: e' la stessa lista, riempita da un'altra
     * fonte di righe. Cosi' cuore, scarto, filtri, vista compatta e apertura
     * della scheda funzionano gia', senza riscriverli. L'unica differenza e'
     * che non si pagina e non si cerca.
     * ------------------------------------------------------------------ */
```


**riga 144:**
```
/*
     * v1.21.0 (2026-08-31) — CORREZIONE: onResume() da solo (v1.20.0) non
     * bastava. Un'offerta fatta da "Sito" (la scheda col sito vero, DENTRO
     * l'app) e un ritorno alla home con l'icona o "indietro" non fanno mai
     * uscire l'Activity in background: e' tutto lo stesso schermo che cambia
     * modo, quindi onResume() non scatta mai in quel percorso. tornaAllaRicerca()
     * e' il punto che TUTTI i modi di "tornare alla home" attraversano (icona,
     * indietro, uscita dai preferiti): da qui si forza la rilettura delle
     * offerte, oltre che da onResume(). Vedi anche MainActivity.cambiaModo().
     */
```


**riga 162:**
```
/*
     * v1.26.0 (2026-09-01) — CORREZIONE: tetto di 6 tolto, vedi il commento
     * su Offerte.recenti(). La lista e' gia' in una LazyColumn (v1.9.0):
     * scorre da sola, non serve piu' tagliarla qui.
     *
     * --- versione precedente (fino alla v1.25.0) ---
     * fun offerteRecenti() : List<Offerte.Offerta> {
     *     revisioneOfferte
     *     return Offerte.recenti(6)
     * }
     * --- fine versione precedente ---
     */
```


**riga 174:**
```
/** Le proposte inviate, per la schermata iniziale: tutte, non solo le ultime. */
```


**riga 180:**
```
/*
     * v1.20.0 (2026-08-31) — CORREZIONE: un'offerta appena fatta si vedeva in
     * lista solo chiudendo l'app dal task manager e riaprendola.
     *
     * La causa era in Offerte.aggiorna(): non piu' di una lettura ogni cinque
     * minuti (VALIDITA_MS), un limite giusto per non ripetere la stessa
     * chiamata a ogni tocco dentro l'app — ma quel limite vive nella singola
     * (l'oggetto Offerte e' un "object" Kotlin, quindi un solo esemplare per
     * processo) e resta finche' il PROCESSO resta vivo. Forzare la chiusura
     * dal task manager termina il processo: si riparte da zero, il limite si
     * azzera, e la prima lettura torna fresca. Restare nell'app — tornare
     * indietro, cambiare schermata, persino andare in background col tasto
     * home e tornare — non tocca il processo: il limite resta quello di
     * prima, e un'offerta fatta due minuti fa non compare finche' non passano
     * i cinque minuti.
     *
     * Rimedio: quando l'utente TORNA nell'app (onResume, vedi MainActivity),
     * e' un segnale chiaro — puo' arrivare da fuori (il sito vero, un'altra
     * app) dove potrebbe aver appena fatto un'offerta — quindi qui si rilegge
     * SUBITO, ignorando il limite dei cinque minuti (forza = true). Il limite
     * resta valido per le riletture "di striscio" durante una ricerca
     * (StatoRicerca.cerca(), invariato): quelle sì restano rare apposta.
     */
```


**riga 217:**
```
/*
         * v1.10.0 — Vinted resta a un passo: il suo elenco preferiti e' gia'
         * fatto di annunci completi (stessa forma del catalogo), quindi
         * preferitiAnnunci() li legge subito.
         */
```


**riga 245:**
```
/*
         * v1.10.0 — Subito e' a DUE passi: prima l'elenco (solo urn e data),
         * poi una risoluzione per ciascuno. Vedi Fonti.kt per il contratto
         * verificato sul campo.
         */
```


**riga 278:**
```
/*
     * v1.10.0 — risolve ogni urn dei preferiti Subito con una chiamata a se',
     * due alla volta: ogni catena chiede il prossimo solo dopo aver ricevuto
     * risposta, la stessa cautela di LuoghiVinted contro le raffiche. Ogni
     * annuncio compare in lista appena arriva, non tutti insieme alla fine.
     */
```


**riga 309:**
```
/*
     * v1.8.0 — l'identificatore Vinted serve solo all'elenco dei preferiti.
     * Sta in /api/v2/users/current, che nella sonda ha risposto 200 con
     * {"user":{"id":163221140,…}}: un campo dichiarato, non un indizio.
     */
```


**riga 331:**
```
/** Il testo rosso accanto al prezzo, o null se su questo annuncio non hai offerto. */
```


**riga 338:**
```
/*
     * v1.28.0 (2026-09-01) — qui viveva contattato(a): "hai scritto al
     * venditore di questo annuncio?", true se esiste una Offerte.Offerta
     * (attiva o conclusa), per l'iconcina in elenco.
     *
     * v1.31.0 (2026-09-01) — TOLTA (non commentata soltanto: rimossa, vedi
     * sotto perche'). L'utente ha chiarito il significato voluto: l'icona
     * deve comparire per un messaggio di chat SENZA offerta — l'opposto di
     * cio' che questa funzione sapeva rispondere (Offerte.perAnnuncio(a) e'
     * per costruzione un'offerta, mai un messaggio semplice). Non e' un
     * refactor da un carattere: serve leggere un dato che oggi non esiste da
     * nessuna parte nel codice —
     *   - Vinted: leggiConversazioneVinted() (Offerte.kt) scarta in silenzio
     *     ogni conversazione priva di "transaction"; la forma di una
     *     conversazione SENZA offerta non e' mai stata catturata da una
     *     sonda, quindi i nomi dei suoi campi non si possono indovinare;
     *   - Subito: non esiste ancora nessuna lettura dell'elenco delle chat,
     *     solo il conteggio dei messaggi non letti (hades.subito.it/v1/chat/
     *     messages/<id>/unread) e le transazioni P2P (v1.30.0).
     * Finche' non arrivano quelle due sonde, tenere la funzione con la
     * semantica sbagliata (o farla tornare sempre false) sarebbe codice
     * morto silenzioso: meglio toglierla e riscriverla quando i dati veri
     * arriveranno. Vedi lo stesso ragionamento in SchermataRicerca.kt, sopra
     * Descrizione().
     *
     * --- versione precedente (v1.28.0 - v1.30.0) ---
     * fun contattato(a: Annuncio): Boolean {
     *     revisioneOfferte
     *     return Offerte.perAnnuncio(a) != null
     * }
     * --- fine versione precedente ---
     */
```


**riga 371:**
```
/**
     * Il testo che sta accanto al nome della fonte.
     * Su Subito il luogo c'e' gia' nei risultati. Su Vinted arriva dopo: finche'
     * non e' arrivato si mostra la condizione, cosi' la riga non e' mai monca.
     */
```


**riga 382:**
```
/*
     * v1.29.0 — recensioni del venditore. null = non ancora sappiamo, non
     * "zero per certo": il filtro qui sotto (risultati) tratta le due cose
     * in modo diverso apposta, altrimenti ogni annuncio sparirebbe finche'
     * la sua riga non ha ancora ricevuto risposta dal profilo del venditore.
     *
     * v1.30.0 — CORREZIONE: alla v1.29.0 qui c'era "if (a.fonte != vinted)
     * null", perche' allora la sonda aveva trovato solo l'endpoint Vinted.
     * Una sonda successiva ha dato anche quello Subito (vedi
     * RecensioniSubito.kt): entrambe le fonti sono coperte allo stesso modo.
     *
     * --- versione precedente (fino alla v1.29.0) ---
     * fun recensioni(a: Annuncio): Int? =
     *     if (a.fonte != "vinted") null
     *     else recensioniVinted[a.venditore] ?: LuoghiVinted.recensioni(a.venditore)
     * --- fine versione precedente ---
     */
```


**riga 441:**
```
/*
     * v1.6.0 (2026-08-31) — CORREZIONE: su Subito il cuore diceva sempre
     * "apri una volta il sito dalla navigazione".
     *
     * Quel messaggio compare quando l'identificatore dell'utente non e' noto, e
     * non lo era mai: la ricerca dell'id frugava fra cookie e localStorage con
     * un'espressione presa da una versione del sito, e quando il sito cambia
     * modo di conservarlo non trova piu' niente e non lo dice a nessuno.
     * Cercare in un posto solo, per giunta senza contratto, era il difetto.
     *
     * Ora ci sono due strade, e la seconda e' solida: l'elenco delle
     * transazioni — quello dei "offerti xxx €", gia' verificato — dichiara
     * "buyer_id":"6338126", che e' proprio l'identificatore che serve. Non e'
     * una traccia lasciata in giro dal sito: e' un campo di una risposta.
     *
     * --- versione precedente (v1.2.0, 2026-08-30) ---
     * private fun scopriUtenteSubito() {
     *     if (utenteSubito.isNotBlank()) return
     *     motore.leggiDallaPagina(FonteSubito, "…regex su cookie e localStorage…") { … }
     * }
     * --- fine versione precedente ---
     */
```


**riga 489:**
```
/** Seconda strada: l'id sta dentro l'elenco delle transazioni, come buyer_id. */
```


**riga 588:**
```
/** Chiamata quando lo scorrimento si avvicina al fondo. */
```


**riga 624:**
```
/*
         * v1.2.0 — Vinted dichiara is_favourite nei risultati: e' verita' fresca
         * dalla piattaforma e vince sulla copia locale. Subito non lo dichiara,
         * e li' lo stato arriva dall'elenco letto a parte.
         */
```


**riga 641:**
```
/*
         * v1.2.0 — i luoghi Vinted si chiedono solo per i venditori che
         * compaiono davvero in lista, e solo per quelli mai visti prima.
         * Si parte dai venditori appena aggiunti: la coda pensa al resto.
         */
```


**riga 649:**
```
/*
         * v1.29.0 — la callback di richiedi() porta ora anche le recensioni
         * (terzo valore, vedi LuoghiVinted.kt): si aggiorna recensioniVinted
         * insieme a luoghiVinted, dalla stessa risposta.
         *
         * --- versione precedente (fino alla v1.28.0) ---
         * if (venditori.isNotEmpty()) {
         *     venditori.forEach { v -> LuoghiVinted.luogo(v)?.let { luoghiVinted[v] = it } }
         *     LuoghiVinted.richiedi(ctx, motore, venditori) { v, l -> luoghiVinted[v] = l }
         * }
         * --- fine versione precedente ---
         */
```


**riga 672:**
```
/*
         * v1.30.0 — NUOVO: le recensioni Subito, stesso schema di quelle
         * Vinted qui sopra ma senza luogo (Subito il luogo lo dichiara gia'
         * nei risultati, vedi FonteSubito.luogo() in Fonti.kt — qui serve
         * solo il conteggio, vedi RecensioniSubito.kt).
         */
```


**riga 688:**
```
/** Scarto con possibilita' di annullare: l'archivio si tocca solo alla conferma. */
```


**riga 699:**
```
/*
     * v1.23.0 (2026-08-31) — CORREZIONE: il chip "Senza Subito" (o "Senza
     * Vinted") lo diceva attivo, ma in mezzo agli annunci restavano comunque
     * annunci dell'altra fonte.
     *
     * "usaSubito"/"usaVinted" hanno sempre significato UNA sola cosa: quali
     * fonti interrogare per le pagine ANCORA da caricare (fonti(), usata da
     * avvia()/altraPagina()). Non hanno MAI significato "quali fonti tenere
     * in vista" — "risultati" (qui sotto) filtrava per eta', prezzo, novita'
     * e venduto, ma non per fonte. Risultato: gli annunci gia' scaricati
     * PRIMA di spuntare il filtro (le prime pagine, caricate 30 alla volta,
     * quando ancora usaSubito era vero) restavano in "grezzi" e continuavano
     * a comparire fra quelli nuovi — non una questione di attesa o di
     * caricamento lento, il filtro proprio non guardava mai la fonte.
     */
```


**riga 715:**
```
/** Lista visibile: filtri applicati a cio' che e' gia' stato scaricato. */
```


**riga 740:**
```
/*
                 * v1.29.0 — "scarta chi ha 0 recensioni". NON scarta quando il
                 * numero non e' ancora noto (recensioni(a) == null): ne' perche'
                 * la risposta del venditore non e' ancora arrivata (Vinted, di
                 * norma questione di secondi), ne' perche' e' Subito, dove il
                 * dato proprio non c'e' ancora (vedi LuoghiVinted.kt). Scarta
                 * solo quando si SA che sono zero.
                 */
```


**riga 796:**
```
/*
     * v1.17.0 (2026-08-31) — NUOVO: riprende l'ultima ricerca da sola.
     *
     * "Indietro" dalla lista dei risultati chiude l'app (e' la radice della
     * schermata: e' il comportamento normale di Android, non un difetto).
     * Il problema era dopo: con piu' WebView nascoste (una per fonte) e
     * l'attivita' impostata singleTask, Android puo' scegliere di eliminare
     * il processo mentre l'app e' in secondo piano per liberare memoria — e a
     * quel punto la ricerca e i risultati, che vivono solo nella memoria di
     * StatoRicerca, sono persi per sempre: riaprire dall'icona ricrea
     * l'attivita' da zero (onCreate), con la casella di ricerca vuota. Da qui
     * "devo forzare la chiusura e riaprire per tornare alla lista" — in
     * realta' non serviva nemmeno forzare la chiusura, il risultato era gia'
     * lo stesso: ricerca persa, si ricomincia da capo.
     *
     * Rimedio minimo: le ricerche recenti erano gia' salvate su disco
     * (ricordaRecente, sopra) per la lista sotto la barra di ricerca. Se
     * l'app riparte da zero (nessuna ricerca ancora fatta in questa sessione,
     * nessun intent l'ha gia' avviata da una notifica o un link) e ne esiste
     * una recente, la si rilancia da sola: non riporta esattamente la
     * schermata di prima (lo scorrimento, per esempio, riparte dall'alto),
     * ma toglie la necessita' di ricordare e riscrivere la query.
     *
     * @return vero se una ricerca e' stata rilanciata
     */
```


### SchermataRicerca.kt


**riga 3:**
```
/*
 * Subito+ — schermata di ricerca
 * v0.5.2 (2026-08-30) — Fase 4, punti 2, 3 e 5 del restyling
 *
 * Sostituisce VistaRicerca.kt (viste tradizionali, v0.4.0 - v0.5.1), rimosso.
 * Cosa cambia rispetto a quella:
 *   - le tre righe di filtri che occupavano mezzo schermo diventano un foglio
 *     che sale dal basso; in cima restano solo le etichette dei filtri attivi
 *   - la lista ha degli stati: scheletri mentre carica, "nessun risultato",
 *     e un errore per fonte con il suo "riprova" invece di una riga di testo
 *   - le pagine successive arrivano da sole scorrendo, senza il pulsante
 *   - scartare un annuncio si puo' annullare subito, senza aprire l'elenco
 */
```


**riga 59:**
```
/* v1.4.0 — piu' scuro del cuore: e' un dato da leggere, non un comando. */
```


**riga 69:**
```
/** v0.6.0 — salva la ricerca corrente con i suoi filtri. */
```


**riga 71:**
```
/** v1.8.0 — apre un indirizzo nella navigazione (le proposte in home). */
```


**riga 73:**
```
/*
     * v1.0.5 (2026-08-30) — CORREZIONE.
     * "Vista compatta" nel menu agiva solo sulle pagine web: la lista dei
     * risultati era scritta con un unico disegno, foto piccola a sinistra e
     * testo a destra, e l'interruttore sembrava rotto perche' non aveva nulla
     * su cui agire. Ora la lista ha due disposizioni e l'interruttore le alterna.
     */
```


**riga 169:**
```
/*
             * v1.9.0 — la schermata iniziale.
             * Sopra le scorciatoie alle ultime ricerche, sotto le proposte
             * aperte in colonna, che riempiono la pagina invece di lasciarla
             * bianca. I due pulsanti sono diventati icone in barra.
             *
             * --- versione precedente (v1.8.0, 2026-08-31) ---
             * due OutlinedButton "Preferiti" e "Ricerche salvate" qui,
             * piu' una LazyRow orizzontale con sei tessere.
             * --- fine versione precedente ---
             */
```


**riga 228:**
```
/*
             * v1.12.0 — il riepilogo ("N risultati…" + "Salva ricerca") sta qui,
             * fisso, e non dentro la LazyColumn: cosi' non scorre via con gli
             * annunci e resta sempre leggibile. Compare solo quando c'e' una
             * lista vera da riassumere (stessa condizione che prima sceglieva
             * il ramo "else" del "when" sottostante).
             */
```


**riga 275:**
```
/*
                         * v1.10.0 — chiave "fonte:id" invece del solo "id": da
                         * quando le offerte arrivano anche da Vinted, un id
                         * Subito e uno Vinted potrebbero coincidere per puro
                         * caso (sono numerazioni indipendenti) e LazyColumn
                         * vuole chiavi uniche in tutta la lista.
                         *
                         * --- versione precedente (fino alla v1.9.0, solo Subito) ---
                         * items(offerte, key = { it.id }) { o -> RigaOfferta(o, apriUrl) }
                         * --- fine versione precedente ---
                         */
```


**riga 306:**
```
/*
                 * v1.12.0 (2026-08-31) — la riga del riepilogo ("N risultati…") non e'
                 * piu' il primo item della LazyColumn: scorreva via con gli annunci
                 * ed era illeggibile appena si scendeva di una riga. Ora e' un Row
                 * fisso subito FUORI dalla lista (vedi sotto, prima del "when"), e
                 * resta visibile sempre, come le etichette dei filtri sopra di lei.
                 *
                 * --- versione precedente (v0.6.0, 2026-08-30) ---
                 * else -> LazyColumn(...) {
                 *     item {
                 *         Row(verticalAlignment = Alignment.CenterVertically) {
                 *             Text(riepilogo(stato), Modifier.weight(1f), …)
                 *             TextButton(onClick = salva) { Text("Salva ricerca") }
                 *         }
                 *     }
                 *     items(lista, key = { it.fonte + it.id }) { a -> … }
                 * }
                 * --- fine versione precedente ---
                 */
```


**riga 385:**
```
/* ------------------------------------------------------------------ */
```


**riga 387:**
```
/*
 * v1.9.0 — una proposta inviata, nella stessa forma di un risultato di ricerca:
 * foto a sinistra, testo a destra, e la fonte in rosso "SUBITO" come in elenco.
 * Coerenza non per estetica: due liste che si leggono allo stesso modo si
 * scorrono senza doverci pensare.
 *
 * --- versione precedente (v1.8.0, 2026-08-31) ---
 * tessera verticale da 168 dp, dentro una LazyRow orizzontale.
 * --- fine versione precedente ---
 */
```


**riga 422:**
```
/*
                 * v1.32.0 (2026-09-02) — NUOVO: quando il venditore ha
                 * rilanciato (o.controproposta > 0, vedi Offerte.kt —
                 * leggiConversazioneVinted riconosce ora anche i messaggi di
                 * tipo "offer_message", non solo la propria offerta), sotto
                 * "offerti X €" compare anche il rilancio, in blu per
                 * distinguerlo a colpo d'occhio dal rosso della propria
                 * offerta (che resta il colore dell'azione fatta DA me,
                 * mentre il rilancio e' quella fatta DALL'ALTRO). Prima la
                 * riga mostrava solo la MIA offerta, anche quando in realta'
                 * era gia' stata superata da un rilancio — la ragione per
                 * cui l'utente segnalava "annunci con un rilancio sono
                 * scomparsi": non erano scomparsi, era la cifra sbagliata (e
                 * a volte lo stato "attiva" sbagliato) a farli sembrare
                 * conclusi. Vedi Offerte.kt per la correzione vera, qui c'e'
                 * solo la vista.
                 *
                 * --- versione precedente (fino alla v1.31.0) ---
                 * Row {
                 *     if (o.prezzoAnnuncio > 0) Text(
                 *         Offerte.euro(o.prezzoAnnuncio),
                 *         style = MaterialTheme.typography.titleMedium,
                 *         fontWeight = FontWeight.SemiBold,
                 *         modifier = Modifier.alignByBaseline()
                 *     )
                 *     Text(
                 *         "  offerti " + Offerte.euro(o.centesimi),
                 *         style = MaterialTheme.typography.labelLarge,
                 *         fontWeight = FontWeight.Bold,
                 *         color = ROSSO_OFFERTA,
                 *         maxLines = 1,
                 *         modifier = Modifier.alignByBaseline()
                 *     )
                 * }
                 * --- fine versione precedente ---
                 */
```


**riga 482:**
```
/*
                 * v1.22.0 (2026-08-31) — CORREZIONE: la scritta diceva sempre
                 * "SUBITO", anche per un'offerta Vinted — restO di quando le
                 * offerte erano solo di Subito (v1.4.0), mai resa dinamica
                 * quando la v1.10.0 ha aggiunto Vinted. Non e' un errore di
                 * lettura dati (o.fonte era gia' giusto, "vinted" o "subito"):
                 * era solo il testo in schermo, scritto fisso, a ignorarlo.
                 *
                 * --- versione precedente (fino alla v1.21.0) ---
                 * Text(
                 *     "SUBITO",
                 *     style = MaterialTheme.typography.labelSmall,
                 *     fontWeight = FontWeight.Bold,
                 *     color = ROSSO_SUBITO
                 * )
                 * --- fine versione precedente ---
                 */
```


**riga 578:**
```
/*
                     * v1.32.0 (2026-09-02) — CORREZIONE: nella vista estesa si
                     * vedeva sempre e solo la PRIMA foto, senza modo di scorrere
                     * le altre — segnalato dall'utente ("non e' possibile
                     * scorrere le foto"). Annuncio.immagini (nuovo, vedi
                     * Annuncio.kt e Fonti.kt) porta ora l'elenco completo, non
                     * solo la prima; quando ne ha piu' di una si mostra un
                     * HorizontalPager invece del singolo AsyncImage, con i
                     * pallini in basso come indicatore di pagina. Con zero o
                     * una sola foto (o se l'elenco non arriva, vedi il
                     * commento su Annuncio.immagini per Vinted) si ripiega sul
                     * comportamento di sempre: un'unica immagine, senza pager.
                     *
                     * --- versione precedente (fino alla v1.31.0) ---
                     * AsyncImage(
                     *     model = a.immagineGrande ?: a.immagine,
                     *     contentDescription = null,
                     *     modifier = Modifier
                     *         .fillMaxWidth()
                     *         .height(210.dp)
                     *         .background(MaterialTheme.colorScheme.surfaceVariant),
                     *     contentScale = ContentScale.Crop
                     * )
                     * --- fine versione precedente ---
                     */
```


**riga 680:**
```
/*
                 * v1.7.0 — sotto la foto grande lo spazio a destra era vuoto.
                 * Ci va il testo dell'annuncio: e' l'unica cosa che in elenco
                 * manca davvero, ed e' cio' che fa decidere se aprire o no.
                 * Dove non c'e' (Vinted non lo espone nel catalogo) la colonna
                 * non compare e i dati si prendono tutta la larghezza.
                 *
                 * --- versione precedente (v1.0.5, 2026-08-30) ---
                 * Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) { Descrizione(a, coda, offerta) }
                 * --- fine versione precedente ---
                 */
```


**riga 691:**
```
/*
                 * v1.12.0 (2026-08-31) — il box del testo era piu' stretto di quello
                 * dei dati (titolo/prezzo/offerta): 52% a sinistra, 48% a destra, ma
                 * a sinistra c'e' poco (due righe corte), a destra un paragrafo che
                 * con quella larghezza andava a capo troppo presto. Si scambiano le
                 * proporzioni: i dati bastano stretti, il testo ora ha piu' spazio.
                 *
                 * --- versione precedente (v1.7.0, 2026-08-31) ---
                 * Column(Modifier.weight(if (a.descrizione.isBlank()) 1f else 0.52f)) {
                 *     Descrizione(a, coda, offerta)
                 * }
                 * if (a.descrizione.isNotBlank()) Text(
                 *     a.descrizione, …, modifier = Modifier.weight(0.48f).padding(start = 12.dp)
                 * )
                 * --- fine versione precedente ---
                 */
```


**riga 727:**
```
/*
 * v1.2.0 — il cuore. Lo stato viene dalla piattaforma, non da noi: pieno se
 * l'annuncio e' fra i preferiti la', vuoto altrimenti. Al tocco cambia subito
 * e la richiesta parte dietro; se non passa, torna com'era.
 */
```


**riga 744:**
```
/*
 * v1.31.0 (2026-09-01) — CORREZIONE: l'icona "hai scritto al venditore"
 * della v1.28.0 mostrava un'icona per QUALUNQUE proposta inviata, offerta
 * compresa — l'utente ha chiarito che deve valere il contrario: solo per un
 * messaggio di chat SENZA offerta ("se e' stata fatta un'offerta abbiamo
 * gia' il testo con l'importo, non serve anche un'icona").
 *
 * Non e' un cambio di un carattere: serve un dato che oggi non abbiamo per
 * NESSUNA delle due fonti. "contattato" leggeva Offerte.perAnnuncio(a), che
 * esiste SOLO per conversazioni con un'offerta di prezzo dentro (Subito:
 * transazione P2P; Vinted: conversazione con un offer_request_message) —
 * una chat senza offerta oggi non lascia traccia da nessuna parte nel
 * codice: leggiConversazioneVinted() (Offerte.kt) scarta in silenzio ogni
 * conversazione priva di "transaction", e su Subito non esiste ancora
 * nessuna lettura dell'elenco chat (solo il conteggio non letti e le
 * transazioni P2P, viste nella sonda della v1.30.0).
 *
 * Finche' non arriva una sonda che mostri la forma di QUELLI (una
 * conversazione Vinted senza offerta, e l'endpoint Subito che elenca le
 * chat), l'icona resta tolta invece di indovinare i nomi dei campi — vedi
 * la nota su questo in StatoRicerca.kt, dove viveva contattato(a).
 * L'icona busta chiesta per il rimpiazzo (invece della "Send" della
 * v1.28.0) e' gia' decisa per quando i dati arriveranno: Icons.Filled.Email,
 * disponibile in material-icons-core come Send.
 *
 * --- versione precedente (v1.28.0, con "contattato" — offerte comprese) ---
 * private fun Descrizione(a: Annuncio, coda: String, offerta: String?, contattato: Boolean) {
 *     … stesso corpo, piu' in fondo:
 *     if (contattato) Icon(
 *         Icons.Filled.Send, "Hai scritto al venditore",
 *         Modifier.padding(start = 4.dp).size(13.dp),
 *         tint = MaterialTheme.colorScheme.onSurfaceVariant
 *     )
 * --- fine versione precedente ---
 *
 * --- versione precedente (fino alla v1.27.0, senza icona) ---
 * private fun Descrizione(a: Annuncio, coda: String, offerta: String?) {
 * --- fine versione precedente ---
 */
```


**riga 783:**
```
/** Il testo dell'annuncio: identico nelle due disposizioni. */
```


**riga 792:**
```
/*
     * v1.4.0 — prezzo chiesto e prezzo offerto sulla stessa riga.
     * Stanno accanto perche' e' il confronto fra i due che serve: «300 € /
     * offerti 170 €» si legge in un colpo, su due righe no.
     *
     * --- versione precedente (v0.5.2, 2026-08-30) ---
     * Text(a.prezzoTesto, style = titleMedium, fontWeight = SemiBold)
     * --- fine versione precedente ---
     */
```


**riga 801:**
```
/*
     * v1.7.0 — i due prezzi sulla stessa LINEA DI BASE.
     * Alignment.Bottom allinea il fondo delle due caselle di testo, non quello
     * delle lettere: con due corpi diversi il risultato e' il testo rosso che
     * "galleggia" piu' in basso. alignByBaseline allinea cio' che l'occhio
     * usa davvero per giudicare, la riga su cui poggiano le cifre.
     *
     * --- versione precedente (v1.4.0, 2026-08-31) ---
     * Row(verticalAlignment = Alignment.Bottom) { … }
     * --- fine versione precedente ---
     */
```


**riga 836:**
```
/*
         * v1.0.4 (2026-08-30) — accanto alla fonte va solo il luogo.
         * Marca e taglia restano nel modello (Annuncio.dettaglio) ma non si
         * mostrano: erano rumore, e su Subito non hanno un corrispondente,
         * quindi le due righe non si leggevano allo stesso modo.
         *
         * --- versione precedente (v0.5.2, 2026-08-30) ---
         * val coda = listOf(a.luogo, a.dettaglio)
         *     .filter { it.isNotBlank() }.joinToString(" · ")
         * --- fine versione precedente ---
         */
```


**riga 847:**
```
/*
         * v1.1.0 — dove c'e' il luogo si mostra il luogo; dove non c'e' — su
         * Vinted non esiste proprio — si mostra la condizione, che sull'usato
         * e' l'altra cosa che si guarda per prima. Mai entrambe: la riga deve
         * restare corta e leggersi allo stesso modo sulle due fonti.
         */
```


**riga 853:**
```
/*
         * v1.2.0 (2026-08-31) — la coda arriva gia' decisa da StatoRicerca.coda():
         * su Vinted il luogo esiste, ma sul profilo del venditore, e arriva
         * dopo la riga; finche' non c'e' si mostra la condizione.
         *
         * --- versione precedente (v1.1.0, 2026-08-30) ---
         * val coda = a.luogo.ifBlank { a.dettaglio }
         * --- fine versione precedente ---
         */
```


**riga 949:**
```
/* ------------------------------------------------------------------ */
```


**riga 1029:**
```
/*
             * v1.29.0 (2026-09-01) — NUOVO: "un toggle per scartare gli
             * annunci il cui venditore ha 0 recensioni". Alla nascita
             * valeva solo su Vinted (Subito non aveva ancora un endpoint
             * confermato) e il sottotitolo lo diceva.
             *
             * v1.30.0 — trovato anche l'endpoint Subito (RecensioniSubito.kt):
             * il sottotitolo che limitava il filtro a Vinted non serve più.
             *
             * --- versione precedente (fino alla v1.29.0) ---
             * Row(verticalAlignment = Alignment.CenterVertically) {
             *     Column(Modifier.weight(1f)) {
             *         Text("Nascondi venditori senza recensioni")
             *         Text(
             *             "Per ora solo su Vinted: su Subito il dato non è ancora letto",
             *             style = MaterialTheme.typography.bodySmall,
             *             color = MaterialTheme.colorScheme.onSurfaceVariant
             *         )
             *     }
             *     Switch(stato.scartaSenzaRecensioni, { stato.scartaSenzaRecensioni = it })
             * }
             * --- fine versione precedente ---
             */
```


**riga 1091:**
```
/* ------------------------------------------------------------------ */
```


### Controllore.kt


**riga 3:**
```
/*
 * Subito+ — controllo periodico delle ricerche salvate
 * v0.6.0 (2026-08-30) — Fase 5
 *
 * COME NON SEMBRARE UN BOT
 * E' il punto piu' delicato del progetto, piu' di qualsiasi impronta lasciata
 * nella pagina: richieste a intervalli regolari, senza immagini e senza
 * scorrimento, sono la firma di un programma. Quindi:
 *   - intervallo minimo di 30 minuti, con scarto casuale all'avvio
 *   - una fonte per volta, con una pausa fra l'una e l'altra
 *   - una pausa anche fra una ricerca salvata e la successiva
 *   - la chiamata parte dalla pagina vera, con la sessione dell'utente,
 *     esattamente come quando la ricerca la fa una persona
 *   - solo la prima pagina di risultati: si cercano le novita', non un archivio
 *
 * Il WebView vive sul filo principale anche qui: il worker si sposta li' per
 * crearlo e interrogarlo, e attende il risultato senza bloccare nulla.
 */
```


**riga 49:**
```
/*
     * v1.27.0 (2026-09-01) — CORREZIONE: "ho attivato le notifiche ma non ne
     * e' arrivata nessuna". Due cose in una, trovate leggendo il codice:
     *
     * 1. Bug vero: TUTTE le notifiche partivano solo alla fine, dopo aver
     *    finito TUTTE le ricerche salvate ("trovati.forEach" stava fuori dal
     *    ciclo "for (r in ricerche)"). Un WorkManager periodico normale ha un
     *    tempo massimo di esecuzione (circa dieci minuti, meno su telefoni
     *    con un risparmio energetico aggressivo) — e con piu' ricerche, piu'
     *    fonti, e le pause volute apposta per non sembrare un programma
     *    (vedi la nota "COME NON SEMBRARE UN BOT" in cima al file), un giro
     *    completo puo' avvicinarsi o superare quel tetto. Se il sistema
     *    interrompe il lavoro a meta', TUTTE le notifiche gia' pronte per le
     *    ricerche finite fino a quel momento sparivano insieme a quelle non
     *    ancora fatte — e gli annunci trovati restavano comunque segnati
     *    come "gia' visti" (registraEsito() lo fa subito, ricerca per
     *    ricerca), quindi non sarebbero MAI piu' stati notificati, nemmeno
     *    al giro successivo. Ora ogni ricerca notifica appena finite le sue
     *    fonti, non aspetta piu' le altre: un'interruzione a meta' lavoro
     *    perde solo cio' che veniva dopo, non cio' che era gia' pronto.
     *
     * 2. Mancava un modo per sapere DAVVERO cosa succede in un lavoro che
     *    gira in background, senza schermo, ore dopo che l'app e' stata
     *    chiusa — la diagnostica di MotoreRicerca (motore.diagnostica) vive
     *    solo per la vita di QUESTA esecuzione e si perde con lei, mai letta
     *    da nessuno. Ora ogni giro scrive poche righe in un file che resta
     *    (controllo-log.txt): quando parte, cosa trova per ogni ricerca e
     *    fonte, se le notifiche sono concesse, quando finisce. Si esporta
     *    con "Esporta diagnostica" (Opzioni), insieme agli altri file. Se le
     *    notifiche continuano a non arrivare, quel file dira' se il lavoro
     *    gira affatto, se si ferma a meta', o se trova zero novita' davvero.
     *
     * --- versione precedente (fino alla v1.26.0) ---
     * override suspend fun doWork(): Result {
     *     val ctx = applicationContext
     *     RicercheSalvate.carica(ctx)
     *     Nascosti.carica(ctx)
     *     val ricerche = RicercheSalvate.tutte()
     *     if (ricerche.isEmpty()) return Result.success()
     *
     *     delay(Random.nextLong(0, 90_000))
     *
     *     val nomePonte = ctx.getSharedPreferences("subitoplus", Context.MODE_PRIVATE)
     *         .getString("nomePonte", null) ?: return Result.success()
     *
     *     val motore = withContext(Dispatchers.Main) { MotoreRicerca(ctx, nomePonte) }
     *     val trovati = LinkedHashMap<String, MutableList<Annuncio>>()
     *
     *     try {
     *         for (r in ricerche) {
     *             for (f in r.fonti()) {
     *                 val esito = interroga(motore, f, r.query)
     *                 if (esito != null) {
     *                     val utili = esito.filter { a ->
     *                         !a.venduto &&
     *                                 !Nascosti.contiene(a.chiave) &&
     *                                 dentroPrezzo(a, r.prezzoMin, r.prezzoMax)
     *                     }
     *                     val nuovi = RicercheSalvate.registraEsito(ctx, r.id, utili.map { it.chiave })
     *                     if (nuovi > 0) {
     *                         trovati.getOrPut(r.id) { ArrayList() }
     *                             .addAll(utili.takeLast(nuovi))
     *                     }
     *                 }
     *                 delay(Random.nextLong(4_000, 12_000))   // pausa fra le fonti
     *             }
     *             delay(Random.nextLong(8_000, 20_000))       // pausa fra le ricerche
     *         }
     *     } finally {
     *         withContext(Dispatchers.Main) { motore.chiudi() }
     *     }
     *
     *     trovati.forEach { (idRicerca, annunci) ->
     *         val r = ricerche.firstOrNull { it.id == idRicerca } ?: return@forEach
     *         avvisa(ctx, r, annunci)
     *     }
     *     return Result.success()
     * }
     * --- fine versione precedente ---
     */
```


**riga 129:**
```
/*
     * v1.31.0 (2026-09-01) — CORREZIONE: "il doppio avvio nel log".
     * L'utente ha mandato controllo-log.txt e c'erano coppie come queste:
     *
     *   12:13:03 avvio · 4 ricerche · notifiche concesse
     *   12:13:31 avvio · 4 ricerche · notifiche concesse
     *   12:14:56 fine giro
     *
     * Due "avvio" a 28 secondi l'uno dall'altro, un solo "fine giro": due
     * ESECUZIONI DI doWork() stavano girando insieme. Leggendo il codice,
     * un varco vero c'e': "Controlla ora" (MainActivity.kt, controllaOra)
     * mette in coda un OneTimeWorkRequest con WorkManager.enqueue() semplice
     * — non enqueueUniqueWork — quindi nulla impedisce che un tocco su
     * "Controlla ora" parta MENTRE il giro periodico (Controllore.programma,
     * quello si' "unique") sta gia' girando: due Controllore, stesso
     * processo, in parallelo. Non e' l'unica spiegazione possibile — anche
     * un'esecuzione interrotta a meta' dal sistema (il processo ucciso prima
     * che il blocco finally sotto arrivi a scrivere "fine giro", poi
     * WorkManager la fa ripartire) produrrebbe lo stesso segno nel log — ma
     * e' l'unica delle due che il codice mostra con certezza, quindi e'
     * quella che si corregge.
     *
     * La correzione non tocca "Controlla ora": chiude il varco qui, in
     * doWork(), con una guardia di processo (inCorso, sotto). Cosi' vale per
     * QUALUNQUE modo in cui due esecuzioni potessero sovrapporsi, non solo
     * per questa — comprese le richieste di rete raddoppiate inutilmente, e
     * la corsa fra due scritture concorrenti su controllo-log.txt (log() non
     * e' protetta da due chiamanti insieme: legge il file, aggiunge una
     * riga, riscrive tutto — se due lo fanno nello stesso momento, l'ultimo
     * che scrive puo' cancellare la riga dell'altro senza errori, ed e'
     * probabilmente per questo che "fine giro" a volte manca).
     *
     * --- versione precedente (fino alla v1.27.0/v1.30.0, corpo invariato) ---
     * override suspend fun doWork(): Result {
     *     val ctx = applicationContext
     *     RicercheSalvate.carica(ctx)
     *     Nascosti.carica(ctx)
     *     val ricerche = RicercheSalvate.tutte()
     *     if (ricerche.isEmpty()) { log(ctx, "nessuna ricerca salvata: esco subito"); return Result.success() }
     *
     *     val haPermesso = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
     *             PackageManager.PERMISSION_GRANTED
     *     log(ctx, "avvio · ${ricerche.size} ricerche · notifiche ${if (haPermesso) "concesse" else "NEGATE — nessun avviso potra' arrivare"}")
     *
     *     delay(Random.nextLong(0, 90_000))
     *
     *     val nomePonte = ctx.getSharedPreferences("subitoplus", Context.MODE_PRIVATE)
     *         .getString("nomePonte", null)
     *     if (nomePonte == null) { log(ctx, "nomePonte assente nelle preferenze: esco"); return Result.success() }
     *
     *     val motore = withContext(Dispatchers.Main) { MotoreRicerca(ctx, nomePonte) }
     *
     *     try {
     *         for (r in ricerche) {
     *             val trovatiPerRicerca = ArrayList<Annuncio>()
     *             for (f in r.fonti()) {
     *                 val esito = interroga(motore, f, r.query)
     *                 if (esito != null) {
     *                     val utili = esito.filter { a ->
     *                         !a.venduto && !Nascosti.contiene(a.chiave) && dentroPrezzo(a, r.prezzoMin, r.prezzoMax)
     *                     }
     *                     val nuovi = RicercheSalvate.registraEsito(ctx, r.id, utili.map { it.chiave })
     *                     log(ctx, "«${r.query}» ${f.nome}: ${esito.size} annunci, $nuovi nuovi")
     *                     if (nuovi > 0) trovatiPerRicerca.addAll(utili.takeLast(nuovi))
     *                 } else {
     *                     log(ctx, "«${r.query}» ${f.nome}: nessuna risposta entro il tempo massimo")
     *                 }
     *                 delay(Random.nextLong(4_000, 12_000))
     *             }
     *             if (trovatiPerRicerca.isNotEmpty()) {
     *                 avvisa(ctx, r, trovatiPerRicerca)
     *                 log(ctx, "«${r.query}»: notificati ${trovatiPerRicerca.size} annunci")
     *             }
     *             delay(Random.nextLong(8_000, 20_000))
     *         }
     *     } finally {
     *         withContext(Dispatchers.Main) { motore.chiudi() }
     *         log(ctx, "fine giro")
     *     }
     *     return Result.success()
     * }
     * --- fine versione precedente ---
     */
```


**riga 286:**
```
/** Una sola pagina, con un tetto di attesa: se la fonte non risponde si passa oltre. */
```


**riga 340:**
```
/* permesso revocato nel frattempo */
```


**riga 350:**
```
/*
         * v1.31.0 — la guardia contro il doppio avvio (vedi doWork()).
         * @Volatile perche' due Controllore possono nascere su coroutine
         * diverse: senza, una scrittura fatta dall'una potrebbe non essere
         * vista subito dall'altra. Vive nella companion apposta — e' UNA
         * variabile per classe, condivisa da ogni istanza di Controllore
         * nello stesso processo, non una per worker.
         */
```


**riga 367:**
```
/**
         * @param minuti intervallo scelto dall'utente; sotto i 30 non si scende,
         *               ed e' una scelta di prudenza, non un limite tecnico.
         */
```


**riga 390:**
```
/*
         * v1.27.0 (2026-09-01) — NUOVO: poche righe per ogni giro del
         * controllo automatico, con un tetto (le piu' vecchie cadono via via
         * che se ne aggiungono di nuove) cosi' il file non cresce
         * all'infinito. E' un comodo per capire cosa succede in un lavoro
         * che gira senza schermo, ore dopo che l'app e' stata chiusa — non
         * un dato dell'utente: se la scrittura fallisce (spazio esaurito,
         * per esempio) il giro prosegue lo stesso, non e' mai un motivo per
         * fermarsi.
         */
```


**riga 408:**
```
/* il log e' un comodo, non un dato */
```


**riga 411:**
```
/** Il log del controllo automatico, per "Esporta diagnostica" (vedi MainActivity.esporta()). */
```


### MainActivity.kt


**riga 3:**
```
/*
 * Subito+ — schermata principale
 * v0.5.1 (2026-08-30)
 *
 * Storia breve del file, perche' spiega com'e' fatto:
 *   v0.1.0  sonda: WebView nudo piu' registro delle richieste
 *   v0.2.0  blocco pubblicita' su shouldInterceptRequest, regole in JSON
 *   v0.3.0  scarto del singolo annuncio, archivio locale
 *   v0.3.2  riduzione delle tracce lasciate nella pagina
 *   v0.4.0  ricerca unificata Subito + Vinted come schermata principale
 *   v0.4.5  script iniettati messi a dieta (latenza sui pulsanti delle schede)
 *   v0.4.6  foto in elenco: resa da elenco invece dell'originale, coda a pila
 *   v0.5.0  Compose e Material 3: tema, icona, avvio, barra dei comandi
 *   v0.5.1  interruttore degli script di pagina, risposte bloccate con il tipo giusto
 *
 * Novita' della v0.5.0:
 *  - l'attivita' e' una ComponentActivity: serve a ospitare Compose e porta
 *    con se' la gestione moderna di "indietro" e dei risultati di attivita'
 *  - la fila di otto pulsanti diventa una barra Material 3 con un menu
 *  - tolto l'interruttore dello user agent: mascherarlo peggiora la
 *    riconoscibilita' invece di ridurla (vedi documento di piano), quindi
 *    resta sempre quello di sistema e non e' piu' una scelta esposta
 *
 * --- versione precedente (fino alla v0.4.6, 2026-08-30) ---
 * class MainActivity : Activity() ... barra: LinearLayout con otto Button
 * ... private var modoUa = 0 ... private fun cambiaUa() { ... }
 * --- fine versione precedente ---
 */
```


**riga 75:**
```
/** Nomi generati una volta per installazione: un nome fisso sarebbe un'impronta. */
```


**riga 82:**
```
/*
     * v1.0.5 — deve essere stato osservabile da Compose: prima era una variabile
     * normale, e cambiandola la lista non si ridisegnava. L'interruttore
     * funzionava sul WebView e sembrava rotto sulla schermata di ricerca.
     */
```


**riga 89:**
```
/** v0.5.1 — spegne in blocco cio' che iniettiamo nelle pagine (vedi menu). */
```


**riga 94:**
```
/** v1.5.0 — riferimento al gestore di "indietro", per poterlo riaccendere. */
```


**riga 96:**
```
/*
     * v1.5.0 — vero finche' si e' sulla pagina che l'app ha aperto di proposito
     * (una scheda, la home di una fonte) e l'utente non ha ancora toccato un
     * link suo. Serve a distinguere i rimbalzi automatici del sito dalle
     * navigazioni volute: vedi azzeraCronologia in onPageFinished.
     */
```


**riga 104:**
```
/** v0.7.0 — titolo della pagina aperta, per la barra. */
```


**riga 106:**
```
/*
     * v1.15.0 (2026-08-31) — il banner che guida al login, solo al primo
     * avvio. Diventa false (e resta cosi' per sempre, e' salvato) appena
     * l'utente lo chiude o tocca davvero "Sito" nel menu: in entrambi i casi
     * ha visto il percorso, non serve piu' indicarglielo.
     */
```


**riga 114:**
```
/*
     * v1.24.0 (2026-08-31) — prima voce del pannello Opzioni: cosa fare
     * all'avvio. Vedi il commento su onCreate() piu' sotto per dove entra in
     * gioco, e DialogoOpzioni.kt per il pannello.
     */
```


**riga 130:**
```
/** Stato osservato dalla barra Compose. */
```


**riga 224:**
```
/*
                         * v1.26.0 (2026-09-01) — sette voci in piu', spostate qui dal
                         * menu dai tre pallini (vedi il commento in cima a
                         * DialogoOpzioni.kt): stesse funzioni private di sempre,
                         * chiamate da un altro posto — nessuna e' cambiata.
                         *
                         * --- versione precedente (fino alla v1.25.0) ---
                         * if (opzioniAperte) DialogoOpzioni(
                         *     avvioHome = avvioHome,
                         *     azioni = AzioniOpzioni(
                         *         cambiaAvvioHome = { acceso ->
                         *             avvioHome = acceso
                         *             prefs.edit().putBoolean("avvioHome", acceso).apply()
                         *         }
                         *     ),
                         *     chiudi = { opzioniAperte = false }
                         * )
                         * --- fine versione precedente ---
                         */
```


**riga 253:**
```
/*")) },
                                cambiaSonda = {
                                    modoSonda = !modoSonda
                                    prefs.edit().putBoolean("modoSonda", modoSonda).apply()
                                    RequestLog.dettaglio = modoSonda
                                    toast(if (modoSonda) "Sonda accesa: lasciala accesa solo il tempo di raccogliere"
                                          else "Sonda spenta")
                                    registraScript(); web.reload(); aggiornaStato()
                                },
                                analizza = { analizzaPagina() },
                                sondaOfferte = { sondaOfferte() },
                                esporta = { esporta() },
                                azzera = {
                                    RequestLog.reset(); Regole.bloccate = 0; ApiLog.reset()
                                    ultimoReportDom = "{}"; aggiornaStato()
                                }
                            ),
                            chiudi = { opzioniAperte = false }
                        )
                    }
                }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // --- corpo: ricerca sopra, WebView sotto ---
        web = WebView(this)
        contenitore = FrameLayout(this)
        contenitore.addView(
            web,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            contenitore,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)

        configuraWebView()
        registraScript()

        motore = MotoreRicerca(this, nomePonte, contenitore)
        statoRicerca = StatoRicerca(this, motore) { attivo ->
            avanzamento = if (attivo) -1 else null
            aggiornaStato()
        }

        /*
         * v0.5.2 — la schermata di ricerca e' in Compose e vive nello stesso
         * contenitore del WebView: si alternano per visibilita', cosi' il
         * WebView non viene mai ricreato e la sessione non si perde.
         */
```


**riga 333:**
```
/*
         * v1.5.0 (2026-08-31) — CORREZIONE: l'app si chiudeva invece di tornare
         * alla ricerca.
         *
         * Il difetto era in una riga sola: "isEnabled = false" spegneva il
         * gestore per SEMPRE. Con android:launchMode="singleTask" e il
         * comportamento di Android 12+, uscire con "indietro" NON distrugge
         * l'activity: la manda in secondo piano e la stessa istanza torna in
         * vita alla riapertura — con il gestore ormai spento. Da quel momento
         * ogni "indietro", anche da una scheda, arriva al sistema e chiude.
         * Ecco perche' sembrava capitare a caso: capitava sempre, ma solo DOPO
         * la prima uscita.
         *
         * Ora il gestore si riaccende subito dopo aver delegato, e si riaccende
         * comunque a ogni onResume (vedi sotto): se una via nuova dovesse
         * spegnerlo, l'app se ne accorge da sola alla prossima apertura.
         *
         * --- versione precedente (v0.5.0, 2026-08-30) ---
         * onBackPressedDispatcher.addCallback(this) {
         *     when {
         *         !modoRicerca && web.canGoBack() -> web.goBack()
         *         !modoRicerca -> { modoRicerca = true; applicaModo(); aggiornaStato() }
         *         else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
         *     }
         * }
         * --- fine versione precedente ---
         */
```


**riga 360:**
```
/*
         * v1.18.0 (2026-08-31) — CORREZIONE: da una ricerca con risultati,
         * "indietro" usciva subito dall'app invece di tornare alla schermata
         * iniziale (le ultime proposte).
         *
         * Il ramo che riportava alla home esisteva gia', ma solo per i
         * preferiti ("statoRicerca.modoPreferiti"): una ricerca normale
         * (primaRicercaFatta = true, modoPreferiti = false) cadeva dritta nel
         * ramo "else" e usciva. Il difetto peggiorava con piu' WebView aperte
         * in background: Android puo' eliminare il processo per liberare
         * memoria, e riaprire dall'icona ricreava l'attivita' da zero — da
         * qui "devo chiudere dal task manager per tornare qui", perche' la
         * ricerca perduta non tornava nemmeno forzando la chiusura, si
         * ripartiva vuoti (vedi StatoRicerca.ripristinaUltimaRicerca, v1.17.0,
         * che attutisce quel caso separato).
         *
         * Il rimedio e' allargare la condizione a "primaRicercaFatta" invece
         * di "modoPreferiti": modoPreferiti la implica gia' (vedi apriPreferiti
         * in StatoRicerca), quindi un ramo solo copre entrambi i casi. Il
         * corpo diventa vaiAllaHome() invece di ripetere le stesse due righe.
         *
         * --- versione precedente (fino alla v1.17.0) ---
         * indietro = onBackPressedDispatcher.addCallback(this) {
         *     when {
         *         !modoRicerca && web.canGoBack() -> web.goBack()
         *         !modoRicerca -> { modoRicerca = true; applicaModo(); aggiornaStato() }
         *         // v1.8.0 — dai preferiti si torna alla schermata iniziale, non fuori
         *         statoRicerca.modoPreferiti -> { statoRicerca.tornaAllaRicerca(); aggiornaStato() }
         *         else -> {
         *             isEnabled = false
         *             onBackPressedDispatcher.onBackPressed()
         *             isEnabled = true
         *         }
         *     }
         * }
         * --- fine versione precedente ---
         */
```


**riga 412:**
```
/*
         * v1.24.0 (2026-08-31) — la ripresa automatica (v1.17.0, sotto) ora e'
         * una scelta: "avvioHome" viene dal pannello Opzioni. Chi preferisce
         * vedere prima la home (le ultime proposte) puo' spegnerla; di default
         * resta accesa, cioe' il comportamento di sempre, per non sorprendere
         * chi gia' usa l'app.
         *
         * --- versione precedente (fino alla v1.23.0) ---
         * // v1.17.0 — nessuna ricerca gia' avviata (ne' da notifica ne' da link):
         * // se l'app e' ripartita da zero, si riprende l'ultima ricerca fatta.
         * // Vedi il commento su StatoRicerca.ripristinaUltimaRicerca().
         * if (statoRicerca.ripristinaUltimaRicerca()) { modoRicerca = true; applicaModo(); aggiornaStato() }
         * --- fine versione precedente ---
         */
```


**riga 431:**
```
/*
     * v1.5.0 — rete di sicurezza. Con singleTask questa istanza sopravvive
     * all'uscita, quindi si controlla a ogni ritorno che "indietro" sia ancora
     * in servizio. Costa niente e rende impossibile che il difetto torni per
     * una strada diversa da quella corretta.
     */
```


**riga 437:**
```
/*
     * v1.20.0 (2026-08-31) — CORREZIONE: un'offerta appena fatta compariva in
     * lista solo forzando la chiusura dell'app dal task manager. Vedi il
     * commento su StatoRicerca.aggiornaOfferteAlRitorno() per la causa (il
     * limite dei cinque minuti di Offerte.aggiorna() sopravvive finche' vive
     * il processo, e un semplice ritorno in foreground non lo tocca). Qui,
     * a ogni ritorno vero nell'app, si forza una rilettura.
     *
     * --- versione precedente (fino alla v1.19.0) ---
     * override fun onResume() {
     *     super.onResume()
     *     indietro?.isEnabled = true
     * }
     * --- fine versione precedente ---
     */
```


**riga 464:**
```
/** Toccare un avviso apre l'app gia' sulla ricerca che l'ha generato. */
```


**riga 495:**
```
/*
         * v1.21.0 (2026-08-31) — quando si passa da "Sito" a "Ricerca" (dove
         * si vede anche l'elenco delle offerte) si forza una rilettura: e'
         * il momento in cui e' piu' probabile che l'utente torni da
         * un'offerta appena fatta sul sito vero. Vedi il commento su
         * StatoRicerca.tornaAllaRicerca() per il perche' onResume() da solo
         * (v1.20.0) non intercettava questo percorso.
         */
```


**riga 557:**
```
/*")) },
        salvaBackup = { salvaBackup() },
        analizza = { analizzaPagina() },
        sondaOfferte = { sondaOfferte() },   // v1.3.0
        esporta = { esporta() },
        azzera = {
            RequestLog.reset(); Regole.bloccate = 0; ApiLog.reset()
            ultimoReportDom = "{}"; aggiornaStato()
        },
        // --- v0.7.0: comandi della navigazione web ---
        ricarica = { web.reload() },
        condividi = {
            val u = web.url
            if (u.isNullOrBlank()) toast("Nessuna pagina aperta")
            else startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, titoloPagina)
                        putExtra(Intent.EXTRA_TEXT, u)
                    }, "Condividi"
                )
            )
        },
        apriNelBrowser = {
            val u = web.url
            if (u.isNullOrBlank()) toast("Nessuna pagina aperta")
            else try {
                /*
                 * Rete di sicurezza per i flussi che dentro un WebView non si
                 * completano — verifiche d'identita', portafogli di pagamento.
                 * I cookie sono condivisi con il browser di sistema, quindi la
                 * sessione regge e si torna indietro senza rifare il login.
                 */
```


**riga 599:**
```
/*
     * v1.18.0 (2026-08-31) — NUOVO: torna alla schermata iniziale ("le tue
     * ultime proposte") da qualunque punto — ricerca in corso, preferiti, o
     * dentro "Sito". La stessa funzione serve sia all'icona in barra sia al
     * ramo nuovo del gestore di "indietro" qui sotto: un solo posto che
     * decide cosa vuol dire "torna alla home".
     */
```


**riga 612:**
```
/** v1.15.0 — chiude il banner di guida al login e non lo mostra mai piu'. */
```


**riga 654:**
```
/* Qui passa ogni risorsa richiesta: e' il punto in cui si blocca. */
```


**riga 666:**
```
/*
             * v0.5.1 — una risposta vuota va servita con il tipo che la pagina
             * si aspetta. Un foglio di stile o uno script consegnati come
             * "text/plain" vengono rifiutati dal browser, e c'e' codice che in
             * quel caso ritenta o resta in attesa: proprio il genere di cosa
             * che si vede come qualche secondo di ritardo su un pulsante.
             */
```


**riga 688:**
```
/*
                 * v1.5.0 — hasGesture() e' vero solo se e' stato l'utente a
                 * toccare qualcosa. Da qui in poi la cronologia gli appartiene
                 * e non va piu' cancellata.
                 */
```


**riga 707:**
```
/*
                 * v1.5.0 (2026-08-31) — la cronologia si azzera per tutta la
                 * catena, non al primo arrivo.
                 *
                 * Aprendo una scheda Subito il sito rimbalza piu' volte
                 * (redirect, pagina di consenso, scheda vera). La versione
                 * precedente puliva al PRIMO onPageFinished — cioe' a meta'
                 * catena — e i passaggi successivi restavano in cronologia:
                 * "indietro" li ripercorreva invece di tornare alla ricerca.
                 * Ora si pulisce a ogni arrivo finche' l'utente non tocca un
                 * link di suo (vedi shouldOverrideUrlLoading).
                 *
                 * --- versione precedente (v0.4.1, 2026-08-30) ---
                 * if (azzeraCronologia) { azzeraCronologia = false; view?.clearHistory() }
                 * --- fine versione precedente ---
                 */
```


**riga 753:**
```
/* Fotocamera e microfono: servono per pubblicare un annuncio con foto. */
```


**riga 767:**
```
/* Posizione: la usa la ricerca "vicino a me" del sito. */
```


**riga 780:**
```
/*
             * I link con target="_blank" chiederebbero una finestra nuova.
             * Invece di aprirne una si carica l'indirizzo qui: una finestra in
             * piu' significherebbe una sessione in piu' da gestire.
             */
```


**riga 802:**
```
/*
         * v0.7.0 — scaricamenti. Il WebView da solo non scarica nulla: si passa
         * al gestore di sistema, portandosi dietro i cookie della sessione,
         * altrimenti i file dietro autenticazione tornano una pagina di login.
         */
```


**riga 827:**
```
/** Iniezioni a document-start, nell'ordine in cui devono girare. */
```


**riga 836:**
```
/*.subito.it", "https://subito.it")
        val tutte = subito + setOf("https://*.vinted.it", "https://vinted.it")

        // Per primo: nasconde il ponte prima che la pagina possa vederlo.
        // Resta attivo anche a script spenti: e' l'unica cosa che tiene il ponte
        // fuori dalle proprieta' visibili di window.
        scriptPrologo = WebViewCompat.addDocumentStartJavaScript(web, Prologo.script(nomePonte), tutte)

        if (!scriptPagina) return   // v0.5.1

        // v1.0.2 — sblocca l'ingrandimento delle foto su entrambi i siti
        scriptZoom = WebViewCompat.addDocumentStartJavaScript(web, Zoom.script(pref), tutte)

        if (bloccoAttivo) scriptCosmetico = WebViewCompat.addDocumentStartJavaScript(
            web, Cosmetici.script(Regole.cssNascondi, pref), subito
        )

        // Lo scarto degli annunci funziona anche con il blocco spento.
        scriptAnnunci = WebViewCompat.addDocumentStartJavaScript(
            web,
            ScriptAnnunci.script(Regole.sezioneIniziale, Regole.cercaApreForm, vistaCompatta, nomePonte, pref),
            subito
        )

        // Sostituire fetch e XHR e' la traccia piu' riconoscibile: solo su richiesta.
        if (modoSonda)
            scriptApi = WebViewCompat.addDocumentStartJavaScript(web, ScriptApi.script(nomePonte), tutte)
    }

    // ------------------------------------------------------------------
    // Ponte JS -> Kotlin
    // ------------------------------------------------------------------
    inner class Ponte {
        @JavascriptInterface
        fun nascondi(id: String, titolo: String) {
            Nascosti.aggiungi(this@MainActivity, id, titolo)
            runOnUiThread { aggiornaStato() }
        }

        @JavascriptInterface
        fun nascostiJson(): String = Nascosti.idJson()

        @JavascriptInterface
        fun reportDom(json: String) {
            ultimoReportDom = json
            runOnUiThread { aggiornaStato() }
        }

        @JavascriptInterface
        fun reportApi(url: String, metodo: String, invio: String, stato: Int, corpo: String) {
            ApiLog.registra(url, metodo, invio, stato, corpo)
            runOnUiThread { aggiornaStato() }
        }
    }

    // ------------------------------------------------------------------
    // Schermate e comandi
    // ------------------------------------------------------------------
    private fun applicaModo() {
        vistaRicerca.visibility = if (modoRicerca) View.VISIBLE else View.GONE
        web.visibility = if (modoRicerca) View.GONE else View.VISIBLE
        if (modoRicerca) avanzamento = null
        if (!modoRicerca && web.url.isNullOrBlank()) web.loadUrl(HOME[fonte])
    }

    /** v1.8.0 — apre un indirizzo qualsiasi nella navigazione (proposte in home). */
```


**riga 982:**
```
/** Salva la ricerca corrente: la domanda e i filtri che contano per il controllo. */
```


**riga 1080:**
```
/** v1.0.0 — un solo file con tutto cio' che non si puo' ricostruire. */
```


**riga 1089:**
```
/*
     * v1.3.0 — la caccia all'elenco delle offerte.
     * Le risposte di ricerca non lo contengono (verificato sul browser: nessun
     * campo offer/proposal/bid ne' su Subito ne' su Vinted), quindi serve una
     * seconda chiamata da incrociare per id. Quale sia, si scopre chiedendo
     * da dentro una sessione vera — questa. Solo GET.
     */
```


**riga 1109:**
```
/*
     * v1.27.0 (2026-09-01) — aggiunto "controllo-log.txt": le notifiche
     * delle ricerche salvate non arrivavano, e fino a questa versione non
     * c'era modo di vedere cosa fa davvero il controllo automatico quando
     * gira in background, ore dopo che l'app e' stata chiusa. Vedi il
     * commento su Controllore.doWork().
     *
     * --- versione precedente (fino alla v1.26.0) ---
     * private fun esporta() {
     *     val ok = listOf(
     *         scriviInDownload("requests.json", RequestLog.requestsJson()),
     *         scriviInDownload("blocklist.json", RequestLog.blocklistJson()),
     *         scriviInDownload("selectors.json", ultimoReportDom),
     *         scriviInDownload("nascosti.json", Nascosti.esportaJson()),
     *         scriviInDownload("api.json", ApiLog.json()),
     *         scriviInDownload("offerte.json", SondaOfferte.json()),   // v1.3.0
     *         scriviInDownload("ricerca-diagnostica.txt", motore.diagnostica.toString())
     *     ).all { it }
     *     toast(if (ok) "Esportati in Download/SubitoPlus" else "Esportazione non riuscita")
     * }
     * --- fine versione precedente ---
     */
```


### MotoreRicerca.kt


**riga 3:**
```
/*
 * Subito+ — motore della ricerca unificata
 * v0.4.0 (2026-08-30) — nuovo, Fase 3
 *
 * PERCHE' PASSA DAL WEBVIEW
 * Le due piattaforme non rispondono a un client HTTP scritto a mano: Akamai
 * davanti a Subito, protezioni analoghe davanti a Vinted. Qui la chiamata parte
 * da DENTRO una pagina della fonte, con la sua sessione e i suoi cookie, con
 * una fetch identica a quelle che fa il sito. Non sostituiamo nulla: chiamiamo.
 *
 * Ogni fonte ha un WebView proprio, tenuto fuori schermo, che ospita la sessione.
 */
```


**riga 29:**
```
/** Contenitore in cui agganciare le sessioni con dimensione 1x1:
     *  un WebView mai attaccato puo' comportarsi in modo diverso fra versioni. */
```


**riga 42:**
```
/** v1.2.0 — chiamate che non sono ricerche: preferiti, profili, elenchi. */
```


**riga 47:**
```
/** Diagnostica dell'ultima ricerca: URL usati, stato, conteggi, chiavi trovate. */
```


**riga 63:**
```
/*.subito.it", "https://subito.it",
                      "https://*.vinted.it", "https://vinted.it")
            )
        }
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) { pronte[f.nome] = true }
        }
        pronte[f.nome] = false
        ancora?.addView(w, android.view.ViewGroup.LayoutParams(1, 1))
        w.loadUrl(f.origine)
        sessioni[f.nome] = w
        return w
    }

    /*
     * v1.2.0 — una chiamata qualsiasi dentro la sessione di una fonte.
     * Stessa tecnica della ricerca: parte da dentro la pagina, con i cookie
     * dell'utente. E' cosi' che si mette un preferito senza che la richiesta
     * si distingua da quella che farebbe il sito.
     */
```


**riga 83:**
```
/*
     * v1.16.0 (2026-08-31) — CORREZIONE: il preferito su Vinted tornava a dare
     * 403 "access_denied" sia in aggiunta che in rimozione, con "hdr:2" (le
     * due intestazioni della v1.11.0) confermate presenti nella diagnostica.
     * Quindi non erano le intestazioni mancanti: la sessione WebView di Vinted
     * si crea UNA VOLTA all'avvio (sessione(f) -> w.loadUrl la prima volta) e
     * resta viva in background per tutta la vita dell'app. Il token CSRF che
     * l'app legge lo prende ogni volta dal DOM di quella pagina (document.scripts),
     * corretto — ma quel DOM e' quello caricato all'avvio: se nel frattempo Vinted
     * pubblica una nuova versione del sito (il token e' legato alla build, vedi
     * v1.11.0), il valore incorporato nella pagina vecchia non e' piu' quello che
     * il server si aspetta. Il sintomo torna: stesso errore su add E rimozione,
     * perche' la verifica del token non distingue le due direzioni.
     *
     * Rimedio: quando una chiamata con csrf=true torna 403, si ricarica UNA
     * VOLTA la pagina della fonte (una pagina fresca porta il token attuale) e
     * si riprova la stessa richiesta. Se fallisce ancora dopo il ricaricamento,
     * il messaggio d'errore che arriva a schermo porta il prefisso "[ricaricato]":
     * dice a chi legge il prossimo avviso che il tentativo di auto-correzione
     * e' gia' scattato e non ha bastato — segno che la causa e' un'altra (per
     * esempio una protezione anti-bot sul sito, non piu' un token vecchio).
     */
```


**riga 105:**
```
/*
     * v1.16.0 — "tentativoRicarica" sta PRIMA di "esito" e non dopo, apposta:
     * "esito" deve restare l'ULTIMO parametro, cosi' le chiamate gia' scritte
     * come motore.azione(f, c) { stato, corpo -> ... } (lambda finale fuori
     * dalle parentesi) continuano a combaciare con "esito" invece che con
     * "tentativoRicarica" — a chi chiama da fuori questo parametro resta
     * invisibile, avendo un valore di default. Stessa ragione per
     * "tentativoAttesa" aggiunto in v1.26.0, sotto: anche lui prima di "esito".
     */
```


**riga 114:**
```
/*
     * v1.26.0 (2026-09-01) — NUOVO: se la chiamata porta "paginaPrima" (i
     * preferiti Vinted, da questa versione — vedi Fonti.kt), la sessione ci
     * naviga DAVVERO prima di provare la chiamata, invece di restare ferma
     * sulla pagina caricata una volta all'avvio (sessione(), sopra). E' la
     * stessa sequenza — apri l'articolo, poi tocca il cuore — catturata
     * funzionante dalla sonda dell'utente su una vera pagina Vinted; finora
     * la sessione nascosta non la faceva mai, restava sulla home caricata
     * al primo avvio per tutta la vita del processo.
     *
     * "w.url != c.paginaPrima" evita di ricaricare a ogni tentativo mentre si
     * aspetta che la pagina finisca (i tentativi successivi la trovano gia'
     * uguale e si limitano ad aspettare "pronte"); il ricaricamento sul 403
     * (piu' sotto) resta esplicito e incondizionato, perche' li' la pagina
     * serve fresca DAVVERO, non solo "gia' quella giusta".
     *
     * "tentativoAttesa" e' un limite nuovo, che prima non c'era: aspettare
     * "pronte" non aveva mai un tetto (a differenza di avvia(), che si arrende
     * dopo 40 tentativi). Con la sola home, caricata una volta sola all'avvio
     * dell'app, non si notava mai. Una pagina di articolo vera — decine di
     * host, un centinaio di richieste, vedi la sonda mandata in chat — puo'
     * impiegare piu' a caricare o non finire mai (rete assente, per esempio):
     * meglio un errore chiaro a chi aspetta che un ciclo che non si ferma.
     */
```


**riga 156:**
```
/*
         * v1.6.0 — il ripiego. Se la prima strada non risponde 2xx si prova la
         * seconda, e all'esterno arriva un esito solo: chi chiama non deve
         * sapere quale indirizzo ha funzionato, deve sapere se ha funzionato.
         *
         * --- versione precedente (fino alla v1.15.0) ---
         * azioni[id] = { stato, corpo ->
         *     if (stato !in 200..299 && c.ripiego != null) {
         *         diagnostica.append("[${f.nome}] ${c.url} -> $stato, provo il ripiego\n")
         *         azione(f, c.ripiego, esito)
         *     } else esito(stato, corpo)
         * }
         * --- fine versione precedente ---
         */
```


**riga 170:**
```
/*
         * v1.19.0 (2026-08-31) — DIAGNOSTICA, non correzione: prima di un
         * altro tentativo "alla cieca" sul 403 di Vinted (dopo che il
         * ricaricamento della v1.16.0 e' risultato inefficace) si raccoglie
         * la prova che finora manca. Il testo che arriva da JS puo' ora
         * portare, davanti al corpo vero, un blocco diagnostico separato da
         * un carattere di controllo (\u0001, non compare mai in un JSON ne'
         * in un messaggio d'errore leggibile): intestazioni vere della
         * risposta e contatore delle chiamate anti-bot ".../j3r4zw/..." viste
         * in QUESTA sessione (vedi Prologo.kt v0.4.0 e il js piu' sotto).
         * Quel blocco va SOLO nel log "diagnostica" (quello che la sonda
         * esporta) — MAI nel testo passato a "esito", che resta corto come
         * prima e finisce nel messaggio mostrato a schermo.
         *
         * --- versione precedente (fino alla v1.18.0) ---
         * azioni[id] = { stato, corpo ->
         *     if (stato !in 200..299 && c.ripiego != null) {
         *         diagnostica.append("[${f.nome}] ${c.url} -> $stato, provo il ripiego\n")
         *         azione(f, c.ripiego, esito = esito)
         *     } else if (stato == 403 && c.csrf && !tentativoRicarica) {
         *         diagnostica.append("[${f.nome}] ${c.url} -> 403, ricarico la pagina e riprovo una volta\n")
         *         pronte[f.nome] = false
         *         w.loadUrl(f.origine)
         *         azione(f, c, tentativoRicarica = true, esito = esito)
         *     } else {
         *         val corpoFinale = if (tentativoRicarica) "[ricaricato] $corpo" else corpo
         *         esito(stato, corpoFinale)
         *     }
         * }
         * --- fine versione precedente ---
         */
```


**riga 210:**
```
/*
                 * v1.26.0 — se la chiamata ha una sua paginaPrima (i preferiti
                 * Vinted, vedi sopra) il ricaricamento la rispetta invece di
                 * tornare sempre alla home: e' quella pagina, non la home,
                 * quella la cui sessione conta per questa chiamata.
                 *
                 * --- versione precedente (fino alla v1.25.0) ---
                 * w.loadUrl(f.origine)
                 * --- fine versione precedente ---
                 */
```


**riga 234:**
```
/*
         * v1.6.0 — intestazioni anti-CSRF raccolte NELLA PAGINA.
         *
         * Vinted risponde 403 "access_denied" a una POST che porta i soli
         * cookie: la sessione la identifica, ma non basta a farla scrivere.
         * Il token non ha una posizione unica e cambia con le versioni del
         * sito, quindi non lo si indovina: lo si cerca dove puo' stare — meta,
         * cookie, storage, e in ultima istanza il sorgente della pagina — e si
         * usa il primo che si trova. Se non c'e', si chiama lo stesso: meglio
         * un 403 con una diagnosi che nessuna chiamata.
         */
```


**riga 245:**
```
/*
         * v1.7.0 (2026-08-31) — CORREZIONE: il token c'era, lo cercavo male.
         *
         * La v1.6.0 riportava "csrf:0", cioe' non trovato, e la POST restava 403.
         * Guardando la pagina vera di Vinted il token c'e' eccome, una volta
         * sola in 7,9 MB di sorgente, dentro il flight di Next.js:
         *
         *   \"CSRF_TOKEN\":\"75f6c9fa-dc8e-4e52-a000-e09dd4084b3e\"
         *
         * Due errori sovrapposti nella versione precedente:
         *  1. le virgolette sono SFUGGITE (\" e non "), e il modello cercava
         *     ["'] subito prima del valore: non poteva combaciare;
         *  2. guardavo i primi 400.000 caratteri di un documento da 7,9 milioni,
         *     e l'unica occorrenza sta ben oltre.
         *
         * Ora si scorrono i soli <script> — dove il flight vive — dall'ultimo al
         * primo, saltando quelli che non contengono "CSRF": 18 ms misurati sulla
         * pagina vera, contro il costo di leggere l'intero innerHTML.
         *
         * --- versione precedente (v1.6.0, 2026-08-31) ---
         * var m = document.querySelector('meta[name="csrf-token"],…');
         * if (!t) { var ck = /(?:^|;\s*)(?:_csrf_token|csrf_token|…)=([^;]+)/i.exec(document.cookie); … }
         * if (!t) { …localStorage… }
         * if (!t) { var h = document.documentElement.innerHTML.slice(0, 400000);
         *           var m2 = /csrf[-_]?token["']?\s*[:=]\s*["']([^"']{16,})["']/i.exec(h); … }
         * --- fine versione precedente ---
         */
```


**riga 272:**
```
/*
         * v1.11.0 (2026-08-31) — CORREZIONE: il token era giusto, mancavano due intestazioni.
         *
         * Il preferito si toglieva su Subito ma non su Vinted: stesso 403
         * "access_denied", stesso token trovato (csrf:36). Il sospetto era che
         * il token fosse sbagliato — non lo era. Letto il bundle vero del sito
         * (marketplace-web-assets.vinted.com, i file .js sotto /_next/static/chunks,
         * gli stessi che il browser scarica per mostrare la pagina) si vede il
         * client che parla con l'API — ApiGatewayClient — e cosa mette in ogni chiamata:
         *
         *   headers: { [PLATFORM_HEADER_KEY]: "web", [X_NEXT_APP]: "marketplace-web" }
         *   csrfTokenInterceptor  -> headers.set("X-CSRF-Token", "75f6c9fa-…")   (letterale nel bundle!)
         *   anonIdInterceptor     -> headers.set("X-Anon-Id", cookie ANON_ID_COOKIE)
         *
         * PLATFORM_HEADER_KEY vale "Platform" (senza X-, verificato nello stesso
         * bundle: "PLATFORM_HEADER_KEY",0,"Platform"). Il token CSRF stesso e'
         * risultato IDENTICO, lettera per lettera, a quello scritto a mano nel
         * codice del sito: e' un segreto legato alla build (RELEASE_VERSION),
         * uguale per tutti in quel momento, non per sessione — la nostra
         * estrazione era gia' corretta. Mancavano solo "Platform: web" e
         * "X-Next-App: marketplace-web", che l'app non mandava affatto.
         *
         * --- versione precedente (v1.7.0, 2026-08-31) ---
         * (uguale a sotto, ma senza le due righe opz.headers['Platform'] / ['X-Next-App'],
         *  e senza 'hdr:2' nella marca diagnostica)
         * --- fine versione precedente ---
         */
```


**riga 332:**
```
/*
         * v1.19.0 (2026-08-31) — cattura, SOLO per le chiamate con csrf=true
         * (i preferiti), le intestazioni vere della risposta e il contatore
         * di telemetria anti-bot di Prologo.kt (window.__mkTel). Va in
         * "extra", mai in "marca": "marca" resta il riassunto corto che finisce
         * nel messaggio a schermo, "extra" e' il blocco lungo che la funzione
         * chiamante (sopra) smista SOLO in "diagnostica". Se qualcosa va storto
         * qui dentro (per esempio le intestazioni non leggibili) non deve MAI
         * far fallire la chiamata vera: e' tutto avvolto in try/catch a parte.
         */
```


**riga 389:**
```
/** v1.2.0 — legge un valore dalla pagina della fonte (identita', stato). */
```


**riga 399:**
```
/** Scalda le sessioni senza cercare nulla: rende immediata la prima ricerca. */
```


**riga 420:**
```
/*
         * Due tentativi. Il primo con i cookie ("include"): serve alle risposte
         * personalizzate. Se la fonte espone CORS con Allow-Origin generico,
         * quel tentativo viene rifiutato dal browser, e il secondo — senza
         * credenziali — passa. Meglio due tentativi che una regola sbagliata.
         */
```


### BarraSuperiore.kt


**riga 3:**
```
/*
 * Subito+ — barra dei comandi
 * v0.5.1 (2026-08-30)
 *
 * Sostituisce la fila di otto pulsanti che occupava la parte alta dello schermo.
 * In vista restano il nome, l'interruttore fra ricerca e navigazione, e il menu:
 * tutto il resto sono comandi che si usano di rado o mai, e stavano li' solo
 * perche' costruire un menu a mano sarebbe costato piu' di una fila di pulsanti.
 *
 * Primo pezzo di interfaccia in Compose. Convive con le viste tradizionali del
 * resto della schermata: e' cosi' che il rifacimento procede un pezzo per volta.
 */
```


**riga 51:**
```
/** Tutto lo stato che la barra mostra, in un solo oggetto. */
```


**riga 58:**
```
/** v0.5.1 — interruttore generale degli script che iniettiamo nelle pagine. */
```


**riga 61:**
```
/** v0.6.0 — quante ricerche salvate e quante novità non ancora guardate. */
```


**riga 64:**
```
/** v0.7.0 — titolo della pagina aperta, mostrato al posto di "Navigazione". */
```


**riga 67:**
```
/** null = nessun caricamento; -1 = attesa senza percentuale; 0..100 = avanzamento */
```


**riga 69:**
```
/** v1.15.0 — il banner "tocca ⋮ e poi Sito per accedere", solo al primo avvio. */
```


**riga 73:**
```
/** Le azioni che la barra puo' scatenare. */
```


**riga 82:**
```
/** v1.9.0 — preferiti raggiungibili da ovunque, non solo dalla home. */
```


**riga 88:**
```
/** v1.3.0 — batteria di sole GET per trovare l'elenco delle offerte fatte. */
```


**riga 96:**
```
/** v1.15.0 — chiude il banner di guida al login, per sempre. */
```


**riga 98:**
```
/*
     * v1.18.0 (2026-08-31) — nuovo: torna alla schermata iniziale (le ultime
     * proposte) da qualunque punto dell'app — una ricerca in corso, i
     * preferiti, o la navigazione dentro "Sito". Vedi il commento sull'icona
     * qui sotto e su MainActivity.vaiAllaHome().
     */
```


**riga 105:**
```
/** v1.24.0 — apre il pannello Opzioni (vedi DialogoOpzioni.kt). */
```


**riga 116:**
```
/*
                 * v1.6.0 — la versione accanto al nome. Serve a sapere, guardando
                 * lo schermo, quale build si sta provando: con consegne cosi'
                 * ravvicinate "l'ho installata?" e' la domanda piu' frequente.
                 * Il numero viene da BuildConfig, cioe' da build.gradle.kts:
                 * una fonte sola, impossibile che diverga.
                 */
```


**riga 123:**
```
/*
                 * v1.13.0 (2026-08-31) — il nome in barra segue il nuovo nome
                 * dell'app: "Subito+" era troppo vicino al marchio del sito.
                 *
                 * --- versione precedente (v0.7.0, 2026-08-30) ---
                 * stato.modoRicerca -> "Subito+"
                 * --- fine versione precedente ---
                 */
```


**riga 157:**
```
/*
                 * v1.9.0 — preferiti e ricerche salvate diventano due icone in
                 * barra. Erano due pulsanti nella schermata iniziale: si vedevano
                 * solo li', e sparivano appena si cercava qualcosa. In barra ci
                 * si arriva sempre, ed e' il posto dove si va a cercarli.
                 * Sul campanello resta il pallino delle novita' non guardate.
                 */
```


**riga 165:**
```
/*
                     * v1.18.0 — icona "home": "indietro" da una ricerca ora torna
                     * gia' da solo qui (vedi MainActivity, il gestore di "indietro"),
                     * ma un tocco diretto e' piu' rapido di un percorso a ritroso —
                     * specie da dentro "Sito", dove "indietro" scorre prima tutta
                     * la cronologia della pagina prima di arrivare in app.
                     */
```


**riga 209:**
```
/*
             * v1.15.0 — banner di guida al login, solo al primo avvio.
             *
             * L'account su Subito o Vinted si usa toccando "Sito" nel menu qui
             * sotto: apre quel sito dentro l'app, dove ci si accede come si
             * farebbe dal browser. Non e' ovvio la prima volta — i tre puntini
             * non dicono "e' qui che si fa il login" — quindi lo si indica una
             * volta sola, subito sotto la barra dov'e' il menu, e poi si sparisce
             * per sempre: alla chiusura esplicita (la ✕) o non appena l'utente
             * tocca davvero "Sito" (vedi MainActivity, cambiaFonte).
             */
```


**riga 282:**
```
/*
     * v1.26.0 (2026-09-01) — CORREZIONE: il menu era cresciuto a quattordici
     * righe. Restano solo le prime sei (quelle di uso frequente, o utili da
     * qualunque schermata); backup, import, sonda e diagnostica — di uso
     * raro — si raggiungono ora da "Opzioni…", in fondo. Vedi il commento
     * in cima a DialogoOpzioni.kt.
     *
     * --- versione precedente (fino alla v1.25.0) ---
     * DropdownMenu(expanded = aperto, onDismissRequest = { aperto = false }) {
     *
     *     voce(
     *         "Ricerche salvate (${stato.ricerche})" +
     *                 if (stato.nuovi > 0) " · ${stato.nuovi} nuovi" else "",
     *         null
     *     ) { aperto = false; azioni.apriRicerche() }
     *     voce("Annunci nascosti (${stato.nascosti})", null) { aperto = false; azioni.apriNascosti() }
     *     voce("Vista compatta", stato.vistaCompatta) { aperto = false; azioni.cambiaVista() }
     *
     *     HorizontalDivider()
     *
     *     if (!stato.modoRicerca) {
     *         voce("Condividi la pagina", null) { aperto = false; azioni.condividi() }
     *         voce("Apri nel browser", null) { aperto = false; azioni.apriNelBrowser() }
     *         HorizontalDivider()
     *     }
     *
     *     voce("Blocco pubblicità", stato.bloccoAttivo) { aperto = false; azioni.cambiaBlocco() }
     *     voce("Script nella pagina", stato.scriptPagina) { aperto = false; azioni.cambiaScriptPagina() }
     *     voce(if (stato.fonteVinted) "Sito: Vinted" else "Sito: Subito", null) { aperto = false; azioni.cambiaFonte() }
     *
     *     HorizontalDivider()
     *
     *     voce("Salva backup", null) { aperto = false; azioni.salvaBackup() }
     *     voce("Importa file…", null) { aperto = false; azioni.caricaRegole() }
     *
     *     HorizontalDivider()
     *
     *     voce("Modalità sonda", stato.modoSonda) { aperto = false; azioni.cambiaSonda() }
     *     voce("Analizza pagina", null) { aperto = false; azioni.analizza() }
     *     voce("Sonda offerte", null) { aperto = false; azioni.sondaOfferte() }
     *     voce("Esporta diagnostica", null) { aperto = false; azioni.esporta() }
     *     voce("Azzera contatori", null) { aperto = false; azioni.azzera() }
     *
     *     HorizontalDivider()
     *
     *     // v1.24.0 — prima voce di un menu pensato per crescere: vedi DialogoOpzioni.kt.
     *     voce("Opzioni…", null) { aperto = false; azioni.apriOpzioni() }
     * }
     * --- fine versione precedente ---
     */
```


**riga 360:**
```
/** @param acceso null per un comando semplice, true/false per un interruttore. */
```


### DialogoOpzioni.kt


**riga 3:**
```
/*
 * Subito+ — pannello "Opzioni"
 * v1.24.0 (2026-08-31) — nuovo.
 *
 * Primo pezzo di un menu pensato per crescere: oggi una sola voce, ma il
 * dialogo e AzioniOpzioni sono gia' fatti per accoglierne altre senza
 * riscrivere la struttura (una Column con dentro tante sezioni, non un
 * singolo controllo isolato). Stessa forma di DialogoRicerche: un AlertDialog
 * invece di un foglio dal basso, per lo stesso motivo — la barra dei comandi
 * vive in una ComposeView alta poche decine di pixel.
 *
 * Prima voce: cosa fare all'avvio. Da sempre (v1.17.0) l'app riparte da sola
 * sull'ultima ricerca fatta se il processo e' stato chiuso — comodo per chi
 * cerca sempre la stessa cosa, fastidioso per chi preferisce vedere prima le
 * ultime proposte (la home). Ora e' una scelta, non un comportamento fisso.
 */
```


**riga 20:**
```
/*
 * v1.26.0 (2026-09-01) — CORREZIONE: il menu dai tre pallini (BarraSuperiore.kt)
 * era cresciuto voce dopo voce da quando esisteva solo quello (v0.5.0) fino a
 * quattordici righe. L'utente chiede di spostare qui tutto tranne le prime
 * sei (Ricerche salvate, Annunci nascosti, Vista compatta, Blocco pubblicita',
 * Script nella pagina, Sito) — quelle restano nel menu perche' si usano
 * spesso o servono da qualunque schermata sia aperta; il resto (backup,
 * import, sonda, diagnostica) si usa di rado, ed e' esattamente cio' per cui
 * questo pannello e' nato. Le azioni vere restano dove gia' vivevano in
 * MainActivity — qui arrivano solo le lambda, come per il menu.
 *
 * "Condividi la pagina" e "Apri nel browser" NON si spostano: sono legate
 * alla pagina aperta in quel momento (compaiono solo fuori da "Ricerca"),
 * non sono impostazioni — spostarle in un pannello raggiungibile sempre le
 * renderebbe cliccabili anche quando non hanno una pagina a cui riferirsi.
 */
```


**riga 57:**
```
/** Le azioni che il pannello Opzioni puo' scatenare. Una per voce. */
```


**riga 59:**
```
/** true = all'avvio vai alla home; false = riprendi l'ultima ricerca. */
```


**riga 61:**
```
/*
     * v1.26.0 — le sette voci spostate qui dal menu dai tre pallini, nello
     * stesso ordine in cui ci stavano: sono le stesse azioni di sempre
     * (vedi MainActivity.azioni() prima della v1.26.0 e AzioniBarra.kt),
     * solo raggiunte da un altro posto.
     */
```


**riga 115:**
```
/*
                 * v1.26.0 — LazyColumn con un tetto d'altezza, stessa tecnica
                 * di DialogoRicerche: qui dentro le righe sono sette e la
                 * lista non cresce da sola come quella delle ricerche
                 * salvate, ma un tetto costa niente e mette al riparo da uno
                 * schermo piccolo.
                 */
```


**riga 146:**
```
/** Una riga radio-button + testo, tutta cliccabile. */
```


**riga 160:**
```
/*
 * v1.26.0 — una riga di "Strumenti": stesso schema della voce del menu dai
 * tre pallini (testo + "ON"/"OFF" se e' un interruttore, altrimenti un
 * comando semplice), ma senza il DropdownMenuItem che serve solo dentro un
 * DropdownMenu — qui siamo dentro un AlertDialog.
 */
```


### ScriptAnnunci.kt


**riga 3:**
```
/*
 * Subito+ — scarto dei singoli annunci e ritocchi all'interfaccia
 * v0.5.1 (2026-08-30)
 *
 * COME RICONOSCE UN ANNUNCIO
 * Dall'export reale della sonda v0.2.0: i link di dettaglio hanno la forma
 *   /<categoria>/<slug-del-titolo>-<idNumerico>.htm
 *   es. /moto-e-scooter/moto-guzzi-v100-mandello-e5-wind-tunnel-bari-632869214.htm
 * L'ultimo gruppo di cifre prima di .htm e' l'identificatore stabile dell'annuncio.
 * Non uso classi CSS per riconoscerlo: le classi sono generate (index-module_...)
 * e cambiano a ogni build del sito, il formato dell'URL no.
 *
 * COME TROVA LA CARD DA NASCONDERE
 * Non c'e' un selettore fisso della card. Si risale dal link finche' l'antenato
 * contiene link di UN SOLO annuncio: l'ultimo antenato "puro" e' la card.
 * Cosi' il meccanismo sopravvive a un rifacimento del sito senza toccare le regole.
 *
 * v0.4.5 — PRESTAZIONI. Toccare "Contatta" o "Fai un'offerta" su una scheda
 * aveva alcuni secondi di ritardo: aprire quei pannelli genera una valanga di
 * modifiche al DOM, e a ogni modifica questo script rifaceva la sua scansione
 * sul filo principale, l'unico su cui il browser puo' anche rispondere al tocco.
 * Tre correzioni:
 *   1. sulle schede di dettaglio lo script non fa nulla e non osserva nulla:
 *      li' non ci sono card da scartare, quindi non c'e' motivo di girare
 *   2. l'identificatore si ricava con un'espressione regolare sull'href invece
 *      che costruendo un oggetto URL per ogni link (era il costo maggiore)
 *   3. la risalita alla card usa contains() sulla lista gia' nota degli annunci
 *      invece di interrogare il DOM a ogni livello
 *
 * Nota sintassi: niente template literal e niente simbolo dollaro nel JS.
 */
```


**riga 36:**
```
/**
     * @param sezioneIniziale scheda da aprire sulla home ("" per non toccare nulla)
     * @param cercaApreForm   se vero, il pulsante "Cerca" della barra in basso
     *                        riporta alla home con il modulo di ricerca invece che ai risultati
     * @param vistaCompatta   se vero, ogni card viene ridisposta con foto piccola a sinistra
     *                        e testo a destra (v0.3.0)
     */
```


**riga 51:**
```
/* ---------------- archivio degli scartati ---------------- */
```


**riga 62:**
```
/* ---------------- riconoscimento annuncio ---------------- */
```


**riga 71:**
```
/* Vero sulla scheda di un singolo annuncio: li' non c'e' niente da fare. */
```


**riga 74:**
```
/*
           * Risale finche' l'antenato contiene link di un solo annuncio.
           * v0.4.5: il confronto usa contains() sulla lista degli annunci gia'
           * raccolta, invece di una querySelectorAll a ogni livello di risalita.
           */
```


**riga 94:**
```
/* ---------------- pulsante di scarto ---------------- */
```


**riga 126:**
```
/* ---------------- vista compatta ----------------
             Il sito non espone un selettore di layout nella versione mobile.
             Qui non si usano classi del sito (sono generate e cambiano): si cerca
             dentro la card il primo nodo che ha almeno due figli, uno con l'immagine
             e uno con del testo, e si dispone quel nodo in orizzontale. */
```


**riga 133:**
```
/*
           * v0.5.1 — CORREZIONE. La versione precedente scendeva nella card solo
           * finche' incontrava nodi con un unico figlio: bastava un contenitore
           * con due figli, di cui nessuno con l'immagine, per fermarla — ed e'
           * il motivo per cui la vista compatta spesso non faceva nulla.
           * Ora si parte dall'immagine e si RISALE finche' si trova un livello
           * dove accanto al ramo dell'immagine c'e' un fratello con del testo:
           * quello e' il livello da disporre in orizzontale.
           *
           * --- versione precedente (v0.4.2, 2026-08-30) ---
           * var cur = cont;
           * for (var g = 0; g < 6; g++) { ...scendeva nei figli unici... }
           * --- fine versione precedente ---
           */
```


**riga 189:**
```
/* ---------------- passata sulla pagina ---------------- */
```


**riga 193:**
```
/* v0.4.5 */
```


**riga 202:**
```
/* Con meno di due annunci non siamo in una lista. */
```


**riga 221:**
```
/* ---------------- ritocchi all'interfaccia ---------------- */
```


**riga 227:**
```
/* La home apre la scheda Motori: se richiesto, si passa subito a un'altra. */
```


**riga 241:**
```
/* "Cerca" nella barra in basso porta ai risultati: lo si riporta al modulo di ricerca. */
```


**riga 248:**
```
/* solo barra in basso */
```


**riga 254:**
```
/* ---------------- avvio e scroll infinito ---------------- */
```


**riga 259:**
```
/* v0.4.5: attesa piu' lunga e, quando c'e', lavoro nei momenti di quiete */
```


**riga 270:**
```
/* v0.4.5: sulla scheda ci fermiamo qui */
```


### ScriptApi.kt


**riga 3:**
```
/*
 * Subito+ — cattura delle chiamate dentro la pagina
 * v1.0.6 (2026-08-30)
 *
 * Viene iniettato a document-start, quindi PRIMA che la pagina definisca le sue
 * chiamate: sostituisce fetch e XMLHttpRequest con versioni che, a risposta
 * arrivata, ne consegnano un campione al lato Kotlin.
 * Non modifica il comportamento: la risposta originale viene restituita intatta
 * (fetch usa clone(), cosi' il corpo resta leggibile dalla pagina).
 *
 * v1.0.6 — si consegna anche il METODO e il CORPO DELLA RICHIESTA.
 * Nasce da una domanda precisa: mostrare in elenco quanto si e' offerto per un
 * annuncio. L'importo di un'offerta viaggia nel corpo di una POST, e senza
 * quello si vedrebbe solo che una richiesta e' partita. Il corpo della risposta
 * da solo non basta nemmeno: dice com'e' andata, non cosa si e' chiesto.
 *
 * Resta acceso solo in modalita' sonda: e' la traccia piu' riconoscibile che
 * l'app possa lasciare, perche' dopo la sostituzione toString su fetch non
 * restituisce piu' [native code].
 *
 * Nota sintassi: niente template literal e niente simbolo dollaro nel JS.
 */
```


**riga 46:**
```
/* Il corpo della richiesta puo' essere testo, FormData o altro:
         si consegna solo cio' che si legge senza consumarlo. */
```


**riga 62:**
```
/* ---- fetch ---- */
```


**riga 79:**
```
/* Anche una risposta non JSON e' informativa quando la
                   richiesta portava qualcosa: dice che quel gesto e' passato di li'. */
```


**riga 89:**
```
/* ---- XMLHttpRequest ---- */
```


### SondaOfferte.kt


**riga 3:**
```
/*
 * Subito+ — sonda delle offerte
 * v1.3.0 (2026-08-31) — nuovo
 *
 * A COSA SERVE
 * Mostrare «offerti xxx €» accanto a un annuncio richiede di sapere quali
 * offerte hai fatto e per quanto. Verificato sul browser, quel dato NON esiste
 * nelle risposte di ricerca:
 *
 *   hades.subito.it/v1/search/items -> ad = {urn, type, category, subject,
 *     body, dates, images, images_360, features, advertiser, geo, urls}
 *     features = /price, /item_condition, /computer_type, spedizione…
 *     nessun campo con offer|proposal|bid|trattativa in tutto il JSON
 *
 *   vinted /api/v2/catalog/items -> item = {id, title, price, brand_title,
 *     user, photos, favourite_count, is_favourite, service_fee,
 *     total_item_price, view_count, size_title, status, item_box}
 *     is_favourite c'e' (lo usa il cuore), delle offerte nulla
 *
 * Ed e' giusto cosi': la risposta di ricerca e' impersonale, non sa chi la
 * chiede. L'elenco delle offerte e' un'altra chiamata, da incrociare per id.
 *
 * COSA HA GIA' DETTO IL CAMPO
 *   GET https://hades.subito.it/v1/p2p/transactions
 *       -> 400 {"error_code":"TRANSACTIONS:bad-request",
 *               "info":"error decoding request: missing role param"}
 *   GET .../v1/p2p/transactions?role=buyer
 *       -> 401 AUTH:unknown-user   (browser non autenticato: non e' un rifiuto,
 *                                   e' l'assenza di sessione)
 * Il 400 dimostra che l'elenco esiste e vuole "role". Il resto — il nome dei
 * campi, dove sta l'importo, come si risale all'annuncio — si vede solo da
 * dentro una sessione vera. Questa sonda e' quella sessione: gira nel WebView
 * dell'app, con i tuoi cookie, e scrive quello che trova.
 *
 * REGOLE DI CONDOTTA
 * Solo GET. Nessuna chiamata che crei, modifichi o cancelli qualcosa: una sonda
 * che ordina per sbaglio non e' una sonda. Una richiesta per volta, con pausa,
 * perche' una raffica e' la firma che distingue un programma da una persona.
 */
```


**riga 50:**
```
/** Quanto si conserva di ogni risposta: serve la forma, non i dati. */
```


**riga 53:**
```
/** Pausa fra una richiesta e la successiva. */
```


**riga 58:**
```
/*
     * I candidati. Ordine: prima quelli con una prova a favore, poi le ipotesi.
     * Un 404 o un 400 qui non e' un fallimento: e' informazione. L'unico esito
     * inutile e' non aver chiesto.
     */
```


**riga 97:**
```
/**
     * Esegue tutta la batteria, una chiamata per volta.
     * @param avanzamento invocato a ogni risposta con (fatte, totali)
     * @param finito invocato a fine giro
     */
```


**riga 130:**
```
/**
     * Il rapporto da esportare. Oltre alle risposte porta una lettura: quale
     * chiamata ha risposto, quale ha chiesto una sessione, quale non esiste.
     * Serve a non dover interpretare 400 e 401 a memoria fra una settimana.
     */
```


### DomProbe.kt


**riga 3:**
```
/*
 * Subito+ — rilevamento della struttura della pagina
 * v0.2.0 (2026-08-30)
 *
 * CAMBIO IMPORTANTE rispetto alla v0.1.0.
 * La prima versione cercava le card annuncio sul pattern di link "/vi/<id>.htm".
 * L'export reale ha restituito cardTrovate = 0: su subito.it quel pattern non esiste.
 * Quindi qui non si ipotizza piu' nulla: si raccolgono TUTTI i link interni, se ne
 * normalizza il percorso (le cifre diventano #) e si contano le forme ricorrenti.
 * La forma piu' frequente che porta con se' immagine e prezzo E' la card annuncio,
 * e da li' si ricava l'identificatore stabile che servira' alla Fase 2.
 *
 * --- versione precedente (v0.1.0, 2026-08-30) ---
 * var re = /\/vi\/(\d+)\.htm/;
 * ...ricerca diretta delle ancore che corrispondono a quel pattern...
 * --- fine versione precedente ---
 *
 * Nota sintassi: niente template literal e niente simbolo dollaro nel JS,
 * perche' la stringa e' un raw string Kotlin.
 */
```


**riga 25:**
```
/** @param selettoriAdv selettori cosmetici attivi, per contare cosa resta visibile dopo il filtro. */
```


**riga 54:**
```
/* ---- 1. forme di link interne, contate ---- */
```


**riga 71:**
```
/* ---- 2. candidate card: link che contengono immagine e prezzo ---- */
```


**riga 96:**
```
/* ---- 3. marcatori sponsorizzato dentro il flusso dei risultati ---- */
```


**riga 112:**
```
/* ---- 4. verifica Fase 1: cosa resta visibile tra gli slot pubblicitari ---- */
```


**riga 124:**
```
/* ---- 4b. promozioni interne di Subito (non pubblicita' di terzi) ----
                 v0.3.0: servono per decidere se e come toglierle nella prossima revisione. */
```


**riga 140:**
```
/* ---- 5. iframe di terze parti rimasti ---- */
```


### Nascosti.kt


**riga 3:**
```
/*
 * Subito+ — archivio degli annunci scartati
 * v0.3.0 (2026-08-30) — nuovo in Fase 2
 *
 * Un file JSON in filesDir: { "<idAnnuncio>": {"t": <timestamp>, "n": "<titolo breve>"} }.
 * Niente database: gli ID sono numeri corti, anche diecimila voci restano
 * un file da poche centinaia di kB che si carica in memoria una volta sola.
 * Il titolo serve solo alla schermata di ripristino, per capire cosa si sta riportando.
 *
 * Il set vive anche in memoria come HashMap, perche' lo script della pagina
 * lo interroga a ogni giro di scroll e deve rispondere in microsecondi.
 */
```


**riga 32:**
```
/*
                 * v1.0.2 (2026-08-30) — CORREZIONE.
                 * esportaJson() scrive { versione, quanti, annunci: { id: {...} } },
                 * mentre qui si leggevano le chiavi della RADICE: alla prima
                 * ("versione", che e' testo e non un oggetto) partiva l'eccezione,
                 * il catch la inghiottiva e l'archivio ripartiva vuoto.
                 * Risultato: dalla v0.3.0 gli annunci nascosti non sono mai
                 * sopravvissuti alla chiusura dell'app.
                 *
                 * --- versione precedente (v0.3.0, 2026-08-30) ---
                 * val j = JSONObject(f.readText())
                 * j.keys().forEach { k ->
                 *     val o = j.getJSONObject(k)
                 *     mappa[k] = Pair(o.optLong("t"), o.optString("n"))
                 * }
                 * --- fine versione precedente ---
                 *
                 * Si accetta anche la forma senza involucro, che e' quella che
                 * il vecchio codice si aspettava: un backup fatto a mano o un
                 * file di un'altra provenienza continua a funzionare.
                 */
```


**riga 59:**
```
/* file corrotto: si riparte da vuoto */
```


**riga 66:**
```
/** v0.4.0 — usato dalla ricerca unificata (chiavi "id" per Subito, "v:id" per Vinted). */
```


**riga 82:**
```
/** Elenco per la schermata di ripristino, dal piu' recente. */
```


**riga 88:**
```
/** Consumato dallo script della pagina: array JSON di soli ID. */
```


**riga 100:**
```
/** Import additivo: unisce, non sostituisce. Serve per travasare da un telefono all'altro. */
```


### RequestLog.kt


**riga 3:**
```
/*
 * Subito+ / Fase 0 "Sonda" — registro delle richieste di rete
 * v0.1.0 (2026-08-30)
 *
 * Scopo: la sonda NON blocca nulla. Osserva ogni risorsa che il WebView chiede,
 * la raggruppa per host e propone una blocklist basata su euristica.
 * L'output rivisto a mano diventa la blocklist definitiva della Fase 1.
 */
```


**riga 17:**
```
/*
     * Host che consideriamo "casa".
     * v0.2.0 (2026-08-30): aggiunti dopo l'export reale — sbito.it e' il CDN delle
     * immagini degli annunci (85 richieste in una sessione) e nella v0.1.0 finiva
     * per errore tra le terze parti da valutare.
     *
     * --- versione precedente (v0.1.0, 2026-08-30) ---
     * private val FIRST_PARTY = listOf("subito.it", "subito.shop", "subitostatic.it")
     * --- fine versione precedente ---
     */
```


**riga 31:**
```
/*
     * Frammenti di dominio noti dell'ecosistema adv/tracking.
     * Lista di partenza: serve solo a pre-marcare i sospetti nel report,
     * NON è ancora la blocklist. Quella la si conferma sui dati reali.
     */
```


**riga 49:**
```
/*
     * Consent Management Platform e affini: NON vanno bloccati alla cieca
     * (senza il banner cookie il sito può restare in stato di attesa).
     * Li marchiamo a parte per deciderlo con prova alla mano in Fase 1.
     */
```


**riga 64:**
```
/*
     * v0.4.6 — il registro dettagliato serve alla diagnostica, non all'uso normale.
     * Tenerlo acceso significava prendere un lock e scrivere in una mappa per OGNI
     * risorsa richiesta dalla pagina, immagini comprese, sul filo di rete.
     * Ora di serie si contano soltanto le richieste; il dettaglio si accende
     * insieme alla sonda.
     */
```


**riga 74:**
```
/** Chiamata dal thread di rete del WebView: deve essere sincronizzata e leggerissima. */
```


**riga 75:**
```
/** Chiamata dal thread di rete del WebView per ogni risorsa: deve costare quasi nulla. */
```


**riga 104:**
```
/** Report completo: ogni host visto, con quante volte e come lo abbiamo classificato. */
```


**riga 129:**
```
/** Blocklist proposta: solo i sospetti + i terze parti mai first-party, da rivedere a mano. */
```


### RicercheSalvate.kt


**riga 3:**
```
/*
 * Subito+ — ricerche salvate
 * v0.6.0 (2026-08-30) — Fase 5
 *
 * Una ricerca salvata e' la domanda piu' cio' che si e' gia' visto: e' la
 * seconda meta' a rendere utile la prima. Senza memoria di quali annunci sono
 * gia' passati sotto gli occhi, un controllo periodico direbbe ogni volta le
 * stesse cose.
 *
 * Gli identificatori gia' visti si conservano fino a un tetto: oltre, i piu'
 * vecchi cadono. Un annuncio uscito dai risultati mesi fa e poi ricomparso e'
 * a tutti gli effetti una novita'.
 */
```


**riga 89:**
```
/* file rovinato: si riparte da vuoto */
```


**riga 108:**
```
/*
             * I risultati gia' sotto gli occhi al momento del salvataggio non
             * sono novita': si parte dalla lista corrente come base.
             */
```


**riga 131:**
```
/** @return quanti identificatori erano davvero nuovi. */
```


**riga 163:**
```
/**
     * v1.0.0 — ripristino da backup, additivo: le ricerche gia' presenti
     * restano com'erano, comprese le loro memorie di cosa hanno gia' mostrato.
     * @return quante ne sono state aggiunte, -1 se il file non e' leggibile.
     */
```


### StoricoRicerche.kt


**riga 3:**
```
/*
 * Subito+ — memoria delle ricerche gia' fatte
 * v1.1.0 (2026-08-30)
 *
 * Serve all'interruttore "solo i nuovi": per sapere cosa e' nuovo bisogna
 * ricordare cosa c'era la volta prima. Per ogni testo cercato si conserva
 * l'insieme degli identificatori visti nell'ultima esecuzione.
 *
 * Differenza rispetto a [RicercheSalvate], che tiene una memoria simile:
 * quella lavora per le ricerche salvate e i controlli automatici, e cresce a
 * ogni passata; questa e' l'istantanea dell'ULTIMA esecuzione di una qualsiasi
 * ricerca fatta a mano, e viene sostituita ogni volta.
 *
 * Il confronto e' per testo cercato, non per filtri: cambiare l'ordinamento o
 * la fascia di prezzo non deve far sembrare nuovo cio' che era gia' passato.
 */
```


**riga 50:**
```
/* file rovinato: si riparte da vuoto */
```


**riga 53:**
```
/** Cosa c'era l'ultima volta che questa ricerca e' stata eseguita. */
```


**riga 60:**
```
/** Sostituisce l'istantanea: da adesso "la volta prima" e' questa. */
```


### Preferiti.kt


**riga 3:**
```
/*
 * Subito+ — preferiti
 * v1.2.0 (2026-08-30)
 *
 * Differenza importante rispetto agli annunci nascosti: i nascosti sono una
 * cosa NOSTRA, esistono solo qui. I preferiti invece esistono sulle due
 * piattaforme, e la verita' e' la' — se metti un cuore dal sito, l'app deve
 * accorgersene; se lo metti da qui, deve finire sul sito.
 *
 * Quindi questo archivio non e' la fonte di verita', e' una copia locale che
 * serve a due cose: disegnare il cuore subito, senza aspettare la rete, e
 * ricordarsi lo stato fra un avvio e l'altro. A ogni ricerca viene riallineato
 * con quello che dicono le piattaforme.
 */
```


**riga 39:**
```
/* file rovinato: si riparte da vuoto */
```


**riga 51:**
```
/**
     * Riallinea con la piattaforma. Si sostituisce lo stato solo per le chiavi
     * di quella fonte: l'altra non c'entra e non va toccata.
     *
     * @param prefisso "v:" per Vinted, "" per Subito (vedi Annuncio.chiave)
     */
```


**riga 67:**
```
/** Allineamento parziale: quello che i risultati di ricerca dichiarano. */
```


### Prologo.kt


**riga 3:**
```
/*
 * Subito+ — prologo di pagina
 * v0.3.2 (2026-08-30) — nuovo, riduce la rilevabilita'
 *
 * IL PROBLEMA CHE RISOLVE
 * addJavascriptInterface pubblica un oggetto su window, visibile a qualsiasi
 * script della pagina. Fino alla v0.3.1 si chiamava "SubitoPlus": bastava un
 * for..in su window per sapere che il sito era aperto dentro questa app, e
 * quel nome diceva pure quale.
 *
 * Questo script gira per primo, prima di ogni script del sito:
 *   1. prende il riferimento al ponte
 *   2. lo cancella da window
 *   3. lo ridefinisce con lo stesso nome ma NON enumerabile, cosi' non compare
 *      in Object.keys(window) ne' in un for..in
 * Il nome e' casuale e diverso per ogni installazione: non e' una stringa
 * riconoscibile nemmeno se qualcuno la cercasse di proposito.
 *
 * Resta vero che una verifica mirata (getOwnPropertyNames) lo troverebbe:
 * l'obiettivo non e' l'invisibilita', e' non essere appariscenti.
 */
```


**riga 38:**
```
/*
       * v0.4.0 (2026-08-31) — CONTATORE TELEMETRIA ANTI-BOT (solo diagnostica,
       * nessun comportamento cambia per l'utente).
       *
       * La sonda mandata in chat (api.json) ha trovato, sulle pagine vinted.it,
       * un secondo sistema anti-bot oltre a Datadome: chiamate POST verso un
       * percorso offuscato (".../j3r4zw/v1/consume") che manda un pacchetto
       * cifrato di segnali di comportamento, con un "avvio" iniziale
       * (".../j3r4zw/v1/config"). Lo script che lo carica (trovato anche su
       * k7v3q2.vinted.com) ha lo stesso hash di versione della risposta di
       * quel "config" — e' davvero attivo sul sito, non un falso positivo.
       *
       * Il sospetto, non ancora provato: la sessione nascosta 1x1 dell'app,
       * mai toccata da un dito vero, non genera mai i segnali che questo
       * sistema si aspetta — e per questo le SCRITTURE (mettere un preferito)
       * vengono respinte mentre le LETTURE (cercare, leggere le conversazioni)
       * passano lo stesso. Per saperlo serve vedere se, nella sessione VERA
       * che fallisce, queste chiamate partono affatto: finora la sonda le ha
       * viste solo da una scheda di browser normale, mai da dentro il WebView
       * nascosto dell'app.
       *
       * Questo contatore, letto da MotoreRicerca subito dopo un 403 sul
       * preferito, dice quante volte e' stato chiamato ".../j3r4zw/..." IN
       * QUESTA sessione prima del fallimento, e con che esito. Se resta a
       * zero il sospetto e' confermato; se e' alto quanto in una sessione
       * normale, va escluso e cercata un'altra causa. E' nascosto da
       * Object.keys(window) per lo stesso motivo del ponte sopra: non e'
       * invisibilita', e' non essere appariscenti.
       */
```


### Regole.kt


**riga 3:**
```
/*
 * Subito+ — motore delle regole di blocco
 * v0.2.0 (2026-08-30) — nuovo in Fase 1
 *
 * Le regole NON sono compilate dentro l'app: stanno in un JSON.
 * All'avvio si carica quello importato dall'utente (filesDir), se assente
 * quello di serie (assets/regole.json). Cosi' un cambio nell'ecosistema
 * pubblicitario si risolve con un file, non con una nuova build.
 *
 * Ordine di valutazione, pensato per non rompere mai il sito:
 *   1. percorso in "bloccaPercorsi" -> blocca   (regola precisa, vale anche sui domini di casa)
 *   2. host in "consentiSempre"     -> passa    (subito.it, CDN immagini, consenso, antifrode, chat)
 *   3. host in "bloccaHost"         -> blocca
 *   4. altrimenti                   -> passa
 * In dubbio si lascia passare: un banner in piu' e' meno grave di una pagina rotta.
 *
 * v0.2.0 (2026-08-30) — l'ordine e' cambiato rispetto alla prima stesura:
 *   prima "consentiSempre" veniva valutato per primo, e questo rendeva impossibile
 *   fermare la raccolta analytics servita da un dominio subito.it (tags.subito.it/g/collect).
 *   Sezione precedente conservata nel metodo blocca().
 */
```


**riga 41:**
```
/* v0.3.0 (2026-08-30) — preferenze di navigazione, anch'esse nel file regole. */
```


**riga 45:**
```
/** Contatore delle risorse effettivamente bloccate, mostrato nella barra di stato. */
```


**riga 59:**
```
/** Usata da "Importa regole": valida, salva e attiva subito. */
```


**riga 90:**
```
/** Vero se l'URL va fermato. Gira sul thread di rete del WebView: deve restare O(n) su liste corte. */
```


### Cosmetici.kt


**riga 3:**
```
/*
 * Subito+ — filtri cosmetici
 * v0.2.0 (2026-08-30) — nuovo in Fase 1
 *
 * Il blocco di rete impedisce il download della pubblicita', ma nel DOM resta
 * il contenitore vuoto, che lascia un buco nel layout. Qui iniettiamo un foglio
 * di stile PRIMA che la pagina esegua i suoi script (document start), cosi'
 * non si vede nessun lampo, e un osservatore che richiude i contenitori
 * rimasti vuoti anche quando arrivano dopo, in scroll infinito.
 *
 * Il codice e' costruito a partire dalle regole caricate, non e' fisso.
 */
```


**riga 38:**
```
/* head puo' non esistere ancora al momento dell'iniezione */
```


**riga 47:**
```
/* Richiude i contenitori rimasti vuoti perche' la risorsa e' stata bloccata:
             solo elementi senza testo e senza figli visibili, per non toccare il sito. */
```


**riga 62:**
```
/*
           * v0.4.5: la ripulitura serve a chiudere i buchi lasciati dagli slot
           * bloccati, e quei buchi compaiono nei primi istanti. Dopo qualche
           * passata l'osservatore si stacca: continuare a girare a ogni modifica
           * del DOM rallentava l'apertura dei pannelli sulle schede annuncio.
           */
```


**riga 78:**
```
/* v0.5.1 — sulla scheda di un singolo annuncio non c'e' nulla da
             richiudere: il foglio di stile basta, l'osservatore no. */
```


### ApiLog.kt


**riga 3:**
```
/*
 * Subito+ — campionatore delle risposte API
 * v0.3.1 (2026-08-30) — nuovo, prepara la ricerca unificata
 *
 * PERCHE' SERVE
 * Fino alla v0.3.0 la sonda vedeva solo gli URL richiesti: bastava per bloccare.
 * Per costruire una lista di risultati nostra, che unisca Subito e Vinted, serve
 * sapere COSA rispondono quelle API: nomi dei campi, forma della paginazione,
 * dove stanno titolo, prezzo, foto, luogo, data.
 * shouldInterceptRequest non vede il corpo della risposta, quindi il campione
 * viene raccolto dentro la pagina (vedi ScriptApi) e consegnato qui.
 *
 * Si tiene UN campione per endpoint (host + percorso, query esclusa), troncato:
 * serve la forma, non i dati.
 */
```


**riga 42:**
```
/*
     * v1.0.6 (2026-08-30) — si registra anche il metodo e il corpo della RICHIESTA.
     * Serve per le offerte: quando ne fai una, l'importo viaggia nel corpo di una
     * POST, e senza quello si vedrebbe solo che una richiesta e' partita, non
     * quale campo porta la cifra. Costa nulla in piu' in riconoscibilita': la
     * sostituzione di fetch e XHR c'e' gia', ed e' accesa solo in modalita' sonda.
     *
     * --- versione precedente (v0.3.1, 2026-08-30) ---
     * fun registra(url: String, stato: Int, corpo: String)
     * --- fine versione precedente ---
     */
```


### Backup.kt


**riga 3:**
```
/*
 * Subito+ — copia di sicurezza
 * v1.0.0 (2026-08-30) — Fase 7
 *
 * Un solo file con tutto cio' che l'app ha imparato dall'uso e che non si puo'
 * ricostruire: gli annunci scartati, le ricerche salvate con la memoria di cosa
 * hanno gia' mostrato, e le regole di blocco se sono state personalizzate.
 *
 * Non contiene sessioni ne' credenziali: quelle restano nei cookie del sistema
 * e non escono dall'app. Un backup e' quindi un file innocuo da conservare
 * dove si vuole.
 */
```


**riga 37:**
```
/** @return descrizione di cosa e' stato ripristinato, o null se il file non e' un backup. */
```


### Tema.kt


**riga 3:**
```
/*
 * Subito+ — fondamenta grafiche
 * v0.5.0 (2026-08-30) — Fase 4, punto 1
 *
 * Una palette propria, coerente in chiaro e in scuro, piu' il colore dinamico
 * su Android 12 e successivi: li' l'app prende i toni dallo sfondo del telefono
 * e sembra parte del sistema invece di un'app capitata li' per caso.
 *
 * I colori NON imitano quelli di Subito: l'app e' una cosa diversa e deve
 * sembrarlo. Verde-ardesia per le superfici, ocra come accento.
 */
```


### Zoom.kt


**riga 3:**
```
/*
 * Subito+ — sblocco dello zoom nelle pagine
 * v1.0.2 (2026-08-30)
 *
 * PERCHE' LE FOTO SI APRONO PICCOLE
 * Non e' una questione di risoluzione: e' che non si possono ingrandire.
 * Le versioni mobili dei due siti dichiarano un viewport con
 * "user-scalable=no", e il WebView lo rispetta: le impostazioni dell'app che
 * abilitano lo zoom (setSupportZoom, builtInZoomControls) vengono scavalcate
 * dalla pagina. Aprendo la galleria di un annuncio la foto entra nello schermo
 * e li' resta, esattamente com'era prima.
 *
 * Qui il viewport viene riscritto per consentire l'ingrandimento fino a cinque
 * volte. Si insiste per una decina di secondi perche' le pagine a schede lo
 * riscrivono quando aprono la galleria, poi si smette: la lezione della v0.4.5
 * vale anche qui, si lavora dove serve e ci si ferma appena finito.
 *
 * Nota sintassi: niente template literal e niente simbolo dollaro nel JS.
 */
```


### DialogoRicerche.kt


**riga 3:**
```
/*
 * Subito+ — pannello delle ricerche salvate
 * v0.6.0 (2026-08-30) — Fase 5
 *
 * E' un dialogo e non un foglio dal basso per una ragione pratica: la barra dei
 * comandi vive in una ComposeView alta poche decine di pixel, e un foglio dal
 * basso resterebbe confinato li' dentro. Un dialogo apre invece una finestra
 * propria e copre lo schermo comunque.
 */
```
