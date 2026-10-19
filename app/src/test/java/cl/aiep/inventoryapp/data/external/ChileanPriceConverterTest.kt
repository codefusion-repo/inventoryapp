package cl.aiep.inventoryapp.data.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ChileanPriceConverterTest {
    @Test fun `redondea estimaciones al centenar de pesos mas cercano`() {
        assertEquals(570000L, ChileanPriceConverter.toClp(599.99, 950.0))
        assertEquals(9300L, ChileanPriceConverter.toClp(9.5, 980.5))
        assertEquals(10100L, ChileanPriceConverter.toClp(10.05, 1000.0))
        assertEquals(10000L, ChileanPriceConverter.toClp(10.04, 1000.0))
    }

    @Test fun `rechaza valores imposibles o sin cotizacion`() {
        assertNull(ChileanPriceConverter.toClp(20.0, null))
        assertNull(ChileanPriceConverter.toClp(-5.0, 950.0))
        assertNull(ChileanPriceConverter.toClp(Double.NaN, 950.0))
        assertNull(ChileanPriceConverter.toClp(20.0, 0.0))
    }

    @Test fun `ignora cotizaciones obsoletas y usa la mas reciente`() {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val now = requireNotNull(parser.parse("2026-10-19T20:00:00.000Z")).time
        val series = listOf(
            DolarIndicatorItem("2026-07-01T12:00:00.000Z", 800.0),
            DolarIndicatorItem("2026-10-18T12:00:00.000Z", 980.5),
            DolarIndicatorItem("2026-10-17T12:00:00.000Z", 979.0)
        )
        assertEquals(980.5, requireNotNull(ChileanPriceConverter.latestValidQuote(series, now)).valor!!, 0.0001)
        assertNull(ChileanPriceConverter.latestValidQuote(series.take(1), now))
    }
}
