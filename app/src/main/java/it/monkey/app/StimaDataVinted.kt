package it.monkey.app

/*
 * Monkey — stima della data di pubblicazione degli annunci Vinted
 * v1.0.0 (2026-09-24) — nuovo, consegnato con l'app v1.58.0
 * v1.1.0 (2026-09-24) — app v1.61.0: anche le letture "Caricato" (al
 *   minuto E all'ora) diventano punti della curva, vedi registraLettura().
 *
 * IL PROBLEMA
 * Dalla v1.56.0 gli annunci Vinted si leggono dalla pagina HTML del
 * catalogo (il vecchio JSON con le date e' stato ritirato da Vinted). La
 * card dell'elenco non porta NESSUNA data: la v1.56.0/1.57.0 usavano una
 * stima ordinale ancorata a "adesso" (posizione nella pagina = qualche
 * secondo indietro), quindi ogni annuncio Vinted risultava "oggi" anche se
 * pubblicato giorni prima — segnalato dall'utente il 2026-09-24 con uno
 * screenshot (tutte le card "24 set 2026 · oggi").
 *
 * L'IDEA: L'ID DICE QUANDO
 * Gli id degli articoli Vinted sono un contatore unico e crescente per
 * tutta la piattaforma: un id piu' alto e' SEMPRE un annuncio pubblicato
 * dopo. Verificato sui dati gia' raccolti in questa conversazione, non
 * indovinato:
 *   - 9848892019 -> foto caricata 2026-08-31 21:21 UTC  (vecchio JSON, data vera)
 *   - 9854267488 -> foto caricata 2026-09-01 13:50 UTC  (vecchio JSON, data vera)
 *   - 9938309969 visto il 2026-09-09, 10014019096 visto il 2026-09-15:
 *     crescita coerente di ~11 milioni di id al giorno (~130 al secondo).
 * Conoscendo DUE punti (id, istante) si stima per interpolazione quando e'
 * nato qualsiasi altro id: e' la stessa cosa che fa chi legge l'eta' di un
 * albero dagli anelli — non una data dichiarata, ma molto meglio di "oggi".
 *
 * L'ANCORA "ADESSO"
 * Per non estrapolare per settimane da due punti di fine agosto, MotoreRicerca
 * (avviaDom) chiede — al massimo una volta ogni 20 minuti — la pagina
 * "https://www.vinted.it/catalog?order=newest_first" SENZA testo di ricerca
 * (fetch dalla stessa pagina gia' aperta, nessuna navigazione: la sessione
 * DOM non si sposta, vedi la lezione v1.50/v1.51 citata li'). L'id piu'
 * alto trovato li' e' un annuncio appena pubblicato: coppia (id, adesso).
 * Le ancore si conservano (SharedPreferences) fra un avvio e l'altro, cosi'
 * col passare dei giorni la curva id->tempo si riempie di punti veri e
 * l'interpolazione segue anche il ritmo giorno/notte delle pubblicazioni.
 *
 * LIMITI DICHIARATI
 *  - E' una STIMA: l'app la mostra con "≈" davanti (Annuncio.dataStimata).
 *  - Un annuncio "ripubblicato" da Vinted prende un id nuovo: qui risulta
 *    nuovo, esattamente come lo mostra Vinted stesso in "piu' recenti".
 *  - Se la pagina ancora non porta id (non ancora verificato sul campo che
 *    l'HTML scaricato con fetch contenga le card come quello navigato), la
 *    diagnostica lo scrive ("ancora: ...") e si resta sui punti gia' noti +
 *    velocita' media: errore che cresce con la distanza dall'ultimo punto.
 */

import android.content.Context
import android.content.SharedPreferences

object StimaDataVinted {

    /*
     * --- versione precedente ---
     * const val VERSIONE = "1.0.0"
     * --- fine versione precedente ---
     */
    const val VERSIONE = "1.1.0"

    private const val PREFS = "stima_data_vinted"
    private const val CHIAVE_ANCORE = "ancore"

