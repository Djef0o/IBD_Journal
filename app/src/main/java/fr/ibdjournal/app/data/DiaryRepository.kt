package fr.ibdjournal.app.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.ibdjournal.app.data.local.AppDatabase
import fr.ibdjournal.app.data.local.toMeal
import fr.ibdjournal.app.data.local.toMealEntity
import fr.ibdjournal.app.data.local.toProduct
import fr.ibdjournal.app.data.local.toProductEntity
import fr.ibdjournal.app.data.local.toStool
import fr.ibdjournal.app.data.local.toStoolEntity
import fr.ibdjournal.app.data.model.Meal
import fr.ibdjournal.app.data.model.Product
import fr.ibdjournal.app.data.model.ProductLookup
import fr.ibdjournal.app.data.model.Stool
import fr.ibdjournal.app.data.remote.OpenFoodFactsApi
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

@Singleton
class DiaryRepository @Inject constructor(
    database: AppDatabase,
    private val api: OpenFoodFactsApi,
    @ApplicationContext private val context: Context,
) {
    private val mealDao = database.mealDao()
    private val stoolDao = database.stoolDao()
    private val productDao = database.productDao()

    // --- Lecture ---

    fun observeMeals(from: LocalDate, to: LocalDate): Flow<List<Meal>> =
        mealDao.observeBetween(from.toEpochDays(), to.toEpochDays())
            .map { list -> list.map { it.toMeal() } }

    fun observeStools(from: LocalDate, to: LocalDate): Flow<List<Stool>> =
        stoolDao.observeBetween(from.toEpochDays(), to.toEpochDays())
            .map { list -> list.map { it.toStool() } }

    fun observeAllStools(): Flow<List<Stool>> =
        stoolDao.observeAll().map { list -> list.map { it.toStool() } }

    // --- Repas ---

    suspend fun saveMeal(meal: Meal) = mealDao.upsert(meal.toMealEntity())

    suspend fun deleteMeal(meal: Meal) {
        meal.photoUri?.let { deletePhoto(it) }
        mealDao.delete(meal.toMealEntity())
    }

    // --- Photos (copiées dans le stockage interne de l'app) ---

    /** Copie une photo choisie dans le stockage interne et renvoie son chemin relatif. */
    suspend fun importPhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            file.relativeTo(context.filesDir).path
        }.getOrNull()
    }

    /** Supprime une photo du stockage interne (chemin relatif). */
    suspend fun deletePhoto(relativePath: String) = withContext(Dispatchers.IO) {
        runCatching { File(context.filesDir, relativePath).delete() }
    }

    // --- Selles ---

    /** Enregistre (ou remplace) la selle et renvoie son identifiant. */
    suspend fun saveStool(stool: Stool): Long = stoolDao.upsert(stool.toStoolEntity())

    suspend fun getStool(id: Long): Stool? = stoolDao.get(id)?.toStool()

    suspend fun deleteStool(stool: Stool) = stoolDao.deleteById(stool.id)

    // --- Produits Open Food Facts (avec cache local) ---

    suspend fun lookupProduct(barcode: String): ProductLookup {
        productDao.get(barcode)?.let { cached ->
            return ProductLookup.Found(cached.toProduct())
        }
        return try {
            val response = api.getProduct(barcode)
            val off = response.product
            if (response.status == 1 && off != null && !off.productName.isNullOrBlank()) {
                val product = Product(
                    barcode = barcode,
                    name = off.productName,
                    brand = off.brands,
                    nutriscore = off.nutriscoreGrade,
                    imageUrl = off.imageFrontUrl,
                    ingredients = off.ingredientsTextFr,
                )
                productDao.upsert(product.toProductEntity())
                ProductLookup.Found(product)
            } else {
                ProductLookup.NotFound(barcode)
            }
        } catch (_: Exception) {
            ProductLookup.NotFound(barcode)
        }
    }
}
