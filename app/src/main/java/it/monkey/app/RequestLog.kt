package it.monkey.app

/*
 * Subito+ / Fase 0 "Sonda" — registro delle richieste di rete
 * v0.1.0 (2026-08-30)
 *
 * Scopo: la sonda NON blocca nulla. Osserva ogni risorsa che il WebView chiede,
 * la raggruppa per host e propone una blocklist basata su euristica.
 * L'output rivisto a mano diventa la blocklist definitiva della Fase 1.
 */

import org.json.JSONArray
import org.json.JSONObject

object RequestLog {

    /*
     * Host che consideriamo "casa".
     * v0.2.0 (2026-08-30): aggiunti dopo l'export reale — sbito.it e' il CDN delle
     * immagini degli annunci (85 richieste in una sessione) e nella v0.1.0 finiva
     * per errore tra le terze parti da valutare.
     *
     * --- versione precedente (v0.1.0, 2026-08-30) ---
     * private val FIRST_PARTY = listOf("subito.it", "subito.shop", "subitostatic.it")
     * --- fine versione precedente ---
     */
    private val FIRST_PARTY = listOf(
        "subito.it", "subito.shop", "subitostatic.it", "sbito.it"
    )

    /*
     * Frammenti di dominio noti dell'ecosistema adv/tracking.
     * Lista di partenza: serve solo a pre-marcare i sospetti nel report,
     * NON è ancora la blocklist. Quella la si conferma sui dati reali.
     */
    private val AD_HINTS = listOf(
        "doubleclick", "googlesyndication", "googletagservices", "googletagmanager",
        "google-analytics", "googleadservices", "adservice.google", "2mdn.net",
        "adnxs", "criteo", "taboola", "outbrain", "pubmatic", "rubiconproject",
        "casalemedia", "smartadserver", "teads", "amazon-adsystem", "prebid",
        "openx", "indexww", "yieldmo", "33across", "sharethrough", "id5-sync",
        "adform", "improvedigital", "liveramp", "permutive", "quantserve",
        "scorecardresearch", "adsafeprotected", "moatads", "omnitagjs",
        "connect.facebook.net", "facebook.com/tr", "hotjar", "clarity.ms",
        "branch.io", "appsflyer", "adjust.com", "onesignal", "bidswitch",
        "creativecdn", "sirdata", "weborama", "audiencerate", "the-ozone-project"
    )

    /*
     * Consent Management Platform e affini: NON vanno bloccati alla cieca
     * (senza il banner cookie il sito può restare in stato di attesa).
     * Li marchiamo a parte per deciderlo con prova alla mano in Fase 1.
     */
    private val CMP_HINTS = listOf("didomi", "onetrust", "sourcepoint", "cookielaw", "consensu.org")

    private data class Entry(
        var count: Int = 0,
        var sample: String = "",
        val host: String
    )

    private val entries = HashMap<String, Entry>()

    /*
     * v0.4.6 — il registro dettagliato serve alla diagnostica, non all'uso normale.
     * Tenerlo acceso significava prendere un lock e scrivere in una mappa per OGNI
     * risorsa richiesta dalla pagina, immagini comprese, sul filo di rete.
     * Ora di serie si contano soltanto le richieste; il dettaglio si accende
     * insieme alla sonda.
     */
    @Volatile var dettaglio: Boolean = false
    private val totale = java.util.concurrent.atomic.AtomicInteger(0)

    /** Chiamata dal thread di rete del WebView: deve essere sincronizzata e leggerissima. */
    /** Chiamata dal thread di rete del WebView per ogni risorsa: deve costare quasi nulla. */
    fun record(url: String) {
        totale.incrementAndGet()
        if (dettaglio) dettaglia(url)
    }

    @Synchronized
    private fun dettaglia(url: String) {
        val host = hostOf(url) ?: return
        val e = entries.getOrPut(host) { Entry(host = host) }
        e.count++
        if (e.sample.isEmpty()) e.sample = url.take(300)
    }

    @Synchronized fun reset() { entries.clear(); totale.set(0) }

    fun totalRequests(): Int = totale.get()
    @Synchronized fun totalHosts(): Int = entries.size
    @Synchronized fun suspectHosts(): Int = entries.keys.count { isSuspect(it) }

    private fun hostOf(url: String): String? = try {
        java.net.URI(url).host?.lowercase()
    } catch (t: Throwable) { null }

    fun isFirstParty(host: String): Boolean = FIRST_PARTY.any { host == it || host.endsWith(".$it") }
    fun isCmp(host: String): Boolean = CMP_HINTS.any { host.contains(it) }
    fun isSuspect(host: String): Boolean =
        !isFirstParty(host) && !isCmp(host) && AD_HINTS.any { host.contains(it) }

    /** Report completo: ogni host visto, con quante volte e come lo abbiamo classificato. */
    @Synchronized
    fun requestsJson(): String {
        val arr = JSONArray()
        entries.values.sortedByDescending { it.count }.forEach { e ->
            arr.put(JSONObject().apply {
                put("host", e.host)
                put("count", e.count)
                put("firstParty", isFirstParty(e.host))
                put("cmp", isCmp(e.host))
                put("suspect", isSuspect(e.host))
                // v0.2.0: verdetto vero delle regole attive, molto piu' utile dell'euristica
                put("bloccatoDalleRegole", Regole.blocca(e.sample))
                put("sample", e.sample)
            })
        }
        return JSONObject().apply {
            put("versione", "0.1.0")
            put("generato", System.currentTimeMillis())
            put("hostTotali", entries.size)
            put("richiesteTotali", entries.values.sumOf { it.count })
            put("host", arr)
        }.toString(2)
    }

    /** Blocklist proposta: solo i sospetti + i terze parti mai first-party, da rivedere a mano. */
    @Synchronized
    fun blocklistJson(): String {
        val blocca = JSONArray()
        val daValutare = JSONArray()
        entries.keys.sorted().forEach { h ->
            when {
                isSuspect(h) -> blocca.put(h)
                !isFirstParty(h) && !isCmp(h) -> daValutare.put(h)
            }
        }
        return JSONObject().apply {
            put("versione", "0.1.0")
            put("nota", "blocca = certi; daValutare = terze parti non riconosciute, da confermare a mano prima della Fase 1")
            put("blocca", blocca)
            put("daValutare", daValutare)
        }.toString(2)
    }
}
