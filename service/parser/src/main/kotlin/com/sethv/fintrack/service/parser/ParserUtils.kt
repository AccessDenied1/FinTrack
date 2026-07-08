package com.sethv.fintrack.service.parser

import com.sethv.fintrack.core.model.TransactionType

internal object ParserUtils {

    // Supports:
    //   "Rs.500", "Rs 500", "Rs 1,500.00"
    //   "INR 500", "INR 2,500.50"
    //   "₹500", "₹ 500", "₹1,500.00"   (U+20B9)
    private val AMOUNT_PATTERN = Regex(
        """(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    private val ACCOUNT_SUFFIX_PATTERN = Regex(
        """(?:a/c|acct|account)\s*(?:no\.?\s*)?[\*xX]*(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    fun parseAmount(text: String): Double? {
        val match = AMOUNT_PATTERN.find(text) ?: return null
        return match.groupValues[1].replace(",", "").toDoubleOrNull()
    }

    fun detectTransactionType(text: String): TransactionType {
        val lower = text.lowercase()
        return when {
            lower.contains("credited") ||
                lower.contains("received") ||
                lower.contains("refund") -> TransactionType.CREDIT
            lower.contains("debited") ||
                lower.contains("paid") ||
                lower.contains("spent") ||
                lower.contains("sent") -> TransactionType.DEBIT
            else -> TransactionType.DEBIT
        }
    }

    fun extractMerchantAfterKeyword(text: String, keyword: String): String? {
        val pattern = Regex(
            """$keyword\s+(.+?)(?:\s+on|\s+ref|\s+upi|\s+avl|\s+bal|\s+via|\s+at|\s+from|\.)""",
            RegexOption.IGNORE_CASE,
        )
        return pattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun extractMerchantFromUpi(text: String): String? {
        val upiPattern = Regex(
            """UPI/[\d]+/(.+?)(?:/\d{2}-\d{2}-\d{2}|/[\d]+|\s|$)""",
            RegexOption.IGNORE_CASE,
        )
        return upiPattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun extractAccountSuffix(text: String): String? =
        ACCOUNT_SUFFIX_PATTERN.find(text)?.groupValues?.get(1)

    fun senderContainsAny(sender: String, vararg tokens: String): Boolean {
        val upperSender = sender.uppercase()
        return tokens.any { upperSender.contains(it.uppercase()) }
    }

    /**
     * Loose gate for "this looks like a transaction notification".
     * Keep the set tight so we don't pick up order confirmations, OTPs, marketing.
     */
    fun looksLikeTransactionSms(body: String): Boolean =
        AMOUNT_PATTERN.containsMatchIn(body) &&
            (
                body.contains("debited", ignoreCase = true) ||
                    body.contains("credited", ignoreCase = true) ||
                    body.contains("paid", ignoreCase = true) ||
                    body.contains("spent", ignoreCase = true) ||
                    body.contains("received", ignoreCase = true) ||
                    body.contains("sent", ignoreCase = true) ||
                    body.contains("UPI", ignoreCase = true) ||
                    body.contains("txn", ignoreCase = true) ||
                    body.contains("transaction", ignoreCase = true)
                )
}