package cl.aiep.inventoryapp.presentation

import cl.aiep.inventoryapp.domain.ExternalCatalogCategory
import cl.aiep.inventoryapp.domain.ExternalCatalogProduct
import cl.aiep.inventoryapp.domain.InventoryPermissions
import cl.aiep.inventoryapp.domain.InventoryProduct
import cl.aiep.inventoryapp.domain.InventoryRole

enum class InventoryDestination { RESUMEN, PRODUCTOS, EXTERNOS, CUENTA }

data class InventoryUiState(
    val products: List<InventoryProduct> = emptyList(),
    val search: String = "",
    val category: String = "Todos",
    val destination: InventoryDestination = InventoryDestination.PRODUCTOS,
    val pending: Int = 0,
    val userEmail: String? = null,
    val role: InventoryRole? = null,
    val cloudSessionActive: Boolean = false,
    val firebaseConfigured: Boolean = false,
    val firebaseDiagnostic: String = "",
    val syncStatus: String = "Modo local",
    val busy: Boolean = false,
    val authError: String? = null,
    val message: String? = null,
    val externalCategory: ExternalCatalogCategory = ExternalCatalogCategory.LAPTOPS,
    val externalProducts: List<ExternalCatalogProduct> = emptyList(),
    val externalLoading: Boolean = false,
    val externalLoaded: Boolean = false,
    val externalError: String? = null
) {
    val isGuest: Boolean get() = userEmail == null
    val canEditQuantity: Boolean get() = userEmail != null && InventoryPermissions.canEditQuantity(role)
    val canManage: Boolean get() = userEmail != null && InventoryPermissions.canManage(role)
    val canUseStockShortcut: Boolean get() = userEmail != null && role == InventoryRole.VENDEDOR
    val canReadCloud: Boolean get() = cloudSessionActive
    val visible: List<InventoryProduct> get() = products.filter {
        (category == "Todos" || category == it.categoria) &&
            (search.isBlank() || listOf(it.nombre, it.descripcion, it.proveedor, it.categoria)
                .any { field -> field.contains(search.trim(), ignoreCase = true) })
    }
    val stockUnits: Int get() = products.sumOf { it.cantidad }
    val lowStock: Int get() = products.count { it.cantidad < 5 }
}
