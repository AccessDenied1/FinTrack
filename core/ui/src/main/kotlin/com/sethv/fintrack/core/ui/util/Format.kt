package com.sethv.fintrack.core.ui.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Single canonical money formatter. Replaces the 5 divergent formatters
 * (TransactionItem, AmountDisplay, ExpenseListScreen, HomeScreen, NetWorthScreen,
 * TransactionNotifierImpl) so all currency rendering is consistent.
 */
object Format {

    private val inr: NumberFormat = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }

    /** Returns e.g. "₹1,234.50" with rupee prefix. */
    fun currency(amount: Double): String = "₹${inr.format(amount)}"

    /** Currency with explicit sign: "+₹500" or "-₹500". */
    fun currencySigned(amount: Double): String =
        if (amount < 0) "-₹${inr.format(kotlin.math.abs(amount))}" else "+₹${inr.format(amount)}"
}