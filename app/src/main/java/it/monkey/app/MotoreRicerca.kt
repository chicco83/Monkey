package it.monkey.app

/*
 * Subito+ — motore della ricerca unificata
 * v0.4.0 (2026-08-30) — nuovo, Fase 3
 *
 * PERCHE' PASSA DAL WEBVIEW
 * Le due piattaforme non rispondono a un client HTTP scritto a mano: Akamai
 * davanti a Subito, protezioni analoghe davanti a Vinted. Qui la chiamata parte
 * da DENTRO una pagina della fonte, con la sua sessione e i suoi cookie, con
 * una fetch identica a quelle che fa il sito. Non sostituiamo nulla: chiamiamo.
 *
 * Ogni fonte ha un WebView proprio, tenuto fuori schermo, che ospita la sessione.
 */

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class MotoreRicerca(
    private val ctx: Context,
    private val nomePonte: String,
    /** Contenitore in cui agganciare le sessioni con dimensione 1x1:
     *  un WebView mai attaccato puo' comportarsi in modo diverso fra versioni. */
    private val ancora: android.view.ViewGroup? = null
) {
    interface Ascoltatore {
        fun risultati(fonte: String, annunci: List<Annuncio>, diagnostica: String)
        fun errore(fonte: String, messaggio: String)
    }

    private val ui = Handler(Looper.getMainLooper())
    private val sessioni = HashMap<String, WebView>()
    private val pronte = HashMap<String, Boolean>()
    private val inAttesa = HashMap<Int, Fonte>()
    /** v1.2.0 — chiamate che non sono ricerche: preferiti, profili, elenchi. */
    private val azioni = HashMap<Int, (Int, String) -> Unit>()
    private var contatore = 1
    var ascoltatore: Ascoltatore? = null

    /** Diagnostica dell'ultima ricerca: URL usati, stato, conteggi, chiavi trovate. */
    val diagnostica = StringBuilder()

    // v1.58.0 (2026-09-24) — la stima di data Vinted conserva le sue "ancore"
    // fra un avvio e l'altro: serve un Context, e questo e' il primo punto
    // dove ce n'e' uno sia nell'app sia nel controllo in background.
    init { StimaDataVinted.inizializza(ctx) }

    private fun sessione(f: Fonte): WebView {
        sessioni[f.nome]?.let { return it }
        val w = WebView(ctx)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.addJavascriptInterface(PonteRicerca(), nomePonte)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                w, Prologo.script(nomePonte),
                setOf("https://*.subito.it", "https://subito.it",
                      "https://*.vinted.it", "https://vinted.it")
            )
        }
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) { pronte[f.nome] = true }
        }
        pronte[f.nome] = false
        ancora?.addView(w, android.view.ViewGroup.LayoutParams(1, 1))
        w.loadUrl(f.origine)
        sessioni[f.nome] = w
        return w
    }

    /*
     * v1.2.0 — una chiamata qualsiasi dentro la sessione di una fonte.
     * Stessa tecnica della ricerca: parte da dentro la pagina, con i cookie
     * dell'utente. E' cosi' che si mette un preferito senza che la richiesta
     * si distingua da quella che farebbe il sito.
     */
    /*
     * v1.16.0 (2026-08-31) — CORREZIONE: il preferito su Vinted tornava a dare
     * 403 "access_denied" sia in aggiunta che in rimozione, con "hdr:2" (le
     * due intestazioni della v1.11.0) confermate presenti nella diagnostica.
     * Quindi non erano le intestazioni mancanti: la sessione WebView di Vinted
     * si crea UNA VOLTA all'avvio (sessione(f) -> w.loadUrl la prima volta) e
     * resta viva in background per tutta la vita dell'app. Il token CSRF che
     * l'app legge lo prende ogni volta dal DOM di quella pagina (document.scripts),
     * corretto — ma quel DOM e' quello caricato all'avvio: se nel frattempo Vinted
     * pubblica una nuova versione del sito (il token e' legato alla build, vedi
     * v1.11.0), il valore incorporato nella pagina vecchia non e' piu' quello che
     * il server si aspetta. Il sintomo torna: stesso errore su add E rimozione,
     * perche' la verifica del token non distingue le due direzioni.
     *
     * Rimedio: quando una chiamata con csrf=true torna 403, si ricarica UNA
     * VOLTA la pagina della fonte (una pagina fresca porta il token attuale) e
     * si riprova la stessa richiesta. Se fallisce ancora dopo il ricaricamento,
     * il messaggio d'errore che arriva a schermo porta il prefisso "[ricaricato]":
     * dice a chi legge il prossimo avviso che il tentativo di auto-correzione
     * e' gia' scattato e non ha bastato — segno che la causa e' un'altra (per
     * esempio una protezione anti-bot sul sito, non piu' un token vecchio).
     */
    /*
     * v1.16.0 — "tentativoRicarica" sta PRIMA di "esito" e non dopo, apposta:
     * "esito" deve restare l'ULTIMO parametro, cosi' le chiamate gia' scritte
     * come motore.azione(f, c) { stato, corpo -> ... } (lambda finale fuori
     * dalle parentesi) continuano a combaciare con "esito" invece che con
     * "tentativoRicarica" — a chi chiama da fuori questo parametro resta
     * invisibile, avendo un valore di default. Stessa ragione per
     * "tentativoAttesa" aggiunto in v1.26.0, sotto: anche lui prima di "esito".
     */
    /*
     * v1.26.0 (2026-09-01) — NUOVO: se la chiamata porta "paginaPrima" (i
     * preferiti Vinted, da questa versione — vedi Fonti.kt), la sessione ci
     * naviga DAVVERO prima di provare la chiamata, invece di restare ferma
     * sulla pagina caricata una volta all'avvio (sessione(), sopra). E' la
     * stessa sequenza — apri l'articolo, poi tocca il cuore — catturata
     * funzionante dalla sonda dell'utente su una vera pagina Vinted; finora
     * la sessione nascosta non la faceva mai, restava sulla home caricata
     * al primo avvio per tutta la vita del processo.
     *
     * "w.url != c.paginaPrima" evita di ricaricare a ogni tentativo mentre si
     * aspetta che la pagina finisca (i tentativi successivi la trovano gia'
     * uguale e si limitano ad aspettare "pronte"); il ricaricamento sul 403
     * (piu' sotto) resta esplicito e incondizionato, perche' li' la pagina
     * serve fresca DAVVERO, non solo "gia' quella giusta".
     *
     * "tentativoAttesa" e' un limite nuovo, che prima non c'era: aspettare
     * "pronte" non aveva mai un tetto (a differenza di avvia(), che si arrende
     * dopo 40 tentativi). Con la sola home, caricata una volta sola all'avvio
     * dell'app, non si notava mai. Una pagina di articolo vera — decine di
     * host, un centinaio di richieste, vedi la sonda mandata in chat — puo'
     * impiegare piu' a caricare o non finire mai (rete assente, per esempio):
     * meglio un errore chiaro a chi aspetta che un ciclo che non si ferma.
     */
    fun azione(
        f: Fonte, c: Chiamata,
        tentativoRicarica: Boolean = false,
        tentativoAttesa: Int = 0,
        esito: (stato: Int, corpo: String) -> Unit
    ) {
        val w = sessione(f)
        if (c.paginaPrima != null && w.url != c.paginaPrima) {
            pronte[f.nome] = false
            w.loadUrl(c.paginaPrima)
        }
        if (pronte[f.nome] != true) {
            if (tentativoAttesa > 50) { esito(-4, "sessione non pronta"); return }
            ui.postDelayed({ azione(f, c, tentativoRicarica, tentativoAttesa + 1, esito) }, 400)
            return
        }
        val id = contatore++

        /*
         * v1.6.0 — il ripiego. Se la prima strada non risponde 2xx si prova la
         * seconda, e all'esterno arriva un esito solo: chi chiama non deve
         * sapere quale indirizzo ha funzionato, deve sapere se ha funzionato.
         *
         * --- versione precedente (fino alla v1.15.0) ---
         * azioni[id] = { stato, corpo ->
         *     if (stato !in 200..299 && c.ripiego != null) {
         *         diagnostica.append("[${f.nome}] ${c.url} -> $stato, provo il ripiego\n")
         *         azione(f, c.ripiego, esito)
         *     } else esito(stato, corpo)
         * }
         * --- fine versione precedente ---
         */
        /*
         * v1.19.0 (2026-08-31) — DIAGNOSTICA, non correzione: prima di un
         * altro tentativo "alla cieca" sul 403 di Vinted (dopo che il
         * ricaricamento della v1.16.0 e' risultato inefficace) si raccoglie
         * la prova che finora manca. Il testo che arriva da JS puo' ora
         * portare, davanti al corpo vero, un blocco diagnostico separato da
         * un carattere di controllo (\u0001, non compare mai in un JSON ne'
         * in un messaggio d'errore leggibile): intestazioni vere della
         * risposta e contatore delle chiamate anti-bot ".../j3r4zw/..." viste
         * in QUESTA sessione (vedi Prologo.kt v0.4.0 e il js piu' sotto).
         * Quel blocco va SOLO nel log "diagnostica" (quello che la sonda
         * esporta) — MAI nel testo passato a "esito", che resta corto come
         * prima e finisce nel messaggio mostrato a schermo.
         *
         * --- versione precedente (fino alla v1.18.0) ---
         * azioni[id] = { stato, corpo ->
         *     if (stato !in 200..299 && c.ripiego != null) {
         *         diagnostica.append("[${f.nome}] ${c.url} -> $stato, provo il ripiego\n")
         *         azione(f, c.ripiego, esito = esito)
         *     } else if (stato == 403 && c.csrf && !tentativoRicarica) {
         *         diagnostica.append("[${f.nome}] ${c.url} -> 403, ricarico la pagina e riprovo una volta\n")
         *         pronte[f.nome] = false
         *         w.loadUrl(f.origine)
         *         azione(f, c, tentativoRicarica = true, esito = esito)
         *     } else {
         *         val corpoFinale = if (tentativoRicarica) "[ricaricato] $corpo" else corpo
         *         esito(stato, corpoFinale)
         *     }
         * }
         * --- fine versione precedente ---
         */
        azioni[id] = { stato, corpoConDiagnostica ->
            val sep = corpoConDiagnostica.indexOf('\u0001')
            if (sep >= 0) diagnostica.append(corpoConDiagnostica.substring(0, sep)).append('\n')
            val corpo = if (sep >= 0) corpoConDiagnostica.substring(sep + 1) else corpoConDiagnostica

            if (stato !in 200..299 && c.ripiego != null) {
                diagnostica.append("[${f.nome}] ${c.url} -> $stato, provo il ripiego\n")
                azione(f, c.ripiego, esito = esito)
            } else if (stato == 403 && c.csrf && !tentativoRicarica) {
                /*
                 * v1.26.0 — se la chiamata ha una sua paginaPrima (i preferiti
                 * Vinted, vedi sopra) il ricaricamento la rispetta invece di
                 * tornare sempre alla home: e' quella pagina, non la home,
                 * quella la cui sessione conta per questa chiamata.
                 *
                 * --- versione precedente (fino alla v1.25.0) ---
                 * w.loadUrl(f.origine)
                 * --- fine versione precedente ---
                 */
                // v1.16.0 — possibile token CSRF invecchiato: pagina fresca e un solo ritentativo.
                diagnostica.append("[${f.nome}] ${c.url} -> 403, ricarico la pagina e riprovo una volta\n")
                pronte[f.nome] = false
                w.loadUrl(c.paginaPrima ?: f.origine)
                azione(f, c, tentativoRicarica = true, esito = esito)
            } else {
                val corpoFinale = if (tentativoRicarica) "[ricaricato] $corpo" else corpo
                esito(stato, corpoFinale)
            }
        }

        val corpoJs = if (c.corpo == null) "undefined"
                      else "'" + c.corpo.replace("\\", "\\\\").replace("'", "\\'") + "'"

        /*
         * v1.6.0 — intestazioni anti-CSRF raccolte NELLA PAGINA.
         *
         * Vinted risponde 403 "access_denied" a una POST che porta i soli
         * cookie: la sessione la identifica, ma non basta a farla scrivere.
         * Il token non ha una posizione unica e cambia con le versioni del
         * sito, quindi non lo si indovina: lo si cerca dove puo' stare — meta,
         * cookie, storage, e in ultima istanza il sorgente della pagina — e si
         * usa il primo che si trova. Se non c'e', si chiama lo stesso: meglio
         * un 403 con una diagnosi che nessuna chiamata.
         */
        /*
         * v1.7.0 (2026-08-31) — CORREZIONE: il token c'era, lo cercavo male.
         *
         * La v1.6.0 riportava "csrf:0", cioe' non trovato, e la POST restava 403.
         * Guardando la pagina vera di Vinted il token c'e' eccome, una volta
         * sola in 7,9 MB di sorgente, dentro il flight di Next.js:
         *
         *   \"CSRF_TOKEN\":\"75f6c9fa-dc8e-4e52-a000-e09dd4084b3e\"
         *
         * Due errori sovrapposti nella versione precedente:
         *  1. le virgolette sono SFUGGITE (\" e non "), e il modello cercava
         *     ["'] subito prima del valore: non poteva combaciare;
         *  2. guardavo i primi 400.000 caratteri di un documento da 7,9 milioni,
         *     e l'unica occorrenza sta ben oltre.
         *
         * Ora si scorrono i soli <script> — dove il flight vive — dall'ultimo al
         * primo, saltando quelli che non contengono "CSRF": 18 ms misurati sulla
         * pagina vera, contro il costo di leggere l'intero innerHTML.
         *
         * --- versione precedente (v1.6.0, 2026-08-31) ---
         * var m = document.querySelector('meta[name="csrf-token"],…');
         * if (!t) { var ck = /(?:^|;\s*)(?:_csrf_token|csrf_token|…)=([^;]+)/i.exec(document.cookie); … }
         * if (!t) { …localStorage… }
         * if (!t) { var h = document.documentElement.innerHTML.slice(0, 400000);
         *           var m2 = /csrf[-_]?token["']?\s*[:=]\s*["']([^"']{16,})["']/i.exec(h); … }
         * --- fine versione precedente ---
         */
        /*
         * v1.11.0 (2026-08-31) — CORREZIONE: il token era giusto, mancavano due intestazioni.
         *
         * Il preferito si toglieva su Subito ma non su Vinted: stesso 403
         * "access_denied", stesso token trovato (csrf:36). Il sospetto era che
         * il token fosse sbagliato — non lo era. Letto il bundle vero del sito
         * (marketplace-web-assets.vinted.com, i file .js sotto /_next/static/chunks,
         * gli stessi che il browser scarica per mostrare la pagina) si vede il
         * client che parla con l'API — ApiGatewayClient — e cosa mette in ogni chiamata:
         *
         *   headers: { [PLATFORM_HEADER_KEY]: "web", [X_NEXT_APP]: "marketplace-web" }
         *   csrfTokenInterceptor  -> headers.set("X-CSRF-Token", "75f6c9fa-…")   (letterale nel bundle!)
         *   anonIdInterceptor     -> headers.set("X-Anon-Id", cookie ANON_ID_COOKIE)
         *
         * PLATFORM_HEADER_KEY vale "Platform" (senza X-, verificato nello stesso
         * bundle: "PLATFORM_HEADER_KEY",0,"Platform"). Il token CSRF stesso e'
         * risultato IDENTICO, lettera per lettera, a quello scritto a mano nel
         * codice del sito: e' un segreto legato alla build (RELEASE_VERSION),
         * uguale per tutti in quel momento, non per sessione — la nostra
         * estrazione era gia' corretta. Mancavano solo "Platform: web" e
         * "X-Next-App: marketplace-web", che l'app non mandava affatto.
         *
         * --- versione precedente (v1.7.0, 2026-08-31) ---
         * (uguale a sotto, ma senza le due righe opz.headers['Platform'] / ['X-Next-App'],
         *  e senza 'hdr:2' nella marca diagnostica)
         * --- fine versione precedente ---
         */
        val csrfJs = if (!c.csrf) "" else """
          try {
            var t = '';
            // Il modello accetta la virgoletta sfuggita: \?["'] prima del valore.
            var re = /CSRF_TOKEN\\?["']?\s*:\s*\\?["']([^"'\\\s]{8,})/i;
            var sc = document.scripts;
            for (var i = sc.length - 1; i >= 0 && !t; i--) {
              var tx = sc[i].textContent;
              if (!tx || tx.indexOf('CSRF') < 0) continue;
              var m = re.exec(tx); if (m) t = m[1];
            }
            // Ripiego: il buffer di Next, se il token e' arrivato dopo il caricamento.
            if (!t) { try { var f = (window.__next_f || []).map(function(x){ return (x && x[1]) || ''; }).join('');
                            var m2 = re.exec(f); if (m2) t = m2[1]; } catch (e) {} }
            // Ultimo ripiego: le vie classiche, se un giorno tornassero.
            if (!t) { var mm = document.querySelector('meta[name="csrf-token"],meta[name="csrf_token"]');
                      if (mm && mm.content) t = mm.content; }
            if (!t) { var ck = /(?:^|;\s*)(?:_csrf_token|csrf_token|XSRF-TOKEN)=([^;]+)/i.exec(document.cookie);
                      if (ck) t = decodeURIComponent(ck[1]); }
            if (t) { opz.headers['X-CSRF-Token'] = t; opz.headers['X-Csrf-Token'] = t; }
            var an = /(?:^|;\s*)anon_id=([^;]+)/.exec(document.cookie);
            if (an) opz.headers['X-Anon-Id'] = decodeURIComponent(an[1]);
            // v1.11.0 — le due intestazioni che mancavano, lette dal bundle vero del sito.
            opz.headers['Platform'] = 'web';
            opz.headers['X-Next-App'] = 'marketplace-web';
            // v1.16.0 — Vinted usa Datadome (anti-bot) sulle scritture: il cookie
            // "datadome" c'e' o no, e' un indizio in piu' se il 403 torna anche
            // dopo il ricaricamento (vedi commento su azione(), sopra).
            var dd = /(?:^|;\s*)datadome=([^;]+)/.exec(document.cookie);
            marca = 'csrf:' + (t ? t.length : 0) + ' anon:' + (an ? 1 : 0) + ' hdr:2' + ' dd:' + (dd ? 1 : 0);
          } catch (e) { marca = 'csrf:errore'; }
        """

        /*
         * v1.19.0 (2026-08-31) — cattura, SOLO per le chiamate con csrf=true
         * (i preferiti), le intestazioni vere della risposta e il contatore
         * di telemetria anti-bot di Prologo.kt (window.__mkTel). Va in
         * "extra", mai in "marca": "marca" resta il riassunto corto che finisce
         * nel messaggio a schermo, "extra" e' il blocco lungo che la funzione
         * chiamante (sopra) smista SOLO in "diagnostica". Se qualcosa va storto
         * qui dentro (per esempio le intestazioni non leggibili) non deve MAI
         * far fallire la chiamata vera: e' tutto avvolto in try/catch a parte.
         */
        val catturaDiagnostica = if (!c.csrf) "" else """
                try {
                  var hs = [];
                  r.headers.forEach(function(v,k){ hs.push(k + '=' + v); });
                  var tel = window.__mkTel || { j3r4zw: 0, ultimoStato: null };
                  extra = 'DIAG [${f.nome}] ${c.url} stato=' + r.status +
                          ' intestazioni=[' + hs.join('|') + ']' +
                          ' telemetriaAntiBot(j3r4zw)=' + tel.j3r4zw +
                          ' ultimoStatoTelemetria=' + tel.ultimoStato;
                } catch (eDiag) { extra = 'DIAG errore=' + String(eDiag); }
        """

        val js = """
        (function(){
          var marca = '';
          var extra = '';
          var opz = {
            method: '${c.metodo}',
            credentials: 'include',
            headers: {'Accept':'application/json','Content-Type':'application/json'}
          };
          $csrfJs
          var corpo = $corpoJs;
          if (corpo !== undefined) opz.body = corpo;
          function consegna(s, t){
            try {
              var testo = marca ? '[' + marca + '] ' + t : t;
              window['$nomePonte'].risultatoRicerca($id, s, (extra ? extra + '\u0001' : '') + testo);
            } catch(e){}
          }
          try {
            fetch('${c.url.replace("'", "\'")}', opz)
              .then(function(r){
                $catturaDiagnostica
                return r.text().then(function(t){ consegna(r.status, t); });
              })
              .catch(function(e){ consegna(-1, String(e)); });
          } catch(e) { consegna(-2, String(e)); }
        })();
        """.trimIndent()
        w.evaluateJavascript(js, null)

        ui.postDelayed({
            if (azioni.remove(id) != null) esito(-3, "nessuna risposta")
        }, 20000)
    }

    /*
     * v1.61.0 (2026-09-24) — SCARICAMENTO PARZIALE di una pagina HTML.
     *
     * La diagnostica della v1.60.0 ha misurato la pagina di un annuncio
     * Vinted: 2 015 274 caratteri, scaricata INTERA per ogni riga scorsa
     * solo per leggerne "Caricato" e la descrizione. Qui la pagina si legge
     * a pezzi mentre arriva (response.body.getReader(), stessa fetch dalla
     * sessione classica, nessuna navigazione — vedi la lezione v1.50/v1.51)
     * e il download si INTERROMPE (reader.cancel()) appena ci sono le due
     * cose che servono:
     *  - "Caricato" FUORI da un blocco <script> (dentro gli script ci sono i
     *    dizionari di traduzione, vedi DataCaricamentoVinted.leggiHtml), con
     *    ~800 caratteri dopo, quanti ne guarda il lettore;
     *  - il blocco JSON-LD (application/ld+json) gia' chiuso, da cui viene
     *    la descrizione.
     * Tetti di sicurezza, non misurati ma prudenti, perche' la POSIZIONE
     * dei due pezzi nella pagina non si conosce ancora:
     *  - trovato "Caricato" ma non il JSON-LD: si continua al massimo altri
     *    300 000 caratteri, poi ci si ferma (si perde la descrizione, non la
     *    data);
     *  - non trovato nulla: ci si ferma a 1 200 000 caratteri.
     * "info" (terzo parametro di esito) porta quanti caratteri si sono letti,
     * se si e' interrotto e a che punto stavano i due pezzi: finisce nella
     * diagnostica, cosi' i tetti si possono stringere con i numeri veri.
     */
    fun paginaParziale(
        f: Fonte, url: String,
        tentativoAttesa: Int = 0,
        esito: (stato: Int, html: String, info: String) -> Unit
    ) {
        val w = sessione(f)
        if (pronte[f.nome] != true) {
            if (tentativoAttesa > 50) { esito(-4, "", "sessione non pronta"); return }
            ui.postDelayed({ paginaParziale(f, url, tentativoAttesa + 1, esito) }, 400)
            return
        }
        val id = contatore++
        azioni[id] = { stato, corpo ->
            // formato: "<info>\u0001<html>"
            val sep = corpo.indexOf('\u0001')
            if (sep >= 0) esito(stato, corpo.substring(sep + 1), corpo.substring(0, sep))
            else esito(stato, corpo, "")
        }
        val js = """
        (function(){
          // v1.62.0 — LIMITE da 1 200 000 a 4 000 000: la diagnostica v1.61.0
          // ha mostrato che a 1,2 M caratteri "Caricato" visibile non e' ancora
          // arrivato (in v1.60.0, pagina intera da 2 M, era stato trovato):
          // sta nella parte FINALE della pagina, dopo il grande dizionario
          // delle traduzioni. Ci si ferma comunque subito dopo averlo trovato.
          // --- versione precedente: var LIMITE = 1200000, DOPO_CARICATO = 300000, FINESTRA = 800;
          var LIMITE = 4000000, DOPO_CARICATO = 300000, FINESTRA = 800;
          var cercatoFino = 0;
          function consegna(s, info, t){
            try { window['$nomePonte'].risultatoRicerca($id, s, info + '\u0001' + t); } catch(e){}
          }
          function fuoriScript(t, i){
            var a = t.lastIndexOf('<script', i), b = t.lastIndexOf('</script', i);
            return a < 0 || b > a;
          }
          // v1.62.0 — riparte da dove era arrivata la ricerca precedente (meno
          // lavoro a ogni pezzo su una pagina da milioni di caratteri).
          // --- versione precedente: stessa funzione, sempre dall'inizio ---
          function posCaricato(t){
            var re = /Caricato|Uploaded/g, m;
            re.lastIndex = Math.max(0, cercatoFino - 20);
            while ((m = re.exec(t)) !== null) { if (fuoriScript(t, m.index)) return m.index; }
            cercatoFino = t.length;
            return -1;
          }
          function posLd(t){
            var i = t.indexOf('application/ld+json');
            if (i < 0) return -1;
            return t.indexOf('</script>', i) >= 0 ? i : -1;
          }
          try {
            fetch('${url.replace("'", "\\'")}', { credentials: 'include' })
              .then(function(r){
                if (!r.body || !r.body.getReader) {
                  return r.text().then(function(t){ consegna(r.status, 'letti=' + t.length + ' interrotto=no (niente lettura a pezzi)', t); });
                }
                var reader = r.body.getReader(), dec = new TextDecoder(), t = '';
                var pc = -1, pl = -1;
                function passo(){
                  return reader.read().then(function(x){
                    if (x.value) t += dec.decode(x.value, { stream: true });
                    if (pc < 0) pc = posCaricato(t);
                    if (pl < 0) pl = posLd(t);
                    var basta =
                      (pc >= 0 && t.length >= pc + FINESTRA && pl >= 0) ||
                      (pc >= 0 && t.length >= pc + DOPO_CARICATO) ||
                      t.length >= LIMITE;
                    var info = function(interrotto){
                      return 'letti=' + t.length + ' interrotto=' + interrotto +
                             ' caricato@' + pc + ' jsonld@' + pl;
                    };
                    if (x.done) { consegna(r.status, info('no (pagina finita)'), t); return; }
                    if (basta) {
                      try { reader.cancel(); } catch (e) {}
                      consegna(r.status, info('si'), t);
                      return;
                    }
                    return passo();
                  });
                }
                return passo();
              })
              .catch(function(e){ consegna(-1, 'errore', String(e)); });
          } catch(e) { consegna(-2, 'errore', String(e)); }
        })();
        """.trimIndent()
        w.evaluateJavascript(js, null)
        ui.postDelayed({
            if (azioni.remove(id) != null) esito(-3, "", "nessuna risposta entro 20 s")
        }, 20000)
    }

    /** v1.2.0 — legge un valore dalla pagina della fonte (identita', stato). */
    fun leggiDallaPagina(f: Fonte, espressione: String, esito: (String) -> Unit) {
        val w = sessione(f)
        if (pronte[f.nome] != true) {
            ui.postDelayed({ leggiDallaPagina(f, espressione, esito) }, 500)
            return
        }
        w.evaluateJavascript(espressione) { v -> ui.post { esito(v ?: "null") } }
    }

    /** Scalda le sessioni senza cercare nulla: rende immediata la prima ricerca. */
    fun prepara(fonti: List<Fonte>) = fonti.forEach { sessione(it) }

    fun cerca(q: String, pagina: Int, ordine: String, fonti: List<Fonte>) {
        if (pagina == 1) diagnostica.setLength(0)
        fonti.forEach { f -> avvia(f, q, pagina, ordine, 0) }
    }

    /*
     * v1.56.0 (2026-09-16) — se la fonte non fa piu' fetch (usaPaginaDom,
     * vedi Fonti.kt: solo Vinted, il suo catalogo JSON e' stato ritirato),
     * si passa alla strada nuova invece di provare a fare fetch su un
     * indirizzo che ormai e' quello di una PAGINA, non di un'API.
     */
    private fun avvia(f: Fonte, q: String, pagina: Int, ordine: String, tentativo: Int) {
        if (f.usaPaginaDom) { avviaDom(f, q, pagina, ordine, tentativo); return }

        val w = sessione(f)
        if (pronte[f.nome] != true) {
            // La pagina della fonte non e' ancora caricata: si riprova a breve.
            if (tentativo > 40) { ascoltatore?.errore(f.nome, "sessione non pronta"); return }
            ui.postDelayed({ avvia(f, q, pagina, ordine, tentativo + 1) }, 400)
            return
        }
        val id = contatore++
        inAttesa[id] = f
        val url = f.url(q, pagina, ordine)
        diagnostica.append("[${f.nome}] $url\n")

        /*
         * Due tentativi. Il primo con i cookie ("include"): serve alle risposte
         * personalizzate. Se la fonte espone CORS con Allow-Origin generico,
         * quel tentativo viene rifiutato dal browser, e il secondo — senza
         * credenziali — passa. Meglio due tentativi che una regola sbagliata.
         */
        val js = """
        (function(){
          var U = '${url.replace("'", "\\'")}';
          var H = {'Accept':'application/json'};
          function consegna(s, t){ try { window['$nomePonte'].risultatoRicerca($id, s, t); } catch(e){} }
          function prova(opz, ripiego){
            try {
              fetch(U, opz)
                .then(function(r){ return r.text().then(function(t){ consegna(r.status, t); }); })
                .catch(function(e){ if (ripiego) ripiego(); else consegna(-1, String(e)); });
            } catch(e) { if (ripiego) ripiego(); else consegna(-2, String(e)); }
          }
          prova({credentials:'include', headers:H}, function(){ prova({headers:H}, null); });
        })();
        """.trimIndent()
        w.evaluateJavascript(js, null)

        ui.postDelayed({
            if (inAttesa.remove(id) != null) ascoltatore?.errore(f.nome, "nessuna risposta entro 20 s")
        }, 20000)
    }

    /*
     * v1.56.0 (2026-09-16) — la ricerca per le fonti con usaPaginaDom=true
     * (solo Vinted): niente fetch, si NAVIGA per davvero f.url(...) — che
     * per queste fonti e' l'indirizzo intero della pagina di ricerca, non
     * un percorso relativo — e si legge il DOM dopo che ha finito di
     * comparire, invece di interpretare una risposta di rete.
     *
     * SESSIONE DEDICATA, MAI quella di azione()/sessione(): e' la lezione
     * pagata con la regressione v1.50.0 -> v1.51.0. Li' si era fatta
     * navigare la sessione CONDIVISA (quella usata anche da preferiti e
     * traduzione) verso la pagina di un annuncio prima di ogni chiamata di
     * traduzione, e la ricerca ancora in corso sulla STESSA sessione ne
     * usciva inaffidabile — sparito il testo, non solo la traduzione.
     * Qui la sessione di ricerca stessa DEVE navigare (non c'e' modo di
     * leggere l'elenco senza farlo, ora che e' HTML vero) — la si isola
     * quindi in una sessione tutta sua (sessioniDom/pronteDom, sotto),
     * cosi' che navigarla per la pagina 2, 3... non intacchi mai una
     * traduzione o un preferito in corso sulla sessione classica, e
     * viceversa.
     *
     * TEMPO DI ATTESA DOPO IL CARICAMENTO: onPageFinished (pronteDom=true)
     * non basta da solo — le sonde che hanno trovato questo formato
     * (v1.54.0, v1.55.0) leggevano il DOM con un ritardo dopo quel momento,
     * mai a caricamento appena finito: il contenuto compare quando la
     * pagina si idrata, un istante dopo che il browser la considera
     * "finita". 1800 ms e' lo stesso ordine di grandezza usato li' (le
     * sonde ne hanno sempre trovati 96, mai zero, con un ritardo simile) —
     * non e' stato misurato un minimo esatto, e' un valore prudente.
     *
     * PAGINAZIONE (pagina 2 in avanti): "page=$pagina" nell'indirizzo NON
     * e' stato ancora confermato da nessuna sonda — nessuna ha mai chiesto
     * una pagina diversa dalla prima. Se Vinted lo ignorasse e restituisse
     * sempre la pagina 1, il sintomo sarebbe innocuo: gli annunci arrivano
     * gia' visti (stesso id), StatoRicerca li scarta come doppioni (vedi
     * "gia.add" in StatoRicerca.kt) e la fonte viene segnata esaurita nello
     * stesso identico modo in cui lo sarebbe se avesse davvero finito gli
     * annunci — nessun crash, nessun duplicato visibile, solo una lista che
     * smette di crescere un po' prima del dovuto finche' non si conferma
     * (o corregge) sul campo, esattamente come gia' successo in passato per
     * ANNUNCI_PER_PAGINA_VINTED (v1.41.0 -> v1.44.0).
     *
     * v1.57.0 (2026-09-16) — CORREZIONE su un problema diverso, trovato
     * dall'utente PRIMA che si potesse verificare quanto sopra: la stima di
     * data per gli annunci Vinted (FonteVinted.leggiDaPaginaDom(), vedi il
     * commento li') ripartiva da "ora" a OGNI pagina, quindi anche la
     * pagina 2, 3... restava sempre "praticamente adesso" — mai abbastanza
     * vecchia da comparire scorrendo verso il passato insieme alle date
     * VERE di Subito. Corretto passando "pagina" dentro la risposta stessa
     * (vedi "risposta = {pagina, annunci}" piu' sotto), cosi' la stima puo'
     * continuare a scendere pagina dopo pagina invece di ripartire ogni
     * volta da capo. Resta, dichiaratamente, solo una stima ordinale — vedi
     * il commento su leggiDaPaginaDom() per il limite onesto che non risolve.
     */
    private val sessioniDom = HashMap<String, WebView>()
    private val pronteDom = HashMap<String, Boolean>()

    private fun sessioneDom(f: Fonte): WebView {
        sessioniDom[f.nome]?.let { return it }
        val w = WebView(ctx)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.addJavascriptInterface(PonteRicerca(), nomePonte)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                w, Prologo.script(nomePonte),
                setOf("https://*.vinted.it", "https://vinted.it")
            )
        }
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) { pronteDom[f.nome] = true }
        }
        pronteDom[f.nome] = false
        ancora?.addView(w, android.view.ViewGroup.LayoutParams(1, 1))
        // niente loadUrl(f.origine) qui: a differenza di sessione(), questa
        // sessione naviga SOLO verso l'indirizzo di ricerca vero e proprio,
        // deciso da avviaDom() qui sotto, mai verso una pagina "di parcheggio".
        sessioniDom[f.nome] = w
        return w
    }

    private fun avviaDom(f: Fonte, q: String, pagina: Int, ordine: String, tentativo: Int) {
        val w = sessioneDom(f)
        val url = f.url(q, pagina, ordine)
        if (w.url != url) {
            pronteDom[f.nome] = false
            w.loadUrl(url)
        }
        if (pronteDom[f.nome] != true) {
            if (tentativo > 40) { ascoltatore?.errore(f.nome, "sessione non pronta"); return }
            ui.postDelayed({ avviaDom(f, q, pagina, ordine, tentativo + 1) }, 400)
            return
        }
        val id = contatore++
        inAttesa[id] = f
        diagnostica.append("[${f.nome}] (pagina) $url\n")

        /*
         * v1.58.0 (2026-09-24) — ANCORA PER LA STIMA DI DATA (vedi
         * StimaDataVinted.kt): al massimo ogni 20 minuti, insieme alla
         * lettura della pagina, si scarica con fetch — SENZA navigare, la
         * sessione resta sulla pagina di ricerca — l'elenco dei piu' recenti
         * di tutto il catalogo, e se ne prende l'id piu' alto: un annuncio
         * appena pubblicato, cioe' la coppia (id, adesso) che ancora la
         * stima. Tempo massimo 6 s: se non arriva, la pagina di ricerca
         * viene consegnata lo stesso, senza ancora, col motivo scritto in
         * "ancoraErr" (finisce nella diagnostica esportata). NON ancora
         * verificato sul campo che l'HTML scaricato cosi' contenga le card
         * come quello navigato: se non le contiene, la diagnostica lo dice
         * ("nessun product-item-id") invece di un'ancora sbagliata.
         */
        val chiediAncora = StimaDataVinted.serveAncora()

        // il contenuto vero compare dopo onPageFinished, non insieme: vedi
        // il commento qui sopra sul perche' di questo ritardo.
        ui.postDelayed({
            /*
             * --- versione precedente (v1.57.0), coda dello script ---
             *     var risposta = { pagina: $pagina, annunci: out };
             *     window['$nomePonte'].risultatoRicerca($id, 200, JSON.stringify(risposta));
             *   } catch (e) {
             *     try { window['$nomePonte'].risultatoRicerca($id, -5, String(e)); } catch (e2) {}
             *   }
             * --- fine versione precedente ---
             */
            val js = """
            (function(){
              try {
                var nodi = document.querySelectorAll('[data-testid]');
                var out = [];
                for (var i = 0; i < nodi.length; i++) {
                  var td = nodi[i].getAttribute('data-testid') || '';
                  // il contenitore vero e' "product-item-id-<id>" senza "--":
                  // quelli con "--" sono i pezzi interni (--overlay-link,
                  // --favourite, --image...) della stessa card.
                  if (td.indexOf('product-item-id-') !== 0 || td.indexOf('--') >= 0) continue;
                  var cont = nodi[i];
                  var img = cont.querySelector('img');
                  var a = cont.querySelector('a[href]');
                  var btn = cont.querySelector('button[aria-pressed]');
                  out.push({
                    id: td.slice('product-item-id-'.length),
                    href: a ? (a.getAttribute('href') || '') : '',
                    alt: img ? (img.getAttribute('alt') || '') : '',
                    src: img ? (img.getAttribute('src') || '') : '',
                    pref: btn ? (btn.getAttribute('aria-pressed') === 'true') : false
                  });
                }
                // v1.57.0 — "pagina" viaggia insieme all'elenco, non piu' un
                // array nudo: serve a FonteVinted.leggi() per sapere quanto
                // indietro stimare la data (vedi il commento li' sul perche',
                // legato alla correzione "gli annunci Vinted spariscono
                // scorrendo indietro nei giorni").
                var risposta = { pagina: $pagina, annunci: out };
                function consegna(){
                  try { window['$nomePonte'].risultatoRicerca($id, 200, JSON.stringify(risposta)); } catch (e3) {}
                }
                // v1.58.0 — ancora per la stima di data (vedi sopra, lato Kotlin)
                if (!$chiediAncora) { consegna(); return; }
                var fatto = false;
                var t0 = Date.now();
                var timer = setTimeout(function(){
                  if (fatto) return; fatto = true;
                  risposta.ancoraErr = 'nessuna risposta entro 6 s';
                  consegna();
                }, 6000);
                fetch('/catalog?order=newest_first', { credentials: 'include' })
                  .then(function(r){ return r.text().then(function(t){ return [r.status, t]; }); })
                  .then(function(x){
                    if (fatto) return; fatto = true; clearTimeout(timer);
                    var re = /product-item-id-(\d+)/g, m, max = 0, n = 0, maxTxt = '';
                    while ((m = re.exec(x[1])) !== null) {
                      n++;
                      var v = Number(m[1]);
                      if (v > max) { max = v; maxTxt = m[1]; }
                    }
                    if (max > 0) risposta.ancora = { id: maxTxt, ms: t0, n: n };
                    else risposta.ancoraErr = 'stato ' + x[0] + ', ' + x[1].length + ' caratteri, nessun product-item-id';
                    consegna();
                  })
                  .catch(function(e){
                    if (fatto) return; fatto = true; clearTimeout(timer);
                    risposta.ancoraErr = String(e);
                    consegna();
                  });
              } catch (e) {
                try { window['$nomePonte'].risultatoRicerca($id, -5, String(e)); } catch (e2) {}
              }
            })();
            """.trimIndent()
            w.evaluateJavascript(js, null)
        }, 1800)

        ui.postDelayed({
            if (inAttesa.remove(id) != null) ascoltatore?.errore(f.nome, "nessuna risposta entro 20 s")
        }, 20000)
    }

    inner class PonteRicerca {
        @android.webkit.JavascriptInterface
        fun risultatoRicerca(id: Int, stato: Int, corpo: String) {
            // v1.2.0 — lo stesso ponte serve ricerche e azioni: si distinguono dall'id
            azioni.remove(id)?.let { cb -> ui.post { cb(stato, corpo) }; return }
            val f = inAttesa.remove(id) ?: return
            ui.post {
                if (stato != 200) {
                    diagnostica.append("[${f.nome}] stato $stato — ${corpo.take(200)}\n")
                    ascoltatore?.errore(f.nome, "risposta $stato")
                    return@post
                }
                try {
                    val lista = f.leggi(corpo)
                    val chiavi = try { f.chiavi(corpo) } catch (t: Throwable) { "-" }
                    diagnostica.append("[${f.nome}] ${lista.size} annunci · campi: $chiavi\n")
                    ascoltatore?.risultati(f.nome, lista, chiavi)
                } catch (t: Throwable) {
                    diagnostica.append("[${f.nome}] lettura fallita: ${t.message} — ${corpo.take(300)}\n")
                    ascoltatore?.errore(f.nome, "risposta non interpretabile")
                }
            }
        }
    }

    fun chiudi() {
        sessioni.values.forEach { it.destroy() }
        sessioni.clear(); pronte.clear()
        // v1.56.0 — anche la sessione dedicata di avviaDom() (solo Vinted).
        sessioniDom.values.forEach { it.destroy() }
        sessioniDom.clear(); pronteDom.clear()
    }
}
