package fr.ibdjournal.app.report

import fr.ibdjournal.app.data.model.ActivityScore
import fr.ibdjournal.app.data.model.Appointment
import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.DoctorQuestion
import fr.ibdjournal.app.data.model.DoseLog
import fr.ibdjournal.app.data.model.ExtraSymptom
import fr.ibdjournal.app.data.model.ExtraSymptomsDay
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.data.model.LabTest
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.Profile
import fr.ibdjournal.app.data.model.Flare
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.data.model.SteroidCourse
import fr.ibdjournal.app.data.model.Vaccination
import fr.ibdjournal.app.domain.Adherence
import fr.ibdjournal.app.domain.AnalysisPeriod
import fr.ibdjournal.app.domain.AnalysisSummary
import fr.ibdjournal.app.domain.LabStatus
import fr.ibdjournal.app.domain.ReferenceRange
import fr.ibdjournal.app.domain.adherence
import fr.ibdjournal.app.domain.WellbeingAverages
import fr.ibdjournal.app.domain.WellbeingLink
import fr.ibdjournal.app.domain.wellbeingAverages
import fr.ibdjournal.app.domain.wellbeingLinks
import fr.ibdjournal.app.data.model.WellbeingDay
import fr.ibdjournal.app.domain.analyze
import fr.ibdjournal.app.domain.end
import fr.ibdjournal.app.domain.reference
import fr.ibdjournal.app.domain.startFrom
import fr.ibdjournal.app.domain.status
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus

/** Beyond this, bars would become unreadable on an A4 page: "All" is limited to 90 days. */
const val REPORT_CHART_MAX_DAYS = 90

/** Number of lab panels (analysis dates) included in the report table. */
const val REPORT_LAB_PANELS = 4

/** Treatment and adherence over the report period. */
data class TreatmentReport(val medication: Medication, val adherence: Adherence)

/** Upcoming check-up, at its next due date. */
data class UpcomingCheckup(val checkup: Checkup, val at: LocalDateTime)

/** Result of a lab table cell; [status] null within the laboratory's values or without them. */
data class LabCell(val result: LabResult, val status: LabStatus?)

/**
 * Lab table row: one cell per date of [LabPanels.dates] (null without a measurement that
 * day); [reference]: laboratory values of the row's most recent result, null without them.
 */
data class LabPanelRow(val test: LabTest, val cells: List<LabCell?>, val reference: ReferenceRange?)

/**
 * Latest lab panels, from most recent to oldest: only analyses measured at least
 * once appear. [notes]: results from these panels carrying a note, in table order.
 */
data class LabPanels(val dates: List<LocalDate>, val rows: List<LabPanelRow>, val notes: List<LabResult>)

/** Number of days with [symptom] over the report period, and the most recent one. */
data class ExtraSymptomReport(val symptom: ExtraSymptom, val days: Int, val last: LocalDate)

/** Means over the period and the factors that came with bad days; see [wellbeingAverages], [wellbeingLinks]. */
data class WellbeingReport(val averages: WellbeingAverages, val links: List<WellbeingLink>)

private fun wellbeingReport(days: List<WellbeingDay>, stools: List<Stool>, from: LocalDate, to: LocalDate): WellbeingReport? {
    val averages = wellbeingAverages(days, from, to)
    if (averages.entries == 0) return null
    return WellbeingReport(averages, wellbeingLinks(days.filter { it.date in from..to }, stools))
}

/** Appointments listed in the report: the next ones from today. */
const val REPORT_APPOINTMENTS = 3

/** Everything the report contains, already computed: PDF rendering only does the layout. */
data class ReportData(
    val profile: Profile,
    val period: AnalysisPeriod,
    /** First day of the period (first day with an entry for "All"). */
    val from: LocalDate,
    val to: LocalDate,
    val generatedAt: LocalDateTime,
    val summary: AnalysisSummary,
    /** First day of the daily chart, which ends at [to]. */
    val chartFrom: LocalDate,
    val treatments: List<TreatmentReport>,
    /** Latest panels up to [to], whatever the period: analyses are spaced out. */
    val labPanels: LabPanels,
    /** Upcoming check-ups, from nearest to furthest. */
    val upcomingCheckups: List<UpcomingCheckup>,
    /** Flares overlapping the period, oldest first. */
    val flares: List<Flare>,
    /** Activity scores within the period, oldest first. */
    val activityScores: List<ActivityScore>,
    /** Steroid courses overlapping the period, oldest first. */
    val steroidCourses: List<SteroidCourse>,
    /** All vaccinations, most recent first. */
    val vaccinations: List<Vaccination>,
    /** Extra-intestinal manifestations over the period, in [ExtraSymptom] order; only those noted. */
    val extraSymptoms: List<ExtraSymptomReport>,
    /** Questions not yet asked, in the repository's order. */
    val doctorQuestions: List<DoctorQuestion>,
    /** Next [REPORT_APPOINTMENTS] appointments from now, chronological. */
    val appointments: List<Appointment>,
    /** Sleep, stress, mood and period over the period; null when none was noted. */
    val wellbeing: WellbeingReport?,
)

