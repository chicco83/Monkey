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
    fun azione(f: Fonte, c: Chiamata, esito: (stato: Int, corpo: String) -> Unit) {
        val w = sessione(f)
        if (pronte[f.nome] != true) {
            ui.postDelayed({ azione(f, c, esito) }, 400)
            return
        }
        val id = contatore++

        /*
         * v1.6.0 — il ripiego. Se la prima strada non risponde 2xx si prova la
         * seconda, e all'esterno arriva un esito solo: chi chiama non deve
         * sapere quale indirizzo ha funzionato, deve sapere se ha funzionato.
         */
        azioni[id] = { stato, corpo ->
            if (stato !in 200..299 && c.ripiego != null) {
                diagnostica.append("[${f.nome}] ${c.url} -> $stato, provo il ripiego\n")
                azione(f, c.ripiego, esito)
            } else esito(stato, corpo)
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
            marca = 'csrf:' + (t ? t.length : 0) + ' anon:' + (an ? 1 : 0) + ' hdr:2';
          } catch (e) { marca = 'csrf:errore'; }
        """

        val js = """
        (function(){
          var marca = '';
          var opz = {
            method: '${c.metodo}',
            credentials: 'include',
            headers: {'Accept':'application/json','Content-Type':'application/json'}
          };
          $csrfJs
          var corpo = $corpoJs;
          if (corpo !== undefined) opz.body = corpo;
          function consegna(s, t){ try { window['$nomePonte'].risultatoRicerca($id, s, marca ? '[' + marca + '] ' + t : t); } catch(e){} }
          try {
            fetch('${c.url.replace("'", "\'")}', opz)
              .then(function(r){ return r.text().then(function(t){ consegna(r.status, t); }); })
              .catch(function(e){ consegna(-1, String(e)); });
          } catch(e) { consegna(-2, String(e)); }
        })();
        """.trimIndent()
        w.evaluateJavascript(js, null)

        ui.postDelayed({
            if (azioni.remove(id) != null) esito(-3, "nessuna risposta")
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

    private fun avvia(f: Fonte, q: String, pagina: Int, ordine: String, tentativo: Int) {
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
    }
}
