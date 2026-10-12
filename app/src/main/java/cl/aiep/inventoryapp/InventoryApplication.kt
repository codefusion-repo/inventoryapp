package cl.aiep.inventoryapp

import android.app.Application
import androidx.room.Room
import cl.aiep.inventoryapp.data.local.InventoryDatabase
import cl.aiep.inventoryapp.data.local.LocalInventoryRepository
import com.google.firebase.FirebaseApp

class InventoryApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, InventoryDatabase::class.java,
        "inventoryapp.db").build() }
    val repository by lazy { LocalInventoryRepository(database) }
    var firebaseConfigured = false
        private set

    override fun onCreate() {
        super.onCreate()
        firebaseConfigured = FirebaseApp.initializeApp(this) != null
    }
}
