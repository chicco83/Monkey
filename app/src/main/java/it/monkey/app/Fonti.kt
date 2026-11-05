package it.monkey.app

/*
 * Subito+ — adattatori delle fonti
 * v0.4.0 (2026-08-30) — nuovo, Fase 3
 *
 * Un adattatore riceve una ricerca e restituisce una lista di Annuncio.
 * Non fa rete da solo: costruisce l'URL e interpreta la risposta.
 * A eseguire la chiamata e' il WebView della fonte (vedi MotoreRicerca),
 * perche' e' li' che vivono sessione e cookie veri.
 *
 * Aggiungere eBay o Wallapop domani = aggiungere un oggetto qui sotto.
 */

import org.json.JSONArray
import org.json.JSONObject

/*
 * v1.41.0 (2026-09-08) — annunci per pagina, raddoppiati da 30 a 60.
 *
 * Meno pagine per coprire la stessa ricerca vuol dire anche meno occasioni
 * di rimescolamento fra Subito e Vinted (vedi il commento su "confineFonte"
 * in StatoRicerca.kt): ogni pagina in piu' e' un punto in cui le due fonti
 * possono essersi allontanate nel tempo l'una dall'altra, quindi dimezzare
 * il numero di pagine dimezza anche quante volte quel disallineamento puo'
 * capitare. Da confermare sul campo che entrambe le API accettino un
 * valore diverso da 30 senza limitarlo per conto loro (nessuna sonda l'ha
 * mai verificato: finora si e' sempre chiesto lo stesso numero che il sito
 * vero chiede).
 *
 * --- versione precedente (fino alla v1.40.0, valore 30 scritto a mano
 *     in FonteSubito.url() — "lim=30" — e in FonteVinted.url() —
 *     "per_page=30") ---
 * (nessuna costante: ogni fonte aveva il proprio "30" scritto direttamente
 *  nell'URL)
 * --- fine versione precedente ---
 */
/*
 * v1.44.0 (2026-09-08) — CORREZIONE: il "da confermare sul campo" della
 * v1.41.0 e' stato confermato, e ha risposto NO per Vinted. Diagnostica
 * mandata dall'utente ("errore 500 da vinted"):
 *
 *   [subito] .../search/items?...&lim=60&start=0 -> 60 annunci (ok)
 *   [vinted] /api/v2/catalog/items?...&per_page=60&page=1 ->
 *            stato 500 — {"code":105,"message":"Errore del server",
 *                          "message_code":"server_error"}
 *
 * Stessa ricerca, stesso momento: Subito accetta 60 senza problemi, Vinted
 * no — non un rifiuto pulito (400/422), un 500 vero e proprio, segno che il
 * valore manda in errore qualcosa lato server invece di essere solo
 * ignorato o troncato. La costante quindi si sdoppia: ANNUNCI_PER_PAGINA
 * resta 60 per Subito (confermato buono dalla stessa diagnostica),
 * ANNUNCI_PER_PAGINA_VINTED torna al 30 di sempre (mai fallito, in nessuna
 * versione fino alla 1.40.0) finche' una sonda non dice qual e' il vero
 * tetto di Vinted (se uno ce n'e', magari un valore fisso come 20 o 24, non
 * necessariamente "raddoppiare non si puo' per niente"). Il raddoppio delle
 * pagine dell'annuncio precedente resta com'era solo per Subito.
 */
const val ANNUNCI_PER_PAGINA = 60
const val ANNUNCI_PER_PAGINA_VINTED = 30

interface Fonte {
    val nome: String
    val origine: String                 // pagina da cui deve partire la chiamata

    /*
     * v1.56.0 (2026-09-16) — true quando questa fonte non risponde piu' con
     * JSON da un endpoint di rete per l'elenco di ricerca: vedi il grande
     * commento in cima a FonteVinted.url() per la storia completa (era
     * "/api/v2/catalog/items", ritirato, sostituito da qui in avanti dalla
     * lettura della pagina vera). Quando e' vero, MotoreRicerca.avvia() non
     * fa una fetch: naviga la pagina intera in una sessione DEDICATA (mai
     * condivisa con quella di azione(), apposta per non ripetere la
     * regressione della v1.50.0/v1.51.0 — sessione condivisa e navigazioni
     * concorrenti) e leggi() interpreta il DOM invece di una risposta HTTP.
     * Solo Vinted, per ora: Subito continua esattamente come prima.
     */
    val usaPaginaDom: Boolean get() = false

    fun url(q: String, pagina: Int, ordine: String): String
    fun leggi(json: String): List<Annuncio>
    fun chiavi(json: String): String    // diagnostica: com'e' fatto il primo elemento

    /*
     * v1.2.0 — preferiti. I contratti sono quelli osservati sul campo con la
     * sonda, non dedotti: due piattaforme, due modi completamente diversi.
     *
     * @param utente identificatore dell'utente sulla piattaforma; stringa vuota
     *        quando non e' ancora noto, e in quel caso il preferito non si tocca.
     */
    fun preferitoAggiungi(a: Annuncio, utente: String): Chiamata?
    fun preferitoRimuovi(a: Annuncio, utente: String): Chiamata?
    /** Elenco dei preferiti gia' presenti sulla piattaforma; null se non serve. */
    fun preferitiElenco(utente: String): Chiamata?
    /** Dalle risposta dell'elenco alle chiavi usate dall'app. */
    fun preferitiLeggi(json: String): List<String>

    /*
     * v1.8.0 — gli stessi preferiti, ma come annunci da mostrare.
     * La forma della risposta e' stata osservata una volta sola, quindi qui si
     * e' tolleranti: si cerca l'elenco fra i nomi possibili e si scarta cio'
     * che non si riesce a leggere, invece di pretendere una struttura esatta.
     * Se torna vuoto, la schermata lo dice e offre di aprire il sito: meglio
     * un vicolo cieco dichiarato che una lista vuota senza spiegazione.
     */
    fun preferitiAnnunci(json: String): List<Annuncio>
}

/**
 * Una chiamata da eseguire dentro la sessione della fonte.
 *
 * v1.6.0 — due aggiunte, entrambe nate da un errore visto sul telefono:
 *
 * @param csrf    aggiunge le intestazioni anti-CSRF raccolte NELLA PAGINA al
 *                momento della chiamata (vedi MotoreRicerca). Vinted rifiuta
 *                con 403 "access_denied" ogni POST che non le porta: la GET
 *                passa con i soli cookie, la POST no. E' la ragione per cui il
 *                cuore su Vinted non salvava.
 * @param ripiego una seconda strada da provare se la prima non risponde 2xx.
 *                Serve dove il contratto e' stato osservato una volta sola e
 *                non si sa se l'indirizzo giusto sia quello relativo al sito o
 *                quello diretto: si prova, non si indovina.
 * @param paginaPrima v1.26.0 — se non nulla, la sessione ci naviga PRIMA di
 *                fare la chiamata (e aspetta che finisca di caricare). Nata
 *                per i preferiti Vinted: vedi il commento su MotoreRicerca.azione().
 */
data class Chiamata(
    val metodo: String,
    val url: String,
    val corpo: String? = null,
    val csrf: Boolean = false,
    val ripiego: Chiamata? = null,
    val paginaPrima: String? = null
)

/* --------------------------------------------------------------------- */

object FonteSubito : Fonte {
    override val nome = "subito"
    override val origine = "https://www.subito.it/"

    /*
     * Endpoint confermato dalla sonda:
     *   https://hades.subito.it/v1/search/items?c=2&t=s&lim=0
     * Risposta: { count_all, lines, start, filters, checknew, ads:[...] }
     * t=s -> annunci in vendita. sort=datedesc -> dal piu' recente.
     */
    /*
     * v1.41.0 — "lim=30" fisso e "start" calcolato su 30 diventano
     * ANNUNCI_PER_PAGINA (60): vedi il commento sulla costante, in cima al
     * file, per il perche'.
     *
     * --- versione precedente (fino alla v1.40.0) ---
     * override fun url(q: String, pagina: Int, ordine: String): String {
     *     val start = (pagina - 1) * 30
     *     val sort = when (ordine) {
     *         "prezzo_cresc" -> "priceasc"
     *         "prezzo_decr" -> "pricedesc"
     *         else -> "datedesc"
     *     }
     *     return "https://hades.subito.it/v1/search/items" +
     *             "?q=" + enc(q) + "&t=s&lim=30&start=$start&sort=$sort"
     * --- fine versione precedente ---
     */
    override fun url(q: String, pagina: Int, ordine: String): String {
        val start = (pagina - 1) * ANNUNCI_PER_PAGINA
        val sort = when (ordine) {
            "prezzo_cresc" -> "priceasc"
            "prezzo_decr" -> "pricedesc"
            else -> "datedesc"
        }
        return "https://hades.subito.it/v1/search/items" +
                "?q=" + enc(q) + "&t=s&lim=$ANNUNCI_PER_PAGINA&start=$start&sort=$sort"
    }

