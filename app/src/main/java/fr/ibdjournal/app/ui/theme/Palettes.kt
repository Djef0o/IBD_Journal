package fr.ibdjournal.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import fr.ibdjournal.app.data.ThemePalette

/**
 * Jeu de couleurs minimal d'une variante (claire ou sombre). Le reste du [ColorScheme]
 * (conteneurs, surfaces étagées, contours) en est dérivé par [toColorScheme], ce qui
 * garde toutes les palettes cohérentes entre elles.
 *
 * Les valeurs reprennent les teintes publiées de chaque thème. En clair, certains
 * accents sont légèrement assombris : utilisés comme couleur de texte sur fond pâle,
 * les originaux manquent de contraste.
 */
private class Tones(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val primary: Color,
    val secondary: Color,
    /** Accent des repas (chaud, orangé dans chaque palette). */
    val tertiary: Color,
    val error: Color,
)

private class PaletteTones(val light: Tones, val dark: Tones)

private val Solarized = PaletteTones(
    light = Tones(
        background = Color(0xFFFDF6E3), // base3
        surface = Color(0xFFFFFBF0),
        surfaceVariant = Color(0xFFEEE8D5), // base2
        onSurface = Color(0xFF073642), // base02
        onSurfaceVariant = Color(0xFF657B83), // base00
        outline = Color(0xFF93A1A1), // base1
        primary = Color(0xFF1E7F78), // cyan assombri
        secondary = Color(0xFF1E6FA8), // blue assombri
        tertiary = Color(0xFFCB4B16), // orange
        error = Color(0xFFDC322F), // red
    ),
    dark = Tones(
        background = Color(0xFF002B36), // base03
        surface = Color(0xFF073642), // base02
        surfaceVariant = Color(0xFF0F3C47),
        onSurface = Color(0xFFEEE8D5), // base2
        onSurfaceVariant = Color(0xFF93A1A1), // base1
        outline = Color(0xFF586E75), // base01
        primary = Color(0xFF39A89F), // cyan éclairci (4,5:1)
        secondary = Color(0xFF268BD2), // blue
        tertiary = Color(0xFFDB815C), // orange éclairci (4,5:1)
        error = Color(0xFFDC322F), // red
    ),
)

private val Nord = PaletteTones(
    light = Tones(
        background = Color(0xFFECEFF4), // nord6
        surface = Color(0xFFF8F9FB),
        surfaceVariant = Color(0xFFE5E9F0), // nord5
        onSurface = Color(0xFF2E3440), // nord0
        onSurfaceVariant = Color(0xFF4C566A), // nord3
        outline = Color(0xFF8F9BB0),
        primary = Color(0xFF56759D), // nord10 assombri (4,5:1)
        secondary = Color(0xFF4F7F7E), // nord7 assombri
        tertiary = Color(0xFFA6604B), // nord12 assombri (4,5:1)
        error = Color(0xFFBF616A), // nord11
    ),
    dark = Tones(
        background = Color(0xFF2E3440), // nord0
        surface = Color(0xFF3B4252), // nord1
        surfaceVariant = Color(0xFF434C5E), // nord2
        onSurface = Color(0xFFECEFF4), // nord6
        onSurfaceVariant = Color(0xFFC0C8D6),
        outline = Color(0xFF4C566A), // nord3
        primary = Color(0xFF88C0D0), // nord8
        secondary = Color(0xFF81A1C1), // nord9
        tertiary = Color(0xFFDAA08E), // nord12 éclairci (4,5:1)
        error = Color(0xFFBF616A), // nord11
    ),
)

private val Gruvbox = PaletteTones(
    light = Tones(
        background = Color(0xFFFBF1C7), // light0
        surface = Color(0xFFF9F5D7), // light0_hard
        surfaceVariant = Color(0xFFEBDBB2), // light1
        onSurface = Color(0xFF3C3836), // dark1
        onSurfaceVariant = Color(0xFF7C6F64), // dark4
        outline = Color(0xFFA89984), // gray
        primary = Color(0xFF427B58), // aqua (faded)
        secondary = Color(0xFF076678), // blue (faded)
        tertiary = Color(0xFFAF3A03), // orange (faded)
        error = Color(0xFF9D0006), // red (faded)
    ),
    dark = Tones(
        background = Color(0xFF1D2021), // dark0_hard
        surface = Color(0xFF282828), // dark0
        surfaceVariant = Color(0xFF3C3836), // dark1
        onSurface = Color(0xFFEBDBB2), // light1
        onSurfaceVariant = Color(0xFFA89984), // light4
        outline = Color(0xFF665C54), // dark3
        primary = Color(0xFF8EC07C), // aqua
        secondary = Color(0xFF83A598), // blue
        tertiary = Color(0xFFFE8019), // orange
        error = Color(0xFFFB4934), // red
    ),
)

