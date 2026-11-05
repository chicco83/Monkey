package it.monkey.app

/*
 * Monkey — il testo dell'annuncio, su Vinted
 * v1.40.0 (2026-09-05) — nuovo
 *
 * IL PROBLEMA, DALLA v1.7.0
 * La vista estesa mostra il testo dell'annuncio accanto ai dati: e' l'unica
 * cosa che in elenco manca davvero, ed e' cio' che fa decidere se aprire o no.
 * Su Subito arriva gia' con i risultati (campo "body", vedi FonteSubito.leggi),
 * su Vinted no: la sonda della v1.1.0 ha elencato i campi veri di un articolo
 * del catalogo —
 *   id, title, price, is_visible, brand_title, path, user, conversion, url,
 *   promoted, photos, photo, favourite_count, is_favourite, service_fee,
 *   total_item_price, view_count, size_title, status, item_box, ...
 * — e il testo non c'e', in nessuna forma. Da allora la colonna, sugli annunci
 * Vinted, resta semplicemente vuota.
 *
 * STESSA ARCHITETTURA DI LuoghiVinted.kt, e per lo stesso motivo
 * Il dato esiste sulla piattaforma ma sta su un'altra risposta, una per
 * articolo. E' identico a com'e' andata per il luogo del venditore: il
 * catalogo non lo espone, il profilo si'. Quindi qui si ripete lo schema che
 * la' funziona da nove versioni — coda, poche richieste per volta, pausa fra
 * l'una e l'altra, cache su file — invece di inventarne un altro.
 *
 * MA CON UNA DIFFERENZA CHE CAMBIA IL DOSAGGIO
 * Un venditore lo si incontra molte volte: la sua riga in cache viene riusata
 * da tutte le ricerche future, e il costo si ammortizza. Una descrizione no:
 * vale per QUEL solo annuncio, e un annuncio dell'usato lo si incrocia una
 * volta sola. Chiedere il testo di tutti e trenta i risultati di ogni pagina
 * vorrebbe dire trenta richieste per pagina che nessuna ricerca futura
 * riusera' mai — cioe' esattamente la firma che distingue un programma da una
 * persona (vedi "COME NON SEMBRARE UN BOT" in Controllore.kt).
 *
 * Percio' qui non si chiede nulla in anticipo: si chiede il testo SOLO degli
 * annunci che finiscono davvero sotto gli occhi, e solo in vista estesa —
 * l'unica in cui quel testo si vede (in vista compatta la colonna non esiste
 * proprio, vedi RigaAnnuncio in SchermataRicerca.kt). In pratica: le quattro o
 * cinque righe visibili, poi altre poche mentre si scorre. E' la stessa
 * andatura di chi guarda gli annunci uno dopo l'altro, non di chi li aspira.
 * Per lo stesso motivo qui si va con UNA richiesta per volta (PARALLELE = 1,
 * contro le due dei luoghi) e una pausa piu' lunga.
 *
 * L'INDIRIZZO — STORIA DEI TENTATIVI
 *
 * 1° (v1.40.0, sbagliato): GET /api/v2/items/<id>, per analogia con la
 * stessa famiglia /api/v2/ usata dal resto dell'app. Una diagnostica
 * dell'utente ha mostrato 404 con la pagina generica dell'app (HTML con
 * l'illustrazione SVG "non trovato"), non un 404 JSON: la rotta non esiste.
 *
 * 2° (v1.43.0, funzionante ma incompleto): la sonda scriptSonda (sotto) ha
 * trovato il testo scorrendo gli <script> della pagina — un blocco JSON-LD
 * standard (schema.org "Product", campo "description"), gia' dentro l'HTML
 * quando la pagina arriva. Funzionava, ma portava sempre il testo ORIGINALE:
 * per un annuncio in lingua straniera, l'utente ha segnalato che la scheda
 * vera mostra la traduzione (attivata da Vinted stesso), mentre l'app
 * mostrava ancora il testo com'era scritto dal venditore.
 *
 * 3° (v1.47.0, quello vero): una nuova sonda, su un annuncio con traduzione
 * attiva, ha catturato la chiamata che il sito fa DAVVERO per comporre la
 * barra laterale della scheda:
 *
 *   GET https://api.vinted.it/item-details/plugins/<id>/translatable?localize=false
 *   -> 200 {"item":{...campi vuoti, un modello...},
 *           "plugins":[
 *             {"name":"summary", ...},
 *             {"name":"description","type":"description","section":"sidebar",
 *              "data":{"description":"Aperto solo per verifica\n\nCasa
 *                       intelligente, compatibile con Home Assistant",
 *                      "is_localized":true,"item_id":"...",
 *                      "localization":"manual","section_title":"Descrizione",
 *                      ...}}
 *           ]}
 *
 * "is_localized":true + "localization":"manual" sull'annuncio straniero della
 * sonda confermano che questo E' il testo (gia') tradotto quando Vinted
 * traduce — esattamente il dato che mancava. E' un endpoint su un altro
 * host (api.vinted.it, non www.vinted.it: un indirizzo assoluto, non piu'
 * relativo alla sessione come /api/v2/...), ma la stessa sonda l'ha
 * catturato mentre lo chiamava la pagina VERA con la sessione dell'utente:
 * se il sito lo fa da dentro www.vinted.it senza inciampare nel CORS, lo
 * stesso vale per la nostra chiamata, che parte dalla stessa pagina.
 *
 * Da questa versione si chiama questo endpoint al posto di scaricare la
 * pagina intera e cercarci dentro un blocco JSON-LD: piu' semplice (una
 * risposta JSON pulita, non piu' html-scraping), piu' leggero (una sola
 * chiamata piccola invece di un'intera pagina), e soprattutto corretto per
 * gli annunci in lingua straniera. Vedi richiedi()/leggiDescrizionePlugin()
 * piu' sotto per i dettagli; il tentativo precedente (JSON-LD dall'HTML)
 * resta commentato piu' in basso, per la cronaca e come ripiego a cui
 * tornare se questo endpoint dovesse mai sparire.
 *
 * v1.49.0 (2026-09-09) — IL BUCO DIAGNOSTICO CHE HA NASCOSTO IL PASSO
 * SUCCESSIVO
 * Dopo la v1.48.0 l'utente ha continuato a vedere il testo in lingua
 * originale in vista estesa. Quattro diagnostiche di fila (api.json della
 * sonda, anche dopo una disinstallazione completa per escludere la cache)
 * non hanno mai mostrato la chiamata a "translatable" — ne' riuscita ne'
 * fallita. Motivo, trovato guardando invece dove segnala() scrive
 * (motore.diagnostica, esportato come "ricerca-diagnostica.txt", un file
 * DIVERSO da "api.json" — la sonda vede solo il traffico della pagina
 * aperta nel tab "Sito", non le chiamate di sfondo di MotoreRicerca):
 * segnala() (piu' sotto) scriveva SOLO quando leggiDescrizionePlugin()
 * tornava null, cioe' solo sui fallimenti. Se la chiamata risponde 200 con
 * un JSON che si legge ma porta il testo SBAGLIATO (non tradotto) —
 * l'ipotesi rimasta in piedi dopo aver escluso la cache — non restava
 * traccia da NESSUNA parte, in nessuno dei due file. Da questa versione
 * segnala() scrive una riga la prima volta che risponde, che vada bene o
 * male: cosi' la prossima "ricerca-diagnostica.txt" mostra la verita'
 * qualunque cosa succeda, invece di restare muta proprio nel caso che
 * serviva vedere.
 *
 * 4° (v1.50.0, 2026-09-09) — quello che mancava: la riga aperta dalla
 * v1.49.0 ha mostrato la risposta vera, su un annuncio in tedesco cercato
 * apposta ("Biete hier ein Konvolut von Spielen..."): stato 200, testo
 * estratto regolare, ma nella lingua ORIGINALE — non tradotto. La chiamata
 * quindi non falliva mai e non restituiva mai un JSON diverso da quello
 * atteso: rispondeva "giusta" con la lingua sbagliata, in modo ripetibile.
 *
 * La causa e' la stessa gia' risolta per il cuore su Vinted, con la stessa
 * prova (v1.26.0, vedi il commento su "toggle" in Fonti.kt): la sessione
 * nascosta di MotoreRicerca carica la HOME di vinted.it una volta all'avvio
 * e ci resta sopra per tutta la vita del processo — non naviga MAI davvero
 * alla pagina di un annuncio. La sonda che aveva scoperto "translatable"
 * (3°, sopra) l'aveva pero' catturata mentre la chiamava un browser vero
 * GIA' SULLA PAGINA di quell'annuncio: e' verosimilmente quella pagina —
 * coi cookie, la cronologia di visita e l'eventuale rilevazione automatica
 * della lingua che porta con se' — a far decidere a Vinted se tradurre,
 * non la sola sessione utente autenticata. Chiamare l'endpoint da una
 * sessione ferma sulla home, come faceva l'app dalla v1.47.0, gli dava
 * tecnicamente tutto il necessario (id, cookie, intestazioni) tranne
 * proprio quel contesto.
 *
 * Rimedio: come per il cuore, "a.url" torna in richiedi() (vedi piu' sotto
 * e StatoRicerca.chiediDescrizione) e viaggia nella Chiamata come
 * "paginaPrima" — la sessione ci naviga DAVVERO prima di chiedere la
 * traduzione, la stessa sequenza gia' provata funzionante. CACHE_VERSIONE
 * sale insieme: ogni descrizione gia' in cache da prima di questa versione
 * puo' essere stata presa con la sessione ferma sulla home (lingua
 * originale, sbagliata) e va richiesta almeno una volta di piu' col nuovo
 * meccanismo.
 *
 * 5° (v1.51.0, 2026-09-09) — REGRESSIONE, RIPORTATA DALL'UTENTE SUBITO DOPO
 * LA v1.50.0: "il testo non compare più in nessuna lingua". Prima della
 * v1.50.0 compariva almeno il testo originale (sbagliato ma presente); dopo,
 * niente affatto — peggio, non meglio.
 *
 * La differenza fra il cuore (v1.26.0) e questo caso, non vista al momento
 * di copiarne il rimedio: il cuore e' UN tocco deliberato dell'utente, una
 * chiamata alla volta, e la sessione che naviga per lui non serve ad altro
 * nel frattempo. La coda di DescrizioniVinted, invece, gira MENTRE la
 * ricerca stessa sta ancora usando la STESSA sessione condivisa
 * (MotoreRicerca tiene un solo WebView per fonte, vedi sessione() li') per
 * le pagine successive del catalogo, mano a mano che si scorre. Far
 * navigare quella sessione, una pagina reale di annuncio dopo l'altra (con
 * tutto cio' che una pagina Vinted vera si porta dietro — decine di host,
 * pubblicita', tracker, vedi il commento su "tentativoAttesa" in
 * MotoreRicerca.kt), MENTRE il catalogo prova a caricare la pagina
 * successiva sulla stessa sessione, li fa contendersi lo stesso "pronte" e
 * lo stesso WebView: la chiamata di ricerca puo' restare a aspettare dietro
 * una navigazione pesante, o una chiamata gia' in volo puo' perdere la sua
 * risposta quando la pagina cambia sotto di lei. Il risultato osservato — il
 * testo sparito del tutto, non solo non tradotto — e' coerente con questo:
 * non un fallimento pulito (quello si vedrebbe in "ricerca-diagnostica.txt",
 * grazie alla v1.49.0), ma un intreccio che lascia la richiesta senza mai
 * un esito.
 *
 * Rimedio, per ora: si torna al comportamento della v1.49.0 (nessuna
 * navigazione, l'endpoint si chiama cosi' com'e' dalla sessione ferma sulla
 * home) — testo originale, non tradotto, ma AFFIDABILE, invece di niente.
 * Il problema della traduzione (4°, sopra) resta aperto: la causa individuata
 * e' probabilmente ancora quella giusta, ma la cura non puo' essere "naviga
 * la STESSA sessione della ricerca" — servirebbe una sessione SEPARATA,
 * dedicata solo a questo, che non contenda mai il WebView del catalogo. Non
 * e' un cambio da fare di fretta subito dopo una regressione: meglio un
 * passo indietro sicuro ora, e riprovare con calma quando serve.
 */

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File

