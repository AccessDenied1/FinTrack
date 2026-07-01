package com.sethv.fintrack.service.parser.impl

import com.sethv.fintrack.core.model.ParsedTransaction
import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.service.parser.ParserUtils
import com.sethv.fintrack.service.parser.SmsParser
import javax.inject.Inject

class SbiBankParser @Inject constructor() : SmsParser {

    override fun canParse(sms: RawSms): Boolean {
        if (!ParserUtils.senderContainsAny(sms.sender, "SBI", "SBIINB", "SBIPSG")) return false
        val body = sms.body
        return body.contains("debited", ignoreCase = true) ||
            body.contains("credited", ignoreCase = true)
    }

    override fun parse(sms: RawSms): ParsedTransaction? {
        if (!canParse(sms)) return null

        val amount = ParserUtils.parseAmount(sms.body) ?: return null
        val merchant = ParserUtils.extractMerchantAfterKeyword(sms.body, "to")
            ?: ParserUtils.extractMerchantFromUpi(sms.body)
            ?: ParserUtils.extractMerchantAfterKeyword(sms.body, "at")
            ?: "SBI Transaction"

        return ParsedTransaction(
            amount = amount,
            merchant = merchant,
            type = ParserUtils.detectTransactionType(sms.body),
            dateTime = sms.timestamp,
            bank = "SBI",
            smsBody = sms.body,
        )
    }
}
