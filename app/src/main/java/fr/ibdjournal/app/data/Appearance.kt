package fr.ibdjournal.app.data

/** Theme brightness. [AMOLED]: dark on pure black (saves OLED screen power). */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

/** Offered color families; each exists in light and dark. */
enum class ThemePalette { SAGE, SOLARIZED, NORD, GRUVBOX, CATPPUCCIN, ROSE_PINE }

data class Appearance(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val palette: ThemePalette = ThemePalette.SAGE,
)
