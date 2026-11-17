package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.LabQualifier
import fr.ibdjournal.app.data.model.LabTest
import kotlin.math.abs
import kotlin.math.round
import kotlinx.datetime.LocalDate

/**
 * Line of text read from a PDF report, with its position on the page.
 * Reports are tables: label, result, unit, reference values and
 * previous results are separate lines aligned horizontally.
 */
data class TextLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val page: Int = 0,
)

/**
 * Detected value, converted to the app's unit ([LabTest.unit]). [qualifier]: result
 * given as a threshold ("<1,0"). [unitFound]: unit read on the report, null if absent
 * (the app's unit is then assumed). [referenceLow]/[referenceHigh]: laboratory reference
 * values, converted like the value; null if absent.
 */
data class DetectedLabValue(
    val test: LabTest,
    val value: Double,
    val qualifier: LabQualifier,
    val unitFound: String?,
    val sourceText: String,
    val referenceLow: Double? = null,
    val referenceHigh: Double? = null,
)

data class LabReportScan(val sampleDate: LocalDate?, val values: List<DetectedLabValue>)

/**
 * Finds the analyses of the [LabTest] catalog on a laboratory report.
 *
 * Rules, established on real reports:
 * - an analysis is recognized only if its label **starts** a table line, which
 *   rules out comments ("… si la ferritine est normale …") and false friends
 *   ("Préalbumine", "Hémoglobine glyquée", "Créatinine urinaire");
 * - the result is the first number to the right of the label whose unit suits the analysis
 *   (the white-cell differential gives the % before the absolute value); reference values
 *   and previous results are printed after;
 * - the unit read is converted to the app's; an unknown unit discards the value
 *   rather than recording a wrong figure;
 * - the date used is the sampling date, not the issue date.
 *
 * The result is a suggestion: the user checks it before any save.
 */
fun parseLabReport(lines: List<TextLine>): LabReportScan {
    val rows = lines.groupBy { it.page }.entries.sortedBy { it.key }.flatMap { pageRows(it.value) }
    val found = linkedMapOf<LabTest, DetectedLabValue>()
    for (row in rows) {
        val label = normalize(row.first().text)
        val (test, labelLength) = LABELS.firstNotNullOfOrNull { matcher ->
            matcher.match(label)?.let { matcher.test to it }
        } ?: continue
        if (test in found) continue // first occurrence: later pages sometimes repeat the previous results
        detectValue(test, row, labelLength)?.let { found[test] = it }
    }
    return LabReportScan(sampleDate = sampleDate(rows), values = found.values.toList())
}

// --- Rows ---

/** Groups horizontally aligned lines, top to bottom then left to right. */
private fun pageRows(lines: List<TextLine>): List<List<TextLine>> {
    val rows = mutableListOf<MutableList<TextLine>>()
    for (line in lines.sortedBy { (it.top + it.bottom) / 2 }) {
        val center = (line.top + line.bottom) / 2
        val row = rows.lastOrNull()?.takeIf { current ->
            val reference = current.first()
            val referenceCenter = (reference.top + reference.bottom) / 2
            abs(center - referenceCenter) <= (reference.bottom - reference.top) * 0.6f
        }
        if (row != null) row += line else rows += mutableListOf(line)
    }
    return rows.map { row -> row.sortedBy { it.left } }
}

// --- Labels ---

private class LabelMatcher(val test: LabTest, val prefixes: List<String>, val excluded: List<String> = emptyList()) {
    /**
     * Length of the label recognized at the start of [normalizedLabel], or null. Spaces are
     * ignored: OCR often glues words together ("Protéine Créactive" on a real report).
     */
    fun match(normalizedLabel: String): Int? {
        if (excluded.any { it in normalizedLabel }) return null
        for (prefix in prefixes) {
            val end = endOfPrefix(normalizedLabel, prefix.replace(" ", "")) ?: continue
            if (end == normalizedLabel.length || !normalizedLabel[end].isLetterOrDigit()) return end
        }
        return null
    }

    /** Position following [compactPrefix] in [text], skipping spaces, or null. */
    private fun endOfPrefix(text: String, compactPrefix: String): Int? {
        var matched = 0
        var index = 0
        while (matched < compactPrefix.length) {
            if (index >= text.length) return null
            val char = text[index++]
            if (char == ' ') continue
            if (char != compactPrefix[matched++]) return null
        }
        return index
    }
}

