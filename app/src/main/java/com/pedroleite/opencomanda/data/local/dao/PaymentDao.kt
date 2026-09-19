package com.pedroleite.opencomanda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pedroleite.opencomanda.data.local.entity.PaymentEntity
import com.pedroleite.opencomanda.domain.PaymentMethod
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Insert
    suspend fun insert(payment: PaymentEntity): Long

    @Query("SELECT * FROM payments WHERE orderId = :orderId ORDER BY createdAt ASC")
    fun getForOrder(orderId: Long): Flow<List<PaymentEntity>>

    /** Live receipts for [cashSessionId] — the Cash Register dashboard derives its grouped
     *  totals, total received and expected cash from this, so it updates automatically as new
     *  sales complete without a manual refresh. */
    @Query("SELECT * FROM payments WHERE cashSessionId = :cashSessionId ORDER BY createdAt ASC")
    fun observeForSession(cashSessionId: Long): Flow<List<PaymentEntity>>

    /** Money received for order sales in [cashSessionId], grouped by method. Never includes Fiado. */
    @Query(
        "SELECT method, COALESCE(SUM(amountCents), 0) AS totalCents " +
            "FROM payments WHERE cashSessionId = :cashSessionId GROUP BY method",
    )
    suspend fun getTotalsByMethodForSession(cashSessionId: Long): List<MethodTotal>

    data class MethodTotal(val method: PaymentMethod, val totalCents: Long)
}
