package fr.ibdjournal.app.data.model

import androidx.annotation.StringRes
import fr.ibdjournal.app.R
import java.time.LocalDate

/** Les trois moments de repas suivis chaque jour. */
enum class MealType(@StringRes val labelRes: Int) {
    BREAKFAST(R.string.meal_breakfast),
    LUNCH(R.string.meal_lunch),
    DINNER(R.string.meal_dinner),
}

/** Un repas enregistré pour un jour donné. */
data class Meal(
    val id: Long = 0,
    val date: LocalDate,
    val type: MealType,
    val description: String,
    val photoUri: String? = null,
    val product: Product? = null,
)

/** Une selle du jour avec détails médicaux (échelle de Bristol, sang, etc.). */
data class Stool(
    val id: Long = 0,
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
data class Product(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val nutriscore: String?,
    val imageUrl: String?,
    val ingredients: String?,
)

/** Résultat d'une recherche de produit par code-barres. */
sealed interface ProductLookup {
    data class Found(val product: Product) : ProductLookup
    data class NotFound(val barcode: String) : ProductLookup
}