/** Urine assays with the same name as blood assays ("Créatinine urinaire"). */
private val URINE = listOf("urin")

/**
 * Recognized labels, per analysis. Order matters: the first match wins
 * ("cholestérol HDL" before plain "cholestérol").
 */
private val LABELS = listOf(
    LabelMatcher(LabTest.CRP, listOf("proteine c reactive", "proteine c-reactive", "crp", "c reactive protein", "c-reactive protein")),
    LabelMatcher(LabTest.CALPROTECTIN, listOf("calprotectine", "calprotectin")),

    LabelMatcher(LabTest.RED_BLOOD_CELLS, listOf("hematies", "globules rouges", "erythrocytes"), excluded = URINE),
    LabelMatcher(LabTest.HEMOGLOBIN, listOf("hemoglobine", "hb"), excluded = listOf("glyq", "a1c", "corpusc")),
    LabelMatcher(LabTest.HEMATOCRIT, listOf("hematocrite")),
    LabelMatcher(LabTest.MCV, listOf("v.g.m", "vgm", "volume globulaire moyen")),
    LabelMatcher(LabTest.MCH, listOf("t.c.m.h", "tcmh", "teneur corpusculaire moyenne")),
    LabelMatcher(LabTest.MCHC, listOf("c.c.m.h", "ccmh", "concentration corpusculaire moyenne")),
    LabelMatcher(LabTest.WHITE_BLOOD_CELLS, listOf("leucocytes", "globules blancs"), excluded = URINE),
    LabelMatcher(LabTest.NEUTROPHILS, listOf("poly. neutrophiles", "polynucleaires neutrophiles", "neutrophiles")),
    LabelMatcher(LabTest.LYMPHOCYTES, listOf("lymphocytes")),
    LabelMatcher(LabTest.MONOCYTES, listOf("monocytes")),
    LabelMatcher(LabTest.EOSINOPHILS, listOf("poly. eosinophiles", "polynucleaires eosinophiles", "eosinophiles")),
    LabelMatcher(LabTest.BASOPHILS, listOf("poly. basophiles", "polynucleaires basophiles", "basophiles")),
    LabelMatcher(LabTest.PLATELETS, listOf("plaquettes", "numeration plaquettaire")),

    LabelMatcher(LabTest.FERRITIN, listOf("ferritine", "ferritinemie")),
    LabelMatcher(LabTest.IRON, listOf("fer serique", "sideremie", "fer")),
    LabelMatcher(LabTest.TRANSFERRIN, listOf("transferrine"), excluded = listOf("saturation", "coefficient")),
    LabelMatcher(LabTest.IRON_BINDING_CAPACITY, listOf("capacite totale de fixation", "ctf")),
    LabelMatcher(LabTest.TRANSFERRIN_SATURATION, listOf("coefficient de saturation", "saturation de la transferrine", "cst")),

    LabelMatcher(LabTest.CREATININE, listOf("creatinine", "creatininemie"), excluded = URINE + "clairance"),
    LabelMatcher(LabTest.EGFR, listOf("debit de filtration", "dfg")),
    LabelMatcher(LabTest.SODIUM, listOf("sodium", "natremie"), excluded = URINE),
    LabelMatcher(LabTest.POTASSIUM, listOf("potassium", "kaliemie"), excluded = URINE),
    LabelMatcher(LabTest.CHLORIDE, listOf("chlore", "chlorure", "chloremie"), excluded = URINE),
    LabelMatcher(LabTest.CALCIUM, listOf("calcium", "calcemie"), excluded = URINE + listOf("ionis", "corrig")),
    LabelMatcher(LabTest.URINE_PROTEIN, listOf("proteines totales urinaires", "proteinurie")),

    LabelMatcher(LabTest.AST, listOf("transaminases tgo", "transaminases asat", "asat", "sgot", "tgo", "aspartate aminotransferase")),
    LabelMatcher(LabTest.ALT, listOf("transaminases tgp", "transaminases alat", "alat", "sgpt", "tgp", "alanine aminotransferase")),
    LabelMatcher(LabTest.GGT, listOf("gamma gt", "gamma-gt", "gamma glutamyltransferase", "gamma-glutamyltransferase", "ggt", "g.g.t")),
    LabelMatcher(LabTest.ALKALINE_PHOSPHATASE, listOf("phosphatases alcalines", "phosphatase alcaline", "pal")),
    LabelMatcher(LabTest.BILIRUBIN_TOTAL, listOf("bilirubine totale")),
    LabelMatcher(LabTest.BILIRUBIN_DIRECT, listOf("bilirubine directe", "bilirubine conjuguee")),
    LabelMatcher(LabTest.BILIRUBIN_INDIRECT, listOf("bilirubine indirecte", "bilirubine libre", "bilirubine non conjuguee")),

    LabelMatcher(LabTest.GLUCOSE, listOf("glycemie", "glucose", "glucosemie"), excluded = URINE),
    LabelMatcher(LabTest.CHOLESTEROL_HDL, listOf("cholesterol hdl", "cholesterol-hdl", "hdl cholesterol", "hdl-cholesterol", "hdl")),
    LabelMatcher(LabTest.CHOLESTEROL_LDL, listOf("cholesterol ldl", "cholesterol-ldl", "ldl cholesterol", "ldl-cholesterol", "ldl")),
    LabelMatcher(LabTest.CHOLESTEROL_TOTAL, listOf("cholesterol total", "cholesterol")),
    LabelMatcher(LabTest.TRIGLYCERIDES, listOf("triglycerides")),

    LabelMatcher(LabTest.VITAMIN_D, listOf("vitamine d", "25 oh", "25-oh", "25(oh)", "25 hydroxy", "25-hydroxy")),
    LabelMatcher(LabTest.ALBUMIN, listOf("albumine", "albuminemie"), excluded = listOf("urin", "ratio")),

    LabelMatcher(LabTest.PROTHROMBIN_TIME, listOf("taux de prothrombine")),
    LabelMatcher(LabTest.PSA, listOf("psa total", "psa", "antigene prostatique")),
)

