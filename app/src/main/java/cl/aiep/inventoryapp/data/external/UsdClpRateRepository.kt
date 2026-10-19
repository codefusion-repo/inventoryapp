package cl.aiep.inventoryapp.data.external

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Si no existe una cotización reciente, se omite el precio antes que inventar un valor. */
interface UsdClpRateProvider {
    suspend fun currentRate(): Double?
}

/**
 * Consulta el dólar observado por HTTPS y mantiene una cotización reciente en preferencias.
 * Los valores son referenciales, no precios finales de compra ni venta.
 */
class UsdClpRateRepository(context: Context) : UsdClpRateProvider {
    private val prefs = context.applicationContext.getSharedPreferences("inventory_exchange_rate", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val api: DolarIndicatorApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(13, TimeUnit.SECONDS).build()
        Retrofit.Builder().baseUrl("https://mindicador.cl/").client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(DolarIndicatorApi::class.java)
    }

    override suspend fun currentRate(): Double? = mutex.withLock {
        val now = System.currentTimeMillis()
        val cached = cachedRate(now)
        val lastChecked = prefs.getLong("checked_at", 0L)
        if (cached != null && now - lastChecked in 0..TimeUnit.HOURS.toMillis(6)) return@withLock cached

        val fresh = try {
            ChileanPriceConverter.latestValidQuote(api.recentDollarQuotes().serie, now)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (fresh?.valor != null && fresh.fecha != null) {
            prefs.edit()
                .putString("rate", fresh.valor.toString())
                .putString("observed_on", fresh.fecha)
                .putLong("checked_at", now).apply()
            return@withLock fresh.valor
        }
        // Sin conectividad o con una respuesta obsoleta solo reutilizamos un dato aún vigente.
        cached
    }

    private fun cachedRate(now: Long): Double? {
        val rate = prefs.getString("rate", null)?.toDoubleOrNull() ?: return null
        val date = prefs.getString("observed_on", null) ?: return null
        return ChileanPriceConverter.latestValidQuote(listOf(DolarIndicatorItem(date, rate)), now)?.valor
    }
}
