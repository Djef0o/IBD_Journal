package fr.ibdjournal.app

import fr.ibdjournal.app.data.model.BackupData
import fr.ibdjournal.app.data.model.ExtraSymptom
import fr.ibdjournal.app.data.model.ExtraSymptomsDay
import fr.ibdjournal.app.data.model.LabQualifier
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.data.model.LabTest
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.data.model.Weight
import fr.ibdjournal.app.domain.isBackupOverdue
import fr.ibdjournal.app.domain.nextBackupReminder
import fr.ibdjournal.app.domain.toCsvFiles
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CsvExportTest {
    private val day = LocalDate(2026, 11, 18)

    private fun export(
        stools: List<Stool> = emptyList(),
        extras: List<ExtraSymptomsDay> = emptyList(),
        weights: List<Weight> = emptyList(),
        labs: List<LabResult> = emptyList(),
    ) = BackupData(
        meals = emptyList(), stools = stools, extraSymptoms = extras, weights = weights, labResults = labs,
    ).toCsvFiles()

    @Test
    fun stoolsGetAHeaderAndOneLinePerEntryInDateOrder() {
        val files = export(
            stools = listOf(
                Stool(date = LocalDate(2026, 11, 19), bristolType = 6, bloodLevel = 2, urgency = true),
                Stool(date = day, bristolType = 4, comment = "ok"),
            ),
        )
        assertEquals(
            "date,bristol_type,blood,mucus,pain,fatigue,urgency,nocturnal,false_urge,comment\r\n" +
                "2026-11-18,4,0,0,0,0,0,0,0,ok\r\n" +
                "2026-11-19,6,2,0,0,0,1,0,0,\r\n",
            files.getValue("stools.csv"),
        )
    }

    @Test
    fun commasQuotesAndLineBreaksAreQuoted() {
        val csv = export(stools = listOf(Stool(date = day, comment = "pain, \"sharp\"\nat night"))).getValue("stools.csv")
        assertTrue(csv.contains(",\"pain, \"\"sharp\"\"\nat night\"\r\n"))
    }

    @Test
    fun textThatSpreadsheetsWouldRunAsAFormulaIsDefused() {
        val csv = export(stools = listOf(Stool(date = day, comment = "=HYPERLINK(\"x\")"))).getValue("stools.csv")
        assertTrue(csv.contains("'=HYPERLINK"))
        assertFalse(csv.contains(",=HYPERLINK"))
    }

    @Test
    fun symptomsWeightsAndLabResultsKeepTheirUnitsAndFlags() {
        val files = export(
            extras = listOf(ExtraSymptomsDay(day, setOf(ExtraSymptom.JOINTS, ExtraSymptom.FEVER))),
            weights = listOf(Weight(date = day, kg = 71.8)),
            labs = listOf(
                LabResult(test = LabTest.CRP, value = 12.5, date = day, qualifier = LabQualifier.BELOW, referenceHigh = 5.0),
            ),
        )
        assertEquals("date,joint_pain,skin,eyes,mouth_ulcers,fever\r\n2026-11-18,1,0,0,0,1\r\n", files.getValue("extra_symptoms.csv"))
        assertEquals("date,kg\r\n2026-11-18,71.8\r\n", files.getValue("weights.csv"))
        assertEquals(
            "date,test,value,unit,qualifier,reference_low,reference_high,note\r\n2026-11-18,crp,12.5,mg/L,below,,5.0,\r\n",
            files.getValue("lab_results.csv"),
        )
    }

    @Test
    fun missingSectionsOfOldBackupsGiveHeaderOnlyFiles() {
        assertEquals("date,kg\r\n", export().getValue("weights.csv"))
    }
}

class BackupReminderTest {
    @Test
    fun overdueAfterThirtyDaysOrWithoutAnyBackup() {
        val today = LocalDate(2026, 12, 20)
        assertTrue(isBackupOverdue(null, today))
        assertTrue(isBackupOverdue(LocalDate(2026, 11, 20), today))
        assertFalse(isBackupOverdue(LocalDate(2026, 11, 21), today))
    }

    @Test
    fun firstReminderThirtyDaysAfterTheLastBackupAtTenOClock() {
        val next = nextBackupReminder(LocalDate(2026, 11, 20), LocalDate(2026, 1, 1), LocalDateTime(2026, 11, 25, 8, 0))
        assertEquals(LocalDateTime(2026, 12, 20, 10, 0), next)
    }

    @Test
    fun withoutBackupTheCountStartsWhenRemindersWereSetUp() {
        val next = nextBackupReminder(null, LocalDate(2026, 11, 1), LocalDateTime(2026, 11, 10, 8, 0))
        assertEquals(LocalDateTime(2026, 12, 1, 10, 0), next)
    }

    @Test
    fun repeatsEverySevenDaysWhileNoBackupIsMade() {
        val last = LocalDate(2026, 10, 1)
        // Due on Oct 31 at 10:00; asked on Nov 2 and Nov 8: next weekly slots.
        assertEquals(LocalDateTime(2026, 11, 7, 10, 0), nextBackupReminder(last, last, LocalDateTime(2026, 11, 2, 9, 0)))
        assertEquals(LocalDateTime(2026, 11, 14, 10, 0), nextBackupReminder(last, last, LocalDateTime(2026, 11, 8, 9, 0)))
        // Exactly at the slot: strictly after, so the following week.
        assertEquals(LocalDateTime(2026, 11, 14, 10, 0), nextBackupReminder(last, last, LocalDateTime(2026, 11, 7, 10, 0)))
    }
}