    override fun leggi(json: String): List<Annuncio> {
        val root = JSONObject(json)
        val ads = root.optJSONArray("ads") ?: return emptyList()
        val out = ArrayList<Annuncio>(ads.length())
        for (i in 0 until ads.length()) {
            val a = ads.optJSONObject(i) ?: continue
            val id = idDaUrn(a.optString("urn")) ?: continue

            // Il prezzo sta fra le "features", riconoscibile dall'uri "/price".
            var prezzoTesto = ""
            var prezzo: Double? = null
            val fs = a.optJSONArray("features")
            if (fs != null) for (k in 0 until fs.length()) {
                val f = fs.optJSONObject(k) ?: continue
                if (f.optString("uri") != "/price") continue
                val v = f.optJSONArray("values")?.optJSONObject(0) ?: continue
                prezzo = v.optString("key").toDoubleOrNull()
                prezzoTesto = v.optString("value")
            }

            /*
             * v0.4.6 — CORREZIONE. cdn_base_url senza parametri restituisce la foto
             * originale, diversi mega, per riempire un riquadro di un centimetro:
             * era la ragione principale delle attese lunghissime in lista.
             * "rule" e' il nome della resa lato CDN, osservato nel traffico del sito.
             *
             * --- versione precedente (v0.4.0, 2026-08-30) ---
             * val img = a.optJSONArray("images")?.optJSONObject(0)?.optString("cdn_base_url")
             * --- fine versione precedente ---
             */
            val immaginiArr = a.optJSONArray("images")
            val imgBase = immaginiArr?.optJSONObject(0)?.optString("cdn_base_url")
            val img = if (imgBase.isNullOrBlank()) null
                      else if (imgBase.contains("?")) imgBase
                      else "$imgBase?rule=large-fixed-card-3x-auto"
            /*
             * v1.7.0 — resa grande per la vista estesa. I nomi delle rese non
             * si inventano: provati uno per uno sul CDN vero.
             *   gallery-mobile-3x-auto   1164x748   <- questa
             *   gallery-desktop-3x-auto  1860x1196  (troppo per un telefono)
             *   large-fixed-card-3x-auto  900x600   (quella d'elenco)
             *   gallery-3x-auto, large, hero-3x-auto, zoom-3x-auto -> errore
             */
            val imgGrande = if (imgBase.isNullOrBlank() || imgBase.contains("?")) img
                            else "$imgBase?rule=gallery-mobile-3x-auto"
            /*
             * v1.32.0 — TUTTE le foto, non solo la prima: stesso array
             * "images", stessa resa "gallery-mobile-3x-auto" gia' verificata
             * sopra per la posizione 0, solo applicata a ogni elemento invece
             * che al solo primo. Serve alla vista estesa per farle scorrere
             * (SchermataRicerca.RigaAnnuncio).
             */
            val immagini = if (immaginiArr != null) (0 until immaginiArr.length()).mapNotNull { k ->
                val base = immaginiArr.optJSONObject(k)?.optString("cdn_base_url")
                when {
                    base.isNullOrBlank() -> null
                    base.contains("?") -> base
                    else -> "$base?rule=gallery-mobile-3x-auto"
                }
            } else emptyList()

            Annuncio(
                fonte = nome,
                id = id,
                titolo = a.optString("subject"),
                prezzo = prezzo,
                prezzoTesto = if (prezzoTesto.isBlank()) "—" else prezzoTesto,
                luogo = luogo(a),
                dettaglio = condizioneSubito(a),   // v1.1.0
                venduto = venduto(a),          // v0.5.1
                dataMillis = iso(a.optJSONObject("dates")?.optString("display_iso8601")),
                immagine = img,
                immagineGrande = imgGrande,               // v1.7.0
                immagini = immagini,                       // v1.32.0
                descrizione = a.optString("body").trim(), // v1.7.0
                url = indirizzo(a, id),
                urn = a.optString("urn"),         // v1.2.0: serve ai preferiti
                /*
                 * v1.30.0 (2026-09-01) — NUOVO: fino a qui "venditore" restava
                 * vuoto per Subito (il campo esiste in Annuncio dalla v1.2.0
                 * ma solo Vinted lo riempiva, per il luogo). "advertiser.user_id"
                 * c'e' gia' in ogni risultato di ricerca — verificato sul campo,
                 * es. "advertiser":{"user_id":"144352772",...} — e ora serve
                 * alle recensioni del venditore (RecensioniSubito.kt), stesso
                 * ruolo che "venditore" gia' aveva per Vinted.
                 */
                venditore = a.optJSONObject("advertiser")?.optString("user_id").orEmpty()
            ).let { out.add(it) }
        }
        return out
    }

    /*
     * v0.5.1 — annuncio gia' venduto.
     * Subito non espone un campo booleano dedicato: l'informazione arriva come
     * una delle "features", con etichetta o valore testuale. Si cerca quindi il
     * marcatore nel testo, senza dipendere dal nome esatto del campo, che e'
     * proprio il genere di cosa che cambia da una versione all'altra del sito.
     */
    private fun venduto(a: JSONObject): Boolean {
        val fs = a.optJSONArray("features") ?: return false
        for (i in 0 until fs.length()) {
            val f = fs.optJSONObject(i) ?: continue
            val uri = f.optString("uri").lowercase()
            if (uri.contains("sold") || uri.contains("transaction_status") || uri.contains("item_status")) {
                val v = f.optJSONArray("values")
                if (v != null) for (k in 0 until v.length()) {
                    val o = v.optJSONObject(k) ?: continue
                    val t = (o.optString("key") + " " + o.optString("value")).lowercase()
                    if (t.contains("vendut") || t.contains("sold")) return true
                }
            }
            val v2 = f.optJSONArray("values")
            if (v2 != null) for (k in 0 until v2.length()) {
                val o = v2.optJSONObject(k) ?: continue
                if (o.optString("value").lowercase().trim() == "venduto") return true
            }
        }
        return false
    }

