package fr.ibdjournal.app.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
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
import fr.ibdjournal.app.security.Vault
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
    private val vault: Vault,
    private val api: OpenFoodFactsApi,
    @ApplicationContext private val context: Context,
) {
    private val mealDao get() = vault.requireDatabase().mealDao()
    private val stoolDao get() = vault.requireDatabase().stoolDao()
    private val productDao get() = vault.requireDatabase().productDao()

    // --- Reading ---

    fun observeMeals(from: LocalDate, to: LocalDate): Flow<List<Meal>> =
        vault.observe { it.mealDao().observeBetween(from.toEpochDays(), to.toEpochDays()) }
            .map { list -> list.map { it.toMeal() } }

    fun observeStools(from: LocalDate, to: LocalDate): Flow<List<Stool>> =
        vault.observe { it.stoolDao().observeBetween(from.toEpochDays(), to.toEpochDays()) }
            .map { list -> list.map { it.toStool() } }

    fun observeAllStools(): Flow<List<Stool>> =
        vault.observe { it.stoolDao().observeAll() }.map { list -> list.map { it.toStool() } }

    // --- Meals ---

    suspend fun saveMeal(meal: Meal) = mealDao.upsert(meal.toMealEntity())

    suspend fun deleteMeal(meal: Meal) {
        meal.photoUri?.let { deletePhoto(it) }
        mealDao.delete(meal.toMealEntity())
    }

    // --- Photos (copied into the app's internal storage, encrypted) ---

    /** Copies a chosen photo, encrypted, into internal storage and returns its relative path. */
    suspend fun importPhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val plain = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null
            val dir = File(context.filesDir, Vault.PHOTOS_DIR).apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            file.writeBytes(vault.photos().encrypt(plain))
            file.relativeTo(context.filesDir).path
        }.getOrNull()
    }

    /** Decrypted photo (relative path), null if missing or unreadable. */
    suspend fun readPhoto(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { vault.photos().decrypt(File(context.filesDir, relativePath).readBytes()) }.getOrNull()
    }

    /** Deletes a photo from internal storage (relative path). */
    suspend fun deletePhoto(relativePath: String) = withContext(Dispatchers.IO) {
        runCatching { File(context.filesDir, relativePath).delete() }
    }

    // --- Stools ---

    /** Saves (or replaces) the stool and returns its identifier. */
    suspend fun saveStool(stool: Stool): Long = stoolDao.upsert(stool.toStoolEntity())

    suspend fun getStool(id: Long): Stool? = stoolDao.get(id)?.toStool()

    suspend fun deleteStool(stool: Stool) = stoolDao.deleteById(stool.id)

    // --- Open Food Facts products (with local cache) ---

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
