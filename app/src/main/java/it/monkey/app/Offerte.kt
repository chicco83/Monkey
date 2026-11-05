package it.monkey.app

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

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Offerte {

    private const val FILE = "offerte.json"

    /** Non piu' di una lettura ogni cinque minuti: e' un elenco, non un flusso. */
    private const val VALIDITA_MS = 5 * 60_000L

    /** v1.10.0 — quante conversazioni Vinted recenti si controllano a ogni giro. */
    private const val ULTIME_CONVERSAZIONI_VINTED = 15

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
    data class Offerta(
        val centesimi: Int,
        val etichetta: String,
        val attiva: Boolean,
        val id: String = "",
        val titolo: String = "",
        val immagine: String? = null,
        val prezzoAnnuncio: Int = 0,
        val quando: Long = 0L,
        /** v1.9.0 — lo stato interno ("PURCHASE_REQUESTED", "CLOSED"): non si
         *  mostra, serve a riconoscere gli ordini conclusi senza dipendere
         *  dalle sole parole italiane dell'etichetta. */
        val stato: String = "",
        val fonte: String = "subito",
        /** v1.32.0 — il rilancio del venditore (solo Vinted, per ora: solo
         *  li' si e' vista la prova di un messaggio di controproposta
         *  distinto). 0 = nessun rilancio pendente. */
        val controproposta: Int = 0
    ) {
        /*
         * v1.10.0 — un URL per fonte.
         *
         * --- versione precedente (fino alla v1.9.0, solo Subito) ---
         * val url: String get() = "https://www.subito.it/vi/$id.htm"
         * --- fine versione precedente ---
         */
        /** Verificato sul campo: /vi/<id>.htm apre la scheda giusta su Subito. */
        val url: String get() = if (fonte == "vinted")
            "https://www.vinted.it/items/$id" else "https://www.subito.it/vi/$id.htm"
    }

    private val perChiave = HashMap<String, Offerta>()
    private var ultimaLettura = 0L
    private var inCorso = false
    private var caricato = false

    // ------------------------------------------------------------------
    @Synchronized
    fun carica(ctx: Context) {
        if (caricato) return
        caricato = true
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return
        try {
            val o = JSONObject(f.readText()).optJSONObject("offerte") ?: return
            o.keys().forEach { k ->
                val v = o.optJSONObject(k) ?: return@forEach
                perChiave[k] = Offerta(
                    centesimi = v.optInt("centesimi"),
                    etichetta = v.optString("etichetta"),
                    attiva = v.optBoolean("attiva", true),
                    id = v.optString("id").ifBlank { k },
                    titolo = v.optString("titolo"),
                    immagine = v.optString("immagine").takeIf { it.isNotBlank() },
                    prezzoAnnuncio = v.optInt("prezzoAnnuncio"),
                    quando = v.optLong("quando"),
                    stato = v.optString("stato"),
                    // v1.10.0 — assente nei backup pre-1.10.0: si ricava dalla chiave.
                    fonte = v.optString("fonte").ifBlank { if (k.startsWith("v:")) "vinted" else "subito" },
                    // v1.32.0 — assente nei backup pre-1.32.0: nessun rilancio noto, 0 va bene.
                    controproposta = v.optInt("controproposta", 0)
                )
            }
        } catch (t: Throwable) { /* file rovinato: si riparte da vuoto */ }
    }

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
    @Synchronized
    fun perAnnuncio(a: Annuncio): Offerta? = perChiave[a.chiave]

    @Synchronized fun quante() = perChiave.count { it.value.attiva }

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
    fun aggiorna(
        ctx: Context,
        motore: MotoreRicerca,
        forza: Boolean = false,
        fatto: () -> Unit = {}
    ) {
        synchronized(this) {
            carica(ctx)
            if (inCorso) return
            if (!forza && System.currentTimeMillis() - ultimaLettura < VALIDITA_MS) return
            inCorso = true
            ultimaLettura = System.currentTimeMillis()   // segnato subito: una raffica di chiamate nella stessa finestra non ripartono tutte
        }
        var restanti = 2   // Subito + Vinted: "inCorso" cade solo a bersaglio raggiunto da entrambe
        fun unaFinita() {
            val chiudi = synchronized(this) { restanti--; restanti <= 0 }
            if (chiudi) synchronized(this) { inCorso = false }
        }
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
        motore.azione(
            FonteSubito,
            Chiamata("GET", "https://hades.subito.it/v1/p2p/transactions?role=buyer")
        ) { stato, corpo ->
            var cambiato = false
            var lette = -1
            if (stato in 200..299) {
                synchronized(this) {
                    lette = try { JSONObject(corpo).optJSONArray("transactions")?.length() ?: 0 }
                            catch (t: Throwable) { -1 }
                    cambiato = leggiSubito(corpo)
                    if (cambiato) salva(ctx)
                }
                motore.diagnostica.append(
                    "[subito] transactions -> $stato, $lette transazioni lette, cambiato=$cambiato\n"
                )
            } else {
                motore.diagnostica.append(
                    "[subito] transactions -> $stato, offerte non aggiornate — ${corpo.take(150)}\n"
                )
            }
            if (cambiato) fatto()
            unaFinita()
        }
        aggiornaVinted(ctx, motore) { cambiato -> if (cambiato) fatto(); unaFinita() }
    }

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
    private fun aggiornaVinted(ctx: Context, motore: MotoreRicerca, fatto: (Boolean) -> Unit) {
        motore.azione(FonteVinted, Chiamata("GET", "/api/v2/inbox?page=1&per_page=25")) { stato, corpo ->
            if (stato !in 200..299) {
                motore.diagnostica.append("[vinted] /inbox -> $stato, offerte non aggiornate — ${corpo.take(150)}\n")
                fatto(false)
                return@azione
            }
            val id = try {
                val arr = JSONObject(corpo).optJSONArray("conversations") ?: JSONArray()
                (0 until minOf(arr.length(), ULTIME_CONVERSAZIONI_VINTED))
                    .mapNotNull { arr.optJSONObject(it)?.opt("id")?.toString() }
            } catch (t: Throwable) {
                motore.diagnostica.append("[vinted] /inbox -> $stato, lettura fallita: ${t.message}\n")
                emptyList()
            }
            motore.diagnostica.append("[vinted] /inbox -> $stato, ${id.size} conversazioni da controllare\n")

            if (id.isEmpty()) {
                val cambiato = synchronized(this) {
                    val c = sostituisciFonteVinted(emptyMap())
                    if (c) salva(ctx)
                    c
                }
                fatto(cambiato)
                return@azione
            }

            val nuove = java.util.Collections.synchronizedMap(LinkedHashMap<String, Offerta>())
            var trovate = 0
            var restanti = id.size
            id.forEach { convId ->
                motore.azione(FonteVinted, Chiamata("GET", "/api/v2/conversations/$convId")) { s2, c2 ->
                    if (s2 in 200..299) {
                        val letta = leggiConversazioneVinted(c2)
                        if (letta != null) { nuove[letta.first] = letta.second; trovate++ }
                    } else {
                        motore.diagnostica.append("[vinted] /conversations/$convId -> $s2 — ${c2.take(150)}\n")
                    }
                    val ultima: Boolean
                    synchronized(this) { restanti--; ultima = restanti <= 0 }
                    if (ultima) {
                        motore.diagnostica.append(
                            "[vinted] conversazioni lette: ${id.size}, offerte trovate: $trovate\n"
                        )
                        val cambiato = synchronized(this) {
                            val c = sostituisciFonteVinted(nuove)
                            if (c) salva(ctx)
                            c
                        }
                        fatto(cambiato)
                    }
                }
            }
        }
    }

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
    /** @return la coppia (chiave, offerta) se la conversazione porta un'offerta attiva, altrimenti null. */
    private fun leggiConversazioneVinted(corpo: String): Pair<String, Offerta>? = try {
        val conv = JSONObject(corpo).optJSONObject("conversation")
        val tr = conv?.optJSONObject("transaction")
        val itemId = tr?.opt("item_id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }

        // Chi sono io e chi e' l'altro, per smistare ogni messaggio di offerta
        // fra "mio" e "della controparte" invece di fidarsi del solo tipo.
        val lato = tr?.optString("current_user_side")
        val mioId = when (lato) {
            "buyer" -> tr?.optString("buyer_id")
            "seller" -> tr?.optString("seller_id")
            else -> null
        }
        val controparteId = when (lato) {
            "buyer" -> tr?.optString("seller_id")
            "seller" -> tr?.optString("buyer_id")
            else -> null
        }

        var mioEntita: JSONObject? = null
        var controEntita: JSONObject? = null
        var ultimoEntita: JSONObject? = null
        var ultimoQuandoIso = ""
        var ultimoDellaControparte = false

        // L'array e' cronologico (verificato convertendo created_at_ts, che usa
        // fusi diversi da un messaggio all'altro ma resta in ordine una volta
        // riportato a UTC): l'ultimo che si incontra di ciascun tipo e' sempre
        // il piu' recente, senza dover confrontare le date a mano.
        val messaggi = conv?.optJSONArray("messages")
        if (messaggi != null) for (i in 0 until messaggi.length()) {
            val m = messaggi.optJSONObject(i) ?: continue
            val tipo = m.optString("entity_type")
            if (tipo != "offer_request_message" && tipo != "offer_message") continue
            val entita = m.optJSONObject("entity") ?: continue
            val quandoIso = m.optString("created_at_ts")
            val userId = entita.optString("user_id")

            ultimoEntita = entita
            ultimoQuandoIso = quandoIso
            ultimoDellaControparte = controparteId != null && userId == controparteId

            when {
                mioId != null && userId == mioId -> mioEntita = entita
                controparteId != null && userId == controparteId -> controEntita = entita
                // Lato sconosciuto (transazione senza current_user_side/buyer_id/
                // seller_id leggibili): si ripiega sul vecchio criterio, il solo
                // tipo del messaggio — non e' mai stata vista una sonda con questo
                // caso, ma e' meglio di scartare tutto.
                mioId == null -> if (tipo == "offer_request_message") mioEntita = entita
                                  else controEntita = entita
            }
        }

        // La MIA ultima offerta e' quella che conta per "quanto ho offerto"; se
        // manca (conversazione dove ha proposto solo il venditore) si usa la sua
        // pur di non scartare la riga.
        val entitaPerImporto = mioEntita ?: controEntita
        val importo = entitaPerImporto?.optJSONObject("price")?.optString("amount")?.toDoubleOrNull()
        val importoContro = controEntita.takeIf { it !== entitaPerImporto }
            ?.optJSONObject("price")?.optString("amount")?.toDoubleOrNull()
            ?.takeIf { it > 0 }

        if (tr == null || itemId == null || entitaPerImporto == null || importo == null || importo <= 0) null
        else {
            // Lo stato si legge dall'ULTIMO messaggio in assoluto: un rilancio
            // del venditore non porta "status_title" (solo l'importo, vedi
            // sopra), quindi quando e' lui l'ultimo si scrive una frase propria
            // invece di lasciare lo stato vecchio della mia offerta gia' decisa.
            val statoTitoloGrezzo = ultimoEntita?.optString("status_title").orEmpty()
            val statoTitolo = when {
                statoTitoloGrezzo.isNotBlank() -> statoTitoloGrezzo
                ultimoDellaControparte -> "Controproposta ricevuta"
                else -> tr.optString("status")
            }
            // v1.25.0 — due booleani veri dalla transazione, piu' affidabili
            // di indovinare da un testo che potrebbe non cambiare mai.
            val chiusaStruttura = tr.optBoolean("is_completed", false) ||
                    tr.optBoolean("item_is_closed", false)
            val foto = tr.optJSONObject("item_photo")?.optString("url")
            // v1.32.0 — "original_price", non piu' "transaction.offer_price"
            // (vedi il commento sopra la funzione: quel campo diventa il
            // rilancio corrente una volta che ce n'e' uno, non il listino).
            val prezzoListino = entitaPerImporto.optJSONObject("original_price")
                ?.optString("amount")?.toDoubleOrNull()
            "v:$itemId" to Offerta(
                centesimi = Math.round(importo * 100).toInt(),
                etichetta = statoTitolo,
                attiva = !terminata(statoTitolo) && !chiusaStruttura,
                id = itemId,
                titolo = tr.optString("item_title"),
                immagine = foto?.takeIf { it.startsWith("http") },
                prezzoAnnuncio = prezzoListino?.let { Math.round(it * 100).toInt() } ?: 0,
                // data del messaggio piu' recente in assoluto (mio o del
                // venditore): un rilancio appena arrivato deve far risalire la
                // conversazione fra le piu' recenti in home, non restarci sotto
                // con la data della mia vecchia offerta.
                quando = iso(ultimoQuandoIso).takeIf { it > 0 } ?: System.currentTimeMillis(),
                stato = statoTitolo,
                fonte = "vinted",
                controproposta = importoContro?.let { Math.round(it * 100).toInt() } ?: 0
            )
        }
    } catch (t: Throwable) { null }

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
    /** v1.10.0 — parole viste sugli stati chiusi delle piattaforme, in inglese e italiano. */
    private fun terminata(stato: String): Boolean {
        val s = stato.lowercase()
        return s.contains("cancel") || s.contains("complet") || s.contains("declin") ||
               s.contains("expir") || s.contains("closed") || s.contains("conclus") ||
               s.contains("rifiut") || s.contains("vendut")
    }

    /**
     * v1.10.0 — sostituisce solo la fetta di "perChiave" di una fonte, senza
     * toccare quella dell'altra (vedi il commento in cima al file: leggi()
     * sostituiva la mappa INTERA, e la seconda fonte ad aggiornarsi cancellava
     * la prima).
     * @return vero se la fetta e' cambiata rispetto a prima
     */
    private fun sostituisciFonte(diQuestaFonte: (String) -> Boolean, nuove: Map<String, Offerta>): Boolean {
        val vecchie = perChiave.filterKeys(diQuestaFonte)
        val diverso = vecchie != nuove
        perChiave.keys.removeAll(diQuestaFonte)
        perChiave.putAll(nuove)
        return diverso
    }

    private fun sostituisciFonteVinted(nuove: Map<String, Offerta>) =
        sostituisciFonte({ it.startsWith("v:") }, nuove)

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
    private fun leggiSubito(corpo: String): Boolean = try {
        val arr = JSONObject(corpo).optJSONArray("transactions")
        val nuove = HashMap<String, Offerta>()
        if (arr != null) for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            val id = idAnnuncio(t.optString("item_id")) ?: continue
            /*
             * buyer_price e' quello che paghi tu, item_price quello concordato
             * sull'oggetto: sulle proposte inviate coincidono, ma se un giorno
             * divergessero e' buyer_price la cifra che hai offerto.
             */
            val cent = t.optInt("buyer_price").takeIf { it > 0 } ?: t.optInt("item_price")
            if (cent <= 0) continue
            val info = t.optJSONObject("state_info")
            val attiva = info?.optBoolean("active", false) ?: false
            val etichetta = info?.optString("label").orEmpty()
                .ifBlank { t.optString("state") }
            /*
             * Sullo stesso annuncio possono esserci piu' transazioni nel tempo
             * (una proposta annullata, poi un'altra). Vince quella attiva; a
             * parita', l'ultima che si incontra — l'elenco arriva dalla piu'
             * recente alla piu' vecchia.
             */
            val ad = t.optJSONObject("ad_info")
            val imgBase = ad?.optJSONObject("image")?.optString("cdn_base_url")
            val gia = nuove[id]
            if (gia == null || (attiva && !gia.attiva)) {
                nuove[id] = Offerta(
                    centesimi = cent,
                    etichetta = etichetta,
                    attiva = attiva,
                    id = id,
                    titolo = ad?.optString("title").orEmpty(),
                    immagine = if (imgBase.isNullOrBlank()) null
                               else "$imgBase?rule=large-fixed-card-3x-auto",
                    prezzoAnnuncio = ad?.optInt("price") ?: 0,
                    quando = iso(t.optString("updated_at")),
                    stato = t.optString("state"),               // v1.9.0
                    fonte = "subito"
                )
            }
        }
        sostituisciFonte({ !it.startsWith("v:") }, nuove)
    } catch (t: Throwable) { false }

    /**
     * Da "ad<uuid>list657012105" al numero che compare anche nell'urn di ricerca.
     * Il taglio si fa sull'ULTIMO "list": l'uuid non lo contiene mai, ma farlo
     * sull'ultimo costa niente e toglie una classe intera di sorprese.
     */
    private fun idAnnuncio(itemId: String): String? {
        val i = itemId.lastIndexOf("list")
        if (i < 0) return null
        val coda = itemId.substring(i + 4)
        return coda.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    }

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
    @Synchronized
    fun recenti(quante: Int = Int.MAX_VALUE): List<Offerta> =
        perChiave.values
            .filter { !conclusa(it) }
            .sortedByDescending { it.quando }
            .take(quante)

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
    private fun conclusa(o: Offerta): Boolean = !o.attiva

    /** Da "2026-08-30T23:35:37.177455+02:00" ai millisecondi. 0 se illeggibile. */
    private fun iso(v: String): Long = try {
        if (v.isBlank()) 0L
        else java.time.OffsetDateTime.parse(v).toInstant().toEpochMilli()
    } catch (t: Throwable) { 0L }

    /** "170 €" per una cifra in centesimi. */
    fun euro(centesimi: Int): String {
        val e = centesimi / 100.0
        return (if (e == Math.floor(e)) e.toInt().toString()
                else String.format(java.util.Locale.ITALY, "%.2f", e)) + " €"
    }

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
    /** "offerti 170 €" — mai "offerti 170,00 €": la riga deve restare corta. */
    fun testo(o: Offerta): String {
        val euro = o.centesimi / 100.0
        val n = if (euro == Math.floor(euro)) euro.toInt().toString()
                else String.format(java.util.Locale.ITALY, "%.2f", euro)
        val base = "offerti $n €"
        return if (o.controproposta > 0) "$base · rilancio ${euro(o.controproposta)}" else base
    }

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
     *     } catch (t: Throwable) { /* la copia locale e' un comodo, non un dato */ }
     * }
     * --- fine versione precedente ---
     */
    private fun salva(ctx: Context) {
        try {
            val o = JSONObject()
            perChiave.forEach { (k, v) ->
                o.put(k, JSONObject()
                    .put("centesimi", v.centesimi)
                    .put("etichetta", v.etichetta)
                    .put("attiva", v.attiva)
                    .put("id", v.id)
                    .put("titolo", v.titolo)
                    .put("immagine", v.immagine ?: "")
                    .put("prezzoAnnuncio", v.prezzoAnnuncio)
                    .put("quando", v.quando)
                    .put("stato", v.stato)
                    .put("fonte", v.fonte)
                    .put("controproposta", v.controproposta))
            }
            File(ctx.filesDir, FILE).writeText(
                JSONObject().put("versione", "1.11.0").put("offerte", o).toString()
            )
        } catch (t: Throwable) { /* la copia locale e' un comodo, non un dato */ }
    }
}
