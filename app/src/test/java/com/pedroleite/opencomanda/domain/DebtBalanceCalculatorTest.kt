package com.pedroleite.opencomanda.domain

import com.pedroleite.opencomanda.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DebtBalanceCalculatorTest {

    @Test
    fun `outstandingBalance subtracts payments from the original amount`() {
        val outstanding = DebtBalanceCalculator.outstandingBalance(
            originalAmount = Money(10_000),
            paymentsSoFar = listOf(Money(3_000), Money(2_000)),
        )
        assertEquals(5_000L, outstanding.minorUnits)
    }

    @Test
    fun `outstandingBalance with no payments equals the original amount`() {
        val outstanding = DebtBalanceCalculator.outstandingBalance(Money(10_000), emptyList())
        assertEquals(10_000L, outstanding.minorUnits)
    }

    @Test
    fun `validatePayment rejects amount not greater than zero`() {
        assertThrows(IllegalArgumentException::class.java) {
            DebtBalanceCalculator.validatePayment(amount = Money(0), outstanding = Money(1_000))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DebtBalanceCalculator.validatePayment(amount = Money(-100), outstanding = Money(1_000))
        }
    }

    @Test
    fun `validatePayment rejects amount exceeding the outstanding balance`() {
        assertThrows(IllegalArgumentException::class.java) {
            DebtBalanceCalculator.validatePayment(amount = Money(1_001), outstanding = Money(1_000))
        }
    }

    @Test
    fun `validatePayment rejects any payment when there is no outstanding balance`() {
        assertThrows(IllegalArgumentException::class.java) {
            DebtBalanceCalculator.validatePayment(amount = Money(100), outstanding = Money(0))
        }
    }

    @Test
    fun `validatePayment returns PARTIALLY_PAID when balance remains after payment`() {
        val status = DebtBalanceCalculator.validatePayment(amount = Money(400), outstanding = Money(1_000))
        assertEquals(DebtStatus.PARTIALLY_PAID, status)
    }

    @Test
    fun `validatePayment returns PAID when the payment exactly settles the balance`() {
        val status = DebtBalanceCalculator.validatePayment(amount = Money(1_000), outstanding = Money(1_000))
        assertEquals(DebtStatus.PAID, status)
    }
}
