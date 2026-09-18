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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies cash-session rules: only one session open at a time, and sales received are
 * kept separate from Fiado debt repayments in the per-method totals (a Fiado sale itself
 * must never appear as money received).
 */
@RunWith(AndroidJUnit4::class)
class CashRegisterRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var cashRegisterRepository: CashRegisterRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var debtRepository: DebtRepository

    private var productId: Long = 0
    private var customerId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        cashRegisterRepository = CashRegisterRepository(
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
        )
        debtRepository = DebtRepository(database, database.debtDao(), database.debtPaymentDao())

        val now = System.currentTimeMillis()
        productId = database.productDao().insert(
            ProductEntity(name = "Agua", priceCents = 400, createdAt = now, updatedAt = now),
        )
        customerId = database.customerDao().insert(CustomerEntity(name = "Carlos", createdAt = now, updatedAt = now))
    }

    @After
    fun tearDown() {
        database.close()
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
    fun cannotCloseASessionThatIsNotOpen() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(0)
        cashRegisterRepository.closeSession(sessionId, closingBalanceCents = 0)

        assertThrows(IllegalStateException::class.java) {
            runBlocking { cashRegisterRepository.closeSession(sessionId, 0) }
        }
        Unit
    }

    @Test
    fun salesAndDebtRepaymentsAreTotaledSeparatelyByMethod() = runBlocking {
        val sessionId = cashRegisterRepository.openSession(openingBalanceCents = 0)

        // A regular cash sale of R$ 4,00.
        orderRepository.confirmQuickSale(
            lines = listOf(CartLine(productId, 1.0)),
            method = PaymentMethod.CASH,
            isFiado = false,
            customerId = null,
            cashSessionId = sessionId,
        )

        // A Fiado sale of R$ 4,00 for the same product — must NOT appear in sales totals.
        orderRepository.confirmQuickSale(
            lines = listOf(CartLine(productId, 1.0)),
            method = null,
            isFiado = true,
            customerId = customerId,
            cashSessionId = sessionId,
        )

        // A pre-existing debt gets paid off in cash during this session.
        val now = System.currentTimeMillis()
        val debtId = database.debtDao().insert(
            DebtEntity(customerId = customerId, originalAmountCents = 2_000, status = DebtStatus.OPEN, createdAt = now),
        )
        debtRepository.registerPayment(debtId, amountCents = 2_000, method = PaymentMethod.CASH, cashSessionId = sessionId)

        val salesTotals = cashRegisterRepository.getSalesTotalsByMethod(sessionId)
        assertEquals(1, salesTotals.size)
        assertEquals(PaymentMethod.CASH, salesTotals.single().method)
        assertEquals(400L, salesTotals.single().totalCents)

        val debtPaymentTotals = cashRegisterRepository.getDebtPaymentTotalsByMethod(sessionId)
        assertEquals(1, debtPaymentTotals.size)
        assertEquals(PaymentMethod.CASH, debtPaymentTotals.single().method)
        assertEquals(2_000L, debtPaymentTotals.single().totalCents)
    }
}
