package com.sethv.fintrack.service.parser.impl

import com.sethv.fintrack.core.model.ParsedTransaction
import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.service.parser.ParserUtils
import com.sethv.fintrack.service.parser.SmsParser
import javax.inject.Inject

/**
 * Last-resort parser: accepts any SMS that passes [ParserUtils.looksLikeTransactionSms],
 * regardless of sender. Catches non-bank / non-UPI transaction notifications
 * (Amazon Pay, Paytm wallet, Jio Money, etc.) that no specific bank parser matches.
 *
 * Registered LAST in the composite so bank-specific parsers win first.
 */
class GenericTransactionParser @Inject constructor() : SmsParser {

    override fun canParse(sms: RawSms): Boolean =
        ParserUtils.looksLikeTransactionSms(sms.body)

    override fun parse(sms: RawSms): ParsedTransaction? {
        if (!canParse(sms)) return null

        val amount = ParserUtils.parseAmount(sms.body) ?: return null
        val merchant = ParserUtils.extractMerchantAfterKeyword(sms.body, "to")
            ?: ParserUtils.extractMerchantAfterKeyword(sms.body, "at")
            ?: ParserUtils.extractMerchantFromUpi(sms.body)
            ?: "Unknown Merchant"

        return ParsedTransaction(
            amount = amount,
            merchant = merchant,
            type = ParserUtils.detectTransactionType(sms.body),
            dateTime = sms.timestamp,
            bank = sms.sender.take(MAX_SENDER_LABEL).ifBlank { "Unknown" },
            smsBody = sms.body,
        )
    }

    private companion object {
        const val MAX_SENDER_LABEL = 16
    }
}