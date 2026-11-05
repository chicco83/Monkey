package it.monkey.app

/*
 * Subito+ — motore delle regole di blocco
 * v0.2.0 (2026-08-30) — nuovo in Fase 1
 *
 * Le regole NON sono compilate dentro l'app: stanno in un JSON.
 * All'avvio si carica quello importato dall'utente (filesDir), se assente
 * quello di serie (assets/regole.json). Cosi' un cambio nell'ecosistema
 * pubblicitario si risolve con un file, non con una nuova build.
 *
 * Ordine di valutazione, pensato per non rompere mai il sito:
 *   1. percorso in "bloccaPercorsi" -> blocca   (regola precisa, vale anche sui domini di casa)
 *   2. host in "consentiSempre"     -> passa    (subito.it, CDN immagini, consenso, antifrode, chat)
 *   3. host in "bloccaHost"         -> blocca
 *   4. altrimenti                   -> passa
 * In dubbio si lascia passare: un banner in piu' e' meno grave di una pagina rotta.
 *
 * v0.2.0 (2026-08-30) — l'ordine e' cambiato rispetto alla prima stesura:
 *   prima "consentiSempre" veniva valutato per primo, e questo rendeva impossibile
 *   fermare la raccolta analytics servita da un dominio subito.it (tags.subito.it/g/collect).
 *   Sezione precedente conservata nel metodo blocca().
 */

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Regole {

    private const val FILE_UTENTE = "regole.json"

    @Volatile private var bloccaHost: List<String> = emptyList()
    @Volatile private var bloccaPercorsi: List<String> = emptyList()
    @Volatile private var consentiSempre: List<String> = emptyList()
    @Volatile var cssNascondi: List<String> = emptyList(); private set
    @Volatile var versioneRegole: String = "-"; private set
    @Volatile var origine: String = "-"; private set

    /* v0.3.0 (2026-08-30) — preferenze di navigazione, anch'esse nel file regole. */
    @Volatile var sezioneIniziale: String = ""; private set
    @Volatile var cercaApreForm: Boolean = false; private set

    /** Contatore delle risorse effettivamente bloccate, mostrato nella barra di stato. */
    @Volatile var bloccate: Int = 0

    fun carica(ctx: Context) {
        val f = File(ctx.filesDir, FILE_UTENTE)
        val testo = if (f.exists()) {
            origine = "importate"; f.readText()
        } else {
            origine = "di serie"
            ctx.assets.open("regole.json").bufferedReader().use { it.readText() }
        }
        applica(testo)
    }

    /** Usata da "Importa regole": valida, salva e attiva subito. */
    fun importa(ctx: Context, testo: String): Boolean = try {
        applica(testo)                                   // se il JSON e' rotto lancia qui
        File(ctx.filesDir, FILE_UTENTE).writeText(testo) // salva solo se valido
        origine = "importate"
        true
    } catch (t: Throwable) { false }

    fun ripristinaDiSerie(ctx: Context) {
        File(ctx.filesDir, FILE_UTENTE).delete()
        carica(ctx)
    }

    private fun applica(testo: String) {
        val j = JSONObject(testo)
        bloccaHost = lista(j.optJSONArray("bloccaHost"))
        bloccaPercorsi = lista(j.optJSONArray("bloccaPercorsi"))
        consentiSempre = lista(j.optJSONArray("consentiSempre"))
        cssNascondi = lista(j.optJSONArray("cssNascondi"))
        versioneRegole = j.optString("versione", "-")

        val ui = j.optJSONObject("interfaccia")
        sezioneIniziale = ui?.optString("sezioneIniziale", "") ?: ""
        cercaApreForm = ui?.optBoolean("cercaApreForm", false) ?: false
    }

    private fun lista(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { a.optString(it).lowercase().trim() }.filter { it.isNotEmpty() }
    }

    /** Vero se l'URL va fermato. Gira sul thread di rete del WebView: deve restare O(n) su liste corte. */
    fun blocca(url: String): Boolean {
        val u = url.lowercase()
        val host = try { java.net.URI(url).host?.lowercase() } catch (t: Throwable) { null } ?: return false

        // --- versione precedente (2026-08-30, v0.1.0 di questo file) ---
        // if (consentiSempre.any { host == it || host.endsWith(".${'$'}it") }) return false
        // if (bloccaHost.any { host == it || host.endsWith(".${'$'}it") }) return true
        // if (bloccaPercorsi.any { u.contains(it) }) return true
        // return false
        // --- fine versione precedente ---

        if (bloccaPercorsi.any { u.contains(it) }) return true
        if (consentiSempre.any { host == it || host.endsWith(".$it") }) return false
        if (bloccaHost.any { host == it || host.endsWith(".$it") }) return true
        return false
    }
}
