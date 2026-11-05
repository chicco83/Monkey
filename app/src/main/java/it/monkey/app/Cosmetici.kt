package it.monkey.app

/*
 * Subito+ — filtri cosmetici
 * v0.2.0 (2026-08-30) — nuovo in Fase 1
 *
 * Il blocco di rete impedisce il download della pubblicita', ma nel DOM resta
 * il contenitore vuoto, che lascia un buco nel layout. Qui iniettiamo un foglio
 * di stile PRIMA che la pagina esegua i suoi script (document start), cosi'
 * non si vede nessun lampo, e un osservatore che richiude i contenitori
 * rimasti vuoti anche quando arrivano dopo, in scroll infinito.
 *
 * Il codice e' costruito a partire dalle regole caricate, non e' fisso.
 */
object Cosmetici {

    // v0.3.2: l'id dello stile non e' piu' fisso ("subitoplus-css"), ma costruito su un prefisso casuale
    fun script(selettori: List<String>, pref: String): String {
        if (selettori.isEmpty()) return "(function(){})();"
        val css = selettori.joinToString(",\n") { it } +
                "{display:none!important;height:0!important;min-height:0!important;margin:0!important;padding:0!important}"
        val cssJs = css.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "")

        return """
        (function () {
          var CSS = '$cssJs';

          function metti() {
            if (document.getElementById('${pref}-c')) return;
            var testa = document.head || document.documentElement;
            if (!testa) return;
            var st = document.createElement('style');
            st.id = '${pref}-c';
            st.textContent = CSS;
            testa.appendChild(st);
          }
          metti();
          /* head puo' non esistere ancora al momento dell'iniezione */
          if (!document.getElementById('${pref}-c')) {
            document.addEventListener('DOMContentLoaded', metti);
            var t = setInterval(function () {
              metti();
              if (document.getElementById('${pref}-c')) clearInterval(t);
            }, 50);
          }

          /* Richiude i contenitori rimasti vuoti perche' la risorsa e' stata bloccata:
             solo elementi senza testo e senza figli visibili, per non toccare il sito. */
          function ripulisci() {
            var sospetti = document.querySelectorAll(
              'div[id*="adv"],div[id*="banner"],div[class*="adv"],div[class*="banner"],div[id^="div-gpt"]'
            );
            for (var i = 0; i < sospetti.length; i++) {
              var e = sospetti[i];
              if (e.getAttribute('data-${pref}d')) continue;
              var vuoto = (e.textContent || '').trim().length === 0 && e.querySelector('img,video,canvas,svg,input,button,a') === null;
              if (vuoto && e.offsetHeight > 0) { e.style.setProperty('display', 'none', 'important'); }
              e.setAttribute('data-${pref}d', '1');
            }
          }

          /*
           * v0.4.5: la ripulitura serve a chiudere i buchi lasciati dagli slot
           * bloccati, e quei buchi compaiono nei primi istanti. Dopo qualche
           * passata l'osservatore si stacca: continuare a girare a ogni modifica
           * del DOM rallentava l'apertura dei pannelli sulle schede annuncio.
           */
          var pend = false, passate = 0, osservatore = null;
          function programma() {
            if (pend) return;
            pend = true;
            setTimeout(function () {
              pend = false;
              try { ripulisci(); } catch (e) {}
              if (++passate >= 6 && osservatore) { osservatore.disconnect(); osservatore = null; }
            }, 600);
          }
          /* v0.5.1 — sulla scheda di un singolo annuncio non c'e' nulla da
             richiudere: il foglio di stile basta, l'osservatore no. */
          function dettaglio() { return /-(\d{6,})\.html?(?:[?#]|${'$'})/.test(location.pathname); }

          document.addEventListener('DOMContentLoaded', function () {
            if (dettaglio()) return;
            try { ripulisci(); } catch (e) {}
            try {
              osservatore = new MutationObserver(programma);
              osservatore.observe(document.documentElement, { childList: true, subtree: true });
            } catch (e) {}
          });
        })();
        """.trimIndent()
    }
}
