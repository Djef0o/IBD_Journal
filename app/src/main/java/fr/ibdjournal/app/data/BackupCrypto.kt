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
 * Chiffrement des sauvegardes par mot de passe (javax.crypto seul, sans dépendance Android).
 *
 * Format du fichier, dans l'ordre :
 * - `IBDJENC1` (8 octets ASCII) : signature ;
 * - nombre d'itérations PBKDF2 (int 32 bits big-endian) : lu dans le fichier, donc modifiable plus tard ;
 * - sel aléatoire (16 octets) ;
 * - IV aléatoire (12 octets) ;
 * - texte chiffré AES-256-GCM suivi de l'étiquette d'authentification (128 bits).
 *
 * Clé : PBKDF2WithHmacSHA256(mot de passe, sel, itérations) → 256 bits.
 * L'en-tête entier sert de données associées (AAD) : toute modification est détectée au déchiffrement.
 * Mot de passe oublié = fichier irrécupérable : aucune porte dérobée.
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

    /** Borne d'un en-tête hostile : évite un calcul de clé interminable. */
    private const val MAX_ITERATIONS = 10_000_000

    private val random = SecureRandom()

    /** Mot de passe faux ou fichier altéré : GCM ne permet pas de distinguer les deux. */
    class WrongPasswordException : Exception("Wrong password or damaged file")

    /** En-tête absent ou incohérent : ce n'est pas une sauvegarde chiffrée lisible. */
    class InvalidBackupException(message: String) : IOException(message)

    /** Vrai si [prefix] commence par la signature d'une sauvegarde chiffrée. */
    fun hasMagic(prefix: ByteArray): Boolean =
        prefix.size >= MAGIC.size && prefix.copyOf(MAGIC.size).contentEquals(MAGIC)

    /**
     * Écrit l'en-tête dans [output] puis renvoie un flux qui chiffre ce qu'on y écrit.
     * Fermer le flux renvoyé écrit l'étiquette GCM et ferme [output].
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
     * Lit une sauvegarde chiffrée complète et renvoie le contenu en clair, vérifié par GCM.
     * @throws WrongPasswordException mot de passe faux ou fichier modifié/tronqué
     * @throws InvalidBackupException en-tête absent ou invalide
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
            // GCM ne livre le clair qu'après vérification de l'étiquette : lecture complète obligatoire.
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
