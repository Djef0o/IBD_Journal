package fr.ibdjournal.app.report

import fr.ibdjournal.app.data.model.Checkup
import fr.ibdjournal.app.data.model.DoseLog
import fr.ibdjournal.app.data.model.LabResult
import fr.ibdjournal.app.data.model.LabTest
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.Medication
import fr.ibdjournal.app.data.model.Profile
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.domain.Adherence
import fr.ibdjournal.app.domain.AnalysisPeriod
import fr.ibdjournal.app.domain.AnalysisSummary
import fr.ibdjournal.app.domain.LabStatus
import fr.ibdjournal.app.domain.ReferenceRange
import fr.ibdjournal.app.domain.adherence
import fr.ibdjournal.app.domain.analyze
import fr.ibdjournal.app.domain.reference
import fr.ibdjournal.app.domain.startFrom
import fr.ibdjournal.app.domain.status
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus

/** Au-delà, les barres deviendraient illisibles sur une page A4 : « Tout » se limite à 90 jours. */
const val REPORT_CHART_MAX_DAYS = 90

/** Nombre de bilans (dates d'analyses) repris dans le tableau du rapport. */
const val REPORT_LAB_PANELS = 4

/** Traitement et observance sur la période du rapport. */
data class TreatmentReport(val medication: Medication, val adherence: Adherence)

/** Contrôle à venir, à sa prochaine échéance. */
data class UpcomingCheckup(val checkup: Checkup, val at: LocalDateTime)

/** Résultat d'une cellule du tableau des bilans, avec sa position par rapport aux normes. */
data class LabCell(val result: LabResult, val status: LabStatus)

/**
 * Ligne du tableau des bilans : une cellule par date de [LabPanels.dates] (null sans dosage ce
 * jour-là) ; [reference] est celle du résultat le plus récent de la ligne.
 */
data class LabPanelRow(val test: LabTest, val cells: List<LabCell?>, val reference: ReferenceRange)

/**
 * Derniers bilans, du plus récent au plus ancien : seules figurent les analyses dosées au moins
 * une fois. [notes] : résultats de ces bilans portant une note, dans l'ordre du tableau.
 */
data class LabPanels(val dates: List<LocalDate>, val rows: List<LabPanelRow>, val notes: List<LabResult>)

/**
 * Tout ce que contient le rapport, déjà calculé : le rendu PDF ne fait que mettre en page.
 * Le sexe du [profile] fixe les valeurs usuelles des analyses.
 */
data class ReportData(
    val profile: Profile,
    val period: AnalysisPeriod,
    /** Premier jour de la période (premier jour noté pour « Tout »). */
    val from: LocalDate,
    val to: LocalDate,
    val generatedAt: LocalDateTime,
    val summary: AnalysisSummary,
    /** Premier jour du graphique quotidien, qui se termine à [to]. */
    val chartFrom: LocalDate,
    val treatments: List<TreatmentReport>,
    /** Derniers bilans jusqu'à [to], quelle que soit la période : les analyses sont espacées. */
    val labPanels: LabPanels,
    /** Contrôles à venir, du plus proche au plus lointain. */
    val upcomingCheckups: List<UpcomingCheckup>,
)

/** Assemble le rapport de [period] se terminant le jour de [now], à partir des données brutes. */
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
): ReportData {
    val today = now.date
    val summary = analyze(stools, meals, today, period)
    val from = today.minus(summary.periodDays - 1, DateTimeUnit.DAY)
    val chartDays = summary.periodDays.coerceAtMost(REPORT_CHART_MAX_DAYS)
    // Sans borne (« Tout »), analyses et observance remontent au-delà de la première selle notée.
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
        labPanels = labPanels(labResults.filter { it.date <= today }, profile),
        upcomingCheckups = checkups
            .mapNotNull { checkup -> checkup.schedule.nextOccurrence(now)?.let { UpcomingCheckup(checkup, it) } }
            .sortedBy { it.at },
    )
}

/** Tableau des [REPORT_LAB_PANELS] dernières dates d'analyses, lignes dans l'ordre de [LabTest]. */
private fun labPanels(results: List<LabResult>, profile: Profile): LabPanels {
    val dates = results.map { it.date }.distinct().sortedDescending().take(REPORT_LAB_PANELS)
    // Deux saisies du même dosage le même jour : la dernière enregistrée l'emporte.
    val byTestAndDate = results
        .filter { it.date in dates }
        .groupBy { it.test to it.date }
        .mapValues { (_, sameDay) -> sameDay.maxBy { it.id } }
    val rows = LabTest.entries.mapNotNull { test ->
        val perDate = dates.map { date -> byTestAndDate[test to date] }
        val latest = perDate.firstNotNullOfOrNull { it } ?: return@mapNotNull null
        LabPanelRow(
            test = test,
            cells = perDate.map { result -> result?.let { LabCell(it, it.status(profile.sex)) } },
            reference = latest.reference(profile.sex),
        )
    }
    val notes = dates.flatMap { date ->
        rows.mapNotNull { row -> byTestAndDate[row.test to date]?.takeIf { it.note.isNotBlank() } }
    }
    return LabPanels(dates, rows, notes)
}