/** Builds the report for [period] ending on the day of [now], from the raw data. */
fun reportData(
    period: AnalysisPeriod,
    now: LocalDateTime,
    profile: Profile,
    stools: List<Stool>,
    meals: List<Meal>,
    medications: List<Medication>,
    doseLogs: List<DoseLog>,
    labResults: List<LabResult>,
    checkups: List<Checkup>,
    flares: List<Flare> = emptyList(),
    activityScores: List<ActivityScore> = emptyList(),
    steroidCourses: List<SteroidCourse> = emptyList(),
    vaccinations: List<Vaccination> = emptyList(),
    extraSymptoms: List<ExtraSymptomsDay> = emptyList(),
    doctorQuestions: List<DoctorQuestion> = emptyList(),
    appointments: List<Appointment> = emptyList(),
    wellbeing: List<WellbeingDay> = emptyList(),
): ReportData {
    val today = now.date
    val summary = analyze(stools, meals, today, period)
    val from = today.minus(summary.periodDays - 1, DateTimeUnit.DAY)
    val chartDays = summary.periodDays.coerceAtMost(REPORT_CHART_MAX_DAYS)
    // Without a bound ("All"), analyses and adherence go back beyond the first logged stool.
    val periodStart = period.startFrom(today)
    return ReportData(
        profile = profile,
        period = period,
        from = from,
        to = today,
        generatedAt = now,
        summary = summary,
        chartFrom = today.minus(chartDays - 1, DateTimeUnit.DAY),
        treatments = medications.map { medication ->
            TreatmentReport(
                medication = medication,
                adherence = adherence(medication, doseLogs, periodStart ?: medication.schedule.startDate, now),
            )
        },
        labPanels = labPanels(labResults.filter { it.date <= today }),
        upcomingCheckups = checkups
            .mapNotNull { checkup -> checkup.schedule.nextOccurrence(now)?.let { UpcomingCheckup(checkup, it) } }
            .sortedBy { it.at },
        flares = flares
            .filter { it.start <= today && (it.end ?: today) >= from }
            .sortedBy { it.start },
        activityScores = activityScores
            .filter { it.date in from..today }
            .sortedWith(compareBy({ it.date }, { it.id })),
        steroidCourses = steroidCourses
            .filter { it.start <= today && it.end >= from }
            .sortedBy { it.start },
        vaccinations = vaccinations.sortedByDescending { it.date },
        extraSymptoms = extraSymptomReports(extraSymptoms.filter { it.date in from..today }),
        doctorQuestions = doctorQuestions.filter { !it.done },
        appointments = appointments
            .filter { it.at >= now }
            .sortedBy { it.at }
            .take(REPORT_APPOINTMENTS),
        wellbeing = wellbeingReport(wellbeing, stools, from, today),
    )
}

/** One entry per manifestation noted at least one day, in enum order. */
private fun extraSymptomReports(days: List<ExtraSymptomsDay>): List<ExtraSymptomReport> =
    ExtraSymptom.entries.mapNotNull { symptom ->
        val matching = days.filter { symptom in it.symptoms }
        val last = matching.maxOfOrNull { it.date } ?: return@mapNotNull null
        ExtraSymptomReport(symptom, matching.map { it.date }.distinct().size, last)
    }

/** Table of the last [REPORT_LAB_PANELS] analysis dates, rows in [LabTest] order. */
private fun labPanels(results: List<LabResult>): LabPanels {
    val dates = results.map { it.date }.distinct().sortedDescending().take(REPORT_LAB_PANELS)
    // Two entries of the same measurement on the same day: the last one saved wins.
    val byTestAndDate = results
        .filter { it.date in dates }
        .groupBy { it.test to it.date }
        .mapValues { (_, sameDay) -> sameDay.maxBy { it.id } }
    val rows = LabTest.entries.mapNotNull { test ->
        val perDate = dates.map { date -> byTestAndDate[test to date] }
        val latest = perDate.firstNotNullOfOrNull { it } ?: return@mapNotNull null
        LabPanelRow(
            test = test,
            cells = perDate.map { result -> result?.let { LabCell(it, it.status()) } },
            reference = latest.reference(),
        )
    }
    val notes = dates.flatMap { date ->
        rows.mapNotNull { row -> byTestAndDate[row.test to date]?.takeIf { it.note.isNotBlank() } }
    }
    return LabPanels(dates, rows, notes)
}
