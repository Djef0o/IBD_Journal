package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.BackupData
import fr.ibdjournal.app.data.model.ExtraSymptom

/**
 * CSV files of the journal, for a spreadsheet or the doctor: file name to content.
 * RFC 4180: comma-separated, UTF-8, CRLF line ends, quotes doubled; dates in ISO (2026-11-19),
 * decimals with a point, yes/no as 1/0. Headers are in English whatever the app's language.
 */
fun BackupData.toCsvFiles(): Map<String, String> = linkedMapOf(
    "stools.csv" to csv(
        listOf("date", "bristol_type", "blood", "mucus", "pain", "fatigue", "urgency", "nocturnal", "false_urge", "comment"),
        stools.sortedWith(compareBy({ it.date }, { it.createdAt })).map {
            listOf(
                it.date.toString(), it.bristolType.toString(), it.bloodLevel.toString(), it.mucusLevel.toString(),
                it.painLevel.toString(), it.fatigueLevel.toString(), it.urgency.flag(), it.nocturnal.flag(),
                it.falseUrge.flag(), text(it.comment),
            )
        },
    ),
    "extra_symptoms.csv" to csv(
        listOf("date", "joint_pain", "skin", "eyes", "mouth_ulcers", "fever"),
        extraSymptoms.orEmpty().sortedBy { it.date }.map { day ->
            listOf(day.date.toString()) + ExtraSymptom.entries.map { (it in day.symptoms).flag() }
        },
    ),
    "meals.csv" to csv(
        listOf("date", "meal", "description", "product"),
        meals.sortedWith(compareBy({ it.date }, { it.type })).map {
            listOf(it.date.toString(), it.type.name.lowercase(), text(it.description), text(it.product?.name.orEmpty()))
        },
    ),
    "weights.csv" to csv(
        listOf("date", "kg"),
        weights.orEmpty().sortedWith(compareBy({ it.date }, { it.id })).map { listOf(it.date.toString(), it.kg.toString()) },
    ),
    "lab_results.csv" to csv(
        listOf("date", "test", "value", "unit", "qualifier", "reference_low", "reference_high", "note"),
        labResults.orEmpty().sortedWith(compareBy({ it.date }, { it.test }, { it.id })).map {
            listOf(
                it.date.toString(), it.test.name.lowercase(), it.value.toString(), it.test.unit,
                it.qualifier.name.lowercase(), it.referenceLow?.toString().orEmpty(),
                it.referenceHigh?.toString().orEmpty(), text(it.note),
            )
        },
    ),
    "flares.csv" to csv(
        listOf("start", "end", "hospitalized", "treatment", "note"),
        flares.orEmpty().sortedBy { it.start }.map {
            listOf(it.start.toString(), it.end?.toString().orEmpty(), it.hospitalized.flag(), text(it.treatment), text(it.note))
        },
    ),
    "activity_scores.csv" to csv(
        listOf("date", "score", "total", "answers"),
        activityScores.orEmpty().sortedWith(compareBy({ it.date }, { it.id })).map {
            listOf(it.date.toString(), it.kind.name.lowercase(), it.total.toString(), it.answers.joinToString(" "))
        },
    ),
    "wellbeing.csv" to csv(
        listOf("date", "sleep", "stress", "mood", "period"),
        wellbeing.orEmpty().sortedBy { it.date }.map {
            listOf(it.date.toString(), it.sleep?.toString().orEmpty(), it.stress?.toString().orEmpty(), it.mood?.toString().orEmpty(), it.period.flag())
        },
    ),
)

private fun Boolean.flag() = if (this) "1" else "0"

/**
 * Free text from the user. A cell starting with "=", "+", "-", "@" or a control character would be
 * run as a formula by spreadsheets: it is prefixed with an apostrophe.
 */
private fun text(value: String): String =
    if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value

private fun csv(header: List<String>, rows: List<List<String>>): String =
    (listOf(header) + rows).joinToString("") { row -> row.joinToString(",") { cell(it) } + "\r\n" }

private fun cell(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
