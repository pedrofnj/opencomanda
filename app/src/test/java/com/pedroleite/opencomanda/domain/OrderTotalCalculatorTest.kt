package com.pedroleite.opencomanda.domain

import com.pedroleite.opencomanda.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OrderTotalCalculatorTest {

    @Test
    fun `itemSubtotal multiplies unit price by quantity`() {
        val subtotal = OrderTotalCalculator.itemSubtotal(Money(500), 3.0)
        assertEquals(1500L, subtotal.minorUnits)
    }

    @Test
    fun `itemSubtotal supports fractional quantities`() {
        // e.g. 0.5 kg at R$ 40,00 per kg = R$ 20,00
        val subtotal = OrderTotalCalculator.itemSubtotal(Money(4000), 0.5)
        assertEquals(2000L, subtotal.minorUnits)
    }

    @Test
    fun `itemSubtotal rejects zero or negative quantity`() {
        assertThrows(IllegalArgumentException::class.java) {
            OrderTotalCalculator.itemSubtotal(Money(500), 0.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OrderTotalCalculator.itemSubtotal(Money(500), -1.0)
        }
    }

    @Test
    fun `orderTotal sums subtotals`() {
        val total = OrderTotalCalculator.orderTotal(listOf(Money(1000), Money(2500), Money(500)))
        assertEquals(4000L, total.minorUnits)
    }

    @Test
    fun `orderTotal of an empty list is zero`() {
        val total = OrderTotalCalculator.orderTotal(emptyList())
        assertEquals(0L, total.minorUnits)
    }
}
