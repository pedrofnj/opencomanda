package com.pedroleite.opencomanda.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pedroleite.opencomanda.domain.OrderStatus
import com.pedroleite.opencomanda.domain.OrderType

/**
 * A Comanda (open tab) or a Quick Sale, unified under one table: both are "an order with
 * items and a settlement", differing only in [orderType] and how they're created/used.
 *
 * The total is intentionally NOT stored here. A denormalized total column could drift from
 * the actual items whenever a comanda's items change while it's open; summing on read (see
 * [com.pedroleite.opencomanda.data.local.dao.OrderItemDao.getOrderTotalCents]) is always
 * correct and, at this app's expected data volume, effectively free.
 */
@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("customerId"), Index("status"), Index("orderType")],
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerId: Long? = null,
    /** Table/tab identifier, e.g. "Mesa 3". Only meaningful for [OrderType.COMANDA]. */
    val displayName: String? = null,
    val orderType: OrderType,
    val status: OrderStatus,
    val openedAt: Long,
    val closedAt: Long? = null,
)
