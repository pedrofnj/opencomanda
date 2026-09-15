package com.pedroleite.opencomanda.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pedroleite.opencomanda.domain.PaymentMethod

/**
 * A single payment applied against a [DebtEntity]. Kept as its own table — rather than a
 * mutable "amount paid" column on Debt — so partial payments and their full history are
 * supported from the start, without needing a schema migration later.
 */
@Entity(
    tableName = "debt_payments",
    foreignKeys = [
        ForeignKey(
            entity = DebtEntity::class,
            parentColumns = ["id"],
            childColumns = ["debtId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CashSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["cashSessionId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("debtId"), Index("cashSessionId")],
)
data class DebtPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val debtId: Long,
    /** The cash session open at the moment this repayment was recorded, if any. */
    val cashSessionId: Long? = null,
    val amountCents: Long,
    val method: PaymentMethod,
    val paidAt: Long,
)
