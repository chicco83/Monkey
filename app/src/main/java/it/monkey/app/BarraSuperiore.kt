package it.monkey.app

/*
 * Subito+ — barra dei comandi
 * v0.5.1 (2026-08-30)
 *
 * Sostituisce la fila di otto pulsanti che occupava la parte alta dello schermo.
 * In vista restano il nome, l'interruttore fra ricerca e navigazione, e il menu:
 * tutto il resto sono comandi che si usano di rado o mai, e stavano li' solo
 * perche' costruire un menu a mano sarebbe costato piu' di una fila di pulsanti.
 *
 * Primo pezzo di interfaccia in Compose. Convive con le viste tradizionali del
 * resto della schermata: e' cosi' che il rifacimento procede un pezzo per volta.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
/*
 * v1.46.0 — "Close" (Icons.Filled.Close) e "Card"/"CardDefaults" tolti: gli
 * unici usi erano nel banner di guida al login, ora un dialogo modale (vedi
 * il commento su StatoBarra.mostraGuida, piu' sotto).
 */
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Tutto lo stato che la barra mostra, in un solo oggetto. */
/*
 * v1.36.0 (2026-09-04) — "vistaCompatta" tolto: serviva solo alla voce
 * "Vista compatta" del menu ⋮, tolta anche lei (vedi il commento su Menu()
 * piu' sotto) — l'interruttore vive ora in SchermataRicerca.kt, sotto il
 * campo di ricerca, che gia' riceveva "compatta" per conto suo.
 *
 * --- versione precedente (fino alla v1.35.0) ---
 * data class StatoBarra(
 *     val modoRicerca: Boolean,
 *     val bloccoAttivo: Boolean,
 *     val vistaCompatta: Boolean,
 *     val modoSonda: Boolean,
 *     ...
 * --- fine versione precedente ---
 */
data class StatoBarra(
    val modoRicerca: Boolean,
    val bloccoAttivo: Boolean,
    val modoSonda: Boolean,
    val fonteVinted: Boolean,
    /** v0.5.1 — interruttore generale degli script che iniettiamo nelle pagine. */
    val scriptPagina: Boolean,
    val nascosti: Int,
    /** v0.6.0 — quante ricerche salvate e quante novità non ancora guardate. */
    val ricerche: Int,
    val nuovi: Int,
    /** v0.7.0 — titolo della pagina aperta, mostrato al posto di "Navigazione". */
    val titoloPagina: String,
    val riga: String,
    /** null = nessun caricamento; -1 = attesa senza percentuale; 0..100 = avanzamento */
    val avanzamento: Int?
    /*
     * v1.46.0 — "mostraGuida" tolto: il banner che pilotava (piu' sotto) e'
     * diventato un dialogo modale (DialogoGuidaLogin.kt), mostrato e chiuso
     * direttamente da MainActivity insieme a DialogoNovita/DialogoAggiornamento
     * — non serve piu' passare lo stato fin qui dentro la barra.
     *
     * --- versione precedente (fino alla v1.45.0) ---
     * val avanzamento: Int?,
     * /** v1.15.0 — il banner "tocca ⋮ e poi Sito per accedere", solo al primo avvio. */
     * val mostraGuida: Boolean
     * --- fine versione precedente ---
     */
)

/*
 * v1.36.0 (2026-09-04) — "cambiaVista" tolto: era legato alla sola voce
 * "Vista compatta" del menu ⋮, tolta anche lei — vedi il commento su
 * StatoBarra qui sopra e su Menu() piu' sotto. La stessa azione (la logica
 * non e' cambiata, solo da dove si chiama) e' ora un parametro di
 * SchermataRicerca (vedi MainActivity.cambiaVista()).
 */
