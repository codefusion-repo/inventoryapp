package cl.aiep.inventoryapp.data.local

import androidx.room.withTransaction
import cl.aiep.inventoryapp.domain.ConflictPolicy
import cl.aiep.inventoryapp.domain.InventoryProduct
import kotlinx.coroutines.flow.map
import java.util.UUID

class LocalInventoryRepository(private val db: InventoryDatabase) {
    private val dao = db.products()
    val activeProducts = dao.observeActive().map { products -> products.map { it.toDomain() } }
    val pendingCount = dao.observePendingCount()

    suspend fun save(
        id: String?, nombre: String, descripcion: String, categoria: String,
        proveedor: String, cantidad: Int, precioClp: Long, userId: String
    ) {
        db.withTransaction {
            val current = id?.let { dao.findById(it) }
            val now = maxOf(System.currentTimeMillis(), (current?.updatedAtMillis ?: 0) + 1)
            val product = InventoryProduct(
                id = id ?: UUID.randomUUID().toString(), nombre = nombre.trim(),
                descripcion = descripcion.trim(), categoria = categoria,
                proveedor = proveedor.trim(), cantidad = cantidad, precioClp = precioClp,
                updatedAtMillis = now, changeId = UUID.randomUUID().toString(),
                updatedBy = userId, deleted = false, pendingSync = true
            )
            dao.upsert(product.toEntity())
        }
    }

    /** Cambia únicamente las existencias; nunca reescribe precio, proveedor u otros atributos. */
    suspend fun updateQuantity(id: String, quantity: Int, userId: String) {
        require(quantity >= 0) { "La cantidad no puede ser negativa." }
        db.withTransaction {
            val current = dao.findById(id) ?: throw IllegalArgumentException("Producto no encontrado")
            require(!current.deleted) { "El producto está dado de baja." }
            if (current.cantidad == quantity) return@withTransaction
            dao.upsert(current.copy(
                cantidad = quantity,
                pendingSync = true,
                updatedAtMillis = maxOf(System.currentTimeMillis(), current.updatedAtMillis + 1),
                changeId = UUID.randomUUID().toString(),
                updatedBy = userId
            ))
        }
    }

    suspend fun softDelete(id: String, userId: String) {
        db.withTransaction {
            val current = dao.findById(id) ?: return@withTransaction
            dao.upsert(current.copy(
                deleted = true, pendingSync = true,
                updatedAtMillis = maxOf(System.currentTimeMillis(), current.updatedAtMillis + 1),
                changeId = UUID.randomUUID().toString(), updatedBy = userId
            ))
        }
    }

    suspend fun pending(): List<InventoryProduct> = dao.getPending().map { it.toDomain() }
    suspend fun acknowledge(product: InventoryProduct) = dao.markSynced(product.id, product.changeId)

    suspend fun mergeRemote(remote: InventoryProduct) {
        db.withTransaction {
            val local = dao.findById(remote.id)?.toDomain()
            if (ConflictPolicy.acceptRemote(local, remote)) {
                dao.upsert(remote.copy(pendingSync = false).toEntity())
            }
        }
    }
}
