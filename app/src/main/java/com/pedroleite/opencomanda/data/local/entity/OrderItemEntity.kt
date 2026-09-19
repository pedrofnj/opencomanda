package com.pedroleite.opencomanda.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A line item within an [OrderEntity]. [productNameSnapshot] and [unitPriceCentsSnapshot] are
 * captured at the moment the item is added and never updated afterwards, even if the source
 * [ProductEntity] later changes name/price or is deactivated — this preserves accurate
 * historical order records. [subtotalCents] is recomputed (and rewritten) only when this same
 * row's own quantity is edited while its order is still open.
 */
@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("orderId"), Index("productId")],
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderId: Long,
    val productId: Long? = null,
    val productNameSnapshot: String,
    val unitPriceCentsSnapshot: Long,
    val quantity: Double,
    val subtotalCents: Long,
)
