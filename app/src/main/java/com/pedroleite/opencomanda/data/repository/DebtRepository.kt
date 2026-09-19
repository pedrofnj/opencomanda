package com.pedroleite.opencomanda.data.repository

import androidx.room.withTransaction
import com.pedroleite.opencomanda.core.Money
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.dao.CashSessionDao
import com.pedroleite.opencomanda.data.local.dao.DebtDao
import com.pedroleite.opencomanda.data.local.dao.DebtPaymentDao
import com.pedroleite.opencomanda.data.local.entity.DebtEntity
import com.pedroleite.opencomanda.data.local.entity.DebtPaymentEntity
import com.pedroleite.opencomanda.domain.CashSessionStatus
import com.pedroleite.opencomanda.domain.DebtBalanceCalculator
import com.pedroleite.opencomanda.domain.DebtStatus
import com.pedroleite.opencomanda.domain.PaymentMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A debt paired with how much of it has been paid so far — [remainingCents] is what's still
 *  owed. Combines a persisted [DebtEntity] with a live total from [DebtPaymentEntity] rows
 *  rather than storing a denormalized remaining-balance column (see [DebtEntity]'s own doc on
 *  why [DebtEntity.status] is the one exception to that rule). */
data class DebtWithRemaining(val debt: DebtEntity, val paidCents: Long) {
    val remainingCents: Long get() = debt.originalAmountCents - paidCents
}

class DebtRepository(
    private val database: AppDatabase,
    private val debtDao: DebtDao,
    private val debtPaymentDao: DebtPaymentDao,
    private val cashSessionDao: CashSessionDao,
) {

    /** The currently OPEN cash session's id, if any — looked up fresh at the moment a
     *  [DebtPaymentEntity] is about to be created, inside the same transaction, exactly like
     *  [OrderRepository.currentOpenCashSessionId]. Never based on UI state that could be stale,
     *  and never retroactively reassigned once a session closes. */
    private suspend fun currentOpenCashSessionId(): Long? = cashSessionDao.getByStatus(CashSessionStatus.OPEN)?.id

    fun getForCustomer(customerId: Long): Flow<List<DebtEntity>> = debtDao.getForCustomer(customerId)

    fun observeDebt(debtId: Long): Flow<DebtEntity?> = debtDao.observeById(debtId)

    fun getPaymentsForDebt(debtId: Long): Flow<List<DebtPaymentEntity>> = debtPaymentDao.getForDebt(debtId)

    /** Every debt across every customer that isn't fully [DebtStatus.PAID] yet, each paired with
     *  its live remaining balance — the Fiado screen's "who owes money?" list is built from this. */
    fun getOpenDebtsWithRemaining(): Flow<List<DebtWithRemaining>> =
        combine(debtDao.getOpen(DebtStatus.PAID), debtPaymentDao.observeTotalsByDebt(), ::mergeWithTotals)

    /** One customer's own outstanding debts (open or partial only — a paid-off debt is history,
     *  not an operational concern), each paired with its live remaining balance. */
    fun getOpenDebtsForCustomerWithRemaining(customerId: Long): Flow<List<DebtWithRemaining>> =
        combine(debtDao.getForCustomer(customerId), debtPaymentDao.observeTotalsByDebt()) { debts, totals ->
            mergeWithTotals(debts.filter { it.status != DebtStatus.PAID }, totals)
        }

    private fun mergeWithTotals(debts: List<DebtEntity>, totals: List<DebtPaymentDao.DebtTotal>): List<DebtWithRemaining> {
        val paidByDebtId = totals.associate { it.debtId to it.totalCents }
        return debts.map { debt -> DebtWithRemaining(debt, paidByDebtId[debt.id] ?: 0L) }
    }

    /**
     * Registers a payment against [debtId], enforcing — in business logic, not just UI —
     * that amount > 0, amount <= outstanding balance, and that the debt still has a balance
     * (see [DebtBalanceCalculator]). Updates the debt's status to PARTIALLY_PAID or PAID
     * accordingly. Atomic: the payment row and the status update happen together, and — just
     * like [OrderRepository.confirmQuickSale] — the payment is attached to whichever cash
     * session is OPEN at this exact moment, if any; the caller never supplies one.
     */
    suspend fun registerPayment(debtId: Long, amountCents: Long, method: PaymentMethod) {
        database.withTransaction {
            val debt = debtDao.getById(debtId) ?: error("Debt $debtId not found")
            val paidSoFar = debtPaymentDao.getTotalPaidForDebt(debtId)
            val outstanding = Money(debt.originalAmountCents) - Money(paidSoFar)
            val newStatus = DebtBalanceCalculator.validatePayment(Money(amountCents), outstanding)

            val now = System.currentTimeMillis()
            debtPaymentDao.insert(
                DebtPaymentEntity(
                    debtId = debtId,
                    cashSessionId = currentOpenCashSessionId(),
                    amountCents = amountCents,
                    method = method,
                    paidAt = now,
                ),
            )
            debtDao.updateStatus(debtId, newStatus)
        }
    }
}
