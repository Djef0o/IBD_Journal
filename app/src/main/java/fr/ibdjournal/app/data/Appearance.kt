package fr.ibdjournal.app.data

/** Luminosité du thème. [AMOLED] : sombre sur fond noir pur (économise l'écran OLED). */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/** Familles de couleurs proposées ; chacune existe en clair et en sombre. */
enum class ThemePalette { SAGE, SOLARIZED, NORD, GRUVBOX, CATPPUCCIN, ROSE_PINE }

data class Appearance(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val palette: ThemePalette = ThemePalette.SAGE,
)
