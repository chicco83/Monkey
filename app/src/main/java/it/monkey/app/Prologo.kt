package it.monkey.app

/*
 * Subito+ — prologo di pagina
 * v0.3.2 (2026-08-30) — nuovo, riduce la rilevabilita'
 *
 * IL PROBLEMA CHE RISOLVE
 * addJavascriptInterface pubblica un oggetto su window, visibile a qualsiasi
 * script della pagina. Fino alla v0.3.1 si chiamava "SubitoPlus": bastava un
 * for..in su window per sapere che il sito era aperto dentro questa app, e
 * quel nome diceva pure quale.
 *
 * Questo script gira per primo, prima di ogni script del sito:
 *   1. prende il riferimento al ponte
 *   2. lo cancella da window
 *   3. lo ridefinisce con lo stesso nome ma NON enumerabile, cosi' non compare
 *      in Object.keys(window) ne' in un for..in
 * Il nome e' casuale e diverso per ogni installazione: non e' una stringa
 * riconoscibile nemmeno se qualcuno la cercasse di proposito.
 *
 * Resta vero che una verifica mirata (getOwnPropertyNames) lo troverebbe:
 * l'obiettivo non e' l'invisibilita', e' non essere appariscenti.
 */
object Prologo {

    fun script(nomePonte: String): String = """
    (function () {
      var N = '$nomePonte';
      try {
        var p = window[N];
        if (!p) return;
        try { delete window[N]; } catch (e) {}
        Object.defineProperty(window, N, {
          value: p, writable: false, enumerable: false, configurable: true
        });
      } catch (e) {}

      /*
       * v0.4.0 (2026-08-31) — CONTATORE TELEMETRIA ANTI-BOT (solo diagnostica,
       * nessun comportamento cambia per l'utente).
       *
       * La sonda mandata in chat (api.json) ha trovato, sulle pagine vinted.it,
       * un secondo sistema anti-bot oltre a Datadome: chiamate POST verso un
       * percorso offuscato (".../j3r4zw/v1/consume") che manda un pacchetto
       * cifrato di segnali di comportamento, con un "avvio" iniziale
       * (".../j3r4zw/v1/config"). Lo script che lo carica (trovato anche su
       * k7v3q2.vinted.com) ha lo stesso hash di versione della risposta di
       * quel "config" — e' davvero attivo sul sito, non un falso positivo.
       *
       * Il sospetto, non ancora provato: la sessione nascosta 1x1 dell'app,
       * mai toccata da un dito vero, non genera mai i segnali che questo
       * sistema si aspetta — e per questo le SCRITTURE (mettere un preferito)
       * vengono respinte mentre le LETTURE (cercare, leggere le conversazioni)
       * passano lo stesso. Per saperlo serve vedere se, nella sessione VERA
       * che fallisce, queste chiamate partono affatto: finora la sonda le ha
       * viste solo da una scheda di browser normale, mai da dentro il WebView
       * nascosto dell'app.
       *
       * Questo contatore, letto da MotoreRicerca subito dopo un 403 sul
       * preferito, dice quante volte e' stato chiamato ".../j3r4zw/..." IN
       * QUESTA sessione prima del fallimento, e con che esito. Se resta a
       * zero il sospetto e' confermato; se e' alto quanto in una sessione
       * normale, va escluso e cercata un'altra causa. E' nascosto da
       * Object.keys(window) per lo stesso motivo del ponte sopra: non e'
       * invisibilita', e' non essere appariscenti.
       */
      try {
        if (!window.__mkTel) {
          var _f = window.fetch;
          var tel = { j3r4zw: 0, ultimoStato: null };
          if (typeof _f === 'function') {
            window.fetch = function (input, init) {
              var u = (typeof input === 'string') ? input : (input && input.url) || '';
              var conta = u.indexOf('j3r4zw') >= 0;
              if (conta) tel.j3r4zw++;
              var p = _f.apply(this, arguments);
              if (conta && p && p.then) {
                p = p.then(function (r) { tel.ultimoStato = r.status; return r; });
              }
              return p;
            };
          }
          Object.defineProperty(window, '__mkTel', {
            value: tel, writable: false, enumerable: false, configurable: true
          });
        }
      } catch (e) {}
    })();
    """.trimIndent()
}
