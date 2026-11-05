package it.monkey.app

/*
 * Subito+ — fondamenta grafiche
 * v0.5.0 (2026-08-30) — Fase 4, punto 1
 *
 * Una palette propria, coerente in chiaro e in scuro, piu' il colore dinamico
 * su Android 12 e successivi: li' l'app prende i toni dallo sfondo del telefono
 * e sembra parte del sistema invece di un'app capitata li' per caso.
 *
 * I colori NON imitano quelli di Subito: l'app e' una cosa diversa e deve
 * sembrarlo. Verde-ardesia per le superfici, ocra come accento.
 */

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Chiaro = lightColorScheme(
    primary = Color(0xFF7A5214),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF5E3CB),
    onPrimaryContainer = Color(0xFF2A1A00),
    secondary = Color(0xFF2C6A63),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE8E4),
    onSecondaryContainer = Color(0xFF052120),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF17211F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17211F),
    surfaceVariant = Color(0xFFE4EBE9),
    onSurfaceVariant = Color(0xFF46534F),
    outline = Color(0xFF7A8783),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val Scuro = darkColorScheme(
    primary = Color(0xFFE7BE85),
    onPrimary = Color(0xFF422B00),
    primaryContainer = Color(0xFF5E3F06),
    onPrimaryContainer = Color(0xFFFFDDB3),
    secondary = Color(0xFFA9CFC9),
    onSecondary = Color(0xFF0F3733),
    secondaryContainer = Color(0xFF2A4F4B),
    onSecondaryContainer = Color(0xFFC5ECE6),
    background = Color(0xFF0F1614),
    onBackground = Color(0xFFE4EBE8),
    surface = Color(0xFF151F1C),
    onSurface = Color(0xFFE4EBE8),
    surfaceVariant = Color(0xFF3A4A46),
    onSurfaceVariant = Color(0xFFC0CCC8),
    outline = Color(0xFF8A9793),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

@Composable
fun TemaSubitoPlus(
    scuro: Boolean = isSystemInDarkTheme(),
    dinamico: Boolean = true,
    contenuto: @Composable () -> Unit
) {
    val schema = when {
        dinamico && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (scuro) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        scuro -> Scuro
        else -> Chiaro
    }
    MaterialTheme(colorScheme = schema, content = contenuto)
}
