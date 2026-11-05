// Subito+ — modulo app
// v0.1.0 (2026-08-30) — Fase 0 "Sonda": nessuna dipendenza esterna, solo framework Android.
//   Scelta voluta: la sonda deve compilare e installarsi ovunque, senza librerie che
//   possano introdurre variabili nel comportamento del WebView che stiamo misurando.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

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
android {
    namespace = "it.monkey.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.monkey.app"
        minSdk = 29            // Android 10: richiesto da MediaStore.Downloads usato per l'export
        targetSdk = 34         // dispositivo di riferimento: Android 13 (API 33)
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
        /*
         * v1.33.0 (2026-09-02) — CORREZIONE minore, un irrobustimento non la
         * caccia a un bug ancora vivo: rileggendo la guardia anti-doppio-
         * avvio della v1.31.0 (Controllore.kt) per rispondere all'utente che
         * chiedeva conferma sul log gia' mandato, si e' visto che
         * "if (inCorso) return; inCorso = true" e' un controllo-poi-scrivi in
         * DUE passi, non atomico: una finestra in teoria apribile (minuscola,
         * poche istruzioni, non secondi) fra i due Controllore.doWork() che
         * la guardia dovrebbe proprio impedire di accavallarsi. Sostituito
         * con AtomicBoolean.compareAndSet(false, true), che fa la stessa cosa
         * in un'unica operazione garantita dalla CPU. Il log gia' analizzato
         * (controllo-log.txt fino al 2026-09-02 00:30) non mostra nessun
         * doppio avvio dopo l'installazione della v1.31.0: questa non e' la
         * correzione di un problema visto, e' la stessa garanzia resa vera
         * invece che "quasi sempre vera".
         *
         * --- versione precedente ---
         * versionCode = 56
         * versionName = "1.32.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.34.0 (2026-09-02) — due correzioni sulla vista annunci, entrambe
         * segnalate dall'utente.
         *
         * 1. "in visualizzazione estesa i due pulsanti per preferiti e per
         *    nascondi sono sovrapposti": IconButton di Material3 impone
         *    un'area minima di tocco di 48dp anche quando gli si chiede una
         *    dimensione piu' piccola (qui 34dp) — l'area toccabile/dipinta
         *    risultava piu' larga della "fetta" di Row riservata dal layout
         *    in base ai 34dp dichiarati, e i due cerchi finivano per
         *    invadersi a vicenda. Sostituiti con un Box (clip circolare +
         *    sfondo + clickable) in SchermataRicerca.RigaAnnuncio: la
         *    dimensione dichiarata e' ora anche quella vera.
         *
         * 2. Nuovo: scorrere un annuncio da destra verso sinistra lo
         *    nasconde, sia in modalita' compatta che estesa, con un
         *    "pulsante di avviso" (sfondo colorato + icona + scritta
         *    "Nascondi l'annuncio") che compare durante lo scorrimento.
         *    Usa SwipeToDismissBox di Material3 (gia' nel compose-bom
         *    dell'app, nessuna libreria in piu'), e passa per la STESSA
         *    funzione "nascondi" gia' usata dalla ✕ — un solo percorso che
         *    nasconde davvero l'annuncio e offre "Annulla" nella snackbar,
         *    non due da tenere allineati.
         *
         * --- versione precedente ---
         * versionCode = 57
         * versionName = "1.33.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.35.0 (2026-09-02) — auto-aggiornamento: l'app non passa dal Play
         * Store (e' personale, passata agli amici a mano), quindi controlla
         * da sola se esiste una versione piu' nuova. Deposito scelto: GitHub
         * Releases del repository (github.com/chicco83/Monkey) — pubblico,
         * senza account ne' token per leggerlo. Vedi Aggiornamenti.kt per la
         * logica intera (confronto versioni, download con DownloadManager di
         * sistema, installazione), DialogoAggiornamento.kt per l'avviso che
         * compare da solo all'avvio quando c'e' qualcosa di nuovo, e la voce
         * "Cerca aggiornamenti" nel pannello Opzioni (DialogoOpzioni.kt) per
         * il controllo manuale.
         *
         * IMPORTANTE per chi consegna una versione da qui in avanti: il
         * controllo legge SOLO le release pubblicate su GitHub, non
         * versionCode/versionName da soli — se non si crea anche la release
         * (tag "v" + questo stesso versionName, APK firmato allegato), il
         * controllo non trova nulla di nuovo e non e' un errore, e' la
         * release mancante.
         *
         * --- versione precedente ---
         * versionCode = 58
         * versionName = "1.34.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.36.0 (2026-09-04) — quattro ritocchi all'interfaccia di ricerca,
         * tutti chiesti dall'utente:
         *
         * 1. La lente nel campo di ricerca era solo decorativa (nessun
         *    onClick): "non ha una funzione chiara". Ora apre le ricerche
         *    salvate, stessa azione della campanella in barra.
         * 2. Tolta la voce "Ricerche salvate" dal menu ⋮ (era doppia con la
         *    campanella e, da questa versione, anche con la lente).
         * 3. Tolta "Vista compatta" dal menu ⋮; al suo posto una nuova icona
         *    sotto il campo di ricerca in SchermataRicerca.kt — sempre
         *    visibile, non serve piu' aprire il menu per cambiarla.
         * 4. Le ricerche recenti sotto il campo di ricerca si cancellano una
         *    per una tenendoci premuto sopra (StatoRicerca.dimenticaRecente).
         *
         * Piu' un quinto pezzo, pensato per restare: un changelog per
         * versione (Changelog.kt), mostrato una volta sola in una finestra
         * flottante sopra la ricerca al primo avvio dopo l'installazione
         * (DialogoNovita.kt) — comincia da questa versione, non e' stato
         * ricostruito per quelle precedenti.
         *
         * --- versione precedente ---
         * versionCode = 59
         * versionName = "1.35.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.37.0 (2026-09-04) — cinque correzioni, tutte segnalate
         * dall'utente dopo aver provato la v1.36.0:
         *
         * 1. Il controllo delle ricerche salvate (automatico e "Controlla
         *    ora") non trovava mai nulla. Sospetto corretto (da confermare
         *    sul campo — vedi il commento in Controllore.doWork()):
         *    MotoreRicerca in background non ha mai avuto un genitore vero
         *    per i suoi WebView, nemmeno invisibile, fin dalla primissima
         *    versione di questa funzione (v0.6.0) — solo la ricerca in primo
         *    piano (MainActivity) ne passava uno. Se il log
         *    (controllo-log.txt, "Esporta diagnostica") continua a mostrare
         *    zero annunci trovati dopo questa versione, la causa e' un'altra
         *    e serve quel file per continuare a cercarla.
         * 2. Aprire una ricerca salvata mentre si scorreva un'altra lista non
         *    riportava lo scorrimento in cima: annunci vecchi e nuovi
         *    comparivano mescolati.
         * 3. La campanella delle ricerche salvate ora ha una piccola lente
         *    sovrapposta: da sola non richiamava l'idea di "ricerche".
         * 4. Tolta l'icona lente accanto alla campanella (passava fra
         *    ricerca e navigazione del sito, funzione poco chiara).
         * 5. L'icona vista compatta/estesa si e' spostata sotto le ricerche
         *    recenti (prima stava sopra).
         *
         * --- versione precedente ---
         * versionCode = 60
         * versionName = "1.36.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.38.0 (2026-09-04) — due correzioni, dopo che l'utente ha
         * provato la v1.37.0 e segnalato che il punto 1 di quella versione
         * NON era risolto:
         *
         * 1. Controllo automatico/"Controlla ora" delle ricerche salvate:
         *    la data "controllata X fa" non si aggiornava mai. Il sospetto
         *    della v1.37.0 (WebView senza un genitore vero) era corretto ma
         *    NON bastava da solo. Causa vera trovata in Controllore.doWork():
         *    solo la COSTRUZIONE di MotoreRicerca girava su
         *    withContext(Dispatchers.Main) — appena quel blocco finiva, il
         *    codice tornava sul thread in background del WorkManager, e li'
         *    giravano le vere chiamate al WebView (motore.cerca() -> avvia()
         *    -> sessione(f): costruzione del WebView, loadUrl,
         *    evaluateJavascript). Le API di WebView si possono usare SOLO dal
         *    thread principale: chiamarle da un altro thread lancia
         *    un'eccezione che WorkManager, girando in background, non mostra
         *    mai all'utente — da qui il fallimento silenzioso, senza un solo
         *    rigo di log per ricerca e senza mai un "ultimoControllo"
         *    aggiornato. Corretto racchiudendo TUTTO il ciclo di ricerca (non
         *    solo la costruzione di MotoreRicerca) in un unico
         *    withContext(Dispatchers.Main). Resta da confermare sul campo:
         *    se il problema persiste anche dopo questa versione, serve il
         *    file controllo-log.txt (Opzioni -> Esporta diagnostica, appena
         *    corretto anche il punto 2 qui sotto).
         * 2. "Esporta diagnostica" e "Azzera contatori" sembravano spariti
         *    dal pannello Opzioni. Il codice c'era sempre: il tetto
         *    d'altezza della lista (fissato a 280.dp in v1.26.0, quando le
         *    righe erano sette) e' rimasto invariato anche dopo che la
         *    v1.35.0 ha aggiunto una ottava riga ("Cerca aggiornamenti"),
         *    tagliando fuori dall'area visibile le ultime due voci. Alzato a
         *    400.dp (vedi DialogoOpzioni.kt).
         *
         * --- versione precedente ---
         * versionCode = 61
         * versionName = "1.37.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.39.0 (2026-09-05) — CONFERMATO SUL CAMPO: il fix della v1.38.0
         * al controllo delle ricerche salvate funziona, le notifiche ora
         * arrivano davvero (prima non erano MAI arrivate, da nessuna
         * versione). Con le notifiche finalmente funzionanti e' emerso un
         * secondo dettaglio: toccandole si apre la ricerca ma con tutti gli
         * annunci mescolati, mentre l'utente si aspetta di vedere gia' solo
         * i nuovi (e' proprio quello di cui parla l'avviso). Corretto in
         * MainActivity.gestisciIntent(): si attiva "soloNuovi" quando la
         * ricerca nasce da una notifica.
         *
         * --- versione precedente ---
         * versionCode = 62
         * versionName = "1.38.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.40.0 (2026-09-05) — il testo dell'annuncio su Vinted, secondo
         * tentativo (il primo, v1.7.0, si era fermato al primo ostacolo).
         *
         * Dalla v1.7.0 la vista estesa mostra il testo dell'annuncio accanto
         * ai dati, ma solo su Subito: li' arriva gia' con i risultati (campo
         * "body"), su Vinted il catalogo non lo espone — verificato con una
         * sonda vera nella v1.1.0, l'elenco completo dei campi di un articolo
         * e' riportato in DescrizioniVinted.kt e il testo non c'e' in nessuna
         * forma. Allora ci si era fermati li' ("Vinted nel catalogo non lo
         * espone: li' resta vuoto e la colonna sparisce", Annuncio.kt).
         *
         * Il dato pero' esiste, solo su un'altra risposta: una chiamata per
         * articolo, GET /api/v2/items/<id>. E' la stessa situazione gia'
         * risolta per il luogo del venditore nella v1.2.0 (il catalogo non lo
         * porta, il profilo si'), quindi si riusa quell'architettura invece di
         * inventarne un'altra: coda, poche richieste per volta, pausa fra
         * l'una e l'altra, cache su file (DescrizioniVinted.kt, gemello di
         * LuoghiVinted.kt).
         *
         * Con una differenza importante nel dosaggio: un venditore lo si
         * reincontra e la sua riga in cache si riusa, una descrizione no —
         * vale per quel solo annuncio. Chiederle tutte in anticipo sarebbe
         * trenta richieste a perdere per pagina, cioe' la firma di un
         * programma (vedi "COME NON SEMBRARE UN BOT", Controllore.kt). Quindi
         * il testo si chiede SOLO per gli annunci che finiscono davvero sotto
         * gli occhi, e solo in vista estesa — l'unica in cui si vede: quattro
         * o cinque righe per schermata, altre poche mentre si scorre, una
         * richiesta per volta.
         *
         * DA CONFERMARE SUL CAMPO: nessuna sonda ha mai catturato QUESTA
         * chiamata, quindi il nome del campo nella risposta ("description") e'
         * l'unico pezzo non verificato. Non si indovina e basta: si prova una
         * lista di nomi plausibili (come per la data, CAMPI_DATA in Fonti.kt)
         * e, se nessuno porta testo, la prima risposta illeggibile finisce
         * nella diagnostica con chiavi vere e un pezzo di corpo. Se la colonna
         * resta vuota bastera' "Esporta diagnostica" — stessa strada che ha
         * chiuso il controllo automatico nella v1.38.0.
         *
         * --- versione precedente ---
         * versionCode = 63
         * versionName = "1.39.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.41.0 (2026-09-08) — tre cose, tutte intorno allo scorrimento
         * della ricerca:
         *
         * 1. CORREZIONE al rimescolamento: scorrendo verso il fondo e
         *    tornando su comparivano annunci nuovi che prima non c'erano.
         *    Causa vera: Subito e Vinted vengono paginati indipendentemente
         *    con lo stesso numero di pagina condiviso, ma non hanno la
         *    stessa densita' di annunci — la pagina N dell'una e la pagina
         *    N dell'altra non coprono lo stesso intervallo di giorni.
         *    "risultati" (StatoRicerca.kt) riordinava tutto per data ogni
         *    volta che qualcosa arrivava: un annuncio dell'una piu' recente
         *    di quelli gia' visibili dell'altra si infilava in cima,
         *    spostando in basso cio' che si era gia' visto. Ora la lista
         *    non mostra mai un annuncio piu' vecchio del punto fino a cui
         *    ENTRAMBE le fonti attive sono davvero arrivate (vedi il
         *    commento su "confineFonte" in StatoRicerca.kt): si avanza
         *    sempre in fondo, mai in mezzo a cio' che si e' gia' visto.
         * 2. Annunci per pagina raddoppiati, 30 -> 60 (ANNUNCI_PER_PAGINA
         *    in Fonti.kt): meno pagine per coprire la stessa ricerca vuol
         *    dire anche meno occasioni per il disallineamento del punto 1.
         * 3. Il filtro "entro 60 giorni" parte gia' attivo (prima: nessun
         *    limite): chi cerca vuole quasi sempre le novita', e tiene
         *    anche sotto controllo quante pagine servono a riempire la
         *    lista. Resta disattivabile come sempre dal foglio filtri.
         *
         * Lo spinner di caricamento (in fondo alla lista durante lo
         * scorrimento, a schermo intero alla primissima ricerca) c'era gia'
         * da prima e resta invariato: con la correzione del punto 1 resta
         * visibile per l'intero giro (finche' entrambe le fonti non hanno
         * risposto), invece di sparire e ricomparire a meta'.
         *
         * --- versione precedente ---
         * versionCode = 64
         * versionName = "1.40.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.42.0 (2026-09-08) — SOLO diagnostica, nessun cambiamento visibile.
         *
         * La sonda mandata dall'utente ha chiuso la domanda della v1.40.0:
         * "GET /api/v2/items/<id>" non e' una rotta vera (404 con la pagina
         * generica dell'app, non un 404 JSON), e con "Modalita' sonda" attiva
         * su una scheda Vinted vera, il campionatore di fetch/XHR non ha mai
         * visto nessuna chiamata per il dettaglio — il testo dell'annuncio
         * non arriva mai con una richiesta separata, e' gia' dentro l'HTML
         * quando la pagina arriva. La richiesta indovinata e' sospesa
         * (DescrizioniVinted.richiedi(), vedi il commento li'): insistere
         * userebbe solo richieste a vuoto per un endpoint che non esiste.
         *
         * Al suo posto una sonda nuova (DescrizioniVinted.scriptSonda,
         * agganciata in MainActivity accanto alla sonda DOM di sempre):
         * attiva solo con "Modalita' sonda" e solo sulle pagine /items/...,
         * cerca fra TUTTI gli <script> della pagina quello che contiene
         * '"description"' e ne manda un pezzo nello stesso canale delle
         * chiamate campionate (api.json, "Esporta diagnostica") — cosi' la
         * prossima diagnostica dira' davvero dove sta il testo, invece di
         * un altro tentativo indovinato.
         *
         * --- versione precedente ---
         * versionCode = 65
         * versionName = "1.41.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.43.0 (2026-09-08) — chiude la caccia al testo dell'annuncio
         * Vinted (v1.40.0 -> tentativo sbagliato -> v1.42.0 diagnostica ->
         * questa). La sonda della v1.42.0 ha trovato dove sta davvero: non
         * arriva da nessun endpoint dell'API, e' gia' dentro l'HTML della
         * pagina annuncio, in un blocco JSON-LD standard (schema.org
         * "Product", campo "description") — la stessa marcatura SEO che
         * quasi ogni sito di e-commerce mette sulla scheda di un prodotto.
         * Vedi il commento in cima a DescrizioniVinted.kt per l'esempio vero
         * catturato dalla sonda e per i dettagli della nuova lettura.
         *
         * Riattivata DescrizioniVinted.richiedi(), sospesa dalla v1.42.0:
         * ora scarica la pagina vera dell'annuncio (Annuncio.url, gia'
         * salvato per ogni risultato) invece del vecchio "/api/v2/items/
         * <id>" (mai stata una rotta vera). Stessa coda/cache/dosaggio di
         * sempre — una richiesta alla volta, solo per gli annunci che si
         * stanno davvero guardando in vista estesa — cambia solo come si
         * arriva al testo.
         *
         * --- versione precedente ---
         * versionCode = 66
         * versionName = "1.42.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.44.0 (2026-09-08) — CORREZIONE: "errore 500 da vinted",
         * segnalato subito dopo la v1.43.0. Diagnostica dell'utente:
         *
         *   [subito] ...&lim=60&start=0 -> 60 annunci (ok)
         *   [vinted] /api/v2/catalog/items?...&per_page=60&page=1 ->
         *            stato 500 — {"code":105,"message":"Errore del
         *            server","message_code":"server_error"}
         *
         * Non era un effetto della descrizione (v1.43.0): e' la ricerca
         * stessa. La v1.41.0 aveva raddoppiato ANNUNCI_PER_PAGINA a 60 per
         * ENTRAMBE le fonti, segnando pero' esplicitamente che nessuna
         * sonda aveva mai confermato che Vinted reggesse un valore diverso
         * da 30 — ora si sa che non lo regge, risponde con un errore
         * server vero, non un rifiuto pulito. Vedi il commento su
         * ANNUNCI_PER_PAGINA_VINTED in Fonti.kt: Subito resta a 60
         * (confermato buono dalla stessa diagnostica), Vinted torna a 30.
         *
         * --- versione precedente ---
         * versionCode = 67
         * versionName = "1.43.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.45.0 (2026-09-08) — CORREZIONE: "la pagina dei preferiti da'
         * errore 403 per Vinted" (Vinted era tornato su dopo il KO della
         * v1.44.0, non c'entra con quello).
         *
         * FonteVinted.preferitiElenco() non ha mai portato le intestazioni
         * anti-CSRF (csrf=true) — la stessa cosa gia' vista e risolta per
         * il singolo preferito (toggle(), v1.6.0-v1.16.0). Applicato lo
         * stesso rimedio confermato la', invece di indovinarne un altro.
         * Aggiunta anche una riga in diagnostica per questo caso, che fin
         * qui non ne lasciava nessuna: se il rimedio non bastasse, la
         * prossima "Esporta diagnostica" dira' subito il resto. Vedi i
         * commenti su Fonti.kt (preferitiElenco) e StatoRicerca.kt
         * (apriPreferiti).
         *
         * --- versione precedente ---
         * versionCode = 68
         * versionName = "1.44.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.46.0 (2026-09-08) — due cose dallo stesso messaggio, dopo che
         * l'utente ha chiarito la vera causa del 403 sui preferiti Vinted
         * (v1.45.0 l'aveva solo attenuato: le intestazioni anti-CSRF servivano
         * comunque, ma non bastavano se la sessione era proprio sloggata):
         *
         * 1. Un vero controllo del login quando una chiamata Vinted che lo
         *    richiede fallisce (apriPreferiti): si verifica per davvero con
         *    /api/v2/users/current (StatoRicerca.controllaLoginVinted) invece
         *    di indovinare dal solo stato HTTP, e se risulta che non sei piu'
         *    collegato il messaggio d'errore lo dice chiaramente, con le
         *    istruzioni per rientrare, invece di un codice tecnico.
         * 2. Il banner "tocca ⋮ e poi Sito per accedere" (v1.15.0), facile da
         *    scorrere via senza completare il login, diventa un dialogo
         *    modale (DialogoGuidaLogin.kt) mostrato una volta sola al primo
         *    avvio in assoluto: spiega dove si accede E cosa fare se piu'
         *    avanti un errore segnala che sei uscito.
         *
         * --- versione precedente ---
         * versionCode = 69
         * versionName = "1.45.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.47.0 (2026-09-08) — CORREZIONE: "per gli annunci stranieri
         * compare il testo originale, mentre aprendolo si attiva il
         * traduttore" — segnalato dall'utente. La v1.43.0 leggeva il testo
         * da un blocco JSON-LD nell'HTML della pagina, sempre quello
         * ORIGINALE. Una nuova sonda su un annuncio con traduzione attiva ha
         * trovato l'indirizzo vero che la scheda chiama per il testo della
         * barra laterale:
         *
         *   GET https://api.vinted.it/item-details/plugins/<id>/translatable
         *       ?localize=false
         *   -> {"plugins":[...,{"name":"description","data":{
         *         "description":"<gia' tradotto, se serve>",
         *         "is_localized":true,"localization":"manual",...}}]}
         *
         * Vedi il commento in cima a DescrizioniVinted.kt per l'esempio
         * completo e per il perche' un host diverso (api.vinted.it) non e'
         * un problema. Da questa versione l'app chiama questo endpoint al
         * posto di scaricare la pagina intera: piu' semplice (JSON pulito,
         * non piu' html-scraping) e finalmente corretto per gli annunci in
         * lingua straniera.
         *
         * --- versione precedente ---
         * versionCode = 70
         * versionName = "1.46.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.48.0 (2026-09-08) — CORREZIONE: "la descrizione compare sempre
         * in lingua originale", anche dopo la v1.47.0. La chiamata nuova era
         * giusta: il bug vero era la CACHE su file (descrizioni_vinted.json),
         * mai invalidata al cambio di meccanismo — un annuncio gia' visto
         * PRIMA della v1.47.0 restava con il testo originale scritto per
         * sempre, e non veniva mai richiesto di nuovo. Vedi il commento su
         * CACHE_VERSIONE in DescrizioniVinted.kt per il rimedio (un confronto
         * di versione sulla cache, un solo "colpo a vuoto" per annuncio gia'
         * visto).
         *
         * --- versione precedente ---
         * versionCode = 71
         * versionName = "1.47.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.49.0 (2026-09-09) — CORREZIONE: dopo la v1.48.0 il problema
         * segnalato ("vedo ancora i testi nella lingua originale") non e'
         * andato via, e quattro diagnostiche di fila (compresa una dopo
         * disinstallazione completa, per escludere la cache) non hanno mai
         * mostrato la chiamata di traduzione — ne' riuscita ne' fallita.
         * Causa: DescrizioniVinted.segnala() scriveva nel log
         * ("ricerca-diagnostica.txt", uno degli otto file di "Esporta
         * diagnostica" — l'altro, "api.json", e' la sonda e vede solo il
         * traffico della pagina aperta nel tab "Sito", non le chiamate di
         * sfondo di MotoreRicerca) SOLO quando la chiamata falliva. Se
         * rispondeva 200 con un testo semplicemente SBAGLIATO (non tradotto)
         * — l'ipotesi rimasta in piedi — non ne restava traccia da nessuna
         * parte. Ora segnala() scrive una riga la prima volta che risponde,
         * bene o male che vada: vedi il commento in cima a
         * DescrizioniVinted.kt, "IL BUCO DIAGNOSTICO...". Nessun'altra
         * modifica: e' un cambio solo diagnostico, per vedere finalmente
         * cosa risponde davvero l'endpoint in questo caso.
         *
         * --- versione precedente ---
         * versionCode = 72
         * versionName = "1.48.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.50.0 (2026-09-09) — CORREZIONE, con la prova in mano: la riga
         * aperta dalla v1.49.0 ha mostrato la risposta vera della chiamata
         * di traduzione su un annuncio in tedesco — stato 200, testo
         * estratto regolare, ma in lingua ORIGINALE, non tradotta. La
         * chiamata quindi non falliva mai: rispondeva "giusta" con la
         * lingua sbagliata. Causa identica, con la stessa prova, a quella
         * gia' risolta per il cuore su Vinted (v1.26.0): la sessione
         * nascosta di MotoreRicerca resta ferma sulla home di vinted.it per
         * tutta la vita del processo, non naviga mai davvero alla pagina
         * dell'annuncio — mentre la sonda che aveva scoperto l'endpoint
         * (v1.47.0) l'aveva catturato mentre lo chiamava un browser vero
         * GIA' SULLA PAGINA di quell'annuncio. Rimedio: come per il cuore,
         * la sessione ci naviga davvero prima di chiedere la traduzione
         * (Chiamata.paginaPrima). Vedi il 4° tentativo in cima a
         * DescrizioniVinted.kt per i dettagli.
         *
         * --- versione precedente ---
         * versionCode = 73
         * versionName = "1.49.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.51.0 (2026-09-09) — REGRESSIONE, RIPORTATA SUBITO DALL'UTENTE:
         * "il testo non compare più in nessuna lingua". La v1.50.0 aveva
         * fatto navigare la sessione nascosta di Vinted (MotoreRicerca)
         * sulla pagina vera dell'annuncio prima di ogni chiamata di
         * traduzione — corretto in teoria per lo stesso motivo gia' visto
         * col cuore (v1.26.0), ma quella sessione e' CONDIVISA con la
         * ricerca ancora in corso mentre si scorre: navigarla ripetutamente
         * mentre il catalogo prova a caricare la pagina successiva ha reso
         * la coda delle descrizioni inaffidabile al punto da non concludersi
         * quasi mai — sparito il testo, non solo la traduzione. Si torna al
         * comportamento della v1.49.0 (nessuna navigazione): testo
         * originale, non tradotto, ma affidabile. Il problema della
         * traduzione resta aperto, da riprendere con una sessione dedicata
         * invece di quella condivisa con la ricerca — vedi il commento in
         * cima a DescrizioniVinted.kt, 5° tentativo.
         *
         * --- versione precedente ---
         * versionCode = 74
         * versionName = "1.50.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.52.0 (2026-09-15) — SOLO DIAGNOSTICA: l'utente ha segnalato che
         * la ricerca Vinted da' errore 404. Una diagnostica ha confermato
         * che "/api/v2/catalog/items" (usato dalla v0.4.0) non risponde
         * piu' con JSON ma con la pagina generica "non trovato" dell'app —
         * la rotta e' sparita, non e' un guasto temporaneo. Una sonda sulla
         * pagina di ricerca vera di Vinted non ha mai mostrato nessuna
         * chiamata fetch/XHR che porti l'elenco annunci: solo suggerimenti,
         * ricerche recenti, "promoted_closets", banner. Ipotesi, sulla
         * scorta di cosa e' gia' successo per la descrizione (v1.43.0):
         * l'elenco ora arriva dentro l'HTML della pagina (reso lato
         * server), non con una chiamata a parte che la sonda fetch/XHR
         * possa vedere. Nuova sonda mirata (SondaCatalogoVinted.kt) per
         * confermarlo e trovare dove: nessun cambiamento visibile finche'
         * non si legge la sua risposta.
         *
         * --- versione precedente ---
         * versionCode = 75
         * versionName = "1.51.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.53.0 (2026-09-15) — SOLO DIAGNOSTICA, seguito diretto della
         * v1.52.0: la prima risposta della sonda ha mostrato due cose. Primo,
         * "__NEXT_DATA__" e' confermato assente — Vinted usa l'App Router di
         * Next.js, non piu' il vecchio Pages Router. Secondo, il marcatore
         * "favourite_count" (v1.52.0) ha dato un FALSO POSITIVO: ha trovato
         * uno script col dizionario di traduzione dell'interfaccia, dentro
         * una chiave come "item.favourite_count.few", non un campo di un
         * annuncio vero — e la sonda si fermava li' al primo trovato, senza
         * controllare il resto della pagina. Vedi il commento in cima a
         * SondaCatalogoVinted.kt per il dettaglio completo.
         *
         * Corretto nella sonda (non nel comportamento dell'app, che resta
         * invariato): marcatori piu' precisi (con virgolette e due punti,
         * cosi' non combaciano piu' dentro una chiave di traduzione), due
         * marcatori nuovi altrettanto noti dal vecchio elenco JSON, e prima
         * di tutto un controllo di "window.__next_f" — il buffer con cui
         * l'App Router trasmette i dati della pagina, lo stesso posto gia'
         * usato con successo in questo progetto per il token anti-CSRF (vedi
         * "csrfJs" in MotoreRicerca.kt). La ricerca non si ferma piu' al
         * primo marcatore trovato: scorre tutto e riporta ogni corrispondenza.
         *
         * --- versione precedente ---
         * versionCode = 76
         * versionName = "1.52.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.54.0 (2026-09-16) — SOLO DIAGNOSTICA, svolta nella caccia
         * all'elenco Vinted: la sonda della v1.53.0 ha chiuso la pista JSON
         * (window.__next_f lungo zero, __NEXT_DATA__ assente, nessun
         * marcatore in 64 script, nessuna chiamata fetch/XHR sospetta), e
         * una sonda diversa gia' nel progetto (DomProbe, quella delle card
         * Subito) ha mostrato che la pagina contiene davvero solo 2 annunci
         * come link HTML veri ("/items/<id>-..."), ma il suo test (un solo
         * <a> con dentro sia immagine che prezzo) non li riconosce: la card
         * probabilmente ha due <a> separati (immagine, titolo) con il
         * prezzo scritto fuori da entrambi, nello stesso contenitore.
         *
         * Questa versione cambia strategia nella sonda (non nel comporta-
         * mento dell'app): invece di cercare JSON, risale dai link /items/
         * gia' noti buoni verso i genitori finche' non trova il contenitore
         * con immagine + prezzo insieme, e ne riporta la struttura e l'HTML
         * vero — cosi' si potra' scrivere un lettore basato sul DOM invece
         * che su una risposta JSON. Vedi il commento in cima a
         * SondaCatalogoVinted.kt per il dettaglio completo.
         *
         * --- versione precedente ---
         * versionCode = 77
         * versionName = "1.53.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.55.0 (2026-09-16) — SOLO DIAGNOSTICA, la risposta cercata da tre
         * versioni: la scansione del DOM della v1.54.0 ha trovato 96 annunci
         * sulla pagina e un contenitore chiaro per ciascuno —
         * "[data-testid='product-item-id-<id>']", nessuna risalita di
         * genitori necessaria. Titolo, marca, condizione, prezzo e (se
         * scontato) prezzo originale sono tutti nell'attributo "alt"
         * dell'immagine, ripetuti nel "title" del link. Vedi il commento in
         * cima a SondaCatalogoVinted.kt per l'esempio completo.
         *
         * Questa versione NON scrive ancora il lettore DOM vero: prima
         * serve vedere il formato dell'"alt" su piu' annunci (non solo il
         * primo) per essere sicuri che sia sempre lo stesso. La sonda ora
         * riporta un campione compatto per fino a 10 annunci invece
         * dell'albero dei genitori, ormai inutile, piu' l'HTML completo di
         * un solo contenitore. Alzato anche ApiLog.MAX_CORPO (6000 ->
         * 10000, vedi il commento li'): la risposta precedente era arrivata
         * troncata a meta' di un JSON.
         *
         * --- versione precedente ---
         * versionCode = 78
         * versionName = "1.54.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.56.0 (2026-09-16) — CORREZIONE: la ricerca Vinted torna a
         * funzionare, chiudendo il 404 segnalato dalla v1.52.0. Confermato
         * su dieci annunci diversi (v1.55.0) che ogni card ha un
         * contenitore "[data-testid='product-item-id-<id>']" e che
         * l'attributo "alt" della sua immagine porta sempre titolo, marca,
         * condizione e prezzo nello stesso formato: si scrive il lettore
         * vero, basato sul DOM invece che su una risposta JSON ormai
         * sparita per sempre. Vedi il commento in cima a FonteVinted.url()
         * (Fonti.kt) per la storia completa delle quattro sonde che ci
         * sono arrivate, e quello su avviaDom() (MotoreRicerca.kt) per
         * come la pagina viene letta — con una sessione DEDICATA, mai
         * condivisa con preferiti/traduzione, apposta per non ripetere la
         * regressione della v1.50.0/v1.51.0.
         *
         * Due limiti onesti, non ancora risolti: "venduto" non si riconosce
         * ancora in elenco (nessun annuncio venduto visto finora in una
         * card), e la paginazione oltre la prima pagina non e' stata
         * ancora confermata da nessuna sonda — se non funzionasse, la
         * lista smette di crescere un po' prima del dovuto, senza crash ne'
         * doppioni (vedi il commento su avviaDom()).
         *
         * --- versione precedente ---
         * versionCode = 79
         * versionName = "1.55.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.57.0 (2026-09-16) — due correzioni segnalate subito dopo la
         * v1.56.0:
         *
         * 1. "per gli annunci esteri non compare niente" (marca, condizione
         *    e prezzo mancanti): non era una questione di lingua/paese —
         *    l'attributo "alt" di un annuncio senza marca dichiarata non
         *    scrive affatto "Brand: …", e il lettore lo pretendeva sempre.
         *    Ora si prova prima il formato con marca, poi quello senza.
         * 2. "andando indietro nei giorni gli annunci Vinted non si vedono
         *    piu'": la stima di data (Vinted non ne ha una vera in questa
         *    pagina) ripartiva da "ora" a ogni pagina invece di continuare
         *    a scendere — ogni pagina restava "praticamente adesso" e non
         *    compariva mai scorrendo verso il passato insieme alle date
         *    vere di Subito. Corretto facendo viaggiare il numero di pagina
         *    insieme all'elenco, cosi' la stima scende con continuita'.
         *
         * Vedi i commenti in cima a FonteVinted (Fonti.kt) e su avviaDom()
         * (MotoreRicerca.kt) per i dettagli completi, compreso il limite
         * che il punto 2 NON risolve (nessuna data vera possibile senza che
         * Vinted la scriva da qualche parte).
         *
         * --- versione precedente ---
         * versionCode = 80
         * versionName = "1.56.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.58.0 (2026-09-24) — due segnalazioni con screenshot sulla
         * v1.56.0/1.57.0:
         *
         * 1. "molti annunci Vinted non mostrano il prezzo nell'anteprima":
         *    l'"alt" della card puo' contenere campi in piu' (es. "Taglia:
         *    …" su orologi e abbigliamento) che i modelli fissi v1.57.0 non
         *    prevedevano — l'annuncio finiva senza prezzo e con l'intero
         *    alt come titolo. Il lettore ora segue la struttura
         *    (titolo, "Etichetta: valore"…, prezzi in coda) invece di due
         *    modelli fissi.
         * 2. "anche se caricati ieri o giorni fa vengono sempre mostrati
         *    come caricati oggi": la stima ordinale partiva sempre da
         *    "adesso". Ora la data si stima dall'ID dell'articolo (crescente
         *    nel tempo su tutta Vinted), ancorato ogni 20 minuti all'annuncio
         *    piu' recente del catalogo — nuovo file StimaDataVinted.kt — e
         *    si mostra con "≈" davanti.
         *
         * --- versione precedente ---
         * versionCode = 81
         * versionName = "1.57.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.59.0 (2026-09-24) — l'utente ha mostrato che la data vera c'e':
         * nella scheda di ogni annuncio Vinted, campo "Caricato" ("un'ora
         * fa"). Non sta nella card dell'elenco ma nel dettaglio, che l'app
         * gia' scarica per la descrizione: ora da quella stessa risposta si
         * legge anche "Caricato" (nuovo DataCaricamentoVinted.kt) e la riga
         * mostra la data vera (senza "≈") appena arriva. Il dettaglio si
         * chiede ora anche in vista compatta. Le letture al minuto
         * diventano ancore per la stima dall'id (StimaDataVinted), che resta
         * per ordinare la lista e per le righe non ancora arrivate.
         *
         * --- versione precedente ---
         * versionCode = 82
         * versionName = "1.58.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.60.0 (2026-09-24) — la diagnostica della v1.59.0 ha confermato
         * che il prezzo ora si legge sempre (96 su 96, etichette Brand/
         * Condizioni/Taglia) e che l'ancora della stima di data funziona;
         * ma l'endpoint del dettaglio risponde 400 ANONYMOUS_VIEWER, quindi
         * niente "Caricato" (e niente descrizione). Ripiego sulla pagina
         * pubblica dell'annuncio, leggibile senza accesso: descrizione dal
         * JSON-LD (lettore v1.43.0 ripristinato) e "Caricato" dall'HTML
         * visibile (DataCaricamentoVinted.leggiHtml, v1.1.0).
         *
         * --- versione precedente ---
         * versionCode = 83
         * versionName = "1.59.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.61.0 (2026-09-24) — dopo la diagnostica v1.60.0 (pagina
         * dell'annuncio 2 015 274 caratteri; stima dall'id 3 h 20 min
         * indietro rispetto a "Caricato"):
         * 1. meno dati: la pagina si legge a pezzi e il download si
         *    interrompe appena ci sono "Caricato" e la descrizione
         *    (MotoreRicerca.paginaParziale);
         * 2. stima piu' precisa: anche le letture "N ore fa" diventano punti
         *    della curva id->data (StimaDataVinted.registraLettura, v1.1.0).
         *
         * --- versione precedente ---
         * versionCode = 84
         * versionName = "1.60.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.62.0 (2026-09-24) — diagnostica v1.61.0: lo scaricamento
         * parziale si fermava a 1,2 M caratteri, PRIMA del "Caricato"
         * visibile (che sta in fondo alla pagina, dopo il dizionario delle
         * traduzioni): data persa su quasi tutte le righe. Tetto portato a
         * 4 M (ci si ferma comunque appena trovato "Caricato"), e corretto il
         * lettore che scambiava la coda di uno script troncato per testo
         * visibile (DataCaricamentoVinted 1.2.0).
         *
         * --- versione precedente ---
         * versionCode = 85
         * versionName = "1.61.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.63.0 (2026-09-24) — l'utente ha scoperto che l'app aveva perso
         * il login senza mostrarlo. Nuovo controllo del login delle due
         * fonti (StatoRicerca.controllaAccessi) con avviso in home e tasto
         * "Accedi". Nessun ritorno a versioni precedenti: le modifiche
         * v1.56-v1.62 o non dipendono dal login (lettura prezzi, stima
         * date) o sono ripieghi che scattano solo quando una chiamata
         * fallisce. Una prova del vecchio catalogo JSON da collegati (solo
         * diagnostica) dira' se la ricerca Vinted puo' tornare a quella
         * strada.
         *
         * --- versione precedente ---
         * versionCode = 86
         * versionName = "1.62.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.64.0 (2026-09-28) — segnalazione con screenshot: in "Sito" le
         * foto di un annuncio Vinted si aprono in un riquadro piccolo e non
         * si scorrono. Nuova galleria dell'app a schermo intero (scorrimento,
         * pizzico/doppio tocco per ingrandire), aperta toccando le foto
         * dell'annuncio — GalleriaVinted.kt. In piu': il controllo del login
         * Vinted riprova una volta dopo 30 s se non ha avuto risposta
         * (diagnostica v1.63.0: "stato -3").
         *
         * --- versione precedente ---
         * versionCode = 87
         * versionName = "1.63.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.65.0 (2026-09-28) — diagnostica v1.64.0, da collegati:
         *  - il vecchio catalogo JSON Vinted risponde 404 anche da collegati:
         *    la ricerca dalla pagina (v1.56.0) resta, nessun ritorno indietro;
         *  - il dettaglio torna a rispondere 200 (descrizione di nuovo dalla
         *    via leggera, senza scaricare la pagina da 2 MB);
         *  - "Caricato" c'e', ma in inglese e in un'unica stringa: "Uploaded
         *    13 min ago". Ora si legge (DataCaricamentoVinted 1.3.0).
         *
         * --- versione precedente ---
         * versionCode = 88
         * versionName = "1.64.0"
         * --- fine versione precedente ---
         */
        /*
         * v1.66.0 (2026-10-03) — galleria foto Vinted: limite 20 foto
         * (GalleriaVinted 1.1.0).
         *
         * --- versione precedente (v1.65.0, 2026-10-02) ---
         * versionCode = 89
         * versionName = "1.65.0"
         * --- fine versione precedente ---
         */
        versionCode = 90
        versionName = "1.66.0"  // <-- aggiornare a ogni consegna
    }

    // Firma: keystore generato per il progetto, da conservare per tutti gli aggiornamenti futuri.
    signingConfigs {
        create("subitoplus") {
            storeFile = file("../keystore/subitoplus.jks")
            storePassword = "subitoplus"
            keyAlias = "subitoplus"
            keyPassword = "subitoplus"
        }
    }

    buildTypes {
        release {
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("subitoplus")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // v0.5.0 — Fase 4, punto 1: fondamenta grafiche
    // v1.6.0 — buildConfig serve per mostrare la versione nella barra:
    // il numero deve venire da una fonte sola, quella di questo file.
    buildFeatures { compose = true; buildConfig = true }
}

/*
 * v0.2.0 (2026-08-30) — prima dipendenza esterna del progetto.
 * androidx.webkit serve per addDocumentStartJavaScript: iniettare il CSS
 * PRIMA che la pagina esegua i suoi script, cosi' i banner non lampeggiano
 * e in Fase 2 le card scartate non vengono mai disegnate.
 */
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")

    /*
     * v0.5.0 — Compose e Material 3.
     * Il resto dell'interfaccia e' ancora a viste tradizionali e ci resta finche'
     * non viene riscritta (punti 2 e seguenti): Compose e le viste convivono
     * nella stessa schermata, e questo permette di procedere un pezzo per volta
     * senza mai avere una build inutilizzabile.
     */
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // v0.5.2 — caricamento immagini in Compose: cache, annullamento allo scorrimento,
    // priorita' a cio' che e' sullo schermo. Sostituisce il caricatore scritto a mano.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // v0.6.0 — controlli periodici delle ricerche salvate
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
