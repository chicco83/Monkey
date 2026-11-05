package it.monkey.app

/*
 * Subito+ — schermata di ricerca
 * v0.5.2 (2026-08-30) — Fase 4, punti 2, 3 e 5 del restyling
 *
 * Sostituisce VistaRicerca.kt (viste tradizionali, v0.4.0 - v0.5.1), rimosso.
 * Cosa cambia rispetto a quella:
 *   - le tre righe di filtri che occupavano mezzo schermo diventano un foglio
 *     che sale dal basso; in cima restano solo le etichette dei filtri attivi
 *   - la lista ha degli stati: scheletri mentre carica, "nessun risultato",
 *     e un errore per fonte con il suo "riprova" invece di una riga di testo
 *   - le pagine successive arrivano da sole scorrendo, senza il pulsante
 *   - scartare un annuncio si puo' annullare subito, senza aprire l'elenco
 */

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
// v1.34.0 — swipe per nascondere (vedi RigaAnnuncio) e i due pulsanti a
// cerchio della vista estesa (vedi lo stesso commento, piu' sotto).
import androidx.compose.foundation.clickable
// v1.36.0 — tenere premuto su una ricerca recente per cancellarla (vedi
// ChipRicercaRecente) e la nuova icona compatta/estesa sotto il campo di
// ricerca (vedi IconVistaLista): niente di nuovo da material-icons-extended,
// si disegnano a mano con Box, stessa scelta gia' fatta per preferiti/nascondi.
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
// v1.32.0 — pager delle foto nella vista estesa (vedi il commento sopra il Box in RigaAnnuncio).
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
// v1.31.0 — "Send" (v1.28.0, icona "hai scritto al venditore") tolto insieme
// all'icona: vedi il commento sopra Descrizione() piu' in basso in questo file.
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val ROSSO_SUBITO = Color(0xFFE3001B)
/* v1.4.0 — piu' scuro del cuore: e' un dato da leggere, non un comando. */
private val ROSSO_OFFERTA = Color(0xFFC62828)
private val ROSSO_CUORE = Color(0xFFE23A48)
private val TEAL_VINTED = Color(0xFF0E9BA4)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SchermataRicerca(
    stato: StatoRicerca,
    apri: (Annuncio) -> Unit,
    /** v0.6.0 — salva la ricerca corrente con i suoi filtri. */
    salva: () -> Unit = {},
    /** v1.8.0 — apre un indirizzo nella navigazione (le proposte in home). */
    apriUrl: (String) -> Unit = {},
    /*
     * v1.0.5 (2026-08-30) — CORREZIONE.
     * "Vista compatta" nel menu agiva solo sulle pagine web: la lista dei
     * risultati era scritta con un unico disegno, foto piccola a sinistra e
     * testo a destra, e l'interruttore sembrava rotto perche' non aveva nulla
     * su cui agire. Ora la lista ha due disposizioni e l'interruttore le alterna.
     */
    compatta: Boolean = true,
    /*
     * v1.36.0 (2026-09-04) — nuovo: due comandi che prima vivevano solo nel
     * menu ⋮ di BarraSuperiore (vedi il commento li' e su IconVistaLista qui
     * sotto) e adesso si raggiungono da qui, dove si guarda mentre si cerca.
     *
     * "apriRicerche" e' lo stesso apriRicerche() di sempre (vedi
     * MainActivity.azioni()): la lente nel campo di ricerca, prima solo
     * decorativa (Icon senza onClick), diventa un tasto vero verso le
     * ricerche salvate, invece di restare un'icona senza funzione chiara.
     */
    apriRicerche: () -> Unit = {},
    /** v1.36.0 — sostituisce la voce "Vista compatta" tolta dal menu ⋮. */
    cambiaVista: () -> Unit = {}
) {

    val avvisi = remember { SnackbarHostState() }
    val ambito = rememberCoroutineScope()
    var filtriAperti by remember { mutableStateOf(false) }
    val tastiera = LocalSoftwareKeyboardController.current
    val fuoco = LocalFocusManager.current
    val scorrimento = rememberLazyListState()

    // v1.2.0 — se un preferito non passa, lo si dice invece di mentire
    LaunchedEffect(stato.messaggio) {
        stato.messaggio?.let { avvisi.showSnackbar(it); stato.messaggio = null }
    }

    /*
     * v1.37.0 (2026-09-04) — CORREZIONE: scorrendo una ricerca e avviandone
     * un'altra (per esempio aprendo una ricerca salvata dalla campanella,
     * ma vale per qualunque nuova ricerca) la lista non tornava in cima: lo
     * scorrimento restava dov'era, e i nuovi annunci — scritti dalla stessa
     * posizione 0 della vecchia lista — comparivano mescolati con quelli
     * vecchi ancora a schermo, sopra E sotto il punto in cui si era arrivati
     * a scorrere. "ultimaQuery" cambia solo quando parte DAVVERO una nuova
     * ricerca (StatoRicerca.cerca() e apriPreferiti(), non altraPagina(): la
     * paginazione non deve far saltare la lista in cima mentre si scorre).
     */
    LaunchedEffect(stato.ultimaQuery) {
        if (stato.ultimaQuery.isNotEmpty()) scorrimento.scrollToItem(0)
    }

    // Le pagine successive arrivano avvicinandosi al fondo.
    LaunchedEffect(scorrimento) {
        snapshotFlow { scorrimento.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { ultimo ->
                if (ultimo >= 0 && ultimo >= stato.risultati.size - 4) stato.altraPagina()
            }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(avvisi) },
        containerColor = MaterialTheme.colorScheme.background
    ) { bordi ->
        Column(
            Modifier
                .padding(bordi)
                .fillMaxSize()
        ) {

            // ---------------- campo di ricerca ----------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = stato.query,
                    onValueChange = { stato.query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Cerca su Subito e Vinted") },
                    /*
                     * v1.36.0 (2026-09-04) — CORREZIONE: la lente era solo
                     * decorativa (Icon senza alcun onClick), "un'icona senza
                     * funzione chiara" — si poteva toccare e non succedeva
                     * niente. Ora e' un tasto vero: apre le ricerche salvate,
                     * stessa azione della campanella in barra (vedi il
                     * parametro "apriRicerche" della funzione).
                     *
                     * --- versione precedente (fino alla v1.35.0) ---
                     * leadingIcon = { Icon(Icons.Filled.Search, null) },
                     * --- fine versione precedente ---
                     */
                    leadingIcon = {
                        IconButton(onClick = apriRicerche) {
                            Icon(Icons.Filled.Search, "Ricerche salvate")
                        }
                    },
                    trailingIcon = {
                        if (stato.query.isNotEmpty()) IconButton(onClick = { stato.query = "" }) {
                            Icon(Icons.Filled.Clear, "Cancella")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        tastiera?.hide(); fuoco.clearFocus(); stato.cerca()
                    })
                )
                Spacer(Modifier.width(4.dp))
                BadgedBox(badge = {
                    if (stato.filtriAttivi > 0) Badge { Text("${stato.filtriAttivi}") }
                }) {
                    TextButton(onClick = { filtriAperti = true }) { Text("Filtri") }
                }
            }

            // ---------------- ricerche recenti ----------------
            if (!stato.primaRicercaFatta && stato.recenti.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    /*
                     * v1.36.0 (2026-09-04) — nuovo: tenendo premuto su una di
                     * queste etichette la si cancella dall'elenco (vedi
                     * ChipRicercaRecente e StatoRicerca.dimenticaRecente).
                     * Un SuggestionChip normale non offre un onLongClick,
                     * solo onClick — da qui il piccolo composable sotto,
                     * stessa idea gia' usata per i pulsanti preferiti/nascondi
                     * (Box disegnato a mano invece del componente Material3
                     * quando serve un comportamento che quello non offre).
                     *
                     * --- versione precedente (fino alla v1.35.0) ---
                     * stato.recenti.forEach { r ->
                     *     SuggestionChip(
                     *         onClick = {
                     *             stato.query = r
                     *             tastiera?.hide(); fuoco.clearFocus(); stato.cerca()
                     *         },
                     *         label = { Text(r) }
                     *     )
                     * }
                     * --- fine versione precedente ---
                     */
                    stato.recenti.forEach { r ->
                        ChipRicercaRecente(
                            testo = r,
                            onClick = {
                                stato.query = r
                                tastiera?.hide(); fuoco.clearFocus(); stato.cerca()
                            },
                            onLongClick = {
                                stato.dimenticaRecente(r)
                                ambito.launch { avvisi.showSnackbar("Rimossa: $r") }
                            }
                        )
                    }
                }
            }

            /*
             * v1.36.0 (2026-09-04) — nuovo: sostituisce la voce "Vista
             * compatta" tolta dal menu ⋮ (vedi il commento in cima a
             * BarraSuperiore.kt). Sempre visibile — a differenza della voce
             * nel menu, non serve aprire nulla per raggiungerla.
             *
             * v1.37.0 (2026-09-04) — spostata qui SOTTO le ricerche recenti
             * (prima stava subito sotto il campo di ricerca, sopra le
             * recenti): l'utente l'ha chiesto esplicitamente.
             *
             * --- versione precedente (v1.36.0, subito sotto il campo di ricerca) ---
             * stessa Row, stesso contenuto, solo prima della sezione
             * "ricerche recenti" invece che dopo.
             * --- fine versione precedente ---
             */
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = cambiaVista)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconVistaLista(compatta, MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (compatta) "Vista compatta" else "Vista estesa",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ---------------- filtri attivi, in breve ----------------
            /*
             * v1.9.0 — la schermata iniziale.
             * Sopra le scorciatoie alle ultime ricerche, sotto le proposte
             * aperte in colonna, che riempiono la pagina invece di lasciarla
             * bianca. I due pulsanti sono diventati icone in barra.
             *
             * --- versione precedente (v1.8.0, 2026-08-31) ---
             * due OutlinedButton "Preferiti" e "Ricerche salvate" qui,
             * piu' una LazyRow orizzontale con sei tessere.
             * --- fine versione precedente ---
             */
            if (stato.filtriAttivi > 0) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    etichetteFiltri(stato).forEach { (testo, togli) ->
                        InputChip(
                            selected = true,
                            onClick = togli,
                            label = { Text(testo) },
                            trailingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(16.dp)) }
                        )
                    }
                }
            }

            /*
             * ---------------- login perso (v1.63.0, 2026-09-24) ----------------
             * Un avviso per ogni fonte VERIFICATA come non collegata
             * (StatoRicerca.accessi == false; "non so" non mostra nulla).
             * "Accedi" apre il sito della fonte in "Sito", dove si fa il
             * login; tornando alla ricerca il controllo riparte da solo
             * (MainActivity.applicaModo) e l'avviso sparisce.
             */
            stato.accessi.filterValues { !it }.keys.sorted().forEach { fonte ->
                val nome = fonte.replaceFirstChar { it.uppercase() }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Non sei collegato a $nome: risultati, date, descrizioni, " +
                                    "preferiti e offerte possono mancare o essere incompleti.",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = {
                            apriUrl(if (fonte == "vinted") "https://www.vinted.it/" else "https://www.subito.it/")
                        }) { Text("Accedi") }
                    }
                }
            }

            // ---------------- errori per fonte ----------------
            stato.errori.forEach { (fonte, messaggio) ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${fonte.replaceFirstChar { it.uppercase() }}: $messaggio",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = { stato.riprova(fonte) }) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Riprova")
                        }
                    }
                }
            }

            /*
             * v1.12.0 — il riepilogo ("N risultati…" + "Salva ricerca") sta qui,
             * fisso, e non dentro la LazyColumn: cosi' non scorre via con gli
             * annunci e resta sempre leggibile. Compare solo quando c'e' una
             * lista vera da riassumere (stessa condizione che prima sceglieva
             * il ramo "else" del "when" sottostante).
             */
            val lista = stato.risultati
            if (stato.primaRicercaFatta && lista.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        riepilogo(stato),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // v0.6.0 — da qui nasce una ricerca salvata
                    TextButton(onClick = salva) { Text("Salva ricerca") }
                }
            }

            // ---------------- corpo ----------------
            when {
                stato.inCorso && lista.isEmpty() -> Scheletri()

                // v1.9.0 — schermata iniziale: le proposte riempiono la pagina
                !stato.primaRicercaFatta && stato.offerteRecenti().isNotEmpty() -> {
                    val offerte = stato.offerteRecenti()
                    LazyColumn(
                        state = scorrimento,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            // v1.26.0 — "ultime" non e' piu' esatto: ora sono tutte (vedi StatoRicerca.offerteRecenti()).
                            Text(
                                "Le tue proposte",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            )
                        }
                        /*
                         * v1.10.0 — chiave "fonte:id" invece del solo "id": da
                         * quando le offerte arrivano anche da Vinted, un id
                         * Subito e uno Vinted potrebbero coincidere per puro
                         * caso (sono numerazioni indipendenti) e LazyColumn
                         * vuole chiavi uniche in tutta la lista.
                         *
                         * --- versione precedente (fino alla v1.9.0, solo Subito) ---
                         * items(offerte, key = { it.id }) { o -> RigaOfferta(o, apriUrl) }
                         * --- fine versione precedente ---
                         */
                        items(offerte, key = { "${it.fonte}:${it.id}" }) { o -> RigaOfferta(o, apriUrl) }
                    }
                }

                !stato.primaRicercaFatta -> Vuoto(
                    "Cerca qualcosa",
                    "I risultati di Subito e Vinted arrivano insieme, in una lista sola."
                )

                lista.isEmpty() && stato.esclusi > 0 -> Vuoto(
                    "Tutto escluso dai filtri",
                    "${stato.esclusi} risultati non passano i filtri attivi.",
                    "Azzera i filtri"
                ) { stato.azzeraFiltri() }

                lista.isEmpty() -> Vuoto(
                    "Nessun risultato",
                    "Nessuna delle due fonti ha trovato «${stato.ultimaQuery}»."
                )

                /*
                 * v1.12.0 (2026-08-31) — la riga del riepilogo ("N risultati…") non e'
                 * piu' il primo item della LazyColumn: scorreva via con gli annunci
                 * ed era illeggibile appena si scendeva di una riga. Ora e' un Row
                 * fisso subito FUORI dalla lista (vedi sotto, prima del "when"), e
                 * resta visibile sempre, come le etichette dei filtri sopra di lei.
                 *
                 * --- versione precedente (v0.6.0, 2026-08-30) ---
                 * else -> LazyColumn(...) {
                 *     item {
                 *         Row(verticalAlignment = Alignment.CenterVertically) {
                 *             Text(riepilogo(stato), Modifier.weight(1f), …)
                 *             TextButton(onClick = salva) { Text("Salva ricerca") }
                 *         }
                 *     }
                 *     items(lista, key = { it.fonte + it.id }) { a -> … }
                 * }
                 * --- fine versione precedente ---
                 */
                else -> LazyColumn(
                    state = scorrimento,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(lista, key = { it.fonte + it.id }) { a ->
                        /*
                         * v1.59.0 (2026-09-24) — ogni riga Vinted chiede il
                         * dettaglio anche in vista compatta: da li' arriva la
                         * data "Caricato" (oltre alla descrizione). La riga
                         * riceve l'annuncio con la data vera al posto della
                         * stima appena arriva (stato.conDataVera); la
                         * posizione in lista non cambia — vedi il commento su
                         * StatoRicerca.dateVinted.
                         *
                         * --- versione precedente (fino alla v1.58.0) ---
                         * RigaAnnuncio(
                         *     a,
                         *     compatta = compatta,
                         *     ...
                         * --- fine versione precedente ---
                         */
                        LaunchedEffect(a.fonte, a.id) { stato.chiediDescrizione(a) }
                        RigaAnnuncio(
                            stato.conDataVera(a),
                            compatta = compatta,
                            // v1.2.0 — su Vinted il luogo arriva dal profilo del
                            // venditore, dopo i risultati: lo legge lo stato.
                            coda = stato.coda(a),
                            // v1.4.0 — «offerti xxx €»: null se su questo annuncio
                            // non hai una proposta aperta
                            offerta = stato.offerta(a),
                            /*
                             * v1.40.0 — il testo dell'annuncio non si legge piu'
                             * da "a.descrizione": su Subito e' la stessa cosa, su
                             * Vinted quel campo e' sempre vuoto (il catalogo non
                             * lo porta) e il testo arriva dopo, dallo stato — come
                             * gia' succede per il luogo qui sopra. Vedi
                             * StatoRicerca.descrizione() e DescrizioniVinted.kt.
                             */
                            descrizione = stato.descrizione(a),
                            chiediDescrizione = { stato.chiediDescrizione(a) },
                            // v1.31.0 — "contattato" (v1.28.0) tolto: vedi il
                            // commento sopra Descrizione() in questo stesso file.
                            preferito = stato.ePreferito(a),
                            cambiaPreferito = { stato.cambiaPreferito(a) },
                            apri = { apri(a) },
                            nascondi = {
                                stato.nascondi(a)
                                ambito.launch {
                                    val r = avvisi.showSnackbar(
                                        message = "Annuncio nascosto",
                                        actionLabel = "Annulla",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (r == SnackbarResult.ActionPerformed) stato.annullaScarto(a)
                                }
                            }
                        )
                    }
                    if (stato.inCorso) item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp), Alignment.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    }
                    if (stato.esaurito) item {
                        Text(
                            "Fine dei risultati",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    if (filtriAperti) FoglioFiltri(stato) { filtriAperti = false }
}

/* ------------------------------------------------------------------ */

/*
 * v1.9.0 — una proposta inviata, nella stessa forma di un risultato di ricerca:
 * foto a sinistra, testo a destra, e la fonte in rosso "SUBITO" come in elenco.
 * Coerenza non per estetica: due liste che si leggono allo stesso modo si
 * scorrono senza doverci pensare.
 *
 * --- versione precedente (v1.8.0, 2026-08-31) ---
 * tessera verticale da 168 dp, dentro una LazyRow orizzontale.
 * --- fine versione precedente ---
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RigaOfferta(o: Offerte.Offerta, apriUrl: (String) -> Unit) {
    Card(
        onClick = { apriUrl(o.url) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp)) {
            AsyncImage(
                model = o.immagine,
                contentDescription = null,
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    o.titolo.ifBlank { "Annuncio " + o.id },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                /*
                 * v1.32.0 (2026-09-02) — NUOVO: quando il venditore ha
                 * rilanciato (o.controproposta > 0, vedi Offerte.kt —
                 * leggiConversazioneVinted riconosce ora anche i messaggi di
                 * tipo "offer_message", non solo la propria offerta), sotto
                 * "offerti X €" compare anche il rilancio, in blu per
                 * distinguerlo a colpo d'occhio dal rosso della propria
                 * offerta (che resta il colore dell'azione fatta DA me,
                 * mentre il rilancio e' quella fatta DALL'ALTRO). Prima la
                 * riga mostrava solo la MIA offerta, anche quando in realta'
                 * era gia' stata superata da un rilancio — la ragione per
                 * cui l'utente segnalava "annunci con un rilancio sono
                 * scomparsi": non erano scomparsi, era la cifra sbagliata (e
                 * a volte lo stato "attiva" sbagliato) a farli sembrare
                 * conclusi. Vedi Offerte.kt per la correzione vera, qui c'e'
                 * solo la vista.
                 *
                 * --- versione precedente (fino alla v1.31.0) ---
                 * Row {
                 *     if (o.prezzoAnnuncio > 0) Text(
                 *         Offerte.euro(o.prezzoAnnuncio),
                 *         style = MaterialTheme.typography.titleMedium,
                 *         fontWeight = FontWeight.SemiBold,
                 *         modifier = Modifier.alignByBaseline()
                 *     )
                 *     Text(
                 *         "  offerti " + Offerte.euro(o.centesimi),
                 *         style = MaterialTheme.typography.labelLarge,
                 *         fontWeight = FontWeight.Bold,
                 *         color = ROSSO_OFFERTA,
                 *         maxLines = 1,
                 *         modifier = Modifier.alignByBaseline()
                 *     )
                 * }
                 * --- fine versione precedente ---
                 */
                Row {
                    if (o.prezzoAnnuncio > 0) Text(
                        Offerte.euro(o.prezzoAnnuncio),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.alignByBaseline()
                    )
                    Text(
                        "  offerti " + Offerte.euro(o.centesimi),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = ROSSO_OFFERTA,
                        maxLines = 1,
                        modifier = Modifier.alignByBaseline()
                    )
                }
                if (o.controproposta > 0) Text(
                    "rilancio del venditore " + Offerte.euro(o.controproposta),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = TEAL_VINTED,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                /*
                 * v1.22.0 (2026-08-31) — CORREZIONE: la scritta diceva sempre
                 * "SUBITO", anche per un'offerta Vinted — restO di quando le
                 * offerte erano solo di Subito (v1.4.0), mai resa dinamica
                 * quando la v1.10.0 ha aggiunto Vinted. Non e' un errore di
                 * lettura dati (o.fonte era gia' giusto, "vinted" o "subito"):
                 * era solo il testo in schermo, scritto fisso, a ignorarlo.
                 *
                 * --- versione precedente (fino alla v1.21.0) ---
                 * Text(
                 *     "SUBITO",
                 *     style = MaterialTheme.typography.labelSmall,
                 *     fontWeight = FontWeight.Bold,
                 *     color = ROSSO_SUBITO
                 * )
                 * --- fine versione precedente ---
                 */
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        o.fonte.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (o.fonte == "vinted") TEAL_VINTED else ROSSO_SUBITO
                    )
                    if (o.etichetta.isNotBlank()) Text(
                        " \u00b7 " + o.etichetta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    dataTesto(o.quando),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/*
 * v1.34.0 (2026-09-02) — NUOVO: scorrere un annuncio da destra verso
 * sinistra lo nasconde, in aggiunta alla ✕ gia' presente — richiesto
 * dall'utente per entrambe le disposizioni (compatta ed estesa). Si usa
 * SwipeToDismissBox di Material3, che l'app gia' ha a disposizione (stesso
 * compose-bom della schermata, nessuna libreria in piu'): un solo
 * involucro attorno alla Card copre entrambe le disposizioni, perche' la
 * scelta compatta/estesa e' DENTRO la Card, non attorno.
 *
 * Solo la direzione destra->sinistra e' abilitata
 * (enableDismissFromStartToEnd = false): uno scorrimento nell'altro verso
 * non fa nulla, com'e' stato chiesto. Il fondo che compare durante lo
 * scorrimento (backgroundContent) e' il "pulsante di avviso" richiesto:
 * un rettangolo colorato con icona e scritta "Nascondi l'annuncio", visibile
 * SOLO quando la direzione del gesto e' quella giusta (dismissDirection),
 * cosi' non lampeggia se lo scorrimento non supera la soglia o va nel verso
 * sbagliato. "confirmValueChange" e' il punto giusto per l'azione vera:
 * scatta quando lo scorrimento ha superato la soglia (non prima, non per un
 * tocco qualunque), e chiama la STESSA "nascondi" gia' passata alla ✕ — un
 * solo punto che nasconde davvero l'annuncio e offre "Annulla" nella
 * snackbar (vedi SchermataRicerca piu' sopra, dove "nascondi" viene
 * costruita), non due percorsi diversi da tenere allineati.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RigaAnnuncio(
    a: Annuncio,
    compatta: Boolean,
    coda: String,
    offerta: String?,
    // v1.31.0 — "contattato" (v1.28.0) tolto: vedi Descrizione() piu' sotto.
    /*
     * v1.40.0 — il testo dell'annuncio arriva da fuori invece che da
     * "a.descrizione": su Vinted non nasce con l'annuncio, si chiede a parte
     * (DescrizioniVinted.kt) e compare quando e' pronto. "chiediDescrizione"
     * e' la richiesta: la fa questa riga, mentre si disegna, e solo in vista
     * estesa — cosi' si chiede il testo dei soli annunci che si stanno
     * davvero guardando. Su Subito non parte nessuna richiesta: il testo c'e'
     * gia' nei risultati, e chiediDescrizione() se ne accorge da sola.
     */
    descrizione: String,
    chiediDescrizione: () -> Unit,
    preferito: Boolean,
    cambiaPreferito: () -> Unit,
    apri: () -> Unit,
    nascondi: () -> Unit
) {
    val statoScorrimento = rememberSwipeToDismissBoxState(
        confirmValueChange = { valore ->
            if (valore == SwipeToDismissBoxValue.EndToStart) nascondi()
            true   // sempre vero: anche tornare a "Settled" (scorrimento non completato) e' un cambio valido
        }
    )
    SwipeToDismissBox(
        state = statoScorrimento,
        enableDismissFromStartToEnd = false,   // solo destra -> sinistra, come chiesto
        enableDismissFromEndToStart = true,
        backgroundContent = {
            if (statoScorrimento.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ROSSO_CUORE)
                        .padding(horizontal = 22.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Nascondi l'annuncio",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        // v1.34.0 — stessa icona della ✕ gia' in uso (Icons.Filled.Close):
                        // "VisibilityOff" sarebbe stata piu' chiara ma non e' fra le icone
                        // "core" gia' incluse (androidx.compose.material:material-icons-core,
                        // vedi build.gradle.kts) — aggiungere l'intero pacchetto "extended"
                        // solo per un'icona non vale il peso in piu' sull'APK.
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
    ) {
    Card(
        onClick = apri,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        if (compatta) {
            // Foto piccola a sinistra, testo a destra: piu' annunci per schermata.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                AsyncImage(
                    model = a.immagine,
                    contentDescription = null,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 4.dp)
                ) { Descrizione(a, coda, offerta) }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Cuore(preferito, cambiaPreferito)
                    IconButton(onClick = nascondi, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Close, "Nascondi questo annuncio",
                            Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Foto grande in alto: si guarda la merce invece di leggere l'elenco.
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth()) {
                    /*
                     * v1.32.0 (2026-09-02) — CORREZIONE: nella vista estesa si
                     * vedeva sempre e solo la PRIMA foto, senza modo di scorrere
                     * le altre — segnalato dall'utente ("non e' possibile
                     * scorrere le foto"). Annuncio.immagini (nuovo, vedi
                     * Annuncio.kt e Fonti.kt) porta ora l'elenco completo, non
                     * solo la prima; quando ne ha piu' di una si mostra un
                     * HorizontalPager invece del singolo AsyncImage, con i
                     * pallini in basso come indicatore di pagina. Con zero o
                     * una sola foto (o se l'elenco non arriva, vedi il
                     * commento su Annuncio.immagini per Vinted) si ripiega sul
                     * comportamento di sempre: un'unica immagine, senza pager.
                     *
                     * --- versione precedente (fino alla v1.31.0) ---
                     * AsyncImage(
                     *     model = a.immagineGrande ?: a.immagine,
                     *     contentDescription = null,
                     *     modifier = Modifier
                     *         .fillMaxWidth()
                     *         .height(210.dp)
                     *         .background(MaterialTheme.colorScheme.surfaceVariant),
                     *     contentScale = ContentScale.Crop
                     * )
                     * --- fine versione precedente ---
                     */
                    val foto = a.immagini.ifEmpty { listOfNotNull(a.immagineGrande ?: a.immagine) }
                    if (foto.size > 1) {
                        val statoPager = rememberPagerState(pageCount = { foto.size })
                        HorizontalPager(
                            state = statoPager,
                            modifier = Modifier.fillMaxWidth().height(210.dp)
                        ) { pagina ->
                            AsyncImage(
                                model = foto[pagina],
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Row(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            repeat(foto.size) { i ->
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (i == statoPager.currentPage) Color.White
                                            else Color(0x80FFFFFF)
                                        )
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model = foto.firstOrNull(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(210.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                    }
                    /*
                     * v1.34.0 (2026-09-02) — CORREZIONE: i due pulsanti (cuore
                     * e ✕) si vedevano sovrapposti, segnalato dall'utente.
                     * Causa: IconButton di Material3 impone un'area minima di
                     * tocco di 48dp anche quando gli si chiede una dimensione
                     * piu' piccola (qui 34dp) — l'area toccabile/dipinta
                     * risultava piu' larga della "fetta" di Row riservata dal
                     * layout in base ai 34dp dichiarati, e i due cerchi
                     * finivano per invadersi a vicenda. Sostituiti con un
                     * semplice Box (clip circolare + sfondo + clickable): la
                     * dimensione dichiarata (34dp) e' anche quella vera,
                     * nessuna area invisibile che sconfina nel vicino.
                     *
                     * --- versione precedente (fino alla v1.33.0) ---
                     * Row(
                     *     Modifier.align(Alignment.TopEnd).padding(6.dp),
                     *     horizontalArrangement = Arrangement.spacedBy(6.dp)
                     * ) {
                     *     IconButton(
                     *         onClick = cambiaPreferito,
                     *         modifier = Modifier
                     *             .size(34.dp)
                     *             .background(Color(0x8C141414), RoundedCornerShape(17.dp))
                     *     ) {
                     *         Icon(
                     *             if (preferito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                     *             if (preferito) "Togli dai preferiti" else "Aggiungi ai preferiti",
                     *             Modifier.size(18.dp),
                     *             tint = if (preferito) ROSSO_CUORE else Color.White
                     *         )
                     *     }
                     *     IconButton(
                     *         onClick = nascondi,
                     *         modifier = Modifier
                     *             .size(34.dp)
                     *             .background(Color(0x8C141414), RoundedCornerShape(17.dp))
                     *     ) {
                     *         Icon(
                     *             Icons.Filled.Close, "Nascondi questo annuncio",
                     *             Modifier.size(18.dp),
                     *             tint = Color.White
                     *         )
                     *     }
                     * }
                     * --- fine versione precedente ---
                     */
                    // Sopra la foto serve un fondo proprio, altrimenti la ✕
                    // sparisce sulle immagini chiare.
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x8C141414))
                                .clickable(onClick = cambiaPreferito),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (preferito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                if (preferito) "Togli dai preferiti" else "Aggiungi ai preferiti",
                                Modifier.size(18.dp),
                                tint = if (preferito) ROSSO_CUORE else Color.White
                            )
                        }
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0x8C141414))
                                .clickable(onClick = nascondi),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Close, "Nascondi questo annuncio",
                                Modifier.size(18.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
                /*
                 * v1.7.0 — sotto la foto grande lo spazio a destra era vuoto.
                 * Ci va il testo dell'annuncio: e' l'unica cosa che in elenco
                 * manca davvero, ed e' cio' che fa decidere se aprire o no.
                 * Dove non c'e' (Vinted non lo espone nel catalogo) la colonna
                 * non compare e i dati si prendono tutta la larghezza.
                 *
                 * --- versione precedente (v1.0.5, 2026-08-30) ---
                 * Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) { Descrizione(a, coda, offerta) }
                 * --- fine versione precedente ---
                 */
                /*
                 * v1.12.0 (2026-08-31) — il box del testo era piu' stretto di quello
                 * dei dati (titolo/prezzo/offerta): 52% a sinistra, 48% a destra, ma
                 * a sinistra c'e' poco (due righe corte), a destra un paragrafo che
                 * con quella larghezza andava a capo troppo presto. Si scambiano le
                 * proporzioni: i dati bastano stretti, il testo ora ha piu' spazio.
                 *
                 * --- versione precedente (v1.7.0, 2026-08-31) ---
                 * Column(Modifier.weight(if (a.descrizione.isBlank()) 1f else 0.52f)) {
                 *     Descrizione(a, coda, offerta)
                 * }
                 * if (a.descrizione.isNotBlank()) Text(
                 *     a.descrizione, …, modifier = Modifier.weight(0.48f).padding(start = 12.dp)
                 * )
                 * --- fine versione precedente ---
                 */
                /*
                 * v1.40.0 (2026-09-05) — la richiesta del testo su Vinted parte
                 * DA QUI, e solo da qui: siamo dentro il ramo della vista
                 * estesa, cioe' l'unico posto in cui quel testo si vede, e
                 * dentro una riga della LazyColumn, che entra in composizione
                 * quando l'annuncio arriva davvero sotto gli occhi. Ne segue
                 * da sola la cosa giusta: si chiede il testo delle quattro o
                 * cinque righe visibili, poi di altre poche mentre si scorre —
                 * non di trenta per pagina, di cui se ne leggono cinque (il
                 * perche' per esteso e' in cima a DescrizioniVinted.kt).
                 *
                 * La chiave e' l'annuncio: la LazyColumn riusa le righe fra
                 * un annuncio e l'altro mentre si scorre, e senza chiave la
                 * richiesta non ripartirebbe per il nuovo occupante della
                 * riga riciclata.
                 *
                 * --- versione precedente (fino alla v1.39.0) ---
                 * (nessuna: su Vinted la colonna del testo restava vuota,
                 *  vedi il commento sotto — "Dove non c'e' … la colonna non
                 *  compare")
                 * --- fine versione precedente ---
                 */
                LaunchedEffect(a.fonte, a.id) { chiediDescrizione() }

                /*
                 * v1.40.0 — "a.descrizione" -> "descrizione" (il parametro):
                 * su Subito sono la stessa cosa, su Vinted il campo
                 * dell'annuncio e' sempre vuoto e il testo vero sta nel
                 * parametro. Il resto della disposizione non cambia di una
                 * riga: quando il testo non c'e' — Vinted prima della
                 * risposta, o annuncio senza descrizione — la colonna non
                 * compare e i dati si prendono tutta la larghezza, esattamente
                 * come dalla v1.7.0.
                 *
                 * --- versione precedente (fino alla v1.39.0) ---
                 * Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                 *     Column(Modifier.weight(if (a.descrizione.isBlank()) 1f else 0.38f)) {
                 *         Descrizione(a, coda, offerta)
                 *     }
                 *     if (a.descrizione.isNotBlank()) Text(
                 *         a.descrizione,
                 *         …
                 *     )
                 * }
                 * --- fine versione precedente ---
                 */
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Column(Modifier.weight(if (descrizione.isBlank()) 1f else 0.38f)) {
                        Descrizione(a, coda, offerta)
                    }
                    if (descrizione.isNotBlank()) Text(
                        descrizione,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(0.62f)
                            .padding(start = 12.dp)
                    )
                }
            }
        }
    }
    }   // chiude il content di SwipeToDismissBox (vedi il commento sopra la funzione)
}

/*
 * v1.2.0 — il cuore. Lo stato viene dalla piattaforma, non da noi: pieno se
 * l'annuncio e' fra i preferiti la', vuoto altrimenti. Al tocco cambia subito
 * e la richiesta parte dietro; se non passa, torna com'era.
 */
@Composable
private fun Cuore(preferito: Boolean, cambia: () -> Unit) {
    IconButton(onClick = cambia, modifier = Modifier.size(36.dp)) {
        Icon(
            if (preferito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            if (preferito) "Togli dai preferiti" else "Aggiungi ai preferiti",
            Modifier.size(20.dp),
            tint = if (preferito) ROSSO_CUORE else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/*
 * v1.36.0 (2026-09-04) — nuovo: l'icona compatta/estesa sotto il campo di
 * ricerca (vedi il commento sopra la Row che la usa). material-icons-core
 * non ha un'icona "griglia"/"elenco" pronta (solo Icons.Filled.List esiste,
 * niente ViewModule/GridView/Apps: verificato spacchettando l'AAR, stesso
 * controllo gia' fatto per VisibilityOff alla v1.34.0) e material-icons-extended
 * resta fuori per lo stesso motivo di allora — un pacchetto intero per
 * un'icona sola. Disegnata a mano con due Box, stessa scelta gia' fatta per
 * i pulsanti preferiti/nascondi della vista estesa.
 */
@Composable
private fun IconVistaLista(compatta: Boolean, tint: Color) {
    Column(
        Modifier.size(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (compatta) {
            // tre barre orizzontali: una riga di testo per annuncio.
            repeat(3) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(tint)
                )
            }
        } else {
            // quattro riquadri: una scheda (foto grande) per annuncio.
            repeat(2) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    repeat(2) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(tint)
                        )
                    }
                }
            }
        }
    }
}

