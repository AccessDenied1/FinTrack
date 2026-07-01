package com.sethv.fintrack.service.parser

import com.sethv.fintrack.core.model.ParsedTransaction
import com.sethv.fintrack.core.model.RawSms
import javax.inject.Inject

class CompositeSmsParser @Inject constructor(
    private val parsers: Set<@JvmSuppressWildcards SmsParser>,
) : SmsParser {

    override fun canParse(sms: RawSms): Boolean =
        parsers.any { it.canParse(sms) }

    override fun parse(sms: RawSms): ParsedTransaction? {
        for (parser in parsers) {
            if (parser.canParse(sms)) {
                return parser.parse(sms)
            }
        }
        return null
    }
}
