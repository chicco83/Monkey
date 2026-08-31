package it.monkey.app

/*
 * Subito+ — rilevamento della struttura della pagina
 * v0.2.0 (2026-08-30)
 *
 * CAMBIO IMPORTANTE rispetto alla v0.1.0.
 * La prima versione cercava le card annuncio sul pattern di link "/vi/<id>.htm".
 * L'export reale ha restituito cardTrovate = 0: su subito.it quel pattern non esiste.
 * Quindi qui non si ipotizza piu' nulla: si raccolgono TUTTI i link interni, se ne
 * normalizza il percorso (le cifre diventano #) e si contano le forme ricorrenti.
 * La forma piu' frequente che porta con se' immagine e prezzo E' la card annuncio,
 * e da li' si ricava l'identificatore stabile che servira' alla Fase 2.
 *
 * --- versione precedente (v0.1.0, 2026-08-30) ---
 * var re = /\/vi\/(\d+)\.htm/;
 * ...ricerca diretta delle ancore che corrispondono a quel pattern...
 * --- fine versione precedente ---
 *
 * Nota sintassi: niente template literal e niente simbolo dollaro nel JS,
 * perche' la stringa e' un raw string Kotlin.
 */
object DomProbe {

    /** @param selettoriAdv selettori cosmetici attivi, per contare cosa resta visibile dopo il filtro. */
    fun script(selettoriAdv: List<String>, ponte: String): String {
        val sel = selettoriAdv.joinToString(",") { it.replace("\\", "\\\\").replace("'", "\\'") }
        return """
        (function () {
          function cls(el) { try { return String(el.className || '').trim().slice(0, 160); } catch (e) { return ''; } }
          function attrs(el) {
            var out = {};
            try {
              for (var i = 0; i < el.attributes.length; i++) {
                var a = el.attributes[i];
                if (a.name.indexOf('data-') === 0 || a.name === 'id' || a.name === 'role')
                  out[a.name] = String(a.value).slice(0, 120);
              }
            } catch (e) {}
            return out;
          }
          function chain(el, up) {
            var out = [], cur = el;
            for (var i = 0; i < up && cur; i++) {
              out.push({ tag: cur.tagName, cls: cls(cur), attrs: attrs(cur) });
              cur = cur.parentElement;
            }
            return out;
          }
          function forma(p) { return p.replace(/\d+/g, '#'); }

          var rep = { versioneSonda: '0.2.0', url: location.href, titolo: document.title, ts: Date.now() };

          /* ---- 1. forme di link interne, contate ---- */
          var an = Array.prototype.slice.call(document.querySelectorAll('a[href]'));
          var conta = {}, esempio = {}, primo = {};
          for (var i = 0; i < an.length; i++) {
            var h = an[i].getAttribute('href') || '';
            if (h.indexOf('http') === 0 && h.indexOf(location.hostname) < 0) continue;
            var path;
            try { path = new URL(h, location.href).pathname; } catch (e) { continue; }
            var f = forma(path);
            conta[f] = (conta[f] || 0) + 1;
            if (!esempio[f]) { esempio[f] = path.slice(0, 160); primo[f] = an[i]; }
          }
          var forme = Object.keys(conta).sort(function (a, b) { return conta[b] - conta[a]; }).slice(0, 8);
          rep.formeLink = forme.map(function (f) {
            return { forma: f, quante: conta[f], esempio: esempio[f] };
          });

          /* ---- 2. candidate card: link che contengono immagine e prezzo ---- */
          var cand = [];
          for (var k = 0; k < an.length && cand.length < 60; k++) {
            var a = an[k];
            var haImg = a.querySelector('img') !== null;
            var txt = (a.textContent || '');
            var haPrezzo = /€|\beuro\b/i.test(txt);
            if (haImg && haPrezzo) cand.push(a);
          }
          rep.cardCandidate = cand.length;
          rep.campioni = [];
          for (var c = 0; c < Math.min(cand.length, 3); c++) {
            var href = cand[c].getAttribute('href') || '';
            var pth = '';
            try { pth = new URL(href, location.href).pathname; } catch (e) {}
            rep.campioni.push({
              href: href.slice(0, 200),
              percorso: pth,
              forma: forma(pth),
              testo: (cand[c].textContent || '').trim().replace(/\s+/g, ' ').slice(0, 120),
              attrsLink: attrs(cand[c]),
              antenati: chain(cand[c], 6)
            });
          }

          /* ---- 3. marcatori sponsorizzato dentro il flusso dei risultati ---- */
          var spon = [], tutti = document.querySelectorAll('span,small,div,p');
          var parole = ['sponsor', 'in evidenza', 'pubblicit', 'annuncio promo'];
          for (var s = 0; s < tutti.length && spon.length < 8; s++) {
            if (tutti[s].childElementCount !== 0) continue;
            var t = (tutti[s].textContent || '').trim().toLowerCase();
            if (!t || t.length > 40) continue;
            for (var p = 0; p < parole.length; p++) {
              if (t.indexOf(parole[p]) >= 0) {
                spon.push({ testo: t.slice(0, 40), antenati: chain(tutti[s], 4) });
                break;
              }
            }
          }
          rep.marcatoriSponsor = spon;

          /* ---- 4. verifica Fase 1: cosa resta visibile tra gli slot pubblicitari ---- */
          var residui = [];
          try {
            var nodi = document.querySelectorAll('$sel');
            for (var r = 0; r < nodi.length && residui.length < 15; r++) {
              var b = nodi[r].getBoundingClientRect();
              if (b.height > 2) residui.push({ tag: nodi[r].tagName, id: nodi[r].id || '',
                                               cls: cls(nodi[r]), h: Math.round(b.height) });
            }
          } catch (e) {}
          rep.slotAdvAncoraVisibili = residui;

          /* ---- 4b. promozioni interne di Subito (non pubblicita' di terzi) ----
                 v0.3.0: servono per decidere se e come toglierle nella prossima revisione. */
          var promo = [], nodi2 = document.querySelectorAll('div,section,aside,a,span,p');
          var chiavi = ['tuttosubito', 'compra ora', 'spedizioni da', 'e\' tempo di ricominciare'];
          for (var q = 0; q < nodi2.length && promo.length < 6; q++) {
            var tq = (nodi2[q].textContent || '').trim().toLowerCase();
            if (!tq || tq.length > 120) continue;
            for (var w = 0; w < chiavi.length; w++) {
              if (tq.indexOf(chiavi[w]) >= 0) {
                promo.push({ testo: tq.slice(0, 90), antenati: chain(nodi2[q], 5) });
                break;
              }
            }
          }
          rep.promoInterni = promo;

          /* ---- 5. iframe di terze parti rimasti ---- */
          var ifr = [], ifs = document.querySelectorAll('iframe');
          for (var f2 = 0; f2 < ifs.length && ifr.length < 12; f2++) {
            ifr.push({ id: ifs[f2].id || '', src: (ifs[f2].getAttribute('src') || '').slice(0, 120),
                       h: Math.round(ifs[f2].getBoundingClientRect().height) });
          }
          rep.iframe = ifr;

          rep.userAgent = navigator.userAgent;
          rep.cookieAbilitati = navigator.cookieEnabled;

          try { window['$ponte'].reportDom(JSON.stringify(rep)); } catch (e) {}
        })();
        """.trimIndent()
    }
}
