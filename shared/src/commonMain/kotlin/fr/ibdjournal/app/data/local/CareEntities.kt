package fr.ibdjournal.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import fr.ibdjournal.app.data.model.EmergencyInfo
import fr.ibdjournal.app.data.model.Flare
import fr.ibdjournal.app.data.model.SteroidCourse
import fr.ibdjournal.app.data.model.SteroidStep
import fr.ibdjournal.app.data.model.Vaccination
import fr.ibdjournal.app.data.model.VaccineType
import kotlinx.datetime.LocalDate

/** Flares, activity scores, vaccinations, steroid courses and emergency sheet (version 10). */
@Entity(tableName = "flares")
data class FlareEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochDay: Long,
    val endEpochDay: Long?,
    val treatment: String,
    val hospitalized: Boolean,
    val note: String,
)

@Entity(tableName = "vaccinations")
data class VaccinationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: VaccineType,
    val dateEpochDay: Long,
    val nextDueEpochDay: Long?,
    val note: String,
    /** Added in version 11. */
    @ColumnInfo(defaultValue = "0") val reminderEnabled: Boolean = false,
)

/** [steps]: `dose:days` pairs separated by semicolons. */
@Entity(tableName = "steroid_courses")
data class SteroidCourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val drug: String,
    val startEpochDay: Long,
    val steps: String,
    val note: String,
)

/** A single row (id = [EmergencyEntity.SINGLE_ID]). */
@Entity(tableName = "emergency_info")
data class EmergencyEntity(
    @PrimaryKey val id: Int = SINGLE_ID,
    val allergies: String,
    val doctorName: String,
    val doctorPhone: String,
    val contactName: String,
    val contactPhone: String,
    val notes: String,
    val showOnLockScreen: Boolean,
) {
    companion object {
        const val SINGLE_ID = 1
    }
}

fun FlareEntity.toFlare() = Flare(
    id = id,
    start = LocalDate.fromEpochDays(startEpochDay),
    end = endEpochDay?.let { LocalDate.fromEpochDays(it) },
    treatment = treatment,
    hospitalized = hospitalized,
    note = note,
)

fun Flare.toFlareEntity() = FlareEntity(
    id = id,
    startEpochDay = start.toEpochDays(),
    endEpochDay = end?.toEpochDays(),
    treatment = treatment,
    hospitalized = hospitalized,
    note = note,
)

fun VaccinationEntity.toVaccination() = Vaccination(
    id = id,
    type = type,
    date = LocalDate.fromEpochDays(dateEpochDay),
    nextDue = nextDueEpochDay?.let { LocalDate.fromEpochDays(it) },
    note = note,
    reminderEnabled = reminderEnabled,
)

fun Vaccination.toVaccinationEntity() = VaccinationEntity(
    id = id,
    type = type,
    dateEpochDay = date.toEpochDays(),
    nextDueEpochDay = nextDue?.toEpochDays(),
    note = note,
    reminderEnabled = reminderEnabled,
)

fun SteroidCourseEntity.toSteroidCourse() = SteroidCourse(
    id = id,
    drug = drug,
    start = LocalDate.fromEpochDays(startEpochDay),
    steps = steps.split(';').mapNotNull { step ->
        val (dose, days) = step.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        val doseMg = dose.toDoubleOrNull() ?: return@mapNotNull null
        val dayCount = days.toIntOrNull() ?: return@mapNotNull null
        SteroidStep(doseMg, dayCount)
    },
    note = note,
)

fun SteroidCourse.toSteroidCourseEntity() = SteroidCourseEntity(
    id = id,
    drug = drug,
    startEpochDay = start.toEpochDays(),
    steps = steps.joinToString(";") { "${it.doseMg}:${it.days}" },
    note = note,
)

fun EmergencyEntity.toEmergencyInfo() = EmergencyInfo(
    allergies = allergies,
    doctorName = doctorName,
    doctorPhone = doctorPhone,
    contactName = contactName,
    contactPhone = contactPhone,
    notes = notes,
    showOnLockScreen = showOnLockScreen,
)

fun EmergencyInfo.toEmergencyEntity() = EmergencyEntity(
    allergies = allergies,
    doctorName = doctorName,
    doctorPhone = doctorPhone,
    contactName = contactName,
    contactPhone = contactPhone,
    notes = notes,
    showOnLockScreen = showOnLockScreen,
)
