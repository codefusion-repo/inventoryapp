package cl.aiep.inventoryapp.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class ClpPriceFormatterTest {
    @Test fun `precio clp sin decimales ni comas`() {
        assertEquals("\$0", formatClp(0))
        assertEquals("\$899", formatClp(899))
        assertEquals("\$8.990", formatClp(8990))
        assertEquals("\$154.900", formatClp(154900))
        assertEquals("\$1.234.567", formatClp(1234567))
    }
}
