package it.monkey.app

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

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File

object LuoghiVinted {

    private const val FILE = "luoghi_vinted.json"

    /** Quante richieste possono essere in volo insieme. Due: non e' una corsa. */
    private const val PARALLELE = 2

    /** Pausa fra una richiesta e la successiva dello stesso lavoratore. */
    private const val PAUSA_MS = 250L

    /** Tetto della coda: oltre, si rinuncia invece di accumulare lavoro inutile. */
    private const val CODA_MAX = 120

    private val ui = Handler(Looper.getMainLooper())

    /** venditore -> luogo gia' noto (anche "" quando la piattaforma non lo espone). */
    private val noti = HashMap<String, String>()

    /*
     * v1.29.0 — venditore -> numero di recensioni gia' noto. Mappa separata
     * (non un solo oggetto con i due campi) cosi' un venditore con SOLO il
     * luogo in cache dalla v1.2.0 non deve perderlo per aggiungere l'altro:
     * le due informazioni si riempiono in modo indipendente, la stessa
     * chiave le tiene insieme.
     */
    private val recensioniNote = HashMap<String, Int>()

    /** Venditori per cui la chiamata e' fallita: si riprovera' al prossimo avvio. */
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
            val root = JSONObject(f.readText())
            root.optJSONObject("luoghi")?.let { o -> o.keys().forEach { k -> noti[k] = o.optString(k) } }
            // v1.29.0 — chiave nuova nello stesso file: assente nei file scritti
            // prima di questa versione, e va bene cosi' (optJSONObject torna null).
            root.optJSONObject("recensioni")?.let { o ->
                o.keys().forEach { k -> recensioniNote[k] = o.optInt(k, -1).takeIf { it >= 0 } ?: return@forEach }
            }
        } catch (t: Throwable) { /* file rovinato: si riparte da vuoto */ }
    }

    /** Luogo gia' noto, oppure null se non e' ancora stato chiesto. */
    @Synchronized
    fun luogo(venditore: String): String? = noti[venditore]

    /** v1.29.0 — recensioni gia' note, oppure null se non ancora chieste (o non lette). */
    @Synchronized
    fun recensioni(venditore: String): Int? = recensioniNote[venditore]

    @Synchronized
    fun quanti() = noti.size

    /**
     * Mette in coda i venditori ancora sconosciuti. Non blocca nulla: i risultati
     * arrivano uno alla volta e la riga si aggiorna quando il suo luogo e' pronto.
     *
     * @param aggiornato invocato sul thread principale a ogni luogo risolto
     */
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
    @Synchronized
    fun richiedi(
        ctx: Context,
        motore: MotoreRicerca,
        venditori: Collection<String>,
        aggiornato: (venditore: String, luogo: String, recensioni: Int?) -> Unit
    ) {
        carica(ctx)
        venditori.forEach { v ->
            if (v.isBlank()) return@forEach
            val giaCompleto = noti.containsKey(v) && recensioniNote.containsKey(v)
            if (giaCompleto || inVolo.contains(v) || coda.contains(v)) return@forEach
            if (coda.size >= CODA_MAX) return@forEach
            coda.addLast(v)
        }
        while (attivi < PARALLELE && coda.isNotEmpty()) avviaProssimo(ctx, motore, aggiornato)
    }

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
    @Synchronized
    private fun avviaProssimo(
        ctx: Context,
        motore: MotoreRicerca,
        aggiornato: (String, String, Int?) -> Unit
    ) {
        val v = coda.removeFirstOrNull() ?: return
        inVolo.add(v)
        attivi++
        // La chiamata parte da dentro la pagina Vinted, con i cookie dell'utente:
        // e' la stessa strada della ricerca e dei preferiti (vedi MotoreRicerca).
        motore.azione(FonteVinted, Chiamata("GET", "/api/v2/users/$v")) { stato, corpo ->
            val info = if (stato in 200..299) leggiInfo(corpo) else null
            concluso(ctx, motore, v, info?.luogo, info?.recensioni, aggiornato)
        }
    }

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
    @Synchronized
    private fun concluso(
        ctx: Context,
        motore: MotoreRicerca,
        venditore: String,
        luogo: String?,
        recensioni: Int?,
        aggiornato: (String, String, Int?) -> Unit
    ) {
        inVolo.remove(venditore)
        attivi--
        if (luogo != null || recensioni != null) {
            if (luogo != null) noti[venditore] = luogo
            if (recensioni != null) recensioniNote[venditore] = recensioni
            falliti.remove(venditore)
            daSalvare = true
            if (!luogo.isNullOrBlank() || recensioni != null) {
                ui.post { aggiornato(venditore, luogo.orEmpty(), recensioni) }
            }
        } else {
            // Errore di rete o rifiuto: non si registra nulla, cosi' la prossima
            // ricerca riprova. Ma non subito, per non insistere su una porta chiusa.
            falliti.add(venditore)
        }
        salvaFraPoco(ctx)
        if (coda.isNotEmpty()) ui.postDelayed({ avviaProssimo(ctx, motore, aggiornato) }, PAUSA_MS)
    }

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
    private class Info(val luogo: String?, val recensioni: Int?)

    private fun leggiInfo(corpo: String): Info? = try {
        val o = JSONObject(corpo)
        val u = o.optJSONObject("user") ?: o
        val citta = u.optString("city").trim().takeIf { it.isNotEmpty() && it != "null" }
        val paese = u.optString("country_title_local").trim()
            .ifEmpty { u.optString("country_title").trim() }
            .takeIf { it.isNotEmpty() && it != "null" }
        val luogo = citta ?: paese ?: ""
        // "feedback_count" verificato su /api/v2/users/current: stessa forma
        // di risposta di /api/v2/users/<id>, che e' l'endpoint chiamato qui.
        val recensioni = if (u.has("feedback_count")) u.optInt("feedback_count", -1)
            .takeIf { it >= 0 } else null
        Info(luogo, recensioni)
    } catch (t: Throwable) { null }

    // ------------------------------------------------------------------
    private fun salvaFraPoco(ctx: Context) {
        if (!daSalvare) return
        // Si scrive una volta sola a fine raffica, non a ogni risposta.
        ui.removeCallbacks(salvataggio)
        contesto = ctx.applicationContext
        ui.postDelayed(salvataggio, 1500)
    }

    private var contesto: Context? = null
    private val salvataggio = Runnable { salva() }

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
    @Synchronized
    private fun salva() {
        val ctx = contesto ?: return
        daSalvare = false
        try {
            val luoghi = JSONObject()
            noti.forEach { (k, v) -> luoghi.put(k, v) }
            val recensioni = JSONObject()
            recensioniNote.forEach { (k, v) -> recensioni.put(k, v) }
            File(ctx.filesDir, FILE).writeText(
                JSONObject()
                    .put("versione", "1.29.0")
                    .put("luoghi", luoghi)
                    .put("recensioni", recensioni)
                    .toString()
            )
        } catch (t: Throwable) { /* la cache e' un lusso, non un dato */ }
    }

    @Synchronized
    fun svuota(ctx: Context) {
        noti.clear(); recensioniNote.clear(); falliti.clear(); coda.clear()
        try { File(ctx.filesDir, FILE).delete() } catch (t: Throwable) {}
    }
}