/** Lowercase without accents, simplified spaces: labels vary from one laboratory to another. */
private fun normalize(text: String): String = buildString {
    for (char in text.trim().lowercase()) append(ACCENTS[char] ?: char)
}.replace(Regex("\\s+"), " ")

private val ACCENTS = mapOf(
    'é' to 'e', 'è' to 'e', 'ê' to 'e', 'ë' to 'e',
    'à' to 'a', 'â' to 'a', 'ä' to 'a',
    'î' to 'i', 'ï' to 'i', 'ô' to 'o', 'ö' to 'o',
    'ù' to 'u', 'û' to 'u', 'ü' to 'u', 'ç' to 'c',
)

// --- Values and units ---

/**
 * Number possibly preceded by a threshold ("<1,0", "> 300"), comma or dot decimal.
 * It must not stick to a letter: "D2+D3" or "25-OH" are not results, nor
 * be a piece of a number ("1" of "1,73m2").
 */
private val NUMBER = Regex("""(?<![A-Za-zÀ-ÿ0-9.,])([<>≤≥]=?)?\s*(\d+(?:[.,]\d+)?)(?![A-Za-zÀ-ÿ0-9]|[.,]\d)""")

private fun detectValue(test: LabTest, row: List<TextLine>, labelLength: Int): DetectedLabValue? {
    // Text to the right of the label: the following columns, or the rest of the line if OCR
    // merged label and result ("Hémoglobine 16,1 g/dL").
    val afterLabel = if (row.size > 1) {
        row.drop(1).joinToString("  ") { it.text }
    } else {
        row.first().text.trim().drop(labelLength)
    }

    // The number chosen is the first followed by a unit valid for the analysis: the
    // white-cell differential prints the percentage before the absolute value ("46,6 % soit 2,84 Giga/L")
    // and OCR sometimes loses the "%".
    val candidates = NUMBER.findAll(afterLabel).mapNotNull { match ->
        val number = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
        val rest = afterLabel.substring(match.range.last + 1)
        val unitMatch = LEADING_UNIT.find(rest)
        val unitFound = unitMatch?.groupValues?.get(1)
        val factor = if (unitFound == null) null else conversionFactor(test, unitFound) ?: return@mapNotNull null
        val afterUnit = if (unitMatch == null) rest else rest.substring(unitMatch.range.last + 1)
        Candidate(match, number, unitFound, factor, afterUnit)
    }.toList()
    val chosen = candidates.firstOrNull { it.factor != null }
        ?: unitlessResult(test, afterLabel, candidates)
        ?: return null
    val factor = chosen.factor ?: 1.0
    val value = round(chosen.number * factor * 1000) / 1000
    val plausible = plausible(test)
    if (value !in plausible) return null
    val reference = referenceRange(chosen.afterUnit, factor)?.takeIf { (low, high) ->
        (low == null || low in plausible) && (high == null || high in plausible)
    }
    return DetectedLabValue(
        test = test,
        value = value,
        qualifier = when (chosen.match.groupValues[1].take(1)) {
            "<", "≤" -> LabQualifier.BELOW
            ">", "≥" -> LabQualifier.ABOVE
            else -> LabQualifier.EXACT
        },
        unitFound = chosen.unit,
        sourceText = row.joinToString("  ") { it.text },
        referenceLow = reference?.first,
        referenceHigh = reference?.second,
    )
}