    /** urn della forma id:ad:<uuid>:list:<numero> */
    private fun idDaUrn(urn: String): String? {
        val p = urn.split(":")
        return p.lastOrNull()?.takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } }
    }

    /** v1.1.0 — la condizione sta fra le features, come il prezzo. */
    private fun condizioneSubito(a: JSONObject): String {
        val fs = a.optJSONArray("features") ?: return ""
        for (i in 0 until fs.length()) {
            val f = fs.optJSONObject(i) ?: continue
            if (f.optString("uri") != "/item_condition") continue
            val v = f.optJSONArray("values")?.optJSONObject(0) ?: continue
            return v.optString("value").orEmpty()
        }
        return ""
    }

    private fun luogo(a: JSONObject): String {
        val g = a.optJSONObject("geo") ?: return ""
        val citta = g.optJSONObject("town")?.optString("value") ?: ""
        val prov = g.optJSONObject("city")?.optString("short_name")
            ?: g.optJSONObject("region")?.optString("short_name") ?: ""
        return listOf(citta, prov).filter { it.isNotBlank() }.joinToString(" ")
    }

    /*
     * L'indirizzo della scheda: si preferisce sempre quello dichiarato dalla
     * risposta. La ricostruzione a mano e' solo una rete di sicurezza, perche'
     * lo slug include anche il comune e non e' garantito che coincida.
     */
    private fun indirizzo(a: JSONObject, id: String): String {
        a.optJSONObject("urls")?.let { u ->
            for (k in listOf("default", "mobile", "desktop")) {
                val v = u.optString(k)
                if (v.isNotBlank()) return assoluto(v, "https://www.subito.it")   // v0.4.1
            }
        }
        val cat = a.optJSONObject("category")?.optString("friendly_name") ?: "annunci"
        val slug = slug(a.optString("subject"))
        return "https://www.subito.it/$cat/$slug-$id.htm"
    }

    override fun chiavi(json: String): String =
        (JSONObject(json).optJSONArray("ads")?.optJSONObject(0)?.keys()
            ?.asSequence()?.toList() ?: emptyList<String>()).joinToString(",")

    /*
     * v1.2.0 — preferiti Subito, contratti osservati con la sonda:
     *   POST   /hades/v1/favorites/search/users/<id>/items   {"urn":"..."}   -> 200
     *   DELETE /hades/v1/favorites/search/users/<id>/items?urns=<urn>        -> 204
     * Il percorso e' relativo a www.subito.it, che fa da proxy verso hades:
     * chiamato in relativo resta stessa origine e si porta dietro la sessione.
     *
     * Attenzione all'identificatore: qui vuole l'URN lungo, non il numero che
     * compare nel link della scheda.
     */
    /*
     * v1.6.0 — l'indirizzo dei preferiti in due forme.
     * La sonda l'aveva visto come percorso relativo al sito (www.subito.it fa
     * da proxy verso hades). Se quel proxy non c'e' o cambia, la stessa risorsa
     * sta sul servizio diretto. Si prova la prima, e la seconda solo se serve.
     */
    private fun base(utente: String) = "/hades/v1/favorites/search/users/$utente/items"
    private fun baseDiretta(utente: String) =
        "https://hades.subito.it/v1/favorites/search/users/$utente/items"

    override fun preferitoAggiungi(a: Annuncio, utente: String): Chiamata? {
        if (utente.isBlank() || a.urn.isBlank()) return null
        val corpo = JSONObject().put("urn", a.urn).toString()
        return Chiamata("POST", base(utente), corpo,
            ripiego = Chiamata("POST", baseDiretta(utente), corpo))
    }

    override fun preferitoRimuovi(a: Annuncio, utente: String): Chiamata? {
        if (utente.isBlank() || a.urn.isBlank()) return null
        val q = "?urns=" + enc(a.urn)
        return Chiamata("DELETE", base(utente) + q,
            ripiego = Chiamata("DELETE", baseDiretta(utente) + q))
    }

    override fun preferitiElenco(utente: String): Chiamata? {
        if (utente.isBlank()) return null
        return Chiamata("GET", base(utente) + "?limit=200",
            ripiego = Chiamata("GET", baseDiretta(utente) + "?limit=200"))
    }

    /*
     * v1.10.0 (2026-08-31) — CORREZIONE: l'elenco preferiti non porta annunci,
     * porta soltanto riferimenti.
     *
     * Verificato sul campo (era l'errore "elenco non letto" mostrato in app):
     *   GET .../favorites/search/users/<id>/items
     *   -> {"favorite_ads":[{"urn":"id:ad:cd0a0bd6-…:list:658151441",
     *                        "insert_date":"2026-08-31 10:43:14"}, …]}
     * Niente titolo, prezzo, foto: solo l'urn e quando l'hai messo nei
     * preferiti. La v1.8.0 lo trattava come se fosse gia' un elenco di
     * annunci, con la stessa forma della ricerca — e infatti non lo era.
     *
     * Ogni urn si risolve con una seconda chiamata, anch'essa verificata sul
     * browser:
     *   GET .../v1/search/items?urn=<urn>  ->  {"ads":[<un solo annuncio>]}
     * la STESSA forma della ricerca normale: si riusa leggi() invariato.
     * Provato anche a chiedere piu' annunci insieme: "urn" ripetuto tiene
     * solo l'ULTIMO valore, "ids" viene ignorato del tutto e torna un elenco
     * a caso. Non esiste una chiamata multipla: una richiesta per annuncio,
     * a due alla volta (vedi StatoRicerca.risolviPreferitiSubito).
     *
     * Questo metodo quindi non basta piu' da solo: resta per compatibilita'
     * dell'interfaccia, la vista reale usa preferitiUrns() + preferitoRisolvi().
     *
     * --- versione precedente (v1.8.0, 2026-08-31) ---
     * override fun preferitiAnnunci(json: String): List<Annuncio> = try {
     *     val r = JSONObject(json)
     *     val arr = r.optJSONArray("ads") ?: r.optJSONArray("items")
     *         ?: r.optJSONArray("favorites") ?: r.optJSONObject("data")?.optJSONArray("ads")
     *     … normalizzava e chiamava leggi() — l'elenco pero' non ha questi campi
     * } catch (t: Throwable) { emptyList() }
     * --- fine versione precedente ---
     */
    override fun preferitiAnnunci(json: String): List<Annuncio> = emptyList()

    /** Gli urn dei preferiti, nell'ordine dell'elenco (il piu' recente per primo). */
    fun preferitiUrns(json: String): List<String> {
        val arr = try { JSONObject(json).optJSONArray("favorite_ads") } catch (t: Throwable) { null }
            ?: return emptyList()
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.optString("urn")?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        }
        return out
    }

    /** La chiamata che risolve un singolo urn in un annuncio completo. */
    fun preferitoRisolvi(urn: String): Chiamata =
        Chiamata("GET", "https://hades.subito.it/v1/search/items?urn=" + enc(urn))

    override fun preferitiLeggi(json: String): List<String> {
        val out = ArrayList<String>()
        val radice = try { JSONObject(json) } catch (t: Throwable) { null }
        // v1.10.0 — il nome vero e' "favorite_ads" (vedi sopra); gli altri restano
        // come rete di sicurezza se la forma dovesse cambiare ancora.
        val arr = radice?.optJSONArray("favorite_ads")
            ?: radice?.optJSONArray("items")
            ?: radice?.optJSONArray("favorites")
            ?: radice?.optJSONArray("ads")
            ?: try { JSONArray(json) } catch (t: Throwable) { null }
            ?: return out
        for (i in 0 until arr.length()) {
            val urn = when (val v = arr.opt(i)) {
                is JSONObject -> v.optString("urn")
                is String -> v
                else -> ""
            }
            urn.split(":").lastOrNull()
                ?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }
                ?.let { out.add(it) }
        }
        return out
    }
}

/* --------------------------------------------------------------------- */

object FonteVinted : Fonte {
    override val nome = "vinted"
    override val origine = "https://www.vinted.it/"

    /*
     * v1.56.0 (2026-09-16) — "/api/v2/catalog/items" (sotto, come storia) e'
     * andato per sempre: quattro sonde di fila (v1.52.0 -> v1.55.0) lo hanno
     * confermato passo per passo.
     *
     *   v1.52.0: l'errore era un vero 404 di rotta sparita, non un guasto
     *            temporaneo — la pagina di ricerca vera non fa PIU' nessuna
     *            chiamata fetch/XHR che porti l'elenco.
     *   v1.53.0: niente JSON nascosto in nessuno script ne' nel buffer di
     *            streaming di Next.js (window.__next_f) — la pista JSON e'
     *            chiusa per davvero, non solo "non ancora trovata".
     *   v1.54.0: la pagina contiene pero' link HTML veri a ogni annuncio
     *            ("/items/<id>") — l'elenco e' HTML gia' pronto, va letto
     *            dal DOM, non da una risposta.
     *   v1.55.0: trovato il contenitore esatto di ogni card —
     *            "[data-testid='product-item-id-<id>']" (senza "--" dopo
     *            l'id, quelli sono i pezzi interni) — e il formato, sempre
     *            uguale su 10 annunci diversi controllati, dell'attributo
     *            "alt" della sua immagine:
     *
     *              "<titolo>, Brand: <marca>, Condizioni: <condizione>,
     *               <prezzo> €, <prezzo totale> €"
     *
     *            (il secondo prezzo include la Protezione acquisti, si
     *            legge nell'HTML vero: aria-label="…€ include la
     *            Protezione acquisti" — non e' uno sconto/prezzo barrato).
     *
     * Da questa versione url() NON e' piu' un percorso relativo per una
     * fetch dentro la sessione di sempre (quella di MotoreRicerca.azione(),
     * ancora usata per preferiti e traduzione): e' l'indirizzo INTERO della
     * pagina di ricerca vera di vinted.it, che MotoreRicerca.avviaDom()
     * naviga per davvero in una sessione SEPARATA e dedicata (vedi il
     * commento su "usaPaginaDom" nell'interfaccia Fonte, e quello su
     * avviaDom() in MotoreRicerca.kt per il perche' una sessione a parte:
     * non e' un capriccio, e' la lezione della regressione v1.50.0/v1.51.0).
     * leggi() smista da solo fra questo nuovo formato e quello vecchio
     * (ancora vivo per l'elenco preferiti, che e' un endpoint diverso e
     * MAI stato toccato da questo ritiro) guardando se il testo comincia
     * con "[" (il nostro elenco) o con "{" (la vecchia risposta JSON).
     *
     * "search_id" (visto nell'indirizzo che Vinted stesso genera navigando
     * col browser) non compare qui: e' un identificatore di telemetria che
     * l'interfaccia di Vinted si assegna da sola, non un filtro — la sonda
     * l'ha sempre visto in QUALUNQUE ricerca, con un valore diverso ogni
     * volta, mai lo stesso a parita' di ricerca: non e' cio' che determina
     * i risultati. "page=$pagina" resta, come nella vecchia rotta, ma NON
     * e' stato ancora verificato che la pagina 2 in avanti porti annunci
     * diversi dalla 1 invece di ripetere la stessa — vedi il commento su
     * avviaDom() per cosa succede in quel caso (nulla di rotto: gli
     * annunci gia' visti vengono scartati come doppioni e la fonte viene
     * segnata esaurita, lo stesso comportamento sicuro gia' in
     * StatoRicerca per ogni fonte che smette di portare novita').
     *
     * --- versione precedente (v1.44.0 -> v1.55.0, "/api/v2/catalog/items",
     *     endpoint ritirato da Vinted — la storia sopra) ---
     * override fun url(q: String, pagina: Int, ordine: String): String {
     *     val order = when (ordine) {
     *         "prezzo_cresc" -> "price_low_to_high"
     *         "prezzo_decr" -> "price_high_to_low"
     *         else -> "newest_first"
     *     }
     *     return "/api/v2/catalog/items?search_text=" + enc(q) +
     *             "&per_page=$ANNUNCI_PER_PAGINA_VINTED&page=$pagina&order=$order"
     * }
     * --- fine versione precedente ---
     */
    override val usaPaginaDom = true

