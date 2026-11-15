package fr.ibdjournal.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations des bases Android existantes. Une installation iOS part directement de la
 * version courante : ces migrations restent donc côté Android.
 */
object Migrations {

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
