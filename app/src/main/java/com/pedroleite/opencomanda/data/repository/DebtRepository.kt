package com.pedroleite.opencomanda.data.repository

import androidx.room.withTransaction
import com.pedroleite.opencomanda.core.Money
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.dao.DebtDao
import com.pedroleite.opencomanda.data.local.dao.DebtPaymentDao
import com.pedroleite.opencomanda.data.local.entity.DebtEntity
import com.pedroleite.opencomanda.data.local.entity.DebtPaymentEntity
import com.pedroleite.opencomanda.domain.DebtBalanceCalculator
import com.pedroleite.opencomanda.domain.DebtStatus
import com.pedroleite.opencomanda.domain.PaymentMethod
import kotlinx.coroutines.flow.Flow

class DebtRepository(
    private val database: AppDatabase,
    private val debtDao: DebtDao,
    private val debtPaymentDao: DebtPaymentDao,
) {

    fun getForCustomer(customerId: Long): Flow<List<DebtEntity>> = debtDao.getForCustomer(customerId)

    fun getOpenDebts(): Flow<List<DebtEntity>> = debtDao.getOpen(DebtStatus.PAID)

    fun getPaymentsForDebt(debtId: Long): Flow<List<DebtPaymentEntity>> = debtPaymentDao.getForDebt(debtId)

    /**
     * Registers a payment against [debtId], enforcing — in business logic, not just UI —
     * that amount > 0, amount <= outstanding balance, and that the debt still has a balance
     * (see [DebtBalanceCalculator]). Updates the debt's status to PARTIALLY_PAID or PAID
     * accordingly. Atomic: the payment row and the status update happen together.
     */
    suspend fun registerPayment(debtId: Long, amountCents: Long, method: PaymentMethod, cashSessionId: Long?) {
        database.withTransaction {
            val debt = debtDao.getById(debtId) ?: error("Debt $debtId not found")
            val paidSoFar = debtPaymentDao.getTotalPaidForDebt(debtId)
            val outstanding = Money(debt.originalAmountCents) - Money(paidSoFar)
            val newStatus = DebtBalanceCalculator.validatePayment(Money(amountCents), outstanding)

            val now = System.currentTimeMillis()
            debtPaymentDao.insert(
                DebtPaymentEntity(
                    debtId = debtId,
                    cashSessionId = cashSessionId,
                    amountCents = amountCents,
                    method = method,
                    paidAt = now,
                ),
            )
            debtDao.updateStatus(debtId, newStatus)
        }
    }
}