object DescrizioniVinted {

    private const val FILE = "descrizioni_vinted.json"

    /*
     * Una per volta. I luoghi (LuoghiVinted) ne fanno due, ma li' la cache si
     * riusa fra ricerche diverse e la coda si esaurisce; qui ogni annuncio e'
     * una richiesta a perdere, quindi si va piu' piano — vedi il commento in
     * cima al file.
     */
    private const val PARALLELE = 1

    /** Pausa fra una richiesta e la successiva. */
    private const val PAUSA_MS = 600L

    /** Tetto della coda: si scorre in fretta, e cio' che e' gia' passato non serve piu'. */
    private const val CODA_MAX = 40

    /*
     * Quanto testo si tiene. In vista estesa se ne vedono al massimo sei righe
     * troncate (vedi RigaAnnuncio), quindi conservare l'annuncio intero
     * riempirebbe il file per niente. 800 caratteri stanno larghi dentro sei
     * righe su qualunque schermo.
     */
    private const val MAX_TESTO = 800

    /*
     * Quante descrizioni si conservano su file. A differenza dei luoghi — che
     * si riusano e quindi vale la pena tenere per sempre — queste servono
     * quasi solo a non richiedere due volte lo stesso annuncio nella stessa
     * giornata di ricerche (tornare indietro nell'elenco, ripetere una
     * ricerca, riaprire una ricerca salvata). Oltre il tetto cadono le piu'
     * vecchie: e' una cache, non un archivio.
     */
    private const val MAX_CACHE = 400

    private val ui = Handler(Looper.getMainLooper())

    /*
     * id annuncio -> testo gia' noto. Anche "" e' un valore buono: vuol dire
     * "chiesto, la piattaforma non ha dato testo" — senza, si richiederebbe
     * all'infinito lo stesso annuncio senza descrizione. LinkedHashMap perche'
     * l'ordine d'inserimento e' quello che serve per buttare via le piu'
     * vecchie quando si supera MAX_CACHE.
     */
    private val noti = LinkedHashMap<String, String>()

