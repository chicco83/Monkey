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

interface Fonte {
    val nome: String
    val origine: String                 // pagina da cui deve partire la chiamata
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
 */
data class Chiamata(
    val metodo: String,
    val url: String,
    val corpo: String? = null,
    val csrf: Boolean = false,
    val ripiego: Chiamata? = null
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
    override fun url(q: String, pagina: Int, ordine: String): String {
        val start = (pagina - 1) * 30
        val sort = when (ordine) {
            "prezzo_cresc" -> "priceasc"
            "prezzo_decr" -> "pricedesc"
            else -> "datedesc"
        }
        return "https://hades.subito.it/v1/search/items" +
                "?q=" + enc(q) + "&t=s&lim=30&start=$start&sort=$sort"
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
            val imgBase = a.optJSONArray("images")?.optJSONObject(0)?.optString("cdn_base_url")
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
                descrizione = a.optString("body").trim(), // v1.7.0
                url = indirizzo(a, id),
                urn = a.optString("urn")          // v1.2.0: serve ai preferiti
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
     * La sonda ha visto la pagina catalogo chiamare, in relativo:
     *   /api/v2/promoted_closets?...&search_text=rtx+16gb&order=newest_first
     * La stessa famiglia serve gli articoli: /api/v2/catalog/items.
     * Chiamata in relativo dalla pagina vinted.it: stessa origine, stessi cookie.
     */
    override fun url(q: String, pagina: Int, ordine: String): String {
        val order = when (ordine) {
            "prezzo_cresc" -> "price_low_to_high"
            "prezzo_decr" -> "price_high_to_low"
            else -> "newest_first"
        }
        return "/api/v2/catalog/items?search_text=" + enc(q) +
                "&per_page=30&page=$pagina&order=$order"
    }

    override fun leggi(json: String): List<Annuncio> {
        val root = JSONObject(json)
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

    override fun chiavi(json: String): String =
        (JSONObject(json).optJSONArray("items")?.optJSONObject(0)?.keys()
            ?.asSequence()?.toList() ?: emptyList<String>()).joinToString(",")

    /*
     * v1.2.0 — preferiti Vinted, contratto osservato con la sonda:
     *   POST /api/v2/user_favourites/toggle  {"type":"item","user_favourites":[<id>]}
     *        -> {"code":0,"message":"Ok"}
     * Un solo endpoint per mettere e togliere: e' un interruttore, non due verbi.
     * Lo stato di partenza non richiede chiamate, sta gia' nei risultati
     * (is_favourite), quindi l'elenco serve solo come rete di sicurezza.
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
        csrf = true
    )

    override fun preferitoAggiungi(a: Annuncio, utente: String) = toggle(a)
    override fun preferitoRimuovi(a: Annuncio, utente: String) = toggle(a)

    override fun preferitiElenco(utente: String): Chiamata? =
        if (utente.isBlank()) null
        else Chiamata("GET", "/api/v2/users/$utente/items/favourites?per_page=200")

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
