package com.pedroleite.opencomanda.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class QuantityInputTest {

    @Test
    fun `parses a plain integer`() {
        assertEquals(12.0, parseStockQuantity("12"))
    }

    @Test
    fun `accepts both comma and dot as the decimal separator`() {
        assertEquals(12.5, parseStockQuantity("12,5"))
        assertEquals(12.5, parseStockQuantity("12.5"))
    }

    @Test
    fun `rejects blank input rather than defaulting to zero`() {
        assertNull(parseStockQuantity(""))
        assertNull(parseStockQuantity("   "))
    }

    @Test
    fun `rejects malformed input`() {
        assertNull(parseStockQuantity("abc"))
        assertNull(parseStockQuantity("1,2,3"))
        assertNull(parseStockQuantity("--5"))
    }

    @Test
    fun `rejects negative quantities`() {
        assertNull(parseStockQuantity("-5"))
        assertNull(parseStockQuantity("-0.5"))
    }

    @Test
    fun `rejects non-finite values`() {
        assertNull(parseStockQuantity("Infinity"))
        assertNull(parseStockQuantity("NaN"))
    }

    @Test
    fun `zero is a valid quantity`() {
        assertEquals(0.0, parseStockQuantity("0"))
    }

    @Test
    fun `formatQuantity trims a whole number's decimal zero`() {
        assertEquals("12", formatQuantity(12.0, Locale.US))
    }

    @Test
    fun `formatQuantity keeps a meaningful fraction using the locale's separator`() {
        assertEquals("12.5", formatQuantity(12.5, Locale.US))
        @Suppress("DEPRECATION")
        val ptBr = Locale("pt", "BR")
        assertEquals("12,5", formatQuantity(12.5, ptBr))
    }
}