    private val coda = ArrayDeque<String>()

    /*
     * v1.59.0 (2026-09-24) — id annuncio -> istante "Caricato" letto dalla
     * stessa risposta della descrizione (vedi DataCaricamentoVinted.kt).
     * Salvato nello stesso file, sotto "date". Un annuncio senza data
     * trovata semplicemente non compare qui: la riga resta con la stima
     * dall'id (StimaDataVinted).
     */
    private val dateNote = HashMap<String, Long>()

    /** v1.59.0 — la diagnostica della data si scrive una volta se trovata e una se no. */
    private var diagnosticaDataTrovata = false   // v1.61.0: non piu' usata, vedi lettureSegnalate
    private var lettureSegnalate = 0
    private var diagnosticaDataMancante = false

    /*
     * v1.50.0 — torna: serve di nuovo per navigare la sessione sulla pagina
     * vera dell'annuncio PRIMA di chiedere la traduzione (vedi il 4° nel
     * commento in cima al file, e Chiamata.paginaPrima in avviaProssimo).
     *
     * --- versione precedente (v1.47.0-v1.49.0, non serviva) ---
     * // "l'indirizzo si costruisce dal solo id, non serve piu' portare in
     * // coda la pagina dell'annuncio" — rivelatosi il pezzo mancante: vedi
     * // il 4° tentativo in cima al file.
     * --- fine versione precedente ---
     *
     * --- versione precedente (v1.43.0-v1.46.0) ---
     * private val urlPerId = HashMap<String, String>()
     * --- fine versione precedente ---
     *
     * v1.51.0 (2026-09-09) — REGRESSIONE: tornare a navigare la sessione
     * CONDIVISA con la ricerca prima di ogni chiamata (v1.50.0) ha fatto
     * sparire il testo del tutto, in ogni lingua — vedi il 5° tentativo in
     * cima al file. "urlPerId" non serve piu' quindi di nuovo: si torna al
     * comportamento della v1.49.0.
     *
     * --- versione precedente (v1.50.0, ripristinata solo per la
     *     navigazione, poi tolta di nuovo) ---
     * private val urlPerId = HashMap<String, String>()
     * --- fine versione precedente ---
     */

    private val inVolo = HashSet<String>()
    private var attivi = 0
    private var caricato = false
    private var daSalvare = false

    /*
     * La diagnostica si scrive una volta sola per sessione: serve a sapere
     * COM'E' FATTA la risposta, e la prima basta. Senza questa guardia, un
     * campo sbagliato riempirebbe il log con trenta copie della stessa cosa.
     */
    private var diagnosticaScritta = false

    /*
     * v1.48.0 (2026-09-08) — CORREZIONE: "la descrizione compare sempre in
     * lingua originale", anche dopo la v1.47.0. La chiamata nuova era giusta
     * (confermata dalla sonda), ma la CACHE su file no: "noti" si carica da
     * descrizioni_vinted.json all'avvio e richiedi() scarta subito ogni id
     * gia' presente li' dentro (vedi piu' sotto) — l'app non cancella mai
     * quel file da sola fra una versione e l'altra (stesso applicationId,
     * stessi dati, vedi build.gradle.kts v1.14.0). Ogni annuncio gia' visto
     * PRIMA della v1.47.0 aveva quindi il testo ORIGINALE scritto per sempre
     * in cache, e richiedi() non lo richiedeva mai piu' — esattamente il
     * "sempre", non "a volte", segnalato dall'utente: non e' un caso che
     * fallisca la traduzione, e' che per gli annunci gia' incontrati prima
     * la nuova chiamata non partiva proprio.
     *
     * Rimedio: il file porta gia' un campo "versione" (finora scritto ma mai
     * riletto — vedi salva(), sotto). Da questa versione carica() lo
     * confronta con CACHE_VERSIONE: se non combacia, la cache e' considerata
     * scritta con il vecchio meccanismo e si riparte vuota invece di
     * caricarla — un solo "colpo a vuoto" per annuncio gia' visto, poi la
     * cache torna a funzionare normalmente con il testo giusto.
     */
    /*
     * v1.51.0 — sale ancora, per lo stesso principio: qualunque descrizione
     * presa durante la finestra della v1.50.0 (sessione condivisa navigata a
     * ogni chiamata, poi tolta per la regressione — vedi il 5° tentativo in
     * cima al file) e' di provenienza incerta, che sia rimasta vuota o che
     * per qualche riga isolata sia comunque riuscita. Un "colpo a vuoto" in
     * piu' e si torna puliti col meccanismo ripristinato (v1.49.0).
     *
     * --- versione precedente (v1.50.0) ---
     * private const val CACHE_VERSIONE = "1.50.0"
     * --- fine versione precedente ---
     *
     * --- versione precedente (v1.48.0-v1.49.0) ---
     * private const val CACHE_VERSIONE = "1.48.0"
     * --- fine versione precedente ---
     */
    /*
     * v1.59.0 (2026-09-24) — sale ancora: da questa versione la stessa
     * risposta porta anche la data "Caricato" (DataCaricamentoVinted), che
     * le descrizioni gia' in cache non hanno. Si riparte vuoti una volta,
     * cosi' ogni annuncio viene richiesto di nuovo e prende anche la data.
     *
     * --- versione precedente (v1.51.0-v1.58.0) ---
     * private const val CACHE_VERSIONE = "1.51.0"
     * --- fine versione precedente ---
     */
    private const val CACHE_VERSIONE = "1.59.0"

    // ------------------------------------------------------------------
    /*
     * v1.48.0 — confronto con CACHE_VERSIONE prima di caricare: vedi il
     * commento sopra per il perche'.
     *
     * --- versione precedente (fino alla v1.47.0) ---
     * @Synchronized
     * fun carica(ctx: Context) {
     *     if (caricato) return
     *     caricato = true
     *     val f = File(ctx.filesDir, FILE)
     *     if (!f.exists()) return
     *     try {
     *         val root = JSONObject(f.readText())
     *         root.optJSONObject("descrizioni")?.let { o ->
     *             o.keys().forEach { k -> noti[k] = o.optString(k) }
     *         }
     *     } catch (t: Throwable) { // file rovinato: si riparte da vuoto
     *     }
     * }
     * --- fine versione precedente ---
     */
    @Synchronized
    fun carica(ctx: Context) {
        if (caricato) return
        caricato = true
        val f = File(ctx.filesDir, FILE)
        if (!f.exists()) return
        try {
            val root = JSONObject(f.readText())
            if (root.optString("versione") != CACHE_VERSIONE) return
            root.optJSONObject("descrizioni")?.let { o ->
                o.keys().forEach { k -> noti[k] = o.optString(k) }
            }
            // v1.59.0
            root.optJSONObject("date")?.let { o ->
                o.keys().forEach { k -> o.optLong(k, 0L).takeIf { it > 0L }?.let { dateNote[k] = it } }
            }
        } catch (t: Throwable) { /* file rovinato: si riparte da vuoto */ }
    }

    /** v1.59.0 — istante "Caricato" gia' noto per questo annuncio, o null. */
    @Synchronized
    fun dataCaricamento(id: String): Long? = dateNote[id]

