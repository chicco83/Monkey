package it.monkey.app

/*
 * Subito+ — schermata principale
 * v0.5.1 (2026-08-30)
 *
 * Storia breve del file, perche' spiega com'e' fatto:
 *   v0.1.0  sonda: WebView nudo piu' registro delle richieste
 *   v0.2.0  blocco pubblicita' su shouldInterceptRequest, regole in JSON
 *   v0.3.0  scarto del singolo annuncio, archivio locale
 *   v0.3.2  riduzione delle tracce lasciate nella pagina
 *   v0.4.0  ricerca unificata Subito + Vinted come schermata principale
 *   v0.4.5  script iniettati messi a dieta (latenza sui pulsanti delle schede)
 *   v0.4.6  foto in elenco: resa da elenco invece dell'originale, coda a pila
 *   v0.5.0  Compose e Material 3: tema, icona, avvio, barra dei comandi
 *   v0.5.1  interruttore degli script di pagina, risposte bloccate con il tipo giusto
 *
 * Novita' della v0.5.0:
 *  - l'attivita' e' una ComponentActivity: serve a ospitare Compose e porta
 *    con se' la gestione moderna di "indietro" e dei risultati di attivita'
 *  - la fila di otto pulsanti diventa una barra Material 3 con un menu
 *  - tolto l'interruttore dello user agent: mascherarlo peggiora la
 *    riconoscibilita' invece di ridurla (vedi documento di piano), quindi
 *    resta sempre quello di sistema e non e' piu' una scelta esposta
 *
 * --- versione precedente (fino alla v0.4.6, 2026-08-30) ---
 * class MainActivity : Activity() ... barra: LinearLayout con otto Button
 * ... private var modoUa = 0 ... private fun cambiaUa() { ... }
 * --- fine versione precedente ---
 */

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.ByteArrayInputStream

