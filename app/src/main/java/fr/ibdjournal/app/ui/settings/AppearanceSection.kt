package fr.ibdjournal.app.ui.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.Appearance
import fr.ibdjournal.app.data.ThemeMode
import fr.ibdjournal.app.data.ThemePalette
import fr.ibdjournal.app.ui.theme.colorSchemeFor
import fr.ibdjournal.app.ui.theme.isDark
import java.util.Locale

/** Translated languages (values-xx folders); English is also the default language. */
private val SupportedLanguages = listOf("fr", "en", "es", "de", "it", "pt")

@get:StringRes
private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_mode_system
        ThemeMode.LIGHT -> R.string.theme_mode_light
        ThemeMode.DARK -> R.string.theme_mode_dark
        ThemeMode.AMOLED -> R.string.theme_mode_amoled
    }

@get:StringRes
private val ThemePalette.labelRes: Int
    get() = when (this) {
        ThemePalette.SAGE -> R.string.palette_sage
        ThemePalette.SOLARIZED -> R.string.palette_solarized
        ThemePalette.NORD -> R.string.palette_nord
        ThemePalette.GRUVBOX -> R.string.palette_gruvbox
        ThemePalette.CATPPUCCIN -> R.string.palette_catppuccin
        ThemePalette.ROSE_PINE -> R.string.palette_rose_pine
    }

@Composable
fun AppearanceSection(
    appearance: Appearance,
    onModeChange: (ThemeMode) -> Unit,
    onPaletteChange: (ThemePalette) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(R.string.settings_appearance)

        Text(
            text = stringResource(R.string.theme_mode),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = appearance.mode == mode,
                    onClick = { onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    icon = {},
                ) {
                    Text(
                        text = stringResource(mode.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.theme_palette),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        val dark = appearance.isDark()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ThemePalette.entries.forEach { palette ->
                PaletteSwatch(
                    palette = palette,
                    dark = dark,
                    amoled = appearance.mode == ThemeMode.AMOLED,
                    selected = appearance.palette == palette,
                    onClick = { onPaletteChange(palette) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Preview of a palette in the current brightness: background, main accent, meal accent. */
@Composable
private fun PaletteSwatch(
    palette: ThemePalette,
    dark: Boolean,
    amoled: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = colorSchemeFor(palette, dark, amoled)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                ),
        ) {
            Row(Modifier.size(52.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight().background(scheme.background))
                Box(Modifier.weight(1f).fillMaxHeight().background(scheme.primary))
                Box(Modifier.weight(1f).fillMaxHeight().background(scheme.tertiary))
            }
            if (selected) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(palette.labelRes),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/**
 * App language choice, independent of the phone's. AppCompat remembers it
 * and recreates the screen; on Android 13+ it also appears in system settings.
 */
@Composable
fun LanguageSection() {
    var showDialog by remember { mutableStateOf(false) }
    val current = AppCompatDelegate.getApplicationLocales()
        .takeUnless { it.isEmpty }
        ?.get(0)
        ?.language

    SectionTitle(R.string.settings_language)
    ListItem(
        headlineContent = {
            Text(current?.let { languageName(it) } ?: stringResource(R.string.language_system))
        },
        leadingContent = { Icon(Icons.Outlined.Language, contentDescription = null) },
        modifier = Modifier.clickable { showDialog = true },
        colors = settingsRowColors(),
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    LanguageOption(
                        label = stringResource(R.string.language_system),
                        selected = current == null,
                        onClick = {
                            showDialog = false
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                        },
                    )
                    SupportedLanguages.forEach { tag ->
                        LanguageOption(
                            label = languageName(tag),
                            selected = current == tag,
                            onClick = {
                                showDialog = false
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Language name in that language itself ("Deutsch", "Español"): recognizable by everyone. */
private fun languageName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

@Composable
internal fun SectionTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}
