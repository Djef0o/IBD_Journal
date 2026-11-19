package fr.ibdjournal.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FlareDao {
    @Query("SELECT * FROM flares ORDER BY startEpochDay, id")
    fun observeAll(): Flow<List<FlareEntity>>

    @Query("SELECT * FROM flares")
    suspend fun getAll(): List<FlareEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(flare: FlareEntity): Long

    @Query("DELETE FROM flares WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(flares: List<FlareEntity>)

    @Query("DELETE FROM flares")
    suspend fun deleteAll()
}

@Dao
interface ActivityScoreDao {
    @Query("SELECT * FROM activity_scores ORDER BY dateEpochDay, id")
    fun observeAll(): Flow<List<ActivityScoreEntity>>

    @Query("SELECT * FROM activity_scores")
    suspend fun getAll(): List<ActivityScoreEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(score: ActivityScoreEntity): Long

    @Query("DELETE FROM activity_scores WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(scores: List<ActivityScoreEntity>)

    @Query("DELETE FROM activity_scores")
    suspend fun deleteAll()
}

@Dao
interface VaccinationDao {
    @Query("SELECT * FROM vaccinations ORDER BY dateEpochDay, id")
    fun observeAll(): Flow<List<VaccinationEntity>>

    @Query("SELECT * FROM vaccinations")
    suspend fun getAll(): List<VaccinationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vaccination: VaccinationEntity): Long

    @Query("DELETE FROM vaccinations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(vaccinations: List<VaccinationEntity>)

    @Query("DELETE FROM vaccinations")
    suspend fun deleteAll()
}

@Dao
interface SteroidCourseDao {
    @Query("SELECT * FROM steroid_courses ORDER BY startEpochDay, id")
    fun observeAll(): Flow<List<SteroidCourseEntity>>

    @Query("SELECT * FROM steroid_courses")
    suspend fun getAll(): List<SteroidCourseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(course: SteroidCourseEntity): Long

    @Query("DELETE FROM steroid_courses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(courses: List<SteroidCourseEntity>)

    @Query("DELETE FROM steroid_courses")
    suspend fun deleteAll()
}

@Dao
interface EmergencyDao {
    @Query("SELECT * FROM emergency_info WHERE id = ${EmergencyEntity.SINGLE_ID}")
    fun observe(): Flow<EmergencyEntity?>

    @Query("SELECT * FROM emergency_info WHERE id = ${EmergencyEntity.SINGLE_ID}")
    suspend fun get(): EmergencyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(info: EmergencyEntity)

    @Query("DELETE FROM emergency_info")
    suspend fun deleteAll()
}
