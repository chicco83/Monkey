package it.monkey.app

/*
 * Subito+ — stato della ricerca unificata
 * v0.5.2 (2026-08-30) — Fase 4, punto 2
 *
 * Separa il "cosa" dal "come si vede": qui vivono la richiesta in corso, i
 * risultati, i filtri e gli errori; la schermata (SchermataRicerca) si limita
 * a disegnarli. E' quello che rende possibile aggiungere una fonte o un filtro
 * senza toccare l'interfaccia, e viceversa.
 *
 * Sostituisce la parte di stato che stava dentro VistaRicerca.kt (viste
 * tradizionali, v0.4.0 - v0.5.1), rimosso in questa versione.
 */

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class StatoRicerca(
    private val ctx: Context,
    private val motore: MotoreRicerca,
    /** Chiamata quando cambia il fatto che ci sia o meno un caricamento in corso. */
    private val caricamento: (Boolean) -> Unit
) {
    // ---- richiesta ----
    var query by mutableStateOf("")
    var ultimaQuery by mutableStateOf("")
        private set
    private var pagina = 1
    private var attesa = 0
    var inCorso by mutableStateOf(false)
        private set
    var primaRicercaFatta by mutableStateOf(false)
        private set
    /** Vero quando una pagina non ha portato nulla di nuovo: inutile chiederne altre. */
    var esaurito by mutableStateOf(false)
        private set
    private var dimensionePrima = 0

    // ---- risultati ----
    private val grezzi = mutableStateListOf<Annuncio>()
    val errori = mutableStateMapOf<String, String>()
    val conteggi = mutableStateMapOf<String, Int>()

    // ---- filtri ----
    var usaSubito by mutableStateOf(true)
    var usaVinted by mutableStateOf(true)
    var ordine by mutableStateOf(0)          // 0 recenti, 1 prezzo cresc., 2 prezzo decr.
    var etaModo by mutableStateOf(0)         // 0 qualsiasi, 1 piu' vecchi di, 2 piu' recenti di
    var giorni by mutableStateOf("30")
    var prezzoMin by mutableStateOf("")
    var prezzoMax by mutableStateOf("")
    var mostraVenduti by mutableStateOf(false)

    /*
     * v1.1.0 — mostra solo cio' che non c'era l'ultima volta che questa stessa
     * ricerca e' stata eseguita. L'istantanea precedente si legge all'avvio
     * della ricerca e resta ferma per tutta la sessione, altrimenti il primo
     * risultato che arriva renderebbe "vecchio" tutto il resto.
     */
    var soloNuovi by mutableStateOf(false)
    private var precedenti: Set<String> = emptySet()
    private val correnti = LinkedHashSet<String>()

    /** Vero se di questa ricerca esiste un'esecuzione precedente da confrontare. */
    var haPrecedente by mutableStateOf(false)
        private set

    /** Ricerche recenti, conservate fra un avvio e l'altro. */
    var recenti by mutableStateOf(caricaRecenti())
        private set

    /* ------------------------------------------------------------------
     * v1.2.0 — preferiti
     *
     * Lo stato mostrato vive qui come mappa osservabile, cosi' il cuore cambia
     * al tocco senza aspettare la rete. La chiamata alla piattaforma parte
     * subito dopo: se fallisce, il cuore torna com'era. Meglio un cuore che
     * torna indietro di uno che mente.
     * ------------------------------------------------------------------ */
    val preferiti = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    var messaggio by mutableStateOf<String?>(null)

    /* ------------------------------------------------------------------
     * v1.2.0 — luogo degli annunci Vinted.
     *
     * Non arriva con i risultati: sta sul profilo del venditore, una chiamata
     * per venditore (vedi LuoghiVinted). Quindi la riga nasce senza luogo e lo
     * riceve dopo, ed e' per questo che serve una mappa osservabile: quando il
     * profilo risponde, quella riga — solo quella — si ridisegna.
     * ------------------------------------------------------------------ */
    val luoghiVinted = androidx.compose.runtime.mutableStateMapOf<String, String>()

    /* ------------------------------------------------------------------
     * v1.4.0 — le offerte fatte.
     *
     * Una sola chiamata per tutta la lista (vedi Offerte), non una per riga.
     * Qui vive solo un contatore osservabile: quando l'elenco cambia lo si
     * incrementa e le righe si ridisegnano leggendo Offerte, che e' la fonte.
     * ------------------------------------------------------------------ */
    var revisioneOfferte by mutableStateOf(0)
        private set

    /* ------------------------------------------------------------------
     * v1.8.0 — la vista "Preferiti".
     *
     * Non e' una schermata nuova: e' la stessa lista, riempita da un'altra
     * fonte di righe. Cosi' cuore, scarto, filtri, vista compatta e apertura
     * della scheda funzionano gia', senza riscriverli. L'unica differenza e'
     * che non si pagina e non si cerca.
     * ------------------------------------------------------------------ */
    var modoPreferiti by mutableStateOf(false)
        private set

    fun tornaAllaRicerca() {
        modoPreferiti = false
        grezzi.clear(); errori.clear(); conteggi.clear()
        primaRicercaFatta = false
        ultimaQuery = ""
    }

    /** Le ultime proposte inviate, per la schermata iniziale. */
    fun offerteRecenti() : List<Offerte.Offerta> {
        revisioneOfferte
        return Offerte.recenti(6)
    }

    fun apriPreferiti() {
        modoPreferiti = true
        primaRicercaFatta = true
        ultimaQuery = "Preferiti"
        esaurito = true                   // non c'e' una pagina due
        grezzi.clear(); errori.clear(); conteggi.clear()
        soloNuovi = false                 // non avrebbe senso qui

        var attese = 0

        /*
         * v1.10.0 — Vinted resta a un passo: il suo elenco preferiti e' gia'
         * fatto di annunci completi (stessa forma del catalogo), quindi
         * preferitiAnnunci() li legge subito.
         */
        val cVinted = FonteVinted.preferitiElenco(utenteVinted)
        if (cVinted == null) {
            errori["vinted"] = "non so ancora chi sei: sto guardando, riprova fra un attimo"
            scopriUtenteVinted()
        } else {
            attese++
            attesa++; inCorso = true; caricamento(true)
            motore.azione(FonteVinted, cVinted) { stato, corpo ->
                if (stato in 200..299) {
                    val lista = FonteVinted.preferitiAnnunci(corpo)
                    conteggi["vinted"] = lista.size
                    // v1.9.0 — se non si riesce a leggere, si mostra l'inizio
                    // della risposta: e' quello che ha sbloccato il CSRF.
                    if (lista.isEmpty())
                        errori["vinted"] = "elenco non letto — " + corpo.take(160).replace("\n", " ")
                    else errori.remove("vinted")
                    lista.forEach { preferiti[it.chiave] = true }
                    aggiungi(lista)
                } else errori["vinted"] = "risposta $stato"
                finita()
            }
        }

        /*
         * v1.10.0 — Subito e' a DUE passi: prima l'elenco (solo urn e data),
         * poi una risoluzione per ciascuno. Vedi Fonti.kt per il contratto
         * verificato sul campo.
         */
        val cSubito = FonteSubito.preferitiElenco(utenteSubito)
        if (cSubito == null) {
            errori["subito"] = "non so ancora chi sei: sto guardando, riprova fra un attimo"
            scopriUtenteSubito()
        } else {
            attese++
            attesa++; inCorso = true; caricamento(true)
            motore.azione(FonteSubito, cSubito) { stato, corpo ->
                if (stato in 200..299) {
                    val urns = FonteSubito.preferitiUrns(corpo)
                    conteggi["subito"] = 0
                    if (urns.isEmpty()) {
                        errori["subito"] =
                            if (corpo.contains("favorite_ads")) "" // elenco vuoto: nessun preferito, non e' un errore
                            else "elenco non letto — " + corpo.take(160).replace("\n", " ")
                        if (errori["subito"].isNullOrBlank()) errori.remove("subito")
                    } else {
                        errori.remove("subito")
                        risolviPreferitiSubito(urns)
                    }
                } else errori["subito"] = "risposta $stato"
                finita()
            }
        }

        if (attese == 0) { inCorso = false; caricamento(false) }
    }

    /*
     * v1.10.0 — risolve ogni urn dei preferiti Subito con una chiamata a se',
     * due alla volta: ogni catena chiede il prossimo solo dopo aver ricevuto
     * risposta, la stessa cautela di LuoghiVinted contro le raffiche. Ogni
     * annuncio compare in lista appena arriva, non tutti insieme alla fine.
     */
    private fun risolviPreferitiSubito(urns: List<String>) {
        val PARALLELE = 2
        var successivo = 0

        fun avanti() {
            val i = successivo
            if (i >= urns.size) return
            successivo++
            attesa++; inCorso = true; caricamento(true)
            motore.azione(FonteSubito, FonteSubito.preferitoRisolvi(urns[i])) { stato, corpo ->
                if (stato in 200..299) {
                    val lista = FonteSubito.leggi(corpo)
                    if (lista.isNotEmpty()) {
                        conteggi["subito"] = (conteggi["subito"] ?: 0) + lista.size
                        preferiti[lista[0].chiave] = true
                        aggiungi(lista)
                    }
                }
                finita()
                avanti()   // la prossima di questa catena parte solo ora
            }
        }
        repeat(minOf(PARALLELE, urns.size)) { avanti() }
    }

    /*
     * v1.8.0 — l'identificatore Vinted serve solo all'elenco dei preferiti.
     * Sta in /api/v2/users/current, che nella sonda ha risposto 200 con
     * {"user":{"id":163221140,…}}: un campo dichiarato, non un indizio.
     */
    private var utenteVinted: String = prefs().getString("utenteVinted", "") ?: ""

    private fun scopriUtenteVinted() {
        if (utenteVinted.isNotBlank()) return
        motore.azione(FonteVinted, Chiamata("GET", "/api/v2/users/current")) { stato, corpo ->
            if (stato !in 200..299) return@azione
            val id = try {
                val o = org.json.JSONObject(corpo.substringAfter("] ", corpo))
                (o.optJSONObject("user") ?: o).opt("id")?.toString().orEmpty()
            } catch (t: Throwable) { "" }
            if (id.isNotBlank() && id.all(Char::isDigit)) {
                utenteVinted = id
                prefs().edit().putString("utenteVinted", id).apply()
            }
        }
    }

    /** Il testo rosso accanto al prezzo, o null se su questo annuncio non hai offerto. */
    fun offerta(a: Annuncio): String? {
        revisioneOfferte      // dipendenza esplicita: rilegge quando l'elenco cambia
        val o = Offerte.perAnnuncio(a) ?: return null
        return if (o.attiva) Offerte.testo(o) else null
    }

    /**
     * Il testo che sta accanto al nome della fonte.
     * Su Subito il luogo c'e' gia' nei risultati. Su Vinted arriva dopo: finche'
     * non e' arrivato si mostra la condizione, cosi' la riga non e' mai monca.
     */
    fun coda(a: Annuncio): String {
        if (a.fonte != "vinted") return a.luogo
        val l = luoghiVinted[a.venditore] ?: LuoghiVinted.luogo(a.venditore)
        return if (!l.isNullOrBlank()) l else a.dettaglio
    }

    private var utenteSubito: String =
        prefs().getString("utenteSubito", "") ?: ""
    private var elencoSubitoLetto = false

    fun ePreferito(a: Annuncio): Boolean =
        preferiti[a.chiave] ?: Preferiti.contiene(a.chiave)

    fun cambiaPreferito(a: Annuncio) {
        val prima = ePreferito(a)
        val dopo = !prima
        val f = FONTI.firstOrNull { it.nome == a.fonte } ?: return

        val utente = if (a.fonte == "subito") utenteSubito else ""
        val c = if (dopo) f.preferitoAggiungi(a, utente) else f.preferitoRimuovi(a, utente)
        if (c == null) {
            messaggio = "Preferiti non disponibili: apri una volta il sito dalla navigazione"
            if (a.fonte == "subito") scopriUtenteSubito()
            return
        }

        preferiti[a.chiave] = dopo
        Preferiti.imposta(ctx, a.chiave, dopo)

        motore.azione(f, c) { stato, corpo ->
            val ok = stato in 200..299 && !corpo.contains("\"code\":1")
            if (!ok) {
                preferiti[a.chiave] = prima
                Preferiti.imposta(ctx, a.chiave, prima)
                // v1.6.0 — nel messaggio anche la prima riga della risposta:
                // "403 access_denied" e "404" vogliono correzioni diverse.
                messaggio = "Preferito non salvato su ${a.fonte} ($stato) " +
                        corpo.take(90).replace("\n", " ")
            }
        }
    }

    /*
     * v1.6.0 (2026-08-31) — CORREZIONE: su Subito il cuore diceva sempre
     * "apri una volta il sito dalla navigazione".
     *
     * Quel messaggio compare quando l'identificatore dell'utente non e' noto, e
     * non lo era mai: la ricerca dell'id frugava fra cookie e localStorage con
     * un'espressione presa da una versione del sito, e quando il sito cambia
     * modo di conservarlo non trova piu' niente e non lo dice a nessuno.
     * Cercare in un posto solo, per giunta senza contratto, era il difetto.
     *
     * Ora ci sono due strade, e la seconda e' solida: l'elenco delle
     * transazioni — quello dei "offerti xxx €", gia' verificato — dichiara
     * "buyer_id":"6338126", che e' proprio l'identificatore che serve. Non e'
     * una traccia lasciata in giro dal sito: e' un campo di una risposta.
     *
     * --- versione precedente (v1.2.0, 2026-08-30) ---
     * private fun scopriUtenteSubito() {
     *     if (utenteSubito.isNotBlank()) return
     *     motore.leggiDallaPagina(FonteSubito, "…regex su cookie e localStorage…") { … }
     * }
     * --- fine versione precedente ---
     */
    private fun scopriUtenteSubito() {
        if (utenteSubito.isNotBlank()) return
        motore.leggiDallaPagina(
            FonteSubito,
            """
            (function(){
              try {
                var s = document.cookie;
                for (var i = 0; i < localStorage.length; i++)
                  s += ' ' + localStorage.getItem(localStorage.key(i));
                for (var j = 0; j < sessionStorage.length; j++)
                  s += ' ' + sessionStorage.getItem(sessionStorage.key(j));
                var m = /sdrn:[a-z.]*subito[a-z.]*:user:(\d+)/i.exec(s);
                if (m) return m[1];
                m = /"user_?id"\s*:\s*"?(\d{5,})/i.exec(s);
                return m ? m[1] : '';
              } catch (e) { return ''; }
            })()
            """.trimIndent()
        ) { grezzo ->
            val id = grezzo.trim().trim('"')
            if (id.isNotBlank() && id.all(Char::isDigit)) usaUtenteSubito(id)
            else utenteDalleTransazioni()
        }
    }

    /** Seconda strada: l'id sta dentro l'elenco delle transazioni, come buyer_id. */
    private fun utenteDalleTransazioni() {
        motore.azione(
            FonteSubito,
            Chiamata("GET", "https://hades.subito.it/v1/p2p/transactions?role=buyer")
        ) { stato, corpo ->
            if (stato !in 200..299) {
                messaggio = "Non riesco a riconoscere il tuo account Subito (risposta $stato)"
                return@azione
            }
            val id = try {
                val arr = org.json.JSONObject(corpo.substringAfter("] ", corpo))
                    .optJSONArray("transactions")
                var trovato = ""
                if (arr != null) for (i in 0 until arr.length()) {
                    val t = arr.optJSONObject(i) ?: continue
                    val b = t.optString("buyer_id")
                    if (b.isNotBlank() && b.all(Char::isDigit)) { trovato = b; break }
                }
                trovato
            } catch (t: Throwable) { "" }
            if (id.isNotBlank()) usaUtenteSubito(id)
            else messaggio = "Account Subito non riconosciuto: fai almeno un'offerta o apri il sito"
        }
    }

    private fun usaUtenteSubito(id: String) {
        utenteSubito = id
        prefs().edit().putString("utenteSubito", id).apply()
        leggiPreferitiSubito()
    }

    private fun leggiPreferitiSubito() {
        if (elencoSubitoLetto || utenteSubito.isBlank()) return
        val c = FonteSubito.preferitiElenco(utenteSubito) ?: return
        elencoSubitoLetto = true
        motore.azione(FonteSubito, c) { stato, corpo ->
            if (stato in 200..299) {
                val veri = FonteSubito.preferitiLeggi(corpo)
                Preferiti.riallinea(ctx, "", veri)
                veri.forEach { preferiti[it] = true }
            } else elencoSubitoLetto = false   // si riprova alla prossima ricerca
        }
    }

    init {
        Preferiti.carica(ctx)
        LuoghiVinted.carica(ctx)          // v1.2.0
        Offerte.carica(ctx)               // v1.4.0
        Offerte.aggiorna(ctx, motore) { revisioneOfferte++ }
        scopriUtenteSubito()
        leggiPreferitiSubito()

        motore.ascoltatore = object : MotoreRicerca.Ascoltatore {
            override fun risultati(fonte: String, annunci: List<Annuncio>, diagnostica: String) {
                errori.remove(fonte)
                conteggi[fonte] = (conteggi[fonte] ?: 0) + annunci.size
                aggiungi(annunci)
                finita()
            }
            override fun errore(fonte: String, messaggio: String) {
                errori[fonte] = messaggio
                finita()
            }
        }
    }

    // ------------------------------------------------------------------
    fun fonti(): List<Fonte> = buildList {
        if (usaSubito) add(FonteSubito)
        if (usaVinted) add(FonteVinted)
    }

    private fun chiaveOrdine() = when (ordine) {
        1 -> "prezzo_cresc"; 2 -> "prezzo_decr"; else -> "recenti"
    }

    fun cerca() {
        val q = query.trim()
        if (q.isEmpty()) return
        val f = fonti()
        if (f.isEmpty()) return
        modoPreferiti = false            // v1.8.0
        ultimaQuery = q
        pagina = 1
        grezzi.clear(); errori.clear(); conteggi.clear()
        primaRicercaFatta = true
        esaurito = false
        // v1.1.0 — si fotografa il "prima" adesso, non dopo
        precedenti = StoricoRicerche.precedenti(q)
        haPrecedente = StoricoRicerche.conosce(q)
        correnti.clear()
        ricordaRecente(q)
        // v1.4.0 — l'elenco delle offerte si rilegge al piu' ogni cinque minuti
        Offerte.aggiorna(ctx, motore) { revisioneOfferte++ }
        avvia(f)
    }

    /** Chiamata quando lo scorrimento si avvicina al fondo. */
    fun altraPagina() {
        if (modoPreferiti) return        // v1.8.0: l'elenco arriva tutto insieme
        if (inCorso || esaurito || ultimaQuery.isEmpty()) return
        val f = fonti()
        if (f.isEmpty()) return
        pagina++
        avvia(f)
    }

    fun riprova(fonte: String) {
        val f = FONTI.firstOrNull { it.nome == fonte } ?: return
        errori.remove(fonte)
        if (modoPreferiti) { apriPreferiti(); return }   // v1.9.0
        avvia(listOf(f))
    }

    private fun avvia(f: List<Fonte>) {
        dimensionePrima = grezzi.size
        attesa += f.size
        inCorso = true
        caricamento(true)
        motore.cerca(ultimaQuery, pagina, chiaveOrdine(), f)
    }

    private fun finita() {
        if (attesa > 0) attesa--
        inCorso = attesa > 0
        if (!inCorso && pagina > 1 && grezzi.size == dimensionePrima) esaurito = true
        // v1.1.0 — a passata conclusa questa diventa "la volta prima" per la successiva
        if (!inCorso && !modoPreferiti) StoricoRicerche.registra(ctx, ultimaQuery, correnti)
        caricamento(inCorso)
    }

    private fun aggiungi(nuovi: List<Annuncio>) {
        val gia = grezzi.mapTo(HashSet()) { it.fonte + it.id }
        /*
         * v1.2.0 — Vinted dichiara is_favourite nei risultati: e' verita' fresca
         * dalla piattaforma e vince sulla copia locale. Subito non lo dichiara,
         * e li' lo stato arriva dall'elenco letto a parte.
         */
        val dichiarati = HashMap<String, Boolean>()
        nuovi.forEach {
            correnti.add(it.chiave)      // v1.1.0: entra nell'istantanea di questa esecuzione
            if (it.fonte == "vinted") {
                dichiarati[it.chiave] = it.preferitoIniziale
                preferiti[it.chiave] = it.preferitoIniziale
            }
            if (gia.add(it.fonte + it.id) && !Nascosti.contiene(it.chiave)) grezzi.add(it)
        }
        if (dichiarati.isNotEmpty()) Preferiti.aggiorna(ctx, dichiarati)
        leggiPreferitiSubito()

        /*
         * v1.2.0 — i luoghi Vinted si chiedono solo per i venditori che
         * compaiono davvero in lista, e solo per quelli mai visti prima.
         * Si parte dai venditori appena aggiunti: la coda pensa al resto.
         */
        val venditori = nuovi.asSequence()
            .filter { it.fonte == "vinted" && it.venditore.isNotBlank() }
            .map { it.venditore }.distinct().toList()
        if (venditori.isNotEmpty()) {
            venditori.forEach { v -> LuoghiVinted.luogo(v)?.let { luoghiVinted[v] = it } }
            LuoghiVinted.richiedi(ctx, motore, venditori) { v, l -> luoghiVinted[v] = l }
        }
    }

    // ------------------------------------------------------------------
    /** Scarto con possibilita' di annullare: l'archivio si tocca solo alla conferma. */
    fun nascondi(a: Annuncio) {
        Nascosti.aggiungi(ctx, a.chiave, a.titolo)
        grezzi.removeAll { it.fonte == a.fonte && it.id == a.id }
    }

    fun annullaScarto(a: Annuncio) {
        Nascosti.rimuovi(ctx, a.chiave)
        if (grezzi.none { it.fonte == a.fonte && it.id == a.id }) grezzi.add(a)
    }

    // ------------------------------------------------------------------
    /** Lista visibile: filtri applicati a cio' che e' gia' stato scaricato. */
    val risultati: List<Annuncio>
        get() {
            val n = giorni.toLongOrNull() ?: 0L
            val soglia = System.currentTimeMillis() - n * 24L * 3600_000L
            val pMin = prezzoMin.replace(',', '.').toDoubleOrNull()
            val pMax = prezzoMax.replace(',', '.').toDoubleOrNull()

            val lista = grezzi.filter { a ->
                val perEta = when {
                    etaModo == 0 || n <= 0 -> true
                    a.dataMillis == 0L -> false      // senza data non si puo' affermare nulla
                    etaModo == 1 -> a.dataMillis <= soglia
                    else -> a.dataMillis >= soglia
                }
                val perPrezzo = when {
                    pMin == null && pMax == null -> true
                    a.prezzo == null -> false
                    pMin != null && a.prezzo < pMin -> false
                    pMax != null && a.prezzo > pMax -> false
                    else -> true
                }
                val perNovita = !soloNuovi || !precedenti.contains(a.chiave)   // v1.1.0
                perEta && perPrezzo && perNovita && (mostraVenduti || !a.venduto)
            }
            return when (ordine) {
                1 -> lista.sortedBy { it.prezzo ?: Double.MAX_VALUE }
                2 -> lista.sortedByDescending { it.prezzo ?: -1.0 }
                else -> lista.sortedByDescending { it.dataMillis }
            }
        }

    val esclusi: Int get() = grezzi.size - risultati.size

    val filtriAttivi: Int
        get() = listOf(
            etaModo != 0 && (giorni.toLongOrNull() ?: 0) > 0,
            prezzoMin.isNotBlank() || prezzoMax.isNotBlank(),
            mostraVenduti,
            soloNuovi,
            !usaSubito || !usaVinted,
            ordine != 0
        ).count { it }

    fun azzeraFiltri() {
        etaModo = 0; giorni = "30"; prezzoMin = ""; prezzoMax = ""
        mostraVenduti = false; soloNuovi = false
        usaSubito = true; usaVinted = true; ordine = 0
    }

    // ------------------------------------------------------------------
    private fun prefs() = ctx.getSharedPreferences("subitoplus", Context.MODE_PRIVATE)

    private fun caricaRecenti(): List<String> =
        prefs().getString("recenti", "")?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    private fun ricordaRecente(q: String) {
        val nuove = (listOf(q) + recenti.filter { !it.equals(q, true) }).take(8)
        recenti = nuove
        prefs().edit().putString("recenti", nuove.joinToString("\n")).apply()
    }

    fun dimenticaRecenti() {
        recenti = emptyList()
        prefs().edit().remove("recenti").apply()
    }
}
