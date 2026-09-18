package com.pedroleite.opencomanda.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSaleCartTest {

    @Test
    fun emptyCartTotalIsZero() {
        assertEquals(0L, QuickSaleCart.totalCents(emptyList()))
    }

    @Test
    fun addingAProductCreatesALineWithQuantityOne() {
        val lines = QuickSaleCart.add(emptyList(), productId = 1, productName = "Espetinho", unitPriceCents = 1250)

        assertEquals(1, lines.size)
        assertEquals(1.0, lines.single().quantity, 0.0)
        assertEquals("Espetinho", lines.single().productName)
    }

    @Test
    fun addingTheSameProductAgainIncrementsItsQuantityInsteadOfDuplicating() {
        var lines = QuickSaleCart.add(emptyList(), 1, "Espetinho", 1250)
        lines = QuickSaleCart.add(lines, 1, "Espetinho", 1250)

        assertEquals(1, lines.size)
        assertEquals(2.0, lines.single().quantity, 0.0)
    }

    @Test
    fun addingADifferentProductCreatesASecondLine() {
        var lines = QuickSaleCart.add(emptyList(), 1, "Espetinho", 1250)
        lines = QuickSaleCart.add(lines, 2, "Coca-Cola", 600)

        assertEquals(2, lines.size)
    }

    @Test
    fun incrementIncreasesQuantityByOne() {
        var lines = QuickSaleCart.add(emptyList(), 1, "Espetinho", 1250)
        lines = QuickSaleCart.increment(lines, 1)

        assertEquals(2.0, lines.single().quantity, 0.0)
    }

    @Test
    fun decrementDecreasesQuantityByOne() {
        var lines = QuickSaleCart.add(emptyList(), 1, "Espetinho", 1250)
        lines = QuickSaleCart.increment(lines, 1)
        lines = QuickSaleCart.decrement(lines, 1)

        assertEquals(1.0, lines.single().quantity, 0.0)
    }

    @Test
    fun decrementingTheFinalUnitRemovesTheLine() {
        var lines = QuickSaleCart.add(emptyList(), 1, "Espetinho", 1250)
        lines = QuickSaleCart.decrement(lines, 1)

        assertTrue(lines.isEmpty())
    }

    @Test
    fun removeDropsTheLineRegardlessOfQuantity() {
        var lines = QuickSaleCart.add(emptyList(), 1, "Espetinho", 1250)
        lines = QuickSaleCart.increment(lines, 1)
        lines = QuickSaleCart.remove(lines, 1)

        assertTrue(lines.isEmpty())
    }

    @Test
    fun incrementOrDecrementOnAnUnknownProductIsANoOp() {
        val lines = QuickSaleCart.add(emptyList(), 1, "Espetinho", 1250)

        assertEquals(lines, QuickSaleCart.increment(lines, 999))
        assertEquals(lines, QuickSaleCart.decrement(lines, 999))
    }

    @Test
    fun lineSubtotalIsUnitPriceTimesQuantity() {
        val line = QuickSaleCartLine(productId = 1, productName = "Espetinho", unitPriceCents = 1250, quantity = 2.0)

        assertEquals(2500L, line.subtotalCents)
    }

    @Test
    fun cartTotalSumsAllLineSubtotals() {
        var lines = QuickSaleCart.add(emptyList(), 1, "Espetinho", 1250)
        lines = QuickSaleCart.increment(lines, 1) // 2 x 1250 = 2500
        lines = QuickSaleCart.add(lines, 2, "Coca-Cola", 600) // 1 x 600 = 600

        assertEquals(3100L, QuickSaleCart.totalCents(lines))
    }

    @Test
    fun totalIsExactForPricesThatWouldDriftUnderFloatingPointMath() {
        // 3 x 0.10 in naive Double math is 0.30000000000000004 — cents must stay exact Longs.
        var lines = QuickSaleCart.add(emptyList(), 1, "Item", unitPriceCents = 10)
        lines = QuickSaleCart.increment(lines, 1)
        lines = QuickSaleCart.increment(lines, 1)

        assertEquals(30L, QuickSaleCart.totalCents(lines))
    }
}
