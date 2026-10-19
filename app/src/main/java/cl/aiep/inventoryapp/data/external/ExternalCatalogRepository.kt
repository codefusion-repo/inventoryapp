package cl.aiep.inventoryapp.data.external

import cl.aiep.inventoryapp.domain.ExternalCatalogCategory
import cl.aiep.inventoryapp.domain.ExternalCatalogProduct
import com.google.gson.JsonParseException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Solo lectura de un servicio público de demostración; nunca escribe en Firebase. */
class ExternalCatalogRepository(private val api: ExternalCatalogApi = defaultApi) {
    suspend fun getProducts(category: ExternalCatalogCategory): List<ExternalCatalogProduct> {
        val response = api.productsByCategory(category.slug)
        val products = response.products ?: throw InvalidExternalResponseException()
        return products.mapNotNull { it.toDomain(category) }
    }

    companion object {
        private val defaultApi: ExternalCatalogApi by lazy {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
            Retrofit.Builder()
                .baseUrl("https://dummyjson.com/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ExternalCatalogApi::class.java)
        }
    }
}

class InvalidExternalResponseException : IllegalStateException("JSON externo no compatible")

/** No se expone al usuario información interna de la librería o infraestructura. */
object ExternalCatalogErrorMessages {
    fun explain(error: Throwable): String = when (error) {
        is UnknownHostException, is SocketTimeoutException ->
            "No pudimos contactar al catálogo externo. Revisa tu conexión y vuelve a intentarlo."
        is HttpException -> when (error.code()) {
            429 -> "El servicio está recibiendo demasiadas consultas. Intenta de nuevo en unos minutos."
            in 500..599 -> "El catálogo externo no está disponible por el momento. Vuelve a intentarlo más tarde."
            else -> "No pudimos consultar el catálogo externo (HTTP ${error.code()})."
        }
        is InvalidExternalResponseException, is JsonParseException ->
            "El servicio respondió con datos que no pudimos interpretar. Intenta más tarde."
        is IOException -> "No pudimos completar la consulta. Verifica tu conexión e inténtalo nuevamente."
        else -> "No se pudo cargar el catálogo externo. Vuelve a intentarlo."
    }
}

private fun ExternalProductDto.toDomain(category: ExternalCatalogCategory): ExternalCatalogProduct? {
    val validatedId = id?.takeIf { it > 0 } ?: return null
    val validatedName = title?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val validatedPrice = price?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    return ExternalCatalogProduct(
        externalId = validatedId,
        name = validatedName,
        description = description.orEmpty().trim(),
        supplier = brand?.trim()?.takeIf { it.isNotBlank() } ?: "Proveedor por confirmar",
        priceUsd = validatedPrice,
        category = category
    )
}
