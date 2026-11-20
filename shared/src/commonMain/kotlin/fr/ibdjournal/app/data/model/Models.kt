package fr.ibdjournal.app.data.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Current timestamp in milliseconds, available on all platforms. */
@OptIn(ExperimentalTime::class)
fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** The three meal times tracked each day. Labels: platform side. */
@Serializable
enum class MealType { BREAKFAST, LUNCH, DINNER }

/**
 * A meal recorded for a given day. Dates are serialized as ISO ("2026-11-15"),
 * as before the move to kotlinx-datetime: existing backups remain readable.
 */
@Serializable
data class Meal(
    val id: Long = 0,
    val date: LocalDate,
    val type: MealType,
    val description: String,
    val photoUri: String? = null,
    val product: Product? = null,
)

/**
 * A stool of the day with medical details (Bristol scale, blood, etc.). [urgency]: urgent
 * need; [nocturnal]: stool that woke from sleep (criteria of activity scores).
 * [falseUrge]: false urge (tenesmus), trip to the toilet without stool, sometimes with blood
 * or mucus; recorded with [bristolType] 0, it is not counted as a stool.
 */
@Serializable
data class Stool(
    val id: Long = 0,
    val date: LocalDate,
    val bristolType: Int = 4, // 1 to 7
    val bloodLevel: Int = 0, // 0 to 5
    val mucusLevel: Int = 0, // 0 to 5
    val painLevel: Int = 0, // 0 to 5
    val fatigueLevel: Int = 0, // 0 to 5
    val comment: String = "",
    val createdAt: Long = nowEpochMillis(),
    val urgency: Boolean = false,
    val nocturnal: Boolean = false,
    val falseUrge: Boolean = false,
) {
    /** Real stool: neither a stool-free day (type 0) nor a false urge. */
    val isRealStool: Boolean get() = bristolType > 0
}

/** Food product (from Open Food Facts or the local cache). */
@Serializable
data class Product(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val nutriscore: String?,
    val imageUrl: String?,
    val ingredients: String?,
)

/**
 * Root object for JSON export/import. Sections missing (null) from earlier backups
 * leave the current data untouched on import.
 */
@Serializable
data class BackupData(
    val meals: List<Meal>,
    val stools: List<Stool>,
    val profile: Profile? = null,
    val medications: List<Medication>? = null,
    val checkups: List<Checkup>? = null,
    val labResults: List<LabResult>? = null,
    val doseLogs: List<DoseLog>? = null,
    val exams: List<Exam>? = null,
    val weights: List<Weight>? = null,
    val flares: List<Flare>? = null,
    val activityScores: List<ActivityScore>? = null,
    val vaccinations: List<Vaccination>? = null,
    val steroidCourses: List<SteroidCourse>? = null,
    val emergencyInfo: EmergencyInfo? = null,
    val extraSymptoms: List<ExtraSymptomsDay>? = null,
    val doctorQuestions: List<DoctorQuestion>? = null,
    val appointments: List<Appointment>? = null,
)

/** Result of a product lookup by barcode. */
sealed interface ProductLookup {
    data class Found(val product: Product) : ProductLookup
    data class NotFound(val barcode: String) : ProductLookup
}
