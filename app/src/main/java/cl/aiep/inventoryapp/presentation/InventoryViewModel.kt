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
            syncStatus = if (app.firebaseConfigured) "Preparando catálogo…" else "Catálogo local · Firebase sin configurar"
        )
    )
    val ui: StateFlow<InventoryUiState> = _ui
    private var coordinator: SyncCoordinator? = null
    private var statusJob: Job? = null
    private var roleJob: Job? = null
    private var guestJob: Job? = null
    private var sessionGeneration: Int = 0
    private val auth: FirebaseAuth? = if (app.firebaseConfigured) FirebaseAuth.getInstance() else null

    init {
        viewModelScope.launch {
            local.activeProducts.collect { list -> _ui.update { it.copy(products = list) } }
        }
        viewModelScope.launch {
            local.pendingCount.collect { count -> _ui.update { it.copy(pending = count) } }
        }
        val current = auth?.currentUser
        when {
            current == null || current.isAnonymous -> startGuestSession()
            !current.email.isNullOrBlank() -> activateCloud(current.uid, requireNotNull(current.email))
            else -> {
                auth?.signOut()
                startGuestSession()
            }
        }
    }

    fun navigate(to: InventoryDestination) = _ui.update {
        it.copy(destination = if (it.userEmail == null) InventoryDestination.PRODUCTOS else to)
    }
    fun search(value: String) = _ui.update { it.copy(search = value) }
    fun category(value: String) = _ui.update { it.copy(category = value) }
    fun dismissMessage() = _ui.update { it.copy(message = null) }

    // El visitante recibe un token anónimo para consultar la misma colección,
    // pero no puede escribir ni crear un perfil de empleado.
    private fun startGuestSession() {
        val firebase = auth ?: run {
            _ui.update { it.copy(cloudSessionActive = false, syncStatus = "Catálogo local · Firebase sin configurar") }
            return
        }
        guestJob?.cancel()
        val generation = ++sessionGeneration
        guestJob = viewModelScope.launch {
            _ui.update {
                it.copy(userEmail = null, role = null, cloudSessionActive = false,
                    destination = InventoryDestination.PRODUCTOS,
                    syncStatus = "Conectando al catálogo…")
            }
            try {
                val guest = firebase.currentUser?.takeIf { it.isAnonymous }
                    ?: firebase.signInAnonymously().await().user
                    ?: error("Firebase no devolvió una sesión de invitado")
                check(guest.isAnonymous) { "Se esperaba una sesión anónima" }
                if (generation != sessionGeneration) return@launch
                activateCoordinator(uid = guest.uid, role = null, canUpload = false)
                _ui.update { it.copy(cloudSessionActive = true) }
            } catch (e: Exception) {
                _ui.update { it.copy(
                    cloudSessionActive = false,
                    syncStatus = "Solo datos guardados · ${e.localizedMessage ?: "no se pudo entrar como invitado"}"
                ) }
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (!app.firebaseConfigured) {
            _ui.update { it.copy(authError = app.firebaseDiagnostic) }
            return
        }
        guestJob?.cancel()
        sessionGeneration++
        stopCoordinator()
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, authError = null, cloudSessionActive = false) }
            try {
                val user = requireNotNull(auth).signInWithEmailAndPassword(email.trim(), password).await().user
                    ?: error("Firebase no devolvió un usuario")
                loadRoleAndStart(user.uid, user.email ?: email.trim())
            } catch (e: Exception) {
                val reason = e.localizedMessage ?: "Error de autenticación"
                _ui.update { it.copy(authError = "No se pudo iniciar sesión: $reason", syncStatus = "Error de autenticación") }
                requireNotNull(auth).signOut()
                startGuestSession()
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private fun activateCloud(uid: String, email: String) {
        guestJob?.cancel()
        sessionGeneration++
        roleJob?.cancel()
        roleJob = viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try { loadRoleAndStart(uid, email) }
            catch (e: Exception) {
                _ui.update { it.copy(authError = "No se pudo cargar el perfil: ${e.localizedMessage}", syncStatus = "Perfil sin verificar") }
                auth?.signOut()
                startGuestSession()
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun loadRoleAndStart(uid: String, email: String) {
        stopCoordinator()
        _ui.update {
            it.copy(role = null, userEmail = null, cloudSessionActive = false,
                syncStatus = "Validando perfil en Firestore…")
        }
        val profile = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
        val role = InventoryRole.fromCloud(profile.getString("role"))
        if (role == null) {
            auth?.signOut()
            _ui.update { it.copy(
                authError = "Tu cuenta no tiene un rol asignado (vendedor o almacén).",
                syncStatus = "Falta perfil de usuario", role = null, userEmail = null
            ) }
            startGuestSession()
            return
        }
        _ui.update { it.copy(userEmail = email, role = role, authError = null,
            syncStatus = "Conectando con Firestore…") }
        activateCoordinator(uid, role, InventoryPermissions.canEditQuantity(role))
        _ui.update { it.copy(cloudSessionActive = true) }
    }

    private fun activateCoordinator(uid: String, role: InventoryRole?, canUpload: Boolean) {
        stopCoordinator()
        coordinator = SyncCoordinator(
            context = app,
            local = local,
            remote = FirestoreInventorySource(FirebaseFirestore.getInstance(), uid, role),
            scope = viewModelScope,
            canUpload = canUpload,
            currentUid = uid
        ).also { c ->
            c.start()
            statusJob = viewModelScope.launch {
                c.status.collect { status -> _ui.update { it.copy(syncStatus = status) } }
            }
        }
    }

    private fun stopCoordinator() {
        coordinator?.stop(); coordinator = null
        statusJob?.cancel(); statusJob = null
    }

    fun logout() {
        guestJob?.cancel(); guestJob = null
        sessionGeneration++
        roleJob?.cancel(); roleJob = null
        stopCoordinator()
        auth?.signOut()
        _ui.update { it.copy(userEmail = null, role = null, cloudSessionActive = false,
            authError = null, destination = InventoryDestination.PRODUCTOS) }
        startGuestSession()
    }

    fun syncNow() {
        if (coordinator == null && _ui.value.isGuest) startGuestSession()
        else coordinator?.retry() ?: _ui.update {
            it.copy(message = "Inicia sesión y valida tu perfil para sincronizar.")
        }
    }

    private fun verifyWriteAccess(): Boolean {
        if (_ui.value.canManage) return true
        _ui.update { it.copy(message = "Acceso restringido: solo almacén puede modificar el inventario.") }
        return false
    }

    fun updateQuantity(product: InventoryProduct, quantityText: String) {
        if (!InventoryPermissions.canEditQuantity(_ui.value.role) || _ui.value.userEmail == null) {
            _ui.update { it.copy(message = "Debes iniciar sesión como vendedor o almacén para modificar existencias.") }
            return
        }
        val quantity = quantityText.toIntOrNull()
        if (quantity == null || quantity < 0) {
            _ui.update { it.copy(message = "La cantidad debe ser un entero igual o mayor que cero.") }
            return
        }
        viewModelScope.launch {
            try {
                val uid = auth?.currentUser?.uid ?: error("Sesión no disponible")
                local.updateQuantity(product.id, quantity, uid)
                _ui.update { it.copy(message = "Existencias actualizadas. Sincronización pendiente si no hay red.") }
            } catch (e: Exception) {
                _ui.update { it.copy(message = "No se pudieron modificar las existencias: ${e.localizedMessage}") }
            }
        }
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
        guestJob?.cancel()
        sessionGeneration++
        roleJob?.cancel()
        coordinator?.stop()
        statusJob?.cancel()
        super.onCleared()
    }
}