    /** Ogni quanto chiedere un'ancora fresca, se l'ultima e' riuscita. */
    private const val VALIDITA_ANCORA_MS = 20L * 60_000L
    /** Dopo un tentativo fallito, quanto aspettare prima di riprovare. */
    private const val PAUSA_DOPO_TENTATIVO_MS = 5L * 60_000L
    /** Due ancore piu' vicine di cosi' sono ridondanti: si tiene la piu' nuova. */
    private const val DISTANZA_MIN_ANCORE_MS = 15L * 60_000L
    /** Ancore piu' vecchie di cosi' non servono piu' agli annunci che si vedono. */
    private const val TENUTA_MS = 120L * 86_400_000L
    private const val MAX_ANCORE = 400

    /**
     * Velocita' di riserva (id al millisecondo) quando non ci sono ancore
     * abbastanza distanti per calcolarla: ~133 id/s = ~11,5 milioni/giorno,
     * vedi i dati nel commento in cima al file.
     */
    private const val VELOCITA_RISERVA = 0.133
    /** Limiti di buon senso per la velocita' calcolata (id/ms). */
    private const val VELOCITA_MIN = 0.03
    private const val VELOCITA_MAX = 0.5
    /** Span minimo fra prima e ultima ancora per fidarsi della velocita' calcolata. */
    private const val SPAN_MIN_VELOCITA_MS = 20L * 3_600_000L

    /**
     * Punti veri di partenza (id, istante in ms), dal vecchio JSON Vinted che
     * portava ancora la data della foto: servono finche' non arriva la prima
     * ancora dal vivo, e restano come punti bassi della curva.
     */
    private val SEMI: List<Pair<Long, Long>> = listOf(
        9_848_892_019L to 1_788_211_276_000L,   // 2026-08-31 21:21:16 UTC
        9_854_267_488L to 1_788_270_609_000L    // 2026-09-01 13:50:09 UTC
    )

    private val ancore = ArrayList<Pair<Long, Long>>()   // (id, ms), ordinate per id

    /*
     * v1.1.0 (2026-09-24) — letture "Caricato" (DataCaricamentoVinted) come
     * punti della curva, TENUTE A PARTE dalle ancore dal vivo:
     *  - la diagnostica v1.60.0 ha mostrato la stima dall'id 3 h 20 min
     *    indietro rispetto a "5 ore fa" letto sulla pagina: con soli due
     *    punti stasera e due a inizio settembre, fra loro si interpola a
     *    velocita' media e non si segue il ritmo giorno/notte;
     *  - un "N ore fa" ha un arrotondamento di circa un'ora, peggio di
     *    un'ancora dal vivo: non deve MAI farne cadere una (in registra() il
     *    punto nuovo contraddittorio vince — qui no), ne' far credere a
     *    serveAncora() che l'ancora "adesso" sia fresca.
     * Terna (id, ms, precisione in ms).
     */
    private data class Lettura(val id: Long, val ms: Long, val precisione: Long)
    private val letture = ArrayList<Lettura>()
    private const val CHIAVE_LETTURE = "letture"
    private const val MAX_LETTURE = 300
    /** Oltre questo arrotondamento una lettura non serve alla curva ("3 giorni fa"). */
    private const val PRECISIONE_MAX_LETTURA = 3_600_000L
    private var prefs: SharedPreferences? = null
    private var ultimaAncoraMs = 0L
    private var ultimoTentativoMs = 0L
    private var ultimoEsito = "nessuna ancora dal vivo in questa sessione"

