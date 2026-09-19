package com.pedroleite.opencomanda.domain

import com.pedroleite.opencomanda.core.AppCurrency
import com.pedroleite.opencomanda.core.Money
import java.util.Currency

/**
 * Pure, Room/Compose-free rules for computing order/comanda totals from their items.
 *
 * Order totals are intentionally never persisted as a single mutable column (see
 * [com.pedroleite.opencomanda.data.local.entity.OrderEntity]): they are always derived
 * from item subtotals, computed here or via an equivalent SQL aggregate.
 */
object OrderTotalCalculator {

    /** Subtotal for one line: unit price times (possibly fractional) quantity, rounded to the cent. */
    fun itemSubtotal(unitPrice: Money, quantity: Double): Money {
        require(quantity > 0) { "Quantity must be greater than zero" }
        return unitPrice.times(quantity)
    }

    /** Sums already-computed subtotals into an order total. */
    fun orderTotal(subtotals: List<Money>, currency: Currency = AppCurrency.default): Money =
        subtotals.fold(Money.zero(currency)) { acc, subtotal -> acc + subtotal }
}
