package fr.ibdjournal.app.ui.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.ibdjournal.app.R

private val CardBackground = Color.Black
private val CardForeground = Color.White
private val CardAccent = Color(0xFFFFD60A)

/**
 * Full-screen card to show to a stranger: neutral message, nothing personal. Fixed colors
 * (not the theme's) for maximum contrast; the screen stays on while it is displayed.
 */
@Composable
fun UrgentNeedCard(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Inside a Dialog, LocalView is the dialog window's own view.
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CardBackground)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Outlined.Wc, contentDescription = null, tint = CardAccent, modifier = Modifier.size(96.dp))
            Text(
                text = stringResource(R.string.urgent_card_title),
                color = CardAccent,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.urgent_card_text),
                color = CardForeground,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CardForeground, contentColor = CardBackground),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.close), fontSize = 18.sp) }
        }
    }
}
