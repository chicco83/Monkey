package it.monkey.app

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

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object StoricoRicerche {

    private const val FILE = "storico.json"
    private const val TETTO_ID = 400        // per ricerca
    private const val TETTO_RICERCHE = 40   // ricerche ricordate

    private val storico = LinkedHashMap<String, LinkedHashSet<String>>()
    private var caricato = false

    private fun chiave(query: String) = query.trim().lowercase()

    @Synchronized
    fun carica(ctx: Context) {
        if (caricato) return
        caricato = true
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return
        try {
            val j = JSONObject(f.readText()).optJSONObject("ricerche") ?: return
            j.keys().forEach { k ->
                val arr = j.optJSONArray(k) ?: return@forEach
                val ids = LinkedHashSet<String>()
                for (i in 0 until arr.length()) ids.add(arr.optString(i))
                storico[k] = ids
            }
        } catch (t: Throwable) { /* file rovinato: si riparte da vuoto */ }
    }

    /** Cosa c'era l'ultima volta che questa ricerca e' stata eseguita. */
    @Synchronized
    fun precedenti(query: String): Set<String> = storico[chiave(query)] ?: emptySet()

    @Synchronized
    fun conosce(query: String): Boolean = storico.containsKey(chiave(query))

    /** Sostituisce l'istantanea: da adesso "la volta prima" e' questa. */
    @Synchronized
    fun registra(ctx: Context, query: String, ids: Collection<String>) {
        if (query.isBlank() || ids.isEmpty()) return
        val k = chiave(query)
        storico.remove(k)                       // rientra in fondo: le vecchie cadono prima
        storico[k] = LinkedHashSet(ids.take(TETTO_ID))
        while (storico.size > TETTO_RICERCHE) {
            val piuVecchia = storico.keys.firstOrNull() ?: break
            storico.remove(piuVecchia)
        }
        salva(ctx)
    }

    @Synchronized
    fun dimentica(ctx: Context, query: String) {
        storico.remove(chiave(query))
        salva(ctx)
    }

    private fun salva(ctx: Context) {
        try {
            val j = JSONObject()
            storico.forEach { (k, v) -> j.put(k, JSONArray(v.toList())) }
            File(ctx.filesDir, FILE).writeText(
                JSONObject().put("versione", "1.1.0").put("ricerche", j).toString()
            )
        } catch (t: Throwable) {}
    }
}
