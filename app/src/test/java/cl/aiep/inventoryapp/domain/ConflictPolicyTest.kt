package cl.aiep.inventoryapp.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictPolicyTest {
    private fun product(time: Long, pending: Boolean = false, change: String = "a") = InventoryProduct(
        "1", "Mouse", "", "Accesorios", "Proveedor", 1, 4990,
        time, change, "tester", false, pending
    )
    @Test fun pendingLocalEditCannotBeOverwritten() {
        assertFalse(ConflictPolicy.acceptRemote(product(10L, true), product(20L)))
    }
    @Test fun moreRecentRemoteEditWins() {
        assertTrue(ConflictPolicy.acceptRemote(product(10L), product(20L)))
    }
    @Test fun olderRemoteEditDoesNotWin() {
        assertFalse(ConflictPolicy.acceptRemote(product(20L), product(10L)))
    }
}
