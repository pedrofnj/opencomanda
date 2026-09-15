package com.pedroleite.opencomanda.domain

import com.pedroleite.opencomanda.core.Money

/**
 * Pure, Room/Compose-free rules for validating and applying Fiado (customer debt) payments.
 * These invariants are enforced here — in business logic — and must not rely on UI-only
 * validation:
 *  - a payment amount must be greater than zero
 *  - a payment cannot exceed the debt's current outstanding balance
 *  - a debt with no outstanding balance cannot receive further payments
 *  - once the outstanding balance reaches zero, the debt is fully [DebtStatus.PAID]
 */
object DebtBalanceCalculator {

    /** Outstanding balance = original amount minus all payments registered so far. */
    fun outstandingBalance(originalAmount: Money, paymentsSoFar: List<Money>): Money {
        val paid = paymentsSoFar.fold(Money.zero(originalAmount.currency)) { acc, payment -> acc + payment }
        return originalAmount - paid
    }

    /**
     * Validates [amount] against [outstanding] and returns the [DebtStatus] the debt should
     * transition to if the payment is accepted.
     *
     * @throws IllegalArgumentException if the payment is invalid.
     */
    fun validatePayment(amount: Money, outstanding: Money): DebtStatus {
        require(outstanding.isPositive) {
            "Debt has no outstanding balance; it cannot receive further payments"
        }
        require(amount.isPositive) { "Payment amount must be greater than zero" }
        require(amount <= outstanding) {
            "Payment (${amount.minorUnits}) cannot exceed the outstanding balance (${outstanding.minorUnits})"
        }
        val remaining = outstanding - amount
        return if (remaining.isZero) DebtStatus.PAID else DebtStatus.PARTIALLY_PAID
    }
}
