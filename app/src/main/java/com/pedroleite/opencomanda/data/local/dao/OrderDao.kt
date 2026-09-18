package com.pedroleite.opencomanda.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
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

    /** Reactive single-order lookup — used by the Comanda detail screen so it picks up this same
     *  order's own later mutations (close/cancel) without a manual refetch. */
    @Query("SELECT * FROM orders WHERE id = :id")
    fun observeById(id: Long): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE orderType = :orderType AND status = :status ORDER BY openedAt DESC")
    fun getByTypeAndStatus(orderType: OrderType, status: OrderStatus): Flow<List<OrderEntity>>

    /** One row per matching order, with its current item count and total pre-aggregated —
     *  everything the Open Comandas list needs without a separate per-order query. */
    @Query(
        "SELECT o.*, COALESCE(SUM(oi.subtotalCents), 0) AS totalCents, COALESCE(SUM(oi.quantity), 0) AS itemCount " +
            "FROM orders o LEFT JOIN order_items oi ON oi.orderId = o.id " +
            "WHERE o.orderType = :orderType AND o.status = :status " +
            "GROUP BY o.id ORDER BY o.openedAt DESC",
    )
    fun getSummariesByTypeAndStatus(orderType: OrderType, status: OrderStatus): Flow<List<ComandaSummary>>

    @Query("UPDATE orders SET status = :status, closedAt = :closedAt WHERE id = :id")
    suspend fun close(id: Long, status: OrderStatus, closedAt: Long)

    data class ComandaSummary(
        @Embedded val order: OrderEntity,
        val totalCents: Long,
        val itemCount: Double,
    )
}
