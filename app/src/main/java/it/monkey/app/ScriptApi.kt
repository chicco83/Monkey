package it.monkey.app

/*
 * Subito+ — cattura delle chiamate dentro la pagina
 * v1.0.6 (2026-08-30)
 *
 * Viene iniettato a document-start, quindi PRIMA che la pagina definisca le sue
 * chiamate: sostituisce fetch e XMLHttpRequest con versioni che, a risposta
 * arrivata, ne consegnano un campione al lato Kotlin.
 * Non modifica il comportamento: la risposta originale viene restituita intatta
 * (fetch usa clone(), cosi' il corpo resta leggibile dalla pagina).
 *
 * v1.0.6 — si consegna anche il METODO e il CORPO DELLA RICHIESTA.
 * Nasce da una domanda precisa: mostrare in elenco quanto si e' offerto per un
 * annuncio. L'importo di un'offerta viaggia nel corpo di una POST, e senza
 * quello si vedrebbe solo che una richiesta e' partita. Il corpo della risposta
 * da solo non basta nemmeno: dice com'e' andata, non cosa si e' chiesto.
 *
 * Resta acceso solo in modalita' sonda: e' la traccia piu' riconoscibile che
 * l'app possa lasciare, perche' dopo la sostituzione toString su fetch non
 * restituisce piu' [native code].
 *
 * Nota sintassi: niente template literal e niente simbolo dollaro nel JS.
 */
object ScriptApi {

    fun script(ponte: String): String = """
    (function () {
      if (window.__spApi) return;
      window.__spApi = true;
      var MAX = 6000, MAX_INVIO = 1200;

      function consegna(u, m, inv, s, b) {
        try {
          window['$ponte'].reportApi(
            String(u).slice(0, 400),
            String(m || 'GET').slice(0, 10),
            String(inv || '').slice(0, MAX_INVIO),
            s | 0,
            String(b || '').slice(0, MAX)
          );
        } catch (e) {}
      }
      function eJson(ct) { return ct && ct.toLowerCase().indexOf('json') >= 0; }

      /* Il corpo della richiesta puo' essere testo, FormData o altro:
         si consegna solo cio' che si legge senza consumarlo. */
      function leggiInvio(c) {
        try {
          if (!c) return '';
          if (typeof c === 'string') return c;
          if (c instanceof URLSearchParams) return c.toString();
          if (typeof FormData !== 'undefined' && c instanceof FormData) {
            var p = [];
            c.forEach(function (v, k) { p.push(k + '=' + String(v).slice(0, 120)); });
            return p.join('&');
          }
          return '[' + (c.constructor ? c.constructor.name : typeof c) + ']';
        } catch (e) { return ''; }
      }

      /* ---- fetch ---- */
      var origFetch = window.fetch;
      if (origFetch) {
        window.fetch = function () {
          var args = arguments;
          var primo = args[0];
          var opz = args[1] || {};
          var u = (primo && primo.url) ? primo.url : primo;
          var m = opz.method || (primo && primo.method) || 'GET';
          var inv = leggiInvio(opz.body);
          return origFetch.apply(this, args).then(function (r) {
            try {
              if (eJson(r.headers.get('content-type'))) {
                r.clone().text()
                 .then(function (t) { consegna(u, m, inv, r.status, t); })
                 .catch(function () {});
              } else {
                /* Anche una risposta non JSON e' informativa quando la
                   richiesta portava qualcosa: dice che quel gesto e' passato di li'. */
                if (inv) consegna(u, m, inv, r.status, '');
              }
            } catch (e) {}
            return r;
          });
        };
      }

      /* ---- XMLHttpRequest ---- */
      var oOpen = XMLHttpRequest.prototype.open;
      var oSend = XMLHttpRequest.prototype.send;
      XMLHttpRequest.prototype.open = function (m, u) {
        this.__spU = u; this.__spM = m;
        return oOpen.apply(this, arguments);
      };
      XMLHttpRequest.prototype.send = function (corpo) {
        var x = this;
        var inv = leggiInvio(corpo);
        try {
          x.addEventListener('load', function () {
            try {
              var ct = x.getResponseHeader('content-type');
              if (eJson(ct)) consegna(x.__spU, x.__spM, inv, x.status, x.responseText);
              else if (inv) consegna(x.__spU, x.__spM, inv, x.status, '');
            } catch (e) {}
          });
        } catch (e) {}
        return oSend.apply(this, arguments);
      };
    })();
    """.trimIndent()
}
