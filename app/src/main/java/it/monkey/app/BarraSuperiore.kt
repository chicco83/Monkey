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

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.unit.dp

/** Tutto lo stato che la barra mostra, in un solo oggetto. */
data class StatoBarra(
    val modoRicerca: Boolean,
    val bloccoAttivo: Boolean,
    val vistaCompatta: Boolean,
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
    val avanzamento: Int?,
    /** v1.15.0 — il banner "tocca ⋮ e poi Sito per accedere", solo al primo avvio. */
    val mostraGuida: Boolean
)

/** Le azioni che la barra puo' scatenare. */
class AzioniBarra(
    val cambiaModo: () -> Unit,
    val cambiaBlocco: () -> Unit,
    val cambiaVista: () -> Unit,
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
    /** v1.15.0 — chiude il banner di guida al login, per sempre. */
    val chiudiGuida: () -> Unit
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
                    IconButton(onClick = azioni.apriPreferiti) {
                        Icon(
                            Icons.Filled.FavoriteBorder, "Preferiti",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BadgedBox(badge = {
                        if (stato.nuovi > 0) Badge { Text("${stato.nuovi}") }
                    }) {
                        IconButton(onClick = azioni.apriRicerche) {
                            Icon(
                                Icons.Filled.Notifications, "Ricerche salvate",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = azioni.cambiaModo) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = if (stato.modoRicerca) "Vai alla navigazione" else "Torna alla ricerca",
                            tint = if (stato.modoRicerca) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!stato.modoRicerca) IconButton(onClick = azioni.ricarica) {
                        Icon(Icons.Filled.Refresh, "Ricarica la pagina")
                    }
                    Menu(stato, azioni)
                }
            )

            /*
             * v1.15.0 — banner di guida al login, solo al primo avvio.
             *
             * L'account su Subito o Vinted si usa toccando "Sito" nel menu qui
             * sotto: apre quel sito dentro l'app, dove ci si accede come si
             * farebbe dal browser. Non e' ovvio la prima volta — i tre puntini
             * non dicono "e' qui che si fa il login" — quindi lo si indica una
             * volta sola, subito sotto la barra dov'e' il menu, e poi si sparisce
             * per sempre: alla chiusura esplicita (la ✕) o non appena l'utente
             * tocca davvero "Sito" (vedi MainActivity, cambiaFonte).
             */
            if (stato.mostraGuida) {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Per accedere con il tuo account, tocca ⋮ qui sopra e poi «Sito» " +
                                    "per aprire Subito o Vinted e fare il login.",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(onClick = azioni.chiudiGuida) {
                            Icon(
                                Icons.Filled.Close, "Chiudi il suggerimento",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

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

    DropdownMenu(expanded = aperto, onDismissRequest = { aperto = false }) {

        voce(
            "Ricerche salvate (${stato.ricerche})" +
                    if (stato.nuovi > 0) " · ${stato.nuovi} nuovi" else "",
            null
        ) { aperto = false; azioni.apriRicerche() }
        voce("Annunci nascosti (${stato.nascosti})", null) { aperto = false; azioni.apriNascosti() }
        voce("Vista compatta", stato.vistaCompatta) { aperto = false; azioni.cambiaVista() }

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

        voce("Salva backup", null) { aperto = false; azioni.salvaBackup() }
        voce("Importa file…", null) { aperto = false; azioni.caricaRegole() }

        HorizontalDivider()

        voce("Modalità sonda", stato.modoSonda) { aperto = false; azioni.cambiaSonda() }
        voce("Analizza pagina", null) { aperto = false; azioni.analizza() }
        voce("Sonda offerte", null) { aperto = false; azioni.sondaOfferte() }
        voce("Esporta diagnostica", null) { aperto = false; azioni.esporta() }
        voce("Azzera contatori", null) { aperto = false; azioni.azzera() }
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
