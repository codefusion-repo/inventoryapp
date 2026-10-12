package cl.aiep.inventoryapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InventoryValidationTest {
    @Test fun validProduct() {
        assertNull(InventoryValidation.validate("Mouse", "Proveedor", 2, 10990))
    }
    @Test fun negativeQuantityIsRejected() {
        assertEquals("La cantidad no puede ser negativa.",
            InventoryValidation.validate("Mouse", "Proveedor", -1, 10990))
    }
    @Test fun emptySupplierIsRejected() {
        assertEquals("El proveedor es obligatorio.",
            InventoryValidation.validate("Mouse", "", 1, 10990))
    }
}