    override fun url(q: String, pagina: Int, ordine: String): String {
        val order = when (ordine) {
            "prezzo_cresc" -> "price_low_to_high"
            "prezzo_decr" -> "price_high_to_low"
            else -> "newest_first"
        }
        return "https://www.vinted.it/catalog?search_text=" + enc(q) +
                "&order=$order&page=$pagina"
    }

    /*
     * v1.58.0 (2026-09-24) — LETTORE DELL'"alt" RISCRITTO, non piu' a
     * modelli fissi.
     *
     * Segnalazione dell'utente con screenshot: "molti annunci Vinted non
     * mostrano il prezzo nell'anteprima" (ricerca "galaxy watch 4 lte": tutte
     * le card con "—" al posto del prezzo e l'intero "alt" come titolo, cioe'
     * il lettore non aveva riconosciuto il formato). Nello screenshot si
     * legge la prova: "Samsung Galaxy watch 4 LTE 44mm, Condizioni: Buone,
     * Ta…" — dopo la condizione c'e' un pezzo "Taglia: …" che nessuno dei due
     * modelli v1.57.0 prevedeva (orologi e abbigliamento hanno la taglia, le
     * CPU della sonda v1.55.0 no: per questo i dieci esempi di allora erano
     * tutti uguali). Aggiungere un terzo e un quarto modello avrebbe solo
     * rimandato il prossimo caso; si legge invece la STRUTTURA, che e' stabile
     * in tutti gli esempi visti finora:
     *
     *   <titolo>, <Etichetta>: <valore>, <Etichetta>: <valore>, ...,
     *   <prezzo> €, <prezzo con protezione> €
     *
     * 1. si staccano dalla coda i pezzi "numero + €" (il PRIMO dei due e' il
     *    prezzo dell'articolo, il secondo include la Protezione acquisti —
     *    confermato in v1.56.0 dall'aria-label della card);
     * 2. il primo pezzo "Parola: valore" (dopo il primo) segna la fine del
     *    titolo — il titolo puo' contenere virgole, resta tutto insieme;
     * 3. da li' in poi ogni "Etichetta: valore" e' un campo; un pezzo senza
     *    etichetta e' la continuazione del valore precedente (un valore con
     *    una virgola dentro, es. una marca "A, B").
     * Le etichette note si usano per nome (Brand/Marca, Condizioni/Stato,
     * Taglia); quelle mai viste finiscono comunque nel dettaglio come
     * "Etichetta valore", e la diagnostica le elenca tutte (chiaviDom()).
     *
     * Il numero del prezzo accetta sia "50.00" (visto) sia "1.234,50" o
     * "1,234.50" (mai visti, ma e' il primo posto dove si romperebbe se
     * Vinted cambiasse formato per i prezzi sopra 999), e gli spazi "non
     * separabili" ( ,  ) che la regex v1.57.0 con "\s" non
     * avrebbe riconosciuto.
     *
     * --- versione precedente (v1.57.0, due modelli fissi) ---
     * private val RE_ALT_CON_MARCA = Regex(
     *     "^(.*), Brand: ([^,]*), Condizioni: ([^,]*), " +
     *             "([0-9]+(?:\\.[0-9]+)?)\\s?€, ([0-9]+(?:\\.[0-9]+)?)\\s?€$"
     * )
     * private val RE_ALT_SENZA_MARCA = Regex(
     *     "^(.*), Condizioni: ([^,]*), " +
     *             "([0-9]+(?:\\.[0-9]+)?)\\s?€, ([0-9]+(?:\\.[0-9]+)?)\\s?€$"
     * )
     * private data class CampiAlt(val titolo: String, val marca: String, val condizione: String, val prezzo: Double?)
     * private fun scomponiAlt(alt: String): CampiAlt {
     *     val a = alt.trim()
     *     RE_ALT_CON_MARCA.matchEntire(a)?.let { m ->
     *         val (titolo, marca, condizione, prezzoTxt, _) = m.destructured
     *         return CampiAlt(titolo.trim(), marca.trim(), condizione.trim(), prezzoTxt.toDoubleOrNull())
     *     }
     *     RE_ALT_SENZA_MARCA.matchEntire(a)?.let { m ->
     *         val (titolo, condizione, prezzoTxt, _) = m.destructured
     *         return CampiAlt(titolo.trim(), "", condizione.trim(), prezzoTxt.toDoubleOrNull())
     *     }
     *     return CampiAlt(a, "", "", null)
     * }
     * --- fine versione precedente ---
     *
     * --- versione ancora precedente (v1.56.0, un solo modello, "Brand: "
     *     sempre obbligatorio) ---
     * private val RE_ALT = Regex(
     *     "^(.*), Brand: ([^,]*), Condizioni: ([^,]*), " +
     *             "([0-9]+(?:\\.[0-9]+)?)\\s?€, ([0-9]+(?:\\.[0-9]+)?)\\s?€$"
     * )
     * --- fine versione precedente ---
     */
    private const val SPAZI = "\\s\\u00a0\\u202f"

    /** Un pezzo fatto solo di prezzo: "50.00 €", "€ 50", "1.234,50 €". */
    private val RE_PEZZO_PREZZO = Regex("^€?[$SPAZI]*[0-9][0-9.,$SPAZI]*[$SPAZI]*€?$")

    /** Un pezzo "Etichetta: valore" (etichetta di sole lettere/spazi, corta). */
    private val RE_ETICHETTA = Regex("^([\\p{L}][\\p{L} ]{0,24}):[$SPAZI]+(.*)$")

    private val ETICHETTE_MARCA = setOf("brand", "marca")
    private val ETICHETTE_CONDIZIONE = setOf("condizioni", "condizione", "stato")
    private val ETICHETTE_TAGLIA = setOf("taglia", "misura")

    private data class CampiAlt(
        val titolo: String,
        val marca: String,
        val condizione: String,
        val taglia: String,
        /** etichette non riconosciute, gia' pronte come "Etichetta valore" */
        val altri: List<String>,
        val prezzo: Double?,
        /** tutte le etichette trovate, per la diagnostica */
        val etichette: List<String>
    )

    /** "50.00" -> 50.0, "1.234,50" -> 1234.5, "1,234.50" -> 1234.5, "1.234" -> 1234.0 */
    private fun numeroPrezzo(testo: String): Double? {
        val t = testo.replace(Regex("[^0-9.,]"), "")
        if (t.isEmpty()) return null
        val sep = maxOf(t.lastIndexOf('.'), t.lastIndexOf(','))
        val decimali = if (sep >= 0) t.length - sep - 1 else 0
        return if (sep >= 0 && decimali in 1..2) {
            (t.substring(0, sep).replace(Regex("[.,]"), "") + "." + t.substring(sep + 1)).toDoubleOrNull()
        } else {
            t.replace(Regex("[.,]"), "").toDoubleOrNull()
        }
    }

