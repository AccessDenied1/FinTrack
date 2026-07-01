package com.sethv.fintrack.service.sms

import com.sethv.fintrack.core.model.RawSms

interface SmsProcessor {
    suspend fun processNewSms(rawSms: RawSms)
}
