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
     *  - venditore: id del venditore su Vinted, unica via per il luogo.
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
     */
    val immagineGrande: String? = null,
    val descrizione: String = "",
    val urn: String = "",
    val venditore: String = "",
    val preferitoIniziale: Boolean = false
) {
    /** Chiave usata nell'archivio degli scartati. Gli annunci Subito restano
     *  con l'ID nudo per compatibilita' con quanto gia' nascosto dalla v0.3.0. */
    val chiave: String get() = if (fonte == "subito") id else "v:$id"
}
