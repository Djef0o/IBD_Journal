package fr.ibdjournal.app

import fr.ibdjournal.app.data.model.LabTest
import fr.ibdjournal.app.domain.DetectedLabValue
import fr.ibdjournal.app.domain.TextLine
import fr.ibdjournal.app.domain.parseLabReport
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate

/**
 * Rangées d'un compte rendu Cerballiance complet réel (pdftotext -bbox), sans les lignes
 * d'identité : formule leucocytaire en % puis en valeur absolue, unités converties
 * (créatinine et bilirubine en mg/L → µmol/L), références « 30 à 300 », « > 60 », « < 50 ».
 */
class LabReportCatalogTest {

    private fun line(page: Int, left: Int, top: Int, right: Int, bottom: Int, text: String) =
        TextLine(text, left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), page)

    private val report = listOf(
        line(1, 31, 137, 155, 146, "Prélevé le 08.04.2026 à 07:26"),
        line(1, 53, 497, 103, 506, "Hématocrite"),
        line(1, 234, 500, 237, 505, "*"),
        line(1, 247, 497, 265, 506, "49,4"),
        line(1, 307, 498, 314, 506, "%"),
        line(1, 357, 498, 392, 506, "39,0 - 49,0"),
        line(1, 465, 498, 479, 506, "45,0"),
        line(1, 53, 509, 82, 518, "V.G.M"),
        line(1, 247, 509, 257, 518, "91"),
        line(1, 307, 510, 314, 518, "fL"),
        line(1, 357, 510, 380, 518, "78 - 97"),
        line(1, 465, 510, 473, 518, "89"),
        line(1, 53, 605, 127, 614, "Poly. neutrophiles"),
        line(1, 170, 606, 184, 614, "46,6"),
        line(1, 199, 606, 206, 614, "%"),
        line(1, 221, 606, 232, 614, "soit"),
        line(1, 247, 605, 265, 614, "2,84"),
        line(1, 300, 606, 322, 614, "Giga/L"),
        line(1, 357, 606, 394, 614, "1,78 - 6,95"),
        line(1, 465, 606, 479, 614, "3,06"),
        line(1, 53, 617, 127, 626, "Poly. éosinophiles"),
        line(1, 170, 618, 180, 626, "2,0"),
        line(1, 199, 618, 206, 626, "%"),
        line(1, 221, 618, 232, 626, "soit"),
        line(1, 247, 617, 265, 626, "0,12"),
        line(1, 300, 618, 322, 626, "Giga/L"),
        line(1, 357, 618, 392, 626, "0,05 - 0,63"),
        line(1, 465, 618, 479, 626, "0,16"),
        line(2, 53, 269, 117, 278, "CREATININE"),
        line(2, 247, 269, 265, 278, "11,2"),
        line(2, 300, 270, 317, 278, "mg/L"),
        line(2, 357, 270, 388, 278, "7,2 - 11,8"),
        line(2, 465, 270, 532, 278, "10,2 Le 11.10.2025"),
        line(2, 54, 284, 142, 289, "Enzymatique, AU Beckman (DI) (c)"),
        line(2, 206, 282, 218, 289, "soit"),
        line(2, 249, 282, 263, 289, "99,0"),
        line(2, 300, 282, 324, 290, "µmol/L"),
        line(2, 357, 282, 396, 290, "63,6 - 104,3"),
        line(2, 53, 365, 160, 374, "Débit de filtration calculé:"),
        line(2, 247, 365, 257, 374, "75"),
        line(2, 300, 366, 347, 374, "ml/mn/1,73m2"),
        line(2, 357, 366, 372, 374, "> 60"),
        // Graphique d'évolution du DFG, plus bas : libellé répété sans valeur sur sa rangée.
        line(2, 84, 385, 168, 393, "Débit de filtration calculé:"),
        line(2, 53, 581, 113, 590, "POTASSIUM"),
        line(2, 247, 581, 260, 590, "4,3"),
        line(2, 300, 582, 325, 590, "mmol/L"),
        line(2, 357, 582, 384, 590, "3,4 - 4,5"),
        line(2, 465, 582, 475, 590, "4,1"),
        line(2, 53, 629, 109, 638, "FERRITINE"),
        line(2, 247, 629, 262, 638, "167"),
        line(2, 300, 630, 319, 638, "ng/ml"),
        line(2, 357, 630, 385, 638, "30 à 300"),
        line(2, 465, 630, 530, 638, "171 Le 09.04.2025"),
        line(3, 53, 329, 200, 338, "TRANSAMINASES TGO (ASAT)"),
        line(3, 247, 329, 257, 338, "25"),
        line(3, 300, 330, 313, 338, "U/L"),
        line(3, 357, 330, 372, 338, "< 50"),
        line(3, 465, 330, 526, 338, "25 Le 09.04.2025"),
        line(3, 53, 473, 157, 482, "BILIRUBINE TOTALE"),
        line(3, 247, 473, 260, 482, "8,9"),
        line(3, 300, 474, 317, 482, "mg/L"),
        line(3, 357, 474, 388, 482, "3,0 - 12,0"),
        line(3, 465, 474, 475, 482, "9,4"),
        line(3, 53, 497, 214, 506, "BILIRUBINE DIRECTE (conjuguée)"),
        line(3, 247, 497, 260, 506, "1,9"),
        line(3, 300, 498, 317, 506, "mg/L"),
        line(3, 357, 498, 374, 506, "< 2,0"),
        line(3, 465, 498, 475, 506, "1,4"),
    )

    private fun assertClose(expected: Double, actual: Double?) =
        assertTrue(actual != null && abs(expected - actual) < 0.01, "attendu $expected, lu $actual")

    private fun Map<LabTest, DetectedLabValue>.check(test: LabTest, value: Double, low: Double?, high: Double?) {
        val detected = getValue(test)
        assertClose(value, detected.value)
        if (low == null) assertNull(detected.referenceLow, "$test : borne basse") else assertClose(low, detected.referenceLow)
        if (high == null) assertNull(detected.referenceHigh, "$test : borne haute") else assertClose(high, detected.referenceHigh)
    }

    @Test
    fun readsEveryValueOfAFullReport() {
        val scan = parseLabReport(report)
        val byTest = scan.values.associateBy { it.test }

        assertEquals(LocalDate(2026, 4, 8), scan.sampleDate)
        assertEquals(11, byTest.size, byTest.keys.toString())
        byTest.check(LabTest.HEMATOCRIT, 49.4, 39.0, 49.0)
        byTest.check(LabTest.MCV, 91.0, 78.0, 97.0)
        // Valeur absolue, pas le pourcentage imprimé avant.
        byTest.check(LabTest.NEUTROPHILS, 2.84, 1.78, 6.95)
        byTest.check(LabTest.EOSINOPHILS, 0.12, 0.05, 0.63)
        // mg/L → µmol/L, références converties de même (le labo imprime 99,0 et 63,6 – 104,3).
        byTest.check(LabTest.CREATININE, 99.0, 63.65, 104.31)
        byTest.check(LabTest.EGFR, 75.0, 60.0, null)
        byTest.check(LabTest.POTASSIUM, 4.3, 3.4, 4.5)
        byTest.check(LabTest.FERRITIN, 167.0, 30.0, 300.0)
        byTest.check(LabTest.AST, 25.0, null, 50.0)
        byTest.check(LabTest.BILIRUBIN_TOTAL, 15.22, 5.13, 20.52)
        byTest.check(LabTest.BILIRUBIN_DIRECT, 3.25, null, 3.42)
    }

    @Test
    fun survivesTheUnitsTheOcrDrops() {
        // Lectures OCR réelles de l'émulateur : point de la date lu comme une virgule, « % »
        // perdu devant la valeur absolue, unité du DFG mal lue (« mI/mn ») dont le « 1,73 »
        // ne doit pas passer pour le résultat.
        val scan = parseLabReport(
            listOf(
                line(1, 31, 137, 155, 146, "Prélevé le 19,05.2023 à 07:40"),
                line(1, 53, 593, 127, 602, "Poly. basophiles"),
                line(1, 170, 594, 184, 602, "1.1"),
                line(1, 221, 593, 265, 602, "Soit 0,07"),
                line(1, 300, 594, 322, 602, "Giga/L"),
                line(1, 357, 593, 394, 602, "0,00 -0, 10"),
                line(1, 465, 594, 479, 602, "0,08"),
                line(1, 53, 605, 127, 614, "Poly. neutrophiles"),
                line(1, 170, 606, 184, 614, "50,3"),
                line(1, 221, 606, 232, 614, "soit"),
                line(1, 247, 605, 265, 614, "2,92"),
                line(1, 300, 606, 322, 614, "Giga/L"),
                line(1, 357, 606, 394, 614, "1,78 - 6,95"),
                line(2, 53, 365, 160, 374, "Débit de filtration calculé:"),
                line(2, 247, 365, 257, 374, "74"),
                line(2, 300, 366, 347, 374, "mI/mn/1,73m2"),
                line(2, 357, 366, 372, 374, "> 60"),
            )
        )
        val byTest = scan.values.associateBy { it.test }
        assertEquals(LocalDate(2023, 5, 19), scan.sampleDate)
        byTest.check(LabTest.NEUTROPHILS, 2.92, 1.78, 6.95)
        assertClose(74.0, byTest[LabTest.EGFR]?.value)
        // « Soit 0,07 » fusionnés et référence « 0,00 -0, 10 » coupée par une espace.
        byTest.check(LabTest.BASOPHILS, 0.07, 0.0, 0.1)
    }

    @Test
    fun neverTakesAReferenceForALostResult() {
        // L'OCR a perdu le résultat : ni la référence ni l'antériorité ne doivent le remplacer.
        val scan = parseLabReport(
            listOf(
                line(1, 53, 100, 120, 109, "FER SERIQUE"),
                line(1, 300, 101, 320, 109, "µg/dL"),
                line(1, 357, 101, 400, 109, "à jeun 70-180"),
                line(1, 465, 101, 530, 109, "105 Le 17.08.2024"),
                line(1, 53, 120, 160, 129, "Débit de filtration calculé:"),
                line(1, 300, 121, 347, 129, "ml/mn/1,73m2"),
                line(1, 357, 121, 372, 129, "> 60"),
                line(1, 53, 140, 130, 149, "Bilirubine totale"),
                line(1, 357, 141, 372, 149, "< 24"),
                // Unité illisible elle aussi : l'antériorité « 318 » reste écartée.
                line(1, 53, 200, 160, 209, "CAPACITE TOTALE DE FIXATION"),
                line(1, 300, 201, 320, 209, "pg/dL"),
                line(1, 357, 201, 392, 209, "280 - 450"),
                line(1, 465, 201, 479, 209, "318"),
                // Unité perdue mais résultat lu : il reste le premier nombre.
                line(1, 53, 160, 110, 169, "Hémoglobine"),
                line(1, 247, 160, 265, 169, "16,1"),
                line(1, 357, 161, 392, 169, "13,4 - 16,7"),
                line(1, 465, 161, 479, 169, "17,1"),
            )
        )
        val byTest = scan.values.associateBy { it.test }
        assertEquals(setOf(LabTest.HEMOGLOBIN), byTest.keys)
        byTest.check(LabTest.HEMOGLOBIN, 16.1, 13.4, 16.7)
    }
}
