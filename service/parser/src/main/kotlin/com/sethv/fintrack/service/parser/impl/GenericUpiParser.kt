package com.sethv.fintrack.service.parser.impl

import com.sethv.fintrack.core.model.ParsedTransaction
import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.service.parser.ParserUtils
import com.sethv.fintrack.service.parser.SmsParser
import javax.inject.Inject

class GenericUpiParser @Inject constructor() : SmsParser {

    override fun canParse(sms: RawSms): Boolean {
        val body = sms.body
        // Require an explicit UPI mention: wallet/fintech SMS ("Paid Rs.500",
        // PAYTM/AmazonPay senders) without one are better served by the
        // last-resort parser, which labels them with the actual sender instead
        // of a blanket "UPI" bank tag.
        return ParserUtils.looksLikeTransactionSms(body) &&
            body.contains("UPI", ignoreCase = true)
    }

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
            bank = "UPI",
            smsBody = sms.body,
        )
    }
}
