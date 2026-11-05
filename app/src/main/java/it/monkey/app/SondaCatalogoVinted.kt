package it.monkey.app

/*
 * Monkey — sonda: dove sta l'elenco degli annunci, ora che Vinted ha
 * cambiato pagina
 * v1.52.0 (2026-09-15) — nuovo
 *
 * IL PROBLEMA
 * "/api/v2/catalog/items" (usato dalla v0.4.0) ha smesso di funzionare:
 * l'utente ha segnalato errore 404, e una diagnostica ("ricerca-diagnostica.txt")
 * ha confermato che non e' un 404 JSON dell'API — e' la pagina generica
 * "non trovato" dell'app (stessa firma SVG gia' vista in passato per una
 * rotta inesistente, vedi DescrizioniVinted.kt). La rotta e' sparita.
 *
 * v1.53.0 (2026-09-15) — chiusa la pista JSON: "__NEXT_DATA__" assente
 * (Vinted usa l'App Router di Next.js, non piu' il Pages Router), e il
 * marcatore "favourite_count" della v1.52.0 era un falso positivo (dentro
 * una chiave del dizionario di traduzioni, "item.favourite_count.few", non
 * in un campo vero). Marcatori piu' precisi e controllo di
 * "window.__next_f" (il buffer di streaming dell'App Router, stesso posto
 * gia' usato per il token CSRF in MotoreRicerca.kt).
 *
 * v1.54.0 (2026-09-16) — confermato: window.__next_f lungo ZERO, nessun
 * marcatore in nessuno dei 64 <script>, nessuna chiamata fetch/XHR
 * sospetta. Una sonda diversa (DomProbe, quella delle card Subito) ha
 * mostrato che la pagina contiene link HTML veri a "/items/<id>", ma il
 * suo test generico (un solo <a> con dentro sia immagine che prezzo) non
 * li riconosceva. Aggiunta una scansione: dai link /items/ si risale ai
 * genitori cercando il primo livello con immagine + prezzo insieme.
 *
 * v1.55.0 (2026-09-16) — LA RISPOSTA, con la struttura vera in mano. La
 * scansione della v1.54.0 ha trovato 96 annunci sulla stessa pagina e,
 * per il primo, il livello buono (2 genitori sopra il link) e' un:
 *
 *   <div data-testid="product-item-id-10014019096"
 *        class="...new-item-box__container">
 *     <div class="...image-container">
 *       <div class="...image">
 *         <img src="https://images1.vinted.net/t/.../310x430/....webp?s=..."
 *              alt="Vends AMD Duron (...), Brand: AMD, Condizioni: Buone,
 *                    20.00 €, 21.70 €"
 *              data-testid="product-item-id-10014019096--image--img">
 *       </div>
 *     </div>
 *     <a href="/items/10014019096-vends-amd-duron-..." target="_self"
 *        rel="noreferrer"
 *        title="Vends AMD Duron (...), Brand: AMD, Condizioni: Buone,
 *               20.00 €, 21.70 €"
 *        data-testid="product-item-id-10014019096--overlay-link">...</a>
 *     <button data-testid="product-item-id-10014019096--favourite"
 *             aria-label="Aggiungi ai preferiti" aria-pressed="false">...
 *   </div>
 *
 * TRE cose utili, tutte piu' semplici del previsto:
 *
 * 1. Il contenitore di ogni card si trova SENZA risalire genitori a
 *    tentativi: "[data-testid^='product-item-id-']" il cui valore NON
 *    contiene "--" (quelli con "--" sono i pezzi interni: --overlay-link,
 *    --favourite, --image, --image--img). Un solo selettore, diretto.
 * 2. Titolo, marca, condizione, prezzo (e prezzo originale se scontato)
 *    sono TUTTI nell'attributo "alt" dell'<img> — e ripetuti identici
 *    nell'attributo "title" dell'<a> — non in un testo visibile a parte:
 *    ecco perche' la v1.54.0 vedeva "prezzo: true" (il "€" c'e' nel DOM,
 *    dentro quell'attributo, e .textContent in realta' NON legge gli
 *    attributi — va verificato meglio, vedi sotto) ma non un elemento di
 *    testo dedicato. Formato osservato: "<titolo>, Brand: <marca>,
 *    Condizioni: <condizione>, <prezzo>, <prezzo originale>" — la virgola
 *    separa i campi, ma il titolo stesso puo' contenere virgole, quindi
 *    non si spacca ciecamente su ",": si isolano i pezzi noti dalla fine
 *    (i due importi con "€", poi "Condizioni: ...", poi "Brand: ...") e
 *    quello che resta all'inizio e' il titolo.
 * 3. L'id numerico e l'indirizzo dell'annuncio sono nell'<a href> dentro
 *    la stessa card — lo stesso pattern "/items/<id>-..." gia' usato da
 *    Fonti.kt e DescrizioniVinted, quindi Annuncio.url si costruisce
 *    esattamente come prima.
 *
 * Questa versione NON scrive ancora il lettore vero (FonteVinted.leggi())
 * — prima serve controllare che il formato dell'"alt" sia lo stesso su
 * PIU' annunci, non solo il primo (prezzo senza sconto? articolo
 * "venduto"? titolo con la parola "Brand" dentro?). Per questo la sonda
 * ora riporta un campione COMPATTO (id, href, alt, title, src) per fino
 * a 10 annunci — non piu' l'albero dei genitori, ormai inutile — piu'
 * l'HTML completo di un solo contenitore, cosi' si vede anche cio' che
 * non e' nell'"alt" (il pulsante preferiti, per dire, gia' un
 * data-testid utilizzabile in futuro). Le fasi 1-3 (script/JSON) restano
 * ma ridotte a una riga sola: servono solo a scoprire se Vinted torna un
 * giorno a un JSON vero, non a ripetere ogni volta la stessa prova
 * lunga.
 *
 * Alzato anche ApiLog.MAX_CORPO (6000 -> 10000, vedi il commento li'): un
 * campione completo piu' dieci compatti superava il vecchio limite e la
 * risposta precedente era arrivata troncata a meta' di un JSON.
 *
 * COSA FA QUESTA SONDA
 * Attiva SOLO in "Modalita' sonda" e SOLO sulla pagina di ricerca/catalogo
 * di Vinted (l'indirizzo contiene "search_text=" o il percorso contiene
 * "/catalog"). In ordine:
 *   1. Riga sola: window.__next_f (lunghezza), __NEXT_DATA__ (presente o
 *      no), marcatori JSON noti in un qualunque <script> (si' o no).
 *   2. Tutti i contenitori "[data-testid='product-item-id-<id>']" (senza
 *      "--" dopo l'id): quanti sono, e per i primi 10 un campione
 *      compatto (id, href, alt, title, src immagine).
 *   3. L'HTML completo (fino a 5000 caratteri) del primo contenitore
 *      trovato.
 *
 * Il risultato va nello stesso canale delle chiamate campionate (reportApi
 * -> ApiLog -> "api.json" in "Esporta diagnostica"), sotto il metodo
 * "SONDA-CATALOGO".
 *
 * QUESTA VERSIONE NON CAMBIA NULLA DI VISIBILE: e' solo la sonda. Una volta
 * confermato il formato su piu' annunci si scrive il lettore DOM vero per
 * FonteVinted.leggi().
 *
 * Nota sintassi: niente template literal e niente simbolo dollaro nel JS,
 * come nel resto delle sonde di questo progetto (vedi DomProbe.kt e
 * DescrizioniVinted.scriptSonda).
 */
