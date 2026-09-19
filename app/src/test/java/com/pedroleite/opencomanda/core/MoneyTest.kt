package com.pedroleite.opencomanda.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Currency
import java.util.Locale

class MoneyTest {

    @Test
    fun `plus adds minor units`() {
        val result = Money(1000) + Money(250)
        assertEquals(1250L, result.minorUnits)
    }

    @Test
    fun `minus subtracts minor units`() {
        val result = Money(1000) - Money(250)
        assertEquals(750L, result.minorUnits)
    }

    @Test
    fun `plus with different currencies throws`() {
        val brl = Money(1000, Currency.getInstance("BRL"))
        val usd = Money(1000, Currency.getInstance("USD"))
        assertThrows(IllegalArgumentException::class.java) { brl + usd }
    }

    @Test
    fun `times rounds to nearest minor unit`() {
        // 333 cents * 3 = 999 cents exactly
        assertEquals(999L, Money(333).times(3.0).minorUnits)
        // 100 cents * 0.5 = 50 cents exactly
        assertEquals(50L, Money(100).times(0.5).minorUnits)
        // 3 cents * (1/3) would be 1 cent (rounded), not truncated to 0 or left as a fraction
        assertEquals(1L, Money(3).times(1.0 / 3.0).minorUnits)
    }

    @Test
    fun `isZero isPositive isNegative reflect sign`() {
        assertTrue(Money(0).isZero)
        assertFalse(Money(0).isPositive)
        assertFalse(Money(0).isNegative)

        assertTrue(Money(10).isPositive)
        assertFalse(Money(10).isZero)

        assertTrue(Money(-10).isNegative)
    }

    @Test
    fun `compareTo orders by minor units within same currency`() {
        assertTrue(Money(100) < Money(200))
        assertTrue(Money(200) > Money(100))
        assertEquals(0, Money(100).compareTo(Money(100)))
    }

    @Test
    fun `format renders using the given locale's currency conventions`() {
        val amount = Money(1250, Currency.getInstance("BRL"))
        @Suppress("DEPRECATION")
        val ptBr = Locale("pt", "BR")
        val ptBrFormatted = amount.format(ptBr)
        assertTrue("expected a decimal amount of 12,50 in $ptBrFormatted", ptBrFormatted.contains("12,50"))

        val usAmount = Money(1250, Currency.getInstance("USD"))
        val usFormatted = usAmount.format(Locale.US)
        assertTrue("expected a decimal amount of 12.50 in $usFormatted", usFormatted.contains("12.50"))
    }

    @Test
    fun `zero returns a zero amount in the requested currency`() {
        val zero = Money.zero(Currency.getInstance("USD"))
        assertEquals(0L, zero.minorUnits)
        assertEquals(Currency.getInstance("USD"), zero.currency)
    }
}
