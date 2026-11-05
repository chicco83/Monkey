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
    })();
    """.trimIndent()
}
