package fr.ibdjournal.app.ui.theme

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

// Palette santé (vert d'eau) utilisée quand la couleur dynamique n'est pas disponible.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E63),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F0E5),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4A635E),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF446279),
    background = Color(0xFFFDFDF6),
    surface = Color(0xFFFDFDF6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BD3C7),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFFB7F0E5),
    secondary = Color(0xFFB1CCC5),
    onSecondary = Color(0xFF1C3530),
    tertiary = Color(0xFFB1C9E4),
)

@Composable
fun IBDJournalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Couleur dynamique (Material You) sur Android 12+, comme recommandé par le Material Design 3.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}
