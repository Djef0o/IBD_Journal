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

/** Export/import JSON (fichier ou cloud) ; le contenu vient du module partagé. */
@OptIn(ExperimentalSerializationApi::class)
@Singleton
class DataBackupManager @Inject constructor(
    private val database: AppDatabase,
    private val reminders: TreatmentReminderScheduler,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportData(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val backup = database.snapshot()
        // Écriture en flux : ni chaîne JSON complète ni copie en ByteArray en mémoire.
        outputStream.buffered().use { json.encodeToStream(backup, it) }
    }

    suspend fun importData(inputStream: InputStream) = withContext(Dispatchers.IO) {
        val backup = inputStream.buffered().use { json.decodeFromStream<BackupData>(it) }

        // Les identifiants changent à l'import : on retire d'abord les alarmes des anciens,
        // puis on replanifie ce que contient la base, que l'import ait abouti ou non.
        reminders.cancelAll()
        try {
            database.restore(backup)
        } finally {
            reminders.rescheduleAll()
        }
    }
}
