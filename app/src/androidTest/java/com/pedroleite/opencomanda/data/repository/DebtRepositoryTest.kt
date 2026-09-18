package com.pedroleite.opencomanda.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import com.pedroleite.opencomanda.data.local.entity.DebtEntity
import com.pedroleite.opencomanda.domain.DebtStatus
import com.pedroleite.opencomanda.domain.PaymentMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Fiado payment invariants are enforced in business logic (via
 * [DebtRepository]/[com.pedroleite.opencomanda.domain.DebtBalanceCalculator]), not just in the
 * UI — including that a repayment is only ever attached to whichever cash session is OPEN at
 * the moment it's recorded, never one supplied by the caller (see [OrderRepository]'s own
 * equivalent rule for sales).
 */
@RunWith(AndroidJUnit4::class)
class DebtRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var debtRepository: DebtRepository
    private lateinit var cashRegisterRepository: CashRegisterRepository
    private var debtId: Long = 0
    private var customerId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        debtRepository = DebtRepository(database, database.debtDao(), database.debtPaymentDao(), database.cashSessionDao())
        cashRegisterRepository = CashRegisterRepository(
            database,
            database.cashSessionDao(),
            database.paymentDao(),
            database.debtPaymentDao(),
        )

        val now = System.currentTimeMillis()
        customerId = database.customerDao().insert(CustomerEntity(name = "Ana", createdAt = now, updatedAt = now))
        debtId = database.debtDao().insert(
            DebtEntity(
                customerId = customerId,
                originalAmountCents = 10_000,
                status = DebtStatus.OPEN,
                createdAt = now,
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ---------------------------------------------------------------------------------------
    // Partial / full payment, validation
    // ---------------------------------------------------------------------------------------

    @Test
    fun partialPaymentMovesStatusToPartiallyPaid() = runBlocking {
        debtRepository.registerPayment(debtId, amountCents = 4_000, method = PaymentMethod.CASH)

        val debt = database.debtDao().getById(debtId)!!
        assertEquals(DebtStatus.PARTIALLY_PAID, debt.status)

        val paid = database.debtPaymentDao().getTotalPaidForDebt(debtId)
        assertEquals(4_000L, paid)
    }

    @Test
    fun aSecondPartialPaymentAccumulatesOnTheFirst() = runBlocking {
        debtRepository.registerPayment(debtId, amountCents = 4_000, method = PaymentMethod.CASH)
        debtRepository.registerPayment(debtId, amountCents = 3_000, method = PaymentMethod.PIX)

        val debt = database.debtDao().getById(debtId)!!
        assertEquals(DebtStatus.PARTIALLY_PAID, debt.status)
        assertEquals(7_000L, database.debtPaymentDao().getTotalPaidForDebt(debtId))
    }

    @Test
    fun payingTheFullOutstandingBalanceMarksTheDebtPaid() = runBlocking {
        debtRepository.registerPayment(debtId, amountCents = 6_000, method = PaymentMethod.PIX)
        debtRepository.registerPayment(debtId, amountCents = 4_000, method = PaymentMethod.CASH)

        val debt = database.debtDao().getById(debtId)!!
        assertEquals(DebtStatus.PAID, debt.status)
    }

    @Test
    fun payingExactlyTheRemainingBalanceInOnePaymentMarksTheDebtPaid() = runBlocking {
        debtRepository.registerPayment(debtId, amountCents = 10_000, method = PaymentMethod.CASH)

        assertEquals(DebtStatus.PAID, database.debtDao().getById(debtId)!!.status)
    }

    @Test
    fun rejectsAZeroOrNegativePayment() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { debtRepository.registerPayment(debtId, 0, PaymentMethod.CASH) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { debtRepository.registerPayment(debtId, -500, PaymentMethod.CASH) }
        }
        Unit
    }

    @Test
    fun rejectsAPaymentThatWouldExceedTheOutstandingBalance() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { debtRepository.registerPayment(debtId, 10_001, PaymentMethod.CASH) }
        }
        Unit
    }

    @Test
    fun anOverpaymentAttemptLeavesTheOutstandingBalanceUnchanged() = runBlocking {
        debtRepository.registerPayment(debtId, amountCents = 4_000, method = PaymentMethod.CASH)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { debtRepository.registerPayment(debtId, 6_001, PaymentMethod.CASH) }
        }

        assertEquals(4_000L, database.debtPaymentDao().getTotalPaidForDebt(debtId))
        assertEquals(DebtStatus.PARTIALLY_PAID, database.debtDao().getById(debtId)!!.status)
    }

    @Test
    fun rejectsFurtherPaymentsOnceTheDebtIsFullyPaid() = runBlocking {
        debtRepository.registerPayment(debtId, 10_000, PaymentMethod.CASH)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { debtRepository.registerPayment(debtId, 1, PaymentMethod.CASH) }
        }
        Unit
    }

    @Test
    fun paymentHistoryIsPreserved() = runBlocking {
        debtRepository.registerPayment(debtId, 3_000, PaymentMethod.CASH)
        debtRepository.registerPayment(debtId, 2_000, PaymentMethod.PIX)

        val history = debtRepository.getPaymentsForDebt(debtId).first()
        assertEquals(2, history.size)
        assertEquals(5_000L, history.sumOf { it.amountCents })
    }

    @Test
    fun aPaidDebtIsNotDeletedItRemainsInHistory() = runBlocking {
        debtRepository.registerPayment(debtId, 10_000, PaymentMethod.CASH)

        assertTrue(database.debtDao().getById(debtId) != null)
    }

    // ---------------------------------------------------------------------------------------
    // Inactive customer
    // ---------------------------------------------------------------------------------------

    @Test
    fun aDebtBelongingToAnInactiveCustomerRemainsPayable() = runBlocking {
        database.customerDao().setActive(customerId, false, System.currentTimeMillis())

        debtRepository.registerPayment(debtId, 4_000, PaymentMethod.CASH)

        assertEquals(DebtStatus.PARTIALLY_PAID, database.debtDao().getById(debtId)!!.status)
    }

    // ---------------------------------------------------------------------------------------
    // Cash session association — the UI never supplies a session id; it's looked up fresh
    // inside the same transaction, exactly like OrderRepository's sales payments.
    // ---------------------------------------------------------------------------------------

    @Test
    fun repaymentHasNoSessionWhenNoneIsOpen() = runBlocking {
        debtRepository.registerPayment(debtId, 4_000, PaymentMethod.CASH)

        val payment = debtRepository.getPaymentsForDebt(debtId).first().single()
        assertNull(payment.cashSessionId)
    }

    @Test
    fun repaymentIsAttachedToTheCurrentlyOpenSession() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)

        debtRepository.registerPayment(debtId, 4_000, PaymentMethod.CASH)

        val payment = debtRepository.getPaymentsForDebt(debtId).first().single()
        assertEquals(sessionId, payment.cashSessionId)
    }

    @Test
    fun anEarlierRepaymentMadeBeforeOpeningTheCashSessionIsNotRetroactivelyAttached() = runBlocking {
        debtRepository.registerPayment(debtId, 4_000, PaymentMethod.CASH) // No session open yet.
        val sessionId = cashRegisterRepository.openSession(0)

        val payments = debtRepository.getPaymentsForDebt(debtId).first()
        assertEquals(1, payments.size)
        assertNull(payments.single().cashSessionId)
        assertTrue(sessionId > 0) // Session did open — it just never touches the earlier payment.
    }

    @Test
    fun repaymentIsNotAttachedToAPreviouslyClosedSession() = runBlocking {
        val closedId = cashRegisterRepository.openSession(0)
        cashRegisterRepository.closeSession(closedId, countedCashCents = 0)

        debtRepository.registerPayment(debtId, 4_000, PaymentMethod.CASH)

        val payment = debtRepository.getPaymentsForDebt(debtId).first().single()
        assertNull(payment.cashSessionId)
    }

    @Test
    fun repaymentBelongsToTheSecondSessionAfterTheFirstIsClosed() = runBlocking {
        val firstId = cashRegisterRepository.openSession(0)
        cashRegisterRepository.closeSession(firstId, countedCashCents = 0)
        val secondId = cashRegisterRepository.openSession(0)

        debtRepository.registerPayment(debtId, 4_000, PaymentMethod.CASH)

        val payment = debtRepository.getPaymentsForDebt(debtId).first().single()
        assertEquals(secondId, payment.cashSessionId)
    }

    // ---------------------------------------------------------------------------------------
    // Remaining balance, reactive
    // ---------------------------------------------------------------------------------------

    @Test
    fun getOpenDebtsWithRemainingReflectsPaymentsMade() = runBlocking {
        debtRepository.registerPayment(debtId, 4_000, PaymentMethod.CASH)

        val open = debtRepository.getOpenDebtsWithRemaining().first()
        val entry = open.single { it.debt.id == debtId }
        assertEquals(4_000L, entry.paidCents)
        assertEquals(6_000L, entry.remainingCents)
    }

    @Test
    fun getOpenDebtsWithRemainingExcludesFullyPaidDebts() = runBlocking {
        debtRepository.registerPayment(debtId, 10_000, PaymentMethod.CASH)

        val open = debtRepository.getOpenDebtsWithRemaining().first()
        assertTrue(open.none { it.debt.id == debtId })
    }

    @Test
    fun getOpenDebtsForCustomerWithRemainingOnlyIncludesThatCustomersOpenDebts() = runBlocking {
        val now = System.currentTimeMillis()
        val otherCustomerId = database.customerDao().insert(CustomerEntity(name = "Bruno", createdAt = now, updatedAt = now))
        database.debtDao().insert(
            DebtEntity(customerId = otherCustomerId, originalAmountCents = 5_000, status = DebtStatus.OPEN, createdAt = now),
        )

        val debts = debtRepository.getOpenDebtsForCustomerWithRemaining(customerId).first()
        assertEquals(1, debts.size)
        assertEquals(debtId, debts.single().debt.id)
    }
}
