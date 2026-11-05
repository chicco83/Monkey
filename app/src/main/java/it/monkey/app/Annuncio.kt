package it.monkey.app

/*
 * Subito+ — modello comune degli annunci
 * v0.5.1 (2026-08-30)
 *
 * Ogni fonte restituisce cose diverse; qui tutto diventa la stessa cosa.
 * L'identificatore porta con se' la fonte ("subito" / "vinted") perche'
 * due piattaforme possono usare lo stesso numero per annunci diversi.
 *
 * v0.5.1 — "luogo" e "dettaglio" erano finiti nello stesso campo, e in elenco
 * accanto agli annunci Subito compariva la localita' mentre accanto a quelli
 * Vinted compariva la marca: due cose diverse nella stessa posizione.
 * Ora sono due campi distinti e ognuno va al suo posto.
 */

data class Annuncio(
    val fonte: String,          // "subito" | "vinted"
    val id: String,             // identificatore nativo della fonte
    val titolo: String,
    val prezzo: Double?,        // null quando la fonte non lo espone
    val prezzoTesto: String,
    val luogo: String,          // dove si trova la merce
    val dettaglio: String,      // marca, taglia: cio' che la fonte aggiunge di suo
    val venduto: Boolean,
    val dataMillis: Long,       // 0 se sconosciuta: finisce in fondo negli ordinamenti per data
    val immagine: String?,
    val url: String,
    /*
     * v1.2.0 — quello che serve per i preferiti, ricavato dalla risposta di
     * ricerca e non altrove:
     *  - urn: identificatore lungo di Subito ("id:ad:<uuid>:list:<numero>"),
     *    che e' la forma richiesta dal suo servizio preferiti. Il numero da
     *    solo non basta.
     *  - venditore: id del venditore. Su Vinted e' l'unica via per il luogo
     *    (LuoghiVinted). Su Subito, dalla v1.30.0, e' "advertiser.user_id"
     *    dei risultati di ricerca, e serve alle recensioni (RecensioniSubito).
     *  - preferitoIniziale: Vinted lo dichiara gia' nei risultati; Subito no,
     *    e li' lo stato arriva da una chiamata a parte.
     */
    /*
     * v1.7.0 — due campi che servono solo alla vista estesa:
     *  - immagineGrande: la stessa foto in una resa adatta a tutta la larghezza
     *    dello schermo. Quella d'elenco e' 310 px (Vinted) e stirata su 1080
     *    si vede sgranata. Verificato sul campo quali rese esistono davvero:
     *    Subito ?rule=gallery-mobile-3x-auto -> 1164x748 (le altre danno errore),
     *    Vinted photo.url -> f800.
     *  - descrizione: il testo dell'annuncio, che su Subito arriva gia' con i
     *    risultati (campo "body") e riempie lo spazio vuoto accanto ai dati.
     *    Vinted nel catalogo non lo espone: li' resta vuoto e la colonna sparisce.
     *    (Non e' un bug da correggere qui: e' l'elenco di ricerca Vinted che
     *    non porta il testo, vedi FonteVinted.leggi in Fonti.kt.)
     *
     *  - immagini (v1.32.0): TUTTE le foto dell'annuncio, non solo la prima,
     *    per farle scorrere nella vista estesa (SchermataRicerca.RigaAnnuncio).
     *    Su Subito viene dallo stesso array "images" gia' letto per la prima
     *    foto (FonteSubito.leggi la leggeva solo in posizione 0); su Vinted
     *    dal campo "photos" (plurale, distinto da "photo" singolare gia' in
     *    uso) — visto SOLO come nome di campo nell'elenco chiavi di una
     *    diagnostica esportata, non ancora in una sonda con il contenuto
     *    vero: si presume la stessa forma dell'oggetto "photo" singolare
     *    (stesso "url"), perche' e' la stessa API. Se restasse vuoto o
     *    sbagliato per Vinted, la vista estesa ripiega da sola su una sola
     *    foto (vedi sotto) — nessun rischio, solo da confermare con una
     *    sonda vera il giorno che si vuole essere certi anche li'.
     */
    val immagineGrande: String? = null,
    val immagini: List<String> = emptyList(),
    val descrizione: String = "",
    val urn: String = "",
    val venditore: String = "",
    val preferitoIniziale: Boolean = false,
    /*
     * v1.58.0 (2026-09-24) — vero quando dataMillis NON e' una data
     * dichiarata dalla fonte ma una stima (oggi: solo gli annunci Vinted letti
     * dalla pagina del catalogo, vedi StimaDataVinted.kt). La riga in elenco
     * la mostra con "≈" davanti, per non spacciare una stima per un fatto.
     * Ultimo parametro e con valore di default: nessuna chiamata esistente
     * cambia.
     */
    val dataStimata: Boolean = false
) {
    /** Chiave usata nell'archivio degli scartati. Gli annunci Subito restano
     *  con l'ID nudo per compatibilita' con quanto gia' nascosto dalla v0.3.0. */
    val chiave: String get() = if (fonte == "subito") id else "v:$id"
}
