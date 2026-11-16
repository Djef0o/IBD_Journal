package fr.ibdjournal.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import fr.ibdjournal.app.data.model.Exam
import fr.ibdjournal.app.data.model.ExamType
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDateTime

/**
 * Examens (version 7). Dates et heures locales au format ISO (« 2026-12-04T09:30 ») : elles
 * restent celles du rendez-vous même si le fuseau du téléphone change, et se trient comme du texte.
 */
@Entity(tableName = "exams")
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ExamType,
    val at: String,
    val note: String,
    val dietDays: Int,
    val prepFirstAt: String?,
    val prepSecondAt: String?,
    val anesthesiaAt: String?,
    val remindersEnabled: Boolean,
)

fun ExamEntity.toExam() = Exam(
    id = id,
    type = type,
    at = LocalDateTime.parse(at),
    note = note,
    dietDays = dietDays,
    prepFirstAt = prepFirstAt?.let(LocalDateTime::parse),
    prepSecondAt = prepSecondAt?.let(LocalDateTime::parse),
    anesthesiaAt = anesthesiaAt?.let(LocalDateTime::parse),
    remindersEnabled = remindersEnabled,
)

fun Exam.toExamEntity() = ExamEntity(
    id = id,
    type = type,
    at = at.toString(),
    note = note,
    dietDays = dietDays,
    prepFirstAt = prepFirstAt?.toString(),
    prepSecondAt = prepSecondAt?.toString(),
    anesthesiaAt = anesthesiaAt?.toString(),
    remindersEnabled = remindersEnabled,
)

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY at")
    fun observeAll(): Flow<List<ExamEntity>>

    @Query("SELECT * FROM exams")
    suspend fun getAll(): List<ExamEntity>

    @Query("SELECT * FROM exams WHERE id = :id")
    suspend fun get(id: Long): ExamEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(exam: ExamEntity): Long

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(exams: List<ExamEntity>)

    @Query("DELETE FROM exams")
    suspend fun deleteAll()
}
