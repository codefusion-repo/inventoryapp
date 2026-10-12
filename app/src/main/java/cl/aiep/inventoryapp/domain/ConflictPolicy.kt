package cl.aiep.inventoryapp.domain

/** Política demostrativa: gana la edición más reciente según reloj del dispositivo.
 * Una operación local pendiente NUNCA se reemplaza por un snapshot remoto.
 * No ofrece serialización fuerte ni resuelve desajustes de reloj entre equipos.
 */
object ConflictPolicy {
    fun acceptRemote(local: InventoryProduct?, remote: InventoryProduct): Boolean {
        if (local == null) return true
        if (local.pendingSync) return false
        return remote.updatedAtMillis > local.updatedAtMillis ||
            (remote.updatedAtMillis == local.updatedAtMillis && remote.changeId > local.changeId)
    }
}
