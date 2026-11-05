package it.monkey.app

/*
 * Monkey — data "Caricato" di un annuncio Vinted
 * v1.0.0 (2026-09-24) — nuovo, consegnato con l'app v1.59.0
 * v1.1.0 (2026-09-24) — app v1.60.0: lettura anche dalla PAGINA HTML
 *   dell'annuncio (leggiHtml), vedi il commento su leggiHtml().
 * v1.2.0 (2026-09-24) — app v1.62.0: pagina TRONCATA (scaricamento
 *   parziale), vedi senzaScript().
 * v1.3.0 (2026-09-28) — app v1.65.0: la forma VERA nel dettaglio, vista
 *   finalmente da collegati: un'unica stringa "Uploaded 13 min ago", con
 *   unita' abbreviate — vedi cercaPrefisso() e RE_EN.
 *
 * L'utente ha mostrato (screenshot della scheda Vinted di un annuncio) che
 * la data c'e': nella scheda compare il campo "Caricato  un'ora fa". Non
 * sta nella card dell'ELENCO (da cui l'app legge i risultati dalla v1.56.0,
 * vedi FonteVinted), ma nel DETTAGLIO dell'annuncio — lo stesso che l'app
 * gia' scarica per la descrizione (DescrizioniVinted, endpoint
 * "item-details/plugins/<id>/translatable"). Qui si legge quel campo dalla
 * stessa risposta: nessuna chiamata in piu'.
 *
 * FORMA NON ANCORA VISTA: la diagnostica di quell'endpoint si fermava ai
 * primi 1200 caratteri, dove c'era solo il plugin "summary". Non si
 * conosce quindi il punto esatto del JSON in cui sta "Caricato". Invece di
 * indovinare un percorso, si cerca nel JSON intero:
 *   1. il piu' PICCOLO oggetto che contiene la parola "Caricato" (o
 *      "Uploaded", se il testo arriva in inglese con localize=false) e,
 *      dopo di essa, un testo tipo "un'ora fa" / "3 giorni fa" / "2 hours
 *      ago": si prende quello;
 *   2. in mancanza, una chiave il cui nome contiene "upload" con dentro un
 *      numero (secondi/millisecondi) o una data ISO.
 * Quello che si trova (o il fatto che non si trova, con un pezzo del corpo
 * attorno alle parole cercate) va nella diagnostica esportata: se il
 * percorso vero e' diverso, la prossima versione lo legge per nome.
 *
 * PRECISIONE: "Caricato" e' un testo relativo, arrotondato da Vinted
 * (minuti, ore, giorni, settimane...). "precisioneMs" dice di quanto: la
 * stima dall'id (StimaDataVinted) usa come nuova ancora SOLO le letture
 * al minuto, le uniche piu' precise di lei; le altre servono solo a
 * mostrare la data vera sulla riga.
 */

import org.json.JSONArray
import org.json.JSONObject

object DataCaricamentoVinted {

    /*
     * --- versione precedente ---
     * const val VERSIONE = "1.0.0"
     * --- fine versione precedente ---
     */
    /*
     * --- versione precedente ---
     * const val VERSIONE = "1.1.0"
     * --- fine versione precedente ---
     */
    /*
     * --- versione precedente ---
     * const val VERSIONE = "1.2.0"
     * --- fine versione precedente ---
     */
    const val VERSIONE = "1.3.0"

    data class Esito(
        /** istante stimato di caricamento (ms) */
        val ms: Long,
        /** il testo trovato, cosi' com'e' ("un'ora fa"), o il nome del campo */
        val testo: String,
        /** larghezza dell'arrotondamento (ms): 60 000 per "5 minuti fa", ecc. */
        val precisioneMs: Long
    )

    private const val MIN = 60_000L
    private const val ORA = 60 * MIN
    private const val GIORNO = 24 * ORA

    private val ETICHETTE = setOf("caricato", "caricato il", "uploaded", "upload date", "pubblicato")

