package fr.ibdjournal.app.data

import fr.ibdjournal.app.data.local.AppDatabase
import fr.ibdjournal.app.data.local.restore
import fr.ibdjournal.app.data.local.snapshot
import fr.ibdjournal.app.data.model.BackupData
import fr.ibdjournal.app.notification.TreatmentReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Nature d'un fichier choisi pour l'import. */
enum class BackupFormat { Encrypted, PlainJson, Unknown }

/**
 * Sauvegarde du journal : export toujours chiffré par mot de passe ([BackupCrypto]) ;
 * import des fichiers chiffrés et des anciens exports JSON en clair. Le contenu vient du module partagé.
 */
@OptIn(ExperimentalSerializationApi::class)
@Singleton
class DataBackupManager @Inject constructor(
    private val database: AppDatabase,
    private val reminders: TreatmentReminderScheduler,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun exportData(outputStream: OutputStream, password: CharArray) = withContext(Dispatchers.IO) {
        outputStream.use { output ->
            val backup = database.snapshot()
            // Écriture en flux dans le chiffrement : ni chaîne JSON complète ni copie en ByteArray côté app.
            BackupCrypto.encrypt(output.buffered(), password).buffered().use { json.encodeToStream(backup, it) }
        }
    }

    /** Lit le début du fichier : signature chiffrée, ancien JSON en clair (`{`) ou autre chose. */
    suspend fun detectFormat(inputStream: InputStream): BackupFormat = withContext(Dispatchers.IO) {
        inputStream.use { detect(it.readPrefix()) }
    }

    /**
     * Déchiffre (si [password] est fourni pour un fichier chiffré) et décode tout le fichier
     * avant de toucher à la base : en cas d'erreur, rien n'est modifié.
     * @throws BackupCrypto.WrongPasswordException mot de passe faux ou fichier altéré
     */
    suspend fun importData(inputStream: InputStream, password: CharArray?) = withContext(Dispatchers.IO) {
        val backup = inputStream.buffered().use { stream ->
            stream.mark(BackupCrypto.MAGIC_SIZE)
            val format = detect(stream.readPrefix())
            stream.reset()
            when (format) {
                BackupFormat.Encrypted -> {
                    val plain = BackupCrypto.decrypt(stream, requireNotNull(password) { "Password required" })
                    try {
                        json.decodeFromStream<BackupData>(plain.inputStream())
                    } finally {
                        plain.fill(0)
                    }
                }
                BackupFormat.PlainJson -> json.decodeFromStream<BackupData>(stream)
                BackupFormat.Unknown -> throw BackupCrypto.InvalidBackupException("Unrecognized file")
            }
        }

        // Les identifiants changent à l'import : on retire d'abord les alarmes des anciens,
        // puis on replanifie ce que contient la base, que l'import ait abouti ou non.
        reminders.cancelAll()
        try {
            database.restore(backup)
        } finally {
            reminders.rescheduleAll()
        }
    }

    private fun InputStream.readPrefix(): ByteArray {
        val prefix = ByteArray(BackupCrypto.MAGIC_SIZE)
        var read = 0
        while (read < prefix.size) {
            val n = read(prefix, read, prefix.size - read)
            if (n < 0) break
            read += n
        }
        return prefix.copyOf(read)
    }

    private fun detect(prefix: ByteArray): BackupFormat = when {
        BackupCrypto.hasMagic(prefix) -> BackupFormat.Encrypted
        // Ancien export de l'app : JSON en clair qui commence par « { ».
        prefix.firstOrNull { !it.toInt().toChar().isWhitespace() } == '{'.code.toByte() -> BackupFormat.PlainJson
        else -> BackupFormat.Unknown
    }
}
