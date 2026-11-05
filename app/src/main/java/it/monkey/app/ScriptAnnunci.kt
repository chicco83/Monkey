package it.monkey.app

/*
 * Subito+ — scarto dei singoli annunci e ritocchi all'interfaccia
 * v0.5.1 (2026-08-30)
 *
 * COME RICONOSCE UN ANNUNCIO
 * Dall'export reale della sonda v0.2.0: i link di dettaglio hanno la forma
 *   /<categoria>/<slug-del-titolo>-<idNumerico>.htm
 *   es. /moto-e-scooter/moto-guzzi-v100-mandello-e5-wind-tunnel-bari-632869214.htm
 * L'ultimo gruppo di cifre prima di .htm e' l'identificatore stabile dell'annuncio.
 * Non uso classi CSS per riconoscerlo: le classi sono generate (index-module_...)
 * e cambiano a ogni build del sito, il formato dell'URL no.
 *
 * COME TROVA LA CARD DA NASCONDERE
 * Non c'e' un selettore fisso della card. Si risale dal link finche' l'antenato
 * contiene link di UN SOLO annuncio: l'ultimo antenato "puro" e' la card.
 * Cosi' il meccanismo sopravvive a un rifacimento del sito senza toccare le regole.
 *
 * v0.4.5 — PRESTAZIONI. Toccare "Contatta" o "Fai un'offerta" su una scheda
 * aveva alcuni secondi di ritardo: aprire quei pannelli genera una valanga di
 * modifiche al DOM, e a ogni modifica questo script rifaceva la sua scansione
 * sul filo principale, l'unico su cui il browser puo' anche rispondere al tocco.
 * Tre correzioni:
 *   1. sulle schede di dettaglio lo script non fa nulla e non osserva nulla:
 *      li' non ci sono card da scartare, quindi non c'e' motivo di girare
 *   2. l'identificatore si ricava con un'espressione regolare sull'href invece
 *      che costruendo un oggetto URL per ogni link (era il costo maggiore)
 *   3. la risalita alla card usa contains() sulla lista gia' nota degli annunci
 *      invece di interrogare il DOM a ogni livello
 *
 * Nota sintassi: niente template literal e niente simbolo dollaro nel JS.
 */
object ScriptAnnunci {

