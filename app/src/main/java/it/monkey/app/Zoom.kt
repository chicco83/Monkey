package it.monkey.app

/*
 * Subito+ — sblocco dello zoom nelle pagine
 * v1.0.2 (2026-08-30)
 *
 * PERCHE' LE FOTO SI APRONO PICCOLE
 * Non e' una questione di risoluzione: e' che non si possono ingrandire.
 * Le versioni mobili dei due siti dichiarano un viewport con
 * "user-scalable=no", e il WebView lo rispetta: le impostazioni dell'app che
 * abilitano lo zoom (setSupportZoom, builtInZoomControls) vengono scavalcate
 * dalla pagina. Aprendo la galleria di un annuncio la foto entra nello schermo
 * e li' resta, esattamente com'era prima.
 *
 * Qui il viewport viene riscritto per consentire l'ingrandimento fino a cinque
 * volte. Si insiste per una decina di secondi perche' le pagine a schede lo
 * riscrivono quando aprono la galleria, poi si smette: la lezione della v0.4.5
 * vale anche qui, si lavora dove serve e ci si ferma appena finito.
 *
 * Nota sintassi: niente template literal e niente simbolo dollaro nel JS.
 */
object Zoom {

    fun script(pref: String): String = """
    (function () {
      if (window['__z$pref']) return;
      window['__z$pref'] = true;

      function sistema() {
        var m = document.querySelector('meta[name="viewport"]');
        if (!m) {
          var testa = document.head || document.documentElement;
          if (!testa) return;
          m = document.createElement('meta');
          m.setAttribute('name', 'viewport');
          testa.appendChild(m);
        }
        var c = m.getAttribute('content') || '';
        if (c.indexOf('user-scalable=yes') >= 0 && c.indexOf('maximum-scale=5') >= 0) return;

        c = c.replace(/,?\s*user-scalable\s*=\s*[^,]*/gi, '')
             .replace(/,?\s*maximum-scale\s*=\s*[^,]*/gi, '')
             .replace(/^\s*,\s*/, '')
             .trim();
        if (c.indexOf('width=') < 0) c = 'width=device-width, initial-scale=1' + (c ? ', ' + c : '');
        m.setAttribute('content', c + ', user-scalable=yes, maximum-scale=5');
      }

      sistema();
      if (document.readyState === 'loading')
        document.addEventListener('DOMContentLoaded', sistema);

      var giri = 0;
      var t = setInterval(function () {
        try { sistema(); } catch (e) {}
        if (++giri > 12) clearInterval(t);
      }, 800);
    })();
    """.trimIndent()
}