    /*
     * Testo relativo. "un'ora" non ha spazio fra articolo e parola, quindi
     * l'apostrofo (dritto o tipografico) chiude il numero da solo.
     */
    private val RE_IT = Regex(
        "(\\d+|un['’]|una|uno|un)\\s*(second[oi]|minut[oi]|or[ae]|giorn[oi]|settiman[ae]|mes[ei]|ann[oi])\\s+fa",
        RegexOption.IGNORE_CASE
    )
    /*
     * v1.3.0 — anche le abbreviazioni: la diagnostica v1.64.0 mostra
     * "Uploaded 13 min ago" ("min", non "minutes"). Si accettano sec/min/
     * hr/h/d/w/mo/y oltre alle parole intere; "\\b" dopo l'unita' evita che
     * "mo" prenda l'inizio di "month" e simili a meta'.
     *
     * --- versione precedente (v1.0.0-v1.2.0) ---
     * private val RE_EN = Regex(
     *     "(\\d+|an|a)\\s+(seconds?|minutes?|hours?|days?|weeks?|months?|years?)\\s+ago",
     *     RegexOption.IGNORE_CASE
     * )
     * --- fine versione precedente ---
     */
    private val RE_EN = Regex(
        "(\\d+|an|a)\\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h|days?|d|weeks?|wks?|w|months?|mos?|years?|yrs?|y)\\b\\.?\\s+ago",
        RegexOption.IGNORE_CASE
    )
    private val RE_ORA_IT = Regex("^(adesso|ora|proprio ora|pochi secondi fa|qualche secondo fa)$", RegexOption.IGNORE_CASE)
    private val RE_ORA_EN = Regex("^(just now|now|a few seconds ago)$", RegexOption.IGNORE_CASE)
    private val RE_DATA_ASSOLUTA = Regex("(\\d{1,2})[/.](\\d{1,2})[/.](\\d{4})")

    /*
     * v1.3.0 — prima le parole intere italiane e inglesi (come prima), poi le
     * abbreviazioni inglesi; "mo" (mese) va provato prima di "m" (minuto).
     *
     * --- versione precedente (v1.0.0-v1.2.0): solo i rami "parola intera" ---
     */
    private fun unita(parola: String): Long = when {
        parola.startsWith("second") -> 1000L
        parola.startsWith("minut") -> MIN
        parola.startsWith("or") || parola.startsWith("hour") -> ORA
        parola.startsWith("giorn") || parola.startsWith("day") -> GIORNO
        parola.startsWith("settiman") || parola.startsWith("week") -> 7 * GIORNO
        parola.startsWith("mes") || parola.startsWith("month") -> 30 * GIORNO
        parola.startsWith("ann") || parola.startsWith("year") -> 365 * GIORNO
        // v1.3.0 — abbreviazioni
        parola.startsWith("sec") || parola == "s" -> 1000L
        parola.startsWith("mo") -> 30 * GIORNO
        parola.startsWith("min") || parola == "m" -> MIN
        parola.startsWith("hr") || parola == "h" -> ORA
        parola == "d" -> GIORNO
        parola.startsWith("wk") || parola == "w" -> 7 * GIORNO
        parola.startsWith("yr") || parola == "y" -> 365 * GIORNO
        else -> 0L
    }

    private fun quanti(parola: String): Long =
        parola.toLongOrNull() ?: 1L   // un, una, uno, un', a, an

    /** "un'ora fa" -> Esito(ora - 1 h, ...), null se il testo non e' una data. */
    fun interpreta(testo: String, ora: Long = System.currentTimeMillis()): Esito? {
        val t = testo.trim()
        if (t.isEmpty() || t.length > 60) return null
        if (RE_ORA_IT.matches(t) || RE_ORA_EN.matches(t)) return Esito(ora, t, MIN)
        if (t.equals("ieri", true) || t.equals("yesterday", true)) return Esito(ora - GIORNO, t, GIORNO)
        (RE_IT.find(t) ?: RE_EN.find(t))?.let { m ->
            val u = unita(m.groupValues[2].lowercase())
            if (u == 0L) return null
            val n = quanti(m.groupValues[1].lowercase().trimEnd('\'', '’'))
            return Esito(ora - n * u, t, u)
        }
        RE_DATA_ASSOLUTA.find(t)?.let { m ->
            val c = java.util.Calendar.getInstance()
            c.clear()
            c.set(m.groupValues[3].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[1].toInt(), 12, 0)
            return Esito(c.timeInMillis, t, GIORNO)
        }
        return null
    }

