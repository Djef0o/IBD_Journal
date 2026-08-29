package fr.ibdjournal.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
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

// --- Mappeurs entité <-> modèle de domaine ---

fun MealEntity.toMeal() = Meal(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    type = mealType,
    description = description,
    photoPath = photoPath,
    productBarcode = productBarcode,
    productName = productName,
)

fun Meal.toMealEntity() = MealEntity(
    id = id,
    dateEpochDay = date.toEpochDay(),
    mealType = type,
    description = description,
    photoPath = photoPath,
    productBarcode = productBarcode,
    productName = productName,
)

fun StoolEntity.toStool() = Stool(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
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
