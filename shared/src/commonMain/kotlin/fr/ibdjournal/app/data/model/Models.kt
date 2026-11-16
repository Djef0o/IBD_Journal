package fr.ibdjournal.app.data.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** Horodatage courant en millisecondes, disponible sur toutes les plateformes. */
@OptIn(ExperimentalTime::class)
fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Les trois moments de repas suivis chaque jour. Libellés : côté plateforme. */
@Serializable
enum class MealType { BREAKFAST, LUNCH, DINNER }

/**
 * Un repas enregistré pour un jour donné. Les dates sont sérialisées en ISO (« 2026-11-15 »),
 * comme avant le passage à kotlinx-datetime : les sauvegardes existantes restent lisibles.
 */
@Serializable
data class Meal(
    val id: Long = 0,
    val date: LocalDate,
    val type: MealType,
    val description: String,
    val photoUri: String? = null,
    val product: Product? = null,
)

/**
 * Une selle du jour avec détails médicaux (échelle de Bristol, sang, etc.). [urgency] : besoin
 * impérieux ; [nocturnal] : selle qui a réveillé la nuit (critères des scores d'activité).
 */
@Serializable
data class Stool(
    val id: Long = 0,
    val date: LocalDate,
    val bristolType: Int = 4, // 1 à 7
    val bloodLevel: Int = 0, // 0 à 5
    val mucusLevel: Int = 0, // 0 à 5
    val painLevel: Int = 0, // 0 à 5
    val fatigueLevel: Int = 0, // 0 à 5
    val comment: String = "",
    val createdAt: Long = nowEpochMillis(),
    val urgency: Boolean = false,
    val nocturnal: Boolean = false,
)

/** Produit alimentaire (issu d'Open Food Facts ou du cache local). */
@Serializable
data class Product(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val nutriscore: String?,
    val imageUrl: String?,
    val ingredients: String?,
)

/**
 * Objet racine pour l'export/import JSON. Les sections absentes (null) des sauvegardes
 * antérieures laissent les données actuelles intactes à l'import.
 */
@Serializable
data class BackupData(
    val meals: List<Meal>,
    val stools: List<Stool>,
    val profile: Profile? = null,
    val medications: List<Medication>? = null,
    val checkups: List<Checkup>? = null,
    val labResults: List<LabResult>? = null,
    val doseLogs: List<DoseLog>? = null,
)

/** Résultat d'une recherche de produit par code-barres. */
sealed interface ProductLookup {
    data class Found(val product: Product) : ProductLookup
    data class NotFound(val barcode: String) : ProductLookup
}
