package cl.aiep.inventoryapp.domain

/** El rol llega desde /users/{uid} y se valida nuevamente en reglas de Firestore. */
enum class InventoryRole(val cloudValue: String, val label: String) {
    VENDEDOR("vendedor", "Vendedor"),
    ALMACEN("almacen", "Encargado de almacén");

    companion object {
        fun fromCloud(value: String?): InventoryRole? = entries.firstOrNull { it.cloudValue == value }
    }
}

object InventoryPermissions {
    fun canRead(role: InventoryRole?): Boolean = role != null
    fun canEditQuantity(role: InventoryRole?): Boolean = role == InventoryRole.VENDEDOR || role == InventoryRole.ALMACEN
    fun canManage(role: InventoryRole?): Boolean = role == InventoryRole.ALMACEN
}