/*
 * v1.36.0 (2026-09-04) — nuovo: le etichette delle ricerche recenti, con
 * tocco lungo per cancellarle (vedi il commento sopra la Row che le usa). Un
 * Surface con combinedClickable invece del SuggestionChip di Material3: quel
 * componente offre solo onClick, non onLongClick, e annidare un secondo
 * gesto sopra il suo si scontra con la gestione del tocco che ha gia' dentro
 * (chi rileva per primo il rilascio del dito "vince", e non e' il genitore).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChipRicercaRecente(testo: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Text(
            testo,
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/*
 * v1.31.0 (2026-09-01) — CORREZIONE: l'icona "hai scritto al venditore"
 * della v1.28.0 mostrava un'icona per QUALUNQUE proposta inviata, offerta
 * compresa — l'utente ha chiarito che deve valere il contrario: solo per un
 * messaggio di chat SENZA offerta ("se e' stata fatta un'offerta abbiamo
 * gia' il testo con l'importo, non serve anche un'icona").
 *
 * Non e' un cambio di un carattere: serve un dato che oggi non abbiamo per
 * NESSUNA delle due fonti. "contattato" leggeva Offerte.perAnnuncio(a), che
 * esiste SOLO per conversazioni con un'offerta di prezzo dentro (Subito:
 * transazione P2P; Vinted: conversazione con un offer_request_message) —
 * una chat senza offerta oggi non lascia traccia da nessuna parte nel
 * codice: leggiConversazioneVinted() (Offerte.kt) scarta in silenzio ogni
 * conversazione priva di "transaction", e su Subito non esiste ancora
 * nessuna lettura dell'elenco chat (solo il conteggio non letti e le
 * transazioni P2P, viste nella sonda della v1.30.0).
 *
 * Finche' non arriva una sonda che mostri la forma di QUELLI (una
 * conversazione Vinted senza offerta, e l'endpoint Subito che elenca le
 * chat), l'icona resta tolta invece di indovinare i nomi dei campi — vedi
 * la nota su questo in StatoRicerca.kt, dove viveva contattato(a).
 * L'icona busta chiesta per il rimpiazzo (invece della "Send" della
 * v1.28.0) e' gia' decisa per quando i dati arriveranno: Icons.Filled.Email,
 * disponibile in material-icons-core come Send.
 *
 * --- versione precedente (v1.28.0, con "contattato" — offerte comprese) ---
 * private fun Descrizione(a: Annuncio, coda: String, offerta: String?, contattato: Boolean) {
 *     … stesso corpo, piu' in fondo:
 *     if (contattato) Icon(
 *         Icons.Filled.Send, "Hai scritto al venditore",
 *         Modifier.padding(start = 4.dp).size(13.dp),
 *         tint = MaterialTheme.colorScheme.onSurfaceVariant
 *     )
 * --- fine versione precedente ---
 *
 * --- versione precedente (fino alla v1.27.0, senza icona) ---
 * private fun Descrizione(a: Annuncio, coda: String, offerta: String?) {
 * --- fine versione precedente ---
 */
