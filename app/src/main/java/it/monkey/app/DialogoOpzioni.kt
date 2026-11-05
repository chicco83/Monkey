package it.monkey.app

/*
 * Subito+ — pannello "Opzioni"
 * v1.35.0 (2026-09-02) — aggiunta "Cerca aggiornamenti" tra gli Strumenti.
 *
 * Primo pezzo di un menu pensato per crescere: oggi una sola voce, ma il
 * dialogo e AzioniOpzioni sono gia' fatti per accoglierne altre senza
 * riscrivere la struttura (una Column con dentro tante sezioni, non un
 * singolo controllo isolato). Stessa forma di DialogoRicerche: un AlertDialog
 * invece di un foglio dal basso, per lo stesso motivo — la barra dei comandi
 * vive in una ComposeView alta poche decine di pixel.
 *
 * Prima voce: cosa fare all'avvio. Da sempre (v1.17.0) l'app riparte da sola
 * sull'ultima ricerca fatta se il processo e' stato chiuso — comodo per chi
 * cerca sempre la stessa cosa, fastidioso per chi preferisce vedere prima le
 * ultime proposte (la home). Ora e' una scelta, non un comportamento fisso.
 *
 * v1.35.0 — il controllo automatico dell'avvio (Aggiornamenti.controlla in
 * MainActivity.onCreate) resta silenzioso se non trova nulla o se fallisce:
 * chi vuole sapere SUBITO se e' gia' all'ultima versione, o forzare un
 * controllo senza riavviare l'app, lo trova qui tra gli Strumenti — si usa
 * di rado, esattamente il criterio che governa questa sezione (vedi sopra).
 */

/*
 * v1.26.0 (2026-09-01) — CORREZIONE: il menu dai tre pallini (BarraSuperiore.kt)
 * era cresciuto voce dopo voce da quando esisteva solo quello (v0.5.0) fino a
 * quattordici righe. L'utente chiede di spostare qui tutto tranne le prime
 * sei (Ricerche salvate, Annunci nascosti, Vista compatta, Blocco pubblicita',
 * Script nella pagina, Sito) — quelle restano nel menu perche' si usano
 * spesso o servono da qualunque schermata sia aperta; il resto (backup,
 * import, sonda, diagnostica) si usa di rado, ed e' esattamente cio' per cui
 * questo pannello e' nato. Le azioni vere restano dove gia' vivevano in
 * MainActivity — qui arrivano solo le lambda, come per il menu.
 *
 * "Condividi la pagina" e "Apri nel browser" NON si spostano: sono legate
 * alla pagina aperta in quel momento (compaiono solo fuori da "Ricerca"),
 * non sono impostazioni — spostarle in un pannello raggiungibile sempre le
 * renderebbe cliccabili anche quando non hanno una pagina a cui riferirsi.
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Le azioni che il pannello Opzioni puo' scatenare. Una per voce. */
class AzioniOpzioni(
    /** true = all'avvio vai alla home; false = riprendi l'ultima ricerca. */
    val cambiaAvvioHome: (Boolean) -> Unit,
    /*
     * v1.26.0 — le sette voci spostate qui dal menu dai tre pallini, nello
     * stesso ordine in cui ci stavano: sono le stesse azioni di sempre
     * (vedi MainActivity.azioni() prima della v1.26.0 e AzioniBarra.kt),
     * solo raggiunte da un altro posto.
     */
    val salvaBackup: () -> Unit,
    val caricaRegole: () -> Unit,
    val cambiaSonda: () -> Unit,
    val analizza: () -> Unit,
    val sondaOfferte: () -> Unit,
    val esporta: () -> Unit,
    val azzera: () -> Unit,
    /*
     * v1.35.0 (2026-09-02) — controllo manuale degli aggiornamenti (vedi
     * Aggiornamenti.kt). A differenza del controllo silenzioso dell'avvio,
     * un tocco esplicito merita sempre una risposta: MainActivity la passa
     * gia' pronta come toast ("c'e' una versione nuova" apre il dialogo da
     * solo tramite lo stesso stato osservato dall'avvio, "sei gia' all'ultima
     * versione"/"controllo fallito" restano un semplice avviso).
     */
    val cercaAggiornamenti: () -> Unit
)

