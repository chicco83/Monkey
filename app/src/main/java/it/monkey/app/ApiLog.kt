package it.monkey.app

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

import org.json.JSONArray
import org.json.JSONObject

object ApiLog {

    private const val MAX_ENDPOINT = 80
    // v1.0.6: alzato da 2500. Un elenco di conversazioni o di offerte veniva
    // troncato proprio dove comincia a essere interessante.
    private const val MAX_CORPO = 6000
    private const val MAX_INVIO = 1200

    private data class Voce(
        val endpoint: String,
        var esempioUrl: String,
        var metodo: String,
        var invio: String,
        var stato: Int,
        var corpo: String,
        var quante: Int = 1
    )

    private val voci = LinkedHashMap<String, Voce>()

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
    @Synchronized
    fun registra(url: String, metodo: String, invio: String, stato: Int, corpo: String) {
        val chiave = try {
            val u = java.net.URI(url)
            (metodo.uppercase() + " " + (u.host ?: "?") + (u.path ?: "")).trim()
        } catch (t: Throwable) { url.take(120) }

        val v = voci[chiave]
        if (v != null) {
            v.quante++
            // si tiene il campione piu' ricco, di solito il piu' informativo
            if (corpo.length > v.corpo.length) { v.corpo = corpo.take(MAX_CORPO); v.stato = stato }
            if (invio.length > v.invio.length) v.invio = invio.take(MAX_INVIO)
            return
        }
        if (voci.size >= MAX_ENDPOINT) return
        voci[chiave] = Voce(
            chiave, url.take(400), metodo.uppercase(),
            invio.take(MAX_INVIO), stato, corpo.take(MAX_CORPO)
        )
    }

    @Synchronized fun quanti(): Int = voci.size
    @Synchronized fun reset() = voci.clear()

    @Synchronized
    fun json(): String {
        val arr = JSONArray()
        voci.values.sortedByDescending { it.quante }.forEach { v ->
            arr.put(JSONObject().apply {
                put("endpoint", v.endpoint)
                put("metodo", v.metodo)
                put("quante", v.quante)
                put("stato", v.stato)
                put("esempioUrl", v.esempioUrl)
                put("invio", v.invio)
                put("corpo", v.corpo)
            })
        }
        return JSONObject().apply {
            put("versione", "0.3.1")
            put("generato", System.currentTimeMillis())
            put("endpointCampionati", voci.size)
            put("api", arr)
        }.toString(2)
    }
}
