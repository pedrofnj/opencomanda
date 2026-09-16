package com.pedroleite.opencomanda.core

import java.text.NumberFormat
import java.util.Locale

/**
 * Parses a stock quantity typed by the user (e.g. product stock, which may be fractional — see
 * [com.pedroleite.opencomanda.data.local.entity.ProductEntity.stockQuantity]). Accepts both
 * "," and "." as the decimal separator, since either may appear depending on the user's keyboard.
 * Returns null for blank, malformed, negative, or non-finite input — callers must treat that as
 * a validation error rather than silently falling back to zero.
 */
fun parseStockQuantity(rawText: String): Double? {
    val normalized = rawText.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    val value = normalized.toDoubleOrNull() ?: return null
    return value.takeIf { it.isFinite() && it >= 0.0 }
}

/**
 * Formats a stock quantity for display, using [locale]'s grouping/decimal conventions and
 * trimming an unnecessary ".0" (12.0 -> "12", 12.5 -> "12,5" in pt-BR).
 */
fun formatQuantity(value: Double, locale: Locale = Locale.getDefault()): String {
    val format = NumberFormat.getNumberInstance(locale)
    format.maximumFractionDigits = 2
    format.minimumFractionDigits = 0
    return format.format(value)
}
