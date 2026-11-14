package fr.ibdjournal.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /** Ordre chronologique : l'interface affiche les selles dans cet ordre, sans retri. */
    @Query("SELECT * FROM stools WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay ORDER BY dateEpochDay, createdAt")
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<StoolEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stool: StoolEntity): Long

    @Query("DELETE FROM stools WHERE id = :id")
    suspend fun deleteById(id: Long)

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
