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

/** Kind of file chosen for import. */
enum class BackupFormat { Encrypted, PlainJson, Unknown }

/**
 * Journal backup: export is always password-encrypted ([BackupCrypto]);
 * import of encrypted files and of old plaintext JSON exports. The content comes from the shared module.
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
            // Streamed writing into the encryption: neither a full JSON string nor a ByteArray copy on the app side.
            BackupCrypto.encrypt(output.buffered(), password).buffered().use { json.encodeToStream(backup, it) }
        }
    }

    /** Reads the start of the file: encrypted signature, old plaintext JSON (`{`) or something else. */
    suspend fun detectFormat(inputStream: InputStream): BackupFormat = withContext(Dispatchers.IO) {
        inputStream.use { detect(it.readPrefix()) }
    }

    /**
     * Decrypts (if [password] is provided for an encrypted file) and decodes the whole file
     * before touching the database: on error, nothing is modified.
     * @throws BackupCrypto.WrongPasswordException wrong password or tampered file
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

        // IDs change on import: first remove the old ones' alarms,
        // then reschedule what the database contains, whether or not the import succeeded.
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
        // Old app export: plaintext JSON starting with "{".
        prefix.firstOrNull { !it.toInt().toChar().isWhitespace() } == '{'.code.toByte() -> BackupFormat.PlainJson
        else -> BackupFormat.Unknown
    }
}
