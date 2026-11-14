package fr.ibdjournal.app.ui.calendar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.ibdjournal.app.R

@Composable
fun BristolIcon(type: Int, modifier: Modifier = Modifier.size(48.dp)) {
    val resId = when (type) {
        0 -> R.drawable.ic_bristol_0
        1 -> R.drawable.ic_bristol_1
        2 -> R.drawable.ic_bristol_2
        3 -> R.drawable.ic_bristol_3
        4 -> R.drawable.ic_bristol_4
        5 -> R.drawable.ic_bristol_5
        6 -> R.drawable.ic_bristol_6
        7 -> R.drawable.ic_bristol_7
        else -> null
    }

    if (resId != null) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = stringResource(R.string.bristol_icon_desc, type),
            modifier = modifier
        )
    }
}
