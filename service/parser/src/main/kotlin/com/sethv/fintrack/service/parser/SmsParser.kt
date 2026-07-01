package com.sethv.fintrack.service.parser

import com.sethv.fintrack.core.model.ParsedTransaction
import com.sethv.fintrack.core.model.RawSms

interface SmsParser {
    fun canParse(sms: RawSms): Boolean
    fun parse(sms: RawSms): ParsedTransaction?
}
