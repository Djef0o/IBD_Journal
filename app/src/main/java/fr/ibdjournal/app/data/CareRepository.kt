package fr.ibdjournal.app.data

import fr.ibdjournal.app.data.local.toAppointment
import fr.ibdjournal.app.data.local.toAppointmentEntity
import fr.ibdjournal.app.data.local.toDoctorQuestion
import fr.ibdjournal.app.data.local.toDoctorQuestionEntity
import fr.ibdjournal.app.data.local.toExtraSymptomEntity
import fr.ibdjournal.app.data.local.toExtraSymptomsDay
import fr.ibdjournal.app.data.local.toWellbeingDay
import fr.ibdjournal.app.data.local.toWellbeingEntity
import fr.ibdjournal.app.data.model.WellbeingDay
import fr.ibdjournal.app.data.local.toEmergencyEntity
import fr.ibdjournal.app.data.local.toEmergencyInfo
import fr.ibdjournal.app.data.local.toFlare
import fr.ibdjournal.app.data.local.toFlareEntity
import fr.ibdjournal.app.data.local.toSteroidCourse
import fr.ibdjournal.app.data.local.toSteroidCourseEntity
import fr.ibdjournal.app.data.local.toVaccination
import fr.ibdjournal.app.data.local.toVaccinationEntity
import fr.ibdjournal.app.data.model.Appointment
import fr.ibdjournal.app.data.model.DoctorQuestion
import fr.ibdjournal.app.data.model.EmergencyInfo
import fr.ibdjournal.app.data.model.ExtraSymptom
import fr.ibdjournal.app.data.model.ExtraSymptomsDay
import fr.ibdjournal.app.data.model.Flare
import fr.ibdjournal.app.data.model.SteroidCourse
import fr.ibdjournal.app.data.model.Vaccination
import fr.ibdjournal.app.security.Vault
import fr.ibdjournal.app.notification.TreatmentReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Flares, activity scores, vaccinations, steroid courses, the emergency sheet, extra-intestinal
 * symptoms, questions for the doctor and appointments. Reads stay silent and writes fail while
 * the app is locked ([Vault]).
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

    // --- Extra-intestinal symptoms (one entry per day) ---

    val extraSymptoms: Flow<List<ExtraSymptomsDay>> =
        vault.observe { it.extraSymptomDao().observeAll() }.map { list -> list.map { it.toExtraSymptomsDay() } }

    /** Symptoms noted on [date] (empty set when none). */
    fun extraSymptomsOn(date: LocalDate): Flow<Set<ExtraSymptom>> =
        vault.observe { it.extraSymptomDao().observe(date.toEpochDays()) }
            .map { it?.toExtraSymptomsDay()?.symptoms.orEmpty() }

    /** Replaces the day's symptoms; an empty set removes the entry. */
    suspend fun saveExtraSymptoms(date: LocalDate, symptoms: Set<ExtraSymptom>) {
        if (symptoms.isEmpty()) {
            db.extraSymptomDao().delete(date.toEpochDays())
        } else {
            db.extraSymptomDao().upsert(ExtraSymptomsDay(date, symptoms).toExtraSymptomEntity())
        }
    }

    // --- Sleep, stress, mood, menstrual period (one entry per day) ---

    val wellbeing: Flow<List<WellbeingDay>> =
        vault.observe { it.wellbeingDao().observeAll() }.map { list -> list.map { it.toWellbeingDay() } }

    /** The day's entry; an empty one when nothing was noted. */
    fun wellbeingOn(date: LocalDate): Flow<WellbeingDay> =
        vault.observe { it.wellbeingDao().observe(date.toEpochDays()) }
            .map { it?.toWellbeingDay() ?: WellbeingDay(date) }

    /** Replaces the day's entry; an empty one removes it. */
    suspend fun saveWellbeing(day: WellbeingDay) {
        if (day.isEmpty) {
            db.wellbeingDao().delete(day.date.toEpochDays())
        } else {
            db.wellbeingDao().upsert(day.toWellbeingEntity())
        }
    }

    // --- Questions for the doctor ---

    /** Open questions first, then answered ones, oldest first. */
    val doctorQuestions: Flow<List<DoctorQuestion>> =
        vault.observe { it.doctorQuestionDao().observeAll() }.map { list -> list.map { it.toDoctorQuestion() } }

    suspend fun saveDoctorQuestion(question: DoctorQuestion) {
        db.doctorQuestionDao().upsert(question.toDoctorQuestionEntity())
    }

    suspend fun deleteDoctorQuestion(id: Long) = db.doctorQuestionDao().deleteById(id)

    // --- Appointments ---

    val appointments: Flow<List<Appointment>> =
        vault.observe { it.appointmentDao().observeAll() }.map { list -> list.map { it.toAppointment() } }

    suspend fun saveAppointment(appointment: Appointment) {
        val id = db.appointmentDao().upsert(appointment.toAppointmentEntity())
        reminders.schedule(appointment.copy(id = id))
    }

    suspend fun deleteAppointment(id: Long) {
        reminders.cancelAppointment(id)
        db.appointmentDao().deleteById(id)
    }
}