    /**
     * @param sezioneIniziale scheda da aprire sulla home ("" per non toccare nulla)
     * @param cercaApreForm   se vero, il pulsante "Cerca" della barra in basso
     *                        riporta alla home con il modulo di ricerca invece che ai risultati
     * @param vistaCompatta   se vero, ogni card viene ridisposta con foto piccola a sinistra
     *                        e testo a destra (v0.3.0)
     */
    fun script(sezioneIniziale: String, cercaApreForm: Boolean, vistaCompatta: Boolean,
               ponte: String, pref: String): String {
        val sez = sezioneIniziale.replace("'", "")
        return """
        (function () {
          if (window['__$pref']) return;
          window['__$pref'] = true;

          /* ---------------- archivio degli scartati ---------------- */
          var nascosti = {};
          function ricaricaNascosti() {
            try {
              var arr = JSON.parse(window['$ponte'].nascostiJson());
              nascosti = {};
              for (var i = 0; i < arr.length; i++) nascosti[arr[i]] = 1;
            } catch (e) {}
          }
          ricaricaNascosti();

          /* ---------------- riconoscimento annuncio ---------------- */
          var RE_ID = /-(\d{6,})\.html?(?:[?#]|${'$'})/;
          function idDa(a) {
            var h = a.getAttribute('href') || '';
            if (h.indexOf('.htm') < 0) return null;
            var m = RE_ID.exec(h);
            return m ? m[1] : null;
          }

          /* Vero sulla scheda di un singolo annuncio: li' non c'e' niente da fare. */
          function paginaDettaglio() { return RE_ID.test(location.pathname); }

          /*
           * Risale finche' l'antenato contiene link di un solo annuncio.
           * v0.4.5: il confronto usa contains() sulla lista degli annunci gia'
           * raccolta, invece di una querySelectorAll a ogni livello di risalita.
           */
          function contenitore(a, id, tutti) {
            var best = a, cur = a, giri = 0;
            while (cur.parentElement && giri < 12) {
              var p = cur.parentElement;
              if (p === document.body || p.tagName === 'MAIN') break;
              var altro = false;
              for (var i = 0; i < tutti.length; i++) {
                if (tutti[i].id !== id && p.contains(tutti[i].el)) { altro = true; break; }
              }
              if (altro) break;
              best = p; cur = p; giri++;
            }
            return best;
          }

          /* ---------------- pulsante di scarto ---------------- */
          function pulsante(cont, id) {
            var b = document.createElement('button');
            b.className = '${pref}-x';
            b.type = 'button';
            b.setAttribute('aria-label', 'Nascondi questo annuncio');
            b.textContent = '✕';
            b.addEventListener('click', function (ev) {
              ev.preventDefault(); ev.stopPropagation();
              var titolo = (cont.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 90);
              try { window['$ponte'].nascondi(id, titolo); } catch (e) {}
              nascosti[id] = 1;
              cont.style.setProperty('display', 'none', 'important');
            }, true);
            if (getComputedStyle(cont).position === 'static') cont.style.position = 'relative';
            cont.appendChild(b);
          }

          function stile() {
            if (document.getElementById('${pref}-s')) return;
            var st = document.createElement('style');
            st.id = '${pref}-s';
            st.textContent =
              '.${pref}-x{position:absolute;top:6px;right:6px;z-index:9999;width:30px;height:30px;' +
              'border:0;border-radius:50%;background:rgba(20,20,20,.62);color:#fff;font-size:15px;' +
              'line-height:30px;padding:0;cursor:pointer;box-shadow:0 1px 4px rgba(0,0,0,.35)}' +
              '.${pref}-x:active{background:rgba(20,20,20,.9)}' +
              '.${pref}-f{flex:0 0 116px!important;width:116px!important;max-width:116px!important}' +
              '.${pref}-f img{width:100%!important;height:116px!important;object-fit:cover!important}';
            (document.head || document.documentElement).appendChild(st);
          }

          /* ---------------- vista compatta ----------------
             Il sito non espone un selettore di layout nella versione mobile.
             Qui non si usano classi del sito (sono generate e cambiano): si cerca
             dentro la card il primo nodo che ha almeno due figli, uno con l'immagine
             e uno con del testo, e si dispone quel nodo in orizzontale. */
          var VISTA_COMPATTA = ${if (vistaCompatta) "true" else "false"};

          /*
           * v0.5.1 — CORREZIONE. La versione precedente scendeva nella card solo
           * finche' incontrava nodi con un unico figlio: bastava un contenitore
           * con due figli, di cui nessuno con l'immagine, per fermarla — ed e'
           * il motivo per cui la vista compatta spesso non faceva nulla.
           * Ora si parte dall'immagine e si RISALE finche' si trova un livello
           * dove accanto al ramo dell'immagine c'e' un fratello con del testo:
           * quello e' il livello da disporre in orizzontale.
           *
           * --- versione precedente (v0.4.2, 2026-08-30) ---
           * var cur = cont;
           * for (var g = 0; g < 6; g++) { ...scendeva nei figli unici... }
           * --- fine versione precedente ---
           */
          function nodoFlex(cont) {
            var img = cont.querySelector('img');
            if (!img) return null;
            var ramo = img, p = img.parentElement, giri = 0;
            while (p && giri < 10) {
              var f = p.children;
              if (f.length >= 2) {
                var idx = -1;
                for (var i = 0; i < f.length; i++) if (f[i] === ramo) idx = i;
                if (idx >= 0) {
                  for (var j = 0; j < f.length; j++) {
                    if (j !== idx && (f[j].textContent || '').trim().length > 0)
                      return { n: p, i: idx };
                  }
                }
              }
              if (p === cont) break;
              ramo = p; p = p.parentElement; giri++;
            }
            return null;
          }

          function compatta(cont) {
            if (!VISTA_COMPATTA) return;
            if (cont.getAttribute('data-${pref}v') === '1') return;
            var t = nodoFlex(cont);
            if (!t) return;
            cont.setAttribute('data-${pref}v', '1');
            var n = t.n, f = n.children;
            n.style.setProperty('display', 'flex', 'important');
            n.style.setProperty('flex-direction', 'row', 'important');
            n.style.setProperty('align-items', 'flex-start', 'important');
            n.style.setProperty('gap', '10px', 'important');
            for (var i = 0; i < f.length; i++) {
              if (i === t.i) f[i].classList.add('${pref}-f');
              else {
                f[i].style.setProperty('flex', '1 1 auto', 'important');
                f[i].style.setProperty('min-width', '0', 'important');
              }
            }
          }

          /* ---------------- passata sulla pagina ---------------- */
          var visti = (typeof WeakSet !== 'undefined') ? new WeakSet() : null;

          function passata() {
            if (paginaDettaglio()) return;      /* v0.4.5 */
            var links = document.querySelectorAll('a[href*=".htm"]');
            var tutti = [], primo = {}, ordine = [];
            for (var i = 0; i < links.length; i++) {
              var id = idDa(links[i]);
              if (!id) continue;
              tutti.push({ el: links[i], id: id });
              if (!primo[id]) { primo[id] = links[i]; ordine.push(id); }
            }
            /* Con meno di due annunci non siamo in una lista. */
            if (ordine.length < 2) return;

            stile();
            for (var k = 0; k < ordine.length; k++) {
              var idv = ordine[k];
              var ancora = primo[idv];
              if (visti && visti.has(ancora)) continue;
              var cont = contenitore(ancora, idv, tutti);
              if (!cont || cont === document.body) continue;
              if (visti) visti.add(ancora);
              if (nascosti[idv]) { cont.style.setProperty('display', 'none', 'important'); continue; }
              if (cont.getAttribute('data-${'$'}pref') === idv) continue;
              cont.setAttribute('data-${'$'}pref', idv);
              pulsante(cont, idv);
              compatta(cont);
            }
          }

          /* ---------------- ritocchi all'interfaccia ---------------- */
          var SEZIONE = '$sez';
          var CERCA_APRE_FORM = ${if (cercaApreForm) "true" else "false"};

          function testo(el) { return (el.textContent || '').trim(); }

          /* La home apre la scheda Motori: se richiesto, si passa subito a un'altra. */
          function apriSezione(tent) {
            if (!SEZIONE) return;
            if (location.pathname !== '/') return;
            var el = document.querySelectorAll('button,a,[role="tab"],[role="button"]');
            for (var i = 0; i < el.length; i++) {
              if (testo(el[i]) === SEZIONE && el[i].offsetHeight > 0 && el[i].offsetHeight < 90) {
                el[i].click();
                return;
              }
            }
            if (tent < 12) setTimeout(function () { apriSezione(tent + 1); }, 400);
          }

          /* "Cerca" nella barra in basso porta ai risultati: lo si riporta al modulo di ricerca. */
          if (CERCA_APRE_FORM) {
            document.addEventListener('click', function (ev) {
              var t = ev.target;
              var v = t && t.closest ? t.closest('a,button,[role="button"]') : null;
              if (!v) return;
              if (testo(v) !== 'Cerca') return;
              if (v.getBoundingClientRect().top < window.innerHeight * 0.6) return; /* solo barra in basso */
              ev.preventDefault(); ev.stopPropagation();
              location.href = 'https://www.subito.it/';
            }, true);
          }

          /* ---------------- avvio e scroll infinito ---------------- */
          var atteso = false;
          function programma() {
            if (atteso) return;
            atteso = true;
            /* v0.4.5: attesa piu' lunga e, quando c'e', lavoro nei momenti di quiete */
            setTimeout(function () {
              atteso = false;
              var fai = function () { try { passata(); } catch (e) {} };
              if (window.requestIdleCallback) requestIdleCallback(fai, { timeout: 1200 });
              else fai();
            }, 500);
          }

          function avvia() {
            apriSezione(0);
            if (paginaDettaglio()) return;   /* v0.4.5: sulla scheda ci fermiamo qui */
            try { passata(); } catch (e) {}
            try {
              new MutationObserver(programma).observe(document.documentElement,
                { childList: true, subtree: true });
            } catch (e) {}
          }

          if (document.readyState === 'loading')
            document.addEventListener('DOMContentLoaded', avvia);
          else avvia();

          window['__${pref}r'] = function () { ricaricaNascosti(); passata(); };
        })();
        """.trimIndent()
    }
}
