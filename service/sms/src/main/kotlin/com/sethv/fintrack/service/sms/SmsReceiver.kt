package com.sethv.fintrack.service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.sethv.fintrack.core.common.di.Dispatcher
import com.sethv.fintrack.core.common.di.FinTrackDispatchers
import com.sethv.fintrack.core.model.RawSms
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smsProcessor: SmsProcessor

    @Inject
    @Dispatcher(FinTrackDispatchers.IO)
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.first().displayOriginatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        if (body.isBlank()) return

        val rawSms = RawSms(
            sender = sender,
            body = body,
            timestamp = messages.first().timestampMillis,
        )

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + ioDispatcher).launch {
            try {
                smsProcessor.processNewSms(rawSms)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
