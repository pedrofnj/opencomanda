package com.pedroleite.opencomanda.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pedroleite.opencomanda.domain.DebtStatus

/**
 * A Fiado debt owed by a customer, optionally originating from an [OrderEntity] that was
 * closed as Fiado. [status] is kept as a persisted, indexed column (rather than computed from
 * payments on every read) because "customers with open Fiado" is a common filtered list, and
 * the column is only ever written in one place:
 * [com.pedroleite.opencomanda.data.repository.DebtRepository.registerPayment].
 */
@Entity(
    tableName = "debts",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("customerId"), Index("orderId"), Index("status")],
)
data class DebtEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerId: Long,
    val orderId: Long? = null,
    val originalAmountCents: Long,
    val notes: String? = null,
    val status: DebtStatus,
    val createdAt: Long,
)