    /** Testo gia' noto, oppure null se questo annuncio non e' ancora stato chiesto. */
    @Synchronized
    fun descrizione(id: String): String? = noti[id]

    /**
     * Chiede il testo di un annuncio Vinted, se non lo si ha gia' — la
     * versione GIA' TRADOTTA quando Vinted traduce (vedi il commento in cima
     * al file).
     *
     * Si chiama una riga per volta, da chi la sta DISEGNANDO in vista estesa
     * (vedi SchermataRicerca.RigaAnnuncio): e' il modo per chiedere solo cio'
     * che si guarda davvero — vedi il commento in cima al file.
     *
     * @param aggiornato invocato sul thread principale quando il testo e' pronto
     */
    /*
     * v1.51.0 (2026-09-09) — REGRESSIONE, TORNA COME IN v1.49.0: la v1.50.0
     * aveva rimesso "url" per far navigare la sessione sulla pagina vera
     * prima della chiamata (idea giusta in teoria, vedi il 4° tentativo in
     * cima al file), ma quella sessione e' CONDIVISA con la ricerca in
     * corso — navigarla per ogni descrizione ha fatto sparire il testo del
     * tutto, non solo la traduzione (5° tentativo, segnalato dall'utente
     * subito dopo la consegna). Si torna al solo id: testo originale,
     * affidabile, invece di niente. La traduzione resta un problema aperto,
     * da riprendere con una sessione dedicata invece di quella della
     * ricerca.
     *
     * --- versione precedente (v1.50.0, ripristinato solo per la
     *     navigazione — causa della regressione) ---
     * fun richiedi(
     *     ctx: Context,
     *     motore: MotoreRicerca,
     *     id: String,
     *     url: String,
     *     aggiornato: (id: String, testo: String) -> Unit
     * ) {
     *     carica(ctx)
     *     if (id.isBlank() || url.isBlank()) return
     *     if (noti.containsKey(id) || inVolo.contains(id) || coda.contains(id)) return
     *     if (coda.size >= CODA_MAX) return
     *     coda.addLast(id)
     *     urlPerId[id] = url
     *     while (attivi < PARALLELE && coda.isNotEmpty()) avviaProssimo(ctx, motore, aggiornato)
     * }
     * --- fine versione precedente ---
     *
     * --- versione precedente (v1.47.0-v1.49.0, solo id) ---
     * fun richiedi(
     *     ctx: Context,
     *     motore: MotoreRicerca,
     *     id: String,
     *     aggiornato: (id: String, testo: String) -> Unit
     * ) {
     *     carica(ctx)
     *     if (id.isBlank()) return
     *     if (noti.containsKey(id) || inVolo.contains(id) || coda.contains(id)) return
     *     if (coda.size >= CODA_MAX) return
     *     coda.addLast(id)
     *     while (attivi < PARALLELE && coda.isNotEmpty()) avviaProssimo(ctx, motore, aggiornato)
     * }
     * --- fine versione precedente ---
     *
     * --- versione precedente (v1.43.0-v1.46.0) ---
     * fun richiedi(
     *     ctx: Context,
     *     motore: MotoreRicerca,
     *     id: String,
     *     url: String,
     *     aggiornato: (id: String, testo: String) -> Unit
     * ) {
     *     carica(ctx)
     *     if (id.isBlank() || url.isBlank()) return
     *     if (noti.containsKey(id) || inVolo.contains(id) || coda.contains(id)) return
     *     if (coda.size >= CODA_MAX) return
     *     coda.addLast(id)
     *     urlPerId[id] = url
     *     while (attivi < PARALLELE && coda.isNotEmpty()) avviaProssimo(ctx, motore, aggiornato)
     * }
     * --- fine versione precedente ---
     *
     * --- versione precedente (v1.42.0, 2026-09-08) — SOSPESA ---
     * // La diagnostica mandata dall'utente ha confermato che
     * // "GET /api/v2/items/<id>" non e' una rotta vera — risponde 404 con
     * // la pagina generica dell'app (HTML con l'illustrazione SVG "non
     * // trovato"), non un 404 JSON dell'API come quelli gia' visti altrove
     * // (es. il 403 "access_denied" del cuore). E una sonda con "Modalita'
     * // sonda" attiva, aperta su una scheda vera, non ha mai visto IL
     * // CAMPIONATORE DI FETCH/XHR (ScriptApi) catturare nessuna chiamata
     * // per il dettaglio: segno che il testo non arriva mai con una
     * // richiesta separata, e' gia' dentro l'HTML della pagina quando
     * // arriva (reso lato server). Insistere con questa chiamata userebbe
     * // solo richieste a vuoto, per un endpoint che si sa gia' non
     * // esistere.
     * fun richiedi(
     *     ctx: Context,
     *     motore: MotoreRicerca,
     *     id: String,
     *     aggiornato: (id: String, testo: String) -> Unit
     * ) {
     *     // v1.42.0 — sospesa: il corpo di prima resta li', come
     *     // "versione precedente", non qui sotto irraggiungibile.
     * }
     * --- fine versione precedente ---
     *
     * --- versione precedente (fino alla v1.41.0) ---
     * fun richiedi(
     *     ctx: Context,
     *     motore: MotoreRicerca,
     *     id: String,
     *     aggiornato: (id: String, testo: String) -> Unit
     * ) {
     *     carica(ctx)
     *     if (id.isBlank()) return
     *     if (noti.containsKey(id) || inVolo.contains(id) || coda.contains(id)) return
     *     if (coda.size >= CODA_MAX) return
     *     coda.addLast(id)
     *     while (attivi < PARALLELE && coda.isNotEmpty()) avviaProssimo(ctx, motore, aggiornato)
     * }
     * --- fine versione precedente ---
     */
    /*
     * v1.59.0 (2026-09-24) — nuovo parametro "dataPronta": la stessa
     * risposta porta anche la data "Caricato" (vedi DataCaricamentoVinted).
     * Sta PRIMA di "aggiornato", con un default, per la stessa ragione
     * gia' scritta in MotoreRicerca.azione (v1.16.0): la lambda finale fuori
     * dalle parentesi deve continuare a combaciare con "aggiornato".
     *
     * --- versione precedente (v1.51.0-v1.58.0) ---
     * @Synchronized
     * fun richiedi(
     *     ctx: Context,
     *     motore: MotoreRicerca,
     *     id: String,
     *     aggiornato: (id: String, testo: String) -> Unit
     * ) {
     *     carica(ctx)
     *     if (id.isBlank()) return
     *     if (noti.containsKey(id) || inVolo.contains(id) || coda.contains(id)) return
     *     if (coda.size >= CODA_MAX) return
     *     coda.addLast(id)
     *     while (attivi < PARALLELE && coda.isNotEmpty()) avviaProssimo(ctx, motore, aggiornato)
     * }
     * --- fine versione precedente ---
     */
    @Synchronized
    fun richiedi(
        ctx: Context,
        motore: MotoreRicerca,
        id: String,
        dataPronta: (id: String, ms: Long) -> Unit = { _, _ -> },
        aggiornato: (id: String, testo: String) -> Unit
    ) {
        carica(ctx)
        if (id.isBlank()) return
        if (noti.containsKey(id) || inVolo.contains(id) || coda.contains(id)) return
        if (coda.size >= CODA_MAX) return
        coda.addLast(id)
        while (attivi < PARALLELE && coda.isNotEmpty()) avviaProssimo(ctx, motore, dataPronta, aggiornato)
    }

