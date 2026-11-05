package it.monkey.app

/*
 * Monkey — dialogo "novità di questa versione"
 * v1.36.0 (2026-09-04) — nuovo.
 *
 * Stessa forma degli altri dialoghi (AlertDialog, non un foglio dal basso):
 * vedi il motivo in cima a DialogoOpzioni.kt — la barra dei comandi vive in
 * una ComposeView alta poche decine di pixel, un foglio dal basso ci
 * finirebbe schiacciato dentro. Compare da solo all'avvio (vedi
 * MainActivity.onCreate e Changelog.kt per il confronto di versione), una
 * volta sola per versione: chi lo chiude non lo rivede finche' non arriva
 * un'altra versione nuova.
 */

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DialogoNovita(voce: VoceChangelog, chiudi: () -> Unit) {
    AlertDialog(
        onDismissRequest = chiudi,
        title = { Text("Novità della versione ${voce.versionName}") },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(voce.novita) { riga ->
                    Text(
                        "•  $riga",
                        Modifier.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = chiudi) { Text("Ho capito") } }
    )
}
