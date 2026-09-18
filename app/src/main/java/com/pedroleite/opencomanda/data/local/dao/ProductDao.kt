package com.pedroleite.opencomanda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Insert
    suspend fun insert(product: ProductEntity): Long

    @Update
    suspend fun update(product: ProductEntity)

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE active = 1 ORDER BY name ASC")
    fun getActive(): Flow<List<ProductEntity>>

    /** Active products in a given category — e.g. for a future Quick Sale category browser. */
    @Query("SELECT * FROM products WHERE active = 1 AND categoryId = :categoryId ORDER BY name ASC")
    fun getActiveByCategory(categoryId: Long): Flow<List<ProductEntity>>

    /** Active products with no category — e.g. for a future Quick Sale "uncategorized" section. */
    @Query("SELECT * FROM products WHERE active = 1 AND categoryId IS NULL ORDER BY name ASC")
    fun getActiveUncategorized(): Flow<List<ProductEntity>>

    @Query("UPDATE products SET active = :active, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean, updatedAt: Long)

    @Query(
        "UPDATE products SET stockQuantity = stockQuantity - :quantity, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun decrementStock(id: Long, quantity: Double, updatedAt: Long)

    /** Restores stock previously reserved by [decrementStock] — e.g. a Comanda item removed or
     *  decreased before the Comanda closes, or a cancelled Comanda giving back everything it held. */
    @Query(
        "UPDATE products SET stockQuantity = stockQuantity + :quantity, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun incrementStock(id: Long, quantity: Double, updatedAt: Long)
}
