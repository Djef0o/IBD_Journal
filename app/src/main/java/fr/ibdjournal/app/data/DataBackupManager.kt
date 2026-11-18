package fr.ibdjournal.app.data

import fr.ibdjournal.app.security.Vault
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

/**
 * Journal backup: export and import are always password-encrypted ([BackupCrypto]).
 * The content comes from the shared module.
 */
@OptIn(ExperimentalSerializationApi::class)
@Singleton
class DataBackupManager @Inject constructor(
    private val vault: Vault,
    private val reminders: TreatmentReminderScheduler,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun exportData(outputStream: OutputStream, password: CharArray) = withContext(Dispatchers.IO) {
        outputStream.use { output ->
            val backup = vault.requireDatabase().snapshot()
            // Streamed writing into the encryption: neither a full JSON string nor a ByteArray copy on the app side.
            BackupCrypto.encrypt(output.buffered(), password).buffered().use { json.encodeToStream(backup, it) }
        }
    }

    /** Reads the start of the file: true if it carries the encrypted backup signature. */
    suspend fun isEncryptedBackup(inputStream: InputStream): Boolean = withContext(Dispatchers.IO) {
        inputStream.use { BackupCrypto.hasMagic(it.readPrefix()) }
    }

    /**
     * Decrypts and decodes the whole file before touching the database: on error, nothing is modified.
     * @throws BackupCrypto.WrongPasswordException wrong password or tampered file
     * @throws BackupCrypto.InvalidBackupException not an encrypted backup
     */
    suspend fun importData(inputStream: InputStream, password: CharArray) = withContext(Dispatchers.IO) {
        val backup = inputStream.buffered().use { stream ->
            val plain = BackupCrypto.decrypt(stream, password)
            try {
                json.decodeFromStream<BackupData>(plain.inputStream())
            } finally {
                plain.fill(0)
            }
        }

        // IDs change on import: first remove the old ones' alarms,
        // then reschedule what the database contains, whether or not the import succeeded.
        reminders.cancelAll()
        try {
            vault.requireDatabase().restore(backup)
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
}
