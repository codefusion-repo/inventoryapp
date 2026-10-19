package cl.aiep.inventoryapp.data.external

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** JSON del proveedor externo; los precios originales no se muestran directamente en la UI. */
data class ExternalProductsResponse(
    val products: List<ExternalProductDto>? = null,
    val total: Int? = null
)

data class ExternalProductDto(
    val id: Int? = null,
    val title: String? = null,
    val description: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val price: Double? = null,
    val thumbnail: String? = null,
    val rating: Double? = null
)

interface ExternalCatalogApi {
    @GET("products/category/{category}")
    suspend fun productsByCategory(
        @Path("category") category: String,
        @Query("limit") limit: Int = 20
    ): ExternalProductsResponse
}

/** Dólar observado en Chile, cotización referencial para conversión. */
data class DolarIndicatorResponse(val serie: List<DolarIndicatorItem>? = null)

interface DolarIndicatorApi {
    @GET("api/dolar")
    suspend fun recentDollarQuotes(): DolarIndicatorResponse
}
