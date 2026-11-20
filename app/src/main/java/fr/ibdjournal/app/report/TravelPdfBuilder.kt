package fr.ibdjournal.app.report

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.LocaleList
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import fr.ibdjournal.app.R
import fr.ibdjournal.app.data.model.Diagnosis
import fr.ibdjournal.app.data.model.EmergencyInfo
import fr.ibdjournal.app.data.model.Profile
import fr.ibdjournal.app.data.model.ReminderSchedule
import fr.ibdjournal.app.data.model.RepeatUnit
import fr.ibdjournal.app.domain.TravelLine
import fr.ibdjournal.app.domain.tripDays
import java.io.OutputStream
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate

/** Languages the travel pack can be written in. */
enum class TravelLanguage(val tag: String) { FRENCH("fr"), ENGLISH("en"), SPANISH("es"), GERMAN("de") }

/** Everything the travel pack contains. [language]: the language of every text in the PDF. */
data class TravelPackData(
    val profile: Profile,
    val emergency: EmergencyInfo,
    val lines: List<TravelLine>,
    val departure: LocalDate,
    val returnDate: LocalDate,
    val marginDays: Int,
    val destination: String,
    val language: TravelLanguage,
)

/**
 * Travel pack in the language of the destination: the medication list with quantities, a letter for
 * the treating physician to date, sign and stamp, and the urgent-need card with the emergency
 * contacts. Every text comes from the app's string resources of that language.
 */
class TravelPdfBuilder(private val context: Context) {

