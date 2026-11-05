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
 *
 * PERCHE' SOLO SUBITO
 * Su Vinted /api/v2/my_orders?type=purchase risponde 200 ma porta
 * conversation_id, transaction_id, title, price — e NESSUN id dell'articolo.
 * Senza quello non c'e' niente da incrociare, e incrociare per titolo su un
 * mercato dell'usato significa sbagliare riga prima o poi. /api/v2/conversations
 * e /api/v2/offers rispondono 404. Quando si trovera' l'elenco giusto, entrera'
 * da qui: la mappa e la riga rossa sono gia' pronte per due fonti.
 */

import android.content.Context
import org.json.JSONObject
import java.io.File

object Offerte {

    private const val FILE = "offerte.json"

    /** Non piu' di una lettura ogni cinque minuti: e' un elenco, non un flusso. */
    private const val VALIDITA_MS = 5 * 60_000L

    /**
     * @param centesimi quanto hai proposto
     * @param etichetta lo stato come lo scrive la piattaforma ("Proposta inviata")
     * @param attiva    false per una proposta chiusa, annullata o scaduta
     *
     * v1.8.0 — il resto serve a mostrarle in home senza chiedere altro a
     * nessuno: la risposta delle transazioni porta gia' ad_info con titolo,
     * foto e prezzo dell'annuncio, e updated_at. Tutto quello che serve a
     * disegnare una riga e' li' dentro.
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
        val stato: String = ""
    ) {
        /** Verificato sul campo: /vi/<id>.htm apre la scheda giusta. */
        val url: String get() = "https://www.subito.it/vi/$id.htm"
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
                    stato = v.optString("stato")
                )
            }
        } catch (t: Throwable) { /* file rovinato: si riparte da vuoto */ }
    }

    @Synchronized
    fun perAnnuncio(a: Annuncio): Offerta? =
        if (a.fonte == "subito") perChiave[a.chiave] else null

    @Synchronized fun quante() = perChiave.count { it.value.attiva }

    /**
     * Rilegge l'elenco dalla piattaforma. Una chiamata sola per tutta la lista:
     * non c'e' niente da chiedere annuncio per annuncio.
     *
     * @param forza ignora la validita' e rilegge comunque
     * @param fatto invocato a lettura riuscita, per ridisegnare le righe
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
        }
        motore.azione(
            FonteSubito,
            Chiamata("GET", "https://hades.subito.it/v1/p2p/transactions?role=buyer")
        ) { stato, corpo ->
            var cambiato = false
            synchronized(this) {
                inCorso = false
                if (stato in 200..299) {
                    cambiato = leggi(corpo)
                    ultimaLettura = System.currentTimeMillis()
                    if (cambiato) salva(ctx)
                }
            }
            if (cambiato) fatto()
        }
    }

    /** @return vero se qualcosa e' cambiato rispetto a prima */
    private fun leggi(corpo: String): Boolean = try {
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
                    stato = t.optString("state")               // v1.9.0
                )
            }
        }
        val diverso = nuove != perChiave
        perChiave.clear()
        perChiave.putAll(nuove)
        diverso
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
    fun recenti(quante: Int = 30): List<Offerta> =
        perChiave.values
            .filter { !conclusa(it) }
            .sortedByDescending { it.quando }
            .take(quante)

    private fun conclusa(o: Offerta): Boolean {
        val t = (o.etichetta + " " + o.stato).lowercase()
        return t.contains("conclus") || t.contains("closed")
    }

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

    /** "offerti 170 €" — mai "offerti 170,00 €": la riga deve restare corta. */
    fun testo(o: Offerta): String {
        val euro = o.centesimi / 100.0
        val n = if (euro == Math.floor(euro)) euro.toInt().toString()
                else String.format(java.util.Locale.ITALY, "%.2f", euro)
        return "offerti $n €"
    }

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
                    .put("stato", v.stato))
            }
            File(ctx.filesDir, FILE).writeText(
                JSONObject().put("versione", "1.9.0").put("offerte", o).toString()
            )
        } catch (t: Throwable) { /* la copia locale e' un comodo, non un dato */ }
    }
}
