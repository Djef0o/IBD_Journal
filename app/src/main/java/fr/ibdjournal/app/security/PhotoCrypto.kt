package fr.ibdjournal.app.security

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Meal photos in internal storage, encrypted with AES-256-GCM. Key derived from the database
 * key ([Vault]): readable only while the app is unlocked.
 * File format: `IBDJPH1` signature, IV (12 bytes), then ciphertext and tag.
 */
internal class PhotoCrypto(databaseKey: ByteArray) {

    private val key = SecretKeySpec(
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(databaseKey, "HmacSHA256")) }
            .doFinal("ibdjournal-photos-v1".toByteArray(Charsets.US_ASCII)),
        "AES",
    )

    fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
        return MAGIC + iv + cipher.doFinal(plain)
    }

    fun decrypt(sealed: ByteArray): ByteArray {
        require(isEncrypted(sealed)) { "Not an encrypted photo" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, MAGIC.size, IV_BYTES))
        }
        val offset = MAGIC.size + IV_BYTES
        return cipher.doFinal(sealed, offset, sealed.size - offset)
    }

    /** Encrypts the photos still stored in clear (versions up to 1.4); idempotent. */
    fun encryptPlainFiles(directory: File) {
        directory.listFiles()?.filter { it.isFile && !it.name.endsWith(TEMP_SUFFIX) }?.forEach { file ->
            val content = file.readBytes()
            if (isEncrypted(content)) return@forEach
            val temp = File(file.path + TEMP_SUFFIX)
            temp.writeBytes(encrypt(content))
            check(temp.renameTo(file)) { "Could not replace ${file.name} with its encrypted copy" }
        }
    }

    private companion object {
        val MAGIC = "IBDJPH1".toByteArray(Charsets.US_ASCII)
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TEMP_SUFFIX = ".encrypting"
        val random = SecureRandom()

        fun isEncrypted(content: ByteArray): Boolean =
            content.size >= MAGIC.size && content.copyOf(MAGIC.size).contentEquals(MAGIC)
    }
}
