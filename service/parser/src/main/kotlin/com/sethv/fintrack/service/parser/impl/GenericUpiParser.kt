package com.sethv.fintrack.service.parser.impl

import com.sethv.fintrack.core.model.ParsedTransaction
import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.service.parser.ParserUtils
import com.sethv.fintrack.service.parser.SmsParser
import javax.inject.Inject

class GenericUpiParser @Inject constructor() : SmsParser {

    override fun canParse(sms: RawSms): Boolean {
        val body = sms.body
        return ParserUtils.looksLikeTransactionSms(body) &&
            (body.contains("UPI", ignoreCase = true) ||
                body.contains("paid", ignoreCase = true) ||
                body.contains("debited", ignoreCase = true))
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
