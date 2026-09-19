package com.pedroleite.opencomanda.core

import java.util.Currency

/**
 * The application's current default currency, used wherever an amount doesn't yet carry
 * its own explicit currency (e.g. Room entities storing raw minor-unit [Long] columns).
 *
 * OpenComanda targets Brazil first, but this is intentionally the single seam for that:
 * changing [default] does not require touching [Money] or any monetary arithmetic, since
 * [Money] itself is currency-aware rather than hardcoded to BRL.
 */
object AppCurrency {
    val default: Currency = Currency.getInstance("BRL")
}
