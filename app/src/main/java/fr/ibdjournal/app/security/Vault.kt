package fr.ibdjournal.app.security

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.data.local.AppDatabase
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class VaultState { NEEDS_SETUP, LOCKED, UNLOCKED }

sealed interface PinResult {
    data object Success : PinResult
    /** Wrong PIN; [attemptsLeft] before a waiting time (0: one is already running). */
    data class Wrong(val attemptsLeft: Int) : PinResult
    /** Too many errors: no attempt before [untilMillis]. */
    data class LockedOut(val untilMillis: Long) : PinResult
}

/** Database access while the app is locked. */
class LockedException : IllegalStateException("The journal is locked")

/**
 * Key of the local data. A random 256-bit key encrypts the database (SQLCipher) and the
 * photos; it is stored only wrapped:
 * - by the PIN (PBKDF2, then AES-GCM), the result itself sealed by a Keystore key: trying
 *   PINs requires this phone, and the app imposes waiting times after 5 errors;
 * - optionally by a biometric Keystore key, usable only after a fingerprint.
 * While unlocked, the key and the open database live in memory; [lock] forgets them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class Vault @Inject constructor(@ApplicationContext private val context: Context) {

    private val file = File(context.noBackupFilesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val random = SecureRandom()

    private val _state = MutableStateFlow(if (file.exists()) VaultState.LOCKED else VaultState.NEEDS_SETUP)
    val state: StateFlow<VaultState> = _state.asStateFlow()

    private val _database = MutableStateFlow<AppDatabase?>(null)
    /** Open database while unlocked, null otherwise. */
    val database: StateFlow<AppDatabase?> = _database.asStateFlow()

    @Volatile private var key: ByteArray? = null
    @Volatile private var photoCrypto: PhotoCrypto? = null

    fun requireDatabase(): AppDatabase = _database.value ?: throw LockedException()

    /** [block] on the open database, silent while locked. */
    fun <T> observe(block: (AppDatabase) -> Flow<T>): Flow<T> =
        database.flatMapLatest { db -> db?.let(block) ?: emptyFlow() }

    internal fun photos(): PhotoCrypto = photoCrypto ?: throw LockedException()

    /** First PIN: creates the key, then encrypts the existing data (versions up to 1.4). */
    suspend fun setup(pin: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(_state.value == VaultState.NEEDS_SETUP) { "PIN already set" }
            val newKey = ByteArray(KEY_BYTES).also(random::nextBytes)
            // Saved before encrypting: an interrupted migration resumes at the next unlock.
            write(VaultFile(salt = encode(salt()), iterations = ITERATIONS, pinWrapped = "").withPin(pin, newKey))
            try {
                open(newKey)
            } catch (error: Throwable) {
                // The key file exists: the next attempt goes through the lock screen.
                _state.value = VaultState.LOCKED
                throw error
            }
        }
    }

    suspend fun unlock(pin: String): PinResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_state.value == VaultState.UNLOCKED) return@withLock PinResult.Success
            val vault = read()
            checkPin(vault, pin) { unlockedKey -> open(unlockedKey) }
        }
    }

    /** End of the current waiting time, or null. */
    fun lockedOutUntil(): Long? =
        runCatching { read().lockedUntil }.getOrNull()?.takeIf { it > System.currentTimeMillis() }

    suspend fun changePin(current: String, new: String): PinResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            val vault = read()
            checkPin(vault, current) { currentKey -> write(read().withPin(new, currentKey)) }
        }
    }

    fun lock() {
        val db = _database.value ?: return
        _database.value = null
        photoCrypto = null
        key?.fill(0)
        key = null
        _state.value = VaultState.LOCKED
        db.close()
    }

    // --- Biometric unlock ---

    val biometricEnabled: Boolean get() = runCatching { read().biometricWrapped != null }.getOrDefault(false)

    /**
     * Cipher to authenticate with the fingerprint before [unlockWithBiometric]; null if the
     * fingerprints changed since activation (biometric unlock is then turned off).
     */
    fun biometricUnlockCipher(): Cipher? {
        val sealed = read().biometricWrapped?.let(::decode) ?: return null
        return try {
            KeystoreBox.decryptCipher(KeystoreBox.BIOMETRIC, sealed)
        } catch (_: KeyPermanentlyInvalidatedException) {
            disableBiometricNow()
            null
        }
    }

    suspend fun unlockWithBiometric(cipher: Cipher) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (_state.value == VaultState.UNLOCKED) return@withLock
            val sealed = decode(checkNotNull(read().biometricWrapped))
            open(KeystoreBox.openWith(cipher, sealed))
            write(read().copy(failedAttempts = 0, lockedUntil = 0))
        }
    }

    /** Cipher to authenticate with the fingerprint before [enableBiometric]. */
    fun biometricEnrollCipher(): Cipher {
        KeystoreBox.delete(KeystoreBox.BIOMETRIC)
        return KeystoreBox.encryptCipher(KeystoreBox.BIOMETRIC)
    }

    suspend fun enableBiometric(cipher: Cipher) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = key ?: throw LockedException()
            write(read().copy(biometricWrapped = encode(KeystoreBox.seal(cipher, current))))
        }
    }

    suspend fun disableBiometric() = withContext(Dispatchers.IO) { mutex.withLock { disableBiometricNow() } }

    private fun disableBiometricNow() {
        write(read().copy(biometricWrapped = null))
        KeystoreBox.delete(KeystoreBox.BIOMETRIC)
    }

    /** Forgotten PIN: the encrypted data becomes unreadable for good, it is deleted. */
    suspend fun erase() = withContext(Dispatchers.IO) {
        mutex.withLock {
            lock()
            EncryptedDatabase.delete(context)
            File(context.filesDir, PHOTOS_DIR).deleteRecursively()
            file.delete()
            KeystoreBox.delete(KeystoreBox.BIOMETRIC)
            _state.value = VaultState.NEEDS_SETUP
        }
    }

    // --- Internals ---

    /** Checks [pin] against the waiting times, then runs [onSuccess] with the key. */
    private inline fun checkPin(vault: VaultFile, pin: String, onSuccess: (ByteArray) -> Unit): PinResult {
        val now = System.currentTimeMillis()
        if (vault.lockedUntil > now) return PinResult.LockedOut(vault.lockedUntil)
        val unwrapped = try {
            unwrap(vault, pin)
        } catch (_: AEADBadTagException) {
            null
        }
        if (unwrapped == null) {
            val failed = vault.failedAttempts + 1
            val delay = if (failed < FREE_ATTEMPTS) 0L else DELAYS_MS[minOf(failed - FREE_ATTEMPTS, DELAYS_MS.lastIndex)]
            write(vault.copy(failedAttempts = failed, lockedUntil = if (delay > 0) now + delay else 0))
            return if (delay > 0) PinResult.LockedOut(now + delay) else PinResult.Wrong(FREE_ATTEMPTS - failed)
        }
        onSuccess(unwrapped)
        write(read().copy(failedAttempts = 0, lockedUntil = 0))
        return PinResult.Success
    }

    private fun open(newKey: ByteArray) {
        val db = EncryptedDatabase.open(context, newKey)
        val crypto = PhotoCrypto(newKey)
        crypto.encryptPlainFiles(File(context.filesDir, PHOTOS_DIR))
        key = newKey
        photoCrypto = crypto
        _database.value = db
        _state.value = VaultState.UNLOCKED
    }

    private fun VaultFile.withPin(pin: String, databaseKey: ByteArray): VaultFile {
        val newSalt = salt()
        val inner = aesGcm(Cipher.ENCRYPT_MODE, pinKey(pin, newSalt, ITERATIONS), null, databaseKey)
        return copy(salt = encode(newSalt), iterations = ITERATIONS, pinWrapped = encode(KeystoreBox.seal(inner)))
    }

    private fun unwrap(vault: VaultFile, pin: String): ByteArray {
        val inner = KeystoreBox.open(decode(vault.pinWrapped))
        return aesGcm(Cipher.DECRYPT_MODE, pinKey(pin, decode(vault.salt), vault.iterations), inner, null)
    }

    /** Encrypts [plain] (IV + ciphertext), or decrypts [sealed]. */
    private fun aesGcm(mode: Int, key: SecretKeySpec, sealed: ByteArray?, plain: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        return if (mode == Cipher.ENCRYPT_MODE) {
            val iv = ByteArray(IV_BYTES).also(random::nextBytes)
            cipher.init(mode, key, GCMParameterSpec(TAG_BITS, iv))
            iv + cipher.doFinal(checkNotNull(plain))
        } else {
            val input = checkNotNull(sealed)
            cipher.init(mode, key, GCMParameterSpec(TAG_BITS, input, 0, IV_BYTES))
            cipher.doFinal(input, IV_BYTES, input.size - IV_BYTES)
        }
    }

    private fun pinKey(pin: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BYTES * 8)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun salt() = ByteArray(SALT_BYTES).also(random::nextBytes)

    private fun read(): VaultFile = json.decodeFromString(VaultFile.serializer(), file.readText())

    /** Atomic replacement: a crash never leaves a half-written key file. */
    private fun write(vault: VaultFile) {
        val temp = File(file.path + ".tmp")
        temp.writeText(json.encodeToString(VaultFile.serializer(), vault))
        check(temp.renameTo(file)) { "Could not save the key file" }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)

    @Serializable
    private data class VaultFile(
        val salt: String,
        val iterations: Int,
        /** Database key wrapped by the PIN, then sealed by the device Keystore key. */
        val pinWrapped: String,
        /** Database key sealed by the biometric Keystore key; null = biometric unlock off. */
        val biometricWrapped: String? = null,
        val failedAttempts: Int = 0,
        val lockedUntil: Long = 0,
    )

    companion object {
        const val PIN_MIN = 4
        const val PIN_MAX = 8
        /** Same folder as [fr.ibdjournal.app.data.DiaryRepository]'s photos. */
        const val PHOTOS_DIR = "photos"
        private const val FILE_NAME = "vault.json"
        private const val KEY_BYTES = 32
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val ITERATIONS = 200_000
        /** Errors allowed before the first waiting time. */
        private const val FREE_ATTEMPTS = 5
        private val DELAYS_MS = longArrayOf(30_000, 60_000, 5 * 60_000, 15 * 60_000, 60 * 60_000)
    }
}
