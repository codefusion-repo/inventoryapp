package cl.aiep.inventoryapp.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import cl.aiep.inventoryapp.data.local.LocalInventoryRepository
import cl.aiep.inventoryapp.data.remote.FirestoreInventorySource
import cl.aiep.inventoryapp.presentation.UserMessages
import kotlin.coroutines.cancellation.CancellationException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncCoordinator(
    private val context: Context,
    private val local: LocalInventoryRepository,
    private val remote: FirestoreInventorySource,
    private val scope: CoroutineScope,
    private val canUpload: Boolean,
    private val currentUid: String
) {
    val status = MutableStateFlow("Sincronización detenida")
    private var subscription: ListenerRegistration? = null
    private var work: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)
    private val mutex = Mutex()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start() {
        if (work != null) return
        status.value = "Actualizando el catálogo…"
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { syncRequests.trySend(Unit) }
        }
        networkCallback = callback
        cm.registerNetworkCallback(NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)

        listenForUpdates()
        work = scope.launch {
            launch {
                local.pendingCount.debounce(400).collect { if (canUpload && it > 0) syncRequests.trySend(Unit) }
            }
            for (request in syncRequests) flush()
        }
        syncRequests.trySend(Unit)
    }

    private fun listenForUpdates() {
        subscription?.remove()
        subscription = remote.listen(
            onProducts = { list, fromCache ->
                scope.launch {
                    try {
                        list.forEach { local.mergeRemote(it) }
                        status.value = when {
                            fromCache -> "Mostrando productos guardados. Actualizaremos al conectar."
                            canUpload -> "Inventario actualizado."
                            else -> "Catálogo actualizado."
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        status.value = "No pudimos actualizar los productos guardados. Inténtalo de nuevo."
                    }
                }
            },
            onError = { error ->
                status.value = UserMessages.inventory(error)
                subscription?.remove()
                subscription = null
            }
        )
    }

    fun retry() {
        if (work == null) return
        if (subscription == null) listenForUpdates()
        syncRequests.trySend(Unit)
    }

    private suspend fun flush() = mutex.withLock {
        if (!canUpload) return@withLock
        // No subir a la nube cambios pendientes creados bajo otra sesión del mismo dispositivo.
        val list = local.pending().filter { it.updatedBy == currentUid }
        if (list.isEmpty()) return@withLock
        status.value = "Guardando cambios en la nube…"
        for (product in list) {
            try {
                remote.upload(product)
                local.acknowledge(product)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                status.value = UserMessages.upload(e)
                return@withLock
            }
        }
        status.value = "Cambios compartidos con el equipo."
    }

    fun stop() {
        subscription?.remove(); subscription = null
        work?.cancel(); work = null
        networkCallback?.let {
            (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .unregisterNetworkCallback(it)
        }
        networkCallback = null
        status.value = "Catálogo en modo local"
    }
}
