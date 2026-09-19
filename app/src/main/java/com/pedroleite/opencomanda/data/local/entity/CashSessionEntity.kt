package com.pedroleite.opencomanda.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pedroleite.opencomanda.domain.CashSessionStatus

/**
 * A cash-register shift/session. [PaymentEntity] and [DebtPaymentEntity] rows link back to the
 * session (if any) open when they were recorded, which is how totals can later distinguish
 * sales received from Fiado repayments, per payment method, without a separate summary table.
 */
@Entity(tableName = "cash_sessions")
data class CashSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val openedAt: Long,
    val closedAt: Long? = null,
    val openingBalanceCents: Long,
    val closingBalanceCents: Long? = null,
    val status: CashSessionStatus,
    val notes: String? = null,
)