/*
 * v1.37.0 (2026-09-04) — "cambiaModo" tolto: era l'icona lente "di cui non
 * capisco il funzionamento" segnalata dall'utente (passava fra ricerca e
 * navigazione del sito vero) — vedi il commento sulle actions() piu' sotto
 * per le due vie che restano per fare la stessa cosa.
 *
 * --- versione precedente (fino alla v1.36.0) ---
 * class AzioniBarra(
 *     val cambiaModo: () -> Unit,
 *     val cambiaBlocco: () -> Unit,
 *     ...
 * --- fine versione precedente ---
 */
/** Le azioni che la barra puo' scatenare. */
class AzioniBarra(
    val cambiaBlocco: () -> Unit,
    val cambiaSonda: () -> Unit,
    val cambiaScriptPagina: () -> Unit,
    val cambiaFonte: () -> Unit,
    val apriNascosti: () -> Unit,
    /** v1.9.0 — preferiti raggiungibili da ovunque, non solo dalla home. */
    val apriPreferiti: () -> Unit,
    val apriRicerche: () -> Unit,
    val caricaRegole: () -> Unit,
    val salvaBackup: () -> Unit,
    val analizza: () -> Unit,
    /** v1.3.0 — batteria di sole GET per trovare l'elenco delle offerte fatte. */
    val sondaOfferte: () -> Unit,
    val esporta: () -> Unit,
    val azzera: () -> Unit,
    // v0.7.0 — comandi della navigazione web
    val ricarica: () -> Unit,
    val condividi: () -> Unit,
    val apriNelBrowser: () -> Unit,
    /*
     * v1.46.0 — "chiudiGuida" tolto insieme al banner (vedi StatoBarra,
     * sopra): il dialogo che l'ha sostituito si chiude da solo, non serve
     * piu' un'azione dedicata qui.
     *
     * --- versione precedente (fino alla v1.45.0) ---
     * /** v1.15.0 — chiude il banner di guida al login, per sempre. */
     * val chiudiGuida: () -> Unit,
     * --- fine versione precedente ---
     */
    /*
     * v1.18.0 (2026-08-31) — nuovo: torna alla schermata iniziale (le ultime
     * proposte) da qualunque punto dell'app — una ricerca in corso, i
     * preferiti, o la navigazione dentro "Sito". Vedi il commento sull'icona
     * qui sotto e su MainActivity.vaiAllaHome().
     */
    val vaiAllaHome: () -> Unit,
    /** v1.24.0 — apre il pannello Opzioni (vedi DialogoOpzioni.kt). */
    val apriOpzioni: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperiore(stato: StatoBarra, azioni: AzioniBarra) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth()) {

            TopAppBar(
                /*
                 * v1.6.0 — la versione accanto al nome. Serve a sapere, guardando
                 * lo schermo, quale build si sta provando: con consegne cosi'
                 * ravvicinate "l'ho installata?" e' la domanda piu' frequente.
                 * Il numero viene da BuildConfig, cioe' da build.gradle.kts:
                 * una fonte sola, impossibile che diverga.
                 */
                /*
                 * v1.13.0 (2026-08-31) — il nome in barra segue il nuovo nome
                 * dell'app: "Subito+" era troppo vicino al marchio del sito.
                 *
                 * --- versione precedente (v0.7.0, 2026-08-30) ---
                 * stato.modoRicerca -> "Subito+"
                 * --- fine versione precedente ---
                 */
                title = {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            when {
                                stato.modoRicerca -> "Monkey"
                                stato.titoloPagina.isNotBlank() -> stato.titoloPagina
                                else -> "Navigazione"
                            },
                            style = if (stato.modoRicerca) MaterialTheme.typography.titleLarge
                                    else MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (stato.modoRicerca) Text(
                            "  " + BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                /*
                 * v1.9.0 — preferiti e ricerche salvate diventano due icone in
                 * barra. Erano due pulsanti nella schermata iniziale: si vedevano
                 * solo li', e sparivano appena si cercava qualcosa. In barra ci
                 * si arriva sempre, ed e' il posto dove si va a cercarli.
                 * Sul campanello resta il pallino delle novita' non guardate.
                 */
                actions = {
                    /*
                     * v1.18.0 — icona "home": "indietro" da una ricerca ora torna
                     * gia' da solo qui (vedi MainActivity, il gestore di "indietro"),
                     * ma un tocco diretto e' piu' rapido di un percorso a ritroso —
                     * specie da dentro "Sito", dove "indietro" scorre prima tutta
                     * la cronologia della pagina prima di arrivare in app.
                     */
                    IconButton(onClick = azioni.vaiAllaHome) {
                        Icon(
                            Icons.Filled.Home, "Le tue ultime proposte",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = azioni.apriPreferiti) {
                        Icon(
                            Icons.Filled.FavoriteBorder, "Preferiti",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    /*
                     * v1.37.0 (2026-09-04) — due correzioni, entrambe segnalate
                     * dall'utente:
                     *
                     * 1. "non ricordavo che il campanello richiamasse le
                     *    ricerche [salvate]": la sola campanella non bastava a
                     *    farlo pensare. Ora una piccola lente sovrapposta in
                     *    basso a destra rinforza l'idea — vedi IconaRicercheSalvate
                     *    piu' sotto, dove sta anche il pallino delle novita'
                     *    (invariato, solo spostato dentro la funzione).
                     * 2. "rimuovi l'icona successiva di una lente di cui non
                     *    capisco il funzionamento": era "cambiaModo", che
                     *    passava fra la ricerca e la navigazione del sito
                     *    vero. Tolta: si entra in navigazione dalla voce
                     *    "Sito: Subito/Vinted" del menu ⋮ (azioni.cambiaFonte),
                     *    e se ne esce con l'icona "home" qui sopra o con
                     *    "indietro" (che ci torna gia' da solo — vedi il
                     *    gestore in MainActivity) — due vie che restano,
                     *    quindi la funzione non sparisce, solo questa
                     *    scorciatoia poco chiara.
                     *
                     * --- versione precedente (fino alla v1.36.0) ---
                     * BadgedBox(badge = {
                     *     if (stato.nuovi > 0) Badge { Text("${stato.nuovi}") }
                     * }) {
                     *     IconButton(onClick = azioni.apriRicerche) {
                     *         Icon(
                     *             Icons.Filled.Notifications, "Ricerche salvate",
                     *             tint = MaterialTheme.colorScheme.onSurfaceVariant
                     *         )
                     *     }
                     * }
                     * IconButton(onClick = azioni.cambiaModo) {
                     *     Icon(
                     *         Icons.Filled.Search,
                     *         contentDescription = if (stato.modoRicerca) "Vai alla navigazione" else "Torna alla ricerca",
                     *         tint = if (stato.modoRicerca) MaterialTheme.colorScheme.primary
                     *                else MaterialTheme.colorScheme.onSurfaceVariant
                     *     )
                     * }
                     * --- fine versione precedente ---
                     */
                    IconaRicercheSalvate(stato.nuovi, azioni.apriRicerche)
                    if (!stato.modoRicerca) IconButton(onClick = azioni.ricarica) {
                        Icon(Icons.Filled.Refresh, "Ricarica la pagina")
                    }
                    Menu(stato, azioni)
                }
            )

            /*
             * v1.46.0 — il banner di guida al login (v1.15.0) e' diventato un
             * dialogo modale: vedi DialogoGuidaLogin.kt e il commento su
             * StatoBarra.mostraGuida, sopra, per il perche'.
             *
             * --- versione precedente (fino alla v1.45.0) ---
             * if (stato.mostraGuida) {
             *     Card(
             *         Modifier
             *             .fillMaxWidth()
             *             .padding(horizontal = 12.dp, vertical = 6.dp),
             *         colors = CardDefaults.cardColors(
             *             containerColor = MaterialTheme.colorScheme.primaryContainer
             *         )
             *     ) {
             *         Row(
             *             Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
             *             verticalAlignment = Alignment.CenterVertically
             *         ) {
             *             Text(
             *                 "Per accedere con il tuo account, tocca ⋮ qui sopra e poi «Sito» " +
             *                         "per aprire Subito o Vinted e fare il login.",
             *                 Modifier.weight(1f),
             *                 style = MaterialTheme.typography.bodySmall,
             *                 color = MaterialTheme.colorScheme.onPrimaryContainer
             *             )
             *             IconButton(onClick = azioni.chiudiGuida) {
             *                 Icon(
             *                     Icons.Filled.Close, "Chiudi il suggerimento",
             *                     tint = MaterialTheme.colorScheme.onPrimaryContainer
             *                 )
             *             }
             *         }
             *     }
             * }
             * --- fine versione precedente ---
             */

            // Avanzamento: sottile, subito sotto la barra, con lo stesso accento del tema.
            when {
                stato.avanzamento == null -> {}
                stato.avanzamento < 0 -> LinearProgressIndicator(Modifier.fillMaxWidth())
                else -> LinearProgressIndicator(
                    progress = { stato.avanzamento / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (stato.riga.isNotBlank()) {
                Text(
                    stato.riga,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
                )
            }

            HorizontalDivider(color = DividerDefaults.color.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun Menu(stato: StatoBarra, azioni: AzioniBarra) {
    var aperto by remember { mutableStateOf(false) }

    IconButton(onClick = { aperto = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = "Altri comandi")
    }

    /*
     * v1.26.0 (2026-09-01) — CORREZIONE: il menu era cresciuto a quattordici
     * righe. Restano solo le prime sei (quelle di uso frequente, o utili da
     * qualunque schermata); backup, import, sonda e diagnostica — di uso
     * raro — si raggiungono ora da "Opzioni…", in fondo. Vedi il commento
     * in cima a DialogoOpzioni.kt.
     *
     * --- versione precedente (fino alla v1.25.0) ---
     * DropdownMenu(expanded = aperto, onDismissRequest = { aperto = false }) {
     *
     *     voce(
     *         "Ricerche salvate (${stato.ricerche})" +
     *                 if (stato.nuovi > 0) " · ${stato.nuovi} nuovi" else "",
     *         null
     *     ) { aperto = false; azioni.apriRicerche() }
     *     voce("Annunci nascosti (${stato.nascosti})", null) { aperto = false; azioni.apriNascosti() }
     *     voce("Vista compatta", stato.vistaCompatta) { aperto = false; azioni.cambiaVista() }
     *
     *     HorizontalDivider()
     *
     *     if (!stato.modoRicerca) {
     *         voce("Condividi la pagina", null) { aperto = false; azioni.condividi() }
     *         voce("Apri nel browser", null) { aperto = false; azioni.apriNelBrowser() }
     *         HorizontalDivider()
     *     }
     *
     *     voce("Blocco pubblicità", stato.bloccoAttivo) { aperto = false; azioni.cambiaBlocco() }
     *     voce("Script nella pagina", stato.scriptPagina) { aperto = false; azioni.cambiaScriptPagina() }
     *     voce(if (stato.fonteVinted) "Sito: Vinted" else "Sito: Subito", null) { aperto = false; azioni.cambiaFonte() }
     *
     *     HorizontalDivider()
     *
     *     voce("Salva backup", null) { aperto = false; azioni.salvaBackup() }
     *     voce("Importa file…", null) { aperto = false; azioni.caricaRegole() }
     *
     *     HorizontalDivider()
     *
     *     voce("Modalità sonda", stato.modoSonda) { aperto = false; azioni.cambiaSonda() }
     *     voce("Analizza pagina", null) { aperto = false; azioni.analizza() }
     *     voce("Sonda offerte", null) { aperto = false; azioni.sondaOfferte() }
     *     voce("Esporta diagnostica", null) { aperto = false; azioni.esporta() }
     *     voce("Azzera contatori", null) { aperto = false; azioni.azzera() }
     *
     *     HorizontalDivider()
     *
     *     // v1.24.0 — prima voce di un menu pensato per crescere: vedi DialogoOpzioni.kt.
     *     voce("Opzioni…", null) { aperto = false; azioni.apriOpzioni() }
     * }
     * --- fine versione precedente ---
     */
    /*
     * v1.36.0 (2026-09-04) — CORREZIONE: "Ricerche salvate" era doppia (gia'
     * raggiungibile dalla campanella qui sopra in barra, con lo stesso
     * pallino delle novita') — l'utente l'ha segnalata come un'icona/voce
     * senza scopo chiaro. Tolta da qui; resta la campanella in barra, e da
     * questa versione anche la lente nel campo di ricerca (vedi
     * SchermataRicerca.kt) apre la stessa cosa.
     *
     * "Vista compatta" si sposta anche lei: vedi il commento su AzioniBarra
     * qui sopra e la nuova icona sotto il campo di ricerca in
     * SchermataRicerca.kt.
     *
     * --- versione precedente (fino alla v1.35.0) ---
     * voce(
     *     "Ricerche salvate (${stato.ricerche})" +
     *             if (stato.nuovi > 0) " · ${stato.nuovi} nuovi" else "",
     *     null
     * ) { aperto = false; azioni.apriRicerche() }
     * voce("Annunci nascosti (${stato.nascosti})", null) { aperto = false; azioni.apriNascosti() }
     * voce("Vista compatta", stato.vistaCompatta) { aperto = false; azioni.cambiaVista() }
     * --- fine versione precedente ---
     */
    DropdownMenu(expanded = aperto, onDismissRequest = { aperto = false }) {

        voce("Annunci nascosti (${stato.nascosti})", null) { aperto = false; azioni.apriNascosti() }

        HorizontalDivider()

        if (!stato.modoRicerca) {
            voce("Condividi la pagina", null) { aperto = false; azioni.condividi() }
            voce("Apri nel browser", null) { aperto = false; azioni.apriNelBrowser() }
            HorizontalDivider()
        }

        voce("Blocco pubblicità", stato.bloccoAttivo) { aperto = false; azioni.cambiaBlocco() }
        voce("Script nella pagina", stato.scriptPagina) { aperto = false; azioni.cambiaScriptPagina() }
        voce(if (stato.fonteVinted) "Sito: Vinted" else "Sito: Subito", null) { aperto = false; azioni.cambiaFonte() }

        HorizontalDivider()

        voce("Opzioni…", null) { aperto = false; azioni.apriOpzioni() }
    }
}

/*
 * v1.37.0 (2026-09-04) — nuovo: vedi il commento sopra le actions() della
 * barra per il perche'. Un Box a mano invece di BadgedBox perche' qui
 * servono DUE overlay sulla stessa icona (il pallino delle novita' in alto
 * a destra, gia' di prima, e la lente in basso a destra) — BadgedBox offre
 * un solo angolo di badge.
 */
@Composable
private fun IconaRicercheSalvate(nuovi: Int, apri: () -> Unit) {
    Box {
        IconButton(onClick = apri) {
            Icon(
                Icons.Filled.Notifications, "Ricerche salvate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (nuovi > 0) {
            Badge(Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 6.dp)) {
                Text("$nuovi")
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-4).dp, y = (-4).dp)
                .size(14.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Search, null,
                Modifier.size(9.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** @param acceso null per un comando semplice, true/false per un interruttore. */
@Composable
private fun voce(testo: String, acceso: Boolean?, azione: () -> Unit) {
    DropdownMenuItem(
        text = { Text(testo) },
        trailingIcon = {
            if (acceso != null) Text(
                if (acceso) "ON" else "OFF",
                style = MaterialTheme.typography.labelSmall,
                color = if (acceso) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        onClick = azione
    )
}
