package fr.ibdjournal.app.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.ReminderSchedule
import fr.ibdjournal.app.data.model.RepeatUnit
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.data.model.Diagnosis
import fr.ibdjournal.app.data.model.ScoreKind
import fr.ibdjournal.app.data.model.VaccineType
import fr.ibdjournal.app.domain.ActivityLevel
import fr.ibdjournal.app.domain.Adherence
import fr.ibdjournal.app.domain.LabStatus
import fr.ibdjournal.app.domain.Symptom
import fr.ibdjournal.app.domain.end
import fr.ibdjournal.app.domain.level
import fr.ibdjournal.app.domain.total
import fr.ibdjournal.app.domain.totalMg
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.display
import fr.ibdjournal.app.ui.displayValue
import fr.ibdjournal.app.ui.formatDecimal
import fr.ibdjournal.app.ui.labelRes
import fr.ibdjournal.app.ui.care.nameRes
import fr.ibdjournal.app.ui.day.labelRes as extraLabelRes
import fr.ibdjournal.app.ui.theme.bristolColor
import java.io.OutputStream
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.yearsUntil

/* A4 in PostScript points (1/72 inch), the unit of PdfDocument. */
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

/** Footer rule; the content stops a bit above it. */
private const val FOOTER_TOP = PAGE_HEIGHT - 48f
private const val CONTENT_BOTTOM = FOOTER_TOP - 12f

private const val SECTION_GAP = 20f
/** Most recent activity scores listed in the compact line of the report. */
private const val ACTIVITY_LIST_MAX = 12
private const val ROW_PADDING = 4f
private const val COLUMN_GAP = 10f
private const val TILE_GAP = 8f
private const val TILE_PADDING = 8f

private const val CHART_HEIGHT = 120f
private const val AXIS_WIDTH = 18f
/** Band above the bars reserved for the "blood" dot, as in the app. */
private const val DOT_ROW = 8f
/** Day numbers then month markers below the axis. */
private const val LABEL_ROWS = 22f
private const val LEGEND_HEIGHT = 12f
/** Minimum gap between two day numbers below the chart. */
private const val MIN_LABEL_SPACING = 14f

/** Lab table: analysis and unit, then one column per date, then the laboratory values. */
private const val LAB_TEST_WIDTH = 150f
private const val LAB_DATE_WIDTH = 62f
/** Tight rows: ~45 analyses must fit on one to two pages. */
private const val LAB_ROW_PADDING = 2.5f

private val INK = 0xFF212121.toInt()
private val MUTED = 0xFF616161.toInt()
private val RULE = 0xFFD6D6D6.toInt()
private val TRACK = 0xFFEEEEEE.toInt()
private val BAR = 0xFF757575.toInt()
private val BLOOD = bristolColor(0).toArgb()

/** Categories stacked from bottom to top, as in the app's "Stools per day" chart. */
private enum class StoolCategory(val types: IntRange, @StringRes val legendRes: Int) {
    HARD(1..2, R.string.analysis_legend_hard),
    NORMAL(3..4, R.string.analysis_legend_normal),
    LOOSE(5..7, R.string.analysis_legend_loose);

    val color: Int get() = bristolColor(types.first).toArgb()
}

/** Type 0 ("no stool") does not count in the bars. */
private class DayBar(val date: LocalDate, stools: List<Stool>) {
    val counts: Map<StoolCategory, Int> = StoolCategory.entries.associateWith { category ->
        stools.count { it.bristolType in category.types }
    }
    val total: Int = counts.values.sum()
    val blood: Boolean = stools.any { it.bloodLevel > 0 }
}

/** Table column: offset from the left margin and width. */
private class Column(
    val x: Float,
    val width: Float,
    val align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
)

/**
 * Table row measured in advance: [draw] draws it at the given ordinate. [keepWith]: height
 * kept on the same page as it (a subheading is not left alone at the bottom of a page).
 */
private class TableRow(val height: Float, val keepWith: Float = 0f, val draw: (top: Float) -> Unit)

/**
 * A4 follow-up report to show the doctor: simple Canvas layout of [ReportData], already
 * computed. [context] is only used for the texts, in the desired language. Sections that no
 * longer fit on the page move to the next one; tables repeat their header.
 */
class ReportPdfBuilder(private val context: Context) {

    private val locale = Locale.getDefault()
    private val percentFormat = NumberFormat.getPercentInstance(locale)

