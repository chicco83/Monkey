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

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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

@OptIn(ExperimentalMaterial3Api::class)
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
    compatta: Boolean = true
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
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
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
                    stato.recenti.forEach { r ->
                        SuggestionChip(
                            onClick = {
                                stato.query = r
                                tastiera?.hide(); fuoco.clearFocus(); stato.cerca()
                            },
                            label = { Text(r) }
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
                            Text(
                                "Le tue ultime proposte",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(offerte, key = { it.id }) { o -> RigaOfferta(o, apriUrl) }
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
                        RigaAnnuncio(
                            a,
                            compatta = compatta,
                            // v1.2.0 — su Vinted il luogo arriva dal profilo del
                            // venditore, dopo i risultati: lo legge lo stato.
                            coda = stato.coda(a),
                            // v1.4.0 — «offerti xxx €»: null se su questo annuncio
                            // non hai una proposta aperta
                            offerta = stato.offerta(a),
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
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SUBITO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ROSSO_SUBITO
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RigaAnnuncio(
    a: Annuncio,
    compatta: Boolean,
    coda: String,
    offerta: String?,
    preferito: Boolean,
    cambiaPreferito: () -> Unit,
    apri: () -> Unit,
    nascondi: () -> Unit
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
                    AsyncImage(
                        /*
                         * v1.7.0 — a tutta larghezza serve la resa grande.
                         * Qui arrivava la foto d'elenco: 310 px su Vinted,
                         * stirati su mille e passa di schermo. Sgranata non per
                         * colpa della foto ma della resa scelta.
                         *
                         * --- versione precedente (v1.0.5, 2026-08-30) ---
                         * model = a.immagine,
                         * --- fine versione precedente ---
                         */
                        model = a.immagineGrande ?: a.immagine,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    // Sopra la foto serve un fondo proprio, altrimenti la ✕
                    // sparisce sulle immagini chiare.
                    Row(
                        Modifier.align(Alignment.TopEnd).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = cambiaPreferito,
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0x8C141414), RoundedCornerShape(17.dp))
                        ) {
                            Icon(
                                if (preferito) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                if (preferito) "Togli dai preferiti" else "Aggiungi ai preferiti",
                                Modifier.size(18.dp),
                                tint = if (preferito) ROSSO_CUORE else Color.White
                            )
                        }
                        IconButton(
                            onClick = nascondi,
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0x8C141414), RoundedCornerShape(17.dp))
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
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Column(Modifier.weight(if (a.descrizione.isBlank()) 1f else 0.38f)) {
                        Descrizione(a, coda, offerta)
                    }
                    if (a.descrizione.isNotBlank()) Text(
                        a.descrizione,
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
    }
    Text(
        dataTesto(a.dataMillis),
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