    /*
     * v1.42.0 (2026-09-08) — sonda per trovare DAVVERO come arriva il testo
     * di un annuncio Vinted, dopo che il tentativo per chiamata separata si
     * e' escluso da solo. Ha fatto il suo lavoro (ha portato al blocco
     * JSON-LD della v1.43.0, poi superato dall'endpoint di traduzione della
     * v1.47.0 — vedi il commento in cima al file) e resta nel codice: come
     * "Modalita' sonda" in generale, serve a diagnosticare un domani se
     * Vinted cambia ancora pagina (stessa logica per cui non si toglie mai
     * "Esporta diagnostica" a bug chiuso).
     *
     * Attiva SOLO in "Modalita' sonda" (come DomProbe) e SOLO sulle pagine
     * articolo di Vinted (percorso "/items/..."): su ogni altra pagina non
     * farebbe che sprecare un confronto a vuoto. Il risultato va a finire
     * nello stesso canale gia' usato per le chiamate campionate (reportApi
     * -> ApiLog -> "api.json" in "Esporta diagnostica"), cosi' compare
     * insieme a tutto il resto invece di aprire un file a parte.
     *
     * Nota sintassi: niente template literal e niente simbolo dollaro nel
     * JS, come nel resto delle sonde di questo progetto (vedi DomProbe.kt).
     */
    fun scriptSonda(ponte: String): String = """
    (function () {
      try {
        if (String(location.pathname || '').indexOf('/items/') === -1) return;
        var scripts = document.querySelectorAll('script');
        var trovato = null;
        for (var i = 0; i < scripts.length; i++) {
          var t = scripts[i].textContent || '';
          if (t.indexOf('"description"') >= 0) { trovato = t; break; }
        }
        if (trovato) {
          var idx = trovato.indexOf('"description"');
          var pezzo = trovato.slice(Math.max(0, idx - 80), idx + 2000);
          window['$ponte'].reportApi(location.href, 'SONDA-DESCRIZIONE', '', 200, pezzo);
        } else {
          window['$ponte'].reportApi(
            location.href, 'SONDA-DESCRIZIONE', '', 404,
            '(nessuno script con "description" trovato - script totali: ' + scripts.length + ')'
          );
        }
      } catch (e) {
        try { window['$ponte'].reportApi(location.href, 'SONDA-DESCRIZIONE', '', -1, String(e)); } catch (e2) {}
      }
    })();
    """.trimIndent()

    /*
     * v1.59.0 — porta anche "dataPronta" (vedi richiedi()).
     * --- versione precedente: stessa firma senza "dataPronta" ---
     */
    @Synchronized
    private fun avviaProssimo(
        ctx: Context,
        motore: MotoreRicerca,
        dataPronta: (String, Long) -> Unit,
        aggiornato: (String, String) -> Unit
    ) {
        val id = coda.removeFirstOrNull() ?: return
        inVolo.add(id)
        attivi++
        /*
         * v1.51.0 — REGRESSIONE, TORNA COME IN v1.49.0: "paginaPrima" (v1.50.0)
         * faceva navigare la sessione CONDIVISA con la ricerca a ogni
         * descrizione — idea giusta in teoria (stessa causa/prova del cuore,
         * v1.26.0) ma su una sessione che nel frattempo la ricerca sta ancora
         * usando per le pagine successive del catalogo: il testo e' sparito
         * del tutto, in ogni lingua (segnalato dall'utente, vedi il 5°
         * tentativo in cima al file). Si toglie "paginaPrima": si torna a
         * chiamare l'endpoint cosi' com'e', dalla sessione ferma sulla home —
         * testo originale, non tradotto, ma di nuovo affidabile.
         *
         * --- versione precedente (v1.50.0, causa della regressione) ---
         * val pagina = urlPerId.remove(id)
         * val url = "https://api.vinted.it/item-details/plugins/$id/translatable?localize=false"
         * motore.azione(FonteVinted, Chiamata("GET", url, paginaPrima = pagina)) { stato, corpo ->
         *     val testo = if (stato in 200..299) leggiDescrizionePlugin(corpo) else null
         *     segnala(motore, id, stato, corpo, testo)
         *     concluso(ctx, motore, id, testo, aggiornato)
         * }
         * --- fine versione precedente ---
         *
         * --- versione precedente (v1.47.0-v1.49.0) ---
         * val url = "https://api.vinted.it/item-details/plugins/$id/translatable?localize=false"
         * motore.azione(FonteVinted, Chiamata("GET", url)) { stato, corpo ->
         *     val testo = if (stato in 200..299) leggiDescrizionePlugin(corpo) else null
         *     segnala(motore, id, stato, corpo, testo)
         *     concluso(ctx, motore, id, testo, aggiornato)
         * }
         * --- fine versione precedente ---
         *
         * --- versione precedente (v1.43.0-v1.46.0, pagina intera + JSON-LD) ---
         * motore.azione(FonteVinted, Chiamata("GET", url)) { stato, corpo ->
         *     val testo = if (stato in 200..299) leggiDescrizioneHtml(corpo) else null
         *     if (testo == null) segnala(motore, id, stato, corpo)
         *     concluso(ctx, motore, id, testo, aggiornato)
         * }
         * --- fine versione precedente ---
         *
         * --- versione precedente (fino alla v1.41.0) ---
         * motore.azione(FonteVinted, Chiamata("GET", "/api/v2/items/$id")) { stato, corpo ->
         *     val testo = if (stato in 200..299) leggiDescrizione(corpo) else null
         *     if (testo == null) segnala(motore, id, stato, corpo)
         *     concluso(ctx, motore, id, testo, aggiornato)
         * }
         * --- fine versione precedente ---
         */
        /*
         * v1.59.0 — dalla stessa risposta anche la data "Caricato"
         * (DataCaricamentoVinted): nessuna chiamata in piu'.
         *
         * --- versione precedente (v1.51.0-v1.58.0) ---
         * motore.azione(FonteVinted, Chiamata("GET", url)) { stato, corpo ->
         *     val testo = if (stato in 200..299) leggiDescrizionePlugin(corpo) else null
         *     segnala(motore, id, stato, corpo, testo)
         *     concluso(ctx, motore, id, testo, aggiornato)
         * }
         * --- fine versione precedente ---
         */
        val url = "https://api.vinted.it/item-details/plugins/$id/translatable?localize=false"
        motore.azione(FonteVinted, Chiamata("GET", url)) { stato, corpo ->
            val testo = if (stato in 200..299) leggiDescrizionePlugin(corpo) else null
            // v1.49.0 — segnala() si chiama SEMPRE (non solo sui fallimenti):
            // vedi il commento in cima al file, "IL BUCO DIAGNOSTICO...".
            segnala(motore, id, stato, corpo, testo)
            /*
             * v1.60.0 (2026-09-24) — se l'endpoint rifiuta (diagnostica
             * v1.59.0: 400 ANONYMOUS_VIEWER), si ripiega sulla PAGINA
             * pubblica dell'annuncio, che si legge senza accesso: da li'
             * descrizione (JSON-LD, lettore della v1.43.0 ripristinato) e
             * data "Caricato" (DataCaricamentoVinted.leggiHtml).
             *
             * --- versione precedente (v1.59.0) ---
             * if (stato in 200..299) dataDa(motore, id, corpo, dataPronta)
             * concluso(ctx, motore, id, testo, dataPronta, aggiornato)
             * --- fine versione precedente ---
             */
            if (stato in 200..299) {
                dataDa(motore, id, corpo, dataPronta)   // v1.59.0
                concluso(ctx, motore, id, testo, dataPronta, aggiornato)
            } else {
                daPagina(ctx, motore, id, dataPronta, aggiornato)
            }
        }
    }

