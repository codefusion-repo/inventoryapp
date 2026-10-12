package cl.aiep.inventoryapp.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.aiep.inventoryapp.InventoryApplication
import cl.aiep.inventoryapp.data.remote.FirestoreInventorySource
import cl.aiep.inventoryapp.data.sync.SyncCoordinator
import cl.aiep.inventoryapp.domain.InventoryPermissions
import cl.aiep.inventoryapp.domain.InventoryProduct
import cl.aiep.inventoryapp.domain.InventoryRole
import cl.aiep.inventoryapp.domain.InventoryValidation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as InventoryApplication
    private val local = app.repository
    private val _ui = MutableStateFlow(
        InventoryUiState(
            firebaseConfigured = app.firebaseConfigured,
            firebaseDiagnostic = app.firebaseDiagnostic,
            syncStatus = if (app.firebaseConfigured) "Listo para iniciar sesión" else "Firebase sin configurar"
        )
    )
    val ui: StateFlow<InventoryUiState> = _ui
    private var coordinator: SyncCoordinator? = null
    private var statusJob: Job? = null
    private var roleJob: Job? = null
    private val auth: FirebaseAuth? = if (app.firebaseConfigured) FirebaseAuth.getInstance() else null

    init {
        viewModelScope.launch {
            local.activeProducts.collect { list -> _ui.update { it.copy(products = list) } }
        }
        viewModelScope.launch {
            local.pendingCount.collect { count -> _ui.update { it.copy(pending = count) } }
        }
        auth?.currentUser?.let { user ->
            val email = user.email
            if (!email.isNullOrBlank()) activateCloud(user.uid, email)
        }
    }

    fun navigate(to: InventoryDestination) = _ui.update { it.copy(destination = to) }
    fun search(value: String) = _ui.update { it.copy(search = value) }
    fun category(value: String) = _ui.update { it.copy(category = value) }
    fun dismissMessage() = _ui.update { it.copy(message = null) }

    fun signIn(email: String, password: String) {
        if (!app.firebaseConfigured) {
            _ui.update { it.copy(authError = app.firebaseDiagnostic) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, authError = null) }
            try {
                val user = requireNotNull(auth).signInWithEmailAndPassword(email.trim(), password).await().user
                    ?: error("Firebase no devolvió un usuario")
                val normalizedEmail = user.email ?: email.trim()
                loadRoleAndStart(user.uid, normalizedEmail)
            } catch (e: Exception) {
                val reason = e.localizedMessage ?: "Error de autenticación"
                _ui.update { it.copy(authError = "No se pudo iniciar sesión: $reason", syncStatus = "Error de autenticación") }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private fun activateCloud(uid: String, email: String) {
        roleJob?.cancel()
        roleJob = viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try { loadRoleAndStart(uid, email) }
            catch (e: Exception) {
                _ui.update { it.copy(authError = "No se pudo cargar el perfil: ${e.localizedMessage}", syncStatus = "Perfil sin verificar") }
            } finally { _ui.update { it.copy(busy = false) } }
        }
    }

    private suspend fun loadRoleAndStart(uid: String, email: String) {
        coordinator?.stop()
        coordinator = null
        statusJob?.cancel()
        statusJob = null
        _ui.update { it.copy(role = null, userEmail = null, syncStatus = "Validando perfil en Firestore…") }

        // El rol se obtiene de Firestore; nunca es enviado por la pantalla de acceso.
        // Firestore admite un perfil en caché cuando el dispositivo está offline.
        val profile = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
        val role = InventoryRole.fromCloud(profile.getString("role"))
        if (role == null) {
            requireNotNull(auth).signOut()
            _ui.update {
                it.copy(authError = "El usuario no tiene un rol válido. Crea users/$uid con role 'vendedor' o 'almacen' en Firestore.",
                    syncStatus = "Falta perfil de usuario", role = null, userEmail = null)
            }
            return
        }
        _ui.update {
            it.copy(userEmail = email, role = role, authError = null, syncStatus = "Conectando con Firestore…")
        }
        coordinator = SyncCoordinator(
            context = app,
            local = local,
            remote = FirestoreInventorySource(FirebaseFirestore.getInstance(), uid),
            scope = viewModelScope,
            canUpload = InventoryPermissions.canManage(role)
        ).also { c ->
            c.start()
            statusJob = viewModelScope.launch {
                c.status.collect { status -> _ui.update { it.copy(syncStatus = status) } }
            }
        }
    }

    fun logout() {
        roleJob?.cancel(); roleJob = null
        coordinator?.stop(); coordinator = null
        statusJob?.cancel(); statusJob = null
        auth?.signOut()
        _ui.update {
            it.copy(userEmail = null, role = null, authError = null,
                syncStatus = "Modo local · sesión cerrada", destination = InventoryDestination.PRODUCTOS)
        }
    }

    fun syncNow() {
        coordinator?.retry() ?: _ui.update {
            it.copy(message = "Inicia sesión y valida tu perfil para sincronizar.")
        }
    }

    private fun verifyWriteAccess(): Boolean {
        if (_ui.value.canManage) return true
        _ui.update { it.copy(message = "Acceso restringido: solo almacén puede modificar el inventario.") }
        return false
    }

    fun save(
        id: String?, name: String, description: String, category: String,
        supplier: String, quantityText: String, priceText: String
    ) {
        if (!verifyWriteAccess()) return
        val qty = quantityText.toIntOrNull()
        val price = priceText.toLongOrNull()
        if (qty == null || price == null) {
            _ui.update { it.copy(message = "Cantidad y precio deben ser números enteros.") }; return
        }
        val error = InventoryValidation.validate(name, supplier, qty, price)
        if (error != null) { _ui.update { it.copy(message = error) }; return }
        viewModelScope.launch {
            try {
                local.save(id, name, description, category, supplier, qty, price,
                    userId = auth?.currentUser?.uid ?: throw IllegalStateException("Sesión no disponible"))
                _ui.update { it.copy(message = "Producto guardado. Se sincronizará al tener conexión.") }
            } catch (e: Exception) {
                _ui.update { it.copy(message = "No se pudo guardar: ${e.localizedMessage}") }
            }
        }
    }

    fun delete(product: InventoryProduct) {
        if (!verifyWriteAccess()) return
        viewModelScope.launch {
            try {
                local.softDelete(product.id, auth?.currentUser?.uid ?: throw IllegalStateException("Sesión no disponible"))
                _ui.update { it.copy(message = "Producto dado de baja. Cambio pendiente de sincronización.") }
            } catch (e: Exception) { _ui.update { it.copy(message = "Error al eliminar: ${e.message}") } }
        }
    }

    fun addDemo() {
        if (!verifyWriteAccess() || _ui.value.products.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val uid = auth?.currentUser?.uid ?: throw IllegalStateException("Sesión no disponible")
                local.save(null, "Auriculares inalámbricos", "Bluetooth con estuche", "Audio", "AudioSur", 15, 18990, uid)
                local.save(null, "Teclado mecánico", "Teclas retroiluminadas", "Accesorios", "TecnoChile", 3, 44990, uid)
                local.save(null, "Notebook 14 pulgadas", "Equipo de demostración", "Computadores", "Distribuidora Norte", 7, 529990, uid)
            } catch (e: Exception) { _ui.update { it.copy(message = "No se pudieron crear productos: ${e.message}") } }
        }
    }

    override fun onCleared() {
        roleJob?.cancel()
        coordinator?.stop()
        statusJob?.cancel()
        super.onCleared()
    }
}