@Composable
fun DialogoOpzioni(
    avvioHome: Boolean,
    modoSonda: Boolean,
    azioni: AzioniOpzioni,
    chiudi: () -> Unit
) {
    AlertDialog(
        onDismissRequest = chiudi,
        confirmButton = { TextButton(onClick = chiudi) { Text("Chiudi") } },
        title = { Text("Opzioni") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "All'avvio",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                VoceScelta(
                    testo = "Riprendi l'ultima ricerca",
                    selezionata = !avvioHome,
                    onClick = { azioni.cambiaAvvioHome(false) }
                )
                VoceScelta(
                    // v1.26.0 — "ultime" tolto: la home ora le mostra tutte.
                    testo = "Vai alla home (le tue proposte)",
                    selezionata = avvioHome,
                    onClick = { azioni.cambiaAvvioHome(true) }
                )

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                Text(
                    "Strumenti",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                /*
                 * v1.38.0 (2026-09-04) — CORREZIONE: il tetto era rimasto a
                 * 280.dp (fissato in v1.26.0 quando le righe erano sette:
                 * Modalita' sonda + sei voci). Da v1.35.0 le righe sono otto
                 * (si e' aggiunta "Cerca aggiornamenti"), e otto righe da
                 * ~40dp l'una (10dp di padding sopra e sotto piu' il testo)
                 * superano gli 280dp: "Esporta diagnostica" e "Azzera
                 * contatori" restavano tagliate fuori dall'area visibile,
                 * senza alcun indizio che la LazyColumn fosse scorribile
                 * (l'utente ha segnalato "in opzioni non c'e' piu' la voce
                 * per esportare o log" — il codice c'era sempre, era solo
                 * fuori dallo spazio disegnato). Alzato a 400.dp, comodo per
                 * qualche voce in piu' futura senza dover ritoccare ancora.
                 *
                 * --- versione precedente (fino alla v1.37.0) ---
                 * LazyColumn(
                 *     Modifier.heightIn(max = 280.dp),
                 *     verticalArrangement = Arrangement.spacedBy(2.dp)
                 * ) {
                 * --- fine versione precedente ---
                 */
                LazyColumn(
                    Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    /*
                     * --- versione precedente (fino alla v1.34.0) ---
                     * val voci = listOf(
                     *     "Salva backup" to azioni.salvaBackup,
                     *     "Importa file…" to azioni.caricaRegole,
                     *     "Analizza pagina" to azioni.analizza,
                     *     "Sonda offerte" to azioni.sondaOfferte,
                     *     "Esporta diagnostica" to azioni.esporta,
                     *     "Azzera contatori" to azioni.azzera
                     * )
                     * --- fine versione precedente ---
                     */
                    val voci = listOf(
                        "Cerca aggiornamenti" to azioni.cercaAggiornamenti,
                        "Salva backup" to azioni.salvaBackup,
                        "Importa file…" to azioni.caricaRegole,
                        "Analizza pagina" to azioni.analizza,
                        "Sonda offerte" to azioni.sondaOfferte,
                        "Esporta diagnostica" to azioni.esporta,
                        "Azzera contatori" to azioni.azzera
                    )
                    item {
                        VoceStrumento("Modalità sonda", modoSonda, azioni.cambiaSonda)
                    }
                    items(voci, key = { it.first }) { (testo, azione) ->
                        VoceStrumento(testo, null, azione)
                    }
                }
            }
        }
    )
}

/** Una riga radio-button + testo, tutta cliccabile. */
@Composable
private fun VoceScelta(testo: String, selezionata: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selezionata, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selezionata, onClick = onClick)
        Text(testo, style = MaterialTheme.typography.bodyMedium)
    }
}

/*
 * v1.26.0 — una riga di "Strumenti": stesso schema della voce del menu dai
 * tre pallini (testo + "ON"/"OFF" se e' un interruttore, altrimenti un
 * comando semplice), ma senza il DropdownMenuItem che serve solo dentro un
 * DropdownMenu — qui siamo dentro un AlertDialog.
 */
@Composable
private fun VoceStrumento(testo: String, acceso: Boolean?, azione: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = false, onClick = azione)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(testo, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (acceso != null) Text(
            if (acceso) "ON" else "OFF",
            style = MaterialTheme.typography.labelSmall,
            color = if (acceso) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
