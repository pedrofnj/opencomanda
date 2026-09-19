package com.pedroleite.opencomanda.domain

import com.pedroleite.opencomanda.core.Money

/**
 * One product's quantity in an in-progress Quick Sale cart, before persistence. Carries only
 * what pricing/display needs — not a full ProductEntity — so cart arithmetic here stays plain
 * Kotlin, testable without Room/Compose/Android.
 *
 * [unitPriceCents] is captured when the line is first added, exactly like an open Comanda's
 * OrderItem snapshot (see [com.pedroleite.opencomanda.data.local.entity.OrderItemEntity]) — it
 * is what the operator sees while building the sale. The actual persisted sale re-reads the
 * product's current price at confirmation time (see
 * [com.pedroleite.opencomanda.data.repository.OrderRepository.confirmQuickSale]), so this is a
 * display snapshot, not the final financial record.
 */
data class QuickSaleCartLine(
    val productId: Long,
    val productName: String,
    val unitPriceCents: Long,
    val quantity: Double,
) {
    val subtotalCents: Long
        get() = OrderTotalCalculator.itemSubtotal(Money(unitPriceCents), quantity).minorUnits
}

/**
 * Pure mutation rules for a Quick Sale cart: adding a product increments its existing line
 * rather than duplicating it, and decrementing a line to zero removes it — a cart never holds
 * a zero-quantity row.
 */
object QuickSaleCart {

    fun add(lines: List<QuickSaleCartLine>, productId: Long, productName: String, unitPriceCents: Long): List<QuickSaleCartLine> {
        val existing = lines.find { it.productId == productId }
        val newLine = QuickSaleCartLine(productId, productName, unitPriceCents, (existing?.quantity ?: 0.0) + 1.0)
        return if (existing != null) {
            lines.map { if (it.productId == productId) newLine else it }
        } else {
            lines + newLine
        }
    }

    fun increment(lines: List<QuickSaleCartLine>, productId: Long): List<QuickSaleCartLine> =
        lines.map { if (it.productId == productId) it.copy(quantity = it.quantity + 1.0) else it }

    /** Decrementing to zero (or below) removes the line rather than leaving it at zero. */
    fun decrement(lines: List<QuickSaleCartLine>, productId: Long): List<QuickSaleCartLine> =
        lines.mapNotNull { line ->
            if (line.productId != productId) return@mapNotNull line
            val newQuantity = line.quantity - 1.0
            if (newQuantity > 0.0) line.copy(quantity = newQuantity) else null
        }

    fun remove(lines: List<QuickSaleCartLine>, productId: Long): List<QuickSaleCartLine> =
        lines.filterNot { it.productId == productId }

    /** Sums line subtotals (each already rounded to the cent) into the sale total. */
    fun totalCents(lines: List<QuickSaleCartLine>): Long =
        OrderTotalCalculator.orderTotal(lines.map { Money(it.subtotalCents) }).minorUnits
}