class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var prefs: SharedPreferences
    private lateinit var contenitore: FrameLayout
    private lateinit var motore: MotoreRicerca
    private lateinit var statoRicerca: StatoRicerca
    private lateinit var vistaRicerca: ComposeView

    /** Nomi generati una volta per installazione: un nome fisso sarebbe un'impronta. */
    private lateinit var nomePonte: String
    private lateinit var pref: String

    @Volatile private var ultimoReportDom: String = "{}"

    private var bloccoAttivo = true
    /*
     * v1.0.5 — deve essere stato osservabile da Compose: prima era una variabile
     * normale, e cambiandola la lista non si ridisegnava. L'interruttore
     * funzionava sul WebView e sembrava rotto sulla schermata di ricerca.
     */
    private var vistaCompatta by mutableStateOf(true)
    private var modoSonda = false
    /** v0.5.1 — spegne in blocco cio' che iniettiamo nelle pagine (vedi menu). */
    private var scriptPagina = true
    private var modoRicerca = true
    private var fonte = 0
    private var azzeraCronologia = false
    /** v1.5.0 — riferimento al gestore di "indietro", per poterlo riaccendere. */
    private var indietro: androidx.activity.OnBackPressedCallback? = null
    /*
     * v1.5.0 — vero finche' si e' sulla pagina che l'app ha aperto di proposito
     * (una scheda, la home di una fonte) e l'utente non ha ancora toccato un
     * link suo. Serve a distinguere i rimbalzi automatici del sito dalle
     * navigazioni volute: vedi azzeraCronologia in onPageFinished.
     */
    private var sullaPaginaAperta = false
    private var avanzamento: Int? = null
    /** v0.7.0 — titolo della pagina aperta, per la barra. */
    private var titoloPagina: String = ""

    private val HOME = arrayOf("https://www.subito.it/", "https://www.vinted.it/")

    private var scriptPrologo: androidx.webkit.ScriptHandler? = null
    private var scriptCosmetico: androidx.webkit.ScriptHandler? = null
    private var scriptAnnunci: androidx.webkit.ScriptHandler? = null
    private var scriptApi: androidx.webkit.ScriptHandler? = null
    private var scriptZoom: androidx.webkit.ScriptHandler? = null

    /** Stato osservato dalla barra Compose. */
    private var statoBarra by mutableStateOf(
        StatoBarra(
            modoRicerca = true, bloccoAttivo = true, vistaCompatta = false,
            modoSonda = false, fonteVinted = false, scriptPagina = true,
            nascosti = 0, ricerche = 0, nuovi = 0,
            titoloPagina = "", riga = "", avanzamento = null
        )
    )

    // ---- v0.6.0: ricerche salvate e controlli automatici ----
    private var ricercheAperte by mutableStateOf(false)
    private var elencoRicerche by mutableStateOf(emptyList<RicercaSalvata>())
    private var automatico by mutableStateOf(false)
    private var intervallo by mutableStateOf(60L)

    // v0.7.0 — permessi che servono al sito dentro il WebView
    private val permessoCamera = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> if (ok) toast("Fotocamera consentita: riprova") }

    private val permessoPosizione = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> if (ok) toast("Posizione consentita: riprova") }

    private val permessoNotifiche = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concesso ->
        if (concesso) accendiControlli()
        else toast("Senza il permesso non posso avvisarti: i controlli restano spenti")
    }

    // ------------------------------------------------------------------
    // Risultati di attivita' esterne (selettore file e apertura documenti)
    // ------------------------------------------------------------------
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val selettoreFile = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        fileCallback?.onReceiveValue(
            WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data)
        )
        fileCallback = null
    }

    private val selettoreRegole = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) importaDocumento(uri) }

    // ------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()          // v0.5.0
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("subitoplus", MODE_PRIVATE)
        bloccoAttivo = prefs.getBoolean("bloccoAttivo", true)
        vistaCompatta = prefs.getBoolean("vistaCompatta", true)
        modoSonda = prefs.getBoolean("modoSonda", false)
        scriptPagina = prefs.getBoolean("scriptPagina", true)
        nomePonte = nomeCasuale("nomePonte", "_")
        pref = nomeCasuale("prefisso", "")

        Regole.carica(this)
        Nascosti.carica(this)
        RicercheSalvate.carica(this)
        StoricoRicerche.carica(this)      // v1.1.0
        RequestLog.dettaglio = modoSonda
        automatico = prefs.getBoolean("automatico", false)
        intervallo = prefs.getLong("intervallo", 60L)
        elencoRicerche = RicercheSalvate.tutte()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // --- barra dei comandi in Compose ---
        root.addView(
            ComposeView(this).apply {
                setContent {
                    TemaSubitoPlus {
                        BarraSuperiore(statoBarra, azioni())
                        // Un dialogo apre una finestra propria: non e' costretto
                        // dentro l'altezza di questa ComposeView.
                        if (ricercheAperte) DialogoRicerche(
                            ricerche = elencoRicerche,
                            automatico = automatico,
                            intervallo = intervallo,
                            azioni = azioniRicerche(),
                            chiudi = { ricercheAperte = false }
                        )
                    }
                }
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // --- corpo: ricerca sopra, WebView sotto ---
        web = WebView(this)
        contenitore = FrameLayout(this)
        contenitore.addView(
            web,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(
            contenitore,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)

        configuraWebView()
        registraScript()

        motore = MotoreRicerca(this, nomePonte, contenitore)
        statoRicerca = StatoRicerca(this, motore) { attivo ->
            avanzamento = if (attivo) -1 else null
            aggiornaStato()
        }

        /*
         * v0.5.2 — la schermata di ricerca e' in Compose e vive nello stesso
         * contenitore del WebView: si alternano per visibilita', cosi' il
         * WebView non viene mai ricreato e la sessione non si perde.
         */
        vistaRicerca = ComposeView(this).apply {
            setContent {
                TemaSubitoPlus {
                    SchermataRicerca(
                        statoRicerca,
                        apri = { a -> apriAnnuncio(a) },
                        salva = { salvaRicerca() },
                        // v1.9.0 — preferiti e ricerche salvate sono passati in barra
                        apriUrl = { u -> apriIndirizzo(u) },
                        compatta = vistaCompatta
                    )
                }
            }
        }
        contenitore.addView(
            vistaRicerca,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        motore.prepara(FONTI)
        applicaModo()
        aggiornaStato()

        /*
         * v1.5.0 (2026-08-31) — CORREZIONE: l'app si chiudeva invece di tornare
         * alla ricerca.
         *
         * Il difetto era in una riga sola: "isEnabled = false" spegneva il
         * gestore per SEMPRE. Con android:launchMode="singleTask" e il
         * comportamento di Android 12+, uscire con "indietro" NON distrugge
         * l'activity: la manda in secondo piano e la stessa istanza torna in
         * vita alla riapertura — con il gestore ormai spento. Da quel momento
         * ogni "indietro", anche da una scheda, arriva al sistema e chiude.
         * Ecco perche' sembrava capitare a caso: capitava sempre, ma solo DOPO
         * la prima uscita.
         *
         * Ora il gestore si riaccende subito dopo aver delegato, e si riaccende
         * comunque a ogni onResume (vedi sotto): se una via nuova dovesse
         * spegnerlo, l'app se ne accorge da sola alla prossima apertura.
         *
         * --- versione precedente (v0.5.0, 2026-08-30) ---
         * onBackPressedDispatcher.addCallback(this) {
         *     when {
         *         !modoRicerca && web.canGoBack() -> web.goBack()
         *         !modoRicerca -> { modoRicerca = true; applicaModo(); aggiornaStato() }
         *         else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
         *     }
         * }
         * --- fine versione precedente ---
         */
        indietro = onBackPressedDispatcher.addCallback(this) {
            when {
                !modoRicerca && web.canGoBack() -> web.goBack()
                !modoRicerca -> { modoRicerca = true; applicaModo(); aggiornaStato() }
                // v1.8.0 — dai preferiti si torna alla schermata iniziale, non fuori
                statoRicerca.modoPreferiti -> { statoRicerca.tornaAllaRicerca(); aggiornaStato() }
                else -> {
                    // Si cede il passo al sistema per un colpo solo, poi si torna in servizio.
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        }

        gestisciIntent(intent)   // v0.6.0: arrivo da una notifica
    }

    /*
     * v1.5.0 — rete di sicurezza. Con singleTask questa istanza sopravvive
     * all'uscita, quindi si controlla a ogni ritorno che "indietro" sia ancora
     * in servizio. Costa niente e rende impossibile che il difetto torni per
     * una strada diversa da quella corretta.
     */
    override fun onResume() {
        super.onResume()
        indietro?.isEnabled = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        gestisciIntent(intent)
    }

    /** Toccare un avviso apre l'app gia' sulla ricerca che l'ha generato. */
    private fun gestisciIntent(i: Intent?) {
        i ?: return

        // v0.7.0 — link di Subito o Vinted toccato altrove: si apre qui.
        if (i.action == Intent.ACTION_VIEW) {
            val u = i.dataString
            if (!u.isNullOrBlank()) {
                fonte = if (u.contains("vinted")) 1 else 0
                modoRicerca = false
                applicaModo()
                web.stopLoading()
                azzeraCronologia = true
                sullaPaginaAperta = true          // v1.5.0
                web.loadUrl(u)
                aggiornaStato()
                return
            }
        }

        val q = i.getStringExtra("query") ?: return
        i.getStringExtra("ricerca")?.let { RicercheSalvate.segnaLetta(this, it) }
        statoRicerca.query = q
        modoRicerca = true
        applicaModo()
        statoRicerca.cerca()
        elencoRicerche = RicercheSalvate.tutte()
        aggiornaStato()
    }

    private fun azioni() = AzioniBarra(
        cambiaModo = { modoRicerca = !modoRicerca; applicaModo(); aggiornaStato() },
        cambiaBlocco = {
            bloccoAttivo = !bloccoAttivo
            prefs.edit().putBoolean("bloccoAttivo", bloccoAttivo).apply()
            registraScript(); web.reload(); aggiornaStato()
        },
        cambiaVista = {
            vistaCompatta = !vistaCompatta
            prefs.edit().putBoolean("vistaCompatta", vistaCompatta).apply()
            // La lista si ridisegna da sola; il WebView si ricarica solo se ha
            // qualcosa da ricaricare, altrimenti e' lavoro per niente.
            registraScript()
            if (!web.url.isNullOrBlank()) web.reload()
            aggiornaStato()
        },
        cambiaScriptPagina = {
            scriptPagina = !scriptPagina
            prefs.edit().putBoolean("scriptPagina", scriptPagina).apply()
            toast(
                if (scriptPagina) "Script attivi: filtri, scarto annunci, vista compatta"
                else "Script spenti: la pagina gira come nel browser. Serve a capire di chi è una lentezza"
            )
            registraScript(); web.reload(); aggiornaStato()
        },
        cambiaSonda = {
            modoSonda = !modoSonda
            prefs.edit().putBoolean("modoSonda", modoSonda).apply()
            RequestLog.dettaglio = modoSonda
            toast(if (modoSonda) "Sonda accesa: lasciala accesa solo il tempo di raccogliere"
                  else "Sonda spenta")
            registraScript(); web.reload(); aggiornaStato()
        },
        cambiaFonte = {
            fonte = if (fonte == 0) 1 else 0
            modoRicerca = false
            applicaModo()
            azzeraCronologia = true
            sullaPaginaAperta = true              // v1.5.0
            web.loadUrl(HOME[fonte])
            aggiornaStato()
        },
        apriNascosti = { mostraNascosti() },
        apriPreferiti = {                       // v1.9.0
            modoRicerca = true; applicaModo()
            statoRicerca.apriPreferiti(); aggiornaStato()
        },
        apriRicerche = { elencoRicerche = RicercheSalvate.tutte(); ricercheAperte = true },
        caricaRegole = { selettoreRegole.launch(arrayOf("*/*")) },
        salvaBackup = { salvaBackup() },
        analizza = { analizzaPagina() },
        sondaOfferte = { sondaOfferte() },   // v1.3.0
        esporta = { esporta() },
        azzera = {
            RequestLog.reset(); Regole.bloccate = 0; ApiLog.reset()
            ultimoReportDom = "{}"; aggiornaStato()
        },
        // --- v0.7.0: comandi della navigazione web ---
        ricarica = { web.reload() },
        condividi = {
            val u = web.url
            if (u.isNullOrBlank()) toast("Nessuna pagina aperta")
            else startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, titoloPagina)
                        putExtra(Intent.EXTRA_TEXT, u)
                    }, "Condividi"
                )
            )
        },
        apriNelBrowser = {
            val u = web.url
            if (u.isNullOrBlank()) toast("Nessuna pagina aperta")
            else try {
                /*
                 * Rete di sicurezza per i flussi che dentro un WebView non si
                 * completano — verifiche d'identita', portafogli di pagamento.
                 * I cookie sono condivisi con il browser di sistema, quindi la
                 * sessione regge e si torna indietro senza rifare il login.
                 */
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
            } catch (t: Throwable) { toast("Nessun browser disponibile") }
        }
    )

    private fun nomeCasuale(chiave: String, prefisso: String): String {
        prefs.getString(chiave, null)?.let { return it }
        val lettere = "abcdefghijklmnopqrstuvwxyz"
        val alfa = lettere + "0123456789"
        val n = prefisso + lettere.random() + (1..10).map { alfa.random() }.joinToString("")
        prefs.edit().putString(chiave, n).apply()
        return n
    }

    // ------------------------------------------------------------------
    // WebView
    // ------------------------------------------------------------------
    private fun configuraWebView() {
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        s.javaScriptCanOpenWindowsAutomatically = true
        s.setGeolocationEnabled(true)                       // v0.7.0
        s.setSupportMultipleWindows(true)                   // v0.7.0

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        WebView.setWebContentsDebuggingEnabled(true)

        web.addJavascriptInterface(Ponte(), nomePonte)

        web.webViewClient = object : WebViewClient() {

            /* Qui passa ogni risorsa richiesta: e' il punto in cui si blocca. */
            override fun shouldInterceptRequest(
                view: WebView?, request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                RequestLog.record(url)
                if (!bloccoAttivo) return null
                if (!Regole.blocca(url)) return null
                Regole.bloccate++
                return rispostaVuota(url)
            }

            /*
             * v0.5.1 — una risposta vuota va servita con il tipo che la pagina
             * si aspetta. Un foglio di stile o uno script consegnati come
             * "text/plain" vengono rifiutati dal browser, e c'e' codice che in
             * quel caso ritenta o resta in attesa: proprio il genere di cosa
             * che si vede come qualche secondo di ritardo su un pulsante.
             */
            private fun rispostaVuota(url: String): WebResourceResponse {
                val u = url.substringBefore('?').lowercase()
                val tipo = when {
                    u.endsWith(".js") || u.contains(".js/") -> "application/javascript"
                    u.endsWith(".css") -> "text/css"
                    u.endsWith(".gif") || u.endsWith(".png") || u.endsWith(".jpg") ||
                    u.endsWith(".jpeg") || u.endsWith(".webp") || u.endsWith(".svg") -> "image/gif"
                    u.endsWith(".json") -> "application/json"
                    else -> "text/plain"
                }
                return WebResourceResponse(tipo, "utf-8", ByteArrayInputStream(ByteArray(0)))
            }

            override fun shouldOverrideUrlLoading(view: WebView?, req: WebResourceRequest?): Boolean {
                val u = req?.url ?: return false
                /*
                 * v1.5.0 — hasGesture() e' vero solo se e' stato l'utente a
                 * toccare qualcosa. Da qui in poi la cronologia gli appartiene
                 * e non va piu' cancellata.
                 */
                if (req.hasGesture() && !req.isRedirect) sullaPaginaAperta = false
                if (u.scheme != "http" && u.scheme != "https") {
                    return try { startActivity(Intent(Intent.ACTION_VIEW, u)); true }
                    catch (t: Throwable) { true }
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (bloccoAttivo && !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
                    view?.evaluateJavascript(Cosmetici.script(Regole.cssNascondi, pref), null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                /*
                 * v1.5.0 (2026-08-31) — la cronologia si azzera per tutta la
                 * catena, non al primo arrivo.
                 *
                 * Aprendo una scheda Subito il sito rimbalza piu' volte
                 * (redirect, pagina di consenso, scheda vera). La versione
                 * precedente puliva al PRIMO onPageFinished — cioe' a meta'
                 * catena — e i passaggi successivi restavano in cronologia:
                 * "indietro" li ripercorreva invece di tornare alla ricerca.
                 * Ora si pulisce a ogni arrivo finche' l'utente non tocca un
                 * link di suo (vedi shouldOverrideUrlLoading).
                 *
                 * --- versione precedente (v0.4.1, 2026-08-30) ---
                 * if (azzeraCronologia) { azzeraCronologia = false; view?.clearHistory() }
                 * --- fine versione precedente ---
                 */
                if (azzeraCronologia && sullaPaginaAperta) view?.clearHistory()
                avanzamento = null
                aggiornaStato()
                // La sonda DOM percorre l'intero albero: parte solo se richiesta.
                if (modoSonda) view?.postDelayed({ analizzaPagina() }, 1500)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?, cb: ValueCallback<Array<Uri>>?, params: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = cb
                return try { selettoreFile.launch(params!!.createIntent()); true }
                catch (t: Throwable) { fileCallback = null; false }
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (!modoRicerca) avanzamento = if (newProgress in 1..99) newProgress else null
                aggiornaStato()
            }

            // ---- v0.7.0: parita' con le app originali ----

            override fun onReceivedTitle(view: WebView?, title: String?) {
                titoloPagina = title.orEmpty().take(60)
                aggiornaStato()
            }

            /* Fotocamera e microfono: servono per pubblicare un annuncio con foto. */
            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                request ?: return
                runOnUiThread {
                    val serve = request.resources.toList()
                    val cameraOk = checkSelfPermission(android.Manifest.permission.CAMERA) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (serve.contains(android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE) && !cameraOk) {
                        permessoCamera.launch(android.Manifest.permission.CAMERA)
                        request.deny()
                    } else request.grant(request.resources)
                }
            }

            /* Posizione: la usa la ricerca "vicino a me" del sito. */
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?, callback: android.webkit.GeolocationPermissions.Callback?
            ) {
                val ok = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                if (ok) callback?.invoke(origin, true, false)
                else {
                    callback?.invoke(origin, false, false)
                    permessoPosizione.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }

            /*
             * I link con target="_blank" chiederebbero una finestra nuova.
             * Invece di aprirne una si carica l'indirizzo qui: una finestra in
             * piu' significherebbe una sessione in piu' da gestire.
             */
            override fun onCreateWindow(
                view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?
            ): Boolean {
                val nuovo = WebView(this@MainActivity)
                nuovo.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                        r?.url?.let { web.loadUrl(it.toString()) }
                        nuovo.destroy()
                        return true
                    }
                }
                (resultMsg?.obj as? WebView.WebViewTransport)?.webView = nuovo
                resultMsg?.sendToTarget()
                return true
            }
        }

        /*
         * v0.7.0 — scaricamenti. Il WebView da solo non scarica nulla: si passa
         * al gestore di sistema, portandosi dietro i cookie della sessione,
         * altrimenti i file dietro autenticazione tornano una pagina di login.
         */
        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val richiesta = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    addRequestHeader("cookie", CookieManager.getInstance().getCookie(url) ?: "")
                    addRequestHeader("User-Agent", userAgent)
                    setNotificationVisibility(
                        android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    val nome = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                    setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, nome)
                }
                (getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(richiesta)
                toast("Scaricamento avviato")
            } catch (t: Throwable) {
                toast("Scaricamento non riuscito")
            }
        }
    }

    /** Iniezioni a document-start, nell'ordine in cui devono girare. */
    private fun registraScript() {
        scriptPrologo?.remove(); scriptPrologo = null
        scriptCosmetico?.remove(); scriptCosmetico = null
        scriptAnnunci?.remove(); scriptAnnunci = null
        scriptApi?.remove(); scriptApi = null
        scriptZoom?.remove(); scriptZoom = null
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return

        val subito = setOf("https://*.subito.it", "https://subito.it")
        val tutte = subito + setOf("https://*.vinted.it", "https://vinted.it")

        // Per primo: nasconde il ponte prima che la pagina possa vederlo.
        // Resta attivo anche a script spenti: e' l'unica cosa che tiene il ponte
        // fuori dalle proprieta' visibili di window.
        scriptPrologo = WebViewCompat.addDocumentStartJavaScript(web, Prologo.script(nomePonte), tutte)

        if (!scriptPagina) return   // v0.5.1

        // v1.0.2 — sblocca l'ingrandimento delle foto su entrambi i siti
        scriptZoom = WebViewCompat.addDocumentStartJavaScript(web, Zoom.script(pref), tutte)

        if (bloccoAttivo) scriptCosmetico = WebViewCompat.addDocumentStartJavaScript(
            web, Cosmetici.script(Regole.cssNascondi, pref), subito
        )

        // Lo scarto degli annunci funziona anche con il blocco spento.
        scriptAnnunci = WebViewCompat.addDocumentStartJavaScript(
            web,
            ScriptAnnunci.script(Regole.sezioneIniziale, Regole.cercaApreForm, vistaCompatta, nomePonte, pref),
            subito
        )

        // Sostituire fetch e XHR e' la traccia piu' riconoscibile: solo su richiesta.
        if (modoSonda)
            scriptApi = WebViewCompat.addDocumentStartJavaScript(web, ScriptApi.script(nomePonte), tutte)
    }

    // ------------------------------------------------------------------
    // Ponte JS -> Kotlin
    // ------------------------------------------------------------------
    inner class Ponte {
        @JavascriptInterface
        fun nascondi(id: String, titolo: String) {
            Nascosti.aggiungi(this@MainActivity, id, titolo)
            runOnUiThread { aggiornaStato() }
        }

        @JavascriptInterface
        fun nascostiJson(): String = Nascosti.idJson()

        @JavascriptInterface
        fun reportDom(json: String) {
            ultimoReportDom = json
            runOnUiThread { aggiornaStato() }
        }

        @JavascriptInterface
        fun reportApi(url: String, metodo: String, invio: String, stato: Int, corpo: String) {
            ApiLog.registra(url, metodo, invio, stato, corpo)
            runOnUiThread { aggiornaStato() }
        }
    }

    // ------------------------------------------------------------------
    // Schermate e comandi
    // ------------------------------------------------------------------
    private fun applicaModo() {
        vistaRicerca.visibility = if (modoRicerca) View.VISIBLE else View.GONE
        web.visibility = if (modoRicerca) View.GONE else View.VISIBLE
        if (modoRicerca) avanzamento = null
        if (!modoRicerca && web.url.isNullOrBlank()) web.loadUrl(HOME[fonte])
    }

    /** v1.8.0 — apre un indirizzo qualsiasi nella navigazione (proposte in home). */
    private fun apriIndirizzo(u: String) {
        fonte = if (u.contains("vinted")) 1 else 0
        modoRicerca = false
        applicaModo()
        web.stopLoading()
        azzeraCronologia = true
        sullaPaginaAperta = true
        web.loadUrl(u)
        aggiornaStato()
    }

    private fun apriAnnuncio(a: Annuncio) {
        fonte = if (a.fonte == "subito") 0 else 1
        modoRicerca = false
        applicaModo()
        web.stopLoading()
        azzeraCronologia = true
        sullaPaginaAperta = true                  // v1.5.0
        web.loadUrl(a.url)
        aggiornaStato()
    }

    // ------------------------------------------------------------------
    // Ricerche salvate (v0.6.0)
    // ------------------------------------------------------------------
    private fun azioniRicerche() = AzioniRicerche(
        apri = { r ->
            ricercheAperte = false
            RicercheSalvate.segnaLetta(this, r.id)
            statoRicerca.query = r.query
            statoRicerca.usaSubito = r.subito
            statoRicerca.usaVinted = r.vinted
            statoRicerca.prezzoMin = r.prezzoMin
            statoRicerca.prezzoMax = r.prezzoMax
            modoRicerca = true
            applicaModo()
            statoRicerca.cerca()
            elencoRicerche = RicercheSalvate.tutte()
            aggiornaStato()
        },
        elimina = { r ->
            RicercheSalvate.rimuovi(this, r.id)
            elencoRicerche = RicercheSalvate.tutte()
            if (elencoRicerche.isEmpty() && automatico) spegniControlli()
            aggiornaStato()
        },
        controllaOra = {
            ricercheAperte = false
            toast("Controllo avviato: gli avvisi arrivano quando ci sono novità")
            androidx.work.WorkManager.getInstance(this).enqueue(
                androidx.work.OneTimeWorkRequestBuilder<Controllore>().build()
            )
        },
        cambiaAutomatico = { acceso ->
            if (!acceso) spegniControlli()
            else if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) accendiControlli()
            else permessoNotifiche.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        },
        cambiaIntervallo = { m ->
            intervallo = m
            prefs.edit().putLong("intervallo", m).apply()
            if (automatico) Controllore.programma(this, m)
        }
    )

    private fun accendiControlli() {
        automatico = true
        prefs.edit().putBoolean("automatico", true).apply()
        Controllore.preparaCanale(this)
        Controllore.programma(this, intervallo)
    }

    private fun spegniControlli() {
        automatico = false
        prefs.edit().putBoolean("automatico", false).apply()
        Controllore.ferma(this)
    }

    /** Salva la ricerca corrente: la domanda e i filtri che contano per il controllo. */
    private fun salvaRicerca() {
        val q = statoRicerca.ultimaQuery
        if (q.isBlank()) { toast("Fai prima una ricerca"); return }
        RicercheSalvate.aggiungi(
            this, q,
            statoRicerca.usaSubito, statoRicerca.usaVinted,
            statoRicerca.prezzoMin, statoRicerca.prezzoMax,
            // Cio' che e' gia' in lista non e' una novita': si parte da qui.
            statoRicerca.risultati.map { it.chiave }
        )
        elencoRicerche = RicercheSalvate.tutte()
        aggiornaStato()
        toast("Ricerca salvata: «$q»")
        if (!automatico) {
            ricercheAperte = true
        }
    }

    private fun mostraNascosti() {
        val voci = Nascosti.elenco()
        if (voci.isEmpty()) { toast("Nessun annuncio nascosto"); return }
        val etichette = voci.map { (id, _, titolo) ->
            if (titolo.isBlank()) "annuncio $id" else titolo
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Nascosti (${voci.size}) — tocca per ripristinare")
            .setItems(etichette) { _, i ->
                Nascosti.rimuovi(this, voci[i].first)
                aggiornaStato(); web.reload()
            }
            .setNeutralButton("Ripristina tutti") { _, _ ->
                Nascosti.svuota(this); aggiornaStato(); web.reload()
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

    private fun analizzaPagina() =
        web.evaluateJavascript(DomProbe.script(Regole.cssNascondi, nomePonte), null)

    private fun aggiornaStato() {
        val j = try { org.json.JSONObject(ultimoReportDom) } catch (t: Throwable) { org.json.JSONObject() }
        val card = j.optInt("cardCandidate", -1)
        val riga = "richieste ${RequestLog.totalRequests()} · bloccate ${Regole.bloccate}" +
                (if (card >= 0) " · card $card" else "") +
                " · nascosti ${Nascosti.quanti()} · regole ${Regole.versioneRegole}"

        statoBarra = StatoBarra(
            modoRicerca = modoRicerca,
            bloccoAttivo = bloccoAttivo,
            vistaCompatta = vistaCompatta,
            modoSonda = modoSonda,
            fonteVinted = fonte == 1,
            scriptPagina = scriptPagina,
            nascosti = Nascosti.quanti(),
            ricerche = RicercheSalvate.quante(),
            nuovi = RicercheSalvate.nuoviTotali(),
            titoloPagina = titoloPagina,
            riga = riga,
            avanzamento = avanzamento
        )
    }

    // ------------------------------------------------------------------
    // Import ed export
    // ------------------------------------------------------------------
    private fun importaDocumento(uri: Uri) {
        val testo = try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (t: Throwable) { null }

        // Lo stesso comando accetta due tipi di file, distinti dalla chiave "annunci".
        when {
            testo == null -> toast("File illeggibile")
            // v1.0.0 — un backup completo ha la precedenza sugli altri formati
            testo.contains(Backup.MARCATORE) -> {
                val esito = Backup.importa(this, testo)
                toast(if (esito != null) "Ripristinati: $esito" else "Backup non valido")
                elencoRicerche = RicercheSalvate.tutte()
                registraScript(); aggiornaStato(); web.reload()
            }
            testo.contains("\"annunci\"") -> {
                val n = Nascosti.importa(this, testo)
                toast(if (n >= 0) "Importati $n annunci nascosti" else "Archivio non valido")
                aggiornaStato(); web.reload()
            }
            Regole.importa(this, testo) -> {
                registraScript(); web.reload()
                toast("Regole ${Regole.versioneRegole} attive")
                aggiornaStato()
            }
            else -> toast("File non riconosciuto")
        }
    }

    /** v1.0.0 — un solo file con tutto cio' che non si puo' ricostruire. */
    private fun salvaBackup() {
        val nome = "subitoplus-backup-" +
                java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ITALY)
                    .format(java.util.Date()) + ".json"
        val ok = scriviInDownload(nome, Backup.esporta(this))
        toast(if (ok) "Backup salvato in Download/SubitoPlus" else "Backup non riuscito")
    }

    /*
     * v1.3.0 — la caccia all'elenco delle offerte.
     * Le risposte di ricerca non lo contengono (verificato sul browser: nessun
     * campo offer/proposal/bid ne' su Subito ne' su Vinted), quindi serve una
     * seconda chiamata da incrociare per id. Quale sia, si scopre chiedendo
     * da dentro una sessione vera — questa. Solo GET.
     */
    private fun sondaOfferte() {
        toast("Sonda offerte: chiedo, una per volta…")
        SondaOfferte.esegui(
            motore,
            avanzamento = { fatte, totali ->
                if (fatte == totali) toast("Sonda offerte: $fatte risposte")
            },
            finito = {
                toast("Fatto. Ora «Esporta diagnostica» e mandami offerte.json")
            }
        )
    }

    private fun esporta() {
        val ok = listOf(
            scriviInDownload("requests.json", RequestLog.requestsJson()),
            scriviInDownload("blocklist.json", RequestLog.blocklistJson()),
            scriviInDownload("selectors.json", ultimoReportDom),
            scriviInDownload("nascosti.json", Nascosti.esportaJson()),
            scriviInDownload("api.json", ApiLog.json()),
            scriviInDownload("offerte.json", SondaOfferte.json()),   // v1.3.0
            scriviInDownload("ricerca-diagnostica.txt", motore.diagnostica.toString())
        ).all { it }
        toast(if (ok) "Esportati in Download/SubitoPlus" else "Esportazione non riuscita")
    }

    private fun scriviInDownload(nome: String, contenuto: String): Boolean = try {
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, nome)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/SubitoPlus")
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
        uri?.let {
            contentResolver.openOutputStream(it, "wt")?.use { os ->
                os.write(contenuto.toByteArray(Charsets.UTF_8))
            }
            true
        } ?: false
    } catch (t: Throwable) { false }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
}
