package com.sethv.fintrack.core.common.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Single canonical money formatter for the whole app (UI, notifications,
 * exports). Replaces every divergent per-screen formatter so all currency
 * rendering is identical.
 *
 * Indian digit grouping ("12,34,567") is applied manually: desktop JVMs ignore
 * the "#,##,##0" secondary-group pattern while Android ICU honours it, so a
 * pattern/locale alone cannot produce identical output on both.
 * DecimalFormat instances are NOT thread-safe, hence one per thread.
 */
object Format {

    private val plainFormat: ThreadLocal<DecimalFormat> = object : ThreadLocal<DecimalFormat>() {
        override fun initialValue(): DecimalFormat =
            DecimalFormat("0.##", DecimalFormatSymbols(Locale("en", "IN")))
    }

    /** Returns e.g. "₹12,34,567.89" with rupee prefix and Indian grouping. */
    fun currency(amount: Double): String = "₹${formatAmount(amount)}"

    /** Currency with explicit sign: "+₹500" or "-₹500". */
    fun currencySigned(amount: Double): String =
        if (amount < 0) "-₹${formatAmount(kotlin.math.abs(amount))}" else "+₹${formatAmount(amount)}"

    private fun formatAmount(amount: Double): String {
        val formatted = requireNotNull(plainFormat.get()).format(amount)
        val fraction = formatted.substringAfter('.', "")
        val groupedInteger = groupIndianDigits(formatted.substringBefore('.'))
        return if (fraction.isEmpty()) groupedInteger else "$groupedInteger.$fraction"
    }

    private fun groupIndianDigits(digits: String): String {
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        var remaining = digits.dropLast(3)
        val leadingGroups = mutableListOf<String>()
        while (remaining.length > 2) {
            leadingGroups += remaining.takeLast(2)
            remaining = remaining.dropLast(2)
        }
        if (remaining.isNotEmpty()) leadingGroups += remaining
        return (leadingGroups.reversed() + lastThree).joinToString(",")
    }
}
