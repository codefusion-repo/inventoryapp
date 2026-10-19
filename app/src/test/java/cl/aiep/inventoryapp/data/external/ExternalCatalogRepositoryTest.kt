package cl.aiep.inventoryapp.data.external

import cl.aiep.inventoryapp.domain.ExternalCatalogCategory
import cl.aiep.inventoryapp.domain.toImportDraft
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ExternalCatalogRepositoryTest {
    private fun repository(server: MockWebServer, rate: Double? = 950.0): ExternalCatalogRepository =
        ExternalCatalogRepository(
            api = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(ExternalCatalogApi::class.java),
            rateProvider = object : UsdClpRateProvider {
                override suspend fun currentRate(): Double? = rate
            }
        )

    @Test fun `parsea JSON y precarga el valor convertido a CLP`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("""
                {"products":[{"id":101,"title":"Notebook portátil","description":"SSD 512 GB",
                "brand":"Fabricante A","price":599.99,"thumbnail":"https://example.com/photo.jpg"}],"total":1}
            """.trimIndent()))

            val products = repository(server).getProducts(ExternalCatalogCategory.LAPTOPS)
            assertEquals(1, products.size)
            assertEquals("Notebook portátil", products.single().name)
            assertEquals(599.99, products.single().priceUsd, 0.001)
            assertEquals(569991L, products.single().priceClp)
            assertEquals("https://example.com/photo.jpg", products.single().thumbnailUrl)
            assertEquals("/products/category/laptops?limit=20", server.takeRequest().path)
            val draft = products.single().toImportDraft()
            assertEquals("Computadores", draft.category)
            assertEquals("Fabricante A", draft.supplier)
            assertEquals("569991", draft.priceClpText)
            assertEquals("", draft.quantityText)
        }
    }

    @Test fun `cotizacion no disponible no inventa precios`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("""
                {"products":[{"id":1,"title":"Tablet","price":199.0}],"total":1}
            """.trimIndent()))
            val product = repository(server, rate = null).getProducts(ExternalCatalogCategory.TABLETS).single()
            assertNull(product.priceClp)
            assertEquals("", product.toImportDraft().priceClpText)
        }
    }

    @Test fun `omite registros invalidos sin inventar datos`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("""
                {"products":[{"id":1,"title":"Tablet","price":199.0},
                 {"id":2,"title":" ","price":10.0},
                 {"id":3,"title":"Inválido","price":-2.0}],"total":3}
            """.trimIndent()))
            val products = repository(server).getProducts(ExternalCatalogCategory.TABLETS)
            assertEquals(1, products.size)
            assertEquals("Proveedor por confirmar", products.single().supplier)
        }
    }

    @Test fun `trata JSON sin lista de productos como respuesta incompatible`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"total":0}"""))
            val error = runCatching { repository(server).getProducts(ExternalCatalogCategory.LAPTOPS) }.exceptionOrNull()
            assertTrue(error is InvalidExternalResponseException)
        }
    }

    @Test fun `errores HTTP se muestran sin detalles internos`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503).setBody("Unavailable"))
            val error = runCatching { repository(server).getProducts(ExternalCatalogCategory.LAPTOPS) }.exceptionOrNull()
            assertTrue(error is HttpException)
            val message = ExternalCatalogErrorMessages.explain(requireNotNull(error))
            assertTrue(message.contains("no está disponible"))
            assertFalse(message.contains("Exception"))
        }
    }
}