object SondaCatalogoVinted {

    /*
     * --- versione precedente (v1.54.0: risaliva i genitori dai link
     *     /items/ cercando immagine+prezzo insieme, riportava l'intero
     *     albero di 3 annunci in JSON — troncato a meta' dal vecchio
     *     ApiLog.MAX_CORPO=6000. Ha trovato il livello buono (il
     *     contenitore data-testid="product-item-id-<id>") ma in modo
     *     indiretto; questa versione lo cerca direttamente) ---
     * fun script(ponte: String): String = """
     * (function () {
     *   try {
     *     ... [fasi 1-3 identiche alla v1.53.0] ...
     *     try {
     *       var an = document.querySelectorAll('a[href]');
     *       var trovatiId = {};
     *       var ordineId = [];
     *       for (var ai = 0; ai < an.length; ai++) {
     *         var h2 = an[ai].getAttribute('href') || '';
     *         var path2;
     *         try { path2 = new URL(h2, location.href).pathname; } catch (e) { continue; }
     *         var m2 = path2.match(/^\/items\/(\d+)/);
     *         if (!m2) continue;
     *         var id2 = m2[1];
     *         if (!trovatiId[id2]) { trovatiId[id2] = an[ai]; ordineId.push(id2); }
     *       }
     *       // per i primi 3 id: risale i genitori (fino a 9 livelli) cercando
     *       // il primo con immagine + testo "€" insieme, riporta l'albero e
     *       // l'HTML di quel livello.
     *       ...
     *     } catch (e) {}
     *     window['$ponte'].reportApi(location.href, 'SONDA-CATALOGO', '', 200, pezzi.join('\n---\n'));
     *   } catch (e) { ... }
     * })();
     * """.trimIndent()
     * --- fine versione precedente ---
     */
    fun script(ponte: String): String = """
    (function () {
      try {
        var href = String(location.href || '');
        var percorso = String(location.pathname || '');
        var isCatalogo = href.indexOf('search_text=') >= 0 || percorso.indexOf('/catalog') >= 0;
        if (!isCatalogo) return;

        var pezzi = [];

        // 1. JSON/script: ridotto a una riga, confermato vuoto due volte
        // di fila (v1.53.0, v1.54.0) — si controlla ancora, ma senza
        // sprecare budget a riportare contenuto che finora non c'e' mai.
        try {
          var buf = (window.__next_f || []).map(function (x) { return (x && x[1]) || ''; }).join('');
          var nd = document.getElementById('__NEXT_DATA__');
          var MARCATORI = ['catalog_items', 'favourite_count":', 'item_box":', 'search_tracking_params":'];
          var trovatoMarcatore = false;
          var scripts = document.querySelectorAll('script');
          for (var i = 0; i < scripts.length && !trovatoMarcatore; i++) {
            var c = scripts[i].textContent || '';
            for (var m = 0; m < MARCATORI.length; m++) {
              if (c.indexOf(MARCATORI[m]) >= 0) { trovatoMarcatore = true; break; }
            }
          }
          pezzi.push('[JSON] next_f=' + buf.length + ' NEXT_DATA=' + (nd ? 'presente' : 'assente') +
            ' marcatori-in-script=' + (trovatoMarcatore ? 'SI' : 'no') + ' (script totali ' + scripts.length + ')');
        } catch (e) { pezzi.push('[JSON] errore: ' + String(e)); }

        // 2. contenitori veri delle card: un data-testid diretto, trovato
        // guardando l'HTML di un annuncio vero (v1.54.0) — niente piu'
        // bisogno di risalire genitori a tentativi.
        try {
          var nodi = document.querySelectorAll('[data-testid]');
          var contenitori = [];
          for (var ni = 0; ni < nodi.length; ni++) {
            var td = nodi[ni].getAttribute('data-testid') || '';
            if (/^product-item-id-\d+$/.test(td)) contenitori.push(nodi[ni]);
          }
          pezzi.push('[DOM container] trovati: ' + contenitori.length);

          if (contenitori.length) {
            pezzi.push('[DOM html completo primo] ' + (contenitori[0].outerHTML || '').slice(0, 5000));
          }

          var campioni = [];
          for (var ci = 0; ci < Math.min(contenitori.length, 10); ci++) {
            var cont = contenitori[ci];
            var td2 = cont.getAttribute('data-testid') || '';
            var idc = td2.replace('product-item-id-', '');
            var img = cont.querySelector('img');
            var a = cont.querySelector('a[href]');
            campioni.push({
              id: idc,
              href: a ? (a.getAttribute('href') || '').slice(0, 200) : '',
              alt: img ? (img.getAttribute('alt') || '').slice(0, 300) : '',
              title: a ? (a.getAttribute('title') || '').slice(0, 300) : '',
              src: img ? (img.getAttribute('src') || '').slice(0, 200) : ''
            });
          }
          pezzi.push('[DOM campioni compatti] ' + JSON.stringify(campioni));
        } catch (e) { pezzi.push('[DOM] errore: ' + String(e)); }

        window['$ponte'].reportApi(location.href, 'SONDA-CATALOGO', '', 200, pezzi.join('\n---\n'));
      } catch (e) {
        try { window['$ponte'].reportApi(location.href, 'SONDA-CATALOGO', '', -1, String(e)); } catch (e2) {}
      }
    })();
    """.trimIndent()
}
