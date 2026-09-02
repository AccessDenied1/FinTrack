package com.sethv.fintrack.service.parser

import com.sethv.fintrack.core.model.TransactionType

internal object ParserUtils {

    // Number formats accepted (Indian + western grouping, optional paise):
    //   500 | 1,500.00 | 1,00,000 | 12,34,567.89 | 100,000
    private const val NUMBER_PATTERN =
        """(?:\d{1,2}(?:,\d{2})*,\d{3}|\d{1,3}(?:,\d{3})+|\d+)(?:\.\d{1,2})?"""

    // Supports:
    //   "Rs.500", "Rs 500", "Rs 1,500.00"
    //   "INR 500", "INR 2,500.50"
    //   "₹500", "₹ 500", "₹12,34,567.89"   (U+20B9)
    private val AMOUNT_PATTERN = Regex(
        """(?:Rs\.?|INR|₹)\s*($NUMBER_PATTERN)""",
        RegexOption.IGNORE_CASE,
    )

    private val ACCOUNT_SUFFIX_PATTERN = Regex(
        """(?:a/c|acct|account)\s*(?:no\.?\s*)?[\*xX]*(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    // Verbs that mark the transaction amount itself ("debited for Rs.500",
    // "Rs 350 spent"). Used to anchor amount extraction so balances/limits
    // quoted elsewhere in the SMS never shadow the real amount.
    private val TXN_VERB_PATTERN = Regex(
        """\b(debited?|debits|credited?|credits|paid|pays|spent|spends|sent|sends|received|withdrawn|withdraws|transferred|transfers|purchased?|charged)\b""",
        RegexOption.IGNORE_CASE,
    )

    // Wording that introduces a NON-transaction figure (balance/limit/outstanding).
    private val BALANCE_CONTEXT_PATTERN = Regex(
        """\b(avl|avail(?:able)?|bal(?:ance)?|limit|outstanding|closing)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val DEBIT_HINTS_PATTERN = Regex(
        """\b(debited?|spent|paid|sent|withdrawn?|purchased?|charged)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val CREDIT_HINTS_PATTERN = Regex(
        """\b(credited?|deposited|received|refunds?|reversed?)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val OTP_PATTERN = Regex(
        """\b(o\.t\.p\.?|otp|one\s*time\s*(?:password|pin))\b""",
        RegexOption.IGNORE_CASE,
    )

    fun parseAmount(text: String): Double? {
        val matches = AMOUNT_PATTERN.findAll(text).toList()
        if (matches.isEmpty()) return null

        // Prefer an amount sitting next to a transaction verb so quoted
        // balances / credit limits never shadow the real transaction amount.
        for (match in matches) {
            val before = text.substring((match.range.first - 30).coerceAtLeast(0), match.range.first)
            val after = text.substring(
                match.range.last + 1,
                minOf(match.range.last + 16, text.length),
            )
            if (isBalanceIntroduced(before)) continue
            if (TXN_VERB_PATTERN.containsMatchIn(before) || TXN_VERB_PATTERN.containsMatchIn(after)) {
                return amountValue(match)
            }
        }

        // No verb-adjacent candidate: fall back to the first amount that is not
        // introduced by balance wording; else the first amount at all.
        return amountValue(
            matches.firstOrNull { match ->
                val before = text.substring((match.range.first - 30).coerceAtLeast(0), match.range.first)
                !isBalanceIntroduced(before)
            } ?: matches.first(),
        )
    }

    /**
     * True when the wording nearest to the amount (inside the preceding
     * window) is balance/limit context rather than a transaction verb.
     * Nearest-keyword-wins: a fixed char window alone misfires when a real
     * txn amount sits close after a balance figure ("...Avl limit Rs.50,000.
     * Spent Rs.2,000..." — the window behind "Rs.2,000" still contains
     * "limit", but the NEAREST keyword is "Spent").
     */
    private fun isBalanceIntroduced(beforeText: String): Boolean {
        val lastBalanceIndex = BALANCE_CONTEXT_PATTERN.findAll(beforeText)
            .maxOfOrNull { it.range.first } ?: return false
        val lastVerbIndex = TXN_VERB_PATTERN.findAll(beforeText)
            .maxOfOrNull { it.range.first } ?: -1
        return lastBalanceIndex > lastVerbIndex
    }

    /**
     * DEBIT hints are checked before CREDIT hints: bank SMS for failed UPI/
     * card transactions routinely contain both ("...debited... refund will be
     * initiated"), and the debit of the attempt is the actual ledger event.
     */
    fun detectTransactionType(text: String): TransactionType = when {
        DEBIT_HINTS_PATTERN.containsMatchIn(text) -> TransactionType.DEBIT
        CREDIT_HINTS_PATTERN.containsMatchIn(text) -> TransactionType.CREDIT
        else -> TransactionType.DEBIT
    }

    fun extractMerchantAfterKeyword(text: String, keyword: String): String? {
        val pattern = Regex(
            """\b${Regex.escape(keyword)}\s+(\S.*?)(?=\s+(?:on|ref|upi|avl|bal|via|at|from)\b|[.,;!]|$)""",
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
     * OTP authorization requests WITH an amount ("approve txn of Rs.9,999") are
     * explicitly rejected — they are not completed transactions.
     */
    fun looksLikeTransactionSms(body: String): Boolean =
        !OTP_PATTERN.containsMatchIn(body) &&
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

    private fun amountValue(match: MatchResult): Double? =
        match.groupValues[1].replace(",", "").toDoubleOrNull()

    // ------------------------------------------------------------------
    // Credit-card specific helpers
    // ------------------------------------------------------------------

    /** Card references: XX1234, ****1234, CC-1234, "card ending 1234". */
    private val CARD_LAST4_PATTERN = Regex(
        """(?:card|cc)\s*(?:no\.?\s*)?(?:ending(?:\s+in)?\s*)?[\*xX\-]{2,6}\s*(\d{4})""" +
            """|(?:card|cc)\s+ending\s+(?:in\s+)?(\d{4})""",
        RegexOption.IGNORE_CASE,
    )

    fun extractCardLast4(text: String): String? {
        val match = CARD_LAST4_PATTERN.find(text) ?: return null
        return match.groupValues[1].takeIf { it.length == 4 }
            ?: match.groupValues[2].takeIf { it.length == 4 }
    }

    private val MONTH_BY_NAME: Map<String, Int> = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    private val CALENDAR_DATE_PATTERN = Regex(
        """(\d{1,2})\s*[-/\.\s]\s*([A-Za-z]{3,9}|\d{1,2})(?:\s*[-/\.\s]\s*(\d{2,4}))?""",
    )

    /**
     * Parses a date appearing near the phrase "due date" (or "payment due").
     * Supported shapes: "15-Aug-26", "15 Aug 2026", "15/08/2026", "05-Aug", "15.08.26".
     * Year-less dates are resolved to the occurrence nearest [smsTimeMillis],
     * since statements arrive days-to-weeks before the due date.
     */
    fun extractDueDate(text: String, smsTimeMillis: Long): Long? {
        val lower = text.lowercase()
        val dueDateIdx = lower.indexOf("due date")
        val dueIdx = if (dueDateIdx >= 0) dueDateIdx else lower.indexOf("payment due")
        if (dueIdx < 0) return null
        val window = text.substring(dueIdx, minOf(dueIdx + 80, text.length))
        val match = CALENDAR_DATE_PATTERN.find(window) ?: return null

        val day = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        val monthToken = match.groupValues[2]
        val month = monthToken.toIntOrNull()?.takeIf { it in 1..12 }
            ?: MONTH_BY_NAME[monthToken.take(3).lowercase()]
            ?: return null
        val rawYear = match.groupValues[3].toIntOrNull()

        val zone = java.time.ZoneId.systemDefault()
        val smsDate = java.time.Instant.ofEpochMilli(smsTimeMillis).atZone(zone).toLocalDate()
        val year = when {
            rawYear == null -> smsDate.year
            rawYear > 100 -> rawYear
            rawYear < 70 -> 2000 + rawYear
            else -> 1900 + rawYear
        }

        var date = runCatching { java.time.LocalDate.of(year, month, day) }.getOrNull() ?: return null
        if (rawYear == null) {
            if (date.isBefore(smsDate.minusDays(20))) date = date.plusYears(1)
            else if (date.isAfter(smsDate.plusDays(300))) date = date.minusYears(1)
        }
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
