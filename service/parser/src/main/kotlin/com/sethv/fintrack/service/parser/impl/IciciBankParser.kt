package com.sethv.fintrack.service.parser.impl

import com.sethv.fintrack.core.model.ParsedTransaction
import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.service.parser.ParserUtils
import com.sethv.fintrack.service.parser.SmsParser
import javax.inject.Inject

class IciciBankParser @Inject constructor() : SmsParser {

    override fun canParse(sms: RawSms): Boolean {
        if (!ParserUtils.senderContainsAny(sms.sender, "ICICI", "ICICIB")) return false
        // Broad gate (like HDFC/Axis) so "Paid/Sent Rs.X to Y" UPI SMS from
        // ICICI senders keep their ICICI attribution instead of falling
        // through to the generic UPI parser.
        return ParserUtils.looksLikeTransactionSms(sms.body)
    }

    override fun parse(sms: RawSms): ParsedTransaction? {
        if (!canParse(sms)) return null

        val amount = ParserUtils.parseAmount(sms.body) ?: return null
        val merchant = ParserUtils.extractMerchantAfterKeyword(sms.body, "to")
            ?: ParserUtils.extractMerchantFromUpi(sms.body)
            ?: ParserUtils.extractMerchantAfterKeyword(sms.body, "at")
            ?: "ICICI Transaction"

        return ParsedTransaction(
            amount = amount,
            merchant = merchant,
            type = ParserUtils.detectTransactionType(sms.body),
            dateTime = sms.timestamp,
            bank = "ICICI",
            smsBody = sms.body,
        )
    }
}
