package com.pedroleite.opencomanda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.domain.OrderStatus
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

    /** Every product whose stock is being tracked, active or not — an inactive product can still
     *  hold stock that needs correcting. Untracked products have no inventory, so they're excluded. */
    @Query("SELECT * FROM products WHERE trackStock = 1 ORDER BY name ASC")
    fun getTrackedStock(): Flow<List<ProductEntity>>

    /** How many order lines for [productId] sit in orders currently in [status] — used to tell whether
     *  a product still holds stock reserved by an open Comanda (see
     *  [com.pedroleite.opencomanda.data.repository.ProductRepository.update]). */
    @Query(
        "SELECT COUNT(*) FROM order_items oi INNER JOIN orders o ON o.id = oi.orderId " +
            "WHERE oi.productId = :productId AND o.status = :status",
    )
    suspend fun countOrderItemsInOrdersWithStatus(productId: Long, status: OrderStatus): Int

    @Query("UPDATE products SET active = :active, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean, updatedAt: Long)

    /** Writes only the stock figure — a manual correction must never touch name, price, category
     *  or the active flag. */
    @Query("UPDATE products SET stockQuantity = :quantity, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setStockQuantity(id: Long, quantity: Double, updatedAt: Long)

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
