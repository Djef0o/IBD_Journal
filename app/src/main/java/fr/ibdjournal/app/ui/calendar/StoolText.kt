package fr.ibdjournal.app.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Stool

/** "Stool · Type 4", or "False urge". */
@Composable
fun stoolTitle(stool: Stool): String =
    if (stool.falseUrge) stringResource(R.string.false_urge) else stringResource(R.string.stool_title, stool.bristolType)

/**
 * Most notable sign of a stool (blood, mucus, pain), otherwise the comment.
 * Urgency and night waking are appended as a suffix: they matter just as much for follow-up.
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
