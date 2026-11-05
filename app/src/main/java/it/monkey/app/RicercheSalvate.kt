package it.monkey.app

/*
 * Subito+ — ricerche salvate
 * v0.6.0 (2026-08-30) — Fase 5
 *
 * Una ricerca salvata e' la domanda piu' cio' che si e' gia' visto: e' la
 * seconda meta' a rendere utile la prima. Senza memoria di quali annunci sono
 * gia' passati sotto gli occhi, un controllo periodico direbbe ogni volta le
 * stesse cose.
 *
 * Gli identificatori gia' visti si conservano fino a un tetto: oltre, i piu'
 * vecchi cadono. Un annuncio uscito dai risultati mesi fa e poi ricomparso e'
 * a tutti gli effetti una novita'.
 */

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RicercaSalvata(
    val id: String,
    val query: String,
    val subito: Boolean,
    val vinted: Boolean,
    val prezzoMin: String,
    val prezzoMax: String,
    val creata: Long,
    var ultimoControllo: Long,
    var nuoviDaVedere: Int,
    val visti: LinkedHashSet<String>
) {
    fun fonti(): List<Fonte> = buildList {
        if (subito) add(FonteSubito)
        if (vinted) add(FonteVinted)
    }

    fun descrizione(): String {
        val f = listOfNotNull(
            if (subito) "Subito" else null,
            if (vinted) "Vinted" else null
        ).joinToString(" + ").ifBlank { "nessuna fonte" }
        val p = when {
            prezzoMin.isBlank() && prezzoMax.isBlank() -> ""
            else -> " · € ${prezzoMin.ifBlank { "0" }}–${prezzoMax.ifBlank { "∞" }}"
        }
        return f + p
    }
}

object RicercheSalvate {

    private const val FILE = "ricerche.json"
    private const val TETTO_VISTI = 400

    private val elenco = ArrayList<RicercaSalvata>()
    private var caricato = false

    @Synchronized
    fun carica(ctx: Context) {
        if (caricato) return
        caricato = true
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return
        try {
            val arr = JSONObject(f.readText()).optJSONArray("ricerche") ?: return
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val visti = LinkedHashSet<String>()
                o.optJSONArray("visti")?.let { v ->
                    for (k in 0 until v.length()) visti.add(v.optString(k))
                }
                elenco.add(
                    RicercaSalvata(
                        id = o.optString("id"),
                        query = o.optString("query"),
                        subito = o.optBoolean("subito", true),
                        vinted = o.optBoolean("vinted", true),
                        prezzoMin = o.optString("prezzoMin"),
                        prezzoMax = o.optString("prezzoMax"),
                        creata = o.optLong("creata"),
                        ultimoControllo = o.optLong("ultimoControllo"),
                        nuoviDaVedere = o.optInt("nuoviDaVedere"),
                        visti = visti
                    )
                )
            }
        } catch (t: Throwable) { /* file rovinato: si riparte da vuoto */ }
    }

    @Synchronized fun tutte(): List<RicercaSalvata> = elenco.toList()
    @Synchronized fun quante(): Int = elenco.size
    @Synchronized fun nuoviTotali(): Int = elenco.sumOf { it.nuoviDaVedere }

    @Synchronized
    fun aggiungi(
        ctx: Context, query: String, subito: Boolean, vinted: Boolean,
        prezzoMin: String, prezzoMax: String, giaVisti: Collection<String>
    ): RicercaSalvata {
        val r = RicercaSalvata(
            id = System.currentTimeMillis().toString(36) + "-" + (0..999).random(),
            query = query, subito = subito, vinted = vinted,
            prezzoMin = prezzoMin, prezzoMax = prezzoMax,
            creata = System.currentTimeMillis(),
            ultimoControllo = System.currentTimeMillis(),
            nuoviDaVedere = 0,
            /*
             * I risultati gia' sotto gli occhi al momento del salvataggio non
             * sono novita': si parte dalla lista corrente come base.
             */
            visti = LinkedHashSet(giaVisti)
        )
        elenco.add(0, r)
        salva(ctx)
        return r
    }

    @Synchronized
    fun rimuovi(ctx: Context, id: String) {
        elenco.removeAll { it.id == id }
        salva(ctx)
    }

    @Synchronized
    fun segnaLetta(ctx: Context, id: String) {
        elenco.firstOrNull { it.id == id }?.nuoviDaVedere = 0
        salva(ctx)
    }

    /** @return quanti identificatori erano davvero nuovi. */
    @Synchronized
    fun registraEsito(ctx: Context, id: String, chiavi: List<String>): Int {
        val r = elenco.firstOrNull { it.id == id } ?: return 0
        var nuovi = 0
        chiavi.forEach { if (r.visti.add(it)) nuovi++ }
        while (r.visti.size > TETTO_VISTI) {
            val primo = r.visti.iterator()
            if (primo.hasNext()) { primo.next(); primo.remove() } else break
        }
        r.ultimoControllo = System.currentTimeMillis()
        r.nuoviDaVedere += nuovi
        salva(ctx)
        return nuovi
    }

    @Synchronized
    fun esportaJson(): String {
        val arr = JSONArray()
        elenco.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id); put("query", r.query)
                put("subito", r.subito); put("vinted", r.vinted)
                put("prezzoMin", r.prezzoMin); put("prezzoMax", r.prezzoMax)
                put("creata", r.creata); put("ultimoControllo", r.ultimoControllo)
                put("nuoviDaVedere", r.nuoviDaVedere)
                put("visti", JSONArray(r.visti.toList()))
            })
        }
        return JSONObject().put("versione", "0.6.0").put("ricerche", arr).toString(2)
    }

    /**
     * v1.0.0 — ripristino da backup, additivo: le ricerche gia' presenti
     * restano com'erano, comprese le loro memorie di cosa hanno gia' mostrato.
     * @return quante ne sono state aggiunte, -1 se il file non e' leggibile.
     */
    @Synchronized
    fun importa(ctx: Context, testo: String): Int = try {
        val arr = JSONObject(testo).optJSONArray("ricerche")
        var n = 0
        if (arr != null) for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (elenco.any { it.id == id }) continue
            val visti = LinkedHashSet<String>()
            o.optJSONArray("visti")?.let { v ->
                for (k in 0 until v.length()) visti.add(v.optString(k))
            }
            elenco.add(
                RicercaSalvata(
                    id = id, query = o.optString("query"),
                    subito = o.optBoolean("subito", true),
                    vinted = o.optBoolean("vinted", true),
                    prezzoMin = o.optString("prezzoMin"),
                    prezzoMax = o.optString("prezzoMax"),
                    creata = o.optLong("creata"),
                    ultimoControllo = o.optLong("ultimoControllo"),
                    nuoviDaVedere = o.optInt("nuoviDaVedere"),
                    visti = visti
                )
            )
            n++
        }
        salva(ctx); n
    } catch (t: Throwable) { -1 }

    private fun salva(ctx: Context) {
        try { File(ctx.filesDir, FILE).writeText(esportaJson()) } catch (t: Throwable) {}
    }
}
