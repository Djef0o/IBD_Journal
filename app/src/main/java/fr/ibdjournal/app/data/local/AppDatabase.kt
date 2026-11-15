package fr.ibdjournal.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MealEntity::class,
        StoolEntity::class,
        ProductEntity::class,
        ProfileEntity::class,
        MedicationEntity::class,
        CheckupEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun stoolDao(): StoolDao
    abstract fun productDao(): ProductDao
    abstract fun profileDao(): ProfileDao
    abstract fun medicationDao(): MedicationDao
    abstract fun checkupDao(): CheckupDao

    companion object {
        /**
         * Ajout du profil, des traitements et des contrôles. Migration explicite : la
         * reconstruction destructive effacerait le journal (repas et selles) existant.
         * Le SQL reprend celui que Room génère pour ces entités (validé à l'ouverture).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER NOT NULL, " +
                        "`firstName` TEXT NOT NULL, `lastName` TEXT NOT NULL, " +
                        "`birthDateEpochDay` INTEGER, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `dosage` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        SCHEDULE_COLUMNS + ")"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checkups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        SCHEDULE_COLUMNS + ")"
                )
            }
        }

        private const val SCHEDULE_COLUMNS =
            "`reminderEnabled` INTEGER NOT NULL, `startDateEpochDay` INTEGER NOT NULL, " +
                "`hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, " +
                "`repeatEvery` INTEGER NOT NULL, `repeatUnit` TEXT NOT NULL"
    }
}
