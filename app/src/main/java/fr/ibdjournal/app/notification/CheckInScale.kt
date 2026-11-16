package fr.ibdjournal.app.notification

import androidx.annotation.StringRes
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.DailyCheckIn
import fr.ibdjournal.app.data.model.Stool

/**
 * Échelles 0 à 5 de la notification du soir, dans l'ordre d'affichage : vues de la section,
 * lecture et écriture de la réponse dans [DailyCheckIn] et dans la selle du rappel.
 */
enum class CheckInScale(
    val headerId: Int,
    val rowId: Int,
    val itemIds: IntArray,
    @StringRes val recordedFormat: Int,
) {
    PAIN(
        R.id.pain_header,
        R.id.row_pain,
        intArrayOf(R.id.pain_0, R.id.pain_1, R.id.pain_2, R.id.pain_3, R.id.pain_4, R.id.pain_5),
        R.string.pain_recorded_format,
    ),
    FATIGUE(
        R.id.fatigue_header,
        R.id.row_fatigue,
        intArrayOf(R.id.fatigue_0, R.id.fatigue_1, R.id.fatigue_2, R.id.fatigue_3, R.id.fatigue_4, R.id.fatigue_5),
        R.string.fatigue_recorded_format,
    );

    fun answer(checkIn: DailyCheckIn): Int? = when (this) {
        PAIN -> checkIn.painLevel
        FATIGUE -> checkIn.fatigueLevel
    }

    fun answered(checkIn: DailyCheckIn, level: Int): DailyCheckIn = when (this) {
        PAIN -> checkIn.copy(painLevel = level)
        FATIGUE -> checkIn.copy(fatigueLevel = level)
    }

    fun applyTo(stool: Stool, level: Int): Stool = when (this) {
        PAIN -> stool.copy(painLevel = level)
        FATIGUE -> stool.copy(fatigueLevel = level)
    }
}
