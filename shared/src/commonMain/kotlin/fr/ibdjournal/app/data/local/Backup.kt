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
            labResults = labResultDao().getAll().map { it.toLabResult() },
            doseLogs = doseLogDao().getAll().map { it.toDoseLog() },
            exams = examDao().getAll().map { it.toExam() },
        )
    }
}

/**
 * Remplace le journal par [backup], en une transaction. Les sections absentes des
 * sauvegardes antérieures (profil, traitements, contrôles, résultats, prises, examens) sont conservées.
 * Les identifiants des traitements sont conservés : les prises confirmées y restent liées.
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
            backup.labResults?.let { list ->
                labResultDao().deleteAll()
                labResultDao().insertAll(list.map { it.toLabResultEntity() })
            }
            backup.doseLogs?.let { list ->
                doseLogDao().deleteAll()
                doseLogDao().insertAll(list.map { it.toDoseLogEntity() })
            }
            backup.exams?.let { list ->
                examDao().deleteAll()
                examDao().insertAll(list.map { it.toExamEntity() })
            }
        }
    }
}
