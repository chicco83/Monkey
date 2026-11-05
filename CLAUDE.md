# CLAUDE.md — Monkey

Versione documento: 1.0 — 2026-11-05 14:50

Progetto: app Android "Monkey" (`it.monkey.app`), ricerca unificata Subito.it + Vinted.

## Regole per ogni sessione
1. Lavora **sempre su `main`**.
2. **Versioning**: a ogni modifica bump di `versionCode`/`versionName` in `app/build.gradle.kts` con commento datato; aggiungi una voce in cima a `Changelog.kt`; correzioni = vecchia versione lasciata come commento datato.
3. Aggiorna sempre i tre documenti, con numero di versione e data/ora in cima: `Monkeymemoriacontesto.md` (context), `CHANGELOG.md`, `MANUAL.md`.
4. Risposte dirette; codice sempre intero, spiegazioni nei commenti.
5. Nessun dato personale (nome/email reali) nei sorgenti; keystore mai nel repo.
6. Fine attivita': `git pull --rebase`, commit SOLO dei file toccati (per nome), messaggio descrittivo, `git push`; copia locale allineata al commit.
7. Leggi `Monkeymemoriacontesto.md` prima di iniziare.
