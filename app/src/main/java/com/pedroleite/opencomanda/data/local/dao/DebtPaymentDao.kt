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

    /** Live receipts for [cashSessionId] — mirrors [PaymentDao.observeForSession] so the Cash
     *  Register dashboard's totals react to a Fiado repayment the same way they react to a
     *  regular sale, with no manual refresh. */
    @Query("SELECT * FROM debt_payments WHERE cashSessionId = :cashSessionId ORDER BY paidAt ASC")
    fun observeForSession(cashSessionId: Long): Flow<List<DebtPaymentEntity>>

    /** Money received paying off Fiado debts in [cashSessionId], grouped by method. */
    @Query(
        "SELECT method, COALESCE(SUM(amountCents), 0) AS totalCents " +
            "FROM debt_payments WHERE cashSessionId = :cashSessionId GROUP BY method",
    )
    suspend fun getTotalsByMethodForSession(cashSessionId: Long): List<MethodTotal>

    /** Total paid so far for every debt that has at least one payment — combined with the open
     *  debts themselves (see [com.pedroleite.opencomanda.data.repository.DebtRepository]) to
     *  derive each debt's remaining balance reactively, without a per-debt flow. */
    @Query("SELECT debtId, COALESCE(SUM(amountCents), 0) AS totalCents FROM debt_payments GROUP BY debtId")
    fun observeTotalsByDebt(): Flow<List<DebtTotal>>

    data class MethodTotal(val method: PaymentMethod, val totalCents: Long)

    data class DebtTotal(val debtId: Long, val totalCents: Long)
}
