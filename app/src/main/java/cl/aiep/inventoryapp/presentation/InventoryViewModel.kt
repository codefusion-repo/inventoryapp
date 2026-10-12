package cl.aiep.inventoryapp.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.aiep.inventoryapp.InventoryApplication
import cl.aiep.inventoryapp.data.remote.FirestoreInventorySource
import cl.aiep.inventoryapp.data.sync.SyncCoordinator
import cl.aiep.inventoryapp.domain.InventoryProduct
import cl.aiep.inventoryapp.domain.InventoryValidation
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class InventoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as InventoryApplication
    private val local = app.repository
    private val _ui = MutableStateFlow(InventoryUiState(firebaseConfigured = app.firebaseConfigured))
    val ui: StateFlow<InventoryUiState> = _ui
    private var coordinator: SyncCoordinator? = null
    private var statusJob: Job? = null
    private val auth: FirebaseAuth? = if (app.firebaseConfigured) FirebaseAuth.getInstance() else null

    init {
        viewModelScope.launch {
            local.activeProducts.collect { list -> _ui.update { it.copy(products = list) } }
        }
        viewModelScope.launch {
            local.pendingCount.collect { count -> _ui.update { it.copy(pending = count) } }
        }
        auth?.currentUser?.email?.let { email -> activateCloud(email) }
    }

    fun search(value: String) = _ui.update { it.copy(search = value) }
    fun category(value: String) = _ui.update { it.copy(category = value) }
    fun dismissMessage() = _ui.update { it.copy(message = null) }

    fun signIn(email: String, password: String) {
        if (!app.firebaseConfigured) {
            _ui.update { it.copy(message = "Configura Firebase para habilitar el acceso a la nube.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            try {
                val result = requireNotNull(auth).signInWithEmailAndPassword(email.trim(), password).await()
                val signed = result.user?.email ?: throw IllegalStateException("No se obtuvo el usuario")
                activateCloud(signed)
                _ui.update { it.copy(message = "Sesión iniciada correctamente.") }
            } catch (e: Exception) {
                _ui.update { it.copy(message = "No fue posible ingresar: ${e.localizedMessage}") }
            } finally { _ui.update { it.copy(busy = false) } }
        }
    }

    private fun activateCloud(email: String) {
        coordinator?.stop()
        statusJob?.cancel()
        val source = FirestoreInventorySource(FirebaseFirestore.getInstance(), requireNotNull(auth?.currentUser?.uid))
        coordinator = SyncCoordinator(app, local, source, viewModelScope).also { c ->
            c.start()
            statusJob = viewModelScope.launch { c.status.collect { status ->
                _ui.update { it.copy(syncStatus = status) }
            } }
        }
        _ui.update { it.copy(userEmail = email) }
    }

    fun logout() {
        coordinator?.stop(); coordinator = null
        statusJob?.cancel(); statusJob = null
        auth?.signOut()
        _ui.update { it.copy(userEmail = null, syncStatus = "Modo local · sesión cerrada") }
    }

    fun syncNow() = coordinator?.retry() ?: run {
        _ui.update { it.copy(message = "Inicia sesión para sincronizar con Firestore.") }
    }

    fun save(
        id: String?, name: String, description: String, category: String,
        supplier: String, quantityText: String, priceText: String
    ) {
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
                    userId = auth?.currentUser?.uid ?: "offline")
                _ui.update { it.copy(message = "Producto guardado localmente.") }
            } catch (e: Exception) {
                _ui.update { it.copy(message = "No se pudo guardar: ${e.localizedMessage}") }
            }
        }
    }

    fun delete(product: InventoryProduct) {
        viewModelScope.launch {
            try {
                local.softDelete(product.id, auth?.currentUser?.uid ?: "offline")
                _ui.update { it.copy(message = "Producto dado de baja localmente.") }
            } catch (e: Exception) { _ui.update { it.copy(message = "Error al eliminar: ${e.message}") } }
        }
    }

    fun addDemo() {
        if (_ui.value.products.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val uid = auth?.currentUser?.uid ?: "offline"
                local.save(null, "Auriculares inalámbricos", "Bluetooth con estuche", "Audio", "AudioSur", 15, 18990, uid)
                local.save(null, "Teclado mecánico", "Teclas retroiluminadas", "Accesorios", "TecnoChile", 3, 44990, uid)
                local.save(null, "Notebook 14 pulgadas", "Equipo de demostración", "Computadores", "Distribuidora Norte", 7, 529990, uid)
            } catch (e: Exception) { _ui.update { it.copy(message = "No se pudieron crear los datos demo: ${e.message}") } }
        }
    }

    override fun onCleared() {
        coordinator?.stop()
        statusJob?.cancel()
        super.onCleared()
    }
}