    /** Da chiamare una volta con un Context (MotoreRicerca lo fa nel suo init). */
    @Synchronized
    fun inizializza(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        ancore.clear()
        p.getString(CHIAVE_ANCORE, "").orEmpty().split(';').forEach { voce ->
            val parti = voce.split(':')
            if (parti.size == 2) {
                val id = parti[0].toLongOrNull()
                val ms = parti[1].toLongOrNull()
                if (id != null && ms != null && id > 0 && ms > 0) ancore.add(id to ms)
            }
        }
        ancore.sortBy { it.first }
        ultimaAncoraMs = ancore.maxOfOrNull { it.second } ?: 0L
        // v1.1.0 — letture "Caricato"
        letture.clear()
        p.getString(CHIAVE_LETTURE, "").orEmpty().split(';').forEach { voce ->
            val parti = voce.split(':')
            if (parti.size == 3) {
                val id = parti[0].toLongOrNull(); val ms = parti[1].toLongOrNull(); val pr = parti[2].toLongOrNull()
                if (id != null && ms != null && pr != null && id > 0 && ms > 0) letture.add(Lettura(id, ms, pr))
            }
        }
    }

    /**
     * v1.1.0 — registra una lettura "Caricato" come punto della curva.
     * Solo fino all'ora di arrotondamento ("5 ore fa" si', "3 giorni fa" no).
     */
    @Synchronized
    fun registraLettura(id: Long, ms: Long, precisioneMs: Long) {
        val ora = System.currentTimeMillis()
        if (id <= 0 || ms <= 0 || ms > ora + 60_000L || precisioneMs > PRECISIONE_MAX_LETTURA) return
        letture.removeAll { it.id == id }
        letture.add(Lettura(id, ms, precisioneMs))
        letture.removeAll { ora - it.ms > TENUTA_MS }
        letture.sortBy { it.id }
        while (letture.size > MAX_LETTURE) letture.removeAt(0)
        prefs?.edit()?.putString(CHIAVE_LETTURE, letture.joinToString(";") { "${it.id}:${it.ms}:${it.precisione}" })?.apply()
    }

    /**
     * Vero se MotoreRicerca deve chiedere un'ancora fresca con questa pagina.
     * Chiamarla "consuma" il turno: segna il tentativo, cosi' un fallimento
     * non fa ripetere la richiesta a ogni pagina successiva.
     */
    @Synchronized
    fun serveAncora(): Boolean {
        val ora = System.currentTimeMillis()
        if (ora - ultimaAncoraMs < VALIDITA_ANCORA_MS) return false
        if (ora - ultimoTentativoMs < PAUSA_DOPO_TENTATIVO_MS) return false
        ultimoTentativoMs = ora
        return true
    }

    /** Registra una coppia (id appena pubblicato, istante) arrivata dalla pagina. */
    @Synchronized
    fun registra(id: Long, ms: Long) {
        val ora = System.currentTimeMillis()
        if (id <= 0 || ms <= 0 || ms > ora + 60_000L) { ultimoEsito = "ancora scartata: valori non validi"; return }
        // coerenza: un id piu' alto non puo' essere nato PRIMA di uno piu'
        // basso. Se una vecchia ancora contraddice quella nuova (per esempio
        // l'orologio del telefono e' stato spostato), vince la nuova.
        ancore.removeAll { (i, t) -> (i >= id && t < ms) || (i <= id && t > ms) }
        // due ancore troppo vicine nel tempo: si tiene solo la nuova
        ancore.removeAll { (_, t) -> kotlin.math.abs(t - ms) < DISTANZA_MIN_ANCORE_MS }
        ancore.add(id to ms)
        ancore.removeAll { (_, t) -> ora - t > TENUTA_MS }
        ancore.sortBy { it.first }
        while (ancore.size > MAX_ANCORE) ancore.removeAt(0)
        ultimaAncoraMs = ms
        ultimoEsito = "ancora dal vivo registrata: id $id"
        prefs?.edit()?.putString(CHIAVE_ANCORE, ancore.joinToString(";") { "${it.first}:${it.second}" })?.apply()
    }

    /** Annota nella diagnostica perche' un'ancora non e' arrivata. */
    @Synchronized
    fun segnaFallimento(motivo: String) { ultimoEsito = "ancora non ottenuta: $motivo" }

