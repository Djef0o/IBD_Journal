package fr.ibdjournal.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations for existing Android databases. An iOS install starts directly at the
 * current version: these migrations therefore stay on the Android side.
 */
object Migrations {

    /**
     * Adds the profile, treatments and check-ups. Explicit migration: destructive
     * rebuilding would erase the existing journal (meals and stools).
     * The SQL mirrors what Room generates for these entities (validated on open).
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

    /** Treatment refill alert: disabled for existing treatments. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `medications` ADD COLUMN `refillAlert` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `medications` ADD COLUMN `stockEndEpochDay` INTEGER")
        }
    }

    /**
     * Urgency and nocturnal stools, lab results, confirmed doses. The SQL of the
     * tables mirrors what Room generates for these entities (validated on open).
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `stools` ADD COLUMN `urgency` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `stools` ADD COLUMN `nocturnal` INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `lab_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`test` TEXT NOT NULL, `value` REAL NOT NULL, `dateEpochDay` INTEGER NOT NULL, " +
                    "`note` TEXT NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_lab_results_test_dateEpochDay` " +
                    "ON `lab_results` (`test`, `dateEpochDay`)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dose_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`medicationId` INTEGER NOT NULL, `scheduledAt` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, `loggedAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_dose_logs_medicationId_scheduledAt` " +
                    "ON `dose_logs` (`medicationId`, `scheduledAt`)"
            )
        }
    }

    /**
     * Threshold and reference values of results, profile sex. Nullable columns: nothing to
     * fill in for existing data. Results imported before this version carried the threshold
     * in the note, translated into the language of the moment: we convert it to [qualifier].
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `lab_results` ADD COLUMN `qualifier` TEXT")
            db.execSQL("ALTER TABLE `lab_results` ADD COLUMN `referenceLow` REAL")
            db.execSQL("ALTER TABLE `lab_results` ADD COLUMN `referenceHigh` REAL")
            db.execSQL("ALTER TABLE `profile` ADD COLUMN `sex` TEXT")
            for ((qualifier, notes) in LEGACY_QUALIFIER_NOTES) {
                db.execSQL(
                    "UPDATE `lab_results` SET `qualifier` = ?, `note` = '' " +
                        "WHERE `note` IN (${notes.joinToString { "?" }})",
                    (listOf(qualifier) + notes).toTypedArray<Any>(),
                )
            }
        }
    }

    /** Notes written by the import before version 6, in the app's 6 languages. */
    private val LEGACY_QUALIFIER_NOTES = mapOf(
        "BELOW" to listOf(
            "Below the measurable limit", "Inférieur au seuil de mesure", "Unter der Messgrenze",
            "Por debajo del límite de medición", "Sotto il limite di misura", "Abaixo do limite de medição",
        ),
        "ABOVE" to listOf(
            "Above the measurable limit", "Supérieur au seuil de mesure", "Über der Messgrenze",
            "Por encima del límite de medición", "Sopra il limite di misura", "Acima do limite de medição",
        ),
    )

    /** Exams (colonoscopy, rectosigmoidoscopy) and their preparation. SQL generated by Room for [ExamEntity]. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `exams` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`type` TEXT NOT NULL, `at` TEXT NOT NULL, `note` TEXT NOT NULL, `dietDays` INTEGER NOT NULL, " +
                    "`prepFirstAt` TEXT, `prepSecondAt` TEXT, `anesthesiaAt` TEXT, `remindersEnabled` INTEGER NOT NULL)"
            )
        }
    }

    /** False urges: one column, existing stools are not any. */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `stools` ADD COLUMN `falseUrge` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL = arrayOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)

    private const val SCHEDULE_COLUMNS =
        "`reminderEnabled` INTEGER NOT NULL, `startDateEpochDay` INTEGER NOT NULL, " +
            "`hour` INTEGER NOT NULL, `minute` INTEGER NOT NULL, " +
            "`repeatEvery` INTEGER NOT NULL, `repeatUnit` TEXT NOT NULL"
}
