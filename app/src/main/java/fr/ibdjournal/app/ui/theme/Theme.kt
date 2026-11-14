package fr.ibdjournal.app.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.data.Appearance
import fr.ibdjournal.app.data.ThemeMode
import fr.ibdjournal.app.data.ThemePalette

/** true si l'apparence choisie s'affiche en sombre (AMOLED compris). */
@Composable
fun Appearance.isDark(): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
}

/**
 * Thème de l'application : palette et luminosité choisies dans les réglages.
 * Pas de couleurs dynamiques Material You : l'apparence reste celle choisie,
 * identique d'un téléphone à l'autre.
 */
@Composable
fun IBDJournalTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorSchemeFor(
            palette = appearance.palette,
            dark = appearance.isDark(),
            amoled = appearance.mode == ThemeMode.AMOLED,
        ),
        content = content,
    )
}

@Preview(name = "Clair")
@Composable
private fun PaletteLightPreview() {
    IBDJournalTheme(Appearance(ThemeMode.LIGHT, ThemePalette.SAGE)) { PalettePreviewContent() }
}

@Preview(name = "Sombre", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PaletteDarkPreview() {
    IBDJournalTheme(Appearance(ThemeMode.DARK, ThemePalette.NORD)) { PalettePreviewContent() }
}

@Preview(name = "AMOLED")
@Composable
private fun PaletteAmoledPreview() {
    IBDJournalTheme(Appearance(ThemeMode.AMOLED, ThemePalette.CATPPUCCIN)) { PalettePreviewContent() }
}

@Composable
private fun PalettePreviewContent() {
    Surface {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "IBD Journal",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Lorem ipsum",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