/**
 * Result whose unit OCR lost: only a number from the results column, hence
 * printed before the unit and before the references ("70-180", "> 60") when they are read,
 * and not a previous result ("105 Le 17.08.2024"). Otherwise nothing is suggested: a reference
 * or previous result mistaken for the result would be a wrong value.
 */
private fun unitlessResult(test: LabTest, text: String, candidates: List<Candidate>): Candidate? {
    val unitStart = ANY_UNIT.findAll(text).firstOrNull { conversionFactor(test, it.value) != null }?.range?.first
    val referenceStart = listOfNotNull(INTERVAL.find(text), THRESHOLD.find(text)).minOfOrNull { it.range.first }
    val columnEnd = listOfNotNull(unitStart, referenceStart).minOrNull() ?: text.length
    return candidates.firstOrNull { candidate ->
        // Position of the first digit: the match starts at the spaces preceding it.
        val digitStart = candidate.match.range.first + candidate.match.value.indexOf(candidate.match.groupValues[2])
        candidate.factor == null &&
            digitStart < columnEnd &&
            !PREVIOUS_RESULT.containsMatchIn(candidate.afterUnit)
    }
}

/** Previous result: value followed by the date of the previous panel. */
private val PREVIOUS_RESULT = Regex("""^\s*le\s+\d""", RegexOption.IGNORE_CASE)

/** Number read on the row; [factor] null = no unit printed right after. */
private class Candidate(
    val match: MatchResult,
    val number: Double,
    val unit: String?,
    val factor: Double?,
    val afterUnit: String,
)

/**
 * First reference printed after the result: interval "13,4 - 16,7" or threshold "< 5,0".
 * Previous results come next, without dash or sign: they are not mistaken for it.
 */
private fun referenceRange(text: String, factor: Double): Pair<Double?, Double?>? {
    val interval = INTERVAL.find(text)
    val threshold = THRESHOLD.find(text)
    fun number(text: String) =
        text.replace(" ", "").replace(',', '.').toDoubleOrNull()?.let { round(it * factor * 1000) / 1000 }
    return when {
        interval != null && (threshold == null || interval.range.first <= threshold.range.first) -> {
            val low = number(interval.groupValues[1]) ?: return null
            val high = number(interval.groupValues[2]) ?: return null
            if (low < high) low to high else null
        }
        threshold != null -> {
            val bound = number(threshold.groupValues[2]) ?: return null
            if (threshold.groupValues[1].first() in "<≤") null to bound else bound to null
        }
        else -> null
    }
}

/** Number of a reference; OCR sometimes inserts a space after the comma ("0, 10"). */
private const val DECIMAL = """\d+(?:[.,] ?\d+)?"""
private val INTERVAL = Regex("""(?<![A-Za-zÀ-ÿ0-9.,])($DECIMAL)\s*(?:-|–|à|a)\s*($DECIMAL)(?![0-9])""")
private val THRESHOLD = Regex("""([<>≤≥]=?)\s*($DECIMAL)(?![0-9])""")

/** Recognized units, longest first ("giga/l" before "g/l"). */
private const val UNITS =
    """(?:ml/mi?n/1[.,]73\s?m[2²]|giga/l|tera/l|10\^?9/l|10\^?12/l|mmol/l|[µu]mol/l|nmol/l|pmol/l|""" +
        """meq/l|mg/dl|mg/l|[µu]g/dl|[µu]g/l|ng/ml|[µu]g/g|mg/kg|g/dl|g/l|ui/l|u/l|fl|[µu]m3|pg|t/l|l/l|""" +
        """/mm3|/[µu]l|%)(?![a-z0-9µ])"""

