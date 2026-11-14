package fr.ibdjournal.app.ui.settings

import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Lignes de réglages sans fond propre : par défaut un ListItem se peint en `surface`,
 * qui diffère du `background` de l'écran dans la plupart des palettes et faisait
 * apparaître chaque ligne comme un bandeau d'une autre couleur.
 */
@Composable
fun settingsRowColors(): ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
