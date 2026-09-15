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

    @Query("SELECT COALESCE(SUM(subtotalCents), 0) FROM order_items WHERE orderId = :orderId")
    fun getOrderTotalCents(orderId: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(subtotalCents), 0) FROM order_items WHERE orderId = :orderId")
    suspend fun getOrderTotalCentsOnce(orderId: Long): Long
}
