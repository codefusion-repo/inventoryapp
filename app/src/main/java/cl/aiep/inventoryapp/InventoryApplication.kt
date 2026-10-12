package cl.aiep.inventoryapp

import android.app.Application
import androidx.room.Room
import cl.aiep.inventoryapp.data.local.InventoryDatabase
import cl.aiep.inventoryapp.data.local.LocalInventoryRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class InventoryApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, InventoryDatabase::class.java, "inventoryapp.db").build()
    }
    val repository by lazy { LocalInventoryRepository(database) }

    var firebaseConfigured: Boolean = false
        private set
    var firebaseDiagnostic: String = ""
        private set

    override fun onCreate() {
        super.onCreate()
        // Recursos generados por el plugin google-services a partir de google-services.json.
        try {
            val options = FirebaseOptions.fromResource(this)
            if (options == null) {
                firebaseDiagnostic = "No se encontraron recursos de Firebase. Revisa google-services.json y sincroniza Gradle."
                return
            }
            FirebaseApp.getApps(this).firstOrNull() ?: FirebaseApp.initializeApp(this, options)
            firebaseConfigured = true
            firebaseDiagnostic = "Firebase configurado para ${options.projectId ?: "un proyecto sin identificador"}."
        } catch (_: IllegalStateException) {
            firebaseConfigured = false
            firebaseDiagnostic = "Firebase no pudo iniciarse. Revisa el archivo de configuración y los servicios disponibles."
        } catch (_: IllegalArgumentException) {
            firebaseConfigured = false
            firebaseDiagnostic = "La configuración de Firebase parece inválida. Verifica el applicationId y google-services.json."
        }
    }
}
