package it.monkey.app

/*
 * Subito+ — luogo degli annunci Vinted
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
            val o = JSONObject(f.readText()).optJSONObject("luoghi") ?: return
            o.keys().forEach { k -> noti[k] = o.optString(k) }
        } catch (t: Throwable) { /* file rovinato: si riparte da vuoto */ }
    }

    /** Luogo gia' noto, oppure null se non e' ancora stato chiesto. */
    @Synchronized
    fun luogo(venditore: String): String? = noti[venditore]

    @Synchronized
    fun quanti() = noti.size

    /**
     * Mette in coda i venditori ancora sconosciuti. Non blocca nulla: i risultati
     * arrivano uno alla volta e la riga si aggiorna quando il suo luogo e' pronto.
     *
     * @param aggiornato invocato sul thread principale a ogni luogo risolto
     */
    @Synchronized
    fun richiedi(
        ctx: Context,
        motore: MotoreRicerca,
        venditori: Collection<String>,
        aggiornato: (venditore: String, luogo: String) -> Unit
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
        aggiornato: (String, String) -> Unit
    ) {
        val v = coda.removeFirstOrNull() ?: return
        inVolo.add(v)
        attivi++
        // La chiamata parte da dentro la pagina Vinted, con i cookie dell'utente:
        // e' la stessa strada della ricerca e dei preferiti (vedi MotoreRicerca).
        motore.azione(FonteVinted, Chiamata("GET", "/api/v2/users/$v")) { stato, corpo ->
            val luogo = if (stato in 200..299) leggiLuogo(corpo) else null
            concluso(ctx, motore, v, luogo, aggiornato)
        }
    }

    @Synchronized
    private fun concluso(
        ctx: Context,
        motore: MotoreRicerca,
        venditore: String,
        luogo: String?,
        aggiornato: (String, String) -> Unit
    ) {
        inVolo.remove(venditore)
        attivi--
        if (luogo != null) {
            noti[venditore] = luogo
            falliti.remove(venditore)
            daSalvare = true
            if (luogo.isNotBlank()) ui.post { aggiornato(venditore, luogo) }
        } else {
            // Errore di rete o rifiuto: non si registra nulla, cosi' la prossima
            // ricerca riprova. Ma non subito, per non insistere su una porta chiusa.
            falliti.add(venditore)
        }
        salvaFraPoco(ctx)
        if (coda.isNotEmpty()) ui.postDelayed({ avviaProssimo(ctx, motore, aggiornato) }, PAUSA_MS)
    }

    /**
     * Dal profilo al testo da mostrare.
     * expose_location vero -> la citta' ("Milano", "Bouguenais").
     * expose_location falso -> resta il paese ("Francia"), che c'e' sempre.
     */
    private fun leggiLuogo(corpo: String): String? = try {
        val o = JSONObject(corpo)
        val u = o.optJSONObject("user") ?: o
        val citta = u.optString("city").trim().takeIf { it.isNotEmpty() && it != "null" }
        val paese = u.optString("country_title_local").trim()
            .ifEmpty { u.optString("country_title").trim() }
            .takeIf { it.isNotEmpty() && it != "null" }
        citta ?: paese ?: ""
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

    @Synchronized
    private fun salva() {
        val ctx = contesto ?: return
        daSalvare = false
        try {
            val o = JSONObject()
            noti.forEach { (k, v) -> o.put(k, v) }
            File(ctx.filesDir, FILE).writeText(
                JSONObject().put("versione", "1.2.0").put("luoghi", o).toString()
            )
        } catch (t: Throwable) { /* la cache e' un lusso, non un dato */ }
    }

    @Synchronized
    fun svuota(ctx: Context) {
        noti.clear(); falliti.clear(); coda.clear()
        try { File(ctx.filesDir, FILE).delete() } catch (t: Throwable) {}
    }
}
