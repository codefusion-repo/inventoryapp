package cl.aiep.inventoryapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM inventory_products WHERE deleted = 0 ORDER BY nombre COLLATE NOCASE")
    fun observeActive(): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM inventory_products WHERE pendingSync = 1")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM inventory_products WHERE pendingSync = 1 ORDER BY updatedAtMillis")
    suspend fun getPending(): List<ProductEntity>

    @Query("SELECT * FROM inventory_products WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: ProductEntity)

    @Query("UPDATE inventory_products SET pendingSync = 0 WHERE id = :id AND changeId = :changeId")
    suspend fun markSynced(id: String, changeId: String): Int
}
