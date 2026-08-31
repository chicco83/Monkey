package it.monkey.app

/*
 * Subito+ — pannello delle ricerche salvate
 * v0.6.0 (2026-08-30) — Fase 5
 *
 * E' un dialogo e non un foglio dal basso per una ragione pratica: la barra dei
 * comandi vive in una ComposeView alta poche decine di pixel, e un foglio dal
 * basso resterebbe confinato li' dentro. Un dialogo apre invece una finestra
 * propria e copre lo schermo comunque.
 */

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class AzioniRicerche(
    val apri: (RicercaSalvata) -> Unit,
    val elimina: (RicercaSalvata) -> Unit,
    val controllaOra: () -> Unit,
    val cambiaAutomatico: (Boolean) -> Unit,
    val cambiaIntervallo: (Long) -> Unit
)

@Composable
fun DialogoRicerche(
    ricerche: List<RicercaSalvata>,
    automatico: Boolean,
    intervallo: Long,
    azioni: AzioniRicerche,
    chiudi: () -> Unit
) {
    AlertDialog(
        onDismissRequest = chiudi,
        confirmButton = { TextButton(onClick = chiudi) { Text("Chiudi") } },
        dismissButton = {
            if (ricerche.isNotEmpty())
                TextButton(onClick = azioni.controllaOra) { Text("Controlla ora") }
        },
        title = { Text("Ricerche salvate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Controlli automatici", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "In background, quando il telefono è in rete",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(automatico, azioni.cambiaAutomatico)
                }

                if (automatico) {
                    Text(
                        "Ogni",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(30L to "30 min", 60L to "1 ora", 180L to "3 ore", 720L to "12 ore")
                            .forEach { (m, t) ->
                                FilterChip(
                                    selected = intervallo == m,
                                    onClick = { azioni.cambiaIntervallo(m) },
                                    label = { Text(t) }
                                )
                            }
                    }
                    Text(
                        "Sotto la mezz'ora non si scende: un ritmo troppo regolare e troppo " +
                                "fitto è la firma che distingue un programma da una persona.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                if (ricerche.isEmpty()) {
                    Text(
                        "Nessuna ricerca salvata. Fai una ricerca e tocca «Salva» sopra i risultati.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(ricerche, key = { it.id }) { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            r.query,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (r.nuoviDaVedere > 0) {
                                            Spacer(Modifier.width(6.dp))
                                            Badge { Text("${r.nuoviDaVedere}") }
                                        }
                                    }
                                    Text(
                                        r.descrizione() + " · " + quando(r.ultimoControllo),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { azioni.apri(r) }) {
                                    Icon(Icons.Filled.Search, "Cerca ora")
                                }
                                IconButton(onClick = { azioni.elimina(r) }) {
                                    Icon(Icons.Filled.Close, "Elimina")
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun quando(t: Long): String {
    if (t <= 0) return "mai controllata"
    val m = (System.currentTimeMillis() - t) / 60_000
    return when {
        m < 2 -> "controllata ora"
        m < 60 -> "controllata $m min fa"
        m < 60 * 24 -> "controllata ${m / 60} h fa"
        else -> "controllata ${m / 1440} gg fa"
    }
}
