package com.pedroleite.opencomanda.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pedroleite.opencomanda.data.local.entity.DebtPaymentEntity
import com.pedroleite.opencomanda.domain.PaymentMethod
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtPaymentDao {
    @Insert
    suspend fun insert(payment: DebtPaymentEntity): Long

    @Query("SELECT * FROM debt_payments WHERE debtId = :debtId ORDER BY paidAt ASC")
    fun getForDebt(debtId: Long): Flow<List<DebtPaymentEntity>>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM debt_payments WHERE debtId = :debtId")
    suspend fun getTotalPaidForDebt(debtId: Long): Long

    /** Money received paying off Fiado debts in [cashSessionId], grouped by method. */
    @Query(
        "SELECT method, COALESCE(SUM(amountCents), 0) AS totalCents " +
            "FROM debt_payments WHERE cashSessionId = :cashSessionId GROUP BY method",
    )
    suspend fun getTotalsByMethodForSession(cashSessionId: Long): List<MethodTotal>

    data class MethodTotal(val method: PaymentMethod, val totalCents: Long)
}
