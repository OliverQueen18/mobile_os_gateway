package com.osgateway.distributor.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formats integer amounts with a thin-space / non-breaking space thousands separator (fr-FR style).
 */
object AmountFormat {
    private val symbols = DecimalFormatSymbols(Locale.FRANCE).apply {
        groupingSeparator = ' '
    }
    private val formatter = DecimalFormat("#,###", symbols)

    fun formatDigits(digits: String): String {
        val cleaned = digits.filter { it.isDigit() }
        if (cleaned.isEmpty()) return ""
        val value = cleaned.toLongOrNull() ?: return cleaned
        return formatter.format(value)
    }

    fun digitsOnly(formatted: String): String = formatted.filter { it.isDigit() }

    fun toDoubleOrNull(formatted: String): Double? {
        val digits = digitsOnly(formatted)
        if (digits.isEmpty()) return null
        return digits.toDoubleOrNull()
    }
}
