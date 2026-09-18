package com.pedroleite.opencomanda.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pedroleite.opencomanda.data.local.AppDatabase
import com.pedroleite.opencomanda.data.local.entity.CustomerEntity
import com.pedroleite.opencomanda.data.local.entity.DebtEntity
import com.pedroleite.opencomanda.data.local.entity.ProductEntity
import com.pedroleite.opencomanda.domain.CashSessionStatus
import com.pedroleite.opencomanda.domain.DebtStatus
import com.pedroleite.opencomanda.domain.PaymentMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Cash Register's business rules: only one session OPEN at a time, its financial
 * summary is always computed from persisted [com.pedroleite.opencomanda.data.local.entity.PaymentEntity]
 * rows rather than trusted from a caller, sales and Fiado debt repayments are kept separate, and
 * closing a session is transactional and immutable afterwards.
 */
@RunWith(AndroidJUnit4::class)
class CashRegisterRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var cashRegisterRepository: CashRegisterRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var debtRepository: DebtRepository

    private var productId: Long = 0
    private var customerId: Long = 0

    /** The test product's unit price, in cents — sales below use quantities of it to produce
     *  round, easy-to-read amounts. */
    private val unitPriceCents = 400L

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        cashRegisterRepository = CashRegisterRepository(
            database,
            database.cashSessionDao(),
            database.paymentDao(),
            database.debtPaymentDao(),
        )
        orderRepository = OrderRepository(
            database = database,
            orderDao = database.orderDao(),
            orderItemDao = database.orderItemDao(),
            paymentDao = database.paymentDao(),
            debtDao = database.debtDao(),
            productDao = database.productDao(),
            cashSessionDao = database.cashSessionDao(),
        )
        debtRepository = DebtRepository(database, database.debtDao(), database.debtPaymentDao())

        val now = System.currentTimeMillis()
        productId = database.productDao().insert(
            ProductEntity(name = "Agua", priceCents = unitPriceCents, createdAt = now, updatedAt = now),
        )
        customerId = database.customerDao().insert(CustomerEntity(name = "Carlos", createdAt = now, updatedAt = now))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun sell(method: PaymentMethod, quantity: Double = 1.0): Long =
        orderRepository.confirmQuickSale(
            lines = listOf(CartLine(productId, quantity)),
            method = method,
            isFiado = false,
            customerId = null,
        )

    // ---------------------------------------------------------------------------------------
    // Opening
    // ---------------------------------------------------------------------------------------

    @Test
    fun openingWithAZeroBalanceSucceeds() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)

        val session = database.cashSessionDao().getById(sessionId)
        assertNotNull(session)
        assertEquals(0L, session!!.openingBalanceCents)
        assertEquals(CashSessionStatus.OPEN, session.status)
    }

    @Test
    fun openingWithAPositiveBalancePersistsItsAmountStatusAndTimestamp() = runBlocking {
        val before = System.currentTimeMillis()
        val sessionId = cashRegisterRepository.openSession(openingBalanceCents = 10_000, notes = "Troco inicial")
        val after = System.currentTimeMillis()

        val session = database.cashSessionDao().getById(sessionId)
        assertNotNull(session)
        assertEquals(10_000L, session!!.openingBalanceCents)
        assertEquals(CashSessionStatus.OPEN, session.status)
        assertEquals("Troco inicial", session.notes)
        assertNull(session.closedAt)
        assertTrue(session.openedAt in before..after)
    }

    @Test
    fun openingWithANegativeBalanceIsRejected() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { cashRegisterRepository.openSession(-1) }
        }
        Unit
    }

    @Test
    fun cannotOpenTwoSessionsAtOnce() = runBlocking {
        cashRegisterRepository.openSession(0)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { cashRegisterRepository.openSession(0) }
        }
        Unit
    }

    @Test
    fun theOpeningBalanceIsNeverRecordedAsAPayment() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(openingBalanceCents = 5_000)

        val summary = cashRegisterRepository.currentSummary(sessionId)
        assertEquals(0L, summary.totalReceivedCents)
        assertTrue(summary.totalsByMethod.isEmpty())
    }

    // ---------------------------------------------------------------------------------------
    // Current-session lookup
    // ---------------------------------------------------------------------------------------

    @Test
    fun observeOpenSessionIsNullWhenNoneHasEverBeenOpened() = runBlocking {
        assertNull(cashRegisterRepository.observeOpenSession().first())
    }

    @Test
    fun observeOpenSessionEmitsTheCurrentlyOpenSession() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)

        val current = cashRegisterRepository.observeOpenSession().first()
        assertNotNull(current)
        assertEquals(sessionId, current!!.id)
    }

    @Test
    fun aClosedSessionIsNoLongerTheCurrentOpenSession() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)
        cashRegisterRepository.closeSession(sessionId, countedCashCents = 0)

        assertNull(cashRegisterRepository.observeOpenSession().first())
    }

    @Test
    fun aNewlyOpenedSessionBecomesTheCurrentOne() = runBlocking {
        val firstId = cashRegisterRepository.openSession(0)
        cashRegisterRepository.closeSession(firstId, countedCashCents = 0)
        val secondId = cashRegisterRepository.openSession(0)

        val current = cashRegisterRepository.observeOpenSession().first()
        assertNotNull(current)
        assertEquals(secondId, current!!.id)
    }

    // ---------------------------------------------------------------------------------------
    // Summary
    // ---------------------------------------------------------------------------------------

    @Test
    fun summaryOfASessionWithNoPaymentsIsAllZero() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(openingBalanceCents = 1_000)

        val summary = cashRegisterRepository.currentSummary(sessionId)
        assertTrue(summary.totalsByMethod.isEmpty())
        assertEquals(0L, summary.totalReceivedCents)
        assertEquals(1_000L, summary.expectedCashCents)
    }

    @Test
    fun summaryGroupsReceivedMoneyByPaymentMethod() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)
        sell(PaymentMethod.CASH)
        sell(PaymentMethod.PIX)
        sell(PaymentMethod.DEBIT)
        sell(PaymentMethod.CREDIT)

        val summary = cashRegisterRepository.currentSummary(sessionId)
        assertEquals(unitPriceCents, summary.receivedCentsFor(PaymentMethod.CASH))
        assertEquals(unitPriceCents, summary.receivedCentsFor(PaymentMethod.PIX))
        assertEquals(unitPriceCents, summary.receivedCentsFor(PaymentMethod.DEBIT))
        assertEquals(unitPriceCents, summary.receivedCentsFor(PaymentMethod.CREDIT))
    }

    @Test
    fun multiplePaymentsOfTheSameMethodAreSummedTogether() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)
        sell(PaymentMethod.CASH, quantity = 1.0)
        sell(PaymentMethod.CASH, quantity = 2.0)

        val summary = cashRegisterRepository.currentSummary(sessionId)
        assertEquals(unitPriceCents * 3, summary.receivedCentsFor(PaymentMethod.CASH))
    }

    @Test
    fun totalReceivedIsTheSumAcrossAllMethods() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)
        sell(PaymentMethod.CASH)
        sell(PaymentMethod.PIX)

        val summary = cashRegisterRepository.currentSummary(sessionId)
        assertEquals(unitPriceCents * 2, summary.totalReceivedCents)
    }

    @Test
    fun expectedCashIsOpeningBalancePlusCashPaymentsOnly() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(openingBalanceCents = 10_000)
        sell(PaymentMethod.CASH, quantity = 2.0)
        sell(PaymentMethod.PIX)
        sell(PaymentMethod.DEBIT)
        sell(PaymentMethod.CREDIT)

        val summary = cashRegisterRepository.currentSummary(sessionId)
        assertEquals(10_000L + unitPriceCents * 2, summary.expectedCashCents)
        // PIX/debit/credit are received money, so they count toward the total, but never touch
        // the physical drawer, so they must not inflate expected cash.
        assertEquals(unitPriceCents * 5, summary.totalReceivedCents)
    }

    @Test
    fun summaryExcludesPaymentsFromOtherSessions() = runBlocking {
        val firstId = cashRegisterRepository.openSession(0)
        sell(PaymentMethod.CASH)
        cashRegisterRepository.closeSession(firstId, countedCashCents = unitPriceCents)

        val secondId = cashRegisterRepository.openSession(0)
        sell(PaymentMethod.PIX)

        val secondSummary = cashRegisterRepository.currentSummary(secondId)
        assertEquals(unitPriceCents, secondSummary.totalReceivedCents)
        assertEquals(0L, secondSummary.receivedCentsFor(PaymentMethod.CASH))
        assertEquals(unitPriceCents, secondSummary.receivedCentsFor(PaymentMethod.PIX))
    }

    @Test
    fun summaryExcludesPaymentsMadeWithNoSessionOpen() = runBlocking {
        sell(PaymentMethod.CASH) // No session open yet — this payment's cashSessionId is null.
        val sessionId = cashRegisterRepository.openSession(0)
        sell(PaymentMethod.PIX)

        val summary = cashRegisterRepository.currentSummary(sessionId)
        assertEquals(unitPriceCents, summary.totalReceivedCents)
        assertEquals(0L, summary.receivedCentsFor(PaymentMethod.CASH))
    }

    // ---------------------------------------------------------------------------------------
    // Payment association — Quick Sale
    // ---------------------------------------------------------------------------------------

    @Test
    fun quickSalePaymentHasNoSessionWhenNoneIsOpen() = runBlocking {
        val orderId = sell(PaymentMethod.CASH)

        val payment = database.paymentDao().getForOrder(orderId).first().single()
        assertNull(payment.cashSessionId)
    }

    @Test
    fun quickSalePaymentIsAttachedToTheCurrentlyOpenSession() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)
        val orderId = sell(PaymentMethod.CASH)

        val payment = database.paymentDao().getForOrder(orderId).first().single()
        assertEquals(sessionId, payment.cashSessionId)
    }

    @Test
    fun quickSalePaymentIsNotAttachedToAPreviouslyClosedSession() = runBlocking {
        val closedId = cashRegisterRepository.openSession(0)
        cashRegisterRepository.closeSession(closedId, countedCashCents = 0)

        val orderId = sell(PaymentMethod.CASH)

        val payment = database.paymentDao().getForOrder(orderId).first().single()
        assertNull(payment.cashSessionId)
    }

    @Test
    fun quickSalePaymentBelongsToTheSecondSessionAfterTheFirstIsClosed() = runBlocking {
        val firstId = cashRegisterRepository.openSession(0)
        cashRegisterRepository.closeSession(firstId, countedCashCents = 0)
        val secondId = cashRegisterRepository.openSession(0)

        val orderId = sell(PaymentMethod.CASH)

        val payment = database.paymentDao().getForOrder(orderId).first().single()
        assertEquals(secondId, payment.cashSessionId)
    }

    // ---------------------------------------------------------------------------------------
    // Payment association — Comanda
    // ---------------------------------------------------------------------------------------

    private suspend fun closeComandaWithPayment(method: PaymentMethod): Long {
        val orderId = orderRepository.createComanda(customerId = null, displayName = "Mesa 1")
        orderRepository.addComandaItem(orderId, productId, 1.0)
        orderRepository.closeOrderWithPayment(orderId, method)
        return orderId
    }

    @Test
    fun comandaPaymentHasNoSessionWhenNoneIsOpen() = runBlocking {
        val orderId = closeComandaWithPayment(PaymentMethod.CASH)

        val payment = database.paymentDao().getForOrder(orderId).first().single()
        assertNull(payment.cashSessionId)
    }

    @Test
    fun comandaPaymentIsAttachedToTheCurrentlyOpenSession() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)
        val orderId = closeComandaWithPayment(PaymentMethod.CASH)

        val payment = database.paymentDao().getForOrder(orderId).first().single()
        assertEquals(sessionId, payment.cashSessionId)
    }

    @Test
    fun comandaPaymentIsNotAttachedToAPreviouslyClosedSession() = runBlocking {
        val closedId = cashRegisterRepository.openSession(0)
        cashRegisterRepository.closeSession(closedId, countedCashCents = 0)

        val orderId = closeComandaWithPayment(PaymentMethod.CASH)

        val payment = database.paymentDao().getForOrder(orderId).first().single()
        assertNull(payment.cashSessionId)
    }

    @Test
    fun comandaPaymentBelongsToTheSecondSessionAfterTheFirstIsClosed() = runBlocking {
        val firstId = cashRegisterRepository.openSession(0)
        cashRegisterRepository.closeSession(firstId, countedCashCents = 0)
        val secondId = cashRegisterRepository.openSession(0)

        val orderId = closeComandaWithPayment(PaymentMethod.CASH)

        val payment = database.paymentDao().getForOrder(orderId).first().single()
        assertEquals(secondId, payment.cashSessionId)
    }

    // ---------------------------------------------------------------------------------------
    // Closing
    // ---------------------------------------------------------------------------------------

    @Test
    fun closingAnOpenSessionMarksItClosedAndPersistsTheCountedAmount() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)
        val before = System.currentTimeMillis()

        cashRegisterRepository.closeSession(sessionId, countedCashCents = 500)
        val after = System.currentTimeMillis()

        val session = database.cashSessionDao().getById(sessionId)!!
        assertEquals(CashSessionStatus.CLOSED, session.status)
        assertEquals(500L, session.closingBalanceCents)
        assertNotNull(session.closedAt)
        assertTrue(session.closedAt!! in before..after)
    }

    @Test
    fun closingComputesExpectedCashAndDifferenceFromPersistedPayments() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(openingBalanceCents = 10_000)
        sell(PaymentMethod.CASH, quantity = 2.0) // 800 cents in cash
        sell(PaymentMethod.PIX) // received, but not drawer cash

        val summary = cashRegisterRepository.closeSession(sessionId, countedCashCents = 10_700)

        val expected = 10_000L + unitPriceCents * 2
        assertEquals(expected, summary.expectedCashCents)
        assertEquals(unitPriceCents * 3, summary.totalReceivedCents)
        assertEquals(10_700L - expected, summary.differenceCents)
    }

    @Test
    fun closingWithoutACountedAmountLeavesTheDifferenceUnset() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)

        val summary = cashRegisterRepository.closeSession(sessionId, countedCashCents = null)

        assertNull(summary.session.closingBalanceCents)
        assertNull(summary.differenceCents)
    }

    @Test
    fun cannotCloseASessionThatIsNotOpen() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)
        cashRegisterRepository.closeSession(sessionId, countedCashCents = 0)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { cashRegisterRepository.closeSession(sessionId, 0) }
        }
        Unit
    }

    @Test
    fun closingWithANegativeCountedAmountIsRejected() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { cashRegisterRepository.closeSession(sessionId, countedCashCents = -1) }
        }
        Unit
    }

    @Test
    fun afterClosingThereIsNoCurrentOpenSession() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)
        cashRegisterRepository.closeSession(sessionId, countedCashCents = 0)

        assertNull(cashRegisterRepository.observeOpenSession().first())
    }

    @Test
    fun aSecondSessionStartsWithNoTotalsLeakedFromTheFirst() = runBlocking {
        val firstId = cashRegisterRepository.openSession(0)
        sell(PaymentMethod.CASH, quantity = 5.0)
        cashRegisterRepository.closeSession(firstId, countedCashCents = unitPriceCents * 5)

        val secondId = cashRegisterRepository.openSession(openingBalanceCents = 1_000)

        val summary = cashRegisterRepository.currentSummary(secondId)
        assertTrue(summary.totalsByMethod.isEmpty())
        assertEquals(0L, summary.totalReceivedCents)
        assertEquals(1_000L, summary.expectedCashCents)
    }

    // ---------------------------------------------------------------------------------------
    // Sales vs. Fiado debt repayments
    // ---------------------------------------------------------------------------------------

    @Test
    fun salesAndDebtRepaymentsAreTotaledSeparatelyByMethod() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(openingBalanceCents = 0)

        // A regular cash sale of R$ 4,00.
        orderRepository.confirmQuickSale(
            lines = listOf(CartLine(productId, 1.0)),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
        )

        // A Fiado sale of R$ 4,00 for the same product — must NOT appear in sales totals.
        orderRepository.confirmQuickSale(
            lines = listOf(CartLine(productId, 1.0)),
            method = null,
            isFiado = true,
            customerId = customerId,
        )

        // A pre-existing debt gets paid off in cash during this session.
        val now = System.currentTimeMillis()
        val debtId = database.debtDao().insert(
            DebtEntity(customerId = customerId, originalAmountCents = 2_000, status = DebtStatus.OPEN, createdAt = now),
        )
        debtRepository.registerPayment(debtId, amountCents = 2_000, method = PaymentMethod.CASH, cashSessionId = sessionId)

        val summary = cashRegisterRepository.currentSummary(sessionId)
        assertEquals(1, summary.totalsByMethod.size)
        assertEquals(400L, summary.receivedCentsFor(PaymentMethod.CASH))

        val debtPaymentTotals = cashRegisterRepository.getDebtPaymentTotalsByMethod(sessionId)
        assertEquals(1, debtPaymentTotals.size)
        assertEquals(PaymentMethod.CASH, debtPaymentTotals.single().method)
        assertEquals(2_000L, debtPaymentTotals.single().totalCents)
    }
}
