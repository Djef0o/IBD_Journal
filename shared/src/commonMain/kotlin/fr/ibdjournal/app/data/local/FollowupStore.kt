package fr.ibdjournal.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import fr.ibdjournal.app.data.model.Appointment
import fr.ibdjournal.app.data.model.AppointmentKind
import fr.ibdjournal.app.data.model.DoctorQuestion
import fr.ibdjournal.app.data.model.ExtraSymptom
import fr.ibdjournal.app.data.model.ExtraSymptomsDay
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** Extra-intestinal symptoms, questions for the doctor and appointments (version 12). */

/** One row per day; [symptoms]: enum names separated by commas. */
@Entity(tableName = "extra_symptoms")
data class ExtraSymptomEntity(
    @PrimaryKey val dateEpochDay: Long,
    val symptoms: String,
)

@Entity(tableName = "doctor_questions")
data class DoctorQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val done: Boolean,
    val createdAt: Long,
)

/** [at]: ISO local date and time ("2026-12-04T09:30"), as for exams. */
@Entity(tableName = "appointments")
data class AppointmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: AppointmentKind,
    val at: String,
    val place: String,
    val note: String,
    val remindersEnabled: Boolean,
)

fun ExtraSymptomEntity.toExtraSymptomsDay() = ExtraSymptomsDay(
    date = LocalDate.fromEpochDays(dateEpochDay),
    symptoms = symptoms.split(',').mapNotNull { name -> ExtraSymptom.entries.firstOrNull { it.name == name.trim() } }.toSet(),
)

fun ExtraSymptomsDay.toExtraSymptomEntity() = ExtraSymptomEntity(
    dateEpochDay = date.toEpochDays(),
    symptoms = symptoms.sorted().joinToString(",") { it.name },
)

fun DoctorQuestionEntity.toDoctorQuestion() = DoctorQuestion(id = id, text = text, done = done, createdAt = createdAt)

fun DoctorQuestion.toDoctorQuestionEntity() = DoctorQuestionEntity(id = id, text = text, done = done, createdAt = createdAt)

fun AppointmentEntity.toAppointment() = Appointment(
    id = id,
    kind = kind,
    at = LocalDateTime.parse(at),
    place = place,
    note = note,
    remindersEnabled = remindersEnabled,
)

fun Appointment.toAppointmentEntity() = AppointmentEntity(
    id = id,
    kind = kind,
    at = at.toString(),
    place = place,
    note = note,
    remindersEnabled = remindersEnabled,
)

@Dao
interface ExtraSymptomDao {
    @Query("SELECT * FROM extra_symptoms ORDER BY dateEpochDay")
    fun observeAll(): Flow<List<ExtraSymptomEntity>>

    @Query("SELECT * FROM extra_symptoms WHERE dateEpochDay = :dateEpochDay")
    fun observe(dateEpochDay: Long): Flow<ExtraSymptomEntity?>

    @Query("SELECT * FROM extra_symptoms")
    suspend fun getAll(): List<ExtraSymptomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ExtraSymptomEntity)

    @Query("DELETE FROM extra_symptoms WHERE dateEpochDay = :dateEpochDay")
    suspend fun delete(dateEpochDay: Long)

    @Insert
    suspend fun insertAll(entries: List<ExtraSymptomEntity>)

    @Query("DELETE FROM extra_symptoms")
    suspend fun deleteAll()
}

@Dao
interface DoctorQuestionDao {
    @Query("SELECT * FROM doctor_questions ORDER BY done, createdAt, id")
    fun observeAll(): Flow<List<DoctorQuestionEntity>>

    @Query("SELECT * FROM doctor_questions")
    suspend fun getAll(): List<DoctorQuestionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(question: DoctorQuestionEntity): Long

    @Query("DELETE FROM doctor_questions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(questions: List<DoctorQuestionEntity>)

    @Query("DELETE FROM doctor_questions")
    suspend fun deleteAll()
}

@Dao
interface AppointmentDao {
    @Query("SELECT * FROM appointments ORDER BY at, id")
    fun observeAll(): Flow<List<AppointmentEntity>>

    @Query("SELECT * FROM appointments")
    suspend fun getAll(): List<AppointmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(appointment: AppointmentEntity): Long

    @Query("DELETE FROM appointments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(appointments: List<AppointmentEntity>)

    @Query("DELETE FROM appointments")
    suspend fun deleteAll()
}
