package it.monkey.app

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

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File

object RecensioniSubito {

    private const val FILE = "recensioni_subito.json"
    private const val PARALLELE = 2
    private const val PAUSA_MS = 250L
    private const val CODA_MAX = 120

    private val ui = Handler(Looper.getMainLooper())

    /** venditore (user_id Subito) -> numero di recensioni gia' noto. */
    private val noti = HashMap<String, Int>()
    private val falliti = HashSet<String>()

    private val coda = ArrayDeque<String>()
    private val inVolo = HashSet<String>()
    private var attivi = 0
    private var caricato = false
    private var daSalvare = false

    // ------------------------------------------------------------------
    @Synchronized
    fun carica(ctx: Context) {
        if (caricato) return
        caricato = true
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return
        try {
            val o = JSONObject(f.readText()).optJSONObject("recensioni") ?: return
            o.keys().forEach { k -> noti[k] = o.optInt(k, 0) }
        } catch (t: Throwable) { /* file rovinato: si riparte da vuoto */ }
    }

    /** Recensioni gia' note, oppure null se non ancora chieste. */
    @Synchronized
    fun recensioni(venditore: String): Int? = noti[venditore]

    @Synchronized
    fun quanti() = noti.size

    /**
     * Mette in coda i venditori ancora sconosciuti. Non blocca nulla: i
     * risultati arrivano uno alla volta e la riga si aggiorna quando la sua
     * risposta e' pronta — stessa forma di LuoghiVinted.richiedi().
     */
    @Synchronized
    fun richiedi(
        ctx: Context,
        motore: MotoreRicerca,
        venditori: Collection<String>,
        aggiornato: (venditore: String, recensioni: Int) -> Unit
    ) {
        carica(ctx)
        venditori.forEach { v ->
            if (v.isBlank()) return@forEach
            if (noti.containsKey(v) || inVolo.contains(v) || coda.contains(v)) return@forEach
            if (coda.size >= CODA_MAX) return@forEach
            coda.addLast(v)
        }
        while (attivi < PARALLELE && coda.isNotEmpty()) avviaProssimo(ctx, motore, aggiornato)
    }

    @Synchronized
    private fun avviaProssimo(
        ctx: Context,
        motore: MotoreRicerca,
        aggiornato: (String, Int) -> Unit
    ) {
        val v = coda.removeFirstOrNull() ?: return
        inVolo.add(v)
        attivi++
        // Stessa strada delle transazioni P2P (vedi Offerte.kt): URL assoluto
        // su hades.subito.it, chiamato da dentro la sessione Subito.
        val url = "https://hades.subito.it/v1/trust/feedback/public/users/" +
                "sdrn:subito:user:$v/feedback?limit=1&page=0&sources=MEMBER"
        motore.azione(FonteSubito, Chiamata("GET", url)) { stato, corpo ->
            val n = if (stato in 200..299) leggiConteggio(corpo) else null
            concluso(ctx, motore, v, n, aggiornato)
        }
    }

    @Synchronized
    private fun concluso(
        ctx: Context,
        motore: MotoreRicerca,
        venditore: String,
        recensioni: Int?,
        aggiornato: (String, Int) -> Unit
    ) {
        inVolo.remove(venditore)
        attivi--
        if (recensioni != null) {
            noti[venditore] = recensioni
            falliti.remove(venditore)
            daSalvare = true
            ui.post { aggiornato(venditore, recensioni) }
        } else {
            // Errore di rete o rifiuto: non si registra nulla, si riprova al
            // prossimo giro (stessa cautela di LuoghiVinted).
            falliti.add(venditore)
        }
        salvaFraPoco(ctx)
        if (coda.isNotEmpty()) ui.postDelayed({ avviaProssimo(ctx, motore, aggiornato) }, PAUSA_MS)
    }

    /** "pagination.totalCount" della risposta: il numero di recensioni. */
    private fun leggiConteggio(corpo: String): Int? = try {
        val o = JSONObject(corpo)
        val p = o.optJSONObject("pagination")
        if (p != null && p.has("totalCount")) p.optInt("totalCount", 0) else null
    } catch (t: Throwable) { null }

    // ------------------------------------------------------------------
    private fun salvaFraPoco(ctx: Context) {
        if (!daSalvare) return
        ui.removeCallbacks(salvataggio)
        contesto = ctx.applicationContext
        ui.postDelayed(salvataggio, 1500)
    }

    private var contesto: Context? = null
    private val salvataggio = Runnable { salva() }

    @Synchronized
    private fun salva() {
        val ctx = contesto ?: return
        daSalvare = false
        try {
            val o = JSONObject()
            noti.forEach { (k, v) -> o.put(k, v) }
            File(ctx.filesDir, FILE).writeText(
                JSONObject().put("versione", "1.30.0").put("recensioni", o).toString()
            )
        } catch (t: Throwable) { /* la cache e' un lusso, non un dato */ }
    }

    @Synchronized
    fun svuota(ctx: Context) {
        noti.clear(); falliti.clear(); coda.clear()
        try { File(ctx.filesDir, FILE).delete() } catch (t: Throwable) {}
    }
}
