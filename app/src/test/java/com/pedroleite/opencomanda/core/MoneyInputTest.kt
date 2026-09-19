package com.pedroleite.opencomanda.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyInputTest {

    @Test
    fun `digits are read directly as cents`() {
        assertEquals(1250L, parseCentsInput("1250"))
        assertEquals(1L, parseCentsInput("1"))
        assertEquals(0L, parseCentsInput("0"))
    }

    @Test
    fun `formatted currency text round-trips back to the same cents`() {
        val cents = 1250L
        @Suppress("DEPRECATION")
        val ptBr = java.util.Locale("pt", "BR")
        val formatted = Money(cents).format(ptBr)
        assertEquals(cents, parseCentsInput(formatted))
    }

    @Test
    fun `non-digit characters are discarded, never produce a negative amount`() {
        assertEquals(50L, parseCentsInput("-50"))
        assertEquals(1250L, parseCentsInput("R$ 12,50"))
        assertTrue(parseCentsInput("abc") >= 0L)
    }

    @Test
    fun `blank or non-numeric input is exactly zero, not silently something else`() {
        assertEquals(0L, parseCentsInput(""))
        assertEquals(0L, parseCentsInput("   "))
        assertEquals(0L, parseCentsInput("abc"))
        assertEquals(0L, parseCentsInput("R$"))
    }

    @Test
    fun `absurdly long digit sequences are capped, never overflow or go negative`() {
        val result = parseCentsInput("99999999999999999999999999")
        assertTrue(result in 0L..99_999_999_99L)
    }
}
