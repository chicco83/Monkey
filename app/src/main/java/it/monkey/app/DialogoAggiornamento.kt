package it.monkey.app

/*
 * Monkey — dialogo "nuova versione disponibile"
 * v1.35.0 (2026-09-02) — nuovo.
 *
 * Stessa forma di DialogoRicerche/DialogoOpzioni: un AlertDialog, non un
 * foglio dal basso, per lo stesso motivo di sempre — la barra dei comandi
 * vive in una ComposeView alta poche decine di pixel, un foglio dal basso
 * ci finirebbe schiacciato dentro. Compare da solo al controllo dell'avvio
 * (vedi Aggiornamenti.kt e MainActivity.onCreate), mai piu' di una volta a
 * sessione: chi tocca "Più tardi" lo rivede al prossimo riavvio dell'app,
 * non subito dopo.
 */

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DialogoAggiornamento(aggiornamento: Aggiornamento, scarica: () -> Unit, chiudi: () -> Unit) {
    AlertDialog(
        onDismissRequest = chiudi,
        title = { Text("Versione ${aggiornamento.versione} disponibile") },
        text = {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                Text(
                    if (aggiornamento.note.isNotBlank()) aggiornamento.note
                    else "È disponibile una nuova versione di Monkey.",
                    style = MaterialTheme.typography.bodyMedium,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = { TextButton(onClick = scarica) { Text("Scarica e installa") } },
        dismissButton = { TextButton(onClick = chiudi) { Text("Più tardi") } }
    )
}
