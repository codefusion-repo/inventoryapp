package cl.aiep.inventoryapp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import cl.aiep.inventoryapp.domain.InventoryProduct

@Entity(tableName = "inventory_products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val nombre: String,
    val descripcion: String,
    val categoria: String,
    val proveedor: String,
    val cantidad: Int,
    val precioClp: Long,
    val updatedAtMillis: Long,
    val changeId: String,
    val updatedBy: String,
    val deleted: Boolean,
    val pendingSync: Boolean
) {
    fun toDomain() = InventoryProduct(id, nombre, descripcion, categoria, proveedor, cantidad,
        precioClp, updatedAtMillis, changeId, updatedBy, deleted, pendingSync)
}
fun InventoryProduct.toEntity() = ProductEntity(id, nombre, descripcion, categoria, proveedor,
    cantidad, precioClp, updatedAtMillis, changeId, updatedBy, deleted, pendingSync)
