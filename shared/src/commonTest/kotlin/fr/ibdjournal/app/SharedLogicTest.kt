package fr.ibdjournal.app

import fr.ibdjournal.app.data.model.ReminderSchedule
import fr.ibdjournal.app.data.model.RepeatUnit
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.domain.AnalysisPeriod
import fr.ibdjournal.app.domain.Symptom
import fr.ibdjournal.app.domain.analyze
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import fr.ibdjournal.app.data.model.BackupData
import kotlinx.serialization.json.Json
import kotlinx.datetime.LocalDateTime

class ReminderScheduleTest {

    private fun schedule(start: String, every: Int, unit: RepeatUnit, hour: Int = 8) =
        ReminderSchedule(startDate = LocalDate.parse(start), hour = hour, minute = 0, repeatEvery = every, repeatUnit = unit)

    private fun next(schedule: ReminderSchedule, after: String) =
        schedule.nextOccurrence(LocalDateTime.parse(after))?.toString()

    @Test
    fun firstOccurrenceInTheFutureIsTheStartDate() {
        assertEquals("2026-11-20T08:00", next(schedule("2026-11-20", 1, RepeatUnit.DAY), "2026-11-15T10:00"))
    }

    @Test
    fun occurrenceAtExactlyNowIsSkipped() {
        // Une alarme qui vient de sonner ne doit pas se reprogrammer sur elle-même.
        assertEquals("2026-11-16T08:00", next(schedule("2026-11-01", 1, RepeatUnit.DAY), "2026-11-15T08:00"))
    }

    @Test
    fun everyThreeDaysStaysOnTheStartGrid() {
        assertEquals("2026-11-16T08:00", next(schedule("2026-11-01", 3, RepeatUnit.DAY), "2026-11-15T09:00"))
    }

    @Test
    fun everyTwoWeeks() {
        assertEquals(
            "2026-11-16T20:00",
            next(schedule("2026-11-02", 2, RepeatUnit.WEEK, hour = 20), "2026-11-15T21:00"),
        )
    }

    @Test
    fun monthlyOnThe31stClampsThenReturnsToThe31st() {
        val monthly = schedule("2026-01-31", 1, RepeatUnit.MONTH, hour = 9)
        assertEquals("2026-02-28T09:00", next(monthly, "2026-02-01T00:00"))
        assertEquals("2026-03-31T09:00", next(monthly, "2026-02-28T10:00"))
    }

    @Test
    fun onceIsOverAfterItsDate() {
        assertNull(next(schedule("2026-11-01", 1, RepeatUnit.ONCE), "2026-11-15T10:00"))
    }
}

class AnalyzeTest {

    private val today = LocalDate.parse("2026-11-15")

    private fun stool(date: String, type: Int = 4, blood: Int = 0) =
        Stool(date = LocalDate.parse(date), bristolType = type, bloodLevel = blood, createdAt = 0)

    @Test
    fun countsDaysWithBloodAndComparesWithThePreviousPeriod() {
        val history = listOf(
            stool("2026-11-15", blood = 2),
            stool("2026-11-15", blood = 1), // même jour : compté une fois
            stool("2026-11-01", blood = 1),
            stool("2026-10-10", blood = 3), // période précédente (16 sept. – 16 oct.)
            stool("2026-10-20"),
        )

        val summary = analyze(history, emptyList(), today, AnalysisPeriod.LAST_30)
        val blood = summary.symptomDays.single { it.symptom == Symptom.BLOOD }

        assertEquals(30, summary.periodDays)
        assertEquals(4, summary.stools.size)
        assertEquals(2, blood.days)
        assertEquals(1, blood.previousDays)
    }

    @Test
    fun liquidStoolsAreAveragedOverTrackedDays() {
        val history = listOf(stool("2026-11-14", type = 6), stool("2026-11-14", type = 7), stool("2026-11-15", type = 4))

        val summary = analyze(history, emptyList(), today, AnalysisPeriod.LAST_30)

        assertEquals(1.0, summary.liquidPerDay)
    }

    @Test
    fun allHistoryStartsAtFirstEntryWithoutComparison() {
        val history = listOf(stool("2026-11-06", blood = 1), stool("2026-11-15"))

        val summary = analyze(history, emptyList(), today, AnalysisPeriod.ALL)

        assertEquals(10, summary.periodDays)
        assertNull(summary.symptomDays.single { it.symptom == Symptom.BLOOD }.previousDays)
    }
}

class BackupFormatTest {

    /** Sauvegarde produite avant le module partagé (dates java.time en ISO, sans profil). */
    private val legacyBackup = """
        {"meals":[{"id":3,"date":"2026-11-14","type":"LUNCH","description":"Riz"}],
         "stools":[{"id":7,"date":"2026-11-15","bristolType":6,"bloodLevel":1,"createdAt":1794733200000}]}
    """.trimIndent()

    @Test
    fun legacyBackupStillDecodes() {
        val backup = Json { ignoreUnknownKeys = true }.decodeFromString<BackupData>(legacyBackup)

        assertEquals(LocalDate(2026, 11, 14), backup.meals.single().date)
        assertEquals(LocalDate(2026, 11, 15), backup.stools.single().date)
        assertNull(backup.profile)
        assertNull(backup.medications)
    }
}
