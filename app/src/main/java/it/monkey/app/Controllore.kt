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

    /*
     * v1.27.0 (2026-09-01) — CORREZIONE: "ho attivato le notifiche ma non ne
     * e' arrivata nessuna". Due cose in una, trovate leggendo il codice:
     *
     * 1. Bug vero: TUTTE le notifiche partivano solo alla fine, dopo aver
     *    finito TUTTE le ricerche salvate ("trovati.forEach" stava fuori dal
     *    ciclo "for (r in ricerche)"). Un WorkManager periodico normale ha un
     *    tempo massimo di esecuzione (circa dieci minuti, meno su telefoni
     *    con un risparmio energetico aggressivo) — e con piu' ricerche, piu'
     *    fonti, e le pause volute apposta per non sembrare un programma
     *    (vedi la nota "COME NON SEMBRARE UN BOT" in cima al file), un giro
     *    completo puo' avvicinarsi o superare quel tetto. Se il sistema
     *    interrompe il lavoro a meta', TUTTE le notifiche gia' pronte per le
     *    ricerche finite fino a quel momento sparivano insieme a quelle non
     *    ancora fatte — e gli annunci trovati restavano comunque segnati
     *    come "gia' visti" (registraEsito() lo fa subito, ricerca per
     *    ricerca), quindi non sarebbero MAI piu' stati notificati, nemmeno
     *    al giro successivo. Ora ogni ricerca notifica appena finite le sue
     *    fonti, non aspetta piu' le altre: un'interruzione a meta' lavoro
     *    perde solo cio' che veniva dopo, non cio' che era gia' pronto.
     *
     * 2. Mancava un modo per sapere DAVVERO cosa succede in un lavoro che
     *    gira in background, senza schermo, ore dopo che l'app e' stata
     *    chiusa — la diagnostica di MotoreRicerca (motore.diagnostica) vive
     *    solo per la vita di QUESTA esecuzione e si perde con lei, mai letta
     *    da nessuno. Ora ogni giro scrive poche righe in un file che resta
     *    (controllo-log.txt): quando parte, cosa trova per ogni ricerca e
     *    fonte, se le notifiche sono concesse, quando finisce. Si esporta
     *    con "Esporta diagnostica" (Opzioni), insieme agli altri file. Se le
     *    notifiche continuano a non arrivare, quel file dira' se il lavoro
     *    gira affatto, se si ferma a meta', o se trova zero novita' davvero.
     *
     * --- versione precedente (fino alla v1.26.0) ---
     * override suspend fun doWork(): Result {
     *     val ctx = applicationContext
     *     RicercheSalvate.carica(ctx)
     *     Nascosti.carica(ctx)
     *     val ricerche = RicercheSalvate.tutte()
     *     if (ricerche.isEmpty()) return Result.success()
     *
     *     delay(Random.nextLong(0, 90_000))
     *
     *     val nomePonte = ctx.getSharedPreferences("subitoplus", Context.MODE_PRIVATE)
     *         .getString("nomePonte", null) ?: return Result.success()
     *
     *     val motore = withContext(Dispatchers.Main) { MotoreRicerca(ctx, nomePonte) }
     *     val trovati = LinkedHashMap<String, MutableList<Annuncio>>()
     *
     *     try {
     *         for (r in ricerche) {
     *             for (f in r.fonti()) {
     *                 val esito = interroga(motore, f, r.query)
     *                 if (esito != null) {
     *                     val utili = esito.filter { a ->
     *                         !a.venduto &&
     *                                 !Nascosti.contiene(a.chiave) &&
     *                                 dentroPrezzo(a, r.prezzoMin, r.prezzoMax)
     *                     }
     *                     val nuovi = RicercheSalvate.registraEsito(ctx, r.id, utili.map { it.chiave })
     *                     if (nuovi > 0) {
     *                         trovati.getOrPut(r.id) { ArrayList() }
     *                             .addAll(utili.takeLast(nuovi))
     *                     }
     *                 }
     *                 delay(Random.nextLong(4_000, 12_000))   // pausa fra le fonti
     *             }
     *             delay(Random.nextLong(8_000, 20_000))       // pausa fra le ricerche
     *         }
     *     } finally {
     *         withContext(Dispatchers.Main) { motore.chiudi() }
     *     }
     *
     *     trovati.forEach { (idRicerca, annunci) ->
     *         val r = ricerche.firstOrNull { it.id == idRicerca } ?: return@forEach
     *         avvisa(ctx, r, annunci)
     *     }
     *     return Result.success()
     * }
     * --- fine versione precedente ---
     */
    /*
     * v1.31.0 (2026-09-01) — CORREZIONE: "il doppio avvio nel log".
     * L'utente ha mandato controllo-log.txt e c'erano coppie come queste:
     *
     *   12:13:03 avvio · 4 ricerche · notifiche concesse
     *   12:13:31 avvio · 4 ricerche · notifiche concesse
     *   12:14:56 fine giro
     *
     * Due "avvio" a 28 secondi l'uno dall'altro, un solo "fine giro": due
     * ESECUZIONI DI doWork() stavano girando insieme. Leggendo il codice,
     * un varco vero c'e': "Controlla ora" (MainActivity.kt, controllaOra)
     * mette in coda un OneTimeWorkRequest con WorkManager.enqueue() semplice
     * — non enqueueUniqueWork — quindi nulla impedisce che un tocco su
     * "Controlla ora" parta MENTRE il giro periodico (Controllore.programma,
     * quello si' "unique") sta gia' girando: due Controllore, stesso
     * processo, in parallelo. Non e' l'unica spiegazione possibile — anche
     * un'esecuzione interrotta a meta' dal sistema (il processo ucciso prima
     * che il blocco finally sotto arrivi a scrivere "fine giro", poi
     * WorkManager la fa ripartire) produrrebbe lo stesso segno nel log — ma
     * e' l'unica delle due che il codice mostra con certezza, quindi e'
     * quella che si corregge.
     *
     * La correzione non tocca "Controlla ora": chiude il varco qui, in
     * doWork(), con una guardia di processo (inCorso, sotto). Cosi' vale per
     * QUALUNQUE modo in cui due esecuzioni potessero sovrapporsi, non solo
     * per questa — comprese le richieste di rete raddoppiate inutilmente, e
     * la corsa fra due scritture concorrenti su controllo-log.txt (log() non
     * e' protetta da due chiamanti insieme: legge il file, aggiunge una
     * riga, riscrive tutto — se due lo fanno nello stesso momento, l'ultimo
     * che scrive puo' cancellare la riga dell'altro senza errori, ed e'
     * probabilmente per questo che "fine giro" a volte manca).
     *
     * --- versione precedente (fino alla v1.27.0/v1.30.0, corpo invariato) ---
     * override suspend fun doWork(): Result {
     *     val ctx = applicationContext
     *     RicercheSalvate.carica(ctx)
     *     Nascosti.carica(ctx)
     *     val ricerche = RicercheSalvate.tutte()
     *     if (ricerche.isEmpty()) { log(ctx, "nessuna ricerca salvata: esco subito"); return Result.success() }
     *
     *     val haPermesso = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
     *             PackageManager.PERMISSION_GRANTED
     *     log(ctx, "avvio · ${ricerche.size} ricerche · notifiche ${if (haPermesso) "concesse" else "NEGATE — nessun avviso potra' arrivare"}")
     *
     *     delay(Random.nextLong(0, 90_000))
     *
     *     val nomePonte = ctx.getSharedPreferences("subitoplus", Context.MODE_PRIVATE)
     *         .getString("nomePonte", null)
     *     if (nomePonte == null) { log(ctx, "nomePonte assente nelle preferenze: esco"); return Result.success() }
     *
     *     val motore = withContext(Dispatchers.Main) { MotoreRicerca(ctx, nomePonte) }
     *
     *     try {
     *         for (r in ricerche) {
     *             val trovatiPerRicerca = ArrayList<Annuncio>()
     *             for (f in r.fonti()) {
     *                 val esito = interroga(motore, f, r.query)
     *                 if (esito != null) {
     *                     val utili = esito.filter { a ->
     *                         !a.venduto && !Nascosti.contiene(a.chiave) && dentroPrezzo(a, r.prezzoMin, r.prezzoMax)
     *                     }
     *                     val nuovi = RicercheSalvate.registraEsito(ctx, r.id, utili.map { it.chiave })
     *                     log(ctx, "«${r.query}» ${f.nome}: ${esito.size} annunci, $nuovi nuovi")
     *                     if (nuovi > 0) trovatiPerRicerca.addAll(utili.takeLast(nuovi))
     *                 } else {
     *                     log(ctx, "«${r.query}» ${f.nome}: nessuna risposta entro il tempo massimo")
     *                 }
     *                 delay(Random.nextLong(4_000, 12_000))
     *             }
     *             if (trovatiPerRicerca.isNotEmpty()) {
     *                 avvisa(ctx, r, trovatiPerRicerca)
     *                 log(ctx, "«${r.query}»: notificati ${trovatiPerRicerca.size} annunci")
     *             }
     *             delay(Random.nextLong(8_000, 20_000))
     *         }
     *     } finally {
     *         withContext(Dispatchers.Main) { motore.chiudi() }
     *         log(ctx, "fine giro")
     *     }
     *     return Result.success()
     * }
     * --- fine versione precedente ---
     */
    /*
     * v1.33.0 — "compareAndSet(false, true)" invece di "if (inCorso) return;
     * inCorso = true": vedi il commento sulla variabile, sopra nella
     * companion, per il perche'. Il resto del corpo (sotto) non cambia.
     *
     * --- versione precedente (v1.31.0) ---
     * override suspend fun doWork(): Result {
     *     val ctx = applicationContext
     *     if (inCorso) {
     *         log(ctx, "un giro e' gia' in corso: salto questo per non raddoppiare le richieste")
     *         return Result.success()
     *     }
     *     inCorso = true
     *     try {
     *         … (corpo identico) …
     *     } finally {
     *         inCorso = false
     *     }
     * }
     * --- fine versione precedente ---
     */
    override suspend fun doWork(): Result {
        val ctx = applicationContext

        // v1.33.0 — un solo giro alla volta, garantito atomicamente: vedi il commento sopra.
        if (!inCorso.compareAndSet(false, true)) {
            log(ctx, "un giro e' gia' in corso: salto questo per non raddoppiare le richieste")
            return Result.success()
        }
        try {
            RicercheSalvate.carica(ctx)
            Nascosti.carica(ctx)
            val ricerche = RicercheSalvate.tutte()
            if (ricerche.isEmpty()) { log(ctx, "nessuna ricerca salvata: esco subito"); return Result.success() }

            val haPermesso = ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            log(ctx, "avvio · ${ricerche.size} ricerche · notifiche ${if (haPermesso) "concesse" else "NEGATE — nessun avviso potra' arrivare"}")

            // Scarto casuale: due telefoni con la stessa app non bussano insieme.
            delay(Random.nextLong(0, 90_000))

            val nomePonte = ctx.getSharedPreferences("subitoplus", Context.MODE_PRIVATE)
                .getString("nomePonte", null)
            if (nomePonte == null) { log(ctx, "nomePonte assente nelle preferenze: esco"); return Result.success() }

            /*
             * v1.37.0 (2026-09-04) — tentativo precedente, insufficiente da
             * solo (l'utente ha confermato: ne' il giro automatico ne'
             * "Controlla ora" aggiornano "ultimoControllo"). Restava un
             * genitore mancante per il WebView, ma non era la causa vera:
             * vedi la CORREZIONE qui sotto.
             *
             * --- versione precedente (v1.37.0) ---
             * val motore = withContext(Dispatchers.Main) {
             *     MotoreRicerca(ctx, nomePonte, android.widget.FrameLayout(ctx))
             * }
             * --- fine versione precedente ---
             */
            val motore = withContext(Dispatchers.Main) {
                MotoreRicerca(ctx, nomePonte, android.widget.FrameLayout(ctx))
            }

            /*
             * v1.38.0 (2026-09-04) — CORREZIONE VERA (si spera): "ultimoControllo"
             * non si aggiornava MAI, ne' dal giro automatico ne' da "Controlla
             * ora" — non un risultato sbagliato, NESSUN risultato, nemmeno
             * "zero annunci trovati". Questo e' il segno giusto per capire cosa
             * succede davvero: "registraEsito()" (che scrive ultimoControllo)
             * si chiama solo quando "interroga()" torna un esito NON nullo — se
             * non si aggiorna mai, vuol dire che "interroga()" non torna MAI,
             * ne' con un successo ne' con l'esito vuoto del suo stesso
             * meccanismo di resa ("sessione non pronta" dopo 16 secondi, in
             * avvia() su MotoreRicerca.kt) — un timeout pieno di 60 secondi
             * senza che nulla dall'interno riesca mai a rispondere.
             *
             * La spiegazione che regge e' un errore di thread, non di rete: un
             * WebView (creazione, loadUrl, evaluateJavascript — tutta
             * MotoreRicerca.sessione()/avvia()) puo' essere toccato SOLO dal
             * thread principale, o Android lo rifiuta con un'eccezione. Qui
             * sopra "MotoreRicerca(...)" viene creato dentro un
             * "withContext(Dispatchers.Main)", ma quel blocco finisce subito
             * dopo — un withContext torna al contesto di partenza appena il
             * suo corpo finisce, non ci resta dentro. "interroga()", chiamata
             * poco sotto, NON era avvolta in un altro withContext(Main): girava
             * quindi sul dispatcher di CoroutineWorker (un executor in
             * background, non il thread principale) esattamente come tutto il
             * resto di doWork(). Il primissimo tocco a un WebView da li'
             * (sessione(f) dentro avvia(), alla prima ricerca del primo giro)
             * lancia un'eccezione subito — MAI un timeout "lento": la riga
             * "avvio · N ricerche…" del log compare sempre (e' prima di questo
             * punto), ma nessuna riga "«query» fonte: N annunci" compare mai
             * dopo — coerente con cio' che il log dell'utente avrebbe mostrato,
             * e con "nessun aggiornamento, nessun errore visibile" (un worker
             * in background che fallisce non mostra nulla a schermo).
             *
             * La ricerca in primo piano (MainActivity, StatoRicerca.cerca())
             * non ha mai avuto questo problema per un motivo semplice: parte
             * SEMPRE da un callback dell'interfaccia, che su Android e' gia'
             * il thread principale — non ha mai avuto bisogno di un
             * withContext esplicito per essere nel posto giusto.
             *
             * Rimedio: l'intero ciclo di ricerca (non solo la costruzione di
             * MotoreRicerca) gira dentro un unico withContext(Dispatchers.Main)
             * — "delay()" al suo interno resta innocuo (non blocca il thread,
             * sospende la coroutine) quindi le pause fra una fonte e l'altra
             * restano quelle di sempre, pensate per non sembrare un programma
             * (vedi la nota in cima al file).
             *
             * --- versione precedente (fino alla v1.37.0) ---
             * try {
             *     for (r in ricerche) {
             *         val trovatiPerRicerca = ArrayList<Annuncio>()
             *         for (f in r.fonti()) {
             *             val esito = interroga(motore, f, r.query)
             *             if (esito != null) {
             *                 val utili = esito.filter { a ->
             *                     !a.venduto &&
             *                             !Nascosti.contiene(a.chiave) &&
             *                             dentroPrezzo(a, r.prezzoMin, r.prezzoMax)
             *                 }
             *                 val nuovi = RicercheSalvate.registraEsito(ctx, r.id, utili.map { it.chiave })
             *                 log(ctx, "«${r.query}» ${f.nome}: ${esito.size} annunci, $nuovi nuovi")
             *                 if (nuovi > 0) trovatiPerRicerca.addAll(utili.takeLast(nuovi))
             *             } else {
             *                 log(ctx, "«${r.query}» ${f.nome}: nessuna risposta entro il tempo massimo")
             *             }
             *             delay(Random.nextLong(4_000, 12_000))   // pausa fra le fonti
             *         }
             *         if (trovatiPerRicerca.isNotEmpty()) {
             *             avvisa(ctx, r, trovatiPerRicerca)
             *             log(ctx, "«${r.query}»: notificati ${trovatiPerRicerca.size} annunci")
             *         }
             *         delay(Random.nextLong(8_000, 20_000))       // pausa fra le ricerche
             *     }
             * } finally {
             *     withContext(Dispatchers.Main) { motore.chiudi() }
             *     log(ctx, "fine giro")
             * }
             * --- fine versione precedente ---
             */
            try {
                withContext(Dispatchers.Main) {
                    for (r in ricerche) {
                        val trovatiPerRicerca = ArrayList<Annuncio>()
                        for (f in r.fonti()) {
                            val esito = interroga(motore, f, r.query)
                            if (esito != null) {
                                val utili = esito.filter { a ->
                                    !a.venduto &&
                                            !Nascosti.contiene(a.chiave) &&
                                            dentroPrezzo(a, r.prezzoMin, r.prezzoMax)
                                }
                                val nuovi = RicercheSalvate.registraEsito(ctx, r.id, utili.map { it.chiave })
                                log(ctx, "«${r.query}» ${f.nome}: ${esito.size} annunci, $nuovi nuovi")
                                if (nuovi > 0) trovatiPerRicerca.addAll(utili.takeLast(nuovi))
                            } else {
                                log(ctx, "«${r.query}» ${f.nome}: nessuna risposta entro il tempo massimo")
                            }
                            delay(Random.nextLong(4_000, 12_000))   // pausa fra le fonti
                        }
                        // v1.27.0 — notifica qui, subito, non alla fine di TUTTE le ricerche: vedi sopra.
                        if (trovatiPerRicerca.isNotEmpty()) {
                            avvisa(ctx, r, trovatiPerRicerca)
                            log(ctx, "«${r.query}»: notificati ${trovatiPerRicerca.size} annunci")
                        }
                        delay(Random.nextLong(8_000, 20_000))       // pausa fra le ricerche
                    }
                }
            } finally {
                withContext(Dispatchers.Main) { motore.chiudi() }
                log(ctx, "fine giro")
            }
            return Result.success()
        } finally {
            inCorso.set(false)
        }
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
        // v1.27.0 — vedi il commento su doWork(): un log che resta, per sapere cosa succede davvero in background.
        private const val FILE_LOG = "controllo-log.txt"
        private const val RIGHE_LOG = 300

        /*
         * v1.33.0 (2026-09-02) — CORREZIONE, trovata rileggendo il codice
         * (nessun doppio avvio nuovo segnalato: e' un irrobustimento, non
         * la caccia a un bug ancora vivo — vedi il log gia' analizzato,
         * §"la prova sul campo" piu' sotto).
         *
         * Il "@Volatile var" della v1.31.0 rendeva VISIBILE subito a ogni
         * coroutine la scrittura fatta dall'altra, ma "leggere, poi
         * decidere, poi scrivere" (if (inCorso) return; inCorso = true) resta
         * sempre DUE passi separati, non un'operazione sola: in teoria, se
         * due Controllore.doWork() partissero a un'istruzione di differenza
         * l'uno dall'altro, potrebbero leggere ENTRAMBI "false" prima che
         * uno dei due arrivi a scrivere "true", e passare entrambi. Una
         * finestra minuscola (poche istruzioni, non secondi) — praticamente
         * mai raggiungibile dallo scheduling di WorkManager, che e' il
         * motivo per cui il log gia' visto non mostra piu' doppi dopo
         * l'installazione della v1.31.0 — ma "praticamente mai" non e'
         * "mai": AtomicBoolean.compareAndSet fa la stessa cosa (leggi,
         * decidi, scrivi) in un'unica operazione atomica vera, garantita
         * dalla CPU, non solo "molto probabile". Costa uguale, e toglie
         * l'ultimo "quasi" dalla garanzia.
         *
         * --- versione precedente (v1.31.0) ---
         * @Volatile private var inCorso = false
         * --- fine versione precedente ---
         */
        private val inCorso = java.util.concurrent.atomic.AtomicBoolean(false)

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

        /*
         * v1.27.0 (2026-09-01) — NUOVO: poche righe per ogni giro del
         * controllo automatico, con un tetto (le piu' vecchie cadono via via
         * che se ne aggiungono di nuove) cosi' il file non cresce
         * all'infinito. E' un comodo per capire cosa succede in un lavoro
         * che gira senza schermo, ore dopo che l'app e' stata chiusa — non
         * un dato dell'utente: se la scrittura fallisce (spazio esaurito,
         * per esempio) il giro prosegue lo stesso, non e' mai un motivo per
         * fermarsi.
         */
        private fun log(ctx: Context, riga: String) {
            try {
                val f = java.io.File(ctx.filesDir, FILE_LOG)
                val ts = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.ITALY)
                    .format(java.util.Date())
                val precedenti = if (f.exists()) f.readText().split("\n").filter { it.isNotBlank() } else emptyList()
                val righe = (precedenti + "$ts $riga").takeLast(RIGHE_LOG)
                f.writeText(righe.joinToString("\n") + "\n")
            } catch (t: Throwable) { /* il log e' un comodo, non un dato */ }
        }

        /** Il log del controllo automatico, per "Esporta diagnostica" (vedi MainActivity.esporta()). */
        fun leggiLog(ctx: Context): String = try {
            java.io.File(ctx.filesDir, FILE_LOG).takeIf { it.exists() }?.readText()
                ?: "Nessun giro ancora eseguito (nessuna ricerca salvata, o controlli automatici mai attivati)."
        } catch (t: Throwable) { "Log illeggibile." }
    }
}
