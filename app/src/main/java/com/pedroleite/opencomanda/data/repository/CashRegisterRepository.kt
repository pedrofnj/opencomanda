package com.pedroleite.opencomanda.data.repository

import com.pedroleite.opencomanda.data.local.dao.CashSessionDao
import com.pedroleite.opencomanda.data.local.dao.DebtPaymentDao
import com.pedroleite.opencomanda.data.local.dao.PaymentDao
import com.pedroleite.opencomanda.data.local.entity.CashSessionEntity
import com.pedroleite.opencomanda.domain.CashSessionStatus
import kotlinx.coroutines.flow.Flow

class CashRegisterRepository(
    private val cashSessionDao: CashSessionDao,
    private val paymentDao: PaymentDao,
    private val debtPaymentDao: DebtPaymentDao,
) {

    fun observeOpenSession(): Flow<CashSessionEntity?> = cashSessionDao.observeByStatus(CashSessionStatus.OPEN)

    fun getAllSessions(): Flow<List<CashSessionEntity>> = cashSessionDao.getAll()

    suspend fun openSession(openingBalanceCents: Long): Long {
        require(openingBalanceCents >= 0) { "Opening balance cannot be negative" }
        check(cashSessionDao.getByStatus(CashSessionStatus.OPEN) == null) {
            "A cash session is already open"
        }
        return cashSessionDao.insert(
            CashSessionEntity(
                openedAt = System.currentTimeMillis(),
                openingBalanceCents = openingBalanceCents,
                status = CashSessionStatus.OPEN,
            ),
        )
    }

    suspend fun closeSession(sessionId: Long, closingBalanceCents: Long?) {
        val session = cashSessionDao.getById(sessionId) ?: error("Cash session $sessionId not found")
        check(session.status == CashSessionStatus.OPEN) { "Cash session is not open" }
        cashSessionDao.update(
            session.copy(
                status = CashSessionStatus.CLOSED,
                closedAt = System.currentTimeMillis(),
                closingBalanceCents = closingBalanceCents,
            ),
        )
    }

    /** Money received for order sales, grouped by payment method. Excludes Fiado entirely. */
    suspend fun getSalesTotalsByMethod(sessionId: Long) = paymentDao.getTotalsByMethodForSession(sessionId)

    /** Money received paying off Fiado debts, grouped by payment method. Kept separate from sales. */
    suspend fun getDebtPaymentTotalsByMethod(sessionId: Long) = debtPaymentDao.getTotalsByMethodForSession(sessionId)
}
