package com.osgateway.shared.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MoneyFormat {
    private val symbols = DecimalFormatSymbols(Locale.FRANCE).apply {
        groupingSeparator = ' '
        decimalSeparator = ','
    }

    private val amountFormat = DecimalFormat("#,##0.##", symbols)
    private val integerFormat = DecimalFormat("#,##0", symbols)

    fun format(value: Number?, decimals: Boolean = true): String {
        val n = value?.toDouble() ?: 0.0
        return if (decimals) amountFormat.format(n) else integerFormat.format(n)
    }

    fun formatXof(value: Number?, decimals: Boolean = false): String =
        "${format(value, decimals)} XOF"
}
