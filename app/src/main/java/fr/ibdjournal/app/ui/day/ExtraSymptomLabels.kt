package fr.ibdjournal.app.ui.day

import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.ExtraSymptom

/** Name of the manifestation, in the app's language. */
internal fun ExtraSymptom.labelRes(): Int = when (this) {
    ExtraSymptom.JOINTS -> R.string.extra_symptom_joints
    ExtraSymptom.SKIN -> R.string.extra_symptom_skin
    ExtraSymptom.EYES -> R.string.extra_symptom_eyes
    ExtraSymptom.MOUTH_ULCERS -> R.string.extra_symptom_mouth_ulcers
    ExtraSymptom.FEVER -> R.string.extra_symptom_fever
}
