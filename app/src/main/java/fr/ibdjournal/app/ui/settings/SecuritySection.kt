package fr.ibdjournal.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import fr.ibdjournal.app.R
import fr.ibdjournal.app.security.PinResult
import fr.ibdjournal.app.ui.security.PinPad
import fr.ibdjournal.app.ui.security.SecurityViewModel
import fr.ibdjournal.app.ui.security.authenticate
import fr.ibdjournal.app.ui.security.biometricAvailable
import fr.ibdjournal.app.ui.security.fragmentActivity
import kotlinx.coroutines.launch

/** PIN change and fingerprint unlock. */
@Composable
internal fun SecuritySection(viewModel: SecurityViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var changingPin by remember { mutableStateOf(false) }
    var biometric by remember { mutableStateOf(viewModel.biometricEnabled) }
    var pinChanged by remember { mutableStateOf(false) }
    val promptTitle = stringResource(R.string.biometric_enable_prompt)
    val cancel = stringResource(R.string.cancel)

    SectionTitle(R.string.settings_security)
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_change_pin)) },
        supportingContent = if (pinChanged) {
            { Text(stringResource(R.string.pin_changed)) }
        } else {
            null
        },
        modifier = Modifier.clickable { changingPin = true },
        colors = settingsRowColors(),
    )
    if (biometricAvailable(context)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_biometric)) },
            supportingContent = { Text(stringResource(R.string.settings_biometric_desc)) },
            trailingContent = {
                Switch(
                    checked = biometric,
                    onCheckedChange = { enable ->
                        if (enable) {
                            val activity = context.fragmentActivity() ?: return@Switch
                            activity.authenticate(viewModel.biometricEnrollCipher(), promptTitle, cancel) { cipher ->
                                scope.launch {
                                    viewModel.enableBiometric(cipher)
                                    biometric = true
                                }
                            }
                        } else {
                            scope.launch {
                                viewModel.disableBiometric()
                                biometric = false
                            }
                        }
                    },
                )
            },
            colors = settingsRowColors(),
        )
    }

    if (changingPin) {
        ChangePinDialog(
            viewModel = viewModel,
            onDismiss = { changingPin = false },
            onChanged = {
                changingPin = false
                pinChanged = true
            },
        )
    }
}

private enum class ChangeStep { CURRENT, NEW, CONFIRM }

/** Current PIN, new PIN, confirmation; the key itself does not change. */
@Composable
private fun ChangePinDialog(viewModel: SecurityViewModel, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(ChangeStep.CURRENT) }
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val wrongPin = stringResource(R.string.pin_wrong)
    val mismatch = stringResource(R.string.pin_mismatch)
    val lockedOut = stringResource(R.string.pin_locked_out_later)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PinPad(
                    title = stringResource(
                        when (step) {
                            ChangeStep.CURRENT -> R.string.settings_change_pin_current
                            ChangeStep.NEW -> R.string.pin_choose
                            ChangeStep.CONFIRM -> R.string.pin_confirm
                        }
                    ),
                    message = error,
                    isError = error != null,
                    enabled = !busy,
                    submitLabel = stringResource(if (step == ChangeStep.CONFIRM) R.string.pin_validate else R.string.onboarding_next),
                    onSubmit = { pin ->
                        error = null
                        when (step) {
                            ChangeStep.CURRENT -> { current = pin; step = ChangeStep.NEW }
                            ChangeStep.NEW -> { new = pin; step = ChangeStep.CONFIRM }
                            ChangeStep.CONFIRM -> if (pin != new) {
                                error = mismatch
                                step = ChangeStep.NEW
                            } else {
                                busy = true
                                scope.launch {
                                    val result = viewModel.changePin(current, new)
                                    busy = false
                                    when (result) {
                                        PinResult.Success -> onChanged()
                                        is PinResult.Wrong -> { error = wrongPin; step = ChangeStep.CURRENT }
                                        is PinResult.LockedOut -> { error = lockedOut; step = ChangeStep.CURRENT }
                                    }
                                }
                            }
                        }
                    },
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}
