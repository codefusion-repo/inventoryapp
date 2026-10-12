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
        // El plugin google-services genera google_app_id / project_id durante la build.
        // FirebaseOptions.fromResource permite diferenciar configuración ausente de sesión cerrada.
        val options = FirebaseOptions.fromResource(this)
        if (options == null) {
            firebaseDiagnostic = "Faltan recursos de Firebase. Verifica app/google-services.json, el applicationId cl.aiep.inventoryapp y vuelve a sincronizar Gradle."
            return
        }
        val app = FirebaseApp.getApps(this).firstOrNull()
            ?: FirebaseApp.initializeApp(this, options)
        firebaseConfigured = app != null
        firebaseDiagnostic = if (firebaseConfigured) {
            "Firebase inicializado (proyecto: ${options.projectId ?: "sin identificador"}). Inicia sesión para comprobar acceso a Firestore."
        } else {
            "No fue posible inicializar Firebase. Revisa la configuración de Google Services."
        }
    }
}
