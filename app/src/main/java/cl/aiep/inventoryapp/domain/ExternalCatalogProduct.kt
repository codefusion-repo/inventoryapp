package cl.aiep.inventoryapp.domain

/** Modelo de productos externos: el precio se expresa en dólares estadounidenses (USD). */
data class ExternalCatalogProduct(
    val externalId: Int,
    val name: String,
    val description: String,
    val supplier: String,
    val priceUsd: Double,
    val category: ExternalCatalogCategory
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

/** Precarga sin stock ni precio CLP: esos valores deben confirmarse localmente. */
data class ExternalImportDraft(
    val externalId: Int,
    val name: String,
    val description: String,
    val supplier: String,
    val category: String,
    val quantityText: String = "0",
    val priceClpText: String = ""
)

fun ExternalCatalogProduct.toImportDraft() = ExternalImportDraft(
    externalId = externalId,
    name = name,
    description = description,
    supplier = supplier,
    category = category.inventoryCategory
)
