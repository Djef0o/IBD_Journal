package fr.ibdjournal.app.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Inflammatory bowel disease type, chosen in the profile; null = not provided. */
@Serializable
enum class Diagnosis { CROHN, ULCERATIVE_COLITIS, OTHER }

/**
 * Flare period. [end] null = still ongoing. [treatment]: free text (e.g. "prednisone 40 mg").
 */
@Serializable
data class Flare(
    val id: Long = 0,
    val start: LocalDate,
    val end: LocalDate? = null,
    val treatment: String = "",
    val hospitalized: Boolean = false,
    val note: String = "",
)

@Serializable
enum class VaccineType { INFLUENZA, PNEUMOCOCCAL, COVID19, HEPATITIS_B, HPV, ZOSTER, TETANUS, OTHER }

/**
 * Vaccine given on [date]; [nextDue]: date of the next dose or booster, if planned.
 * [reminderEnabled]: notify a week before [nextDue] and on that day.
 */
@Serializable
data class Vaccination(
    val id: Long = 0,
    val type: VaccineType,
    val date: LocalDate,
    val nextDue: LocalDate? = null,
    val note: String = "",
    val reminderEnabled: Boolean = false,
)

/** [doseMg] per day, during [days] consecutive days. */
@Serializable
data class SteroidStep(val doseMg: Double, val days: Int)

/** Corticosteroid course: a daily dose that decreases by steps from [start]. */
@Serializable
data class SteroidCourse(
    val id: Long = 0,
    val drug: String,
    val start: LocalDate,
    val steps: List<SteroidStep>,
    val note: String = "",
)

/**
 * Emergency sheet. The diagnosis, name and birth date come from the [Profile], the
 * treatments from the treatments list. [showOnLockScreen]: readable without the PIN.
 */
@Serializable
data class EmergencyInfo(
    val allergies: String = "",
    val doctorName: String = "",
    val doctorPhone: String = "",
    val contactName: String = "",
    val contactPhone: String = "",
    val notes: String = "",
    val showOnLockScreen: Boolean = false,
)
