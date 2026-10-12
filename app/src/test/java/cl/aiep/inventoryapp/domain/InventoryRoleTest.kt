package cl.aiep.inventoryapp.domain

import org.junit.Assert.*
import org.junit.Test

class InventoryRoleTest {
    @Test fun guestCannotModifyInventory() {
        assertFalse(InventoryPermissions.canManage(null))
        assertFalse(InventoryPermissions.canEditQuantity(null))
    }

    @Test fun rolesUnknownAreDenied() {
        assertNull(InventoryRole.fromCloud("admin"))
        assertNull(InventoryRole.fromCloud(null))
        assertFalse(InventoryPermissions.canManage(null))
    }

    @Test fun sellerMayReadButNotWrite() {
        val seller = InventoryRole.fromCloud("vendedor")
        assertEquals(InventoryRole.VENDEDOR, seller)
        assertTrue(InventoryPermissions.canRead(seller))
        assertFalse(InventoryPermissions.canManage(seller))
        assertTrue(InventoryPermissions.canEditQuantity(seller))
    }

    @Test fun warehouseMayManageInventory() {
        assertTrue(InventoryPermissions.canManage(InventoryRole.fromCloud("almacen")))
        assertTrue(InventoryPermissions.canEditQuantity(InventoryRole.ALMACEN))
        assertFalse(InventoryPermissions.canEditQuantity(null))
    }
}
