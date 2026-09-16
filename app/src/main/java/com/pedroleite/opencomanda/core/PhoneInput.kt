package com.pedroleite.opencomanda.core

/**
 * Keeps only the digits from a phone number, so search can match regardless of the punctuation
 * or spacing the number was stored or typed with — e.g. both "(62) 99999-1234" and "62999991234"
 * normalize to "62999991234". Deliberately not a full phone-number parser/formatter: a customer
 * phone here is operational contact info, not a validated identity.
 */
fun normalizePhoneDigits(raw: String): String = raw.filter { it.isDigit() }