    /** Tutte le stringhe dentro un nodo, in ordine, fino a una certa profondita'. */
    private fun stringhe(v: Any?, prof: Int, out: MutableList<String>) {
        if (prof < 0 || out.size > 60) return
        when (v) {
            is String -> out.add(v)
            is JSONObject -> v.keys().forEach { k -> stringhe(v.opt(k), prof - 1, out) }
            is JSONArray -> for (i in 0 until v.length()) stringhe(v.opt(i), prof - 1, out)
        }
    }

    /** Il piu' piccolo nodo con l'etichetta e un testo-data dopo di essa. */
    private fun cercaEtichetta(v: Any?, ora: Long): Esito? {
        // prima i figli: cosi' vince il contenitore piu' piccolo
        when (v) {
            is JSONObject -> v.keys().forEach { k -> cercaEtichetta(v.opt(k), ora)?.let { return it } }
            is JSONArray -> for (i in 0 until v.length()) cercaEtichetta(v.opt(i), ora)?.let { return it }
            else -> return null
        }
        val s = ArrayList<String>()
        stringhe(v, 4, s)
        val iEtichetta = s.indexOfFirst { it.trim().trimEnd(':').lowercase() in ETICHETTE }
        if (iEtichetta < 0) return null
        for (j in iEtichetta + 1 until s.size) interpreta(s[j], ora)?.let { return it }
        return null
    }

    /** Ripiego: una chiave con "upload" nel nome e un valore-data. */
    private fun cercaCampo(v: Any?): Esito? {
        when (v) {
            is JSONObject -> for (k in v.keys()) {
                val x = v.opt(k)
                if (k.lowercase().contains("upload")) {
                    val ms = when (x) {
                        is Number -> x.toLong().let { if (it > 1_000_000_000_000L) it else it * 1000L }
                        is String -> x.toLongOrNull()?.let { if (it > 1_000_000_000_000L) it else it * 1000L }
                            ?: iso(x)
                        else -> 0L
                    }
                    if (ms > 1_000_000_000_000L) return Esito(ms, k, 1000L)
                }
                cercaCampo(x)?.let { return it }
            }
            is JSONArray -> for (i in 0 until v.length()) cercaCampo(v.opt(i))?.let { return it }
        }
        return null
    }

    /** Legge la data di caricamento dalla risposta del dettaglio; null se non c'e'. */
    /*
     * v1.3.0 — forma vista nel dettaglio da collegati (diagnostica v1.64.0):
     *   {"type":"text","value":"Uploaded 13 min ago","style":"body"}
     * etichetta e data nella STESSA stringa, dentro il plugin "summary".
     * cercaEtichetta() cercava l'etichetta come stringa a se' e non la
     * trovava. Qui: qualunque stringa che COMINCIA con l'etichetta, e il
     * resto letto con interpreta().
     */
    private val RE_PREFISSO = Regex("^\\s*(Uploaded|Caricato|Pubblicato)\\s*:?\\s+(.+)$", RegexOption.IGNORE_CASE)

    private fun cercaPrefisso(v: Any?, ora: Long): Esito? {
        when (v) {
            is String -> RE_PREFISSO.matchEntire(v)?.let { m ->
                interpreta(m.groupValues[2], ora)?.let { return it.copy(testo = v) }
            }
            is JSONObject -> for (k in v.keys()) cercaPrefisso(v.opt(k), ora)?.let { return it }
            is JSONArray -> for (i in 0 until v.length()) cercaPrefisso(v.opt(i), ora)?.let { return it }
        }
        return null
    }

    /*
     * v1.3.0 — cercaPrefisso() per prima (la forma vera); le altre due
     * restano come ripiego.
     * --- versione precedente (v1.0.0-v1.2.0) ---
     * cercaEtichetta(root, ora) ?: cercaCampo(root)
     * --- fine versione precedente ---
     */
    fun leggi(corpo: String, ora: Long = System.currentTimeMillis()): Esito? = try {
        val root = JSONObject(corpo)
        cercaPrefisso(root, ora) ?: cercaEtichetta(root, ora) ?: cercaCampo(root)
    } catch (t: Throwable) { null }

