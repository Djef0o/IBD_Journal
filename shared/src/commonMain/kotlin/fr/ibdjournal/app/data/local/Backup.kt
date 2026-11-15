package fr.ibdjournal.app.data.local

import androidx.room.deferredTransaction
import androidx.room.immediateTransaction
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import fr.ibdjournal.app.data.model.BackupData

/** Instantané cohérent de toutes les données à sauvegarder (lecture dans une transaction). */
suspend fun AppDatabase.snapshot(): BackupData = useReaderConnection { connection ->
    connection.deferredTransaction {
        BackupData(
            meals = mealDao().getAll().map { it.toMeal() },
            stools = stoolDao().getAll().map { it.toStool() },
            profile = profileDao().get()?.toProfile(),
            medications = medicationDao().getAll().map { it.toMedication() },
            checkups = checkupDao().getAll().map { it.toCheckup() },
        )
    }
}

/**
 * Remplace le journal par [backup], en une transaction. Profil, traitements et contrôles
 * absents (sauvegardes antérieures) sont conservés tels quels.
 */
suspend fun AppDatabase.restore(backup: BackupData) {
    useWriterConnection { connection ->
        connection.immediateTransaction {
            mealDao().deleteAll()
            stoolDao().deleteAll()
            mealDao().insertAll(backup.meals.map { it.toMealEntity() })
            stoolDao().insertAll(backup.stools.map { it.toStoolEntity() })
            backup.profile?.let { profileDao().upsert(it.toProfileEntity()) }
            backup.medications?.let { list ->
                medicationDao().deleteAll()
                medicationDao().insertAll(list.map { it.toMedicationEntity() })
            }
            backup.checkups?.let { list ->
                checkupDao().deleteAll()
                checkupDao().insertAll(list.map { it.toCheckupEntity() })
            }
        }
    }
}
