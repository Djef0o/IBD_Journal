package fr.ibdjournal.app.domain

import fr.ibdjournal.app.data.model.ActivityScore
import fr.ibdjournal.app.data.model.Diagnosis
import fr.ibdjournal.app.data.model.ScoreKind
import fr.ibdjournal.app.data.model.Stool

/**
 * Questionnaires, in the order of [ActivityScore.answers].
 *
 * HBI (Harvey-Bradshaw index, Crohn's disease), for the day:
 * 0 general well-being (0 very well … 4 terrible); 1 abdominal pain (0 none … 3 severe);
 * 2 number of liquid or soft stools; 3 abdominal mass (0 none … 3 definite and tender);
 * 4 complications, bit mask of [HBI_COMPLICATIONS] items, 1 point each.
 *
 * SCCAI (Simple Clinical Colitis Activity Index, ulcerative colitis):
 * 0 stools per day (0: 1–3, 1: 4–6, 2: 7–9, 3: more); 1 stools at night (0: none, 1: 1–3, 2: 4–6);
 * 2 urgency (0 none, 1 hurry, 2 immediate, 3 incontinence); 3 blood in stool (0 none … 3 usually frank);
 * 4 general well-being (0 very well … 4 terrible); 5 extracolonic features, bit mask of
 * [SCCAI_EXTRACOLONIC] items, 1 point each. Maximum 19.
 */
const val HBI_COMPLICATIONS = 8
const val SCCAI_EXTRACOLONIC = 4

/** Answer count of each questionnaire. */
val ScoreKind.questionCount: Int get() = when (this) {
    ScoreKind.HBI -> 5
    ScoreKind.SCCAI -> 6
}

/** Total of the questionnaire; missing answers count as 0. */
val ActivityScore.total: Int
    get() {
        fun answer(index: Int) = answers.getOrElse(index) { 0 }.coerceAtLeast(0)
        return when (kind) {
            ScoreKind.HBI -> (0..3).sumOf(::answer) + answer(4).countOneBits()
            ScoreKind.SCCAI -> (0..4).sumOf(::answer) + answer(5).countOneBits()
        }
    }

enum class ActivityLevel { REMISSION, MILD, MODERATE, SEVERE, ACTIVE }

/**
 * Usual interpretation: HBI under 5 remission, 5–7 mild, 8–16 moderate, above 16 severe;
 * SCCAI up to 2 remission, 5 and above active, in between none.
 */
val ActivityScore.level: ActivityLevel?
    get() = when (kind) {
        ScoreKind.HBI -> when {
            total < 5 -> ActivityLevel.REMISSION
            total <= 7 -> ActivityLevel.MILD
            total <= 16 -> ActivityLevel.MODERATE
            else -> ActivityLevel.SEVERE
        }
        ScoreKind.SCCAI -> when {
            total <= 2 -> ActivityLevel.REMISSION
            total >= 5 -> ActivityLevel.ACTIVE
            else -> null
        }
    }

/** Questionnaire suggested by the diagnosis; null if unknown. */
val Diagnosis?.suggestedScore: ScoreKind?
    get() = when (this) {
        Diagnosis.CROHN -> ScoreKind.HBI
        Diagnosis.ULCERATIVE_COLITIS -> ScoreKind.SCCAI
        else -> null
    }

/**
 * Answers that the day's stools settle: liquid stools (HBI), daily and night frequency and
 * urgency (SCCAI). The others stay at 0 for the user to answer. [dayStools] all from the same date.
 */
fun prefilledAnswers(kind: ScoreKind, dayStools: List<Stool>): List<Int> {
    val real = dayStools.filter { it.isRealStool }
    return when (kind) {
        ScoreKind.HBI -> listOf(0, 0, real.count { it.bristolType >= 6 }, 0, 0)
        ScoreKind.SCCAI -> listOf(
            when {
                real.size <= 3 -> 0
                real.size <= 6 -> 1
                real.size <= 9 -> 2
                else -> 3
            },
            when (real.count { it.nocturnal }) {
                0 -> 0
                in 1..3 -> 1
                else -> 2
            },
            if (real.any { it.urgency }) 1 else 0,
            0, 0, 0,
        )
    }
}
