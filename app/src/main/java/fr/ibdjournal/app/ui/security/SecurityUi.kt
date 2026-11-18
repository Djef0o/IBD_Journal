package fr.ibdjournal.app.ui.security

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.ibdjournal.app.R
import fr.ibdjournal.app.security.PinResult
import fr.ibdjournal.app.security.Vault
import fr.ibdjournal.app.security.VaultState
import javax.crypto.Cipher
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- Fingerprint ---

internal fun biometricAvailable(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

internal tailrec fun Context.fragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.fragmentActivity()
    else -> null
}

/** Fingerprint prompt bound to [cipher]: the Keystore key only works after it. */
internal fun FragmentActivity.authenticate(
    cipher: Cipher,
    title: String,
    negative: String,
    onSuccess: (Cipher) -> Unit,
) {
    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let(onSuccess)
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setNegativeButtonText(negative)
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .build()
    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
}

// --- PIN pad ---

/**
 * App keypad rather than the system keyboard: no learning or suggestions on the PIN.
 * The digits stay in this composable only, cleared on every submission.
 */
@Composable
internal fun PinPad(
    title: String,
    message: String?,
    isError: Boolean,
    enabled: Boolean,
    submitLabel: String,
    onSubmit: (String) -> Unit,
    extraKey: (@Composable () -> Unit)? = null,
) {
    var digits by remember { mutableStateOf("") }
    val canSubmit = enabled && digits.length >= Vault.PIN_MIN
    val entered = stringResource(R.string.pin_digits_entered, digits.length)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .height(16.dp)
                .semantics { contentDescription = entered },
        ) {
            repeat(maxOf(Vault.PIN_MIN, digits.length)) { index ->
                val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                Box(
                    Modifier
                        .size(14.dp)
                        .then(
                            if (index < digits.length) Modifier.background(color, CircleShape)
                            else Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        )
                )
            }
        }
        Text(
            text = message.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(40.dp),
        )
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { digit ->
                    DigitKey(digit, enabled && digits.length < Vault.PIN_MAX) { digits += digit }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) { extraKey?.invoke() }
            DigitKey("0", enabled && digits.length < Vault.PIN_MAX) { digits += "0" }
            Box(Modifier.size(KEY_SIZE), contentAlignment = Alignment.Center) {
                IconButton(onClick = { digits = digits.dropLast(1) }, enabled = enabled && digits.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = stringResource(R.string.pin_delete_digit))
                }
            }
        }
        Button(
            onClick = {
                val pin = digits
                digits = ""
                onSubmit(pin)
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(submitLabel) }
    }
}

private val KEY_SIZE = 72.dp

@Composable
private fun DigitKey(digit: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, enabled = enabled, shape = CircleShape, modifier = Modifier.size(KEY_SIZE)) {
        Text(digit, fontSize = 24.sp)
    }
}

// --- First PIN ---

/**
 * Choice and confirmation of the PIN, encryption of the existing data, then fingerprint offer.
 * [upgrade]: shown after the update to a version that encrypts an existing journal.
 */
@Composable
fun SecuritySetupContent(viewModel: SecurityViewModel, upgrade: Boolean) {
    val context = LocalContext.current
    val state by viewModel.vaultState.collectAsStateWithLifecycle()
    val phase by viewModel.setupPhase.collectAsStateWithLifecycle()
    var mismatch by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        when {
            phase == SetupPhase.ENCRYPTING -> {
                Spacer(Modifier.height(48.dp))
                CircularProgressIndicator()
                Text(stringResource(R.string.pin_encrypting), style = MaterialTheme.typography.bodyLarge)
            }
            phase == SetupPhase.BIOMETRIC_OFFER -> {
                Header(Icons.Outlined.Fingerprint, stringResource(R.string.biometric_offer_title), stringResource(R.string.biometric_offer_text))
                val title = stringResource(R.string.biometric_enable_prompt)
                val negative = stringResource(R.string.cancel)
                Button(
                    onClick = {
                        val activity = context.fragmentActivity() ?: return@Button
                        activity.authenticate(viewModel.biometricEnrollCipher(), title, negative) { cipher ->
                            scope.launch {
                                viewModel.enableBiometric(cipher)
                                viewModel.finishSetup()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.biometric_enable)) }
                TextButton(onClick = viewModel::finishSetup) { Text(stringResource(R.string.biometric_skip)) }
            }
            state != VaultState.NEEDS_SETUP -> {
                Header(Icons.Outlined.Lock, stringResource(R.string.pin_set_title), stringResource(R.string.pin_set_text))
            }
            else -> {
                Header(
                    Icons.Outlined.Lock,
                    stringResource(if (upgrade) R.string.security_upgrade_title else R.string.security_setup_title),
                    stringResource(if (upgrade) R.string.security_upgrade_text else R.string.security_setup_text),
                )
                val confirming = phase == SetupPhase.CONFIRM
                PinPad(
                    title = stringResource(if (confirming) R.string.pin_confirm else R.string.pin_choose),
                    message = when {
                        mismatch -> stringResource(R.string.pin_mismatch)
                        else -> stringResource(R.string.pin_length_hint, Vault.PIN_MIN, Vault.PIN_MAX)
                    },
                    isError = mismatch,
                    enabled = true,
                    submitLabel = stringResource(if (confirming) R.string.pin_validate else R.string.onboarding_next),
                    onSubmit = { pin ->
                        if (confirming) {
                            mismatch = !viewModel.confirmPin(pin, biometricAvailable(context))
                        } else {
                            mismatch = false
                            viewModel.choosePin(pin)
                        }
                    },
                )
            }
        }
    }
}

