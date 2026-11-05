package it.monkey.app

/*
 * Subito+ — controllo periodico delle ricerche salvate
 * v0.6.0 (2026-08-30) — Fase 5
 *
 * COME NON SEMBRARE UN BOT
 * E' il punto piu' delicato del progetto, piu' di qualsiasi impronta lasciata
 * nella pagina: richieste a intervalli regolari, senza immagini e senza
 * scorrimento, sono la firma di un programma. Quindi:
 *   - intervallo minimo di 30 minuti, con scarto casuale all'avvio
 *   - una fonte per volta, con una pausa fra l'una e l'altra
 *   - una pausa anche fra una ricerca salvata e la successiva
 *   - la chiamata parte dalla pagina vera, con la sessione dell'utente,
 *     esattamente come quando la ricerca la fa una persona
 *   - solo la prima pagina di risultati: si cercano le novita', non un archivio
 *
 * Il WebView vive sul filo principale anche qui: il worker si sposta li' per
 * crearlo e interrogarlo, e attende il risultato senza bloccare nulla.
 */

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.random.Random

class Controllore(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        RicercheSalvate.carica(ctx)
        Nascosti.carica(ctx)
        val ricerche = RicercheSalvate.tutte()
        if (ricerche.isEmpty()) return Result.success()

        // Scarto casuale: due telefoni con la stessa app non bussano insieme.
        delay(Random.nextLong(0, 90_000))

        val nomePonte = ctx.getSharedPreferences("subitoplus", Context.MODE_PRIVATE)
            .getString("nomePonte", null) ?: return Result.success()

        val motore = withContext(Dispatchers.Main) { MotoreRicerca(ctx, nomePonte) }
        val trovati = LinkedHashMap<String, MutableList<Annuncio>>()

        try {
            for (r in ricerche) {
                for (f in r.fonti()) {
                    val esito = interroga(motore, f, r.query)
                    if (esito != null) {
                        val utili = esito.filter { a ->
                            !a.venduto &&
                                    !Nascosti.contiene(a.chiave) &&
                                    dentroPrezzo(a, r.prezzoMin, r.prezzoMax)
                        }
                        val nuovi = RicercheSalvate.registraEsito(ctx, r.id, utili.map { it.chiave })
                        if (nuovi > 0) {
                            trovati.getOrPut(r.id) { ArrayList() }
                                .addAll(utili.takeLast(nuovi))
                        }
                    }
                    delay(Random.nextLong(4_000, 12_000))   // pausa fra le fonti
                }
                delay(Random.nextLong(8_000, 20_000))       // pausa fra le ricerche
            }
        } finally {
            withContext(Dispatchers.Main) { motore.chiudi() }
        }

        trovati.forEach { (idRicerca, annunci) ->
            val r = ricerche.firstOrNull { it.id == idRicerca } ?: return@forEach
            avvisa(ctx, r, annunci)
        }
        return Result.success()
    }

    private fun dentroPrezzo(a: Annuncio, min: String, max: String): Boolean {
        val pMin = min.replace(',', '.').toDoubleOrNull()
        val pMax = max.replace(',', '.').toDoubleOrNull()
        if (pMin == null && pMax == null) return true
        val p = a.prezzo ?: return false
        if (pMin != null && p < pMin) return false
        if (pMax != null && p > pMax) return false
        return true
    }

    /** Una sola pagina, con un tetto di attesa: se la fonte non risponde si passa oltre. */
    private suspend fun interroga(motore: MotoreRicerca, f: Fonte, query: String): List<Annuncio>? =
        withTimeoutOrNull(60_000) {
            suspendCancellableCoroutine { cont ->
                motore.ascoltatore = object : MotoreRicerca.Ascoltatore {
                    override fun risultati(fonte: String, annunci: List<Annuncio>, diagnostica: String) {
                        if (fonte == f.nome && cont.isActive) cont.resume(annunci)
                    }
                    override fun errore(fonte: String, messaggio: String) {
                        if (fonte == f.nome && cont.isActive) cont.resume(emptyList())
                    }
                }
                motore.cerca(query, 1, "recenti", listOf(f))
            }
        }

    // ------------------------------------------------------------------
    private fun avvisa(ctx: Context, r: RicercaSalvata, annunci: List<Annuncio>) {
        if (annunci.isEmpty()) return
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        preparaCanale(ctx)

        val primo = annunci.first()
        val titolo = if (annunci.size == 1) "Nuovo annuncio: ${r.query}"
        else "${annunci.size} nuovi annunci: ${r.query}"
        val testo = annunci.take(3).joinToString("\n") {
            "${it.prezzoTesto} — ${it.titolo.take(60)}"
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("query", r.query)
            putExtra("ricerca", r.id)
        }
        val pi = PendingIntent.getActivity(
            ctx, r.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(ctx, CANALE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(titolo)
            .setContentText(primo.titolo.take(60))
            .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(r.id.hashCode(), n)
        } catch (t: SecurityException) { /* permesso revocato nel frattempo */ }
    }

    companion object {
        private const val CANALE = "ricerche"
        private const val LAVORO = "controllo-ricerche"

        fun preparaCanale(ctx: Context) {
            val c = NotificationChannel(
                CANALE, "Ricerche salvate", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Avvisi sui nuovi annunci delle ricerche salvate" }
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }

        /**
         * @param minuti intervallo scelto dall'utente; sotto i 30 non si scende,
         *               ed e' una scelta di prudenza, non un limite tecnico.
         */
        fun programma(ctx: Context, minuti: Long) {
            val periodo = minuti.coerceAtLeast(30)
            val lavoro = PeriodicWorkRequestBuilder<Controllore>(periodo, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(Random.nextLong(5, 20), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                LAVORO, ExistingPeriodicWorkPolicy.UPDATE, lavoro
            )
        }

        fun ferma(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(LAVORO)
        }
    }
}