private val Catppuccin = PaletteTones(
    light = Tones( // Latte
        background = Color(0xFFE6E9EF), // mantle
        surface = Color(0xFFEFF1F5), // base
        surfaceVariant = Color(0xFFDCE0E8), // crust
        onSurface = Color(0xFF4C4F69), // text
        onSurfaceVariant = Color(0xFF6C6F85), // subtext0
        outline = Color(0xFF9CA0B0), // overlay0
        primary = Color(0xFF8839EF), // mauve
        secondary = Color(0xFF1E66F5), // blue
        tertiary = Color(0xFFBC4A08), // peach assombri (4,5:1)
        error = Color(0xFFD20F39), // red
    ),
    dark = Tones( // Mocha
        background = Color(0xFF181825), // mantle
        surface = Color(0xFF1E1E2E), // base
        surfaceVariant = Color(0xFF313244), // surface0
        onSurface = Color(0xFFCDD6F4), // text
        onSurfaceVariant = Color(0xFFA6ADC8), // subtext0
        outline = Color(0xFF6C7086), // overlay0
        primary = Color(0xFFCBA6F7), // mauve
        secondary = Color(0xFF89B4FA), // blue
        tertiary = Color(0xFFFAB387), // peach
        error = Color(0xFFF38BA8), // red
    ),
)

private val RosePine = PaletteTones(
    light = Tones( // Dawn
        background = Color(0xFFFAF4ED), // base
        surface = Color(0xFFFFFAF3), // surface
        surfaceVariant = Color(0xFFF2E9E1), // overlay
        onSurface = Color(0xFF575279), // text
        onSurfaceVariant = Color(0xFF797593), // subtle
        outline = Color(0xFF9893A5), // muted
        primary = Color(0xFF286983), // pine
        secondary = Color(0xFF907AA9), // iris
        tertiary = Color(0xFF9D6819), // gold assombri (4,5:1)
        error = Color(0xFFB4637A), // love
    ),
    dark = Tones( // Main
        background = Color(0xFF191724), // base
        surface = Color(0xFF1F1D2E), // surface
        surfaceVariant = Color(0xFF26233A), // overlay
        onSurface = Color(0xFFE0DEF4), // text
        onSurfaceVariant = Color(0xFF908CAA), // subtle
        outline = Color(0xFF524F67), // highlight high
        primary = Color(0xFF9CCFD8), // foam
        secondary = Color(0xFFC4A7E7), // iris
        tertiary = Color(0xFFF6C177), // gold
        error = Color(0xFFEB6F92), // love
    ),
)

/**
 * Palette de marque « Sauge » : vert d'eau apaisant pour le médical, un orange chaud
 * réservé aux repas. Réglée à la main, elle n'est pas dérivée de [Tones].
 */
private val SageLight = lightColorScheme(
    primary = Color(0xFF0B6E63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F0E5),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4A635E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8E0),
    onSecondaryContainer = Color(0xFF06201B),
    tertiary = Color(0xFF9A5B22),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCC2),
    onTertiaryContainer = Color(0xFF321300),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFE6EFEB),
    onSurfaceVariant = Color(0xFF4A5B56),
    surfaceDim = Color(0xFFDCE5E1),
    surfaceBright = Color(0xFFFBFDFB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F8F5),
    surfaceContainer = Color(0xFFEDF3F0),
    surfaceContainerHigh = Color(0xFFE7EEEB),
    surfaceContainerHighest = Color(0xFFE1E9E6),
    inverseSurface = Color(0xFF2C3230),
    inverseOnSurface = Color(0xFFEDF1EF),
    inversePrimary = Color(0xFF7FD8C4),
    outline = Color(0xFF7A8C86),
    outlineVariant = Color(0xFFC9D8D3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
)

private val SageDark = darkColorScheme(
    primary = Color(0xFF7FD8C4),
    onPrimary = Color(0xFF00332C),
    primaryContainer = Color(0xFF0F3B34),
    onPrimaryContainer = Color(0xFFB7F0E5),
    secondary = Color(0xFFB1CCC5),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF223833),
    onSecondaryContainer = Color(0xFFD6EDE7),
    tertiary = Color(0xFFF3B57C),
    onTertiary = Color(0xFF3A1D00),
    tertiaryContainer = Color(0xFF4A2A0C),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF0C1311),
    onBackground = Color(0xFFE9F1EE),
    surface = Color(0xFF141D1A),
    onSurface = Color(0xFFE9F1EE),
    surfaceVariant = Color(0xFF1B2723),
    onSurfaceVariant = Color(0xFF92A7A0),
    surfaceDim = Color(0xFF0C1311),
    surfaceBright = Color(0xFF333B38),
    surfaceContainerLowest = Color(0xFF080E0C),
    surfaceContainerLow = Color(0xFF141D1A),
    surfaceContainer = Color(0xFF18221F),
    surfaceContainerHigh = Color(0xFF1F2A27),
    surfaceContainerHighest = Color(0xFF26332F),
    inverseSurface = Color(0xFFE9F1EE),
    inverseOnSurface = Color(0xFF2C3230),
    inversePrimary = Color(0xFF0B6E63),
    outline = Color(0xFF3A4A45),
    outlineVariant = Color(0xFF283832),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0500),
)

