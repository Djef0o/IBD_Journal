package fr.ibdjournal.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MealEntity::class, StoolEntity::class, ProductEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun stoolDao(): StoolDao
    abstract fun productDao(): ProductDao
}
