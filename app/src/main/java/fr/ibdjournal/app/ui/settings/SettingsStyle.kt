package fr.ibdjournal.app.ui.settings

import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Settings rows with no background of their own: by default a ListItem paints itself in `surface`,
 * which differs from the screen `background` in most palettes and made
 * each row look like a banner of a different color.
 */
@Composable
fun settingsRowColors(): ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
