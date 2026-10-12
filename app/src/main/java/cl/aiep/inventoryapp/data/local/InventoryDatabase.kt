package cl.aiep.inventoryapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ProductEntity::class], version = 1, exportSchema = true)
abstract class InventoryDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
}
