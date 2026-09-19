package com.pedroleite.opencomanda.core

/** Safety ceiling for a single typed amount: R$ 99.999.999,99 — far beyond any real product price. */
private const val MAX_MONEY_INPUT_CENTS = 99_999_999_99L

/**
 * Parses raw text from a masked money field into a non-negative cent amount: every non-digit
 * character (minus signs, letters, extra separators, spaces...) is simply discarded, and the
 * remaining digits are read as the literal cent value (e.g. typing "1250" means R$ 12,50).
 *
 * This makes an invalid or negative amount structurally impossible to produce from this input —
 * there is no decimal separator to misinterpret and no way to type a minus sign — rather than
 * something we validate and reject after the fact.
 */
fun parseCentsInput(rawText: String): Long {
    val digitsOnly = rawText.filter(Char::isDigit)
    if (digitsOnly.isEmpty()) return 0L
    val value = digitsOnly.toLongOrNull() ?: return MAX_MONEY_INPUT_CENTS
    return value.coerceIn(0L, MAX_MONEY_INPUT_CENTS)
}