    private val brandPaint = textPaint(9f, MUTED, bold = true)
    private val titlePaint = textPaint(20f, INK, bold = true)
    private val patientPaint = textPaint(11f, INK, bold = true)
    private val sectionPaint = textPaint(13f, INK, bold = true)
    private val bodyPaint = textPaint(9.5f, INK)
    private val strongPaint = textPaint(9.5f, INK, bold = true)
    private val notePaint = textPaint(8f, MUTED)
    private val headerCellPaint = textPaint(8f, MUTED, bold = true)
    private val categoryPaint = textPaint(7.5f, INK, bold = true).apply { letterSpacing = 0.06f }
    private val valuePaint = textPaint(16f, INK, bold = true)
    private val axisPaint = textPaint(6.5f, MUTED)
    private val dayLabelPaint = textPaint(6.5f, MUTED).apply { textAlign = Paint.Align.CENTER }
    private val monthLabelPaint = textPaint(6.5f, MUTED, bold = true).apply { textAlign = Paint.Align.CENTER }
    private val legendPaint = textPaint(7.5f, MUTED)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        color = RULE
    }

    fun write(data: ReportData, out: OutputStream) {
        val document = PdfDocument()
        try {
            Writer(document, data).run {
                header()
                summary()
                symptoms()
                dailyChart()
                extraSymptoms()
                treatments()
                flares()
                activityScores()
                steroidCourses()
                vaccinations()
                labResults()
                checkups()
                appointments()
                doctorQuestions()
                finish()
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    /** Write cursor: current page and ordinate [y] of the next element. */
    private inner class Writer(private val document: PdfDocument, private val data: ReportData) {
        private var pageNumber = 1
        private var page: PdfDocument.Page = document.startPage(pageInfo(pageNumber))
        private val canvas: Canvas get() = page.canvas
        private var y = MARGIN

        fun finish() = closePage()

        private fun closePage() {
            drawFooter()
            document.finishPage(page)
        }

        /** Moves to a new page if [height] no longer fits; true if a new page was opened. */
        private fun ensure(height: Float): Boolean {
            if (y + height <= CONTENT_BOTTOM) return false
            closePage()
            pageNumber++
            page = document.startPage(pageInfo(pageNumber))
            y = MARGIN
            return true
        }

        private fun drawFooter() {
            canvas.drawLine(MARGIN, FOOTER_TOP, MARGIN + CONTENT_WIDTH, FOOTER_TOP, rulePaint)
            val pageWidth = 60f
            val disclaimer = layout(string(R.string.report_footer), notePaint, CONTENT_WIDTH - pageWidth - COLUMN_GAP)
            val pageLabel = layout(
                string(R.string.report_page, pageNumber),
                notePaint,
                pageWidth,
                Layout.Alignment.ALIGN_OPPOSITE,
            )
            canvas.drawLayout(disclaimer, MARGIN, FOOTER_TOP + 6f)
            canvas.drawLayout(pageLabel, MARGIN + CONTENT_WIDTH - pageWidth, FOOTER_TOP + 6f)
        }

        private fun paragraph(text: String, paint: TextPaint, gapAfter: Float = 4f) {
            val layout = layout(text, paint, CONTENT_WIDTH)
            ensure(layout.height.toFloat())
            canvas.drawLayout(layout, MARGIN, y)
            y += layout.height + gapAfter
        }

        /** Section title, kept on the same page as the first [keepWith] points of content. */
        private fun section(title: String, subtitle: String?, keepWith: Float) {
            val titleLayout = layout(title, sectionPaint, CONTENT_WIDTH)
            val subtitleLayout = subtitle?.let { layout(it, notePaint, CONTENT_WIDTH) }
            val height = titleLayout.height + (subtitleLayout?.let { it.height + 2f } ?: 0f) + 8f
            // At the top of a new page, no space before the title.
            if (!ensure(SECTION_GAP + height + keepWith)) y += SECTION_GAP
            canvas.drawLayout(titleLayout, MARGIN, y)
            y += titleLayout.height
            subtitleLayout?.let {
                y += 2f
                canvas.drawLayout(it, MARGIN, y)
                y += it.height
            }
            y += 8f
        }

        private fun table(header: TableRow?, rows: List<TableRow>) {
            header?.let { drawRow(it) }
            rows.forEach { row ->
                if (ensure(row.height + row.keepWith) && header != null) drawRow(header)
                drawRow(row)
            }
        }

        private fun drawRow(row: TableRow) {
            row.draw(y)
            y += row.height
            canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, rulePaint)
        }

        private fun textRow(cells: List<String>, columns: List<Column>, paint: TextPaint): TableRow {
            val layouts = cells.mapIndexed { index, text ->
                layout(text, paint, columns[index].width, columns[index].align)
            }
            return TableRow(layouts.maxOf { it.height } + 2 * ROW_PADDING) { top ->
                layouts.forEachIndexed { index, layout ->
                    canvas.drawLayout(layout, MARGIN + columns[index].x, top + ROW_PADDING)
                }
            }
        }

        // --- Sections ---

        fun header() {
            fun line(text: String, paint: TextPaint, gapAfter: Float) {
                val layout = layout(text, paint, CONTENT_WIDTH)
                canvas.drawLayout(layout, MARGIN, y)
                y += layout.height + gapAfter
            }
            line(string(R.string.app_name), brandPaint, 2f)
            line(string(R.string.report_title), titlePaint, 8f)
            val profile = data.profile
            val name = listOf(profile.firstName, profile.lastName).filter { it.isNotBlank() }.joinToString(" ")
            if (name.isNotEmpty()) line(string(R.string.report_patient, name), patientPaint, 2f)
            // Age and sex on one line.
            val identity = listOfNotNull(
                profile.birthDate?.let { birthDate ->
                    string(
                        R.string.profile_born_on,
                        DateFormats.dayMonthYear(birthDate),
                        plural(R.plurals.profile_age, birthDate.yearsUntil(data.to)),
                    )
                },
                profile.sex?.let { string(it.labelRes) },
            ).joinToString(" · ")
            if (identity.isNotEmpty()) line(identity, bodyPaint, 2f)
            data.profile.diagnosis?.let { line(string(R.string.report_care_diagnosis, diagnosisName(it)), bodyPaint, 2f) }
            line(
                string(
                    R.string.report_period,
                    string(data.period.labelRes),
                    DateFormats.dayMonthYear(data.from),
                    DateFormats.dayMonthYear(data.to),
                ),
                bodyPaint,
                2f,
            )
            line(
                string(
                    R.string.report_generated,
                    DateFormats.dayMonthYear(data.generatedAt.date),
                    DateFormats.time(data.generatedAt.hour, data.generatedAt.minute),
                ),
                notePaint,
                10f,
            )
            canvas.drawLine(MARGIN, y, MARGIN + CONTENT_WIDTH, y, rulePaint)
        }

        fun summary() {
            val summary = data.summary
            val stools = summary.realStools
            // Days logged: at least one stool entry, including "no stool" (same basis as liquid stools).
            val trackedDays = summary.stools.map { it.date }.distinct().size
            val daysFollowed = (summary.stools.map { it.date } + summary.meals.map { it.date }).distinct().size
            val tiles = listOf(
                stools.size.toString() to R.string.analysis_stools,
                (if (trackedDays == 0) "—" else formatDecimal(stools.size.toDouble() / trackedDays)) to
                    R.string.report_stools_per_day,
                (summary.averageBristol?.let(::formatDecimal) ?: "—") to R.string.analysis_avg,
                (summary.liquidPerDay?.let(::formatDecimal) ?: "—") to R.string.analysis_liquid_per_day,
                daysFollowed.toString() to R.string.analysis_days,
            )
            val tileWidth = (CONTENT_WIDTH - TILE_GAP * (tiles.size - 1)) / tiles.size
            val innerWidth = tileWidth - 2 * TILE_PADDING
            val values = tiles.map { (value, _) -> layout(value, valuePaint, innerWidth) }
            val labels = tiles.map { (_, label) -> layout(string(label), notePaint, innerWidth) }
            val valueHeight = values.maxOf { it.height }
            val height = 2 * TILE_PADDING + valueHeight + 2f + labels.maxOf { it.height }

            section(string(R.string.report_summary), null, height)
            tiles.indices.forEach { index ->
                val x = MARGIN + index * (tileWidth + TILE_GAP)
                canvas.drawRoundRect(x, y, x + tileWidth, y + height, 6f, 6f, rulePaint)
                canvas.drawLayout(values[index], x + TILE_PADDING, y + TILE_PADDING)
                canvas.drawLayout(labels[index], x + TILE_PADDING, y + TILE_PADDING + valueHeight + 2f)
            }
            y += height + 6f
            paragraph(string(R.string.report_summary_note), notePaint)
        }

        fun symptoms() {
            val summary = data.summary
            val subtitle = data.period.days?.let { string(R.string.analysis_symptoms_compare, it) }
                ?: string(R.string.analysis_symptoms_days)
            if (summary.stools.isEmpty()) {
                section(string(R.string.analysis_symptoms), null, bodyPaint.textSize * 2)
                paragraph(string(R.string.report_no_data), bodyPaint)
                return
            }
            val label = Column(0f, 175f)
            val bar = Column(185f, 140f)
            val days = Column(335f, 80f, Layout.Alignment.ALIGN_OPPOSITE)
            val previous = Column(425f, CONTENT_WIDTH - 425f, Layout.Alignment.ALIGN_OPPOSITE)
            val compare = data.period.days != null
            val header = textRow(
                listOfNotNull(
                    string(R.string.report_col_symptom),
                    "",
                    string(R.string.report_col_days),
                    if (compare) string(R.string.report_col_previous) else null,
                ),
                listOf(label, bar, days, previous),
                headerCellPaint,
            )
            val rows = summary.symptomDays.map { row ->
                val labelLayout = layout(string(row.symptom.labelRes), bodyPaint, label.width)
                val daysLayout = layout(plural(R.plurals.analysis_symptom_days, row.days), strongPaint, days.width, days.align)
                val previousLayout = row.previousDays?.let { previousDays ->
                    val delta = row.days - previousDays
                    val change = when {
                        delta > 0 -> "+$delta"
                        delta < 0 -> "−${-delta}"
                        else -> "="
                    }
                    layout(
                        string(R.string.report_previous, plural(R.plurals.analysis_symptom_days, previousDays), change),
                        bodyPaint,
                        previous.width,
                        previous.align,
                    )
                }
                val height = max(labelLayout.height, daysLayout.height) + 2 * ROW_PADDING
                TableRow(height) { top ->
                    canvas.drawLayout(labelLayout, MARGIN + label.x, top + ROW_PADDING)
                    canvas.drawLayout(daysLayout, MARGIN + days.x, top + ROW_PADDING)
                    previousLayout?.let { canvas.drawLayout(it, MARGIN + previous.x, top + ROW_PADDING) }
                    // Share of the period's days affected by the symptom.
                    val barTop = top + height / 2 - 3f
                    val barLeft = MARGIN + bar.x
                    fillPaint.color = TRACK
                    canvas.drawRoundRect(barLeft, barTop, barLeft + bar.width, barTop + 6f, 3f, 3f, fillPaint)
                    val share = (row.days.toFloat() / summary.periodDays).coerceIn(0f, 1f)
                    if (share > 0f) {
                        fillPaint.color = if (row.symptom == Symptom.BLOOD) BLOOD else BAR
                        canvas.drawRoundRect(barLeft, barTop, barLeft + bar.width * share, barTop + 6f, 3f, 3f, fillPaint)
                    }
                }
            }
            section(string(R.string.analysis_symptoms), subtitle, header.height + rows.first().height)
            table(header, rows)
        }

        fun dailyChart() {
            val byDate = data.summary.stoolHistory
                .filter { it.date >= data.chartFrom && it.date <= data.to }
                .groupBy { it.date }
            if (byDate.isEmpty()) return
            val bars = generateSequence(data.chartFrom) { it.plus(1, DateTimeUnit.DAY) }
                .takeWhile { it <= data.to }
                .map { DayBar(it, byDate[it].orEmpty()) }
                .toList()
            val max = bars.maxOf { it.total }.coerceAtLeast(1)
            val height = DOT_ROW + CHART_HEIGHT + LABEL_ROWS + 6f + LEGEND_HEIGHT

            section(
                string(R.string.analysis_daily),
                context.resources.getQuantityString(
                    R.plurals.report_chart_range,
                    bars.size,
                    bars.size,
                    DateFormats.dayMonthYear(data.chartFrom),
                    DateFormats.dayMonthYear(data.to),
                ),
                height,
            )
            val top = y + DOT_ROW
            val baseline = top + CHART_HEIGHT
            val plotLeft = MARGIN + AXIS_WIDTH
            val plotWidth = CONTENT_WIDTH - AXIS_WIDTH
            val slot = plotWidth / bars.size
            val barWidth = (slot * 0.7f).coerceAtMost(14f)
            val dotRadius = (slot * 0.35f).coerceAtMost(2.5f)
            val labelEvery = ceil(MIN_LABEL_SPACING / slot).toInt().coerceAtLeast(1)

            // Scale: maximum at the top, 0 at the bottom.
            canvas.drawLine(plotLeft, top, plotLeft + plotWidth, top, rulePaint)
            canvas.drawLine(plotLeft, baseline, plotLeft + plotWidth, baseline, rulePaint)
            val maxLabel = layout(max.toString(), axisPaint, AXIS_WIDTH - 4f, Layout.Alignment.ALIGN_OPPOSITE)
            val zeroLabel = layout("0", axisPaint, AXIS_WIDTH - 4f, Layout.Alignment.ALIGN_OPPOSITE)
            canvas.drawLayout(maxLabel, MARGIN, top - maxLabel.height / 2f)
            canvas.drawLayout(zeroLabel, MARGIN, baseline - zeroLabel.height / 2f)

            bars.forEachIndexed { index, day ->
                val center = plotLeft + slot * (index + 0.5f)
                var barBottom = baseline
                StoolCategory.entries.forEach { category ->
                    val count = day.counts.getValue(category)
                    if (count > 0) {
                        val segment = CHART_HEIGHT * count / max
                        fillPaint.color = category.color
                        canvas.drawRect(center - barWidth / 2, barBottom - segment, center + barWidth / 2, barBottom, fillPaint)
                        barBottom -= segment
                    }
                }
                if (day.blood) {
                    fillPaint.color = BLOOD
                    canvas.drawCircle(center, top - DOT_ROW / 2, dotRadius, fillPaint)
                }
                // Numbers counted from the last day, which is always labeled.
                if ((bars.lastIndex - index) % labelEvery == 0) {
                    canvas.drawText(day.date.day.toString(), center, baseline + 9f, dayLabelPaint)
                }
                if (index == 0 || day.date.day == 1) {
                    canvas.drawText(DateFormats.monthShort(day.date), center, baseline + 18f, monthLabelPaint)
                }
            }

            // Legend.
            val legendTop = baseline + LABEL_ROWS + 6f
            val swatch = 7f
            var x = MARGIN
            StoolCategory.entries.forEach { category ->
                fillPaint.color = category.color
                canvas.drawRect(x, legendTop, x + swatch, legendTop + swatch, fillPaint)
                val text = string(category.legendRes)
                canvas.drawText(text, x + swatch + 3f, legendTop + swatch - 0.5f, legendPaint)
                x += swatch + 3f + legendPaint.measureText(text) + 12f
            }
            fillPaint.color = BLOOD
            canvas.drawCircle(x + swatch / 2, legendTop + swatch / 2, swatch / 2, fillPaint)
            canvas.drawText(string(R.string.analysis_legend_blood), x + swatch + 3f, legendTop + swatch - 0.5f, legendPaint)
            y = legendTop + LEGEND_HEIGHT
        }

        fun treatments() {
            val blocks = data.treatments.map { treatment ->
                val medication = treatment.medication
                val name = listOf(medication.name, medication.dosage).filter { it.isNotBlank() }.joinToString(" · ")
                val lines = listOfNotNull(
                    layout(name, strongPaint, CONTENT_WIDTH),
                    layout(scheduleText(medication.schedule), bodyPaint, CONTENT_WIDTH),
                    adherenceText(treatment.adherence)?.let { layout(it, bodyPaint, CONTENT_WIDTH) },
                )
                val height = lines.sumOf { it.height } + 2f * (lines.size - 1) + 2 * ROW_PADDING
                TableRow(height) { top ->
                    var lineTop = top + ROW_PADDING
                    lines.forEach { line ->
                        canvas.drawLayout(line, MARGIN, lineTop)
                        lineTop += line.height + 2f
                    }
                }
            }
            section(string(R.string.treatments_title), null, blocks.firstOrNull()?.height ?: bodyPaint.textSize * 2)
            if (blocks.isEmpty()) {
                paragraph(string(R.string.treatments_empty), bodyPaint)
            } else {
                table(null, blocks)
            }
        }

        /** Text lines stacked in one table row. */
        private fun linesRow(lines: List<StaticLayout>): TableRow {
            val height = lines.sumOf { it.height } + 2f * (lines.size - 1) + 2 * ROW_PADDING
            return TableRow(height) { top ->
                var lineTop = top + ROW_PADDING
                lines.forEach { line ->
                    canvas.drawLayout(line, MARGIN, lineTop)
                    lineTop += line.height + 2f
                }
            }
        }

        /** Flares overlapping the period: dates, hospitalisation and treatment. */
        fun flares() {
            val rows = data.flares.map { flare ->
                val range = string(
                    R.string.report_care_flare_range,
                    DateFormats.dayMonthYear(flare.start),
                    flare.end?.let { DateFormats.dayMonthYear(it) } ?: string(R.string.report_care_flare_ongoing),
                )
                val title = if (flare.hospitalized) "$range · ${string(R.string.report_care_flare_hospitalized)}" else range
                linesRow(
                    listOfNotNull(
                        layout(title, strongPaint, CONTENT_WIDTH),
                        flare.treatment.takeIf { it.isNotBlank() }
                            ?.let { layout(string(R.string.report_care_flare_treatment, it.trim()), bodyPaint, CONTENT_WIDTH) },
                    ),
                )
            }
            if (rows.isEmpty()) return
            section(string(R.string.report_care_flares), null, rows.first().height)
            table(null, rows)
        }

        /** Latest score with its level, then the most recent entries of the period in a compact line. */
        fun activityScores() {
            val scores = data.activityScores
            val latest = scores.lastOrNull() ?: return
            val date = DateFormats.dayMonthYear(latest.date)
            val level = latest.level
            val latestText = if (level != null) {
                string(R.string.report_care_activity_latest_level, scoreName(latest.kind), latest.total, date, levelName(level))
            } else {
                string(R.string.report_care_activity_latest, scoreName(latest.kind), latest.total, date)
            }
            section(
                string(R.string.report_care_activity),
                string(R.string.report_care_activity_note),
                layout(latestText, strongPaint, CONTENT_WIDTH).height.toFloat(),
            )
            paragraph(latestText, strongPaint)
            if (scores.size > 1) {
                val entries = scores.takeLast(ACTIVITY_LIST_MAX).joinToString("; ") {
                    string(R.string.report_care_activity_entry, DateFormats.dayMonthYear(it.date), scoreName(it.kind), it.total)
                }
                paragraph(entries, bodyPaint)
            }
        }

        /** Steroid courses overlapping the period, with their total dose. */
        fun steroidCourses() {
            val rows = data.steroidCourses.map { course ->
                val total = course.totalMg
                val totalText = if (total % 1.0 == 0.0) total.toLong().toString() else formatDecimal(total)
                val text = string(
                    R.string.report_care_steroid_row,
                    course.drug.trim(),
                    DateFormats.dayMonthYear(course.start),
                    DateFormats.dayMonthYear(course.end),
                    totalText,
                )
                linesRow(listOf(layout(text, bodyPaint, CONTENT_WIDTH)))
            }
            if (rows.isEmpty()) return
            section(string(R.string.report_care_steroids), null, rows.first().height)
            table(null, rows)
        }

        fun vaccinations() {
            if (data.vaccinations.isEmpty()) return
            val columns = columns(150f, 120f)
            val header = textRow(
                listOf(
                    string(R.string.report_care_col_vaccine),
                    string(R.string.report_care_col_given),
                    string(R.string.report_care_col_next_due),
                ),
                columns,
                headerCellPaint,
            )
            val rows = data.vaccinations.map { vaccination ->
                textRow(
                    listOf(
                        vaccineName(vaccination.type),
                        DateFormats.dayMonthYear(vaccination.date),
                        vaccination.nextDue?.let { DateFormats.dayMonthYear(it) } ?: "–",
                    ),
                    columns,
                    bodyPaint,
                )
            }
            section(string(R.string.report_care_vaccinations), null, header.height + rows.first().height)
            table(header, rows)
        }

        /**
         * Latest panels side by side, most recent on the left, analyses grouped by category under
         * a subheading. Outside the laboratory values: bold, ↑/↓ arrow (readable in black and
         * white) and grayed background. Notes follow the table.
         */
        fun labResults() {
            val panels = data.labPanels
            if (panels.rows.isEmpty()) return
            val testColumn = Column(0f, LAB_TEST_WIDTH)
            // Right-aligned values: digits stay aligned despite the arrows.
            val dateColumns = panels.dates.indices.map { index ->
                Column(
                    LAB_TEST_WIDTH + COLUMN_GAP + index * (LAB_DATE_WIDTH + COLUMN_GAP),
                    LAB_DATE_WIDTH,
                    Layout.Alignment.ALIGN_OPPOSITE,
                )
            }
            val referenceX = LAB_TEST_WIDTH + COLUMN_GAP + panels.dates.size * (LAB_DATE_WIDTH + COLUMN_GAP)
            val referenceColumn = Column(referenceX, CONTENT_WIDTH - referenceX)
            val header = textRow(
                listOf(string(R.string.report_col_test)) +
                    panels.dates.map { DateFormats.dayMonthYear(it) } +
                    string(R.string.report_col_reference),
                listOf(testColumn) + dateColumns + referenceColumn,
                headerCellPaint,
            )
            val rows = panels.rows.groupBy { it.test.category }.toSortedMap().flatMap { (category, group) ->
                val testRows = group.map { labRow(it, testColumn, dateColumns, referenceColumn) }
                listOf(categoryRow(string(category.labelRes), testRows.first().height)) + testRows
            }
            section(string(R.string.report_labs), string(R.string.report_labs_legend), header.height + rows[0].height + rows[1].height)
            table(header, rows)
            labNotes()
        }

        /** Category subheading across the full width, kept with the first analysis of the group. */
        private fun categoryRow(title: String, keepWith: Float): TableRow {
            val label = layout(title.uppercase(locale), categoryPaint, CONTENT_WIDTH)
            return TableRow(label.height + 2 * LAB_ROW_PADDING + 3f, keepWith) { top ->
                canvas.drawLayout(label, MARGIN, top + LAB_ROW_PADDING + 3f)
            }
        }

        private fun labRow(row: LabPanelRow, testColumn: Column, dateColumns: List<Column>, referenceColumn: Column): TableRow {
            val label = layout("${string(row.test.labelRes)} (${row.test.unit})", bodyPaint, testColumn.width)
            val cells = row.cells.mapIndexed { index, cell ->
                val column = dateColumns[index]
                val text = cell?.let {
                    val marker = when (it.status) {
                        LabStatus.BELOW -> "↓ "
                        LabStatus.ABOVE -> "↑ "
                        null -> ""
                    }
                    marker + it.result.displayValue(withUnit = false)
                } ?: "–"
                val paint = if (cell?.status == null) bodyPaint else strongPaint
                layout(text, paint, column.width, column.align)
            }
            val reference = layout(row.reference?.display() ?: "–", bodyPaint, referenceColumn.width)
            val height = (cells + label + reference).maxOf { it.height } + 2 * LAB_ROW_PADDING
            return TableRow(height) { top ->
                fillPaint.color = TRACK
                row.cells.forEachIndexed { index, cell ->
                    if (cell?.status != null) {
                        val left = MARGIN + dateColumns[index].x - COLUMN_GAP / 2
                        val right = left + LAB_DATE_WIDTH + COLUMN_GAP
                        canvas.drawRoundRect(left, top + 1f, right, top + height - 1f, 3f, 3f, fillPaint)
                    }
                }
                canvas.drawLayout(label, MARGIN + testColumn.x, top + LAB_ROW_PADDING)
                cells.forEachIndexed { index, cell ->
                    canvas.drawLayout(cell, MARGIN + dateColumns[index].x, top + LAB_ROW_PADDING)
                }
                canvas.drawLayout(reference, MARGIN + referenceColumn.x, top + LAB_ROW_PADDING)
            }
        }

        /** "date – analysis: note", below the lab table; title kept with the first note. */
        private fun labNotes() {
            val notes = data.labPanels.notes.map { result ->
                string(
                    R.string.report_lab_note,
                    DateFormats.dayMonthYear(result.date),
                    string(result.test.labelRes),
                    result.note.trim(),
                )
            }
            if (notes.isEmpty()) return
            val title = string(R.string.report_lab_notes)
            y += 8f
            ensure(layout(title, headerCellPaint, CONTENT_WIDTH).height + 2f + layout(notes.first(), bodyPaint, CONTENT_WIDTH).height)
            paragraph(title, headerCellPaint, 2f)
            notes.forEach { paragraph(it, bodyPaint, 2f) }
        }

        /** Manifestations noted over the period: days count and most recent day. */
        fun extraSymptoms() {
            val rows = data.extraSymptoms.map { report ->
                val line = string(
                    R.string.report_followup_extra_line,
                    string(report.symptom.extraLabelRes()),
                    plural(R.plurals.report_followup_days, report.days),
                    DateFormats.dayMonthYear(report.last),
                )
                linesRow(listOf(layout(line, bodyPaint, CONTENT_WIDTH)))
            }
            if (rows.isEmpty()) return
            section(string(R.string.report_followup_extra_title), string(R.string.report_followup_extra_note), rows.first().height)
            table(null, rows)
        }

        /** Next appointments: kind, date and time, then place. */
        fun appointments() {
            val rows = data.appointments.map { appointment ->
                val title = "${string(appointment.kind.nameRes())} · " +
                    string(
                        R.string.report_checkup_at,
                        DateFormats.dayMonthYear(appointment.at.date),
                        DateFormats.time(appointment.at.hour, appointment.at.minute),
                    )
                linesRow(
                    listOfNotNull(
                        layout(title, strongPaint, CONTENT_WIDTH),
                        appointment.place.takeIf { it.isNotBlank() }?.let { layout(it.trim(), bodyPaint, CONTENT_WIDTH) },
                    ),
                )
            }
            if (rows.isEmpty()) return
            section(string(R.string.report_followup_appointments), null, rows.first().height)
            table(null, rows)
        }

        /** Open questions as a bullet list. */
        fun doctorQuestions() {
            val rows = data.doctorQuestions.map { question ->
                linesRow(listOf(layout("• ${question.text.trim()}", bodyPaint, CONTENT_WIDTH)))
            }
            if (rows.isEmpty()) return
            section(string(R.string.report_followup_questions), null, rows.first().height)
            table(null, rows)
        }

        fun checkups() {
            val columns = columns(150f, 120f)
            val header = textRow(
                listOf(
                    string(R.string.report_col_date),
                    string(R.string.report_col_test),
                    string(R.string.report_col_note),
                ),
                columns,
                headerCellPaint,
            )
            val rows = data.upcomingCheckups.map { upcoming ->
                textRow(
                    listOf(
                        string(
                            R.string.report_checkup_at,
                            DateFormats.dayMonthYear(upcoming.at.date),
                            DateFormats.time(upcoming.at.hour, upcoming.at.minute),
                        ),
                        string(upcoming.checkup.type.labelRes),
                        upcoming.checkup.note,
                    ),
                    columns,
                    bodyPaint,
                )
            }
            section(
                string(R.string.report_checkups),
                null,
                rows.firstOrNull()?.let { header.height + it.height } ?: bodyPaint.textSize * 2,
            )
            if (rows.isEmpty()) {
                paragraph(string(R.string.checkups_empty), bodyPaint)
            } else {
                table(header, rows)
            }
        }
    }

    // --- Care labels ---

    private fun scoreName(kind: ScoreKind): String = string(
        when (kind) {
            ScoreKind.HBI -> R.string.report_care_score_hbi
            ScoreKind.SCCAI -> R.string.report_care_score_sccai
        },
    )

    private fun levelName(level: ActivityLevel): String = string(
        when (level) {
            ActivityLevel.REMISSION -> R.string.report_care_level_remission
            ActivityLevel.MILD -> R.string.report_care_level_mild
            ActivityLevel.MODERATE -> R.string.report_care_level_moderate
            ActivityLevel.SEVERE -> R.string.report_care_level_severe
            ActivityLevel.ACTIVE -> R.string.report_care_level_active
        },
    )

    private fun diagnosisName(diagnosis: Diagnosis): String = string(
        when (diagnosis) {
            Diagnosis.CROHN -> R.string.report_care_diagnosis_crohn
            Diagnosis.ULCERATIVE_COLITIS -> R.string.report_care_diagnosis_uc
            Diagnosis.OTHER -> R.string.report_care_diagnosis_other
        },
    )

    private fun vaccineName(type: VaccineType): String = string(
        when (type) {
            VaccineType.INFLUENZA -> R.string.report_care_vaccine_influenza
            VaccineType.PNEUMOCOCCAL -> R.string.report_care_vaccine_pneumococcal
            VaccineType.COVID19 -> R.string.report_care_vaccine_covid19
            VaccineType.HEPATITIS_B -> R.string.report_care_vaccine_hepatitis_b
            VaccineType.HPV -> R.string.report_care_vaccine_hpv
            VaccineType.ZOSTER -> R.string.report_care_vaccine_zoster
            VaccineType.TETANUS -> R.string.report_care_vaccine_tetanus
            VaccineType.OTHER -> R.string.report_care_vaccine_other
        },
    )

    // --- Textes ---

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun plural(@PluralsRes id: Int, count: Int): String =
        context.resources.getQuantityString(id, count, count)

    /** "Every day at 08:00, since March 3, 2026" or "Once, on March 3, 2026 at 08:00". */
    private fun scheduleText(schedule: ReminderSchedule): String {
        val time = DateFormats.time(schedule.hour, schedule.minute)
        val start = DateFormats.dayMonthYear(schedule.startDate)
        val every = schedule.repeatEvery.coerceAtLeast(1)
        val repeat = when (schedule.repeatUnit) {
            RepeatUnit.ONCE -> return string(R.string.report_schedule_once, start, time)
            RepeatUnit.DAY -> plural(R.plurals.repeat_every_days, every)
            RepeatUnit.WEEK -> plural(R.plurals.repeat_every_weeks, every)
            RepeatUnit.MONTH -> plural(R.plurals.repeat_every_months, every)
        }
        return string(R.string.report_schedule, repeat, time, start)
    }

    /** Nothing without a scheduled intake over the period; unanswered intakes are counted separately. */
    private fun adherenceText(adherence: Adherence): String? {
        val answered = adherence.taken + adherence.missed
        return when {
            adherence.expected == 0 -> null
            answered == 0 -> string(R.string.report_adherence_none)
            else -> string(
                R.string.report_adherence,
                percentFormat.format(adherence.takenRate ?: 0.0),
                adherence.taken,
                adherence.expected,
                adherence.missed,
                adherence.expected - answered,
            )
        }
    }

    // --- Dessin ---

    private fun pageInfo(number: Int): PdfDocument.PageInfo =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()

    /** Fixed-width columns, the last taking the rest of the line. */
    private fun columns(vararg widths: Float): List<Column> {
        var x = 0f
        val fixed = widths.map { width -> Column(x, width).also { x += width + COLUMN_GAP } }
        return fixed + Column(x, CONTENT_WIDTH - x)
    }

    private fun layout(
        text: CharSequence,
        paint: TextPaint,
        width: Float,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    ): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1))
            .setAlignment(align)
            .setIncludePad(false)
            .build()

    private fun Canvas.drawLayout(layout: StaticLayout, x: Float, y: Float) {
        save()
        translate(x, y)
        layout.draw(this)
        restore()
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
}
