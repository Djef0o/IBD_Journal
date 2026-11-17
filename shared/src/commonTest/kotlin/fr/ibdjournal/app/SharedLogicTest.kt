package fr.ibdjournal.app

import fr.ibdjournal.app.data.model.DoseLog
import fr.ibdjournal.app.data.model.DoseStatus
import fr.ibdjournal.app.domain.Adherence
import fr.ibdjournal.app.domain.adherence
import fr.ibdjournal.app.domain.dosesOn
import fr.ibdjournal.app.domain.occurrencesBetween
import fr.ibdjournal.app.domain.toEpochMillis
import kotlinx.datetime.atTime
import fr.ibdjournal.app.domain.doseEvent
import fr.ibdjournal.app.domain.refillEvent
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.RefillBasis
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
        // An alarm that just rang must not reschedule itself.
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

    @Test
    fun spacedInjectionAlertsSevenDaysBeforeEachDose() {
        // Adalimumab every 2 weeks since November 2: doses on the 16th, the 30th…
        val injection = Medication(
            name = "Adalimumab",
            schedule = schedule("2026-11-02", 2, RepeatUnit.WEEK),
            refillAlert = true,
        )
        assertEquals(RefillBasis.BEFORE_DOSE, injection.refillBasis)
        assertEquals("2026-11-23T08:00", injection.nextRefillAlert(LocalDateTime.parse("2026-11-15T10:00")).toString())
    }

    @Test
    fun dailyTreatmentAlertsSevenDaysBeforeStockEnd() {
        val daily = Medication(
            name = "Mesalazine",
            schedule = schedule("2026-11-01", 1, RepeatUnit.DAY),
            refillAlert = true,
            stockEndDate = LocalDate.parse("2026-12-01"),
        )
        assertEquals(RefillBasis.STOCK_END, daily.refillBasis)
        assertEquals("2026-11-24T08:00", daily.nextRefillAlert(LocalDateTime.parse("2026-11-15T10:00")).toString())
        // Alert in the past: nothing more until the end date is renewed.
        assertNull(daily.nextRefillAlert(LocalDateTime.parse("2026-11-24T08:00")))
    }

    @Test
    fun calendarEventsMirrorTheReminders() {
        val injection = Medication(
            id = 4,
            name = "Adalimumab",
            schedule = schedule("2026-11-02", 2, RepeatUnit.WEEK),
            refillAlert = true,
        )
        val dose = injection.doseEvent()!!
        val refill = injection.refillEvent()!!

        assertEquals("dose:4", dose.key)
        assertEquals("2026-11-02T08:00", dose.start.toString())
        // Refill series: same rhythm, 7 days before each dose.
        assertEquals("2026-10-26T08:00", refill.start.toString())
        assertEquals(RepeatUnit.WEEK, refill.repeatUnit)
        assertEquals(2, refill.repeatEvery)

        val daily = Medication(
            id = 5,
            name = "Mesalazine",
            schedule = schedule("2026-11-01", 1, RepeatUnit.DAY).copy(enabled = false),
            refillAlert = true,
            stockEndDate = LocalDate.parse("2026-12-01"),
        )
        assertNull(daily.doseEvent())
        assertEquals("2026-11-24T08:00", daily.refillEvent()!!.start.toString())
        assertEquals(RepeatUnit.ONCE, daily.refillEvent()!!.repeatUnit)
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
            stool("2026-11-15", blood = 1), // same day: counted once
            stool("2026-11-01", blood = 1),
            stool("2026-10-10", blood = 3), // previous period (Sept 16 – Oct 16)
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

    /** Backup produced before the shared module (java.time dates in ISO, no profile). */
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

class AdherenceTest {

    private fun schedule(start: String, every: Int, unit: RepeatUnit) =
        ReminderSchedule(startDate = LocalDate.parse(start), hour = 8, minute = 0, repeatEvery = every, repeatUnit = unit)

    @Test
    fun occurrencesFollowTheSameGridAsReminders() {
        val monthly = schedule("2026-01-31", 1, RepeatUnit.MONTH)
        assertEquals(
            listOf("2026-01-31T08:00", "2026-02-28T08:00", "2026-03-31T08:00"),
            monthly.occurrencesBetween(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-03-31")).map { it.toString() },
        )
        val everyTwoWeeks = schedule("2026-11-02", 2, RepeatUnit.WEEK)
        assertEquals(
            listOf("2026-11-02T08:00", "2026-11-16T08:00"),
            everyTwoWeeks.occurrencesBetween(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-20")).map { it.toString() },
        )
    }

    @Test
    fun adherenceCountsOnlyDosesDueSoFar() {
        val medication = Medication(id = 1, name = "Azathioprine", schedule = schedule("2026-11-10", 1, RepeatUnit.DAY))
        val due = medication.schedule.occurrencesBetween(LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-15"))
        val logs = listOf(
            DoseLog(medicationId = 1, scheduledAt = due[0].toEpochMillis(), status = DoseStatus.TAKEN),
            DoseLog(medicationId = 1, scheduledAt = due[1].toEpochMillis(), status = DoseStatus.MISSED),
            DoseLog(medicationId = 2, scheduledAt = due[2].toEpochMillis(), status = DoseStatus.TAKEN),
        )

        // On the 15th at 07:00, the 08:00 dose is not yet due: 10 → 14 = 5 doses.
        val result = adherence(medication, logs, LocalDate.parse("2026-11-01"), LocalDateTime.parse("2026-11-15T07:00"))

        assertEquals(Adherence(expected = 5, taken = 1, missed = 1), result)
        assertEquals(0.2, result.takenRate)
    }

    @Test
    fun dosesOfTheDayCarryTheirConfirmation() {
        val daily = Medication(id = 1, name = "Mesalazine", schedule = schedule("2026-11-01", 1, RepeatUnit.DAY))
        val weekly = Medication(id = 2, name = "Methotrexate", schedule = schedule("2026-11-06", 1, RepeatUnit.WEEK))
        val today = LocalDate.parse("2026-11-13")
        val logs = listOf(DoseLog(medicationId = 1, scheduledAt = today.atTime(8, 0).toEpochMillis(), status = DoseStatus.TAKEN))

        val doses = dosesOn(today, listOf(daily, weekly), logs)

        assertEquals(listOf(1L to DoseStatus.TAKEN, 2L to null), doses.map { it.medication.id to it.status })
    }
}
