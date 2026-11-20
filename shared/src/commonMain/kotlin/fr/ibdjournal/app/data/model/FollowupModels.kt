package fr.ibdjournal.app.data.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/** Extra-intestinal manifestations and fever, noted per day. */
@Serializable
enum class ExtraSymptom { JOINTS, SKIN, EYES, MOUTH_ULCERS, FEVER }

/** Manifestations noted on [date]; a day without any has no entry. */
@Serializable
data class ExtraSymptomsDay(val date: LocalDate, val symptoms: Set<ExtraSymptom>)

/** Note to raise at the next appointment; [done] once asked. */
@Serializable
data class DoctorQuestion(
    val id: Long = 0,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long = nowEpochMillis(),
)

@Serializable
enum class AppointmentKind {
    GASTROENTEROLOGIST, DERMATOLOGIST, OPHTHALMOLOGIST, RHEUMATOLOGIST, DIETITIAN, GENERAL_PRACTITIONER, OTHER,
}

/**
 * Medical appointment. [at]: local date and time of the appointment, unchanged if the phone's
 * time zone changes. [place]: doctor or address; [remindersEnabled]: notification the evening
 * before and shortly before.
 */
@Serializable
data class Appointment(
    val id: Long = 0,
    val kind: AppointmentKind,
    val at: LocalDateTime,
    val place: String = "",
    val note: String = "",
    val remindersEnabled: Boolean = true,
)
