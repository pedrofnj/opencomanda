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
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Fiado payment invariants are enforced in business logic (via
 * [DebtRepository]/[com.pedroleite.opencomanda.domain.DebtBalanceCalculator]), not just in the UI.
 */
@RunWith(AndroidJUnit4::class)
class DebtRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var debtRepository: DebtRepository
    private var debtId: Long = 0
    private var customerId: Long = 0

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        debtRepository = DebtRepository(database, database.debtDao(), database.debtPaymentDao())

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

    @Test
    fun partialPaymentMovesStatusToPartiallyPaid() = runBlocking {
        debtRepository.registerPayment(debtId, amountCents = 4_000, method = PaymentMethod.CASH, cashSessionId = null)

        val debt = database.debtDao().getById(debtId)!!
        assertEquals(DebtStatus.PARTIALLY_PAID, debt.status)

        val paid = database.debtPaymentDao().getTotalPaidForDebt(debtId)
        assertEquals(4_000L, paid)
    }

    @Test
    fun payingTheFullOutstandingBalanceMarksTheDebtPaid() = runBlocking {
        debtRepository.registerPayment(debtId, amountCents = 6_000, method = PaymentMethod.PIX, cashSessionId = null)
        debtRepository.registerPayment(debtId, amountCents = 4_000, method = PaymentMethod.CASH, cashSessionId = null)

        val debt = database.debtDao().getById(debtId)!!
        assertEquals(DebtStatus.PAID, debt.status)
    }

    @Test
    fun rejectsAZeroOrNegativePayment() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { debtRepository.registerPayment(debtId, 0, PaymentMethod.CASH, null) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { debtRepository.registerPayment(debtId, -500, PaymentMethod.CASH, null) }
        }
        Unit
    }

    @Test
    fun rejectsAPaymentThatWouldExceedTheOutstandingBalance() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { debtRepository.registerPayment(debtId, 10_001, PaymentMethod.CASH, null) }
        }
        Unit
    }

    @Test
    fun rejectsFurtherPaymentsOnceTheDebtIsFullyPaid() = runBlocking {
        debtRepository.registerPayment(debtId, 10_000, PaymentMethod.CASH, null)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { debtRepository.registerPayment(debtId, 1, PaymentMethod.CASH, null) }
        }
        Unit
    }

    @Test
    fun paymentHistoryIsPreserved() = runBlocking {
        debtRepository.registerPayment(debtId, 3_000, PaymentMethod.CASH, null)
        debtRepository.registerPayment(debtId, 2_000, PaymentMethod.PIX, null)

        val history = debtRepository.getPaymentsForDebt(debtId).first()
        assertEquals(2, history.size)
        assertEquals(5_000L, history.sumOf { it.amountCents })
    }
}
