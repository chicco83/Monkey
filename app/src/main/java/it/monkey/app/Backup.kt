package it.monkey.app

/*
 * Subito+ — copia di sicurezza
 * v1.0.0 (2026-08-30) — Fase 7
 *
 * Un solo file con tutto cio' che l'app ha imparato dall'uso e che non si puo'
 * ricostruire: gli annunci scartati, le ricerche salvate con la memoria di cosa
 * hanno gia' mostrato, e le regole di blocco se sono state personalizzate.
 *
 * Non contiene sessioni ne' credenziali: quelle restano nei cookie del sistema
 * e non escono dall'app. Un backup e' quindi un file innocuo da conservare
 * dove si vuole.
 */

import android.content.Context
import org.json.JSONObject
import java.io.File

object Backup {

    const val MARCATORE = "\"subitoplus-backup\""

    fun esporta(ctx: Context): String {
        val j = JSONObject()
        j.put("subitoplus-backup", "1.0.0")
        j.put("generato", System.currentTimeMillis())
        j.put("nascosti", JSONObject(Nascosti.esportaJson()))
        j.put("ricerche", JSONObject(RicercheSalvate.esportaJson()))
        val regoleUtente = File(ctx.filesDir, "regole.json")
        if (regoleUtente.exists()) {
            try { j.put("regole", JSONObject(regoleUtente.readText())) } catch (t: Throwable) {}
        }
        return j.toString(2)
    }

    /** @return descrizione di cosa e' stato ripristinato, o null se il file non e' un backup. */
    fun importa(ctx: Context, testo: String): String? {
        if (!testo.contains(MARCATORE)) return null
        val j = try { JSONObject(testo) } catch (t: Throwable) { return null }
        val parti = ArrayList<String>()

        j.optJSONObject("nascosti")?.let {
            val n = Nascosti.importa(ctx, it.toString())
            if (n >= 0) parti.add("$n annunci nascosti")
        }
        j.optJSONObject("ricerche")?.let {
            val n = RicercheSalvate.importa(ctx, it.toString())
            if (n >= 0) parti.add("$n ricerche salvate")
        }
        j.optJSONObject("regole")?.let {
            if (Regole.importa(ctx, it.toString())) parti.add("regole di blocco")
        }
        return if (parti.isEmpty()) "niente da ripristinare" else parti.joinToString(", ")
    }
}