/** Unit printed right after the result. */
private val LEADING_UNIT = Regex("""^\s*($UNITS)""", RegexOption.IGNORE_CASE)

/** Unit anywhere on the row, excluding a longer word ("pg" of "pgm"). */
private val ANY_UNIT = Regex("""(?<![a-z])$UNITS""", RegexOption.IGNORE_CASE)

/** Factor to the app's unit; null if the unit does not match the analysis. */
private fun conversionFactor(test: LabTest, unit: String): Double? {
    val u = unit.lowercase().replace(" ", "").replace("^", "").replace('²', '2').replace('.', ',')
        .replace("ug", "µg").replace("umol", "µmol").replace("um3", "µm3").replace("/ul", "/µl")
    val cells = mapOf("giga/l" to 1.0, "g/l" to 1.0, "109/l" to 1.0, "/mm3" to 0.001, "/µl" to 0.001)
    val factors: Map<String, Double> = when (test) {
        LabTest.CRP -> mapOf("mg/l" to 1.0, "mg/dl" to 10.0)
        LabTest.CALPROTECTIN -> mapOf("µg/g" to 1.0, "mg/kg" to 1.0)
        LabTest.RED_BLOOD_CELLS -> mapOf("tera/l" to 1.0, "t/l" to 1.0, "1012/l" to 1.0)
        LabTest.HEMOGLOBIN -> mapOf("g/dl" to 1.0, "g/l" to 0.1, "mmol/l" to 1.611)
        LabTest.HEMATOCRIT -> mapOf("%" to 1.0, "l/l" to 100.0)
        LabTest.MCV -> mapOf("fl" to 1.0, "µm3" to 1.0)
        LabTest.MCH -> mapOf("pg" to 1.0)
        LabTest.MCHC -> mapOf("g/dl" to 1.0, "g/l" to 0.1, "%" to 1.0)
        LabTest.WHITE_BLOOD_CELLS, LabTest.NEUTROPHILS, LabTest.LYMPHOCYTES, LabTest.MONOCYTES,
        LabTest.EOSINOPHILS, LabTest.BASOPHILS, LabTest.PLATELETS -> cells
        LabTest.FERRITIN -> mapOf("µg/l" to 1.0, "ng/ml" to 1.0)
        LabTest.IRON, LabTest.IRON_BINDING_CAPACITY -> mapOf("µg/dl" to 1.0, "µmol/l" to 5.585)
        LabTest.TRANSFERRIN -> mapOf("g/l" to 1.0, "mg/dl" to 0.01)
        LabTest.TRANSFERRIN_SATURATION, LabTest.PROTHROMBIN_TIME -> mapOf("%" to 1.0)
        LabTest.CREATININE -> mapOf("µmol/l" to 1.0, "mg/l" to 8.84, "mg/dl" to 88.4)
        LabTest.EGFR -> mapOf("ml/mn/1,73m2" to 1.0, "ml/min/1,73m2" to 1.0)
        LabTest.SODIUM, LabTest.POTASSIUM, LabTest.CHLORIDE -> mapOf("mmol/l" to 1.0, "meq/l" to 1.0)
        LabTest.CALCIUM -> mapOf("mmol/l" to 1.0, "mg/l" to 0.02495, "mg/dl" to 0.2495)
        LabTest.URINE_PROTEIN -> mapOf("g/l" to 1.0, "mg/l" to 0.001)
        LabTest.AST, LabTest.ALT, LabTest.GGT, LabTest.ALKALINE_PHOSPHATASE -> mapOf("u/l" to 1.0, "ui/l" to 1.0)
        LabTest.BILIRUBIN_TOTAL, LabTest.BILIRUBIN_DIRECT, LabTest.BILIRUBIN_INDIRECT ->
            mapOf("µmol/l" to 1.0, "mg/l" to 1.71, "mg/dl" to 17.1)
        LabTest.GLUCOSE -> mapOf("g/l" to 1.0, "mmol/l" to 0.18016, "mg/dl" to 0.01)
        LabTest.CHOLESTEROL_TOTAL, LabTest.CHOLESTEROL_HDL, LabTest.CHOLESTEROL_LDL ->
            mapOf("g/l" to 1.0, "mmol/l" to 0.3867, "mg/dl" to 0.01)
        LabTest.TRIGLYCERIDES -> mapOf("g/l" to 1.0, "mmol/l" to 0.8857, "mg/dl" to 0.01)
        LabTest.VITAMIN_D -> mapOf("ng/ml" to 1.0, "µg/l" to 1.0, "nmol/l" to 0.4006)
        LabTest.ALBUMIN -> mapOf("g/l" to 1.0, "g/dl" to 10.0)
        LabTest.PSA -> mapOf("µg/l" to 1.0, "ng/ml" to 1.0)
    }
    return factors[u]
}

