package com.pedroleite.opencomanda.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * A monetary amount, stored as an exact integer count of the currency's minor units
 * (e.g. cents for BRL/USD) — never as [Float]/[Double], which cannot represent decimal
 * fractions exactly and accumulate rounding errors across additions.
 *
 * [Money] carries its own [currency] rather than assuming BRL: OpenComanda's first market
 * is Brazil, but the type itself must not make that permanent, since the app already
 * supports other locales and may run outside Brazil in the future.
 */
data class Money(
    val minorUnits: Long,
    val currency: Currency = AppCurrency.default,
) : Comparable<Money> {

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minorUnits + other.minorUnits, currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minorUnits - other.minorUnits, currency)
    }

    operator fun unaryMinus(): Money = Money(-minorUnits, currency)

    /** Multiplies by a (possibly fractional) quantity, rounding to the nearest minor unit. */
    fun times(quantity: Double): Money {
        val result = BigDecimal(minorUnits)
            .multiply(BigDecimal.valueOf(quantity))
            .setScale(0, RoundingMode.HALF_UP)
        return Money(result.toLong(), currency)
    }

    val isZero: Boolean get() = minorUnits == 0L
    val isPositive: Boolean get() = minorUnits > 0L
    val isNegative: Boolean get() = minorUnits < 0L

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    /** Formats this amount for display, using [locale] for grouping/decimal separators. */
    fun format(locale: Locale = Locale.getDefault()): String {
        val numberFormat = NumberFormat.getCurrencyInstance(locale)
        numberFormat.currency = currency
        val decimalAmount = BigDecimal(minorUnits).movePointLeft(currency.defaultFractionDigits)
        return numberFormat.format(decimalAmount)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Cannot operate on Money with different currencies: $currency vs ${other.currency}"
        }
    }

    companion object {
        fun zero(currency: Currency = AppCurrency.default): Money = Money(0L, currency)
    }
}
