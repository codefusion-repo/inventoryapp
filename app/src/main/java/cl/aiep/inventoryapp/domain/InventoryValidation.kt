package cl.aiep.inventoryapp.domain

object InventoryValidation {
    fun validate(nombre: String, proveedor: String, cantidad: Int, precioClp: Long): String? = when {
        nombre.trim().length < 2 -> "Ingresa un nombre de al menos 2 caracteres."
        proveedor.trim().isEmpty() -> "El proveedor es obligatorio."
        cantidad < 0 -> "La cantidad no puede ser negativa."
        precioClp < 0 -> "El precio no puede ser negativo."
        else -> null
    }
}
