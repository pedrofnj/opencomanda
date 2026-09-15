package com.pedroleite.opencomanda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pedroleite.opencomanda.data.local.entity.OrderEntity
import com.pedroleite.opencomanda.domain.OrderStatus
import com.pedroleite.opencomanda.domain.OrderType
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert
    suspend fun insert(order: OrderEntity): Long

    @Update
    suspend fun update(order: OrderEntity)

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getById(id: Long): OrderEntity?

    @Query("SELECT * FROM orders WHERE orderType = :orderType AND status = :status ORDER BY openedAt DESC")
    fun getByTypeAndStatus(orderType: OrderType, status: OrderStatus): Flow<List<OrderEntity>>

    @Query("UPDATE orders SET status = :status, closedAt = :closedAt WHERE id = :id")
    suspend fun close(id: Long, status: OrderStatus, closedAt: Long)
}
