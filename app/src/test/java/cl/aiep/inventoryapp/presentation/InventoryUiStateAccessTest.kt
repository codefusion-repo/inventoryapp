package cl.aiep.inventoryapp.presentation

import cl.aiep.inventoryapp.domain.InventoryRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryUiStateAccessTest {
    @Test fun guestHasNoWriteControls() {
        val state = InventoryUiState()
        assertTrue(state.isGuest)
        assertFalse(state.canManage)
        assertFalse(state.canUseStockShortcut)
    }

    @Test fun sellerOnlyHasQuantityShortcut() {
        val state = InventoryUiState(userEmail = "vendedor@example.test", role = InventoryRole.VENDEDOR)
        assertTrue(state.canEditQuantity)
        assertTrue(state.canUseStockShortcut)
        assertFalse(state.canManage)
    }

    @Test fun warehouseHasManagementButNoRedundantShortcut() {
        val state = InventoryUiState(userEmail = "almacen@example.test", role = InventoryRole.ALMACEN)
        assertTrue(state.canEditQuantity)
        assertTrue(state.canManage)
        assertFalse(state.canUseStockShortcut)
    }
}
