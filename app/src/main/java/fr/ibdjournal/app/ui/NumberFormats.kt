package fr.ibdjournal.app.ui

import java.util.Locale

/** Nombre à une décimale dans la langue de l'app : « 3,5 » en français, « 3.5 » en anglais. */
fun formatDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
