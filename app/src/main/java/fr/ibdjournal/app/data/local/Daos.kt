package fr.ibdjournal.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {

    @Query(
        """SELECT * FROM meals WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay
           ORDER BY dateEpochDay, CASE mealType WHEN 'BREAKFAST' THEN 0 WHEN 'LUNCH' THEN 1 ELSE 2 END"""
    )
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<MealEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meal: MealEntity): Long

    @Delete
    suspend fun delete(meal: MealEntity)
}

@Dao
interface StoolDao {

    @Query("SELECT * FROM stools WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay ORDER BY createdAt DESC")
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<StoolEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stool: StoolEntity): Long

    @Query("DELETE FROM stools WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE barcode = :barcode")
    suspend fun get(barcode: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity)
}
