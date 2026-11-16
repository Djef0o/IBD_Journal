package fr.ibdjournal.app.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [
        MealEntity::class,
        StoolEntity::class,
        ProductEntity::class,
        ProfileEntity::class,
        MedicationEntity::class,
        CheckupEntity::class,
        LabResultEntity::class,
        DoseLogEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun stoolDao(): StoolDao
    abstract fun productDao(): ProductDao
    abstract fun profileDao(): ProfileDao
    abstract fun medicationDao(): MedicationDao
    abstract fun checkupDao(): CheckupDao
    abstract fun labResultDao(): LabResultDao
    abstract fun doseLogDao(): DoseLogDao

    companion object {
        const val NAME = "ibd_diary.db"
    }
}

/** Implémentation générée par Room pour chaque plateforme. */
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
