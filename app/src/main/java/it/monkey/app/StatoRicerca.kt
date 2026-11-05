package it.monkey.app

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

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class StatoRicerca(
    private val ctx: Context,
    private val motore: MotoreRicerca,
    /** Chiamata quando cambia il fatto che ci sia o meno un caricamento in corso. */
    private val caricamento: (Boolean) -> Unit
) {
    // ---- richiesta ----
    var query by mutableStateOf("")
    var ultimaQuery by mutableStateOf("")
        private set
    private var pagina = 1
    private var attesa = 0
    var inCorso by mutableStateOf(false)
        private set
    var primaRicercaFatta by mutableStateOf(false)
        private set
    /** Vero quando una pagina non ha portato nulla di nuovo: inutile chiederne altre. */
    var esaurito by mutableStateOf(false)
        private set
    private var dimensionePrima = 0
    /*
     * v1.41.0 (2026-09-08) — quanti annunci avevamo GIA' per ciascuna fonte
     * prima dell'ultima richiesta: serve, fonte per fonte, a capire se
     * l'ultima pagina ha portato qualcosa di nuovo o no (vedi
     * aggiornaConfini() piu' sotto — "dimensionePrima", qui sopra, fa la
     * stessa cosa ma sommando le due fonti insieme, e resta per "esaurito").
     */
    private val dimensionePrimaPerFonte = HashMap<String, Int>()

    /*
     * v1.41.0 (2026-09-08) — CORREZIONE al rimescolamento durante lo
     * scorrimento.
     *
     * IL BUG SEGNALATO: scorrendo verso il fondo per vedere annunci piu'
     * vecchi e poi tornando su, comparivano annunci NUOVI che prima non
     * c'erano. Non un errore di rete: un effetto della fusione fra due
     * fonti paginate INDIPENDENTEMENTE con lo stesso numero di pagina
     * condiviso (avvia()/altraPagina(), "pagina" unico per entrambe). Le
     * due piattaforme non hanno la stessa densita' di annunci per la
     * stessa ricerca: la pagina N di Subito e la pagina N di Vinted non
     * coprono lo stesso intervallo di giorni. "risultati" (piu' sotto)
     * riordina TUTTO cio' che e' in "grezzi" per data ogni volta che
     * qualcosa arriva: se una fonte e' rimasta piu' "vicina a oggi"
     * dell'altra, un annuncio appena arrivato da quella fonte puo' avere
     * una data piu' recente di annunci gia' visibili in cima — e li' si
     * infila, spostando in basso tutto il resto. Con uno scorrimento
     * ancorato all'indice (non all'annuncio), tornare su mostra posizioni
     * diverse da prima: sembra "sono comparsi annunci nuovi", anche se in
     * realta' sono solo stati inseriti piu' in alto di dove si guardava.
     *
     * IL RIMEDIO: non mostrare mai un annuncio piu' vecchio del punto fino
     * a cui ENTRAMBE (o tutte) le fonti attive hanno gia' risposto.
     * "confineFonte" tiene, per ciascuna fonte, la data del suo annuncio
     * piu' vecchio scaricato finora; "confineSicuro()" (vicino a
     * "risultati") prende la piu' recente delle due soglie — quella che
     * frena di piu', ed e' il vincolo vero — e la usa come taglio. Una
     * fonte esaurita (o disattivata dai filtri) non conta piu' come
     * vincolo: li' non manca piu' nulla da scoprire, o non la si guarda
     * proprio.
     *
     * Gli annunci scaricati ma non ancora "sicuri" restano in "grezzi" —
     * non si butta via nulla — semplicemente non compaiono finche' il giro
     * non e' completo per tutte le fonti attive: "aggiornaConfini()" (piu'
     * sotto) si chiama SOLO a giro finito (in finita(), quando "attesa"
     * torna a zero), mai al singolo arrivo di una fonte. Cosi' la lista
     * non trema a meta' giro: o e' ferma, o avanza tutta insieme quando e'
     * il momento giusto — e avanzare vuol dire sempre aggiungere in fondo,
     * mai inserire in mezzo a cio' che si e' gia' visto.
     */
    private val confineFonte = HashMap<String, Long>()
    private val esauritoFonte = HashMap<String, Boolean>()
    /*
     * v1.41.0 — vero appena una fonte ha risposto ALMENO una volta in questa
     * ricerca, anche se quella risposta non ha portato nessuna data valida
     * (dataMillis 0, capita per annunci senza data letta). Separata da
     * "confineFonte" apposta: se si usasse "confineFonte.containsKey(...)"
     * per decidere "questa fonte ha gia' risposto", una fonte che risponde
     * ma non porta mai una data utile bloccherebbe la lista PER SEMPRE
     * (vedi confineSicuro() — "nessuno ha ancora risposto" non finirebbe
     * mai). Qui "ha risposto" e "ha una soglia utile" sono due domande
     * diverse, e vanno tenute separate.
     */
    private val fonteRisposta = HashMap<String, Boolean>()

    // ---- risultati ----
    private val grezzi = mutableStateListOf<Annuncio>()
    val errori = mutableStateMapOf<String, String>()

    /*
     * v1.63.0 (2026-09-24) — STATO DEL LOGIN per fonte, verificato davvero.
     *
     * Segnalato dall'utente: l'app aveva perso il login (se n'e' accorto
     * solo aprendo "Sito") e non lo mostrava in alcun modo. Col senno di
     * poi e' coerente con la diagnostica: il dettaglio Vinted rispondeva
     * 400 {"code":"ANONYMOUS_VIEWER"} — "utente anonimo", cioe' non
     * collegato — e le ultime versioni (v1.60.0-v1.62.0) hanno costruito
     * un ripiego sulla pagina pubblica per un problema che forse era solo
     * questo. Da qui in avanti il login si controlla e si MOSTRA in home.
     *
     * true = collegato, false = NON collegato (verificato), assente = non
     * si sa (rete assente, risposta inattesa): in quel caso non si mostra
     * nulla, per non dare un allarme falso. Vedi controllaAccessi(), sotto.
     */
    val accessi = mutableStateMapOf<String, Boolean>()
    val conteggi = mutableStateMapOf<String, Int>()

    // ---- filtri ----
    var usaSubito by mutableStateOf(true)
    var usaVinted by mutableStateOf(true)
    var ordine by mutableStateOf(0)          // 0 recenti, 1 prezzo cresc., 2 prezzo decr.
    /*
     * v1.41.0 (2026-09-08) — "entro 60 giorni" gia' attivo di default,
     * invece di "qualsiasi": chi cerca vuole quasi sempre le novita', non
     * l'archivio intero, ed e' anche il filtro che tiene sotto controllo
     * quante pagine servono per riempire la lista (vedi ANNUNCI_PER_PAGINA
     * in Fonti.kt e il commento su "confineFonte" qui sotto — meno pagine,
     * meno occasioni di disallineamento fra le due fonti). Resta una
     * scelta come le altre: si spegne dal foglio filtri quando serve
     * l'archivio intero, e "azzeraFiltri()" (piu' sotto) continua a togliere
     * ogni limite, incluso questo — "azzera" vuol dire "nessun filtro",
     * non "torna al default".
     *
     * --- versione precedente (fino alla v1.40.0) ---
     * var etaModo by mutableStateOf(0)         // 0 qualsiasi, 1 piu' vecchi di, 2 piu' recenti di
     * var giorni by mutableStateOf("30")
     * --- fine versione precedente ---
     */
    var etaModo by mutableStateOf(2)         // 0 qualsiasi, 1 piu' vecchi di, 2 piu' recenti di
    var giorni by mutableStateOf("60")
    var prezzoMin by mutableStateOf("")
    var prezzoMax by mutableStateOf("")
    var mostraVenduti by mutableStateOf(false)
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
    var scartaSenzaRecensioni by mutableStateOf(false)

    /*
     * v1.1.0 — mostra solo cio' che non c'era l'ultima volta che questa stessa
     * ricerca e' stata eseguita. L'istantanea precedente si legge all'avvio
     * della ricerca e resta ferma per tutta la sessione, altrimenti il primo
     * risultato che arriva renderebbe "vecchio" tutto il resto.
     */
    var soloNuovi by mutableStateOf(false)
    private var precedenti: Set<String> = emptySet()
    private val correnti = LinkedHashSet<String>()

    /** Vero se di questa ricerca esiste un'esecuzione precedente da confrontare. */
    var haPrecedente by mutableStateOf(false)
        private set

    /** Ricerche recenti, conservate fra un avvio e l'altro. */
    var recenti by mutableStateOf(caricaRecenti())
        private set

    /* ------------------------------------------------------------------
     * v1.2.0 — preferiti
     *
     * Lo stato mostrato vive qui come mappa osservabile, cosi' il cuore cambia
     * al tocco senza aspettare la rete. La chiamata alla piattaforma parte
     * subito dopo: se fallisce, il cuore torna com'era. Meglio un cuore che
     * torna indietro di uno che mente.
     * ------------------------------------------------------------------ */
    val preferiti = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    var messaggio by mutableStateOf<String?>(null)

    /* ------------------------------------------------------------------
     * v1.2.0 — luogo degli annunci Vinted.
     *
     * Non arriva con i risultati: sta sul profilo del venditore, una chiamata
     * per venditore (vedi LuoghiVinted). Quindi la riga nasce senza luogo e lo
     * riceve dopo, ed e' per questo che serve una mappa osservabile: quando il
     * profilo risponde, quella riga — solo quella — si ridisegna.
     * ------------------------------------------------------------------ */
    val luoghiVinted = androidx.compose.runtime.mutableStateMapOf<String, String>()

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
    val recensioniVinted = androidx.compose.runtime.mutableStateMapOf<String, Int>()
    val recensioniSubito = androidx.compose.runtime.mutableStateMapOf<String, Int>()

    /*
     * v1.40.0 (2026-09-05) — il testo degli annunci Vinted, stessa forma e
     * stesso motivo delle due mappe qui sopra: il catalogo non lo porta, si
     * chiede a parte un annuncio alla volta e arriva dopo (vedi
     * DescrizioniVinted.kt). La chiave e' l'id dell'annuncio, non il
     * venditore: due annunci dello stesso venditore hanno testi diversi.
     */
    val descrizioniVinted = androidx.compose.runtime.mutableStateMapOf<String, String>()

    /*
     * v1.59.0 (2026-09-24) — data "Caricato" degli annunci Vinted, letta dal
     * dettaglio insieme alla descrizione (DataCaricamentoVinted.kt). Serve
     * SOLO a mostrare la data giusta sulla riga (conDataVera, sotto): la
     * posizione in elenco resta quella della stima dall'id, che per Vinted
     * segue gia' l'ordine vero di pubblicazione — cambiarla mentre arrivano
     * le date farebbe saltare le righe sotto il dito mentre si scorre.
     */
    val dateVinted = androidx.compose.runtime.mutableStateMapOf<String, Long>()

    /** v1.59.0 — l'annuncio con la data "Caricato" al posto della stima, se gia' nota. */
    fun conDataVera(a: Annuncio): Annuncio {
        if (a.fonte != "vinted") return a
        val ms = dateVinted[a.id] ?: DescrizioniVinted.dataCaricamento(a.id) ?: return a
        return a.copy(dataMillis = ms, dataStimata = false)
    }

    /* ------------------------------------------------------------------
     * v1.4.0 — le offerte fatte.
     *
     * Una sola chiamata per tutta la lista (vedi Offerte), non una per riga.
     * Qui vive solo un contatore osservabile: quando l'elenco cambia lo si
     * incrementa e le righe si ridisegnano leggendo Offerte, che e' la fonte.
     * ------------------------------------------------------------------ */
    var revisioneOfferte by mutableStateOf(0)
        private set

    /* ------------------------------------------------------------------
     * v1.8.0 — la vista "Preferiti".
     *
     * Non e' una schermata nuova: e' la stessa lista, riempita da un'altra
     * fonte di righe. Cosi' cuore, scarto, filtri, vista compatta e apertura
     * della scheda funzionano gia', senza riscriverli. L'unica differenza e'
     * che non si pagina e non si cerca.
     * ------------------------------------------------------------------ */
    var modoPreferiti by mutableStateOf(false)
        private set

    /*
     * v1.21.0 (2026-08-31) — CORREZIONE: onResume() da solo (v1.20.0) non
     * bastava. Un'offerta fatta da "Sito" (la scheda col sito vero, DENTRO
     * l'app) e un ritorno alla home con l'icona o "indietro" non fanno mai
     * uscire l'Activity in background: e' tutto lo stesso schermo che cambia
     * modo, quindi onResume() non scatta mai in quel percorso. tornaAllaRicerca()
     * e' il punto che TUTTI i modi di "tornare alla home" attraversano (icona,
     * indietro, uscita dai preferiti): da qui si forza la rilettura delle
     * offerte, oltre che da onResume().
     *
     * v1.37.0 — "MainActivity.cambiaModo()", citata qui, e' stata tolta
     * (l'icona lente "di cui non capisco il funzionamento", vedi
     * BarraSuperiore.kt): il riferimento restava, corretto perche' non
     * punti piu' a una funzione che non esiste.
     */
    fun tornaAllaRicerca() {
        modoPreferiti = false
        grezzi.clear(); errori.clear(); conteggi.clear()
        primaRicercaFatta = false
        ultimaQuery = ""
        aggiornaOfferteAlRitorno()
    }

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
    /** Le proposte inviate, per la schermata iniziale: tutte, non solo le ultime. */
    fun offerteRecenti() : List<Offerte.Offerta> {
        revisioneOfferte
        return Offerte.recenti()
    }

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
    fun aggiornaOfferteAlRitorno() {
        Offerte.aggiorna(ctx, motore, forza = true) { revisioneOfferte++ }
    }

    fun apriPreferiti() {
        modoPreferiti = true
        primaRicercaFatta = true
        ultimaQuery = "Preferiti"
        esaurito = true                   // non c'e' una pagina due
        grezzi.clear(); errori.clear(); conteggi.clear()
        soloNuovi = false                 // non avrebbe senso qui

        var attese = 0

        /*
         * v1.10.0 — Vinted resta a un passo: il suo elenco preferiti e' gia'
         * fatto di annunci completi (stessa forma del catalogo), quindi
         * preferitiAnnunci() li legge subito.
         */
        val cVinted = FonteVinted.preferitiElenco(utenteVinted)
        if (cVinted == null) {
            errori["vinted"] = "non so ancora chi sei: sto guardando, riprova fra un attimo"
            scopriUtenteVinted()
        } else {
            attese++
            attesa++; inCorso = true; caricamento(true)
            motore.azione(FonteVinted, cVinted) { stato, corpo ->
                if (stato in 200..299) {
                    val lista = FonteVinted.preferitiAnnunci(corpo)
                    conteggi["vinted"] = lista.size
                    // v1.9.0 — se non si riesce a leggere, si mostra l'inizio
                    // della risposta: e' quello che ha sbloccato il CSRF.
                    if (lista.isEmpty())
                        errori["vinted"] = "elenco non letto — " + corpo.take(160).replace("\n", " ")
                    else errori.remove("vinted")
                    lista.forEach { preferiti[it.chiave] = true }
                    aggiungi(lista)
                } else {
                    errori["vinted"] = "risposta $stato"
                    /*
                     * v1.45.0 — questa chiamata (csrf=false fino alla
                     * v1.44.0) non lasciava MAI una riga in diagnostica su
                     * un fallimento: il ramo di MotoreRicerca.azione() che
                     * scrive li' scatta solo per il ripiego o per il 403
                     * con csrf=true. Ora che porta csrf=true (vedi
                     * FonteVinted.preferitiElenco) quel caso e' coperto, ma
                     * un 403 puo' arrivare anche DOPO il ricaricamento
                     * automatico gia' tentato li' — quindi si registra
                     * comunque un pezzo del corpo qui, invece di lasciare
                     * "risposta 403" come unica traccia se serve un'altra
                     * diagnostica.
                     */
                    motore.diagnostica.append(
                        "[vinted] preferiti (elenco) -> stato $stato — " +
                                "${corpo.take(300).replace("\n", " ")}\n"
                    )
                    /*
                     * v1.46.0 — verifica vera del login (vedi
                     * controllaLoginVinted, sopra) invece di lasciare solo
                     * "risposta $stato": se risulta che non sei piu'
                     * collegato, il messaggio diventa un'istruzione utile
                     * invece di un codice tecnico. Il "riprova" del bottone
                     * in schermata resta lo stesso (StatoRicerca.riprova):
                     * dopo aver rifatto il login da "Sito" e tornati qui, ha
                     * senso che riprovi la chiamata vera, non solo il
                     * controllo.
                     */
                    controllaLoginVinted { loggato ->
                        if (!loggato) errori["vinted"] =
                            "sembra tu non sia più collegato — apri ⋮ → Sito → " +
                                    "Vinted, accedi col tuo account, poi torna qui e riprova"
                    }
                }
                finita()
            }
        }

        /*
         * v1.10.0 — Subito e' a DUE passi: prima l'elenco (solo urn e data),
         * poi una risoluzione per ciascuno. Vedi Fonti.kt per il contratto
         * verificato sul campo.
         */
        val cSubito = FonteSubito.preferitiElenco(utenteSubito)
        if (cSubito == null) {
            errori["subito"] = "non so ancora chi sei: sto guardando, riprova fra un attimo"
            scopriUtenteSubito()
        } else {
            attese++
            attesa++; inCorso = true; caricamento(true)
            motore.azione(FonteSubito, cSubito) { stato, corpo ->
                if (stato in 200..299) {
                    val urns = FonteSubito.preferitiUrns(corpo)
                    conteggi["subito"] = 0
                    if (urns.isEmpty()) {
                        errori["subito"] =
                            if (corpo.contains("favorite_ads")) "" // elenco vuoto: nessun preferito, non e' un errore
                            else "elenco non letto — " + corpo.take(160).replace("\n", " ")
                        if (errori["subito"].isNullOrBlank()) errori.remove("subito")
                    } else {
                        errori.remove("subito")
                        risolviPreferitiSubito(urns)
                    }
                } else errori["subito"] = "risposta $stato"
                finita()
            }
        }

        if (attese == 0) { inCorso = false; caricamento(false) }
    }

    /*
     * v1.10.0 — risolve ogni urn dei preferiti Subito con una chiamata a se',
     * due alla volta: ogni catena chiede il prossimo solo dopo aver ricevuto
     * risposta, la stessa cautela di LuoghiVinted contro le raffiche. Ogni
     * annuncio compare in lista appena arriva, non tutti insieme alla fine.
     */
    private fun risolviPreferitiSubito(urns: List<String>) {
        val PARALLELE = 2
        var successivo = 0

        fun avanti() {
            val i = successivo
            if (i >= urns.size) return
            successivo++
            attesa++; inCorso = true; caricamento(true)
            motore.azione(FonteSubito, FonteSubito.preferitoRisolvi(urns[i])) { stato, corpo ->
                if (stato in 200..299) {
                    val lista = FonteSubito.leggi(corpo)
                    if (lista.isNotEmpty()) {
                        conteggi["subito"] = (conteggi["subito"] ?: 0) + lista.size
                        preferiti[lista[0].chiave] = true
                        aggiungi(lista)
                    }
                }
                finita()
                avanti()   // la prossima di questa catena parte solo ora
            }
        }
        repeat(minOf(PARALLELE, urns.size)) { avanti() }
    }

    /*
     * v1.8.0 — l'identificatore Vinted serve solo all'elenco dei preferiti.
     * Sta in /api/v2/users/current, che nella sonda ha risposto 200 con
     * {"user":{"id":163221140,…}}: un campo dichiarato, non un indizio.
     */
    private var utenteVinted: String = prefs().getString("utenteVinted", "") ?: ""

    private fun scopriUtenteVinted() {
        if (utenteVinted.isNotBlank()) return
        motore.azione(FonteVinted, Chiamata("GET", "/api/v2/users/current")) { stato, corpo ->
            if (stato !in 200..299) return@azione
            val id = try {
                val o = org.json.JSONObject(corpo.substringAfter("] ", corpo))
                (o.optJSONObject("user") ?: o).opt("id")?.toString().orEmpty()
            } catch (t: Throwable) { "" }
            if (id.isNotBlank() && id.all(Char::isDigit)) {
                utenteVinted = id
                prefs().edit().putString("utenteVinted", id).apply()
            }
        }
    }

    /*
     * v1.46.0 (2026-09-08) — "l'errore era causato dal fatto che vinted si
     * era sloggato", segnalato dall'utente dopo il 403 corretto (a meta') in
     * v1.45.0: le intestazioni anti-CSRF servivano, ma un logout vero le
     * rende inutili lo stesso — non c'e' intestazione che tenga se la
     * sessione dietro i cookie non e' piu' autenticata.
     *
     * Invece di indovinare dal solo stato HTTP se e' proprio questo il caso
     * (403 e 401 su Vinted arrivano anche per altri motivi, vedi la storia
     * intera del CSRF in MotoreRicerca.kt), si VERIFICA per davvero: la
     * stessa chiamata gia' usata per scoprire chi sei (/api/v2/users/current,
     * v1.8.0) torna un "id" valido solo se la sessione e' ancora autenticata
     * — se non lo e' piu', quella stessa chiamata fallisce o non porta piu'
     * un id, ed e' un segnale certo, non un'ipotesi.
     *
     * Un'unica richiesta in piu', e SOLO sul percorso d'errore (mai durante
     * una ricerca che va bene): stesso principio del resto dell'app, vedi
     * "COME NON SEMBRARE UN BOT" in Controllore.kt.
     */
    /*
     * v1.63.0 (2026-09-24) — controllo del login delle due fonti, per
     * l'avviso in home (SchermataRicerca, sotto la barra dei filtri).
     *
     * Vinted: la stessa verifica gia' usata dalla v1.46.0 per i preferiti
     *   (controllaLoginVinted, sotto): /api/v2/users/current porta un id
     *   numerico solo con una sessione autenticata.
     * Subito: l'elenco delle transazioni (hades .../p2p/transactions), la
     *   stessa chiamata che Offerte fa gia' da sempre e che nella
     *   diagnostica risponde 200 con l'utente collegato. Si considera "non
     *   collegato" SOLO su 401/403 — un altro stato (rete, 5xx) resta "non
     *   so", scritto in diagnostica, senza avviso.
     *
     * Quando: all'avvio, a ogni nuova ricerca e a ogni ritorno nell'app, ma
     * al massimo ogni 10 minuti; sempre (forza=true) tornando da "Sito",
     * dove si fa il login, cosi' l'avviso sparisce appena si e' rientrati.
     * Due richieste leggere, niente di piu'.
     *
     * In piu', una sola volta per sessione e solo se Vinted risulta
     * collegato: una prova della VECCHIA ricerca JSON (/api/v2/catalog/items,
     * abbandonata in v1.56.0 perche' rispondeva con la pagina "non
     * trovato"). Se da collegati tornasse a rispondere, e' la prova che si
     * puo' tornare indietro a quella strada (date vere, venditore, venduto);
     * se no, la lettura dalla pagina resta l'unica. Solo diagnostica: non
     * cambia nulla di cio' che si vede.
     */
    private var ultimoControlloAccessi = 0L
    private var provaCatalogoFatta = false
    private var riprovaLoginFatta = false   // v1.64.0

    fun controllaAccessi(forza: Boolean = false) {
        val ora = System.currentTimeMillis()
        if (!forza && ora - ultimoControlloAccessi < 10 * 60_000L) return
        ultimoControlloAccessi = ora

        // Vinted: come controllaLoginVinted, ma a tre stati — rete assente o
        // risposta inattesa non devono diventare un falso "non collegato".
        motore.azione(FonteVinted, Chiamata("GET", "/api/v2/users/current")) { stato, corpo ->
            val loggato: Boolean? = when (stato) {
                in 200..299 -> try {
                    val o = org.json.JSONObject(corpo.substringAfter("] ", corpo))
                    val id = (o.optJSONObject("user") ?: o).opt("id")?.toString().orEmpty()
                    id.isNotBlank() && id.all(Char::isDigit)
                } catch (t: Throwable) { false }
                401, 403 -> false
                else -> null
            }
            if (loggato == null) {
                accessi.remove("vinted")
                motore.diagnostica.append("[vinted] controllo login -> stato $stato (esito incerto)\n")
                /*
                 * v1.64.0 — diagnostica v1.63.0: "controllo login -> stato -3"
                 * (nessuna risposta entro 20 s) mentre la stessa sessione era
                 * occupata con le conversazioni delle offerte. Un solo nuovo
                 * tentativo dopo 30 s, invece di restare "non so" fino al
                 * prossimo controllo fra 10 minuti.
                 */
                if (!riprovaLoginFatta) {
                    riprovaLoginFatta = true
                    android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed({ controllaAccessi(forza = true) }, 30_000L)
                }
                return@azione
            }
            riprovaLoginFatta = false
            accessi["vinted"] = loggato
            if (loggato && !provaCatalogoFatta) {
                provaCatalogoFatta = true
                motore.azione(
                    FonteVinted,
                    Chiamata("GET", "/api/v2/catalog/items?search_text=iphone&per_page=2&order=newest_first")
                ) { stato, corpo ->
                    motore.diagnostica.append(
                        "[vinted] prova vecchio catalogo JSON (collegato) -> stato $stato · " +
                                corpo.take(200).replace("\n", " ") + "\n"
                    )
                }
            }
        }
        motore.azione(
            FonteSubito,
            Chiamata("GET", "https://hades.subito.it/v1/p2p/transactions?role=buyer")
        ) { stato, _ ->
            when (stato) {
                in 200..299 -> accessi["subito"] = true
                401, 403 -> accessi["subito"] = false
                else -> {
                    accessi.remove("subito")
                    motore.diagnostica.append("[subito] controllo login -> stato $stato (esito incerto)\n")
                }
            }
        }
    }

    private fun controllaLoginVinted(esito: (loggato: Boolean) -> Unit) {
        motore.azione(FonteVinted, Chiamata("GET", "/api/v2/users/current")) { stato, corpo ->
            val loggato = if (stato !in 200..299) false else try {
                val o = org.json.JSONObject(corpo.substringAfter("] ", corpo))
                val id = (o.optJSONObject("user") ?: o).opt("id")?.toString().orEmpty()
                id.isNotBlank() && id.all(Char::isDigit)
            } catch (t: Throwable) { false }
            esito(loggato)
        }
    }

    /** Il testo rosso accanto al prezzo, o null se su questo annuncio non hai offerto. */
    fun offerta(a: Annuncio): String? {
        revisioneOfferte      // dipendenza esplicita: rilegge quando l'elenco cambia
        val o = Offerte.perAnnuncio(a) ?: return null
        return if (o.attiva) Offerte.testo(o) else null
    }

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

    /**
     * Il testo che sta accanto al nome della fonte.
     * Su Subito il luogo c'e' gia' nei risultati. Su Vinted arriva dopo: finche'
     * non e' arrivato si mostra la condizione, cosi' la riga non e' mai monca.
     */
    fun coda(a: Annuncio): String {
        if (a.fonte != "vinted") return a.luogo
        val l = luoghiVinted[a.venditore] ?: LuoghiVinted.luogo(a.venditore)
        return if (!l.isNullOrBlank()) l else a.dettaglio
    }

    /*
     * v1.40.0 (2026-09-05) — il testo dell'annuncio, per la vista estesa.
     *
     * Su Subito e' gia' nell'annuncio (campo "body" dei risultati, vedi
     * FonteSubito.leggi): si legge e basta, come e' sempre stato. Su Vinted il
     * catalogo non lo espone (sonda della v1.1.0, elenco campi in
     * DescrizioniVinted.kt) e arriva da una chiamata a parte, dopo: finche'
     * non e' arrivato si torna stringa vuota, ed e' esattamente cio' che la
     * riga gia' sa gestire da sempre — la colonna del testo non compare e i
     * dati si prendono tutta la larghezza (vedi RigaAnnuncio). Nessun posto
     * vuoto in attesa, nessuno spazio che salta quando la risposta arriva:
     * la colonna semplicemente compare.
     */
    fun descrizione(a: Annuncio): String =
        if (a.fonte != "vinted") a.descrizione
        else descrizioniVinted[a.id] ?: DescrizioniVinted.descrizione(a.id) ?: ""

    /**
     * v1.40.0 — chiede il testo di QUESTO annuncio Vinted, se non lo si ha.
     *
     * La chiama la riga mentre si disegna, e solo in vista estesa: e' il modo
     * per chiedere il testo dei soli annunci che si stanno davvero guardando,
     * invece di trenta per pagina di cui se ne leggono cinque (vedi il
     * ragionamento per esteso in cima a DescrizioniVinted.kt). Chiamarla piu'
     * volte per lo stesso annuncio non costa nulla: chi ha gia' il testo, o e'
     * gia' in coda, viene scartato subito.
     */
    /*
     * v1.51.0 (2026-09-09) — REGRESSIONE, TORNA COME IN v1.49.0: la v1.50.0
     * aveva rimesso "a.url" per far navigare la sessione sulla pagina vera
     * prima della chiamata di traduzione — idea corretta in teoria (stessa
     * causa/prova gia' viste per il cuore, v1.26.0), ma quella sessione e'
     * la STESSA, condivisa, che la ricerca sta ancora usando per le pagine
     * successive del catalogo mentre si scorre. L'utente ha segnalato che il
     * testo e' sparito del tutto, in ogni lingua — peggio di prima. Si torna
     * al solo id: testo originale, non tradotto, ma di nuovo affidabile.
     * Vedi il 5° tentativo in cima a DescrizioniVinted.kt.
     *
     * --- versione precedente (v1.50.0, causa della regressione) ---
     * fun chiediDescrizione(a: Annuncio) {
     *     if (a.fonte != "vinted" || a.id.isBlank() || a.url.isBlank()) return
     *     if (descrizioniVinted.containsKey(a.id)) return
     *     DescrizioniVinted.richiedi(ctx, motore, a.id, a.url) { id, testo ->
     *         descrizioniVinted[id] = testo
     *     }
     * }
     * --- fine versione precedente ---
     *
     * --- versione precedente (v1.47.0-v1.49.0) ---
     * fun chiediDescrizione(a: Annuncio) {
     *     if (a.fonte != "vinted" || a.id.isBlank()) return
     *     if (descrizioniVinted.containsKey(a.id)) return
     *     DescrizioniVinted.richiedi(ctx, motore, a.id) { id, testo ->
     *         descrizioniVinted[id] = testo
     *     }
     * }
     * --- fine versione precedente ---
     *
     * --- versione precedente (v1.43.0-v1.46.0) ---
     * fun chiediDescrizione(a: Annuncio) {
     *     if (a.fonte != "vinted" || a.id.isBlank() || a.url.isBlank()) return
     *     if (descrizioniVinted.containsKey(a.id)) return
     *     DescrizioniVinted.richiedi(ctx, motore, a.id, a.url) { id, testo ->
     *         descrizioniVinted[id] = testo
     *     }
     * }
     * --- fine versione precedente ---
     *
     * --- versione precedente (fino alla v1.42.0) ---
     * fun chiediDescrizione(a: Annuncio) {
     *     if (a.fonte != "vinted" || a.id.isBlank()) return
     *     if (descrizioniVinted.containsKey(a.id)) return
     *     DescrizioniVinted.richiedi(ctx, motore, a.id) { id, testo ->
     *         descrizioniVinted[id] = testo
     *     }
     * }
     * --- fine versione precedente ---
     */
    /*
     * v1.59.0 (2026-09-24) — la stessa richiesta porta anche la data
     * "Caricato" (dataPronta -> dateVinted). La chiama ora ogni riga Vinted,
     * anche in vista compatta (vedi SchermataRicerca), non piu' solo la
     * vista estesa: la data si vede in entrambe.
     *
     * --- versione precedente (v1.51.0-v1.58.0) ---
     * fun chiediDescrizione(a: Annuncio) {
     *     if (a.fonte != "vinted" || a.id.isBlank()) return
     *     if (descrizioniVinted.containsKey(a.id)) return
     *     DescrizioniVinted.richiedi(ctx, motore, a.id) { id, testo ->
     *         descrizioniVinted[id] = testo
     *     }
     * }
     * --- fine versione precedente ---
     */
    fun chiediDescrizione(a: Annuncio) {
        if (a.fonte != "vinted" || a.id.isBlank()) return
        if (descrizioniVinted.containsKey(a.id)) return
        DescrizioniVinted.richiedi(
            ctx, motore, a.id,
            dataPronta = { id, ms -> dateVinted[id] = ms }
        ) { id, testo ->
            descrizioniVinted[id] = testo
        }
    }

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
    fun recensioni(a: Annuncio): Int? = when (a.fonte) {
        "vinted" -> recensioniVinted[a.venditore] ?: LuoghiVinted.recensioni(a.venditore)
        "subito" -> recensioniSubito[a.venditore] ?: RecensioniSubito.recensioni(a.venditore)
        else -> null
    }

    private var utenteSubito: String =
        prefs().getString("utenteSubito", "") ?: ""
    private var elencoSubitoLetto = false

    fun ePreferito(a: Annuncio): Boolean =
        preferiti[a.chiave] ?: Preferiti.contiene(a.chiave)

    fun cambiaPreferito(a: Annuncio) {
        val prima = ePreferito(a)
        val dopo = !prima
        val f = FONTI.firstOrNull { it.nome == a.fonte } ?: return

        val utente = if (a.fonte == "subito") utenteSubito else ""
        val c = if (dopo) f.preferitoAggiungi(a, utente) else f.preferitoRimuovi(a, utente)
        if (c == null) {
            messaggio = "Preferiti non disponibili: apri una volta il sito dalla navigazione"
            if (a.fonte == "subito") scopriUtenteSubito()
            return
        }

        preferiti[a.chiave] = dopo
        Preferiti.imposta(ctx, a.chiave, dopo)

        motore.azione(f, c) { stato, corpo ->
            val ok = stato in 200..299 && !corpo.contains("\"code\":1")
            if (!ok) {
                preferiti[a.chiave] = prima
                Preferiti.imposta(ctx, a.chiave, prima)
                // v1.6.0 — nel messaggio anche la prima riga della risposta:
                // "403 access_denied" e "404" vogliono correzioni diverse.
                messaggio = "Preferito non salvato su ${a.fonte} ($stato) " +
                        corpo.take(90).replace("\n", " ")
            }
        }
    }

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
    private fun scopriUtenteSubito() {
        if (utenteSubito.isNotBlank()) return
        motore.leggiDallaPagina(
            FonteSubito,
            """
            (function(){
              try {
                var s = document.cookie;
                for (var i = 0; i < localStorage.length; i++)
                  s += ' ' + localStorage.getItem(localStorage.key(i));
                for (var j = 0; j < sessionStorage.length; j++)
                  s += ' ' + sessionStorage.getItem(sessionStorage.key(j));
                var m = /sdrn:[a-z.]*subito[a-z.]*:user:(\d+)/i.exec(s);
                if (m) return m[1];
                m = /"user_?id"\s*:\s*"?(\d{5,})/i.exec(s);
                return m ? m[1] : '';
              } catch (e) { return ''; }
            })()
            """.trimIndent()
        ) { grezzo ->
            val id = grezzo.trim().trim('"')
            if (id.isNotBlank() && id.all(Char::isDigit)) usaUtenteSubito(id)
            else utenteDalleTransazioni()
        }
    }

    /** Seconda strada: l'id sta dentro l'elenco delle transazioni, come buyer_id. */
    private fun utenteDalleTransazioni() {
        motore.azione(
            FonteSubito,
            Chiamata("GET", "https://hades.subito.it/v1/p2p/transactions?role=buyer")
        ) { stato, corpo ->
            if (stato !in 200..299) {
                messaggio = "Non riesco a riconoscere il tuo account Subito (risposta $stato)"
                return@azione
            }
            val id = try {
                val arr = org.json.JSONObject(corpo.substringAfter("] ", corpo))
                    .optJSONArray("transactions")
                var trovato = ""
                if (arr != null) for (i in 0 until arr.length()) {
                    val t = arr.optJSONObject(i) ?: continue
                    val b = t.optString("buyer_id")
                    if (b.isNotBlank() && b.all(Char::isDigit)) { trovato = b; break }
                }
                trovato
            } catch (t: Throwable) { "" }
            if (id.isNotBlank()) usaUtenteSubito(id)
            else messaggio = "Account Subito non riconosciuto: fai almeno un'offerta o apri il sito"
        }
    }

    private fun usaUtenteSubito(id: String) {
        utenteSubito = id
        prefs().edit().putString("utenteSubito", id).apply()
        leggiPreferitiSubito()
    }

    private fun leggiPreferitiSubito() {
        if (elencoSubitoLetto || utenteSubito.isBlank()) return
        val c = FonteSubito.preferitiElenco(utenteSubito) ?: return
        elencoSubitoLetto = true
        motore.azione(FonteSubito, c) { stato, corpo ->
            if (stato in 200..299) {
                val veri = FonteSubito.preferitiLeggi(corpo)
                Preferiti.riallinea(ctx, "", veri)
                veri.forEach { preferiti[it] = true }
            } else elencoSubitoLetto = false   // si riprova alla prossima ricerca
        }
    }

    init {
        Preferiti.carica(ctx)
        LuoghiVinted.carica(ctx)          // v1.2.0
        DescrizioniVinted.carica(ctx)     // v1.40.0
        RecensioniSubito.carica(ctx)      // v1.30.0
        Offerte.carica(ctx)               // v1.4.0
        Offerte.aggiorna(ctx, motore) { revisioneOfferte++ }
        scopriUtenteSubito()
        leggiPreferitiSubito()
        controllaAccessi(forza = true)    // v1.63.0

        motore.ascoltatore = object : MotoreRicerca.Ascoltatore {
            override fun risultati(fonte: String, annunci: List<Annuncio>, diagnostica: String) {
                errori.remove(fonte)
                conteggi[fonte] = (conteggi[fonte] ?: 0) + annunci.size
                aggiungi(annunci)
                finita()
            }
            override fun errore(fonte: String, messaggio: String) {
                errori[fonte] = messaggio
                finita()
            }
        }
    }

    // ------------------------------------------------------------------
    fun fonti(): List<Fonte> = buildList {
        if (usaSubito) add(FonteSubito)
        if (usaVinted) add(FonteVinted)
    }

    private fun chiaveOrdine() = when (ordine) {
        1 -> "prezzo_cresc"; 2 -> "prezzo_decr"; else -> "recenti"
    }

    fun cerca() {
        val q = query.trim()
        if (q.isEmpty()) return
        val f = fonti()
        if (f.isEmpty()) return
        modoPreferiti = false            // v1.8.0
        controllaAccessi()               // v1.63.0 — al massimo ogni 10 minuti
        ultimaQuery = q
        pagina = 1
        grezzi.clear(); errori.clear(); conteggi.clear()
        // v1.41.0 — nuova ricerca, i confini della precedente non contano piu'.
        confineFonte.clear(); esauritoFonte.clear(); dimensionePrimaPerFonte.clear(); fonteRisposta.clear()
        primaRicercaFatta = true
        esaurito = false
        // v1.1.0 — si fotografa il "prima" adesso, non dopo
        precedenti = StoricoRicerche.precedenti(q)
        haPrecedente = StoricoRicerche.conosce(q)
        correnti.clear()
        ricordaRecente(q)
        // v1.4.0 — l'elenco delle offerte si rilegge al piu' ogni cinque minuti
        Offerte.aggiorna(ctx, motore) { revisioneOfferte++ }
        avvia(f)
    }

    /** Chiamata quando lo scorrimento si avvicina al fondo. */
    fun altraPagina() {
        if (modoPreferiti) return        // v1.8.0: l'elenco arriva tutto insieme
        if (inCorso || esaurito || ultimaQuery.isEmpty()) return
        val f = fonti()
        if (f.isEmpty()) return
        pagina++
        avvia(f)
    }

    fun riprova(fonte: String) {
        val f = FONTI.firstOrNull { it.nome == fonte } ?: return
        errori.remove(fonte)
        if (modoPreferiti) { apriPreferiti(); return }   // v1.9.0
        avvia(listOf(f))
    }

    /*
     * v1.41.0 — oltre allo scatto complessivo (dimensionePrima), ora si
     * fotografa anche quanto avevamo GIA' per ciascuna delle fonti che
     * stanno per rispondere: serve a aggiornaConfini(), a giro finito, per
     * sapere se QUELLA fonte ha portato qualcosa o e' arrivata a secco.
     *
     * --- versione precedente (fino alla v1.40.0) ---
     * private fun avvia(f: List<Fonte>) {
     *     dimensionePrima = grezzi.size
     *     attesa += f.size
     *     inCorso = true
     *     caricamento(true)
     *     motore.cerca(ultimaQuery, pagina, chiaveOrdine(), f)
     * }
     * --- fine versione precedente ---
     */
    private fun avvia(f: List<Fonte>) {
        dimensionePrima = grezzi.size
        f.forEach { fonte -> dimensionePrimaPerFonte[fonte.nome] = grezzi.count { it.fonte == fonte.nome } }
        attesa += f.size
        inCorso = true
        caricamento(true)
        motore.cerca(ultimaQuery, pagina, chiaveOrdine(), f)
    }

    /*
     * v1.41.0 — "aggiornaConfini()" si chiama solo qui, e solo quando il
     * giro e' DAVVERO completo (attesa a zero): vedi il commento su
     * "confineFonte" per il perche'.
     *
     * --- versione precedente (fino alla v1.40.0) ---
     * private fun finita() {
     *     if (attesa > 0) attesa--
     *     inCorso = attesa > 0
     *     if (!inCorso && pagina > 1 && grezzi.size == dimensionePrima) esaurito = true
     *     if (!inCorso && !modoPreferiti) StoricoRicerche.registra(ctx, ultimaQuery, correnti)
     *     caricamento(inCorso)
     * }
     * --- fine versione precedente ---
     */
    private fun finita() {
        if (attesa > 0) attesa--
        inCorso = attesa > 0
        if (!inCorso) {
            if (pagina > 1 && grezzi.size == dimensionePrima) esaurito = true
            aggiornaConfini()
        }
        // v1.1.0 — a passata conclusa questa diventa "la volta prima" per la successiva
        if (!inCorso && !modoPreferiti) StoricoRicerche.registra(ctx, ultimaQuery, correnti)
        caricamento(inCorso)
    }

    /**
     * v1.41.0 — chiusura di un giro completo: per ogni fonte che ha appena
     * risposto, si registra fin dove e' arrivata (la data del suo annuncio
     * piu' vecchio finora) e se e' rimasta a secco (nessun annuncio nuovo
     * rispetto a prima di questa richiesta). Vedi il commento su
     * "confineFonte" per il quadro completo.
     */
    private fun aggiornaConfini() {
        dimensionePrimaPerFonte.forEach { (nome, contoPrima) ->
            fonteRisposta[nome] = true
            val contoDopo = grezzi.count { it.fonte == nome }
            if (contoDopo == contoPrima) esauritoFonte[nome] = true
            grezzi.asSequence()
                .filter { it.fonte == nome && it.dataMillis > 0L }
                .minOfOrNull { it.dataMillis }
                ?.let { confineFonte[nome] = it }
        }
    }

    /**
     * v1.41.0 — quanto indietro nel tempo si puo' mostrare senza rischiare
     * "buchi" (un annuncio che sembra mancare solo perche' quella fonte non
     * e' ancora arrivata cosi' indietro). Vedi il commento su "confineFonte".
     *
     * Long.MAX_VALUE = non mostrare ancora nulla (una fonte attiva non ha
     * ancora risposto neppure una volta in questa ricerca).
     * Long.MIN_VALUE = nessun limite (tutte le fonti attive sono esaurite,
     * o i preferiti non sono paginati per data e la domanda non si pone).
     */
    private fun confineSicuro(): Long {
        if (modoPreferiti) return Long.MIN_VALUE
        val attive = fonti()
        if (attive.isEmpty()) return Long.MIN_VALUE
        val nonRisposto = attive.any { f -> fonteRisposta[f.nome] != true }
        if (nonRisposto) return Long.MAX_VALUE
        val restrittive = attive.mapNotNull { f -> if (esauritoFonte[f.nome] == true) null else confineFonte[f.nome] }
        return restrittive.minOrNull() ?: Long.MIN_VALUE
    }

    private fun aggiungi(nuovi: List<Annuncio>) {
        val gia = grezzi.mapTo(HashSet()) { it.fonte + it.id }
        /*
         * v1.2.0 — Vinted dichiara is_favourite nei risultati: e' verita' fresca
         * dalla piattaforma e vince sulla copia locale. Subito non lo dichiara,
         * e li' lo stato arriva dall'elenco letto a parte.
         */
        val dichiarati = HashMap<String, Boolean>()
        nuovi.forEach {
            correnti.add(it.chiave)      // v1.1.0: entra nell'istantanea di questa esecuzione
            if (it.fonte == "vinted") {
                dichiarati[it.chiave] = it.preferitoIniziale
                preferiti[it.chiave] = it.preferitoIniziale
            }
            if (gia.add(it.fonte + it.id) && !Nascosti.contiene(it.chiave)) grezzi.add(it)
        }
        if (dichiarati.isNotEmpty()) Preferiti.aggiorna(ctx, dichiarati)
        leggiPreferitiSubito()

        /*
         * v1.2.0 — i luoghi Vinted si chiedono solo per i venditori che
         * compaiono davvero in lista, e solo per quelli mai visti prima.
         * Si parte dai venditori appena aggiunti: la coda pensa al resto.
         */
        val venditori = nuovi.asSequence()
            .filter { it.fonte == "vinted" && it.venditore.isNotBlank() }
            .map { it.venditore }.distinct().toList()
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
        if (venditori.isNotEmpty()) {
            venditori.forEach { v ->
                LuoghiVinted.luogo(v)?.let { luoghiVinted[v] = it }
                LuoghiVinted.recensioni(v)?.let { recensioniVinted[v] = it }
            }
            LuoghiVinted.richiedi(ctx, motore, venditori) { v, l, r ->
                luoghiVinted[v] = l
                if (r != null) recensioniVinted[v] = r
            }
        }

        /*
         * v1.30.0 — NUOVO: le recensioni Subito, stesso schema di quelle
         * Vinted qui sopra ma senza luogo (Subito il luogo lo dichiara gia'
         * nei risultati, vedi FonteSubito.luogo() in Fonti.kt — qui serve
         * solo il conteggio, vedi RecensioniSubito.kt).
         */
        val venditoriSubito = nuovi.asSequence()
            .filter { it.fonte == "subito" && it.venditore.isNotBlank() }
            .map { it.venditore }.distinct().toList()
        if (venditoriSubito.isNotEmpty()) {
            venditoriSubito.forEach { v -> RecensioniSubito.recensioni(v)?.let { recensioniSubito[v] = it } }
            RecensioniSubito.richiedi(ctx, motore, venditoriSubito) { v, r -> recensioniSubito[v] = r }
        }
    }

    // ------------------------------------------------------------------
    /** Scarto con possibilita' di annullare: l'archivio si tocca solo alla conferma. */
    fun nascondi(a: Annuncio) {
        Nascosti.aggiungi(ctx, a.chiave, a.titolo)
        grezzi.removeAll { it.fonte == a.fonte && it.id == a.id }
    }

    fun annullaScarto(a: Annuncio) {
        Nascosti.rimuovi(ctx, a.chiave)
        if (grezzi.none { it.fonte == a.fonte && it.id == a.id }) grezzi.add(a)
    }

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
    // ------------------------------------------------------------------
    /*
     * v1.41.0 — "risultati" guadagna un sesto filtro, "perCompletezza": vedi
     * il lungo commento sopra "confineFonte" per il bug che chiude (il
     * rimescolamento durante lo scorrimento). Non e' un filtro che l'utente
     * sceglie, come gli altri cinque: e' una garanzia interna, sempre
     * attiva, che non mostra mai un annuncio piu' vecchio del punto fino a
     * cui TUTTE le fonti attive sono davvero arrivate.
     */
    /** Lista visibile: filtri applicati a cio' che e' gia' stato scaricato. */
    val risultati: List<Annuncio>
        get() {
            val n = giorni.toLongOrNull() ?: 0L
            val soglia = System.currentTimeMillis() - n * 24L * 3600_000L
            val pMin = prezzoMin.replace(',', '.').toDoubleOrNull()
            val pMax = prezzoMax.replace(',', '.').toDoubleOrNull()
            val confine = confineSicuro()   // v1.41.0

            val lista = grezzi.filter { a ->
                val perEta = when {
                    etaModo == 0 || n <= 0 -> true
                    a.dataMillis == 0L -> false      // senza data non si puo' affermare nulla
                    etaModo == 1 -> a.dataMillis <= soglia
                    else -> a.dataMillis >= soglia
                }
                val perPrezzo = when {
                    pMin == null && pMax == null -> true
                    a.prezzo == null -> false
                    pMin != null && a.prezzo < pMin -> false
                    pMax != null && a.prezzo > pMax -> false
                    else -> true
                }
                val perNovita = !soloNuovi || !precedenti.contains(a.chiave)   // v1.1.0
                // v1.23.0 — la fonte va rispettata anche in vista, non solo nel fetch.
                val perFonte = (a.fonte != "subito" || usaSubito) && (a.fonte != "vinted" || usaVinted)
                /*
                 * v1.29.0 — "scarta chi ha 0 recensioni". NON scarta quando il
                 * numero non e' ancora noto (recensioni(a) == null): ne' perche'
                 * la risposta del venditore non e' ancora arrivata (Vinted, di
                 * norma questione di secondi), ne' perche' e' Subito, dove il
                 * dato proprio non c'e' ancora (vedi LuoghiVinted.kt). Scarta
                 * solo quando si SA che sono zero.
                 */
                val perRecensioni = !scartaSenzaRecensioni || (recensioni(a) ?: 1) > 0
                // v1.41.0 — vedi il commento su "confineFonte": un annuncio senza
                // data non si puo' collocare nel tempo, quindi non lo si trattiene.
                val perCompletezza = a.dataMillis == 0L || a.dataMillis >= confine
                perEta && perPrezzo && perNovita && perFonte && perRecensioni && perCompletezza &&
                        (mostraVenduti || !a.venduto)
            }
            return when (ordine) {
                1 -> lista.sortedBy { it.prezzo ?: Double.MAX_VALUE }
                2 -> lista.sortedByDescending { it.prezzo ?: -1.0 }
                else -> lista.sortedByDescending { it.dataMillis }
            }
        }

    val esclusi: Int get() = grezzi.size - risultati.size

    val filtriAttivi: Int
        get() = listOf(
            etaModo != 0 && (giorni.toLongOrNull() ?: 0) > 0,
            prezzoMin.isNotBlank() || prezzoMax.isNotBlank(),
            mostraVenduti,
            soloNuovi,
            !usaSubito || !usaVinted,
            ordine != 0,
            scartaSenzaRecensioni     // v1.29.0
        ).count { it }

    fun azzeraFiltri() {
        etaModo = 0; giorni = "30"; prezzoMin = ""; prezzoMax = ""
        mostraVenduti = false; soloNuovi = false
        usaSubito = true; usaVinted = true; ordine = 0
        scartaSenzaRecensioni = false     // v1.29.0
    }

    // ------------------------------------------------------------------
    private fun prefs() = ctx.getSharedPreferences("subitoplus", Context.MODE_PRIVATE)

    private fun caricaRecenti(): List<String> =
        prefs().getString("recenti", "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    private fun ricordaRecente(q: String) {
        val nuove = (listOf(q) + recenti.filter { !it.equals(q, true) }).take(8)
        recenti = nuove
        prefs().edit().putString("recenti", nuove.joinToString("\n")).apply()
    }

    fun dimenticaRecenti() {
        recenti = emptyList()
        prefs().edit().remove("recenti").apply()
    }

    /**
     * v1.36.0 (2026-09-04) — nuovo: toglie UNA sola ricerca recente (tenendo
     * premuto sulla sua etichetta sotto il campo di ricerca — vedi
     * ChipRicercaRecente in SchermataRicerca.kt), invece di svuotare tutto
     * l'elenco come faceva gia' dimenticaRecenti() qui sopra.
     */
    fun dimenticaRecente(q: String) {
        val nuove = recenti.filter { !it.equals(q, true) }
        recenti = nuove
        prefs().edit().putString("recenti", nuove.joinToString("\n")).apply()
    }

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
    fun ripristinaUltimaRicerca(): Boolean {
        if (primaRicercaFatta) return false
        val ultima = recenti.firstOrNull() ?: return false
        query = ultima
        cerca()
        return true
    }
}