    /*
     * v1.1.0 — in due passate: prima i punti precisi (semi, ancore dal vivo,
     * letture al minuto) resi monotoni come prima; poi le letture all'ora,
     * ognuna "stretta" fra i due punti precisi che la circondano (il vero
     * istante sta per forza fra loro, e l'arrotondamento della lettura puo'
     * averla spinta appena fuori). Cosi' una lettura grossolana non scalza
     * mai un punto preciso, ma riempie i buchi fra loro.
     *
     * --- versione precedente (v1.0.0) ---
     * private fun punti(): List<Pair<Long, Long>> {
     *     val tutti = (SEMI + ancore).sortedBy { it.first }
     *     val out = ArrayList<Pair<Long, Long>>(tutti.size)
     *     for (p in tutti) {
     *         if (out.isEmpty() || (p.first > out.last().first && p.second >= out.last().second)) out.add(p)
     *     }
     *     return out
     * }
     * --- fine versione precedente ---
     */
    private fun punti(): List<Pair<Long, Long>> {
        val precisi = (SEMI + ancore + letture.filter { it.precisione <= 60_000L }.map { it.id to it.ms })
            .sortedBy { it.first }
        val out = ArrayList<Pair<Long, Long>>(precisi.size + letture.size)
        for (p in precisi) {
            // si scarta un punto che andrebbe "indietro nel tempo" rispetto al
            // precedente: la curva id->tempo deve essere crescente
            if (out.isEmpty() || (p.first > out.last().first && p.second >= out.last().second)) out.add(p)
        }
        for (l in letture.filter { it.precisione > 60_000L }.sortedBy { it.id }) {
            val dopo = out.indexOfFirst { it.first >= l.id }
            if (dopo >= 0 && out[dopo].first == l.id) continue
            val minT = if (dopo == 0) Long.MIN_VALUE else (if (dopo < 0) out.last() else out[dopo - 1]).second
            val maxT = if (dopo < 0) Long.MAX_VALUE else out[dopo].second
            if (minT > maxT) continue
            val t = l.ms.coerceIn(minT, maxT)
            if (dopo < 0) out.add(l.id to t) else out.add(dopo, l.id to t)
        }
        return out
    }

    private fun velocita(pt: List<Pair<Long, Long>>): Double {
        if (pt.size < 2) return VELOCITA_RISERVA
        val a = pt.first(); val b = pt.last()
        val dt = b.second - a.second
        if (dt < SPAN_MIN_VELOCITA_MS) return VELOCITA_RISERVA
        return ((b.first - a.first).toDouble() / dt).coerceIn(VELOCITA_MIN, VELOCITA_MAX)
    }

    /**
     * Istante stimato (ms) in cui e' nato l'annuncio con questo id.
     * Fra due punti noti: interpolazione lineare. Oltre l'ultimo o prima del
     * primo: estrapolazione con la velocita' media. Mai nel futuro.
     */
    @Synchronized
    fun stima(id: Long): Long {
        val ora = System.currentTimeMillis()
        val pt = punti()
        if (pt.isEmpty() || id <= 0) return 0L
        val v = velocita(pt)
        val dopo = pt.indexOfFirst { it.first >= id }
        val t = when {
            dopo == 0 -> pt[0].second - ((pt[0].first - id) / v).toLong()
            dopo < 0 -> pt.last().second + ((id - pt.last().first) / v).toLong()
            else -> {
                val a = pt[dopo - 1]; val b = pt[dopo]
                if (b.first == a.first) b.second
                else a.second + ((id - a.first).toDouble() / (b.first - a.first) * (b.second - a.second)).toLong()
            }
        }
        return t.coerceIn(1L, ora)
    }

    /** Una riga per la diagnostica esportata. */
    @Synchronized
    fun descrizione(): String {
        val pt = punti()
        val v = velocita(pt)
        val ultima = ancore.maxByOrNull { it.second }
        val fmt = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.ITALY)
        return "stima-data v$VERSIONE: ancore=${ancore.size} letture=${letture.size} punti=${pt.size} " +
                "velocita=${"%.0f".format(v * 1000)} id/s" +
                (ultima?.let { " ultima=${it.first}@${fmt.format(java.util.Date(it.second))}" } ?: "") +
                " · $ultimoEsito"
    }
}
