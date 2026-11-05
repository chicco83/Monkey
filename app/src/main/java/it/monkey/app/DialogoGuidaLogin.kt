package it.monkey.app

/*
 * Monkey — dialogo "come accedere con il tuo account"
 * v1.46.0 (2026-09-08) — nuovo, sostituisce il banner della v1.15.0.
 *
 * PERCHE' UN DIALOGO E NON PIU' UN BANNER
 * Dalla v1.15.0 la stessa spiegazione viveva in un banner dentro la
 * schermata di ricerca (Card richiudibile, non bloccante — vedi la
 * "versione precedente" commentata in BarraSuperiore.kt): spariva per
 * sempre alla prima chiusura O al primo tocco vero su "Sito", anche solo
 * per curiosita', senza aver poi completato davvero il login.
 *
 * L'utente ha segnalato un 403 su Vinted causato proprio da un logout mai
 * notato: segno che quel banner, leggero e facile da scorrere via, non
 * bastava a far capire dove e come si accede — ne' che un logout puo'
 * ripresentarsi piu' avanti, non solo al primo avvio. Un AlertDialog blocca
 * lo schermo finche' non lo si chiude apposta (stesso schema di
 * DialogoNovita/DialogoAggiornamento, vedi MainActivity): piu' invadente la
 * prima volta, ma nessuno lo salta per sbaglio.
 *
 * Compare una volta sola, al primo avvio in assoluto — non per versione:
 * riusa la stessa chiave prefs "guidaLoginVista" della v1.15.0, la logica di
 * MainActivity.onCreate/chiudiGuidaLogin() non cambia, cambia solo COSA
 * quello stato ora fa apparire (vedi li').
 */

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DialogoGuidaLogin(chiudi: () -> Unit) {
    AlertDialog(
        onDismissRequest = chiudi,
        title = { Text("Come accedere con il tuo account") },
        text = {
            Column {
                Text(
                    "Cercare non richiede un account, ma per vedere i preferiti o " +
                            "scrivere a un venditore serve essere collegati su Subito e " +
                            "su Vinted, come sul sito vero.",
                    Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Tocca ⋮ in alto, poi «Sito»: si apre Subito o Vinted dentro " +
                            "l'app, dove accedi esattamente come faresti dal browser. " +
                            "Finito, torna qui con l'icona home o «indietro».",
                    Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Se più avanti compare un avviso rosso che dice che Vinted non " +
                            "risponde più correttamente, spesso vuol dire solo che sei " +
                            "uscito: ripeti questi stessi passaggi per rientrare.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { TextButton(onClick = chiudi) { Text("Ho capito") } }
    )
}