    /** Per la diagnostica, quando non si trova: nomi dei plugin e il pezzo attorno alle parole cercate. */
    fun indizi(corpo: String): String {
        val nomi = try {
            val p = JSONObject(corpo).optJSONArray("plugins")
            if (p == null) "(nessun \"plugins\")"
            else (0 until p.length()).joinToString(",") { p.optJSONObject(it)?.optString("name").orEmpty() }
        } catch (t: Throwable) { "(corpo non JSON)" }
        val i = listOf("aricat", "ploaded", "upload").map { corpo.indexOf(it, ignoreCase = true) }.filter { it >= 0 }.minOrNull()
        val pezzo = if (i == null) "(nessuna parola \"caricato\"/\"upload\" nel corpo)"
                    else corpo.substring(maxOf(0, i - 150), minOf(corpo.length, i + 350))
        return "plugin: $nomi · attorno: $pezzo"
    }

    /*
     * v1.1.0 (2026-09-24) — LETTURA DALLA PAGINA HTML DELL'ANNUNCIO.
     *
     * La diagnostica della v1.59.0 ha mostrato che l'endpoint del dettaglio
     * ora risponde
     *   400 {"code":"ANONYMOUS_VIEWER","message":"not allowed"}
     * (il 15/09 rispondeva 200): la sessione nascosta non e' piu' accettata
     * li'. La pagina pubblica dell'annuncio (www.vinted.it/items/<id>) invece
     * si legge anche senza accesso — e' quella dello screenshot con
     * "Caricato  un'ora fa" — e dalla stessa ricerca e' confermato che
     * l'HTML scaricato con fetch contiene gia' il contenuto vero (l'ancora
     * della v1.58.0 ha trovato le card cosi': "ancora dal vivo registrata").
     *
     * Come si cerca, senza sapere ancora il markup esatto:
     *  - si tolgono i blocchi <script>: li' dentro ci sono i dizionari delle
     *    traduzioni del sito (gia' visti nelle sonde precedenti), dove
     *    "Caricato" e "un'ora fa" possono comparire come testi generici e
     *    darebbero una data falsa;
     *  - dopo ogni "Caricato" rimasto si guardano ~800 caratteri: prima un
     *    attributo title="…"/datetime="…" con una data assoluta (se c'e'
     *    e' la piu' precisa), poi il testo visibile (tag tolti, entita'
     *    decodificate) — i primi 60 caratteri dopo la parola — letto con
     *    interpreta().
     * Quello che si trova, o il pezzo di HTML attorno alla parola quando non
     * si trova, va nella diagnostica (indiziHtml).
     */
    private val RE_SCRIPT = Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
    private val RE_STYLE = Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE)
    private val RE_TAG = Regex("<[^>]*>")
    private val RE_ATTR_DATA = Regex("(?:title|datetime|data-date)=\"([^\"]{6,60})\"", RegexOption.IGNORE_CASE)
    private val RE_ETICHETTA_HTML = Regex("(Caricato|Uploaded)", RegexOption.IGNORE_CASE)

    private val MESI = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio",
        "agosto", "settembre", "ottobre", "novembre", "dicembre")
    private val RE_DATA_ESTESA = Regex("(\\d{1,2})\\s+(${MESI.joinToString("|")})\\s+(\\d{4})(?:[^0-9]{1,6}(\\d{1,2})[:.](\\d{2}))?",
        RegexOption.IGNORE_CASE)

    private fun decodifica(t: String): String = t
        .replace("&#x27;", "'").replace("&#39;", "'").replace("&apos;", "'")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
        .replace("\\u0027", "'").replace("\u00a0", " ")

    /** Data assoluta in un attributo: ISO, "24/09/2026 21:10", "24 settembre 2026, 21:10". */
    private fun dataAssoluta(v: String): Long? {
        iso(v).takeIf { it > 0L }?.let { return it }
        RE_DATA_ESTESA.find(v)?.let { m ->
            val c = java.util.Calendar.getInstance(); c.clear()
            val ore = m.groupValues[4].toIntOrNull() ?: 12
            val min = m.groupValues[5].toIntOrNull() ?: 0
            c.set(m.groupValues[3].toInt(), MESI.indexOf(m.groupValues[2].lowercase()), m.groupValues[1].toInt(), ore, min)
            return c.timeInMillis
        }
        Regex("(\\d{1,2})/(\\d{1,2})/(\\d{4})(?:[^0-9]{1,3}(\\d{1,2}):(\\d{2}))?").find(v)?.let { m ->
            val c = java.util.Calendar.getInstance(); c.clear()
            c.set(m.groupValues[3].toInt(), m.groupValues[2].toInt() - 1, m.groupValues[1].toInt(),
                m.groupValues[4].toIntOrNull() ?: 12, m.groupValues[5].toIntOrNull() ?: 0)
            return c.timeInMillis
        }
        return null
    }

    /*
     * v1.2.0 (2026-09-24) — CORREZIONE: con lo scaricamento parziale (app
     * v1.61.0) la pagina arriva spesso TRONCATA a meta' di un <script> mai
     * chiuso. RE_SCRIPT toglie solo i blocchi chiusi, quindi la coda dello
     * script aperto restava "visibile": la diagnostica v1.61.0 lo mostra
     * ("fuori dagli script: …Se hai caricato meno di {items_count}
     * articoli…" — e' il dizionario delle traduzioni). Dopo aver tolto i
     * blocchi chiusi, un "<script" rimasto e' per forza aperto: si taglia
     * tutto da li' in avanti.
     */
    private fun senzaScript(html: String): String {
        val v = RE_SCRIPT.replace(html, " ")
        val aperto = v.indexOf("<script", ignoreCase = true)
        return if (aperto >= 0) v.substring(0, aperto) else v
    }

    /** v1.1.0 — "Caricato" dalla pagina HTML dell'annuncio; null se non c'e'. */
    fun leggiHtml(html: String, ora: Long = System.currentTimeMillis()): Esito? = try {
        // v1.2.0 — senzaScript() invece di RE_SCRIPT.replace (script aperto in coda)
        // --- versione precedente: val visibile = RE_STYLE.replace(RE_SCRIPT.replace(html, " "), " ")
        val visibile = RE_STYLE.replace(senzaScript(html), " ")
        var trovato: Esito? = null
        for (m in RE_ETICHETTA_HTML.findAll(visibile)) {
            val finestra = visibile.substring(m.range.last + 1, minOf(visibile.length, m.range.last + 1 + 800))
            // 1) data assoluta in un attributo, se c'e'
            for (a in RE_ATTR_DATA.findAll(finestra)) {
                dataAssoluta(decodifica(a.groupValues[1]))?.takeIf { it in 1L..(ora + 60_000L) }?.let {
                    trovato = Esito(it, "attributo: " + a.groupValues[1], MIN)
                }
                if (trovato != null) break
            }
            if (trovato != null) break
            // 2) testo visibile subito dopo la parola
            val testo = decodifica(RE_TAG.replace(finestra, " ")).replace(Regex("\\s+"), " ").trim().take(60)
            (RE_IT.find(testo) ?: RE_EN.find(testo))?.let { r -> interpreta(r.value, ora)?.let { trovato = it } }
            if (trovato == null) {
                val primo = testo.split(Regex("[·|,;]")).firstOrNull()?.trim().orEmpty()
                interpreta(primo, ora)?.let { trovato = it }
            }
            if (trovato != null) break
        }
        trovato
    } catch (t: Throwable) { null }

    /** v1.1.0 — per la diagnostica: dove compare la parola nella pagina, e cosa c'e' attorno. */
    fun indiziHtml(html: String): String {
        // v1.2.0 — senzaScript(), vedi sopra
        // --- versione precedente: val visibile = RE_SCRIPT.replace(html, " ")
        val visibile = senzaScript(html)
        val iVis = RE_ETICHETTA_HTML.find(visibile)?.range?.first
        val iTot = RE_ETICHETTA_HTML.find(html)?.range?.first
        return "html ${html.length} caratteri · " +
                (if (iVis != null) "fuori dagli script: " + visibile.substring(maxOf(0, iVis - 100), minOf(visibile.length, iVis + 500))
                 else "\"Caricato\" assente fuori dagli script") +
                (if (iVis == null && iTot != null) " · dentro uno script: " + html.substring(maxOf(0, iTot - 100), minOf(html.length, iTot + 400)) else "")
    }
}
