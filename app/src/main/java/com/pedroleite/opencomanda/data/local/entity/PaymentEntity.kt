package com.pedroleite.opencomanda.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pedroleite.opencomanda.domain.PaymentMethod

/**
 * Money actually received for an [OrderEntity]. Fiado is deliberately NOT a [PaymentMethod]:
 * closing an order as Fiado produces a [DebtEntity] instead of a Payment, since no money
 * changes hands at that moment. This makes it structurally impossible for a Fiado sale to be
 * accidentally summed into cash-register "money received" totals.
 */
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CashSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["cashSessionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("orderId"), Index("cashSessionId"), Index("method")],
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val orderId: Long,
    /** The cash session open at the moment this payment was recorded, if any. */
    val cashSessionId: Long? = null,
    val method: PaymentMethod,
    val amountCents: Long,
    val createdAt: Long,
)
