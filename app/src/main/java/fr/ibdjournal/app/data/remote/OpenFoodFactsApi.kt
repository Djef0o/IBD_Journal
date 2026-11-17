package fr.ibdjournal.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Open Food Facts API (v2) client. */
interface OpenFoodFactsApi {

    /**
     * Looks up a product by barcode.
     * See https://openfoodfacts.github.io/openfoodfacts-server/api/ref/#get-/api/v2/product/-barcode-
     */
    @GET("api/v2/product/{barcode}.json")
    suspend fun getProduct(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = PRODUCT_FIELDS,
    ): OffResponse

    companion object {
        private const val PRODUCT_FIELDS =
            "product_name,brands,nutriscore_grade,image_front_url,ingredients_text_fr"
    }
}

@Serializable
data class OffResponse(
    val status: Int = 0,
    val product: OffProduct? = null,
)

@Serializable
data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    val brands: String? = null,
    @SerialName("nutriscore_grade") val nutriscoreGrade: String? = null,
    @SerialName("image_front_url") val imageFrontUrl: String? = null,
    @SerialName("ingredients_text_fr") val ingredientsTextFr: String? = null,
)
