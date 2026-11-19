package fr.ibdjournal.app.data

import fr.ibdjournal.app.data.local.toActivityScore
import fr.ibdjournal.app.data.local.toActivityScoreEntity
import fr.ibdjournal.app.data.local.toEmergencyEntity
import fr.ibdjournal.app.data.local.toEmergencyInfo
import fr.ibdjournal.app.data.local.toFlare
import fr.ibdjournal.app.data.local.toFlareEntity
import fr.ibdjournal.app.data.local.toSteroidCourse
import fr.ibdjournal.app.data.local.toSteroidCourseEntity
import fr.ibdjournal.app.data.local.toVaccination
import fr.ibdjournal.app.data.local.toVaccinationEntity
import fr.ibdjournal.app.data.model.ActivityScore
import fr.ibdjournal.app.data.model.EmergencyInfo
import fr.ibdjournal.app.data.model.Flare
import fr.ibdjournal.app.data.model.SteroidCourse
import fr.ibdjournal.app.data.model.Vaccination
import fr.ibdjournal.app.security.Vault
import fr.ibdjournal.app.notification.TreatmentReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Flares, activity scores, vaccinations, steroid courses and the emergency sheet. Reads stay
 * silent and writes fail while the app is locked ([Vault]).
 */
@Singleton
class CareRepository @Inject constructor(
    private val vault: Vault,
    private val reminders: TreatmentReminderScheduler,
) {

    private val db get() = vault.requireDatabase()

    val flares: Flow<List<Flare>> =
        vault.observe { it.flareDao().observeAll() }.map { list -> list.map { it.toFlare() } }

    suspend fun saveFlare(flare: Flare) {
        db.flareDao().upsert(flare.toFlareEntity())
    }

    suspend fun deleteFlare(id: Long) = db.flareDao().deleteById(id)

    val activityScores: Flow<List<ActivityScore>> =
        vault.observe { it.activityScoreDao().observeAll() }.map { list -> list.map { it.toActivityScore() } }

    suspend fun saveActivityScore(score: ActivityScore) {
        db.activityScoreDao().upsert(score.toActivityScoreEntity())
    }

    suspend fun deleteActivityScore(id: Long) = db.activityScoreDao().deleteById(id)

    val vaccinations: Flow<List<Vaccination>> =
        vault.observe { it.vaccinationDao().observeAll() }.map { list -> list.map { it.toVaccination() } }

    suspend fun saveVaccination(vaccination: Vaccination) {
        val id = db.vaccinationDao().upsert(vaccination.toVaccinationEntity())
        reminders.schedule(vaccination.copy(id = id))
    }

    suspend fun deleteVaccination(id: Long) {
        reminders.cancelVaccination(id)
        db.vaccinationDao().deleteById(id)
    }

    val steroidCourses: Flow<List<SteroidCourse>> =
        vault.observe { it.steroidCourseDao().observeAll() }.map { list -> list.map { it.toSteroidCourse() } }

    suspend fun saveSteroidCourse(course: SteroidCourse) {
        db.steroidCourseDao().upsert(course.toSteroidCourseEntity())
    }

    suspend fun deleteSteroidCourse(id: Long) = db.steroidCourseDao().deleteById(id)

    val emergencyInfo: Flow<EmergencyInfo> =
        vault.observe { it.emergencyDao().observe() }.map { it?.toEmergencyInfo() ?: EmergencyInfo() }

    suspend fun saveEmergencyInfo(info: EmergencyInfo) {
        db.emergencyDao().upsert(info.toEmergencyEntity())
    }
}
