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
    val photoPath: String? = null,
    val productBarcode: String? = null,
    val productName: String? = null,
)

/** Une selle du jour, avec commentaire facultatif. */
data class Stool(
    val id: Long = 0,
    val date: LocalDate,
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