    /**
     * v1.58.0 — vedi il commento sopra. Un "alt" di cui non si riconosce
     * nulla resta tutto come titolo, senza prezzo (come prima): non si scarta
     * mai un annuncio per un formato inatteso — lo conta la diagnostica.
     */
    private fun scomponiAlt(alt: String): CampiAlt {
        val a = alt.trim()
        val pezzi = a.split(", ").toMutableList()

        // 1) prezzi in coda (al massimo due, e sempre almeno un pezzo di titolo)
        val prezzi = ArrayList<String>()
        while (pezzi.size > 1 && prezzi.size < 2) {
            val p = pezzi.last().trim()
            if (p.contains('€') && RE_PEZZO_PREZZO.matches(p)) {
                prezzi.add(0, p); pezzi.removeAt(pezzi.lastIndex)
            } else break
        }
        val prezzo = prezzi.firstOrNull()?.let { numeroPrezzo(it) }

        // 2) fine del titolo = primo pezzo etichettato dopo il primo
        val inizioCampi = (1 until pezzi.size).firstOrNull { RE_ETICHETTA.matches(pezzi[it].trim()) } ?: pezzi.size
        val titolo = pezzi.subList(0, inizioCampi).joinToString(", ").trim()

        // 3) campi etichettati; un pezzo senza etichetta continua il valore precedente
        val campi = LinkedHashMap<String, String>()
        var ultima: String? = null
        for (j in inizioCampi until pezzi.size) {
            val pezzo = pezzi[j].trim()
            val m = RE_ETICHETTA.matchEntire(pezzo)
            if (m != null) {
                ultima = m.groupValues[1].trim()
                campi[ultima] = m.groupValues[2].trim()
            } else if (ultima != null) {
                campi[ultima] = campi[ultima] + ", " + pezzo
            }
        }

        var marca = ""; var condizione = ""; var taglia = ""
        val altri = ArrayList<String>()
        for ((etichetta, valore) in campi) {
            when (etichetta.lowercase()) {
                in ETICHETTE_MARCA -> marca = valore
                in ETICHETTE_CONDIZIONE -> condizione = valore
                in ETICHETTE_TAGLIA -> taglia = valore
                else -> if (valore.isNotBlank()) altri.add("$etichetta $valore")
            }
        }
        return CampiAlt(titolo.ifBlank { a }, marca, condizione, taglia, altri, prezzo, campi.keys.toList())
    }

    /** v1.58.0 — la riga "dettaglio": marca · condizione · taglia · altro. */
    private fun dettaglioDa(c: CampiAlt): String =
        (listOf(c.marca, c.condizione, c.taglia.takeIf { it.isNotBlank() }?.let { "Taglia $it" }.orEmpty()) + c.altri)
            .filter { it.isNotBlank() }
            .joinToString(" · ")

    /**
     * v1.56.0 — l'elenco letto dalla pagina vera (MotoreRicerca.avviaDom()),
     * non da una risposta dell'API: "annunci" qui e' l'array che la nostra
     * stessa estrazione JS ha costruito dal DOM, un oggetto per annuncio
     * con id/href/alt/src/pref — vedi il commento su avviaDom().
     *
     * Due campi restano vuoti rispetto a prima, onestamente non disponibili
     * nella scheda dell'elenco (si vedevano solo nella vecchia risposta
     * JSON, ora sparita):
     *  - venditore: nessun id venditore nella card — LuoghiVinted (il
     *    luogo, gia' "" da sempre qui, vedi sotto) semplicemente non ha
     *    nulla da risolvere, si comporta come quando venditore era gia'
     *    vuoto per altri motivi.
     *  - venduto: nessun indizio "venduto" visto finora in una card (i
     *    dieci esempi controllati erano tutti in vendita) — resta "false"
     *    finche' una diagnostica non mostra come si presenta un annuncio
     *    venduto in elenco, invece di indovinare un testo o una classe CSS
     *    mai vista.
     *
     * v1.57.0 (2026-09-16) — la stima ordinale diventa continua fra le
     * pagine (VOCI_STIMATE_PER_PAGINA): prima ripartiva da "ora" a ogni
     * pagina e gli annunci Vinted sparivano scorrendo indietro nei giorni.
     *
     * v1.58.0 (2026-09-24) — CORREZIONE: "anche se caricati ieri o giorni fa
     * vengono sempre mostrati come caricati oggi". Vero, e dichiarato come
     * limite gia' nella v1.57.0: la stima ordinale partiva sempre da "adesso"
     * e scendeva di un secondo per annuncio — 96 annunci = un minuto e mezzo,
     * quindi tutto "oggi". Ora la data si stima dall'ID dell'articolo (i
     * numeri Vinted crescono nel tempo, vedi StimaDataVinted.kt per le prove
     * e il metodo), con "ancore" vere prese dalla pagina dei piu' recenti.
     * Se l'id non e' un numero (mai visto, ma non si scarta l'annuncio), si
     * ripiega sulla stima ordinale della v1.57.0. dataStimata=true fa
     * comparire "≈" davanti alla data in elenco.
     * Nello stesso punto: dettaglio con taglia e campi extra (dettaglioDa()).
     *
     * --- versione precedente (v1.57.0) ---
     * private fun leggiDaPaginaDom(root: JSONObject): List<Annuncio> {
     *     val pagina = root.optInt("pagina", 1).coerceAtLeast(1)
     *     val arr = root.optJSONArray("annunci") ?: return emptyList()
     *     val out = ArrayList<Annuncio>(arr.length())
     *     val ora = System.currentTimeMillis()
     *     val offsetPagina = (pagina - 1).toLong() * VOCI_STIMATE_PER_PAGINA
     *     for (i in 0 until arr.length()) {
     *         ...
     *         dettaglio = listOf(campi.marca, campi.condizione).filter { it.isNotBlank() }.joinToString(" · "),
     *         dataMillis = ora - (offsetPagina + i) * 1000L,
     *         ...
     *     }
     *     return out
     * }
     * --- fine versione precedente ---
     *
     * --- versione ancora precedente (v1.56.0) ---
     * private fun leggiDaPaginaDom(json: String): List<Annuncio> {
     *     val arr = try { JSONArray(json) } catch (t: Throwable) { return emptyList() }
     *     ...
     *         dataMillis = ora - (i * 1000L),
     *     ...
     * }
     * --- fine versione precedente ---
     */
    private const val VOCI_STIMATE_PER_PAGINA = 200

    /** v1.58.0 — prima di leggere gli annunci si registra l'eventuale ancora arrivata con la pagina. */
    private fun registraAncora(root: JSONObject) {
        val anc = root.optJSONObject("ancora")
        if (anc != null) {
            val id = anc.optString("id").toLongOrNull() ?: 0L
            val ms = anc.optLong("ms", 0L)
            StimaDataVinted.registra(id, ms)
        } else root.optString("ancoraErr").takeIf { it.isNotBlank() }?.let {
            StimaDataVinted.segnaFallimento(it)
        }
    }

