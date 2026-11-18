package fr.ibdjournal.app.data

import fr.ibdjournal.app.security.Vault
import fr.ibdjournal.app.data.local.toCheckup
import fr.ibdjournal.app.data.local.toCheckupEntity
import fr.ibdjournal.app.data.local.toExam
import fr.ibdjournal.app.data.local.toExamEntity
import fr.ibdjournal.app.data.model.Exam
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
import fr.ibdjournal.app.data.model.Weight
import fr.ibdjournal.app.data.local.toWeight
import fr.ibdjournal.app.data.local.toWeightEntity
import fr.ibdjournal.app.notification.TreatmentReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Profile, treatments, check-ups, lab results and confirmed doses; each treatment
 * or check-up write keeps the reminders up to date. Reads stay silent and writes fail
 * while the app is locked ([Vault]).
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val vault: Vault,
    private val reminders: TreatmentReminderScheduler,
) {
    private val profileDao get() = vault.requireDatabase().profileDao()
    private val medicationDao get() = vault.requireDatabase().medicationDao()
    private val checkupDao get() = vault.requireDatabase().checkupDao()
    private val labResultDao get() = vault.requireDatabase().labResultDao()
    private val doseLogDao get() = vault.requireDatabase().doseLogDao()
    private val examDao get() = vault.requireDatabase().examDao()
    private val weightDao get() = vault.requireDatabase().weightDao()

    val profile: Flow<Profile> = vault.observe { it.profileDao().observe() }.map { it?.toProfile() ?: Profile() }

    val medications: Flow<List<Medication>> =
        vault.observe { it.medicationDao().observeAll() }.map { list -> list.map { it.toMedication() } }

    val checkups: Flow<List<Checkup>> =
        vault.observe { it.checkupDao().observeAll() }.map { list -> list.map { it.toCheckup() } }

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

    // --- Exams (colonoscopy, rectosigmoidoscopy) ---

    val exams: Flow<List<Exam>> = vault.observe { it.examDao().observeAll() }.map { list -> list.map { it.toExam() } }

    suspend fun saveExam(exam: Exam) {
        val id = examDao.upsert(exam.toExamEntity())
        reminders.schedule(exam.copy(id = id))
    }

    suspend fun deleteExam(id: Long) {
        reminders.cancelExam(id)
        examDao.deleteById(id)
    }

    // --- Lab results ---

    val labResults: Flow<List<LabResult>> =
        vault.observe { it.labResultDao().observeAll() }.map { list -> list.map { it.toLabResult() } }

    suspend fun saveLabResult(result: LabResult) {
        labResultDao.upsert(result.toLabResultEntity())
    }

    suspend fun deleteLabResult(id: Long) = labResultDao.deleteById(id)

    // --- Body weight ---

    val weights: Flow<List<Weight>> =
        vault.observe { it.weightDao().observeAll() }.map { list -> list.map { it.toWeight() } }

    suspend fun saveWeight(weight: Weight) {
        weightDao.upsert(weight.toWeightEntity())
    }

    suspend fun deleteWeight(id: Long) = weightDao.deleteById(id)

    // --- Confirmed doses (adherence) ---

    val doseLogs: Flow<List<DoseLog>> =
        vault.observe { it.doseLogDao().observeAll() }.map { list -> list.map { it.toDoseLog() } }

    /** Confirms a scheduled dose ([status] null: cancels the confirmation). */
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
