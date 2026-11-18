package fr.ibdjournal.app.ui.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.security.JournalEraser
import fr.ibdjournal.app.security.PinResult
import fr.ibdjournal.app.security.Vault
import fr.ibdjournal.app.security.VaultState
import javax.crypto.Cipher
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Steps of the first PIN, at onboarding or after the update that brings encryption. */
enum class SetupPhase { CHOOSE, CONFIRM, ENCRYPTING, BIOMETRIC_OFFER }

/** PIN, fingerprint and locking; one instance per activity, shared by its screens. */
@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val vault: Vault,
    private val eraser: JournalEraser,
) : ViewModel() {

    val vaultState: StateFlow<VaultState> = vault.state

    private val _setupPhase = MutableStateFlow(SetupPhase.CHOOSE)
    val setupPhase: StateFlow<SetupPhase> = _setupPhase.asStateFlow()

    /** PIN typed at the first step, waiting for its confirmation. */
    private var chosenPin: String? = null

    /** Setup screens shown: PIN not chosen yet, or encryption and fingerprint offer in progress. */
    fun setupVisible(state: VaultState, phase: SetupPhase): Boolean =
        state == VaultState.NEEDS_SETUP || phase == SetupPhase.ENCRYPTING || phase == SetupPhase.BIOMETRIC_OFFER

    fun choosePin(pin: String) {
        chosenPin = pin
        _setupPhase.value = SetupPhase.CONFIRM
    }

    /** false: different from the first one, back to the choice. */
    fun confirmPin(pin: String, biometricAvailable: Boolean): Boolean {
        val chosen = chosenPin
        if (chosen != pin) {
            chosenPin = null
            _setupPhase.value = SetupPhase.CHOOSE
            return false
        }
        _setupPhase.value = SetupPhase.ENCRYPTING
        viewModelScope.launch {
            try {
                vault.setup(chosen)
                chosenPin = null
                _setupPhase.value = if (biometricAvailable) SetupPhase.BIOMETRIC_OFFER else SetupPhase.CHOOSE
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // The key is saved: the lock screen takes over and resumes the encryption.
                _setupPhase.value = SetupPhase.CHOOSE
            }
        }
        return true
    }

    fun finishSetup() {
        _setupPhase.value = SetupPhase.CHOOSE
    }

    suspend fun unlock(pin: String): PinResult = vault.unlock(pin)

    fun lockedOutUntil(): Long? = vault.lockedOutUntil()

    suspend fun changePin(current: String, new: String): PinResult = vault.changePin(current, new)

    // --- Fingerprint ---

    val biometricEnabled: Boolean get() = vault.biometricEnabled

    fun biometricUnlockCipher(): Cipher? = vault.biometricUnlockCipher()

    suspend fun unlockWithBiometric(cipher: Cipher) = vault.unlockWithBiometric(cipher)

    fun biometricEnrollCipher(): Cipher = vault.biometricEnrollCipher()

    suspend fun enableBiometric(cipher: Cipher) = vault.enableBiometric(cipher)

    suspend fun disableBiometric() = vault.disableBiometric()

    fun eraseEverything() {
        viewModelScope.launch { eraser.eraseEverything() }
    }
}
