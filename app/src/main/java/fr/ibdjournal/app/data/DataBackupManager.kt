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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataBackupManager @Inject constructor(
    private val database: AppDatabase
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportData(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val meals = database.mealDao().getAll().map { it.toMeal() }
        val stools = database.stoolDao().getAll().map { it.toStool() }
        val backup = BackupData(meals = meals, stools = stools)
        val jsonString = json.encodeToString(backup)
        outputStream.use { it.write(jsonString.toByteArray()) }
    }

    suspend fun importData(inputStream: InputStream) = withContext(Dispatchers.IO) {
        val jsonString = inputStream.use { it.readBytes().decodeToString() }
        val backup = json.decodeFromString<BackupData>(jsonString)

        database.withTransaction {
            database.mealDao().deleteAll()
            database.stoolDao().deleteAll()

            backup.meals.forEach { database.mealDao().upsert(it.toMealEntity()) }
            backup.stools.forEach { database.stoolDao().upsert(it.toStoolEntity()) }
        }
    }
}
