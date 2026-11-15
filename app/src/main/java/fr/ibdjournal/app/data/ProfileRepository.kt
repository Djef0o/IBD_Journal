package fr.ibdjournal.app.data

import fr.ibdjournal.app.data.local.AppDatabase
import fr.ibdjournal.app.data.local.toCheckup
import fr.ibdjournal.app.data.local.toCheckupEntity
import fr.ibdjournal.app.data.local.toMedication
import fr.ibdjournal.app.data.local.toMedicationEntity
import fr.ibdjournal.app.data.local.toProfile
import fr.ibdjournal.app.data.local.toProfileEntity
import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.Profile
import fr.ibdjournal.app.notification.TreatmentReminderScheduler
import fr.ibdjournal.app.notification.TreatmentReminderScheduler.Kind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Profil, traitements et contrôles ; chaque écriture tient les rappels à jour. */
@Singleton
class ProfileRepository @Inject constructor(
    database: AppDatabase,
    private val reminders: TreatmentReminderScheduler,
) {
    private val profileDao = database.profileDao()
    private val medicationDao = database.medicationDao()
    private val checkupDao = database.checkupDao()

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
        reminders.cancel(Kind.MEDICATION, id)
        medicationDao.deleteById(id)
    }

    suspend fun saveCheckup(checkup: Checkup) {
        val id = checkupDao.upsert(checkup.toCheckupEntity())
        reminders.schedule(checkup.copy(id = id))
    }

    suspend fun deleteCheckup(id: Long) {
        reminders.cancel(Kind.CHECKUP, id)
        checkupDao.deleteById(id)
    }
}