/** Screen shown once after the update, before the journal can be used. */
@Composable
fun SecurityUpgradeScreen(viewModel: SecurityViewModel) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            SecuritySetupContent(viewModel, upgrade = true)
        }
    }
}

@Composable
private fun Header(icon: ImageVector, title: String, text: String) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

// --- Lock screen ---

/** Covers the app while the journal is locked; the screens below keep their state. */
@Composable
fun LockScreen(viewModel: SecurityViewModel) {
    val context = LocalContext.current
    val activity = context.fragmentActivity()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lockedUntil by remember { mutableStateOf(viewModel.lockedOutUntil()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmErase by remember { mutableStateOf(false) }
    val biometric = remember { viewModel.biometricEnabled }

    val wrongPin = stringResource(R.string.pin_wrong)
    val openFailed = stringResource(R.string.pin_open_failed)
    val invalidated = stringResource(R.string.biometric_invalidated)
    val promptTitle = stringResource(R.string.biometric_unlock_prompt)
    val usePin = stringResource(R.string.biometric_use_pin)

    LaunchedEffect(lockedUntil) {
        val until = lockedUntil ?: return@LaunchedEffect
        while (System.currentTimeMillis() < until) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        lockedUntil = null
        message = null
    }

    fun unlockWith(block: suspend () -> PinResult) {
        busy = true
        scope.launch {
            try {
                when (val result = block()) {
                    PinResult.Success -> message = null
                    is PinResult.Wrong -> message = wrongPin
                    is PinResult.LockedOut -> lockedUntil = result.untilMillis
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                message = openFailed
            } finally {
                busy = false
            }
        }
    }

    val promptBiometric: () -> Unit = prompt@{
        val cipher = viewModel.biometricUnlockCipher()
        if (cipher == null) {
            message = invalidated
            return@prompt
        }
        activity?.authenticate(cipher, promptTitle, usePin) { authenticated ->
            unlockWith { viewModel.unlockWithBiometric(authenticated); PinResult.Success }
        }
    }

    LaunchedEffect(Unit) {
        if (biometric && viewModel.lockedOutUntil() == null) promptBiometric()
    }
    // Back leaves the app instead of reaching the screens behind.
    BackHandler { activity?.moveTaskToBack(true) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val until = lockedUntil
            PinPad(
                title = stringResource(R.string.pin_enter),
                message = if (until != null) {
                    val seconds = ((until - now + 999) / 1_000).coerceAtLeast(0)
                    stringResource(R.string.pin_locked_out, "%d:%02d".format(seconds / 60, seconds % 60))
                } else {
                    message
                },
                isError = until != null || message != null,
                enabled = !busy && until == null,
                submitLabel = stringResource(R.string.pin_unlock),
                onSubmit = { pin -> unlockWith { viewModel.unlock(pin) } },
                extraKey = if (biometric) {
                    {
                        IconButton(onClick = promptBiometric, enabled = !busy && until == null) {
                            Icon(Icons.Outlined.Fingerprint, contentDescription = stringResource(R.string.biometric_unlock_button))
                        }
                    }
                } else {
                    null
                },
            )
            TextButton(onClick = { confirmErase = true }) { Text(stringResource(R.string.pin_forgot)) }
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text(stringResource(R.string.pin_forgot_title)) },
            text = { Text(stringResource(R.string.pin_forgot_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmErase = false
                    viewModel.eraseEverything()
                }) { Text(stringResource(R.string.pin_forgot_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
