package fr.ibdjournal.app.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM keys of the Android Keystore: they never leave the phone's secure hardware,
 * so a copied file cannot be decrypted elsewhere.
 * - [DEVICE]: usable without user action; wraps what must stay readable while the app is
 *   locked (PIN-wrapped key, pending notification answers, reminder schedule).
 * - [BIOMETRIC]: each use requires a strong biometric authentication; invalidated when
 *   the phone's fingerprints change.
 * Sealed format: IV (12 bytes) followed by the ciphertext and its tag.
 */
object KeystoreBox {
    const val DEVICE = "ibdjournal.device"
    const val BIOMETRIC = "ibdjournal.biometric"

    private const val PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    fun seal(plain: ByteArray): ByteArray = seal(encryptCipher(DEVICE), plain)

    fun open(sealed: ByteArray): ByteArray = decryptCipher(DEVICE, sealed).doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)

    /** Cipher for [BIOMETRIC] or [DEVICE], to encrypt; the key is created on first use. */
    fun encryptCipher(alias: String): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key(alias)) }

    /** Cipher to decrypt [sealed]; for [BIOMETRIC], to authenticate before [Cipher.doFinal]. */
    fun decryptCipher(alias: String, sealed: ByteArray): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(alias), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        }

    fun seal(cipher: Cipher, plain: ByteArray): ByteArray = cipher.iv + cipher.doFinal(plain)

    fun openWith(cipher: Cipher, sealed: ByteArray): ByteArray = cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)

    fun delete(alias: String) {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun key(alias: String): SecretKey =
        (keyStore.getKey(alias, null) as SecretKey?) ?: generate(alias)

    private fun generate(alias: String): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply {
                if (alias == BIOMETRIC) {
                    setUserAuthenticationRequired(true)
                    setInvalidatedByBiometricEnrollment(true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                    } else {
                        @Suppress("DEPRECATION")
                        setUserAuthenticationValidityDurationSeconds(-1)
                    }
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }
}
