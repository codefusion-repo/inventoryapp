package cl.aiep.inventoryapp.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.aiep.inventoryapp.InventoryApplication
import cl.aiep.inventoryapp.data.external.ExternalCatalogRepository
import cl.aiep.inventoryapp.data.external.ExternalCatalogErrorMessages
import cl.aiep.inventoryapp.data.remote.FirestoreInventorySource
import cl.aiep.inventoryapp.data.sync.SyncCoordinator
import cl.aiep.inventoryapp.domain.ExternalCatalogCategory
import cl.aiep.inventoryapp.domain.InventoryPermissions
import cl.aiep.inventoryapp.domain.InventoryProduct
import cl.aiep.inventoryapp.domain.InventoryRole
import cl.aiep.inventoryapp.domain.InventoryValidation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as InventoryApplication
    private val local = app.repository
    private val externalCatalog = ExternalCatalogRepository()
    private var externalJob: Job? = null
    private val _ui = MutableStateFlow(
        InventoryUiState(
            firebaseConfigured = app.firebaseConfigured,
            firebaseDiagnostic = app.firebaseDiagnostic,
            syncStatus = if (app.firebaseConfigured) "Preparando catálogo…" else "Puedes consultar los productos guardados en este dispositivo."
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

    fun navigate(to: InventoryDestination) {
        val current = _ui.value
        val allowed = when {
            current.isGuest -> InventoryDestination.PRODUCTOS
            to == InventoryDestination.EXTERNOS && !current.canManage -> InventoryDestination.PRODUCTOS
            else -> to
        }
        _ui.update { it.copy(destination = allowed) }
        if (allowed == InventoryDestination.EXTERNOS && !current.externalLoaded && !current.externalLoading) {
            loadExternalProducts()
        }
    }

    fun selectExternalCategory(category: ExternalCatalogCategory) {
        if (!_ui.value.canManage) return
        _ui.update { it.copy(externalCategory = category) }
        loadExternalProducts()
    }

    fun loadExternalProducts() {
        if (!_ui.value.canManage) return
        externalJob?.cancel()
        val category = _ui.value.externalCategory
        externalJob = viewModelScope.launch {
            _ui.update {
                it.copy(externalLoading = true, externalLoaded = false,
                    externalError = null, externalProducts = emptyList())
            }
            try {
                val products = externalCatalog.getProducts(category)
                _ui.update {
                    it.copy(externalProducts = products, externalLoading = false,
                        externalLoaded = true, externalError = null)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _ui.update {
                    it.copy(externalProducts = emptyList(), externalLoading = false,
                        externalLoaded = false, externalError = ExternalCatalogErrorMessages.explain(e))
                }
            }
        }
    }
    fun search(value: String) = _ui.update { it.copy(search = value) }
    fun category(value: String) = _ui.update { it.copy(category = value) }
    fun dismissMessage() = _ui.update { it.copy(message = null) }

    // El visitante recibe un token anónimo para consultar la misma colección,
    // pero no puede escribir ni crear un perfil de empleado.
    private fun startGuestSession() {
        externalJob?.cancel()
        _ui.update { it.copy(externalProducts = emptyList(), externalLoaded = false,
            externalLoading = false, externalError = null) }
        val firebase = auth ?: run {
            _ui.update { it.copy(cloudSessionActive = false, syncStatus = "Puedes consultar los productos guardados en este dispositivo.") }
            return
        }
        guestJob?.cancel()
        val generation = ++sessionGeneration
        guestJob = viewModelScope.launch {
            _ui.update {
                it.copy(userEmail = null, role = null, cloudSessionActive = false,
                    destination = InventoryDestination.PRODUCTOS,
                    syncStatus = "Preparando el catálogo…")
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
                if (e is CancellationException) throw e
                if (generation != sessionGeneration) return@launch
                _ui.update { it.copy(
                    cloudSessionActive = false,
                    syncStatus = UserMessages.guest(e)
                ) }
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (!app.firebaseConfigured) {
            _ui.update { it.copy(authError = "El acceso para empleados aún no está disponible. Contacta al administrador.") }
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
                val role = _ui.value.role
                if (role != null) _ui.update {
                    it.copy(message = if (role == InventoryRole.ALMACEN)
                        "¡Bienvenido! Ya puedes administrar el inventario."
                        else "¡Bienvenido! Ya puedes actualizar las existencias.")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _ui.update { it.copy(authError = it.authError ?: UserMessages.login(e)) }
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
                if (e is CancellationException) throw e
                _ui.update { it.copy(authError = UserMessages.profile(e), syncStatus = "No pudimos validar tu acceso") }
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
                syncStatus = "Comprobando tu acceso…")
        }
        val profile = try {
            FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _ui.update { it.copy(authError = UserMessages.profile(e)) }
            throw e
        }
        val role = InventoryRole.fromCloud(profile.getString("role"))
        if (role == null) {
            auth?.signOut()
            _ui.update { it.copy(
                authError = "Tu cuenta todavía no tiene acceso al inventario. Solicita al administrador que habilite tu perfil.",
                syncStatus = "Acceso pendiente de habilitación", role = null, userEmail = null
            ) }
            startGuestSession()
            return
        }
        if (role != cl.aiep.inventoryapp.domain.InventoryRole.ALMACEN) externalJob?.cancel()
        _ui.update { it.copy(userEmail = email, role = role, authError = null,
            destination = InventoryDestination.PRODUCTOS,
            externalProducts = if (role == cl.aiep.inventoryapp.domain.InventoryRole.ALMACEN) it.externalProducts else emptyList(),
            externalLoaded = role == cl.aiep.inventoryapp.domain.InventoryRole.ALMACEN && it.externalLoaded,
            externalLoading = false,
            syncStatus = "Actualizando tus productos…") }
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
        externalJob?.cancel()
        guestJob?.cancel(); guestJob = null
        sessionGeneration++
        roleJob?.cancel(); roleJob = null
        stopCoordinator()
        auth?.signOut()
        _ui.update { it.copy(userEmail = null, role = null, cloudSessionActive = false,
            authError = null, destination = InventoryDestination.PRODUCTOS,
            message = "Sesión cerrada. Puedes seguir explorando el catálogo.") }
        startGuestSession()
    }

    fun syncNow() {
        if (coordinator == null && _ui.value.isGuest) startGuestSession()
        else coordinator?.retry() ?: _ui.update {
            it.copy(message = "Conectando al catálogo. Intenta actualizar de nuevo en un momento.")
        }
    }

    private fun verifyWriteAccess(): Boolean {
        if (_ui.value.canManage) return true
        _ui.update { it.copy(message = "Para modificar este producto, ingresa con una cuenta de almacén.") }
        return false
    }

    fun updateQuantity(product: InventoryProduct, quantityText: String) {
        if (!InventoryPermissions.canEditQuantity(_ui.value.role) || _ui.value.userEmail == null) {
            _ui.update { it.copy(message = "Inicia sesión como vendedor o almacén para cambiar las existencias.") }
            return
        }
        val quantity = quantityText.toIntOrNull()
        if (quantity == null || quantity < 0) {
            _ui.update { it.copy(message = "Ingresa una cantidad válida, sin decimales ni valores negativos.") }
            return
        }
        viewModelScope.launch {
            try {
                val uid = auth?.currentUser?.uid ?: error("Sesión no disponible")
                val changed = local.updateQuantity(product.id, quantity, uid)
                _ui.update { it.copy(message = if (changed)
                    "Listo, guardamos las existencias de «${product.nombre}»."
                    else "La cantidad de «${product.nombre}» ya estaba actualizada.") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _ui.update { it.copy(message = UserMessages.localSave(e)) }
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
            _ui.update { it.copy(message = "Revisa la cantidad y el precio: deben ser números enteros.") }; return
        }
        val error = InventoryValidation.validate(name, supplier, qty, price)
        if (error != null) { _ui.update { it.copy(message = error) }; return }
        viewModelScope.launch {
            try {
                local.save(id, name, description, category, supplier, qty, price,
                    userId = auth?.currentUser?.uid ?: throw IllegalStateException("Sesión no disponible"))
                _ui.update { it.copy(message = if (id == null)
                    "¡Producto agregado! «${name.trim()}» ya está guardado en este dispositivo."
                    else "Cambios guardados para «${name.trim()}». Se compartirán al sincronizar.") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _ui.update { it.copy(message = UserMessages.localSave(e)) }
            }
        }
    }

    fun delete(product: InventoryProduct) {
        if (!verifyWriteAccess()) return
        viewModelScope.launch {
            try {
                val deleted = local.softDelete(product.id,
                    auth?.currentUser?.uid ?: throw IllegalStateException("Sesión no disponible"))
                _ui.update { it.copy(message = if (deleted)
                    "«${product.nombre}» ya no aparece en el catálogo. Se reflejará en los demás dispositivos al sincronizar."
                    else "Este producto ya estaba dado de baja.") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _ui.update { it.copy(message = UserMessages.localSave(e)) }
            }
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
                _ui.update { it.copy(message = "Agregamos tres productos para comenzar.") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _ui.update { it.copy(message = UserMessages.localSave(e)) }
            }
        }
    }

    override fun onCleared() {
        externalJob?.cancel()
        guestJob?.cancel()
        sessionGeneration++
        roleJob?.cancel()
        coordinator?.stop()
        statusJob?.cancel()
        super.onCleared()
    }
}
