package com.sethv.fintrack.service.parser

import com.sethv.fintrack.core.model.RawSms
import javax.inject.Inject

/** A credit-card statement/bill detected in an SMS. */
data class ParsedCardBill(
    val cardLastFour: String,
    val bankHint: String,
    val totalDue: Double,
    val minDue: Double?,
    val dueDate: Long,
    val statementLabel: String,
)

/** A "payment received" confirmation for a specific card. */
data class ParsedCardPayment(
    val cardLastFour: String,
    val bankHint: String,
    val amount: Double,
)

/**
 * Understands Indian bank credit-card SMS: statement generation, payment
 * reminders and payment confirmations. Deliberately separate from
 * [SmsParser] because bills are not transactions — they carry a DUE date
 * and get upserted into their own ledger.
 */
class CardSmsParser @Inject constructor() {

    private val TOTAL_DUE_PATTERN = Regex(
        """(?:total|outstanding|net)\s*(?:amount\s*)?(?:due|payable)\s*(?:is|:)?\s*(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    private val MIN_DUE_PATTERN = Regex(
        """min(?:imum)?\s*(?:amount\s*)?due\s*(?:is|:)?\s*(?:Rs\.?|INR|₹)?\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    private val PAYMENT_AMOUNT_PATTERN = Regex(
        """(?:payment(?:\s+of)?|paid)\s*(?:of|:)?\s*(?:Rs\.?|INR|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    private val PAYMENT_CONFIRM_PATTERN = Regex(
        """payment\b.{0,30}\b(?:received|successful)|received\s+payment|thank\s+you\s+for\s+your\s+payment""",
        RegexOption.IGNORE_CASE,
    )

    fun parseBill(sms: RawSms): ParsedCardBill? {
        val body = sms.body
        val lower = body.lowercase()

        // Payment confirmations are handled by [parsePayment], never as bills.
        if (PAYMENT_CONFIRM_PATTERN.containsMatchIn(body)) return null

        val last4 = ParserUtils.extractCardLast4(body) ?: return null
        val mentionsDue = lower.contains("total due") ||
            lower.contains("minimum due") ||
            lower.contains("min due") ||
            lower.contains("payment due") ||
            (lower.contains("statement") && lower.contains("due"))
        if (!mentionsDue) return null

        val totalDue = TOTAL_DUE_PATTERN.find(body)?.let { amountFrom(it) }
            ?: ParserUtils.parseAmount(body)
            ?: return null
        val minDue = MIN_DUE_PATTERN.find(body)?.let { amountFrom(it) }
        val dueDate = ParserUtils.extractDueDate(body, sms.timestamp) ?: return null

        return ParsedCardBill(
            cardLastFour = last4,
            bankHint = bankHintFromSender(sms.sender),
            totalDue = totalDue,
            minDue = minDue,
            dueDate = dueDate,
            statementLabel = statementMonthLabel(sms.timestamp),
        )
    }

    fun parsePayment(sms: RawSms): ParsedCardPayment? {
        val body = sms.body
        if (!PAYMENT_CONFIRM_PATTERN.containsMatchIn(body)) return null
        val last4 = ParserUtils.extractCardLast4(body) ?: return null
        val amount = PAYMENT_AMOUNT_PATTERN.find(body)?.let { amountFrom(it) }
            ?: ParserUtils.parseAmount(body)
            ?: return null
        return ParsedCardPayment(
            cardLastFour = last4,
            bankHint = bankHintFromSender(sms.sender),
            amount = amount,
        )
    }

    private fun amountFrom(match: kotlin.text.MatchResult): Double? =
        match.groupValues[1].replace(",", "").toDoubleOrNull()

    private fun bankHintFromSender(sender: String): String = when {
        sender.uppercase().contains("HDFC") -> "HDFC"
        sender.uppercase().contains("ICICI") -> "ICICI"
        sender.uppercase().contains("SBI") -> "SBI"
        sender.uppercase().contains("AXIS") -> "Axis"
        else -> sender.take(10).ifBlank { "Unknown" }
    }

    /** e.g. "Aug 2026 statement". */
    private fun statementMonthLabel(epochMillis: Long): String {
        val date = java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneId.systemDefault())
        val month = date.month.getDisplayName(
            java.time.format.TextStyle.FULL,
            java.util.Locale.ENGLISH,
        )
        return "$month ${date.year}"
    }
}