/** Physiologically possible bounds: beyond them, the reading is wrong (neighboring column, OCR). */
private fun plausible(test: LabTest): ClosedFloatingPointRange<Double> = when (test) {
    LabTest.CRP -> 0.0..1000.0
    LabTest.CALPROTECTIN -> 0.0..20000.0
    LabTest.RED_BLOOD_CELLS -> 0.5..10.0
    LabTest.HEMOGLOBIN, LabTest.MCHC -> 2.0..45.0
    LabTest.HEMATOCRIT -> 5.0..80.0
    LabTest.MCV -> 40.0..150.0
    LabTest.MCH -> 10.0..50.0
    LabTest.WHITE_BLOOD_CELLS -> 0.1..500.0
    LabTest.NEUTROPHILS, LabTest.LYMPHOCYTES, LabTest.MONOCYTES,
    LabTest.EOSINOPHILS, LabTest.BASOPHILS -> 0.0..300.0
    LabTest.PLATELETS -> 1.0..3000.0
    LabTest.FERRITIN -> 0.0..20000.0
    LabTest.IRON, LabTest.IRON_BINDING_CAPACITY -> 1.0..1000.0
    LabTest.TRANSFERRIN -> 0.1..10.0
    LabTest.TRANSFERRIN_SATURATION, LabTest.PROTHROMBIN_TIME -> 0.0..150.0
    LabTest.CREATININE -> 5.0..3000.0
    LabTest.EGFR -> 1.0..200.0
    LabTest.SODIUM, LabTest.CHLORIDE -> 50.0..200.0
    LabTest.POTASSIUM -> 1.0..10.0
    LabTest.CALCIUM -> 0.5..5.0
    LabTest.URINE_PROTEIN -> 0.0..50.0
    LabTest.AST, LabTest.ALT, LabTest.GGT, LabTest.ALKALINE_PHOSPHATASE -> 0.0..20000.0
    LabTest.BILIRUBIN_TOTAL, LabTest.BILIRUBIN_DIRECT, LabTest.BILIRUBIN_INDIRECT -> 0.0..1000.0
    LabTest.GLUCOSE -> 0.1..10.0
    LabTest.CHOLESTEROL_TOTAL, LabTest.CHOLESTEROL_HDL, LabTest.CHOLESTEROL_LDL -> 0.05..20.0
    LabTest.TRIGLYCERIDES -> 0.05..50.0
    LabTest.VITAMIN_D -> 0.0..300.0
    LabTest.ALBUMIN -> 5.0..80.0
    LabTest.PSA -> 0.0..10000.0
}

// --- Sampling date ---

/** "19.05.2023", "05/12/22"; OCR sometimes reads a dot as a comma ("19,05.2023"). */
private const val DATE = """(\d{1,2})\s?[./,]\s?(\d{1,2})\s?[./,]\s?(\d{4}|\d{2})(?!\d)"""

/**
 * Sampling date, otherwise the date performed or recorded (the same day in practice):
 * a report whose "Prélevé le" line is unreadable keeps a plausible date.
 */
private val SAMPLE_DATES = listOf(
    Regex("""prelev\w*\s+(?:le|du)\s+$DATE"""),
    Regex("""(?:realises?|enregistre)\s+le\s+$DATE"""),
)

private fun sampleDate(rows: List<List<TextLine>>): LocalDate? {
    val texts = rows.map { row -> normalize(row.joinToString(" ") { it.text }) }
    for (pattern in SAMPLE_DATES) {
        for (text in texts) {
            val match = pattern.find(text) ?: continue
            val (day, month, rawYear) = match.destructured
            val year = rawYear.toInt().let { if (rawYear.length == 2) 2000 + it else it }
            return runCatching { LocalDate(year, month.toInt(), day.toInt()) }.getOrNull() ?: continue
        }
    }
    return null
}
