package com.pedroleite.opencomanda.data.repository

import androidx.room.withTransaction
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.dao.CashSessionDao
import com.pedroleite.opencomanda.data.local.dao.DebtPaymentDao
import com.pedroleite.opencomanda.data.local.dao.PaymentDao
import com.pedroleite.opencomanda.data.local.entity.CashSessionEntity
import com.pedroleite.opencomanda.data.local.entity.PaymentEntity
import com.pedroleite.opencomanda.domain.CashSessionStatus
import com.pedroleite.opencomanda.domain.PaymentMethod
import kotlinx.coroutines.flow.Flow

/**
 * A cash session's financial summary, computed from its persisted [PaymentEntity] rows — never
 * from a total supplied by the UI (see [CashRegisterRepository.closeSession]).
 *
 * [expectedCashCents] is [CashSessionEntity.openingBalanceCents] plus only the
 * [PaymentMethod.CASH] portion of [totalsByMethod] — PIX/debit/credit are received money too,
 * but they never touch the physical drawer, so they're excluded from the expected cash amount
 * even though they count toward [totalReceivedCents].
 */
data class CashSessionSummary(
    val session: CashSessionEntity,
    val totalsByMethod: Map<PaymentMethod, Long>,
    val totalReceivedCents: Long,
    val expectedCashCents: Long,
) {
    fun receivedCentsFor(method: PaymentMethod): Long = totalsByMethod[method] ?: 0L

    /** Counted minus expected, once [CashSessionEntity.closingBalanceCents] is set — null while
     *  still OPEN, or if the session was closed without a counted amount. */
    val differenceCents: Long? get() = session.closingBalanceCents?.let { it - expectedCashCents }
}

class CashRegisterRepository(
    private val database: AppDatabase,
    private val cashSessionDao: CashSessionDao,
    private val paymentDao: PaymentDao,
    private val debtPaymentDao: DebtPaymentDao,
) {

    /** The currently OPEN session, if any — Quick Sale/Comanda payments made while this is null
     *  are simply never attached to a session (see [OrderRepository]'s own lookup at the moment
     *  a Payment is created; this repository doesn't mediate that). */
    fun observeOpenSession(): Flow<CashSessionEntity?> = cashSessionDao.observeByStatus(CashSessionStatus.OPEN)

    fun getAllSessions(): Flow<List<CashSessionEntity>> = cashSessionDao.getAll()

    fun observePaymentsForSession(sessionId: Long): Flow<List<PaymentEntity>> =
        paymentDao.observeForSession(sessionId)

    /**
     * Opens a new cash session with [openingBalanceCents] of float/change money already in the
     * drawer — never revenue, never a [PaymentEntity]. Atomic: checking that no session is
     * already open and inserting the new one happen in one transaction, so two rapid opens can
     * never both succeed (unlike a plain check-then-insert, which would race).
     */
    suspend fun openSession(openingBalanceCents: Long, notes: String? = null): Long {
        require(openingBalanceCents >= 0) { "Opening balance cannot be negative" }
        return database.withTransaction {
            check(cashSessionDao.getByStatus(CashSessionStatus.OPEN) == null) {
                "A cash session is already open"
            }
            cashSessionDao.insert(
                CashSessionEntity(
                    openedAt = System.currentTimeMillis(),
                    openingBalanceCents = openingBalanceCents,
                    status = CashSessionStatus.OPEN,
                    notes = notes?.trim()?.ifBlank { null },
                ),
            )
        }
    }

    /**
     * Closes [sessionId] atomically: revalidates it is still OPEN, computes the final summary
     * from persisted [PaymentEntity] rows (never trusting a total supplied by the caller),
     * persists [countedCashCents] as the drawer's actual counted amount, and marks the session
     * CLOSED. Once closed, no new payment can attach to it (see [OrderRepository]'s lookup,
     * which only ever finds a session with [CashSessionStatus.OPEN]).
     */
    suspend fun closeSession(sessionId: Long, countedCashCents: Long?): CashSessionSummary {
        if (countedCashCents != null) require(countedCashCents >= 0) { "Counted cash cannot be negative" }
        return database.withTransaction {
            val session = cashSessionDao.getById(sessionId) ?: error("Cash session $sessionId not found")
            check(session.status == CashSessionStatus.OPEN) { "Cash session is not open" }

            val summary = buildSummary(session)
            val closedSession = session.copy(
                status = CashSessionStatus.CLOSED,
                closedAt = System.currentTimeMillis(),
                closingBalanceCents = countedCashCents,
            )
            cashSessionDao.update(closedSession)
            summary.copy(session = closedSession)
        }
    }

    /** The current, authoritative summary for [sessionId] — computed fresh from persisted
     *  payments, independent of whatever the dashboard last observed reactively. Used right
     *  before showing the closing confirmation. */
    suspend fun currentSummary(sessionId: Long): CashSessionSummary {
        val session = cashSessionDao.getById(sessionId) ?: error("Cash session $sessionId not found")
        return buildSummary(session)
    }

    private suspend fun buildSummary(session: CashSessionEntity): CashSessionSummary {
        val totals = paymentDao.getTotalsByMethodForSession(session.id).associate { it.method to it.totalCents }
        val totalReceived = totals.values.sum()
        val cashReceived = totals[PaymentMethod.CASH] ?: 0L
        return CashSessionSummary(
            session = session,
            totalsByMethod = totals,
            totalReceivedCents = totalReceived,
            expectedCashCents = session.openingBalanceCents + cashReceived,
        )
    }

    /** Money received paying off Fiado debts, grouped by payment method. Always empty for now —
     *  Fiado/debt settlement isn't implemented yet (a later increment) — but [DebtPaymentEntity]
     *  already carries its own `cashSessionId`, so once it exists this can be folded into
     *  [CashSessionSummary] alongside sales without a schema change. */
    suspend fun getDebtPaymentTotalsByMethod(sessionId: Long) = debtPaymentDao.getTotalsByMethodForSession(sessionId)
}
