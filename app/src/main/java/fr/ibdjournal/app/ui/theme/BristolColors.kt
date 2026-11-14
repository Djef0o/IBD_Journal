package fr.ibdjournal.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Couleurs de l'échelle de Bristol, reprises de l'ancienne vue jour :
 * le vert signale les selles normales (types 3–4), l'orange les selles
 * molles/liquides (5–7), le marron les selles dures (1–2) et le rouge
 * le type 0. Des teintes volontairement plus claires que l'ancien
 * marron #3E2723, illisible sur fond sombre.
 */
fun bristolColor(type: Int): Color = when (type) {
    0 -> Color(0xFFD32F2F)   // rouge : très dur
    1, 2 -> Color(0xFF8D6E63) // marron : dur
    3, 4 -> Color(0xFF43A047) // vert : normal
    else -> Color(0xFFEF6C00) // orange : mou / liquide
}
