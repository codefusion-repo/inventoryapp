package cl.aiep.inventoryapp.domain

import cl.aiep.inventoryapp.presentation.InventoryUiState
import org.junit.Assert.*
import org.junit.Test

class GuestCatalogTest {
    @Test fun guestReadsCloudWithoutEmployeePrivileges() {
        val guest = InventoryUiState(cloudSessionActive = true)
        assertTrue(guest.isGuest)
        assertTrue(guest.canReadCloud)
        assertFalse(guest.canEditQuantity)
        assertFalse(guest.canManage)
    }

    @Test fun guestOfflineFallbackDoesNotClaimCloudConnection() {
        val offline = InventoryUiState(cloudSessionActive = false)
        assertTrue(offline.isGuest)
        assertFalse(offline.canReadCloud)
    }
}
