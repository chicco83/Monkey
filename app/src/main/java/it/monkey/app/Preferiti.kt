package it.monkey.app

/*
 * Subito+ — preferiti
 * v1.2.0 (2026-08-30)
 *
 * Differenza importante rispetto agli annunci nascosti: i nascosti sono una
 * cosa NOSTRA, esistono solo qui. I preferiti invece esistono sulle due
 * piattaforme, e la verita' e' la' — se metti un cuore dal sito, l'app deve
 * accorgersene; se lo metti da qui, deve finire sul sito.
 *
 * Quindi questo archivio non e' la fonte di verita', e' una copia locale che
 * serve a due cose: disegnare il cuore subito, senza aspettare la rete, e
 * ricordarsi lo stato fra un avvio e l'altro. A ogni ricerca viene riallineato
 * con quello che dicono le piattaforme.
 */

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Preferiti {

    private const val FILE = "preferiti.json"

    private val chiavi = LinkedHashSet<String>()
    private var caricato = false

    @Synchronized
    fun carica(ctx: Context) {
        if (caricato) return
        caricato = true
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return
        try {
            val arr = JSONObject(f.readText()).optJSONArray("chiavi") ?: return
            for (i in 0 until arr.length()) chiavi.add(arr.optString(i))
        } catch (t: Throwable) { /* file rovinato: si riparte da vuoto */ }
    }

    @Synchronized fun contiene(chiave: String) = chiavi.contains(chiave)
    @Synchronized fun quanti() = chiavi.size

    @Synchronized
    fun imposta(ctx: Context, chiave: String, attivo: Boolean) {
        if (attivo) chiavi.add(chiave) else chiavi.remove(chiave)
        salva(ctx)
    }

    /**
     * Riallinea con la piattaforma. Si sostituisce lo stato solo per le chiavi
     * di quella fonte: l'altra non c'entra e non va toccata.
     *
     * @param prefisso "v:" per Vinted, "" per Subito (vedi Annuncio.chiave)
     */
    @Synchronized
    fun riallinea(ctx: Context, prefisso: String, veri: Collection<String>) {
        val diQuestaFonte = chiavi.filter {
            if (prefisso.isEmpty()) !it.startsWith("v:") else it.startsWith(prefisso)
        }
        chiavi.removeAll(diQuestaFonte.toSet())
        chiavi.addAll(veri)
        salva(ctx)
    }

    /** Allineamento parziale: quello che i risultati di ricerca dichiarano. */
    @Synchronized
    fun aggiorna(ctx: Context, dichiarati: Map<String, Boolean>) {
        var cambiato = false
        dichiarati.forEach { (k, v) ->
            if (v && chiavi.add(k)) cambiato = true
            if (!v && chiavi.remove(k)) cambiato = true
        }
        if (cambiato) salva(ctx)
    }

    @Synchronized
    fun esportaJson(): String =
        JSONObject().put("versione", "1.2.0")
            .put("chiavi", JSONArray(chiavi.toList())).toString(2)

    private fun salva(ctx: Context) {
        try { File(ctx.filesDir, FILE).writeText(esportaJson()) } catch (t: Throwable) {}
    }
}
