package fr.ibdjournal.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.MealType
import fr.ibdjournal.app.data.model.Product
import fr.ibdjournal.app.data.model.Stool
import java.time.LocalDate

@Entity(tableName = "meals", indices = [Index("dateEpochDay")])
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val mealType: MealType,
    val description: String = "",
    val photoPath: String? = null,
    val productBarcode: String? = null,
    val productName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "stools", indices = [Index("dateEpochDay")])
data class StoolEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val bristolType: Int,
    val bloodLevel: Int,
    val mucusLevel: Int,
    val painLevel: Int,
    val fatigueLevel: Int,
    val comment: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Cache des produits Open Food Facts, par code-barres. */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val barcode: String,
    val name: String?,
    val brand: String?,
    val nutriscore: String?,
    val imageUrl: String?,
    val ingredients: String?,
    val fetchedAt: Long = System.currentTimeMillis(),
)

/**
 * Repas avec la fiche produit complète du cache Open Food Facts. Le repas ne stocke
 * que code-barres et nom ; image, marque et Nutri-Score viennent de `products`.
 */
data class MealWithProduct(
    @Embedded val meal: MealEntity,
    @Relation(parentColumn = "productBarcode", entityColumn = "barcode")
    val product: ProductEntity?,
)

// --- Mappeurs entité <-> modèle de domaine ---

fun MealWithProduct.toMeal(): Meal {
    val base = meal.toMeal()
    return if (product != null) base.copy(product = product.toProduct()) else base
}

fun MealEntity.toMeal() = Meal(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    type = mealType,
    description = description,
    photoUri = photoPath,
    product = productBarcode?.let {
        Product(
            barcode = it,
            name = productName,
            brand = null,
            nutriscore = null,
            imageUrl = null,
            ingredients = null
        )
    },
)

fun Meal.toMealEntity() = MealEntity(
    id = id,
    dateEpochDay = date.toEpochDay(),
    mealType = type,
    description = description,
    photoPath = photoUri,
    productBarcode = product?.barcode,
    productName = product?.name,
)

fun StoolEntity.toStool() = Stool(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    bristolType = bristolType,
    bloodLevel = bloodLevel,
    mucusLevel = mucusLevel,
    painLevel = painLevel,
    fatigueLevel = fatigueLevel,
    comment = comment,
    createdAt = createdAt,
)

fun Stool.toStoolEntity() = StoolEntity(
    id = id,
    dateEpochDay = date.toEpochDay(),
    bristolType = bristolType,
    bloodLevel = bloodLevel,
    mucusLevel = mucusLevel,
    painLevel = painLevel,
    fatigueLevel = fatigueLevel,
    comment = comment,
    createdAt = createdAt,
)

fun ProductEntity.toProduct() = Product(
    barcode = barcode,
    name = name,
    brand = brand,
    nutriscore = nutriscore,
    imageUrl = imageUrl,
    ingredients = ingredients,
)

fun Product.toProductEntity() = ProductEntity(
    barcode = barcode,
    name = name,
    brand = brand,
    nutriscore = nutriscore,
    imageUrl = imageUrl,
    ingredients = ingredients,
)
