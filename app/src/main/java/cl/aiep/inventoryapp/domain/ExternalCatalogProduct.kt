package cl.aiep.inventoryapp.domain

/** Datos externos: solo el precio convertido a CLP se presenta a usuarios. */
data class ExternalCatalogProduct(
    val externalId: Int,
    val name: String,
    val description: String,
    val supplier: String,
    val priceUsd: Double,
    val category: ExternalCatalogCategory,
    val priceClp: Long? = null,
    val thumbnailUrl: String? = null,
    val rating: Double? = null
)

enum class ExternalCatalogCategory(
    val slug: String,
    val label: String,
    val inventoryCategory: String
) {
    LAPTOPS("laptops", "Portátiles", "Computadores"),
    SMARTPHONES("smartphones", "Teléfonos", "Otros"),
    TABLETS("tablets", "Tablets", "Computadores"),
    ACCESSORIES("mobile-accessories", "Accesorios", "Accesorios")
}

/** La cantidad requiere confirmación explícita; el precio CLP se precarga si existe cotización. */
data class ExternalImportDraft(
    val externalId: Int,
    val name: String,
    val description: String,
    val supplier: String,
    val category: String,
    val quantityText: String = "",
    val priceClpText: String = ""
)

fun ExternalCatalogProduct.toImportDraft() = ExternalImportDraft(
    externalId = externalId,
    name = name,
    description = description,
    supplier = supplier,
    category = category.inventoryCategory,
    priceClpText = priceClp?.toString().orEmpty()
)