    private fun leggiDaPaginaDom(root: JSONObject): List<Annuncio> {
        registraAncora(root)                                        // v1.58.0
        val pagina = root.optInt("pagina", 1).coerceAtLeast(1)
        val arr = root.optJSONArray("annunci") ?: return emptyList()
        val out = ArrayList<Annuncio>(arr.length())
        val ora = System.currentTimeMillis()
        val offsetPagina = (pagina - 1).toLong() * VOCI_STIMATE_PER_PAGINA
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
            val campi = scomponiAlt(o.optString("alt"))
            val src = o.optString("src").takeIf { it.startsWith("http") }
            // v1.58.0 — data dall'id; ripiego sulla stima ordinale v1.57.0
            val daId = id.toLongOrNull()?.let { StimaDataVinted.stima(it) } ?: 0L
            val data = if (daId > 0L) daId else ora - (offsetPagina + i) * 1000L

            out.add(
                Annuncio(
                    fonte = nome,
                    id = id,
                    titolo = campi.titolo,
                    prezzo = campi.prezzo,
                    prezzoTesto = campi.prezzo?.let { formatta(it, "EUR") } ?: "—",
                    luogo = "",
                    dettaglio = dettaglioDa(campi),                 // v1.58.0
                    venduto = false,
                    dataMillis = data,                              // v1.58.0
                    immagine = src,
                    immagineGrande = src,
                    immagini = src?.let { listOf(it) } ?: emptyList(),
                    url = assoluto(o.optString("href"), "https://www.vinted.it"),
                    venditore = "",
                    preferitoIniziale = o.optBoolean("pref", false),
                    dataStimata = true                              // v1.58.0
                )
            )
        }
        return out
    }

    /*
     * v1.56.0 — diagnostica gemella di chiavi(), per il nuovo formato DOM.
     *
     * v1.58.0 (2026-09-24) — conta i prezzi letti (non piu' "modello
     * riconosciuto", concetto sparito col nuovo lettore), elenca TUTTE le
     * etichette trovate con quante volte (e' cosi' che si scopre un campo
     * nuovo, come "Taglia", senza aspettare uno screenshot), mostra un
     * esempio di "alt" senza prezzo, e riporta lo stato della stima di data:
     * ancora arrivata o motivo del fallimento, e le date stimate piu' nuova
     * e piu' vecchia di questa pagina.
     *
     * --- versione precedente (v1.57.0) ---
     * private fun chiaviDom(root: JSONObject): String {
     *     val arr = root.optJSONArray("annunci") ?: return "(dom) \"annunci\" mancante"
     *     var ok = 0; var falliti = 0; var esempioFallito = ""
     *     for (i in 0 until arr.length()) {
     *         val alt = arr.optJSONObject(i)?.optString("alt").orEmpty().trim()
     *         if (RE_ALT_CON_MARCA.matches(alt) || RE_ALT_SENZA_MARCA.matches(alt)) ok++
     *         else { falliti++; if (esempioFallito.isBlank()) esempioFallito = alt.take(200) }
     *     }
     *     return "(dom) pagina=${root.optInt("pagina", 1)} annunci=${arr.length()} " +
     *             "alt-riconosciuti=$ok alt-non-riconosciuti=$falliti" +
     *             if (esempioFallito.isNotBlank()) " · esempio: \"$esempioFallito\"" else ""
     * }
     * --- fine versione precedente ---
     */
    private fun chiaviDom(root: JSONObject): String {
        val arr = root.optJSONArray("annunci") ?: return "(dom) \"annunci\" mancante"
        var conPrezzo = 0; var senzaPrezzo = 0; var esempioSenza = ""
        val etichette = LinkedHashMap<String, Int>()
        var idMin = Long.MAX_VALUE; var idMax = Long.MIN_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val alt = o.optString("alt").trim()
            val c = scomponiAlt(alt)
            if (c.prezzo != null) conPrezzo++
            else { senzaPrezzo++; if (esempioSenza.isBlank()) esempioSenza = alt.take(200) }
            c.etichette.forEach { etichette[it] = (etichette[it] ?: 0) + 1 }
            o.optString("id").toLongOrNull()?.let { idMin = minOf(idMin, it); idMax = maxOf(idMax, it) }
        }
        val fmt = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.ITALY)
        val date = if (idMax >= idMin)
            " · date stimate ${fmt.format(java.util.Date(StimaDataVinted.stima(idMax)))}" +
                    " → ${fmt.format(java.util.Date(StimaDataVinted.stima(idMin)))} (id $idMax → $idMin)"
        else ""
        val ancora = root.optJSONObject("ancora")?.let { " · ancora pagina: id=${it.optString("id")} card=${it.optInt("n")}" }
            ?: root.optString("ancoraErr").takeIf { it.isNotBlank() }?.let { " · ancora pagina fallita: $it" }
            ?: ""
        return "(dom) pagina=${root.optInt("pagina", 1)} annunci=${arr.length()} " +
                "prezzo-letto=$conPrezzo prezzo-mancante=$senzaPrezzo" +
                " · etichette: " + etichette.entries.joinToString(", ") { "${it.key}=${it.value}" } +
                (if (esempioSenza.isNotBlank()) " · esempio senza prezzo: \"$esempioSenza\"" else "") +
                ancora + date +
                "\n  " + StimaDataVinted.descrizione()
    }

    /*
     * v1.57.0 — lo smistamento non guarda piu' se il testo comincia con "["
     * o "{" (fragile: ora ENTRAMBI i formati sono oggetti, vedi sopra): si
     * guarda direttamente se c'e' la chiave "annunci", che solo il nostro
     * elenco DOM porta.
     *
     * --- versione precedente (v1.56.0) ---
     * override fun leggi(json: String): List<Annuncio> {
     *     if (json.trimStart().startsWith("[")) return leggiDaPaginaDom(json)
     *     val root = JSONObject(json)
     *     val items = root.optJSONArray("items") ?: return emptyList()
     *     ...
     * }
     * --- fine versione precedente ---
     */
    override fun leggi(json: String): List<Annuncio> {
        val root = JSONObject(json)
        if (root.has("annunci")) return leggiDaPaginaDom(root)   // v1.57.0
        val items = root.optJSONArray("items") ?: return emptyList()
        val out = ArrayList<Annuncio>(items.length())
        for (i in 0 until items.length()) {
            val it0 = items.optJSONObject(i) ?: continue
            val id = it0.opt("id")?.toString() ?: continue

            val p = it0.optJSONObject("price")
            val importo = p?.optString("amount")?.toDoubleOrNull() ?: it0.optDouble("price", -1.0)
                .takeIf { it >= 0 }
            val valuta = p?.optString("currency_code") ?: "EUR"

            /*
             * v0.4.6 — prima si prende la miniatura, poi eventualmente la foto piena.
             * photo.url punta alla resa f800: troppo per una riga di elenco.
             *
             * --- versione precedente (v0.4.0, 2026-08-30) ---
             * val img = foto?.optString("url")?.takeIf { it.startsWith("http") } ?: miniatura(foto)
             * --- fine versione precedente ---
             */
            val foto = it0.optJSONObject("photo")
            val img = miniatura(foto) ?: foto?.optString("url")?.takeIf { it.startsWith("http") }
            /*
             * v1.32.0 — tutte le foto per la vista estesa (vedi il commento su
             * Annuncio.immagini): il campo "photos" (plurale) e' visto nel
             * solo elenco chiavi di una diagnostica esportata, non ancora in
             * una sonda col contenuto vero — si presume la stessa forma di
             * "photo" (singolare), gia' verificato qui sopra ("url" -> resa
             * f800). Se l'array manca o i suoi elementi non hanno "url", il
             * risultato e' semplicemente vuoto e la vista estesa ripiega da
             * sola sulla sola foto singola: nessun rischio di crash o di dato
             * sbagliato, solo — nel peggiore dei casi — niente scorrimento
             * finche' una sonda vera non conferma la forma dell'array.
             */
            val fotoArr = it0.optJSONArray("photos")
            val immaginiVinted = if (fotoArr != null) (0 until fotoArr.length()).mapNotNull { k ->
                fotoArr.optJSONObject(k)?.optString("url")?.takeIf { u -> u.startsWith("http") }
            } else emptyList()

            val url = assoluto(
                it0.optString("url").ifBlank { "/items/$id" },
                "https://www.vinted.it"
            )

            out.add(
                Annuncio(
                    fonte = nome,
                    id = id,
                    titolo = it0.optString("title"),
                    prezzo = importo,
                    prezzoTesto = if (importo != null) formatta(importo, valuta) else "—",
                    // v1.1.0 — su Vinted il luogo non esiste (vedi condizioneVinted);
                    // al suo posto la condizione dell'articolo.
                    luogo = "",
                    dettaglio = condizioneVinted(it0),
                    venduto = it0.optBoolean("is_closed", false) ||
                              it0.optBoolean("is_sold", false),
                    dataMillis = dataVinted(it0, foto),
                    immagine = img,
                    // v1.7.0 — photo.url e' la resa f800: giusta a tutta larghezza,
                    // sprecata in elenco (vedi v0.4.6). Ognuna al suo posto.
                    immagineGrande = foto?.optString("url")?.takeIf { it.startsWith("http") } ?: img,
                    immagini = immaginiVinted,             // v1.32.0
                    url = url,
                    // v1.2.0
                    venditore = it0.optJSONObject("user")?.opt("id")?.toString().orEmpty(),
                    preferitoIniziale = it0.optBoolean("is_favourite", false)
                )
            )
        }
        return out
    }

    /*
     * v0.4.2 (2026-08-30) — data di inserimento.
     * Il catalogo Vinted non espone sempre lo stesso campo: si prova in ordine
     * quelli noti e si ripiega sul timestamp della foto, che nella pratica
     * coincide quasi sempre con la pubblicazione.
     *
     * --- versione precedente (v0.4.0) ---
     * dataMillis = tsSecondi(foto?.optJSONObject("high_resolution")?.optLong("timestamp"))
     * --- fine versione precedente ---
     */
    private fun dataVinted(it0: JSONObject, foto: JSONObject?): Long {
        for (k in CAMPI_DATA) {
            val v = it0.opt(k) ?: continue
            leggiData(v)?.let { return it }
        }
        foto?.optJSONObject("high_resolution")?.opt("timestamp")?.let { t ->
            leggiData(t)?.let { return it }
        }

        /*
         * v1.0.2 (2026-08-30) — ultima risorsa, ed e' quella che funziona.
         * Nel catalogo Vinted i campi con la data spesso non ci sono affatto:
         * il risultato era "data non disponibile" su ogni riga.
         * L'indirizzo della foto pero' finisce sempre con l'ora di caricamento:
         *   .../06_01dea_HTkh.../f800/1769551993.webp?s=...
         * e per un annuncio dell'usato la foto viene caricata quando l'annuncio
         * viene pubblicato. E' una stima, non una dichiarazione, ma sbaglia di
         * minuti dove prima non si diceva nulla.
         */
        // Si cerca in tutto l'oggetto foto: l'ora sta nell'indirizzo, e gli
        // indirizzi sono piu' d'uno (piena, 310, 150...), tutti con lo stesso valore.
        val url = foto?.toString().orEmpty()
        RE_TS_FOTO.find(url)?.groupValues?.get(1)?.toLongOrNull()?.let { return it * 1000L }
        return 0L
    }

    private val CAMPI_DATA = listOf(
        "created_at_ts", "created_at", "active_date", "updated_at_ts", "updated_at"
    )
    private val RE_TS_FOTO = Regex("/(\\d{10})\\.(?:webp|jpe?g|png)")

    /** Accetta numeri in secondi o millisecondi, e testo in formato ISO. */
    private fun leggiData(v: Any?): Long? = when (v) {
        is Number -> v.toLong().takeIf { it > 0 }
            ?.let { if (it > 1_000_000_000_000L) it else it * 1000L }
        is String -> {
            val n = v.toLongOrNull()
            if (n != null && n > 0) (if (n > 1_000_000_000_000L) n else n * 1000L)
            else iso(v).takeIf { it > 0 }
        }
        else -> null
    }

    /*
     * v0.5.1 — la localita' nel catalogo Vinted non ha una posizione fissa:
     * a volte sta sull'articolo, a volte sul venditore, spesso non c'e'.
     * Si prova nell'ordine e ci si accontenta di cio' che si trova.
     */
    /*
     * v1.2.0 (2026-08-31) — la v1.1.0 aveva ragione a meta'.
     *
     * Nel CATALOGO il luogo davvero non c'e', e quanto scritto sotto resta
     * esatto. Ma la piattaforma lo conosce: sta sul PROFILO del venditore,
     *   GET /api/v2/users/<id> -> { city, country_title_local, expose_location }
     * verificato sul browser con la sessione vera, 12 venditori su 12.
     * Se ne occupa LuoghiVinted, una chiamata per venditore, con cache.
     * Qui l'annuncio continua a nascere senza luogo: e' corretto, perche' in
     * questa risposta non c'e'. La condizione resta in "dettaglio" e riempie
     * la riga finche' il profilo non ha risposto.
     */
    /*
     * v1.1.0 (2026-08-30) — guardando la risposta vera.
     *
     * Nel catalogo Vinted il luogo NON esiste. L'articolo ha:
     *   id, title, price, is_visible, brand_title, path, user, conversion, url,
     *   promoted, photos, photo, favourite_count, is_favourite, service_fee,
     *   total_item_price, view_count, size_title, status, item_box, ...
     * e l'oggetto "user" contiene soltanto id, login, profile_url, photo,
     * business. Nessuna citta', nessun paese, da nessuna parte.
     *
     * Le due versioni precedenti cercavano qualcosa che non c'e': la prima in
     * tre posti fissi, la seconda in tutto l'oggetto. Entrambe tornavano vuote,
     * ed era l'unico esito possibile. Al posto del luogo si mostra la
     * CONDIZIONE, che c'e' sempre ed e' cio' che serve davvero sull'usato.
     *
     * --- versione precedente (v1.0.3, 2026-08-30) ---
     * private fun luogoVinted(it0: JSONObject): String {
     *     cerca(it0, CHIAVI_LUOGO)?.let { return it }
     *     cerca(it0, CHIAVI_CODICE_PAESE)?.let { return paese(it) }
     *     return ""
     * }
     * ...piu' le tabelle dei paesi e la ricerca annidata, tutte rimosse.
     * --- fine versione precedente ---
     */
    private fun condizioneVinted(it0: JSONObject): String {
        val box = it0.optJSONObject("item_box")
        val riga = box?.optString("second_line").orEmpty().trim()
        if (riga.isNotEmpty() && riga != "null") return riga
        return it0.optString("status").orEmpty().trim().takeIf { it != "null" } ?: ""
    }

    private fun miniatura(foto: JSONObject?): String? {
        val t = foto?.optJSONArray("thumbnails") ?: return null
        // v0.4.6 — si preferisce la 310, poi la 150, poi qualunque cosa ci sia.
        for (misura in listOf("thumb310", "thumb150")) {
            for (i in 0 until t.length()) {
                val o = t.optJSONObject(i) ?: continue
                if (o.optString("type") == misura) {
                    val u = o.optString("url")
                    if (u.startsWith("http")) return u
                }
            }
        }
        return t.optJSONObject(0)?.optString("url")?.takeIf { it.startsWith("http") }
    }

    /*
     * v1.21.0 (2026-08-31) — DIAGNOSTICA per "su Vinted vedo solo gli annunci
     * di oggi, quelli di ieri o prima compaiono solo su Subito".
     *
     * dataVinted() (sopra) ammette gia' da sola, nei suoi commenti, che i
     * campi diretti (CAMPI_DATA) spesso mancano nel catalogo, e che la stima
     * dall'indirizzo della foto "e' una stima, non una dichiarazione". Se
     * quella stima fallisse proprio per gli annunci NON di oggi (per esempio
     * perche' l'indirizzo della foto cambia forma dopo che Vinted la
     * rielabora), ogni annuncio piu' vecchio di oggi prenderebbe dataMillis=0
     * e l'ordinamento "piu' recenti" (StatoRicerca.risultati) lo spingerebbe
     * in fondo alla lista — sembrerebbe sparito, mentre e' solo mal datato.
     * E' un sospetto coerente con quanto gia' scritto nel codice, ma non
     * ancora una prova.
     *
     * "chiavi()" e' gia' solo diagnostica (vedi l'interfaccia Fonte: "com'e'
     * fatto il primo elemento") e finisce dritta nel log che la sonda esporta
     * — zero rischio di toccare cosa si vede in lista. Qui si aggiunge, oltre
     * ai campi del primo annuncio, quanti annunci DI QUESTA PAGINA hanno preso
     * la data da un campo diretto, quanti dalla stima sulla foto, e quanti da
     * nessuna delle due: la prossima sonda dira' se il sospetto regge.
     *
     * --- versione precedente (fino alla v1.20.0) ---
     * override fun chiavi(json: String): String =
     *     (JSONObject(json).optJSONArray("items")?.optJSONObject(0)?.keys()
     *         ?.asSequence()?.toList() ?: emptyList<String>()).joinToString(",")
     * --- fine versione precedente ---
     */
    /*
     * v1.56.0 — smista come leggi(): il nostro elenco letto dal DOM
     * (chiaviDom(), sopra) porta la chiave "annunci", la vecchia risposta
     * JSON no (viva solo per l'elenco preferiti, endpoint mai toccato dal
     * ritiro del catalogo — vedi il commento in cima a leggi()).
     *
     * v1.57.0 — lo smistamento non guarda piu' "[" contro "{" (ora
     * ENTRAMBI i formati sono oggetti): si guarda la chiave "annunci",
     * come in leggi().
     *
     * --- versione precedente (v1.56.0) ---
     * override fun chiavi(json: String): String {
     *     if (json.trimStart().startsWith("[")) return chiaviDom(json)
     *     val items = JSONObject(json).optJSONArray("items")
     *     ...
     * }
     * --- fine versione precedente ---
     *
     * --- versione precedente (fino alla v1.55.0, nessuno smistamento) ---
     * (uguale al corpo dopo l'if, senza lo smistamento) ---
     * --- fine versione precedente ---
     */
    override fun chiavi(json: String): String {
        val root = JSONObject(json)
        if (root.has("annunci")) return chiaviDom(root)   // v1.57.0

        val items = root.optJSONArray("items")
        val campi = (items?.optJSONObject(0)?.keys()?.asSequence()?.toList()
            ?: emptyList<String>()).joinToString(",")

        var daCampo = 0; var daFoto = 0; var senzaData = 0
        if (items != null) for (i in 0 until items.length()) {
            val it0 = items.optJSONObject(i) ?: continue
            val foto = it0.optJSONObject("photo")
            when {
                CAMPI_DATA.any { chiave -> it0.opt(chiave) != null } -> daCampo++
                RE_TS_FOTO.containsMatchIn(foto?.toString().orEmpty()) -> daFoto++
                else -> senzaData++
            }
        }
        return "$campi | data: campo=$daCampo foto=$daFoto mancante=$senzaData"
    }

    /*
     * v1.2.0 — preferiti Vinted, contratto osservato con la sonda:
     *   POST /api/v2/user_favourites/toggle  {"type":"item","user_favourites":[<id>]}
     *        -> {"code":0,"message":"Ok"}
     * Un solo endpoint per mettere e togliere: e' un interruttore, non due verbi.
     * Lo stato di partenza non richiede chiamate, sta gia' nei risultati
     * (is_favourite), quindi l'elenco serve solo come rete di sicurezza.
     */
    /*
     * v1.26.0 (2026-09-01) — CORREZIONE, con la prova in mano: l'utente ha
     * catturato con la sonda un'aggiunta e una rimozione di preferito fatte
     * DENTRO una vera pagina di un articolo Vinted (quella su cui e' arrivato
     * navigando dai risultati, con foto, venditore, tutto) — e li' funziona,
     * identica per indirizzo e corpo a quella che l'app manda gia' (stesso
     * "/api/v2/user_favourites/toggle", stesso {"type":"item","user_favourites":[…]}).
     * Quindi non e' MAI stato un problema di indirizzo, corpo o intestazioni
     * (v1.7.0, v1.11.0 le avevano gia' trovate giuste) — e' la PAGINA da cui
     * parte la chiamata. La sessione nascosta dell'app (MotoreRicerca) carica
     * la home di vinted.it UNA volta all'avvio e ci resta sopra per tutta la
     * vita del processo: mai una vera navigazione, mai i tanti caricamenti
     * (foto, venditore, pubblicita', contatori) che accompagnano l'apertura
     * reale di un articolo e che verosimilmente contano per chi giudica se la
     * sessione e' "umana". Ora "paginaPrima" dice alla sessione di caricare
     * DAVVERO la pagina dell'annuncio (a.url, la stessa che si aprirebbe
     * toccandolo) subito prima di provare il preferito — la stessa sequenza
     * catturata funzionante. Vedi il commento su MotoreRicerca.azione().
     *
     * --- versione precedente (fino alla v1.25.0) ---
     * private fun toggle(a: Annuncio) = Chiamata(
     *     "POST", "/api/v2/user_favourites/toggle",
     *     JSONObject()
     *         .put("type", "item")
     *         .put("user_favourites", JSONArray().put(a.id.toLongOrNull() ?: a.id))
     *         .toString(),
     *     csrf = true
     * )
     * --- fine versione precedente ---
     */
    /*
     * v1.6.0 (2026-08-31) — CORREZIONE: la POST tornava 403.
     *
     * Verificato sul browser, stessa sessione, stesso corpo:
     *   GET  /api/v2/catalog/items      -> 200
     *   POST /api/v2/user_favourites/toggle
     *        -> 403 {"code":106,"message_code":"access_denied"}
     * I cookie bastano a leggere, non a scrivere: Vinted vuole anche le
     * intestazioni anti-CSRF, che il suo sito prende dalla pagina. Ora le
     * prende anche l'app, dalla pagina vera, al momento della chiamata
     * (csrf = true, vedi MotoreRicerca).
     *
     * --- versione precedente (v1.2.0, 2026-08-30) ---
     * private fun toggle(a: Annuncio) = Chiamata(
     *     "POST", "/api/v2/user_favourites/toggle",
     *     JSONObject().put("type","item")
     *         .put("user_favourites", JSONArray().put(a.id.toLongOrNull() ?: a.id))
     *         .toString())
     * --- fine versione precedente ---
     */
    private fun toggle(a: Annuncio) = Chiamata(
        "POST", "/api/v2/user_favourites/toggle",
        JSONObject()
            .put("type", "item")
            .put("user_favourites", JSONArray().put(a.id.toLongOrNull() ?: a.id))
            .toString(),
        csrf = true,
        paginaPrima = a.url.takeIf { it.isNotBlank() }
    )

    override fun preferitoAggiungi(a: Annuncio, utente: String) = toggle(a)
    override fun preferitoRimuovi(a: Annuncio, utente: String) = toggle(a)

    /*
     * v1.45.0 (2026-09-08) — CORREZIONE: "la pagina dei preferiti da' errore
     * 403 per Vinted", segnalato dall'utente dopo che Vinted e' tornato
     * raggiungibile (il KO del giorno prima era un problema loro, non
     * nostro — nessun codice cambiato per quello).
     *
     * Questa chiamata non ha MAI portato le intestazioni anti-CSRF
     * (csrf = true): da v1.2.0 era una semplice GET con i soli cookie. E'
     * la stessa identica situazione gia' vista e risolta per il preferito
     * singolo (toggle(), qui sopra, v1.6.0-v1.16.0): Vinted rifiuta con 403
     * "access_denied" le chiamate senza quelle intestazioni (token CSRF,
     * Platform, X-Next-App, X-Anon-Id — raccolte nella pagina al momento
     * della chiamata, vedi MotoreRicerca.azione). Fin qui l'elenco preferiti
     * evidentemente non le richiedeva (ha funzionato per mesi senza), quindi
     * o e' cambiato lato Vinted (irrigidito anche sulle letture, non solo
     * sulle scritture) o serve un session/token piu' fresco di quanto
     * offrisse la sola pagina caricata all'avvio.
     *
     * Applicato lo stesso rimedio gia' confermato altrove, invece di
     * indovinarne uno nuovo: csrf = true, che aggiunge le intestazioni E fa
     * scattare il ricaricamento automatico + un ritentativo sul primo 403
     * (vedi MotoreRicerca.azione). Aggiunta anche una riga in diagnostica
     * (StatoRicerca.apriPreferiti) per il caso in cui questo non basti: fin
     * qui un 403 su questa chiamata non lasciava alcuna traccia in
     * "Esporta diagnostica" (il ramo senza csrf di azione() non scrive
     * nulla li'), solo "risposta 403" a schermo — troppo poco per capire il
     * prossimo passo senza un altro giro a vuoto.
     *
     * --- versione precedente (fino alla v1.44.0) ---
     * override fun preferitiElenco(utente: String): Chiamata? =
     *     if (utente.isBlank()) null
     *     else Chiamata("GET", "/api/v2/users/$utente/items/favourites?per_page=200")
     * --- fine versione precedente ---
     */
    override fun preferitiElenco(utente: String): Chiamata? =
        if (utente.isBlank()) null
        else Chiamata(
            "GET", "/api/v2/users/$utente/items/favourites?per_page=200",
            csrf = true
        )

    /*
     * v1.8.0 — su Vinted l'elenco dei preferiti usa lo stesso serializzatore
     * del catalogo: {"items":[…]}. Quindi il lettore e' gia' scritto.
     */
    override fun preferitiAnnunci(json: String): List<Annuncio> =
        try { leggi(json) } catch (t: Throwable) { emptyList() }

    override fun preferitiLeggi(json: String): List<String> {
        val arr = try { JSONObject(json).optJSONArray("items") } catch (t: Throwable) { null }
            ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.opt("id")?.toString()?.let { out.add("v:" + it) }
        }
        return out
    }
}

