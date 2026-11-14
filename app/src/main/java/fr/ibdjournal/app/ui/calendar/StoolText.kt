package fr.ibdjournal.app.ui.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Stool

/** « Selle · Type 4 ». */
@Composable
fun stoolTitle(stool: Stool): String = stringResource(R.string.stool_title, stool.bristolType)

/** Signe le plus notable d'une selle (sang, glaires, douleur), sinon le commentaire. */
@Composable
fun stoolSummary(stool: Stool): String = when {
    stool.bloodLevel > 0 -> stringResource(R.string.stool_flag_blood)
    stool.mucusLevel > 0 -> stringResource(R.string.stool_flag_mucus)
    stool.painLevel > 0 -> stringResource(R.string.stool_flag_pain, stool.painLevel)
    stool.comment.isNotBlank() -> stool.comment
    else -> stringResource(R.string.stool_flag_none)
}
