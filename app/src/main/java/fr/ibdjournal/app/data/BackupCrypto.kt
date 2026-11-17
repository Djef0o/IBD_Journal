package fr.ibdjournal.app.data

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based backup encryption (javax.crypto only, no Android dependency).
 *
 * File format, in order:
 * - `IBDJENC1` (8 ASCII bytes): signature;
 * - PBKDF2 iteration count (32-bit big-endian int): read from the file, so it can be changed later;
 * - random salt (16 bytes);
 * - random IV (12 bytes);
 * - AES-256-GCM ciphertext followed by the authentication tag (128 bits).
 *
 * Key: PBKDF2WithHmacSHA256(password, salt, iterations) → 256 bits.
 * The whole header serves as associated data (AAD): any modification is detected on decryption.
 * Forgotten password = unrecoverable file: no backdoor.
 */
object BackupCrypto {
    const val ITERATIONS = 600_000

    private val MAGIC = "IBDJENC1".toByteArray(Charsets.US_ASCII)
    val MAGIC_SIZE = MAGIC.size
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** Upper bound against a hostile header: avoids an endless key derivation. */
    private const val MAX_ITERATIONS = 10_000_000

    private val random = SecureRandom()

    /** Wrong password or tampered file: GCM cannot tell the two apart. */
    class WrongPasswordException : Exception("Wrong password or damaged file")

    /** Missing or inconsistent header: this is not a readable encrypted backup. */
    class InvalidBackupException(message: String) : IOException(message)

    /** True if [prefix] starts with the signature of an encrypted backup. */
    fun hasMagic(prefix: ByteArray): Boolean =
        prefix.size >= MAGIC.size && prefix.copyOf(MAGIC.size).contentEquals(MAGIC)

    /**
     * Writes the header to [output] then returns a stream that encrypts what is written to it.
     * Closing the returned stream writes the GCM tag and closes [output].
     */
    fun encrypt(output: OutputStream, password: CharArray, iterations: Int = ITERATIONS): OutputStream {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = header(iterations, salt, iv)
        val cipher = cipher(Cipher.ENCRYPT_MODE, password, iterations, salt, iv, header)
        output.write(header)
        return CipherOutputStream(output, cipher)
    }

    /**
     * Reads a complete encrypted backup and returns the plaintext, verified by GCM.
     * @throws WrongPasswordException wrong password or modified/truncated file
     * @throws InvalidBackupException missing or invalid header
     */
    fun decrypt(input: InputStream, password: CharArray): ByteArray {
        val data = DataInputStream(input)
        try {
            val magic = ByteArray(MAGIC.size).also(data::readFully)
            if (!magic.contentEquals(MAGIC)) throw InvalidBackupException("Not an encrypted backup")
            val iterations = data.readInt()
            if (iterations !in 1..MAX_ITERATIONS) throw InvalidBackupException("Invalid iteration count")
            val salt = ByteArray(SALT_BYTES).also(data::readFully)
            val iv = ByteArray(IV_BYTES).also(data::readFully)
            val cipher = cipher(Cipher.DECRYPT_MODE, password, iterations, salt, iv, header(iterations, salt, iv))
            // GCM only releases the plaintext after verifying the tag: reading it fully is mandatory.
            return cipher.doFinal(data.readBytes())
        } catch (e: EOFException) {
            throw InvalidBackupException("Truncated header")
        } catch (e: AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    private fun header(iterations: Int, salt: ByteArray, iv: ByteArray): ByteArray {
        val bytes = ByteArrayOutputStream(MAGIC.size + Int.SIZE_BYTES + SALT_BYTES + IV_BYTES)
        DataOutputStream(bytes).apply {
            write(MAGIC)
            writeInt(iterations)
            write(salt)
            write(iv)
        }
        return bytes.toByteArray()
    }

    private fun cipher(
        mode: Int,
        password: CharArray,
        iterations: Int,
        salt: ByteArray,
        iv: ByteArray,
        header: ByteArray,
    ): Cipher {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val keyBytes = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(mode, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(TAG_BITS, iv))
                updateAAD(header)
            }
        } finally {
            keyBytes.fill(0)
        }
    }
}
