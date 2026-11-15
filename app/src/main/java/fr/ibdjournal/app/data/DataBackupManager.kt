package fr.ibdjournal.app.data

import androidx.room.withTransaction
import fr.ibdjournal.app.data.local.AppDatabase
import fr.ibdjournal.app.data.local.toCheckup
import fr.ibdjournal.app.data.local.toCheckupEntity
import fr.ibdjournal.app.data.local.toMeal
import fr.ibdjournal.app.data.local.toMealEntity
import fr.ibdjournal.app.data.local.toMedication
import fr.ibdjournal.app.data.local.toMedicationEntity
import fr.ibdjournal.app.data.local.toProfile
import fr.ibdjournal.app.data.local.toProfileEntity
import fr.ibdjournal.app.data.local.toStool
import fr.ibdjournal.app.data.local.toStoolEntity
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
        // Lecture dans une transaction : toutes les tables forment un instantané cohérent.
        val backup = database.withTransaction {
            BackupData(
                meals = database.mealDao().getAll().map { it.toMeal() },
                stools = database.stoolDao().getAll().map { it.toStool() },
                profile = database.profileDao().get()?.toProfile(),
                medications = database.medicationDao().getAll().map { it.toMedication() },
                checkups = database.checkupDao().getAll().map { it.toCheckup() },
            )
        }
        // Écriture en flux : ni chaîne JSON complète ni copie en ByteArray en mémoire.
        outputStream.buffered().use { json.encodeToStream(backup, it) }
    }

    suspend fun importData(inputStream: InputStream) = withContext(Dispatchers.IO) {
        val backup = inputStream.buffered().use { json.decodeFromStream<BackupData>(it) }

        // Les identifiants changent à l'import : on retire d'abord les alarmes des anciens,
        // puis on replanifie ce que contient la base, que l'import ait abouti ou non.
        reminders.cancelAll()
        try {
            database.withTransaction {
                database.mealDao().deleteAll()
                database.stoolDao().deleteAll()
                database.mealDao().insertAll(backup.meals.map { it.toMealEntity() })
                database.stoolDao().insertAll(backup.stools.map { it.toStoolEntity() })
                backup.profile?.let { database.profileDao().upsert(it.toProfileEntity()) }
                backup.medications?.let { list ->
                    database.medicationDao().deleteAll()
                    database.medicationDao().insertAll(list.map { it.toMedicationEntity() })
                }
                backup.checkups?.let { list ->
                    database.checkupDao().deleteAll()
                    database.checkupDao().insertAll(list.map { it.toCheckupEntity() })
                }
            }
        } finally {
            reminders.rescheduleAll()
        }
    }
}