    /*
     * v1.60.0 (2026-09-24) — ripiego sulla pagina dell'annuncio. Fetch dalla
     * sessione classica senza navigarla (stessa tecnica della v1.43.0,
     * funzionante allora per la descrizione): la regressione v1.50/v1.51 era
     * data dal NAVIGARE la sessione condivisa, non dal chiederle una pagina.
     */
    private fun daPagina(
        ctx: Context,
        motore: MotoreRicerca,
        id: String,
        dataPronta: (String, Long) -> Unit,
        aggiornato: (String, String) -> Unit
    ) {
        /*
         * v1.61.0 (2026-09-24) — scaricamento PARZIALE: la pagina pesa ~2 MB
         * (diagnostica v1.60.0) e ne servono solo due pezzi; vedi
         * MotoreRicerca.paginaParziale per quando si interrompe.
         *
         * --- versione precedente (v1.60.0, pagina intera) ---
         * motore.azione(FonteVinted, Chiamata("GET", "https://www.vinted.it/items/$id")) { stato, html ->
         *     val ok = stato in 200..299
         *     val testo = if (ok) leggiDescrizioneHtml(html) else null
         *     if (ok) dataDaHtml(motore, id, html, dataPronta)
         *     segnalaPagina(motore, id, stato, html, testo)
         *     concluso(ctx, motore, id, testo, dataPronta, aggiornato)
         * }
         * --- fine versione precedente ---
         */
        motore.paginaParziale(FonteVinted, "https://www.vinted.it/items/$id") { stato, html, info ->
            val ok = stato in 200..299
            val testo = if (ok) leggiDescrizioneHtml(html) else null
            if (ok) dataDaHtml(motore, id, html, dataPronta)
            segnalaPagina(motore, id, stato, html, testo, info)
            concluso(ctx, motore, id, testo, dataPronta, aggiornato)
        }
    }

    // v1.61.0 — sostituito da "pagineSegnalate" (sotto), piu' righe invece di una
    // private var diagnosticaPaginaScritta = false

    /** v1.60.0 — una riga per sessione su come e' andato il ripiego sulla pagina. */
    /*
     * v1.61.0 — riporta anche "info" dello scaricamento parziale (letti,
     * interrotto, posizioni). Si scrivono le prime TRE pagine, non una sola:
     * servono piu' misure per stringere i tetti di paginaParziale.
     *
     * --- versione precedente (v1.60.0) ---
     * private fun segnalaPagina(motore: MotoreRicerca, id: String, stato: Int, html: String, testo: String?) {
     *     if (diagnosticaPaginaScritta) return
     *     diagnosticaPaginaScritta = true
     *     motore.diagnostica.append(
     *         "[vinted] pagina articolo $id (ripiego) -> stato $stato, ${html.length} caratteri · descrizione: " +
     *                 ...
     * --- fine versione precedente ---
     */
    private var pagineSegnalate = 0

    @Synchronized
    private fun segnalaPagina(motore: MotoreRicerca, id: String, stato: Int, html: String, testo: String?, info: String) {
        if (pagineSegnalate >= 3) return
        pagineSegnalate++
        motore.diagnostica.append(
            "[vinted] pagina articolo $id (ripiego) -> stato $stato, $info · descrizione: " +
                    (testo?.let { "\"" + it.take(120).replace("\n", " ") + "\"" } ?: "(nessuna)") + "\n"
        )
    }

    /** v1.60.0 — come dataDa(), ma dalla pagina HTML. */
    @Synchronized
    private fun dataDaHtml(motore: MotoreRicerca, id: String, html: String, dataPronta: (String, Long) -> Unit) {
        val esito = DataCaricamentoVinted.leggiHtml(html)
        if (esito != null) registraData(motore, id, esito, "pagina", dataPronta)
        else if (!diagnosticaDataMancante) {
            diagnosticaDataMancante = true
            motore.diagnostica.append(
                "[vinted] caricato articolo $id (pagina): NON trovato · " +
                        DataCaricamentoVinted.indiziHtml(html).take(900).replace("\n", " ") + "\n"
            )
        }
    }

    /*
     * v1.59.0 — legge "Caricato", lo conserva, lo passa a chi aspetta e, se
     * la lettura e' al minuto (l'unica piu' precisa della stima dall'id), ne
     * fa un'ancora per StimaDataVinted: ogni annuncio recente aperto migliora
     * la data stimata di tutti gli altri. Scrive la diagnostica una volta per
     * esito (trovata / non trovata), con gli indizi quando non si trova.
     */
    @Synchronized
    private fun dataDa(motore: MotoreRicerca, id: String, corpo: String, dataPronta: (String, Long) -> Unit) {
        val esito = DataCaricamentoVinted.leggi(corpo)
        if (esito != null) {
            registraData(motore, id, esito, "dettaglio", dataPronta)   // v1.60.0: in comune con la pagina
        } else if (!diagnosticaDataMancante) {
            diagnosticaDataMancante = true
            motore.diagnostica.append(
                "[vinted] caricato articolo $id: NON trovato · " +
                        DataCaricamentoVinted.indizi(corpo).take(700) + "\n"
            )
        }
    }

