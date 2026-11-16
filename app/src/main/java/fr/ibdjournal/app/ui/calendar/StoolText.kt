package fr.ibdjournal.app.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Stool

/** « Selle · Type 4 », ou « Fausse envie ». */
@Composable
fun stoolTitle(stool: Stool): String =
    if (stool.falseUrge) stringResource(R.string.false_urge) else stringResource(R.string.stool_title, stool.bristolType)

/**
 * Signe le plus notable d'une selle (sang, glaires, douleur), sinon le commentaire.
 * L'urgence et le réveil nocturne s'ajoutent en suffixe : ils comptent autant pour le suivi.
 */
@Composable
fun stoolSummary(stool: Stool): String {
    val main = when {
        stool.bloodLevel > 0 -> stringResource(R.string.stool_flag_blood)
        stool.mucusLevel > 0 -> stringResource(R.string.stool_flag_mucus)
        stool.painLevel > 0 -> stringResource(R.string.stool_flag_pain, stool.painLevel)
        stool.comment.isNotBlank() -> stool.comment
        else -> null
    }
    val urgency = if (stool.urgency) stringResource(R.string.stool_urgency) else null
    val nocturnal = if (stool.nocturnal) stringResource(R.string.stool_nocturnal) else null
    val parts = listOfNotNull(main, urgency, nocturnal)
    return if (parts.isEmpty()) stringResource(R.string.stool_flag_none) else parts.joinToString(" · ")
}
