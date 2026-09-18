package com.pedroleite.opencomanda.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pedroleite.opencomanda.data.local.entity.OrderItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderItemDao {
    @Insert
    suspend fun insert(item: OrderItemEntity): Long

    @Update
    suspend fun update(item: OrderItemEntity)

    @Delete
    suspend fun delete(item: OrderItemEntity)

    @Query("SELECT * FROM order_items WHERE orderId = :orderId ORDER BY id ASC")
    fun getItemsForOrder(orderId: Long): Flow<List<OrderItemEntity>>

    /** Suspend, one-shot variant of [getItemsForOrder] — for use inside a transaction (e.g.
     *  cancelling a Comanda, which must walk every item to restore its stock), where collecting
     *  a Flow query is unnecessary and best avoided. */
    @Query("SELECT * FROM order_items WHERE orderId = :orderId ORDER BY id ASC")
    suspend fun getItemsForOrderOnce(orderId: Long): List<OrderItemEntity>

    /** The existing line for this product within this order, if any — an open Comanda holds at
     *  most one line per product, so adding more of the same product merges into it rather than
     *  creating a duplicate row (see [com.pedroleite.opencomanda.data.repository.OrderRepository.addComandaItem]). */
    @Query("SELECT * FROM order_items WHERE orderId = :orderId AND productId = :productId LIMIT 1")
    suspend fun getByOrderAndProduct(orderId: Long, productId: Long): OrderItemEntity?

    @Query("SELECT COALESCE(SUM(subtotalCents), 0) FROM order_items WHERE orderId = :orderId")
    fun getOrderTotalCents(orderId: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(subtotalCents), 0) FROM order_items WHERE orderId = :orderId")
    suspend fun getOrderTotalCentsOnce(orderId: Long): Long
}