private fun Tones.toColorScheme(dark: Boolean): ColorScheme {
    // Texte posé sur un accent : sombre sur accent clair, blanc sur accent foncé.
    fun on(accent: Color) = if (accent.luminance() > 0.35f) {
        if (dark) background else onSurface
    } else {
        Color.White
    }
    fun container(accent: Color) =
        if (dark) lerp(background, accent, 0.28f) else lerp(surface, accent, 0.18f)
    fun onContainer(accent: Color) =
        if (dark) lerp(accent, Color.White, 0.45f) else lerp(accent, Color.Black, 0.45f)
    // Surfaces étagées : on glisse du fond vers la couleur du texte.
    fun step(fraction: Float) = lerp(background, onSurface, fraction)

    val common = if (dark) {
        darkColorScheme(
            surfaceDim = background,
            surfaceBright = step(0.16f),
            surfaceContainerLowest = lerp(background, Color.Black, 0.3f),
            surfaceContainerLow = step(0.03f),
            surfaceContainer = step(0.05f),
            surfaceContainerHigh = step(0.08f),
            surfaceContainerHighest = step(0.11f),
            outlineVariant = lerp(background, outline, 0.55f),
        )
    } else {
        lightColorScheme(
            surfaceDim = step(0.10f),
            surfaceBright = surface,
            surfaceContainerLowest = surface,
            surfaceContainerLow = step(0.02f),
            surfaceContainer = step(0.04f),
            surfaceContainerHigh = step(0.06f),
            surfaceContainerHighest = step(0.08f),
            outlineVariant = lerp(background, outline, 0.45f),
        )
    }
    return common.copy(
        primary = primary,
        onPrimary = on(primary),
        primaryContainer = container(primary),
        onPrimaryContainer = onContainer(primary),
        secondary = secondary,
        onSecondary = on(secondary),
        secondaryContainer = container(secondary),
        onSecondaryContainer = onContainer(secondary),
        tertiary = tertiary,
        onTertiary = on(tertiary),
        tertiaryContainer = container(tertiary),
        onTertiaryContainer = onContainer(tertiary),
        error = error,
        onError = on(error),
        errorContainer = container(error),
        onErrorContainer = onContainer(error),
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = onSurface,
        inverseOnSurface = background,
        inversePrimary = if (dark) lerp(primary, Color.Black, 0.4f) else lerp(primary, Color.White, 0.4f),
        outline = outline,
    )
}

/**
 * Variante AMOLED : fond et cartes en noir pur (pixels éteints), surfaces élevées à
 * peine teintées pour garder la hiérarchie ; les cartes restent lisibles par leur contour.
 */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, surfaceContainerLow, 0.5f),
    surfaceContainer = lerp(Color.Black, surfaceContainer, 0.6f),
    surfaceContainerHigh = lerp(Color.Black, surfaceContainerHigh, 0.7f),
    surfaceContainerHighest = lerp(Color.Black, surfaceContainerHighest, 0.8f),
    surfaceVariant = lerp(Color.Black, surfaceVariant, 0.7f),
    surfaceBright = lerp(Color.Black, surfaceBright, 0.8f),
    inverseOnSurface = Color.Black,
)

private val ThemePalette.tones: PaletteTones?
    get() = when (this) {
        ThemePalette.SAGE -> null
        ThemePalette.SOLARIZED -> Solarized
        ThemePalette.NORD -> Nord
        ThemePalette.GRUVBOX -> Gruvbox
        ThemePalette.CATPPUCCIN -> Catppuccin
        ThemePalette.ROSE_PINE -> RosePine
    }

private val schemeCache = HashMap<Triple<ThemePalette, Boolean, Boolean>, ColorScheme>()

/** Jeu de couleurs complet d'une palette ; [amoled] n'a d'effet qu'avec [dark]. */
fun colorSchemeFor(palette: ThemePalette, dark: Boolean, amoled: Boolean = false): ColorScheme =
    schemeCache.getOrPut(Triple(palette, dark, amoled && dark)) {
        val base = palette.tones
            ?.let { tones -> if (dark) tones.dark.toColorScheme(true) else tones.light.toColorScheme(false) }
            ?: if (dark) SageDark else SageLight
        if (amoled && dark) base.toAmoled() else base
    }
