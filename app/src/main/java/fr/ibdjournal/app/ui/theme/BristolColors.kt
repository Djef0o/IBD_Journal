package fr.ibdjournal.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Bristol scale colors, carried over from the old day view:
 * green marks normal stools (types 3–4), orange the soft/liquid
 * stools (5–7), brown the hard stools (1–2) and red
 * type 0. Deliberately lighter shades than the old
 * brown #3E2723, which was unreadable on a dark background.
 */
fun bristolColor(type: Int): Color = when (type) {
    0 -> Color(0xFFD32F2F)   // red: very hard
    1, 2 -> Color(0xFF8D6E63) // brown: hard
    3, 4 -> Color(0xFF43A047) // green: normal
    else -> Color(0xFFEF6C00) // orange: soft / liquid
}
