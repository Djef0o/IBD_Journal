package fr.ibdjournal.app.data

import androidx.room.withTransaction
import fr.ibdjournal.app.data.local.AppDatabase
import fr.ibdjournal.app.data.local.toMeal
import fr.ibdjournal.app.data.local.toMealEntity
import fr.ibdjournal.app.data.local.toStool
import fr.ibdjournal.app.data.local.toStoolEntity
import fr.ibdjournal.app.data.model.BackupData
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
    private val database: AppDatabase
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportData(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        // Lecture dans une transaction : repas et selles forment un instantané cohérent.
        val backup = database.withTransaction {
            BackupData(
                meals = database.mealDao().getAll().map { it.toMeal() },
                stools = database.stoolDao().getAll().map { it.toStool() },
            )
        }
        // Écriture en flux : ni chaîne JSON complète ni copie en ByteArray en mémoire.
        outputStream.buffered().use { json.encodeToStream(backup, it) }
    }

    suspend fun importData(inputStream: InputStream) = withContext(Dispatchers.IO) {
        val backup = inputStream.buffered().use { json.decodeFromStream<BackupData>(it) }

        database.withTransaction {
            database.mealDao().deleteAll()
            database.stoolDao().deleteAll()
            database.mealDao().insertAll(backup.meals.map { it.toMealEntity() })
            database.stoolDao().insertAll(backup.stools.map { it.toStoolEntity() })
        }
    }
}
