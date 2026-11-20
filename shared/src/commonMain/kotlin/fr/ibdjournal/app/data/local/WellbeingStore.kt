package fr.ibdjournal.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import fr.ibdjournal.app.data.model.WellbeingDay
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** Sleep, stress, mood and menstrual period of a day (version 13): one row per day. */
@Entity(tableName = "wellbeing")
data class WellbeingEntity(
    @PrimaryKey val dateEpochDay: Long,
    val sleep: Int?,
    val stress: Int?,
    val mood: Int?,
    val period: Boolean,
)

fun WellbeingEntity.toWellbeingDay() = WellbeingDay(LocalDate.fromEpochDays(dateEpochDay), sleep, stress, mood, period)

fun WellbeingDay.toWellbeingEntity() = WellbeingEntity(date.toEpochDays(), sleep, stress, mood, period)

@Dao
interface WellbeingDao {
    @Query("SELECT * FROM wellbeing ORDER BY dateEpochDay")
    fun observeAll(): Flow<List<WellbeingEntity>>

    @Query("SELECT * FROM wellbeing WHERE dateEpochDay = :dateEpochDay")
    fun observe(dateEpochDay: Long): Flow<WellbeingEntity?>

    @Query("SELECT * FROM wellbeing")
    suspend fun getAll(): List<WellbeingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WellbeingEntity)

    @Query("DELETE FROM wellbeing WHERE dateEpochDay = :dateEpochDay")
    suspend fun delete(dateEpochDay: Long)

    @Insert
    suspend fun insertAll(entries: List<WellbeingEntity>)

    @Query("DELETE FROM wellbeing")
    suspend fun deleteAll()
}