/* --------------------------------------------------------------------- */

private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

/*
 * v0.4.1 (2026-08-30) — CORREZIONE
 * Vinted restituisce l'indirizzo della scheda a volte assoluto, a volte in forma
 * relativa (/items/123). loadUrl con un percorso relativo non fa nulla e non
 * segnala errore: era il motivo per cui toccare un risultato Vinted sembrava
 * non avere effetto. Ogni indirizzo passa da qui prima di uscire dall'adattatore.
 */
private fun assoluto(u: String, base: String): String = when {
    u.isBlank() -> base
    u.startsWith("http") -> u
    u.startsWith("//") -> "https:" + u
    u.startsWith("/") -> base.trimEnd('/') + u
    else -> base.trimEnd('/') + "/" + u
}

private fun formatta(v: Double, valuta: String): String {
    val n = if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.2f", v)
    return if (valuta == "EUR") "$n €" else "$n $valuta"
}

private fun tsSecondi(t: Long?): Long = if (t == null || t <= 0) 0L else t * 1000L

internal fun iso(s: String?): Long {
    if (s.isNullOrBlank()) return 0L
    // v0.4.2: Vinted usa 2026-08-27T23:11:07Z, Subito 2026-08-29T15:55:19.928+0200
    try {
        if (s.endsWith("Z")) {
            val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.ITALY)
            f.timeZone = java.util.TimeZone.getTimeZone("UTC")
            f.parse(s)?.let { return it.time }
        }
    } catch (t: Throwable) {}
    return try {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.ITALY)
            .parse(s)?.time ?: 0L
    } catch (t: Throwable) {
        try {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ITALY)
                .parse(s)?.time ?: 0L
        } catch (t2: Throwable) { 0L }
    }
}

private fun slug(s: String): String = s.lowercase()
    .replace("[àáâä]".toRegex(), "a").replace("[èéêë]".toRegex(), "e")
    .replace("[ìíîï]".toRegex(), "i").replace("[òóôö]".toRegex(), "o")
    .replace("[ùúûü]".toRegex(), "u")
    .replace("[^a-z0-9]+".toRegex(), "-").trim('-')

val FONTI: List<Fonte> = listOf(FonteSubito, FonteVinted)
