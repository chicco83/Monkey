package it.monkey.app

/*
 * Subito+ — sonda delle offerte
 * v1.3.0 (2026-08-31) — nuovo
 *
 * A COSA SERVE
 * Mostrare «offerti xxx €» accanto a un annuncio richiede di sapere quali
 * offerte hai fatto e per quanto. Verificato sul browser, quel dato NON esiste
 * nelle risposte di ricerca:
 *
 *   hades.subito.it/v1/search/items -> ad = {urn, type, category, subject,
 *     body, dates, images, images_360, features, advertiser, geo, urls}
 *     features = /price, /item_condition, /computer_type, spedizione…
 *     nessun campo con offer|proposal|bid|trattativa in tutto il JSON
 *
 *   vinted /api/v2/catalog/items -> item = {id, title, price, brand_title,
 *     user, photos, favourite_count, is_favourite, service_fee,
 *     total_item_price, view_count, size_title, status, item_box}
 *     is_favourite c'e' (lo usa il cuore), delle offerte nulla
 *
 * Ed e' giusto cosi': la risposta di ricerca e' impersonale, non sa chi la
 * chiede. L'elenco delle offerte e' un'altra chiamata, da incrociare per id.
 *
 * COSA HA GIA' DETTO IL CAMPO
 *   GET https://hades.subito.it/v1/p2p/transactions
 *       -> 400 {"error_code":"TRANSACTIONS:bad-request",
 *               "info":"error decoding request: missing role param"}
 *   GET .../v1/p2p/transactions?role=buyer
 *       -> 401 AUTH:unknown-user   (browser non autenticato: non e' un rifiuto,
 *                                   e' l'assenza di sessione)
 * Il 400 dimostra che l'elenco esiste e vuole "role". Il resto — il nome dei
 * campi, dove sta l'importo, come si risale all'annuncio — si vede solo da
 * dentro una sessione vera. Questa sonda e' quella sessione: gira nel WebView
 * dell'app, con i tuoi cookie, e scrive quello che trova.
 *
 * REGOLE DI CONDOTTA
 * Solo GET. Nessuna chiamata che crei, modifichi o cancelli qualcosa: una sonda
 * che ordina per sbaglio non e' una sonda. Una richiesta per volta, con pausa,
 * perche' una raffica e' la firma che distingue un programma da una persona.
 */

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

object SondaOfferte {

    /** Quanto si conserva di ogni risposta: serve la forma, non i dati. */
    private const val MAX_CORPO = 4000

    /** Pausa fra una richiesta e la successiva. */
    private const val PAUSA_MS = 700L

    private val ui = Handler(Looper.getMainLooper())

    /*
     * I candidati. Ordine: prima quelli con una prova a favore, poi le ipotesi.
     * Un 404 o un 400 qui non e' un fallimento: e' informazione. L'unico esito
     * inutile e' non aver chiesto.
     */
    private val SUBITO = listOf(
        // Provato dal browser: esiste, vuole "role".
        "https://hades.subito.it/v1/p2p/transactions?role=buyer",
        "https://hades.subito.it/v1/p2p/transactions?role=seller",
        "https://hades.subito.it/v1/p2p/transactions?role=buyer&limit=50",
        // La sonda aveva catturato POST /v1/p2p/transactions/fullshipping/instances/<id>:
        // se esiste l'istanza, spesso esiste anche il suo elenco.
        "https://hades.subito.it/v1/p2p/transactions/fullshipping/instances",
        // Le offerte su Subito nascono nella chat: l'elenco delle conversazioni
        // potrebbe portarsi dietro l'ultimo importo proposto.
        "https://hades.subito.it/v1/conversations",
        "https://hades.subito.it/v1/messages/conversations"
    )

    private val VINTED = listOf(
        // Su Vinted un'offerta vive dentro una conversazione o un ordine.
        "/api/v2/conversations?page=1&per_page=20",
        "/api/v2/my_orders?type=purchase&page=1&per_page=20",
        "/api/v2/offers",
        "/api/v2/users/current"
    )

    private data class Esito(
        val fonte: String,
        val url: String,
        val stato: Int,
        val corpo: String
    )

    private val esiti = ArrayList<Esito>()

    @Synchronized fun quanti() = esiti.size
    @Synchronized fun azzera() = esiti.clear()

    /**
     * Esegue tutta la batteria, una chiamata per volta.
     * @param avanzamento invocato a ogni risposta con (fatte, totali)
     * @param finito invocato a fine giro
     */
    fun esegui(
        motore: MotoreRicerca,
        avanzamento: (Int, Int) -> Unit,
        finito: () -> Unit
    ) {
        synchronized(this) { esiti.clear() }
        val lavori = SUBITO.map { FonteSubito to it } + VINTED.map { FonteVinted to it }
        passo(motore, lavori, 0, avanzamento, finito)
    }

    private fun passo(
        motore: MotoreRicerca,
        lavori: List<Pair<Fonte, String>>,
        i: Int,
        avanzamento: (Int, Int) -> Unit,
        finito: () -> Unit
    ) {
        if (i >= lavori.size) { finito(); return }
        val (fonte, url) = lavori[i]
        motore.azione(fonte, Chiamata("GET", url)) { stato, corpo ->
            synchronized(this) {
                esiti.add(Esito(fonte.nome, url, stato, corpo.take(MAX_CORPO)))
            }
            avanzamento(i + 1, lavori.size)
            ui.postDelayed({ passo(motore, lavori, i + 1, avanzamento, finito) }, PAUSA_MS)
        }
    }

    /**
     * Il rapporto da esportare. Oltre alle risposte porta una lettura: quale
     * chiamata ha risposto, quale ha chiesto una sessione, quale non esiste.
     * Serve a non dover interpretare 400 e 401 a memoria fra una settimana.
     */
    @Synchronized
    fun json(): String {
        val arr = JSONArray()
        esiti.forEach { e ->
            arr.put(
                JSONObject()
                    .put("fonte", e.fonte)
                    .put("url", e.url)
                    .put("stato", e.stato)
                    .put("lettura", lettura(e.stato))
                    .put("corpo", e.corpo)
            )
        }
        return JSONObject()
            .put("versione", "1.3.0")
            .put("quante", esiti.size)
            .put("nota", "solo GET; corpi troncati a $MAX_CORPO caratteri")
            .put("esiti", arr)
            .toString(2)
    }

    private fun lettura(stato: Int) = when {
        stato in 200..299 -> "risposta buona: qui dentro c'e' la forma da leggere"
        stato == 400 -> "endpoint esistente, parametri sbagliati"
        stato == 401 || stato == 403 -> "sessione non riconosciuta su questa chiamata"
        stato == 404 -> "non esiste: candidato da scartare"
        stato < 0 -> "la chiamata non e' partita (rete o CORS)"
        else -> "stato $stato"
    }
}
