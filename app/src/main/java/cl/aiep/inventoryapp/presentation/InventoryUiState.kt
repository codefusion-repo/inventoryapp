package cl.aiep.inventoryapp.presentation

import cl.aiep.inventoryapp.domain.InventoryProduct

data class InventoryUiState(
    val products: List<InventoryProduct> = emptyList(),
    val search: String = "",
    val category: String = "Todos",
    val pending: Int = 0,
    val userEmail: String? = null,
    val firebaseConfigured: Boolean = false,
    val syncStatus: String = "Modo local (sin conexión a nube)",
    val busy: Boolean = false,
    val message: String? = null
) {
    val visible: List<InventoryProduct> get() = products.filter {
        (category == "Todos" || category == it.categoria) &&
            (search.isBlank() || listOf(it.nombre, it.descripcion, it.proveedor, it.categoria)
                .any { field -> field.contains(search.trim(), ignoreCase = true) })
    }
    val stockUnits: Int get() = products.sumOf { it.cantidad }
    val lowStock: Int get() = products.count { it.cantidad < 5 }
}
