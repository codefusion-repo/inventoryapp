package cl.aiep.inventoryapp.domain

data class InventoryProduct(
    val id: String,
    val nombre: String,
    val descripcion: String,
    val categoria: String,
    val proveedor: String,
    val cantidad: Int,
    val precioClp: Long,
    val updatedAtMillis: Long,
    val changeId: String,
    val updatedBy: String,
    val deleted: Boolean = false,
    val pendingSync: Boolean = false
)
