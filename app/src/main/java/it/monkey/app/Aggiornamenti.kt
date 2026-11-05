package it.monkey.app

/*
 * Monkey — controllo aggiornamenti
 * v1.35.0 (2026-09-02) — nuovo.
 *
 * L'app non passa dal Play Store (mai potrebbe: e' personale, distribuita
 * agli amici a mano), quindi l'aggiornamento deve trovarselo da solo.
 * GitHub Releases fa da magazzino: ogni versione consegnata diventa una
 * "release" del repository (github.com/chicco83/Monkey) con l'APK come
 * allegato, e questo file la legge con un'unica chiamata pubblica, senza
 * bisogno di account ne' di token:
 *
 *   GET https://api.github.com/repos/chicco83/Monkey/releases/latest
 *   -> {"tag_name":"v1.35.0", "body":"…note della release…",
 *       "assets":[{"name":"Monkey-1.35.0.apk",
 *                  "browser_download_url":"https://…", "size":3472555}]}
 *
 * PER FAR FUNZIONARE QUESTO controllo, a ogni consegna va anche creata la
 * release su GitHub (github.com/chicco83/Monkey/releases/new): tag "v" +
 * lo stesso versionName di app/build.gradle.kts (es. "v1.35.0"), allegato
 * l'APK firmato. Senza una release pubblicata, o senza un allegato .apk,
 * questo controllo semplicemente non trova nulla di nuovo — non e' un
 * errore, e' la release mancante.
 *
 * "tag_name" si confronta con BuildConfig.VERSION_NAME, non con
 * versionCode: GitHub non lo sa, e non serve — il progetto non salta mai un
 * numero e non torna mai indietro (vedi la disciplina di versioning in ogni
 * altro file), quindi confrontare "1.35.0" con "1.34.0" per componenti
 * numerici (non come testo: "1.9.0" contro "1.34.0" da stringa darebbe il
 * risultato sbagliato, '9' > '3') basta a sapere chi e' piu' recente.
 *
 * Il download usa DownloadManager di sistema (non una chiamata scritta a
 * mano): sopravvive se l'app va in background, mostra da solo l'avanzamento
 * nella tendina delle notifiche, e la sua "getUriForDownloadedFile" da' gia'
 * un content:// pronto per l'installazione — senza bisogno di dichiarare un
 * FileProvider apposta. L'installazione vera e propria resta sempre un
 * tocco esplicito dell'utente (mai automatica): installare un APK fuori dal
 * Play Store chiede comunque il permesso "consenti da questa fonte" la
 * prima volta, e non ha senso nascondere quel momento.
 */

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** @param versione "1.35.0" (senza la "v" iniziale del tag di GitHub). */
data class Aggiornamento(
    val versione: String,
    val note: String,
    val urlApk: String,
    val dimensioneByte: Long
)

object Aggiornamenti {

    private const val API_ULTIMA_RELEASE =
        "https://api.github.com/repos/chicco83/Monkey/releases/latest"

    /**
     * Controlla se esiste una release piu' recente di quella installata.
     * Chiama "trovato" solo se ce n'e' una, sul thread principale, pronta per
     * aggiornare lo stato di Compose. "aggiornato" (facoltativo) e' per il
     * controllo manuale dal pannello Opzioni, dove un tocco esplicito merita
     * una risposta comunque: true se il controllo e' arrivato in fondo (con
     * o senza una versione nuova — "trovato" e' gia' scattato in quel caso),
     * false se e' fallito (offline, GitHub non raggiungibile). Il controllo
     * automatico dell'avvio lo ignora apposta (resta silenzioso: un fallimento
     * di rete a ogni apertura dell'app non e' un errore da segnalare).
     */
    fun controlla(
        ctx: Context,
        trovato: (Aggiornamento) -> Unit,
        aggiornato: (Boolean) -> Unit = {}
    ) {
        Thread {
            try {
                val corpo = leggi(API_ULTIMA_RELEASE)
                val json = JSONObject(corpo)
                val tag = json.optString("tag_name")
                val versioneRemota = tag.removePrefix("v").trim()
                val note = json.optString("body").trim()
                val assets = json.optJSONArray("assets") ?: JSONArray()

                var urlApk: String? = null
                var dimensione = 0L
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        urlApk = a.optString("browser_download_url").takeIf { it.isNotBlank() }
                        dimensione = a.optLong("size")
                        break
                    }
                }

                if (urlApk != null && versioneRemota.isNotBlank() &&
                    piuRecente(versioneRemota, BuildConfig.VERSION_NAME)
                ) {
                    val agg = Aggiornamento(versioneRemota, note, urlApk, dimensione)
                    Handler(Looper.getMainLooper()).post { trovato(agg) }
                }
                Handler(Looper.getMainLooper()).post { aggiornato(true) }
            } catch (t: Throwable) {
                // Offline, GitHub non raggiungibile, repo senza release: si
                // riprova al prossimo avvio, non e' un motivo per un avviso ora
                // (il controllo automatico non passa "aggiornato" — vedi sopra).
                Handler(Looper.getMainLooper()).post { aggiornato(false) }
            }
        }.start()
    }

    /**
     * true se "remota" e' piu' recente di "attuale". Confronto per componenti
     * numerici punto per punto, non come testo — vedi il commento in cima al
     * file. Un componente mancante o non numerico vale 0, cosi' "1.35" contro
     * "1.35.0" non da' un falso "piu' recente".
     */
    private fun piuRecente(remota: String, attuale: String): Boolean {
        val r = remota.split(".").map { it.toIntOrNull() ?: 0 }
        val a = attuale.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, a.size)) {
            val x = r.getOrElse(i) { 0 }
            val y = a.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun leggi(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        // GitHub nega le richieste senza uno User-Agent riconoscibile (403).
        conn.setRequestProperty("User-Agent", "Monkey-Android-App")
        try {
            val stato = conn.responseCode
            if (stato !in 200..299) throw IOException("HTTP $stato")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Accoda il download con DownloadManager di sistema (notifica di
     * avanzamento inclusa) nella cartella Download privata dell'app — non
     * serve alcun permesso di archiviazione perche' e' "interna" all'app
     * (getExternalFilesDir), non la Download pubblica del telefono.
     * @return l'id del download, da confrontare con l'id nel broadcast
     *         DownloadManager.ACTION_DOWNLOAD_COMPLETE (vedi MainActivity).
     */
    fun scarica(ctx: Context, aggiornamento: Aggiornamento): Long {
        val richiesta = DownloadManager.Request(Uri.parse(aggiornamento.urlApk))
            .setTitle("Monkey ${aggiornamento.versione}")
            .setDescription("Aggiornamento in scarico")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, "Monkey-update.apk")
            .setMimeType("application/vnd.android.package-archive")
        val dm = ctx.getSystemService(DownloadManager::class.java)
        return dm.enqueue(richiesta)
    }

    /**
     * Avvia l'installazione di un APK gia' scaricato (uri di DownloadManager,
     * gia' un content:// valido — vedi il commento in cima al file).
     * Se l'utente non ha ancora concesso "consenti da questa fonte" per
     * Monkey, lo si porta dritto alla schermata di sistema che lo chiede
     * (Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES) invece di far fallire
     * l'installazione in silenzio: dovra' poi toccare di nuovo "Scarica e
     * installa" una volta tornato, ma almeno sa perche'.
     */
    fun installa(ctx: Context, apk: Uri) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apk, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }
}
