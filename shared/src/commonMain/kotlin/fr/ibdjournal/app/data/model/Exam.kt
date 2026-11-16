package fr.ibdjournal.app.data.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

/** Examens endoscopiques suivis dans Soins. */
@Serializable
enum class ExamType { COLONOSCOPY, RECTOSIGMOIDOSCOPY }

/**
 * Examen programmé et sa préparation : régime sans résidu ([dietDays] jours avant, 0 = aucun),
 * prises de la préparation à boire ([prepFirstAt], [prepSecondAt] pour une prise fractionnée)
 * et, pour la coloscopie sous anesthésie, la consultation d'anesthésie ([anesthesiaAt], null
 * tant qu'elle n'est pas fixée). [remindersEnabled] : rappels de chaque étape.
 */
@Serializable
data class Exam(
    val id: Long = 0,
    val type: ExamType,
    val at: LocalDateTime,
    val note: String = "",
    val dietDays: Int = 0,
    val prepFirstAt: LocalDateTime? = null,
    val prepSecondAt: LocalDateTime? = null,
    val anesthesiaAt: LocalDateTime? = null,
    val remindersEnabled: Boolean = true,
)
