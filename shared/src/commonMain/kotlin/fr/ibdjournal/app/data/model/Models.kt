package fr.ibdjournal.app.data.model

import androidx.annotation.StringRes
import fr.ibdjournal.app.R
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

/** Les trois moments de repas suivis chaque jour. */
@Serializable
enum class MealType(@StringRes val labelRes: Int) {
    BREAKFAST(R.string.meal_breakfast),
    LUNCH(R.string.meal_lunch),
    DINNER(R.string.meal_dinner),
}

/** Un repas enregistré pour un jour donné. */
@Serializable
data class Meal(
    val id: Long = 0,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val type: MealType,
    val description: String,
    val photoUri: String? = null,
    val product: Product? = null,
)

/** Une selle du jour avec détails médicaux (échelle de Bristol, sang, etc.). */
@Serializable
data class Stool(
    val id: Long = 0,
    @Serializable(with = LocalDateSerializer::class)
    val date: LocalDate,
    val bristolType: Int = 4, // 1 à 7
    val bloodLevel: Int = 0, // 0 à 5
    val mucusLevel: Int = 0, // 0 à 5
    val painLevel: Int = 0, // 0 à 5
    val fatigueLevel: Int = 0, // 0 à 5
    val comment: String = "",
    val createdAt: Long = System.currentTimeMillis(),
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
 * Objet racine pour l'export/import JSON. Profil, traitements et contrôles sont absents
 * (null) des sauvegardes antérieures : l'import conserve alors les données actuelles.
 */
@Serializable
data class BackupData(
    val meals: List<Meal>,
    val stools: List<Stool>,
    val profile: Profile? = null,
    val medications: List<Medication>? = null,
    val checkups: List<Checkup>? = null,
)

/** Résultat d'une recherche de produit par code-barres. */
sealed interface ProductLookup {
    data class Found(val product: Product) : ProductLookup
    data class NotFound(val barcode: String) : ProductLookup
}