    fun write(data: TravelPackData, out: OutputStream) {
        val language = Locale.forLanguageTag(data.language.tag)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(language))
        }
        val localized = context.createConfigurationContext(configuration)
        val document = PdfDocument()
        try {
            Writer(document, localized, language, data).run {
                medicationList()
                letter()
                urgentCard()
                finish()
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private class Writer(
        private val document: PdfDocument,
        private val context: Context,
        private val language: Locale,
        private val data: TravelPackData,
    ) {
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page
        private lateinit var canvas: Canvas
        private var y = MARGIN.toFloat()

        private val titlePaint = paint(20f, bold = true)
        private val headingPaint = paint(12f, bold = true)
        private val bodyPaint = paint(10.5f)
        private val strongPaint = paint(10.5f, bold = true)
        private val mutedPaint = paint(9f, color = MUTED)
        private val bigPaint = paint(30f, bold = true)
        private val bigBodyPaint = paint(17f)
        private val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RULE
            strokeWidth = 0.8f
        }
        private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(language)

        private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        private fun string(id: Int, vararg args: Any): String = context.getString(id, *args)
        private fun plural(id: Int, count: Int): String = context.resources.getQuantityString(id, count, count)
        private fun date(value: LocalDate): String = dateFormat.format(value.toJavaLocalDate())

        // --- Page handling ---

        private fun newPage() {
            if (pageNumber > 0) document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page.canvas
            y = MARGIN.toFloat()
        }

        fun finish() {
            if (pageNumber > 0) document.finishPage(page)
        }

        private fun layout(text: String, paint: TextPaint, width: Int = CONTENT_WIDTH): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .build()

        /** Draws [text] at the current line, starting a new page when it does not fit. */
        private fun paragraph(text: String, paint: TextPaint, after: Float = 6f, width: Int = CONTENT_WIDTH, x: Float = MARGIN.toFloat()) {
            val block = layout(text, paint, width)
            if (y + block.height > PAGE_HEIGHT - MARGIN) newPage()
            canvas.save()
            canvas.translate(x, y)
            block.draw(canvas)
            canvas.restore()
            y += block.height + after
        }

        private fun rule() {
            canvas.drawLine(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y, rulePaint)
            y += 8f
        }

        // --- Texts ---

        private fun schedule(schedule: ReminderSchedule): String {
            val every = schedule.repeatEvery.coerceAtLeast(1)
            val repeat = when (schedule.repeatUnit) {
                RepeatUnit.ONCE -> return string(R.string.travel_pdf_schedule_once)
                RepeatUnit.DAY -> plural(R.plurals.repeat_every_days, every)
                RepeatUnit.WEEK -> plural(R.plurals.repeat_every_weeks, every)
                RepeatUnit.MONTH -> plural(R.plurals.repeat_every_months, every)
            }
            return string(R.string.travel_pdf_schedule, repeat, "%02d:%02d".format(schedule.hour, schedule.minute))
        }

        private fun diagnosis(): String? = when (data.profile.diagnosis) {
            Diagnosis.CROHN -> string(R.string.diagnosis_crohn)
            Diagnosis.ULCERATIVE_COLITIS -> string(R.string.diagnosis_uc)
            Diagnosis.OTHER -> string(R.string.diagnosis_other)
            null -> null
        }

        private fun travelerName(): String =
            listOf(data.profile.firstName, data.profile.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { BLANK }

        private fun trip(): String = string(
            R.string.travel_pdf_trip,
            date(data.departure),
            date(data.returnDate),
            plural(R.plurals.travel_pdf_days, tripDays(data.departure, data.returnDate)),
        )

        // --- Sections ---

        /** Page 1: who travels, when, and what to carry. */
        fun medicationList() {
            newPage()
            paragraph(string(R.string.travel_pdf_title), titlePaint, 8f)
            paragraph(trip(), strongPaint, 2f)
            data.destination.trim().takeIf { it.isNotEmpty() }?.let {
                paragraph(string(R.string.travel_pdf_destination, it), bodyPaint, 2f)
            }
            y += 6f
            rule()
            paragraph(travelerName(), strongPaint, 2f)
            data.profile.birthDate?.let { paragraph(string(R.string.emergency_field_born) + ": " + date(it), bodyPaint, 2f) }
            diagnosis()?.let { paragraph(string(R.string.diagnosis_title) + ": " + it, bodyPaint, 2f) }
            y += 8f
            paragraph(string(R.string.travel_pdf_list_title), headingPaint, 6f)

            val nameWidth = 210
            val scheduleWidth = 165
            val quantityWidth = CONTENT_WIDTH - nameWidth - scheduleWidth
            row(
                listOf(
                    string(R.string.travel_pdf_col_medication),
                    string(R.string.travel_pdf_col_schedule),
                    string(R.string.travel_pdf_col_quantity),
                ),
                listOf(nameWidth, scheduleWidth, quantityWidth),
                strongPaint,
            )
            rule()
            if (data.lines.isEmpty()) {
                paragraph(string(R.string.travel_pdf_empty), bodyPaint)
            }
            data.lines.forEach { line ->
                val medication = line.medication
                val name = listOf(medication.name, medication.dosage.trim()).filter { it.isNotEmpty() }.joinToString("\n")
                row(
                    listOf(name, schedule(medication.schedule), plural(R.plurals.travel_pdf_intakes, line.total)),
                    listOf(nameWidth, scheduleWidth, quantityWidth),
                    bodyPaint,
                )
            }
            y += 8f
            paragraph(string(R.string.travel_pdf_margin_note, data.marginDays), mutedPaint, 3f)
            paragraph(string(R.string.travel_pdf_carry_note), mutedPaint, 3f)
        }

        private fun row(cells: List<String>, widths: List<Int>, paint: TextPaint) {
            val layouts = cells.mapIndexed { index, text -> layout(text, paint, widths[index] - 8) }
            val height = layouts.maxOf { it.height }
            if (y + height > PAGE_HEIGHT - MARGIN) newPage()
            var x = MARGIN.toFloat()
            layouts.forEachIndexed { index, block ->
                canvas.save()
                canvas.translate(x, y)
                block.draw(canvas)
                canvas.restore()
                x += widths[index]
            }
            y += height + 6f
        }

        /** Page 2: letter to be dated, signed and stamped by the treating physician. */
        fun letter() {
            newPage()
            paragraph(string(R.string.travel_cert_title), titlePaint, 14f)
            paragraph(string(R.string.travel_cert_greeting), bodyPaint, 10f)
            paragraph(
                string(
                    R.string.travel_cert_body,
                    travelerName(),
                    data.profile.birthDate?.let(::date) ?: BLANK,
                    diagnosis() ?: string(R.string.travel_cert_condition_default),
                    date(data.departure),
                    date(data.returnDate),
                ),
                bodyPaint,
                10f,
            )
            paragraph(string(R.string.travel_cert_list_ref, data.marginDays), bodyPaint, 10f)
            paragraph(string(R.string.travel_cert_customs), bodyPaint, 24f)
            paragraph(string(R.string.travel_cert_sign_place), strongPaint, 34f)
            paragraph(string(R.string.travel_cert_sign_doctor), strongPaint, 80f)
            rule()
            paragraph(string(R.string.travel_cert_disclaimer), mutedPaint)
        }

        /** Page 3: the urgent-need card, then the emergency contacts, in the language of the trip. */
        fun urgentCard() {
            newPage()
            paragraph(string(R.string.urgent_card_title), bigPaint, 18f)
            paragraph(string(R.string.urgent_card_text), bigBodyPaint, 26f)
            rule()
            val emergency = data.emergency
            val fields = listOf(
                R.string.emergency_field_allergies to emergency.allergies,
                R.string.emergency_field_doctor to listOf(emergency.doctorName, emergency.doctorPhone).filter { it.isNotBlank() }.joinToString(" · "),
                R.string.emergency_field_contact to listOf(emergency.contactName, emergency.contactPhone).filter { it.isNotBlank() }.joinToString(" · "),
                R.string.emergency_field_notes to emergency.notes,
            ).filter { it.second.isNotBlank() }
            fields.forEach { (label, value) ->
                paragraph(string(label), strongPaint, 1f)
                paragraph(value.trim(), bodyPaint, 8f)
            }
            if (fields.isEmpty()) paragraph(string(R.string.travel_pdf_card_empty), mutedPaint)
        }
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 44
        const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN
        const val BLANK = "………………………"
        val MUTED = Color.rgb(96, 96, 96)
        val RULE = Color.rgb(190, 190, 190)
    }
}
