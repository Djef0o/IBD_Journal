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
import fr.ibdjournal.app.domain.Adherence
import fr.ibdjournal.app.domain.LabStatus
import fr.ibdjournal.app.domain.Symptom
import fr.ibdjournal.app.ui.DateFormats
import fr.ibdjournal.app.ui.display
import fr.ibdjournal.app.ui.displayValue
import fr.ibdjournal.app.ui.formatDecimal
import fr.ibdjournal.app.ui.labelRes
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

/* A4 en points PostScript (1/72 pouce), l'unité de PdfDocument. */
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f
private const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

/** Filet du pied de page ; le contenu s'arrête un peu au-dessus. */
private const val FOOTER_TOP = PAGE_HEIGHT - 48f
private const val CONTENT_BOTTOM = FOOTER_TOP - 12f

private const val SECTION_GAP = 20f
private const val ROW_PADDING = 4f
private const val COLUMN_GAP = 10f
private const val TILE_GAP = 8f
private const val TILE_PADDING = 8f

private const val CHART_HEIGHT = 120f
private const val AXIS_WIDTH = 18f
/** Bande au-dessus des barres réservée au point « sang », comme dans l'app. */
private const val DOT_ROW = 8f
/** Numéros de jour puis repères de mois sous l'axe. */
private const val LABEL_ROWS = 22f
private const val LEGEND_HEIGHT = 12f
/** Écart minimal entre deux numéros de jour sous le graphique. */
private const val MIN_LABEL_SPACING = 14f

/** Tableau des bilans : analyse et unité, puis une colonne par date, puis les valeurs du laboratoire. */
private const val LAB_TEST_WIDTH = 150f
private const val LAB_DATE_WIDTH = 62f
/** Lignes resserrées : ~45 analyses doivent tenir sur une à deux pages. */
private const val LAB_ROW_PADDING = 2.5f

private val INK = 0xFF212121.toInt()
private val MUTED = 0xFF616161.toInt()
private val RULE = 0xFFD6D6D6.toInt()
private val TRACK = 0xFFEEEEEE.toInt()
private val BAR = 0xFF757575.toInt()
private val BLOOD = bristolColor(0).toArgb()

/** Catégories empilées de bas en haut, comme le graphique « Selles par jour » de l'app. */
private enum class StoolCategory(val types: IntRange, @StringRes val legendRes: Int) {
    HARD(1..2, R.string.analysis_legend_hard),
    NORMAL(3..4, R.string.analysis_legend_normal),
    LOOSE(5..7, R.string.analysis_legend_loose);

    val color: Int get() = bristolColor(types.first).toArgb()
}

/** Le type 0 (« pas de selle ») ne compte pas dans les barres. */
private class DayBar(val date: LocalDate, stools: List<Stool>) {
    val counts: Map<StoolCategory, Int> = StoolCategory.entries.associateWith { category ->
        stools.count { it.bristolType in category.types }
    }
    val total: Int = counts.values.sum()
    val blood: Boolean = stools.any { it.bloodLevel > 0 }
}

/** Colonne de tableau : décalage depuis la marge gauche et largeur. */
private class Column(
    val x: Float,
    val width: Float,
    val align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
)

/**
 * Ligne de tableau mesurée d'avance : [draw] la dessine à l'ordonnée donnée. [keepWith] : hauteur
 * gardée sur la même page qu'elle (un intertitre ne reste pas seul en bas de page).
 */
private class TableRow(val height: Float, val keepWith: Float = 0f, val draw: (top: Float) -> Unit)

/**
 * Rapport de suivi A4 à montrer au médecin : simple mise en page Canvas de [ReportData], déjà
 * calculées. [context] ne sert qu'aux textes, dans la langue voulue. Les sections qui ne tiennent
 * plus sur la page passent à la suivante ; les tableaux répètent leur en-tête.
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
                treatments()
                labResults()
                checkups()
                finish()
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    /** Curseur d'écriture : page courante et ordonnée [y] du prochain élément. */
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

        /** Change de page si [height] ne tient plus ; vrai si une nouvelle page a été ouverte. */
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

        /** Titre de section, gardé sur la même page que les [keepWith] premiers points du contenu. */
        private fun section(title: String, subtitle: String?, keepWith: Float) {
            val titleLayout = layout(title, sectionPaint, CONTENT_WIDTH)
            val subtitleLayout = subtitle?.let { layout(it, notePaint, CONTENT_WIDTH) }
            val height = titleLayout.height + (subtitleLayout?.let { it.height + 2f } ?: 0f) + 8f
            // En haut d'une nouvelle page, pas d'espace avant le titre.
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
            // Âge et sexe sur une ligne.
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
            // Jours notés : au moins une saisie de selle, y compris « pas de selle » (même base que les selles liquides).
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
                    // Part des jours de la période touchés par le symptôme.
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

            // Échelle : maximum en haut, 0 en bas.
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
                // Numéros comptés depuis le dernier jour, toujours étiqueté.
                if ((bars.lastIndex - index) % labelEvery == 0) {
                    canvas.drawText(day.date.day.toString(), center, baseline + 9f, dayLabelPaint)
                }
                if (index == 0 || day.date.day == 1) {
                    canvas.drawText(DateFormats.monthShort(day.date), center, baseline + 18f, monthLabelPaint)
                }
            }

            // Légende.
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

        /**
         * Derniers bilans côte à côte, le plus récent à gauche, analyses groupées par catégorie sous
         * un intertitre. Hors des valeurs du laboratoire : gras, flèche ↑/↓ (lisible en noir et
         * blanc) et fond grisé. Les notes suivent le tableau.
         */
        fun labResults() {
            val panels = data.labPanels
            if (panels.rows.isEmpty()) return
            val testColumn = Column(0f, LAB_TEST_WIDTH)
            // Valeurs alignées à droite : les chiffres restent alignés malgré les flèches.
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

        /** Intertitre de catégorie sur toute la largeur, gardé avec la première analyse du groupe. */
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

        /** « date – analyse : note », sous le tableau des bilans ; titre gardé avec la première note. */
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

    // --- Textes ---

    private fun string(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun plural(@PluralsRes id: Int, count: Int): String =
        context.resources.getQuantityString(id, count, count)

    /** « Tous les jours à 08:00, depuis le 3 mars 2026 » ou « Une fois, le 3 mars 2026 à 08:00 ». */
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

    /** Rien sans prise prévue sur la période ; les prises sans réponse sont comptées à part. */
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

    /** Colonnes de largeurs fixes, la dernière prenant le reste de la ligne. */
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
