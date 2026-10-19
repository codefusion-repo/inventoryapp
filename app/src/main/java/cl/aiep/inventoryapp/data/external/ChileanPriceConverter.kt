package cl.aiep.inventoryapp.data.external

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class DolarIndicatorItem(val fecha: String? = null, val valor: Double? = null)

object ChileanPriceConverter {
    fun toClp(usd: Double, clpPerUsd: Double?): Long? {
        if (!usd.isFinite() || usd < 0.0 || clpPerUsd == null ||
            !clpPerUsd.isFinite() || clpPerUsd <= 0.0) return null
        return try {
            BigDecimal.valueOf(usd).multiply(BigDecimal.valueOf(clpPerUsd))
                .setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: ArithmeticException) { null }
    }

    /** Valida cotizaciones recientes. Se toleran fines de semana y feriados. */
    fun latestValidQuote(serie: List<DolarIndicatorItem>?, nowMillis: Long): DolarIndicatorItem? {
        val parse = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return serie.orEmpty().mapNotNull { item ->
            val rate = item.valor
            val date = item.fecha?.let { runCatching { parse.parse(it) }.getOrNull()?.time }
            if (rate == null || !rate.isFinite() || rate <= 0.0 || date == null ||
                date > nowMillis + TimeUnit.HOURS.toMillis(24) ||
                nowMillis - date > TimeUnit.DAYS.toMillis(7)) null else date to item
        }.maxByOrNull { it.first }?.second
    }
}
