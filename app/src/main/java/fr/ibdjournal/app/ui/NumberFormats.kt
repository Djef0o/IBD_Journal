package fr.ibdjournal.app.ui

import java.util.Locale

/** Number with one decimal in the app's language: "3,5" in French, "3.5" in English. */
fun formatDecimal(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