    /*
     * v1.60.0 — la parte comune a dataDa() e dataDaHtml(), prima scritta
     * dentro dataDa() (v1.59.0, stesso contenuto).
     */
    @Synchronized
    private fun registraData(
        motore: MotoreRicerca, id: String, esito: DataCaricamentoVinted.Esito,
        origine: String, dataPronta: (String, Long) -> Unit
    ) {
        // v1.61.0 — la stima si calcola PRIMA di registrare la lettura, altrimenti
        // la diagnostica confronterebbe la lettura con se stessa
        val stimaPrima = id.toLongOrNull()?.let { StimaDataVinted.stima(it) }
        run {
            dateNote[id] = esito.ms
            daSalvare = true
            /*
             * v1.61.0 — anche le letture all'ora ("5 ore fa"), non solo al
             * minuto, e come "lettura" (non come ancora dal vivo): vedi
             * StimaDataVinted.registraLettura.
             *
             * --- versione precedente (v1.59.0-v1.60.0) ---
             * if (esito.precisioneMs <= 60_000L) id.toLongOrNull()?.let { StimaDataVinted.registra(it, esito.ms) }
             * --- fine versione precedente ---
             */
            id.toLongOrNull()?.let { StimaDataVinted.registraLettura(it, esito.ms, esito.precisioneMs) }
            ui.post { dataPronta(id, esito.ms) }
            // v1.61.0 — le prime CINQUE letture, non una: si vede se lo
            // scarto fra stima e lettura cala man mano che arrivano letture
            if (lettureSegnalate < 5) {
                lettureSegnalate++
                motore.diagnostica.append(
                    "[vinted] caricato articolo $id ($origine): \"${esito.testo}\" -> " +
                            java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.ITALY)
                                .format(java.util.Date(esito.ms)) +
                            " (stima dall'id: " + (stimaPrima?.let {
                                java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.ITALY)
                                    .format(java.util.Date(it))
                            } ?: "-") + ")\n"
                )
            }
        }
    }

    /*
     * v1.59.0 — porta anche "dataPronta" per ripartire con il prossimo.
     * --- versione precedente: stessa firma senza "dataPronta" ---
     */
    @Synchronized
    private fun concluso(
        ctx: Context,
        motore: MotoreRicerca,
        id: String,
        testo: String?,
        dataPronta: (String, Long) -> Unit,
        aggiornato: (String, String) -> Unit
    ) {
        inVolo.remove(id)
        attivi--
        if (testo != null) {
            noti[id] = testo
            // Il tetto si applica qui, subito: la piu' vecchia esce quando
            // entra la nuova, cosi' la mappa non cresce mai oltre MAX_CACHE.
            while (noti.size > MAX_CACHE) {
                val piuVecchia = noti.keys.firstOrNull() ?: break
                noti.remove(piuVecchia)
            }
            daSalvare = true
            if (testo.isNotBlank()) ui.post { aggiornato(id, testo) }
        }
        // Se la chiamata e' fallita non si registra nulla: la prossima volta
        // che l'annuncio compare si riprova. Nessuna lista di "falliti" come
        // in LuoghiVinted — li' un venditore torna spesso e insistere avrebbe
        // un costo, qui l'annuncio probabilmente non lo si rivede piu'.
        salvaFraPoco(ctx)
        if (coda.isNotEmpty()) ui.postDelayed({ avviaProssimo(ctx, motore, dataPronta, aggiornato) }, PAUSA_MS)
    }

    /*
     * v1.47.0 — lettura del testo dalla risposta VERA dell'endpoint di
     * traduzione: {"item":{...un modello, sempre vuoto...},"plugins":[...]}.
     * Il testo sta in "plugins", nell'elemento con "name":"description",
     * dentro "data.description" — vedi l'esempio completo in cima al file.
     * Non si presume che sia sempre il primo elemento dell'array (l'ordine
     * non e' garantito da nessuna parte): si scorre l'array e si prende il
     * primo plugin il cui nome e' "description".
     */
    private fun leggiDescrizionePlugin(corpo: String): String? = try {
        val root = JSONObject(corpo)
        val plugins = root.optJSONArray("plugins")
        var trovato: String? = null
        if (plugins != null) {
            for (i in 0 until plugins.length()) {
                val p = plugins.optJSONObject(i) ?: continue
                if (p.optString("name") != "description") continue
                val v = p.optJSONObject("data")?.optString("description")?.trim().orEmpty()
                if (v.isNotEmpty() && v != "null") { trovato = v; break }
            }
        }
        trovato?.let { pulisci(it) }
    } catch (t: Throwable) { null }

    /*
     * --- versione precedente (v1.43.0-v1.46.0, lettura dal blocco JSON-LD
     *     della pagina intera — funzionava, ma portava solo il testo
     *     ORIGINALE, mai la traduzione: vedi il commento in cima al file) ---
     * private val SCRIPT_LDJSON = Regex(
     *     "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>([\\s\\S]*?)</script>",
     *     RegexOption.IGNORE_CASE
     * )
     * private val SCRIPT_QUALSIASI = Regex(
     *     "<script[^>]*>([\\s\\S]*?)</script>",
     *     RegexOption.IGNORE_CASE
     * )
     *
     * private fun leggiDescrizioneHtml(html: String): String? {
     *     for (m in SCRIPT_LDJSON.findAll(html)) {
     *         leggiDaBloccoJson(m.groupValues[1])?.let { return pulisci(it) }
     *     }
     *     for (m in SCRIPT_QUALSIASI.findAll(html)) {
     *         val corpo = m.groupValues[1]
     *         if (corpo.contains("\"description\"")) {
     *             leggiDaBloccoJson(corpo)?.let { return pulisci(it) }
     *         }
     *     }
     *     return null
     * }
     *
     * private fun leggiDaBloccoJson(testo: String): String? = try {
     *     val t = testo.trim()
     *     when {
     *         t.startsWith("[") -> leggiDescrizioneDaArray(JSONArray(t))
     *         t.startsWith("{") -> {
     *             val o = JSONObject(t)
     *             val graph = o.optJSONArray("@graph")
     *             if (graph != null) leggiDescrizioneDaArray(graph)
     *             else campoTesto(o)
     *         }
     *         else -> null
     *     }
     * } catch (e: Throwable) { null }
     *
     * private fun leggiDescrizioneDaArray(arr: JSONArray): String? {
     *     for (i in 0 until arr.length()) {
     *         val o = arr.optJSONObject(i) ?: continue
     *         campoTesto(o)?.let { return it }
     *     }
     *     return null
     * }
     *
     * private fun campoTesto(o: JSONObject): String? {
     *     val v = o.optString("description").trim()
     *     return if (v.isNotEmpty() && v != "null") v else null
     * }
     * --- fine versione precedente ---
     *
     * --- versione precedente (fino alla v1.42.0, lettura da risposta JSON
     *     dell'API "/api/v2/items/<id>", mai risultata una rotta vera) ---
     * private val CAMPI_TESTO = listOf(
     *     "description", "body", "item_description", "description_text", "text"
     * )
     *
     * private fun leggiDescrizione(corpo: String): String? = try {
     *     val root = JSONObject(corpo)
     *     val o = root.optJSONObject("item") ?: root
     *     var trovato: String? = null
     *     for (k in CAMPI_TESTO) {
     *         val v = o.optString(k).trim()
     *         if (v.isNotEmpty() && v != "null") { trovato = v; break }
     *     }
     *     trovato?.let { pulisci(it) }
     * } catch (t: Throwable) { null }
     * --- fine versione precedente ---
     */

    /**
     * Il testo com'e' scritto dal venditore (o gia' tradotto, quando Vinted
     * traduce), solo accorciato e senza le righe vuote di troppo: su Vinted
     * si va spesso a capo per elencare misure e difetti, e sei righe di
     * elenco si riempirebbero di spazi invece che di parole.
     */
    /*
     * v1.60.0 (2026-09-24) — RIPRISTINATO dal commento qui sopra (lettore
     * v1.43.0-v1.46.0, JSON-LD della pagina intera): serve al ripiego sulla
     * pagina quando l'endpoint del dettaglio rifiuta (daPagina()). Stesso
     * codice di allora, che funzionava; porta il testo originale, non la
     * traduzione.
     */
    private val SCRIPT_LDJSON = Regex(
        "<script[^>]*type=[\"']application/ld\\+json[\"'][^>]*>([\\s\\S]*?)</script>",
        RegexOption.IGNORE_CASE
    )
    private val SCRIPT_QUALSIASI = Regex(
        "<script[^>]*>([\\s\\S]*?)</script>",
        RegexOption.IGNORE_CASE
    )

    private fun leggiDescrizioneHtml(html: String): String? {
        for (m in SCRIPT_LDJSON.findAll(html)) {
            leggiDaBloccoJson(m.groupValues[1])?.let { return pulisci(it) }
        }
        for (m in SCRIPT_QUALSIASI.findAll(html)) {
            val corpo = m.groupValues[1]
            if (corpo.contains("\"description\"")) {
                leggiDaBloccoJson(corpo)?.let { return pulisci(it) }
            }
        }
        return null
    }

    private fun leggiDaBloccoJson(testo: String): String? = try {
        val t = testo.trim()
        when {
            t.startsWith("[") -> leggiDescrizioneDaArray(org.json.JSONArray(t))
            t.startsWith("{") -> {
                val o = JSONObject(t)
                val graph = o.optJSONArray("@graph")
                if (graph != null) leggiDescrizioneDaArray(graph)
                else campoTesto(o)
            }
            else -> null
        }
    } catch (e: Throwable) { null }

    private fun leggiDescrizioneDaArray(arr: org.json.JSONArray): String? {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            campoTesto(o)?.let { return it }
        }
        return null
    }

    private fun campoTesto(o: JSONObject): String? {
        val v = o.optString("description").trim()
        return if (v.isNotEmpty() && v != "null") v else null
    }

    private fun pulisci(s: String): String {
        val compatto = s.replace(Regex("\n{2,}"), "\n").trim()
        return if (compatto.length <= MAX_TESTO) compatto
        else compatto.take(MAX_TESTO).trimEnd() + "…"
    }

    /*
     * La prova da mandare, se non si e' trovato testo. Non si scrive l'intero
     * corpo (puo' essere lungo): stato e un pezzo, che bastano a capire se la
     * risposta e' arrivata per davvero (JSON vero) o se la sessione ha
     * ricevuto qualcos'altro (per esempio un blocco anti-bot).
     */
    /*
     * v1.49.0 — CORREZIONE: si chiama sempre, non solo sui fallimenti (vedi
     * il commento in cima al file, "IL BUCO DIAGNOSTICO..."). Prima si
     * sapeva SE la chiamata falliva, mai COSA rispondeva quando "riusciva"
     * ma con lingua sbagliata — il caso rimasto proprio quando serviva
     * vederlo. Ora la firma porta anche "testo" (il risultato di
     * leggiDescrizionePlugin, null se non trovato) e lo si scrive in
     * chiaro: cosi' la riga dice sia cosa e' arrivato sia cosa se ne e'
     * ricavato, senza dover rileggere a mano il corpo JSON ogni volta.
     *
     * --- versione precedente (v1.47.0-v1.48.0, solo sui fallimenti) ---
     * @Synchronized
     * private fun segnala(motore: MotoreRicerca, id: String, stato: Int, corpo: String) {
     *     if (diagnosticaScritta) return
     *     diagnosticaScritta = true
     *     motore.diagnostica.append(
     *         "[vinted] descrizione articolo $id (translatable) -> stato $stato\n" +
     *                 "  cercato: \"plugins\" con un elemento name=\"description\" e " +
     *                 "data.description\n" +
     *                 "  corpo (primi 1200): ${corpo.take(1200)}\n"
     *     )
     * }
     * --- fine versione precedente ---
     *
     * --- versione precedente (v1.43.0-v1.46.0) ---
     * // stessa forma, ma cercava un <script> ld+json invece di "plugins"
     * // (vedi la versione precedente ancora piu' sotto in leggiDaBloccoJson)
     * --- fine versione precedente ---
     *
     * --- versione precedente (fino alla v1.42.0) ---
     * // stessa forma, ma cercava i campi di CAMPI_TESTO dentro "item"
     * // invece di "plugins" (endpoint /api/v2/items/<id>, mai risultato
     * // una rotta vera)
     * --- fine versione precedente ---
     */
    @Synchronized
    private fun segnala(motore: MotoreRicerca, id: String, stato: Int, corpo: String, testo: String?) {
        if (diagnosticaScritta) return
        diagnosticaScritta = true
        motore.diagnostica.append(
            "[vinted] descrizione articolo $id (translatable) -> stato $stato\n" +
                    "  testo estratto: " +
                    (testo?.let { "\"" + it.take(200).replace("\n", " ") + "\"" }
                        ?: "(nessuno — vedi il corpo qui sotto)") + "\n" +
                    "  corpo (primi 1200): ${corpo.take(1200)}\n"
        )
    }

    // ------------------------------------------------------------------
    private fun salvaFraPoco(ctx: Context) {
        if (!daSalvare) return
        // Si scrive una volta sola a fine raffica, non a ogni risposta.
        ui.removeCallbacks(salvataggio)
        contesto = ctx.applicationContext
        ui.postDelayed(salvataggio, 1500)
    }

    private var contesto: Context? = null
    private val salvataggio = Runnable { salva() }

    @Synchronized
    /*
     * v1.48.0 — scrive CACHE_VERSIONE (la costante confrontata da carica(),
     * sopra), non piu' una stringa fissa scritta a mano: cosi' non si
     * dimentica di allinearle di nuovo alla prossima versione che cambia
     * ancora una volta il meccanismo.
     *
     * --- versione precedente (fino alla v1.47.0) ---
     * File(ctx.filesDir, FILE).writeText(
     *     JSONObject().put("versione", "1.47.0").put("descrizioni", o).toString()
     * )
     * --- fine versione precedente ---
     */
    private fun salva() {
        val ctx = contesto ?: return
        daSalvare = false
        try {
            val o = JSONObject()
            noti.forEach { (k, v) -> o.put(k, v) }
            // v1.59.0 — anche le date "Caricato", solo per gli annunci ancora in cache
            val d = JSONObject()
            dateNote.keys.retainAll(noti.keys + inVolo)
            dateNote.forEach { (k, v) -> d.put(k, v) }
            File(ctx.filesDir, FILE).writeText(
                JSONObject().put("versione", CACHE_VERSIONE).put("descrizioni", o).put("date", d).toString()
            )
        } catch (t: Throwable) { /* la cache e' un lusso, non un dato */ }
    }

    @Synchronized
    /*
     * v1.51.0 — "urlPerId" tolto dal reset insieme a lui: non esiste piu'
     * (vedi il 5° tentativo in cima al file — regressione della v1.50.0).
     *
     * --- versione precedente (v1.50.0) ---
     * noti.clear(); coda.clear(); urlPerId.clear()
     * --- fine versione precedente ---
     */
    fun svuota(ctx: Context) {
        noti.clear(); coda.clear(); dateNote.clear()   // v1.59.0: anche le date
        try { File(ctx.filesDir, FILE).delete() } catch (t: Throwable) {}
    }
}
