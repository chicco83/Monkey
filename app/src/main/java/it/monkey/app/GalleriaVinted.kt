package it.monkey.app

/*
 * Monkey — galleria foto a schermo intero per le pagine annuncio Vinted
 * v1.0.0 (2026-09-28) — nuovo, consegnato con l'app v1.64.0
 *
 * SEGNALAZIONE (con screenshot): in "Sito", sulla pagina di un annuncio
 * Vinted, toccando le miniature la galleria del sito si apre come un
 * riquadro piccolo in mezzo allo schermo e non si scorre col dito. Lo
 * sblocco dello zoom (Zoom.kt, v1.0.2) non basta: il riquadro della galleria
 * di Vinted e' disegnato piccolo apposta dal sito, non e' un problema di
 * viewport.
 *
 * COSA FA: sulle sole pagine "/items/<id>" di Vinted un tocco su una foto
 * DELL'ANNUNCIO (non su quelle degli altri articoli piu' sotto, che sono
 * dentro un link a un altro annuncio; non sugli avatar, troppo piccoli)
 * viene intercettato PRIMA che arrivi al sito (fase di cattura) e apre una
 * galleria dell'app: nero, a tutto schermo, scorrimento col dito fra le
 * foto, pizzico e doppio tocco per ingrandire, contatore "2 / 5", X e
 * "indietro" per chiudere.
 *
 * DA DOVE PRENDE LE FOTO (non visto ancora il markup esatto della
 * galleria, quindi due fonti, in quest'ordine):
 *  1. il blocco JSON-LD della pagina (campo "image"), gia' usato con
 *     successo per la descrizione (DescrizioniVinted) — di solito porta
 *     TUTTE le foto, in ordine e in grande;
 *  2. le <img> dell'annuncio visibili nella pagina (la resa piu' grande
 *     dal loro srcset, se c'e'), per quelle che il JSON-LD non avesse.
 * Doppioni tolti confrontando il codice della foto nell'indirizzo
 * ("/t/<codice>/…"): e' lo stesso per miniatura e foto grande.
 * Se non si trova nessuna foto il tocco passa al sito come prima: nel
 * peggiore dei casi si torna al comportamento di oggi, mai a niente.
 *
 * Nota sintassi JS: niente template literal, niente simbolo dollaro, niente
 * funzioni freccia (come in Zoom.kt, DomProbe.kt).
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

object GalleriaVinted {

    // v1.1.0 (2026-10-03): limite 20 foto, terza fonte dai dati incorporati.
    // --- versione precedente (v1.0.0, 2026-09-28) ---
    // const val VERSIONE = "1.0.0"
    // --- fine versione precedente ---
    const val VERSIONE = "1.1.0"
    const val MAX_FOTO = 20

    fun script(ponte: String, pref: String, maxFoto: Int = MAX_FOTO): String = """
    (function () {
      if (window['__g$pref']) return;
      window['__g$pref'] = true;

      function suArticolo() { return /\/items\/\d+/.test(location.pathname); }

      function codice(u) {
        var m = /\/t\/([^\/?]+)\//.exec(u || '');
        return m ? m[1] : (u || '');
      }

      function migliore(img) {
        var ss = img.getAttribute('srcset');
        if (ss) {
          var best = '', bw = -1;
          ss.split(',').forEach(function (p) {
            var q = p.trim().split(/\s+/);
            var w = parseInt(q[1], 10) || 0;
            if (q[0] && w >= bw) { bw = w; best = q[0]; }
          });
          if (best) return best;
        }
        return img.currentSrc || img.src || '';
      }

      function fotoArticolo(img) {
        var u = img.currentSrc || img.src || '';
        if (u.indexOf('vinted.net') < 0) return false;
        var a = img.closest ? img.closest('a[href]') : null;
        if (a && /\/items\/\d+/.test(a.getAttribute('href') || '')) return false;
        if (/\/(50x50|75x75|100x100|150x150)\//.test(u)) return false;
        var r = img.getBoundingClientRect();
        if (r.width < 60 || r.height < 60) return false;
        return true;
      }

      function daJsonLd() {
        var out = [];
        var ss = document.querySelectorAll('script[type="application/ld+json"]');
        for (var i = 0; i < ss.length; i++) {
          try {
            var o = JSON.parse(ss[i].textContent);
            var arr = Array.isArray(o) ? o : (o && o['@graph'] ? o['@graph'] : [o]);
            for (var j = 0; j < arr.length; j++) {
              var im = arr[j] && arr[j].image;
              if (typeof im === 'string') out.push(im);
              else if (Array.isArray(im)) {
                for (var k = 0; k < im.length; k++) {
                  if (typeof im[k] === 'string') out.push(im[k]);
                  else if (im[k] && im[k].url) out.push(im[k].url);
                  else if (im[k] && im[k].contentUrl) out.push(im[k].contentUrl);
                }
              }
            }
          } catch (e) {}
        }
        return out;
      }

      /*
       * v1.1.0 (2026-10-03) — SEGNALAZIONE: la galleria mostrava solo 5 foto
       * anche per annunci che ne hanno fino a 20. Il JSON-LD e le <img>
       * presenti in pagina ne portano poche (le altre miniature non sono
       * ancora create dal sito), quindi si legge anche l'elenco "photos" dei
       * dati incorporati nella pagina (script Next.js). Si prende SOLO
       * l'array "photos":[ ... ] (l'elenco foto dell'annuncio aperto), mai le
       * foto singole ("photo":{...}) degli articoli suggeriti. Il testo di
       * quegli script e' con virgolette e barre protette (\" e \/): si
       * tolgono prima di cercare. Se non c'e' nulla non cambia niente.
       */
      function daDatiPagina() {
        var out = [];
        try {
          var ss = document.querySelectorAll('script:not([src])');
          for (var i = 0; i < ss.length; i++) {
            var t = ss[i].textContent || '';
            if (t.indexOf('photos') < 0) continue;
            t = t.split('\\"').join('"').split('\\/').join('/');
            var pos = 0;
            while ((pos = t.indexOf('"photos":[', pos)) >= 0) {
              pos += 10;
              var prof = 1, fine = pos;
              while (fine < t.length && prof > 0 && fine - pos < 60000) {
                var ch = t.charAt(fine);
                if (ch === '[') prof++;
                else if (ch === ']') prof--;
                fine++;
              }
              var blocco = t.substring(pos, fine);
              var re = /"(?:full_size_url|url)":"(https:[^"]+vinted\.net[^"]+)"/g, m;
              while ((m = re.exec(blocco)) !== null) out.push(m[1]);
              if (out.length) return out;
            }
          }
        } catch (e) {}
        return out;
      }

      function immagineToccata(t) {
        var n = t;
        for (var i = 0; n && i < 5; i++) {
          if (n.tagName === 'IMG') return n;
          if (n.querySelector) {
            var dentro = n.querySelector('img');
            if (dentro) return dentro;
          }
          n = n.parentElement;
        }
        return null;
      }

      document.addEventListener('click', function (ev) {
        try {
          if (!suArticolo()) return;
          var img = immagineToccata(ev.target);
          if (!img || !fotoArticolo(img)) return;

          var lista = [], visti = {};
          function aggiungi(u) {
            if (!u || u.indexOf('http') !== 0) return;
            var c = codice(u);
            if (visti[c]) return;
            visti[c] = true;
            lista.push(u);
          }
          daJsonLd().forEach(aggiungi);
          daDatiPagina().forEach(aggiungi);
          var tutte = document.querySelectorAll('img');
          for (var i = 0; i < tutte.length; i++) {
            if (fotoArticolo(tutte[i])) aggiungi(migliore(tutte[i]));
          }
          if (lista.length === 0) return;
          if (lista.length > $maxFoto) lista = lista.slice(0, $maxFoto);

          var c0 = codice(migliore(img)), indice = 0;
          for (var j = 0; j < lista.length; j++) { if (codice(lista[j]) === c0) { indice = j; break; } }

          ev.preventDefault();
          ev.stopPropagation();
          if (ev.stopImmediatePropagation) ev.stopImmediatePropagation();
          window['$ponte'].apriGalleria(JSON.stringify(lista), indice);
        } catch (e) {}
      }, true);
    })();
    """.trimIndent()
}

/**
 * v1.0.0 — la galleria a schermo intero. Scorrimento orizzontale fra le
 * foto; pizzico o doppio tocco per ingrandire (fino a 5x); da ingrandita si
 * sposta col dito e lo scorrimento fra le foto si blocca finche' non si
 * torna a 1x (altrimenti il dito sposterebbe la pagina invece della foto).
 */
@Composable
fun GalleriaSchermoIntero(foto: List<String>, iniziale: Int, chiudi: () -> Unit) {
    val statoPager = rememberPagerState(
        initialPage = iniziale.coerceIn(0, (foto.size - 1).coerceAtLeast(0)),
        pageCount = { foto.size }
    )
    var ingrandita by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = statoPager,
            userScrollEnabled = !ingrandita,
            modifier = Modifier.fillMaxSize()
        ) { pagina ->
            FotoIngrandibile(
                url = foto[pagina],
                attiva = pagina == statoPager.currentPage,
                cambioIngrandimento = { if (pagina == statoPager.currentPage) ingrandita = it }
            )
        }
        Text(
            "${statoPager.currentPage + 1} / ${foto.size}",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 14.dp)
        )
        IconButton(
            onClick = chiudi,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Chiudi", tint = Color.White)
        }
    }
}

// transformable(canPan = …) e' marcata sperimentale in foundation 1.7.x
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FotoIngrandibile(url: String, attiva: Boolean, cambioIngrandimento: (Boolean) -> Unit) {
    var scala by remember { mutableFloatStateOf(1f) }
    var spostamento by remember { mutableStateOf(Offset.Zero) }
    // tornando su una foto lasciata ingrandita, si riparte da 1x
    if (!attiva && scala != 1f) { scala = 1f; spostamento = Offset.Zero }

    val stato = rememberTransformableState { zoom, pan, _ ->
        scala = (scala * zoom).coerceIn(1f, 5f)
        spostamento = if (scala == 1f) Offset.Zero else spostamento + pan
        cambioIngrandimento(scala > 1f)
    }
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(url) {
                detectTapGestures(onDoubleTap = {
                    if (scala > 1f) { scala = 1f; spostamento = Offset.Zero } else scala = 2.5f
                    cambioIngrandimento(scala > 1f)
                })
            }
            .transformable(stato, canPan = { scala > 1f })
            .graphicsLayer(
                scaleX = scala, scaleY = scala,
                translationX = spostamento.x, translationY = spostamento.y
            )
    )
}
