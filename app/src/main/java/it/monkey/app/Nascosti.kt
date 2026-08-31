package it.monkey.app

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

import android.content.Context
import org.json.JSONObject
import java.io.File

object Nascosti {

    private const val FILE = "nascosti.json"
    private val mappa = LinkedHashMap<String, Pair<Long, String>>()
    private var caricato = false

    @Synchronized
    fun carica(ctx: Context) {
        if (caricato) return
        val f = File(ctx.filesDir, FILE)
        if (f.exists()) {
            try {
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
                val radice = JSONObject(f.readText())
                val annunci = radice.optJSONObject("annunci") ?: radice
                annunci.keys().forEach { k ->
                    val o = annunci.optJSONObject(k) ?: return@forEach
                    mappa[k] = Pair(o.optLong("t"), o.optString("n"))
                }
            } catch (t: Throwable) { /* file corrotto: si riparte da vuoto */ }
        }
        caricato = true
    }

    @Synchronized fun quanti(): Int = mappa.size

    /** v0.4.0 — usato dalla ricerca unificata (chiavi "id" per Subito, "v:id" per Vinted). */
    @Synchronized fun contiene(chiave: String): Boolean = mappa.containsKey(chiave)

    @Synchronized
    fun aggiungi(ctx: Context, id: String, titolo: String) {
        if (id.isBlank()) return
        mappa[id] = Pair(System.currentTimeMillis(), titolo.take(90))
        salva(ctx)
    }

    @Synchronized
    fun rimuovi(ctx: Context, id: String) { mappa.remove(id); salva(ctx) }

    @Synchronized
    fun svuota(ctx: Context) { mappa.clear(); salva(ctx) }

    /** Elenco per la schermata di ripristino, dal piu' recente. */
    @Synchronized
    fun elenco(): List<Triple<String, Long, String>> =
        mappa.entries.map { Triple(it.key, it.value.first, it.value.second) }
            .sortedByDescending { it.second }

    /** Consumato dallo script della pagina: array JSON di soli ID. */
    @Synchronized
    fun idJson(): String = mappa.keys.joinToString(",", "[", "]") { "\"$it\"" }

    @Synchronized
    fun esportaJson(): String {
        val j = JSONObject()
        mappa.forEach { (k, v) -> j.put(k, JSONObject().put("t", v.first).put("n", v.second)) }
        return JSONObject().put("versione", "0.3.0").put("quanti", mappa.size)
            .put("annunci", j).toString(2)
    }

    /** Import additivo: unisce, non sostituisce. Serve per travasare da un telefono all'altro. */
    @Synchronized
    fun importa(ctx: Context, testo: String): Int = try {
        val root = JSONObject(testo)
        val j = root.optJSONObject("annunci") ?: root
        var n = 0
        j.keys().forEach { k ->
            if (!mappa.containsKey(k)) {
                val o = j.optJSONObject(k)
                mappa[k] = Pair(o?.optLong("t") ?: System.currentTimeMillis(), o?.optString("n") ?: "")
                n++
            }
        }
        salva(ctx); n
    } catch (t: Throwable) { -1 }

    private fun salva(ctx: Context) {
        try { File(ctx.filesDir, FILE).writeText(esportaJson()) } catch (t: Throwable) {}
    }
}
