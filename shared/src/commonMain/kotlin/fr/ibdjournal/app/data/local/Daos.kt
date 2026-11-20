package fr.ibdjournal.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import fr.ibdjournal.app.data.model.DoseStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {

    @Transaction
    @Query(
        """SELECT * FROM meals WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay
           ORDER BY dateEpochDay, CASE mealType WHEN 'BREAKFAST' THEN 0 WHEN 'LUNCH' THEN 1 ELSE 2 END"""
    )
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<MealWithProduct>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meal: MealEntity): Long

    @Delete
    suspend fun delete(meal: MealEntity)

    @Query("SELECT * FROM meals")
    suspend fun getAll(): List<MealEntity>

    @Insert
    suspend fun insertAll(meals: List<MealEntity>)

    @Query("DELETE FROM meals")
    suspend fun deleteAll()
}

@Dao
interface StoolDao {

    /** Chronological order: the UI displays stools in this order, without re-sorting. */
    @Query("SELECT * FROM stools WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay ORDER BY dateEpochDay, createdAt")
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<StoolEntity>>

    @Query("SELECT * FROM stools ORDER BY dateEpochDay, createdAt")
    fun observeAll(): Flow<List<StoolEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stool: StoolEntity): Long

    @Query("DELETE FROM stools WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM stools WHERE id = :id")
    suspend fun get(id: Long): StoolEntity?

    @Query("SELECT * FROM stools")
    suspend fun getAll(): List<StoolEntity>

    @Insert
    suspend fun insertAll(stools: List<StoolEntity>)

    @Query("DELETE FROM stools")
    suspend fun deleteAll()
}

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE barcode = :barcode")
    suspend fun get(barcode: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity)
}

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile WHERE id = ${ProfileEntity.SINGLE_ID}")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = ${ProfileEntity.SINGLE_ID}")
    suspend fun get(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface MedicationDao {

    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE, createdAt")
    fun observeAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications")
    suspend fun getAll(): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun get(id: Long): MedicationEntity?

    /** Moves the intakes left by [delta] (never below 0); medications that are not counted stay as they are. */
    @Query("UPDATE medications SET stockDoses = MAX(0, stockDoses + :delta) WHERE id = :id AND stockDoses IS NOT NULL")
    suspend fun adjustStock(id: Long, delta: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(medication: MedicationEntity): Long

    @Query("DELETE FROM medications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(medications: List<MedicationEntity>)

    @Query("DELETE FROM medications")
    suspend fun deleteAll()
}

@Dao
interface CheckupDao {

    @Query("SELECT * FROM checkups ORDER BY startDateEpochDay, hour, minute")
    fun observeAll(): Flow<List<CheckupEntity>>

    @Query("SELECT * FROM checkups")
    suspend fun getAll(): List<CheckupEntity>

    @Query("SELECT * FROM checkups WHERE id = :id")
    suspend fun get(id: Long): CheckupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checkup: CheckupEntity): Long

    @Query("DELETE FROM checkups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(checkups: List<CheckupEntity>)

    @Query("DELETE FROM checkups")
    suspend fun deleteAll()
}

@Dao
interface LabResultDao {

    @Query("SELECT * FROM lab_results ORDER BY dateEpochDay, id")
    fun observeAll(): Flow<List<LabResultEntity>>

    @Query("SELECT * FROM lab_results")
    suspend fun getAll(): List<LabResultEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: LabResultEntity): Long

    @Query("DELETE FROM lab_results WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(results: List<LabResultEntity>)

    @Query("DELETE FROM lab_results")
    suspend fun deleteAll()
}

@Dao
interface DoseLogDao {

    @Query("SELECT * FROM dose_logs ORDER BY scheduledAt")
    fun observeAll(): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs")
    suspend fun getAll(): List<DoseLogEntity>

    /** Replaces any existing confirmation of the same occurrence (unique index). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: DoseLogEntity): Long

    @Query("DELETE FROM dose_logs WHERE medicationId = :medicationId AND scheduledAt = :scheduledAt")
    suspend fun delete(medicationId: Long, scheduledAt: Long)

    @Query("SELECT status FROM dose_logs WHERE medicationId = :medicationId AND scheduledAt = :scheduledAt")
    suspend fun statusOf(medicationId: Long, scheduledAt: Long): DoseStatus?

    @Query("DELETE FROM dose_logs WHERE medicationId = :medicationId")
    suspend fun deleteForMedication(medicationId: Long)

    @Insert
    suspend fun insertAll(logs: List<DoseLogEntity>)

    @Query("DELETE FROM dose_logs")
    suspend fun deleteAll()
}

@Dao
interface WeightDao {

    @Query("SELECT * FROM weights ORDER BY dateEpochDay, id")
    fun observeAll(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weights")
    suspend fun getAll(): List<WeightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(weight: WeightEntity): Long

    @Query("DELETE FROM weights WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(weights: List<WeightEntity>)

    @Query("DELETE FROM weights")
    suspend fun deleteAll()
}
