package cl.aiep.inventoryapp.data.remote

import cl.aiep.inventoryapp.domain.InventoryProduct
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.tasks.await

class FirestoreInventorySource(private val firestore: FirebaseFirestore, private val signedInUid: String) {
    private val collection = firestore.collection("inventory_products")

    suspend fun upload(product: InventoryProduct) {
        val payload: Map<String, Any> = mapOf(
            "id" to product.id,
            "nombre" to product.nombre,
            "descripcion" to product.descripcion,
            "categoria" to product.categoria,
            "proveedor" to product.proveedor,
            "cantidad" to product.cantidad,
            "precioClp" to product.precioClp,
            "updatedAtMillis" to product.updatedAtMillis,
            "changeId" to product.changeId,
            "updatedBy" to signedInUid,
            "deleted" to product.deleted,
            "serverUpdatedAt" to FieldValue.serverTimestamp()
        )
        collection.document(product.id).set(payload).await()
    }

    fun listen(
        onProducts: (List<InventoryProduct>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = collection.addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
        if (error != null) {
            onError(error)
            return@addSnapshotListener
        }
        // Ignorar datos locales de Firestore que aún no fueron confirmados por servidor.
        val ready = snap?.documents?.filter { !it.metadata.hasPendingWrites() } ?: emptyList()
        val mapped = ready.mapNotNull { it.toProduct() }
        onProducts(mapped)
    }

    private fun DocumentSnapshot.toProduct(): InventoryProduct? {
        val id = getString("id") ?: return null
        val nombre = getString("nombre") ?: return null
        val proveedor = getString("proveedor") ?: return null
        val cantidad = getLong("cantidad")?.toInt() ?: return null
        val price = getLong("precioClp") ?: return null
        val time = getLong("updatedAtMillis") ?: return null
        val changeId = getString("changeId") ?: return null
        return InventoryProduct(
            id = id, nombre = nombre, descripcion = getString("descripcion") ?: "",
            categoria = getString("categoria") ?: "Otros", proveedor = proveedor,
            cantidad = cantidad, precioClp = price, updatedAtMillis = time,
            changeId = changeId, updatedBy = getString("updatedBy") ?: "remoto",
            deleted = getBoolean("deleted") == true, pendingSync = false
        )
    }
}
