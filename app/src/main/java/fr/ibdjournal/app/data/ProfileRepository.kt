package fr.ibdjournal.app.data

import fr.ibdjournal.app.data.local.AppDatabase
import fr.ibdjournal.app.data.local.toCheckup
import fr.ibdjournal.app.data.local.toCheckupEntity
import fr.ibdjournal.app.data.local.toDoseLog
import fr.ibdjournal.app.data.local.toDoseLogEntity
import fr.ibdjournal.app.data.local.toLabResult
import fr.ibdjournal.app.data.local.toLabResultEntity
import fr.ibdjournal.app.data.local.toMedication
import fr.ibdjournal.app.data.local.toMedicationEntity
import fr.ibdjournal.app.data.local.toProfile
import fr.ibdjournal.app.data.local.toProfileEntity
import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.DoseLog
import fr.ibdjournal.app.data.model.DoseStatus
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.Profile
import fr.ibdjournal.app.notification.TreatmentReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Profil, traitements, contrôles, résultats d'analyses et prises confirmées ; chaque écriture
 * de traitement ou de contrôle tient les rappels à jour.
 */
@Singleton
class ProfileRepository @Inject constructor(
    database: AppDatabase,
    private val reminders: TreatmentReminderScheduler,
) {
    private val profileDao = database.profileDao()
    private val medicationDao = database.medicationDao()
    private val checkupDao = database.checkupDao()
    private val labResultDao = database.labResultDao()
    private val doseLogDao = database.doseLogDao()

    val profile: Flow<Profile> = profileDao.observe().map { it?.toProfile() ?: Profile() }

    val medications: Flow<List<Medication>> =
        medicationDao.observeAll().map { list -> list.map { it.toMedication() } }

    val checkups: Flow<List<Checkup>> =
        checkupDao.observeAll().map { list -> list.map { it.toCheckup() } }

    suspend fun saveProfile(profile: Profile) = profileDao.upsert(profile.toProfileEntity())

    suspend fun saveMedication(medication: Medication) {
        val id = medicationDao.upsert(medication.toMedicationEntity())
        reminders.schedule(medication.copy(id = id))
    }

    suspend fun deleteMedication(id: Long) {
        reminders.cancelMedication(id)
        medicationDao.deleteById(id)
        doseLogDao.deleteForMedication(id)
    }

    suspend fun saveCheckup(checkup: Checkup) {
        val id = checkupDao.upsert(checkup.toCheckupEntity())
        reminders.schedule(checkup.copy(id = id))
    }

    suspend fun deleteCheckup(id: Long) {
        reminders.cancelCheckup(id)
        checkupDao.deleteById(id)
    }

    // --- Résultats d'analyses ---

    val labResults: Flow<List<LabResult>> =
        labResultDao.observeAll().map { list -> list.map { it.toLabResult() } }

    suspend fun saveLabResult(result: LabResult) {
        labResultDao.upsert(result.toLabResultEntity())
    }

    suspend fun deleteLabResult(id: Long) = labResultDao.deleteById(id)

    // --- Prises confirmées (observance) ---

    val doseLogs: Flow<List<DoseLog>> =
        doseLogDao.observeAll().map { list -> list.map { it.toDoseLog() } }

    /** Confirme une prise prévue ([status] null : annule la confirmation). */
    suspend fun logDose(medicationId: Long, scheduledAt: Long, status: DoseStatus?) {
        if (status == null) {
            doseLogDao.delete(medicationId, scheduledAt)
        } else {
            doseLogDao.upsert(
                DoseLog(medicationId = medicationId, scheduledAt = scheduledAt, status = status).toDoseLogEntity()
            )
        }
    }
}
