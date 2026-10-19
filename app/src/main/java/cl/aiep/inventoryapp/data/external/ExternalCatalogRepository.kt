package cl.aiep.inventoryapp.data.external

import cl.aiep.inventoryapp.domain.ExternalCatalogCategory
import cl.aiep.inventoryapp.domain.ExternalCatalogProduct
import com.google.gson.JsonParseException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Consulta el proveedor externo. Una falla de cotización no impide ver los artículos. */
class ExternalCatalogRepository(
    private val api: ExternalCatalogApi = defaultApi,
    private val rateProvider: UsdClpRateProvider
) {
    suspend fun getProducts(category: ExternalCatalogCategory): List<ExternalCatalogProduct> = coroutineScope {
        val rate = async {
            try { rateProvider.currentRate() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { null }
        }
        val response = api.productsByCategory(category.slug)
        val products = response.products ?: throw InvalidExternalResponseException()
        val clpPerUsd = rate.await()
        products.mapNotNull { it.toDomain(category, clpPerUsd) }
    }

    companion object {
        private val defaultApi: ExternalCatalogApi by lazy {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
            Retrofit.Builder().baseUrl("https://dummyjson.com/").client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(ExternalCatalogApi::class.java)
        }
    }
}

class InvalidExternalResponseException : IllegalStateException("JSON externo no compatible")

/** Mensajes útiles sin exponer excepciones ni datos internos del servicio. */
object ExternalCatalogErrorMessages {
    fun explain(error: Throwable): String = when (error) {
        is UnknownHostException, is SocketTimeoutException ->
            "No pudimos cargar los productos. Comprueba tu conexión e inténtalo otra vez."
        is HttpException -> when (error.code()) {
            429 -> "Estamos recibiendo muchas solicitudes. Prueba nuevamente en unos minutos."
            in 500..599 -> "El catálogo no está disponible por ahora. Vuelve a intentarlo más tarde."
            else -> "No pudimos cargar los productos en este momento."
        }
        is InvalidExternalResponseException, is JsonParseException ->
            "Recibimos información incompleta. Intenta actualizar el catálogo."
        is IOException -> "Se interrumpió la consulta. Revisa tu conexión y vuelve a intentarlo."
        else -> "Ocurrió un problema al cargar los productos. Inténtalo de nuevo."
    }
}

private fun ExternalProductDto.toDomain(category: ExternalCatalogCategory, rate: Double?): ExternalCatalogProduct? {
    val validatedId = id?.takeIf { it > 0 } ?: return null
    val validatedName = title?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val validatedPrice = price?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    return ExternalCatalogProduct(
        externalId = validatedId,
        name = validatedName,
        description = description.orEmpty().trim(),
        supplier = brand?.trim()?.takeIf { it.isNotBlank() } ?: "Proveedor por confirmar",
        priceUsd = validatedPrice,
        category = category,
        priceClp = ChileanPriceConverter.toClp(validatedPrice, rate),
        thumbnailUrl = thumbnail?.takeIf { it.startsWith("https://") },
        rating = rating?.takeIf { it.isFinite() && it in 0.0..5.0 }
    )
}