/** Il testo dell'annuncio: identico nelle due disposizioni. */
@Composable
private fun Descrizione(a: Annuncio, coda: String, offerta: String?) {
    Text(
        a.titolo,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 2, overflow = TextOverflow.Ellipsis
    )
    Spacer(Modifier.height(2.dp))
    /*
     * v1.4.0 — prezzo chiesto e prezzo offerto sulla stessa riga.
     * Stanno accanto perche' e' il confronto fra i due che serve: «300 € /
     * offerti 170 €» si legge in un colpo, su due righe no.
     *
     * --- versione precedente (v0.5.2, 2026-08-30) ---
     * Text(a.prezzoTesto, style = titleMedium, fontWeight = SemiBold)
     * --- fine versione precedente ---
     */
    /*
     * v1.7.0 — i due prezzi sulla stessa LINEA DI BASE.
     * Alignment.Bottom allinea il fondo delle due caselle di testo, non quello
     * delle lettere: con due corpi diversi il risultato e' il testo rosso che
     * "galleggia" piu' in basso. alignByBaseline allinea cio' che l'occhio
     * usa davvero per giudicare, la riga su cui poggiano le cifre.
     *
     * --- versione precedente (v1.4.0, 2026-08-31) ---
     * Row(verticalAlignment = Alignment.Bottom) { … }
     * --- fine versione precedente ---
     */
    Row {
        Text(
            a.prezzoTesto,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.alignByBaseline()
        )
        if (offerta != null) Text(
            "  $offerta",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = ROSSO_OFFERTA,
            maxLines = 1,
            modifier = Modifier.alignByBaseline()
        )
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (a.fonte == "subito") "SUBITO" else "VINTED",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (a.fonte == "subito") ROSSO_SUBITO else TEAL_VINTED
        )
        /*
         * v1.0.4 (2026-08-30) — accanto alla fonte va solo il luogo.
         * Marca e taglia restano nel modello (Annuncio.dettaglio) ma non si
         * mostrano: erano rumore, e su Subito non hanno un corrispondente,
         * quindi le due righe non si leggevano allo stesso modo.
         *
         * --- versione precedente (v0.5.2, 2026-08-30) ---
         * val coda = listOf(a.luogo, a.dettaglio)
         *     .filter { it.isNotBlank() }.joinToString(" · ")
         * --- fine versione precedente ---
         */
        /*
         * v1.1.0 — dove c'e' il luogo si mostra il luogo; dove non c'e' — su
         * Vinted non esiste proprio — si mostra la condizione, che sull'usato
         * e' l'altra cosa che si guarda per prima. Mai entrambe: la riga deve
         * restare corta e leggersi allo stesso modo sulle due fonti.
         */
        /*
         * v1.2.0 (2026-08-31) — la coda arriva gia' decisa da StatoRicerca.coda():
         * su Vinted il luogo esiste, ma sul profilo del venditore, e arriva
         * dopo la riga; finche' non c'e' si mostra la condizione.
         *
         * --- versione precedente (v1.1.0, 2026-08-30) ---
         * val coda = a.luogo.ifBlank { a.dettaglio }
         * --- fine versione precedente ---
         */
        if (coda.isNotBlank()) Text(
            " · $coda",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        // v1.31.0 — l'icona qui era la v1.28.0 ("hai scritto al venditore",
        // per QUALUNQUE proposta): tolta, vedi il commento sopra Descrizione().
    }
    /*
     * v1.58.0 (2026-09-24) — "≈" davanti quando la data e' stimata
     * (Annuncio.dataStimata: Vinted, vedi StimaDataVinted.kt).
     *
     * --- versione precedente (fino alla v1.57.0) ---
     * Text(
     *     dataTesto(a.dataMillis),
     *     ...
     * --- fine versione precedente ---
     */
    Text(
        (if (a.dataStimata && a.dataMillis > 0L) "≈ " else "") + dataTesto(a.dataMillis),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary
    )
    if (a.venduto) Text(
        "VENDUTO",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun Scheletri() {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        repeat(6) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(104.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    listOf(0.9f, 0.4f, 0.65f).forEach { larghezza ->
                        Box(
                            Modifier
                                .fillMaxWidth(larghezza)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Vuoto(
    titolo: String,
    testo: String,
    azione: String? = null,
    onAzione: () -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(titolo, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            testo,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (azione != null) {
            Spacer(Modifier.height(14.dp))
            FilledTonalButton(onClick = onAzione) { Text(azione) }
        }
    }
}

/* ------------------------------------------------------------------ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoglioFiltri(stato: StatoRicerca, chiudi: () -> Unit) {
    ModalBottomSheet(onDismissRequest = chiudi) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Filtri",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { stato.azzeraFiltri() }) { Text("Azzera") }
            }

            Titoletto("Fonti")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(stato.usaSubito, { stato.usaSubito = !stato.usaSubito }, { Text("Subito") })
                FilterChip(stato.usaVinted, { stato.usaVinted = !stato.usaVinted }, { Text("Vinted") })
            }

            Titoletto("Ordine")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Più recenti", "Prezzo ↑", "Prezzo ↓").forEachIndexed { i, t ->
                    FilterChip(stato.ordine == i, { stato.ordine = i }, { Text(t) })
                }
            }

            Titoletto("Età dell'annuncio")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Qualsiasi", "Più vecchi di", "Più recenti di").forEachIndexed { i, t ->
                    FilterChip(stato.etaModo == i, { stato.etaModo = i }, { Text(t) })
                }
            }
            if (stato.etaModo != 0) {
                OutlinedTextField(
                    value = stato.giorni,
                    onValueChange = { nuovo -> stato.giorni = nuovo.filter { it.isDigit() } },
                    label = { Text("giorni") },
                    singleLine = true,
                    modifier = Modifier.width(150.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Titoletto("Prezzo")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = stato.prezzoMin,
                    onValueChange = { stato.prezzoMin = it },
                    label = { Text("€ min") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = stato.prezzoMax,
                    onValueChange = { stato.prezzoMax = it },
                    label = { Text("€ max") }, singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mostra anche i venduti", Modifier.weight(1f))
                Switch(stato.mostraVenduti, { stato.mostraVenduti = it })
            }

            /*
             * v1.29.0 (2026-09-01) — NUOVO: "un toggle per scartare gli
             * annunci il cui venditore ha 0 recensioni". Alla nascita
             * valeva solo su Vinted (Subito non aveva ancora un endpoint
             * confermato) e il sottotitolo lo diceva.
             *
             * v1.30.0 — trovato anche l'endpoint Subito (RecensioniSubito.kt):
             * il sottotitolo che limitava il filtro a Vinted non serve più.
             *
             * --- versione precedente (fino alla v1.29.0) ---
             * Row(verticalAlignment = Alignment.CenterVertically) {
             *     Column(Modifier.weight(1f)) {
             *         Text("Nascondi venditori senza recensioni")
             *         Text(
             *             "Per ora solo su Vinted: su Subito il dato non è ancora letto",
             *             style = MaterialTheme.typography.bodySmall,
             *             color = MaterialTheme.colorScheme.onSurfaceVariant
             *         )
             *     }
             *     Switch(stato.scartaSenzaRecensioni, { stato.scartaSenzaRecensioni = it })
             * }
             * --- fine versione precedente ---
             */
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Nascondi venditori senza recensioni", Modifier.weight(1f))
                Switch(stato.scartaSenzaRecensioni, { stato.scartaSenzaRecensioni = it })
            }

            // v1.1.0 — il confronto con l'esecuzione precedente della stessa ricerca
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Solo i nuovi")
                    Text(
                        if (stato.haPrecedente)
                            "Nascondi ciò che c'era già l'ultima volta che hai cercato questo"
                        else
                            "Prima volta che cerchi questo: il confronto parte dalla prossima",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(stato.soloNuovi, { stato.soloNuovi = it })
            }

            Text(
                "I filtri agiscono su ciò che è già stato scaricato: scorri per allargarne l'insieme.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(onClick = chiudi, modifier = Modifier.fillMaxWidth()) { Text("Fatto") }
        }
    }
}

@Composable
private fun Titoletto(t: String) = Text(
    t,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

/* ------------------------------------------------------------------ */

private fun etichetteFiltri(stato: StatoRicerca): List<Pair<String, () -> Unit>> = buildList {
    if (stato.ordine != 0) add(
        (if (stato.ordine == 1) "Prezzo ↑" else "Prezzo ↓") to { stato.ordine = 0 }
    )
    if (!stato.usaSubito) add("Senza Subito" to { stato.usaSubito = true })
    if (!stato.usaVinted) add("Senza Vinted" to { stato.usaVinted = true })
    if (stato.etaModo != 0 && (stato.giorni.toLongOrNull() ?: 0L) > 0L) add(
        (if (stato.etaModo == 1) "Oltre ${stato.giorni} gg" else "Ultimi ${stato.giorni} gg") to
                { stato.etaModo = 0 }
    )
    if (stato.prezzoMin.isNotBlank() || stato.prezzoMax.isNotBlank()) add(
        ("€ " + stato.prezzoMin.ifBlank { "0" } + "–" + stato.prezzoMax.ifBlank { "∞" }) to
                { stato.prezzoMin = ""; stato.prezzoMax = "" }
    )
    if (stato.mostraVenduti) add("Con venduti" to { stato.mostraVenduti = false })
    if (stato.soloNuovi) add("Solo nuovi" to { stato.soloNuovi = false })
    // v1.29.0
    if (stato.scartaSenzaRecensioni) add("Senza 0 recensioni" to { stato.scartaSenzaRecensioni = false })
}

private fun riepilogo(stato: StatoRicerca): String {
    val fonti = stato.conteggi.entries.sortedBy { it.key }
        .joinToString(" · ") { "${it.key} ${it.value}" }
    val esclusi = stato.esclusi
    return "${stato.risultati.size} risultati" +
            (if (fonti.isBlank()) "" else " ($fonti)") +
            (if (esclusi > 0) " · $esclusi esclusi" else "")
}

private fun dataTesto(t: Long): String {
    if (t <= 0L) return "data non disponibile"
    val g = ((System.currentTimeMillis() - t) / 86_400_000L).toInt()
    val d = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ITALY)
        .format(java.util.Date(t))
    val quando = when {
        g <= 0 -> "oggi"
        g == 1 -> "ieri"
        else -> "$g giorni fa"
    }
    return "$d · $quando"
}
