package com.sethv.fintrack.service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.sethv.fintrack.core.common.di.Dispatcher
import com.sethv.fintrack.core.common.di.FinTrackDispatchers
import com.sethv.fintrack.core.model.RawSms
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smsProcessor: SmsProcessor

    @Inject
    @Dispatcher(FinTrackDispatchers.IO)
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d(TAG, "Ignoring non-SMS_RECEIVED intent: ${intent.action}")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            Log.d(TAG, "SMS intent had no messages")
            return
        }

        val sender = messages.first().displayOriginatingAddress
        if (sender.isNullOrBlank()) {
            Log.d(TAG, "SMS had blank sender")
            return
        }

        val body = messages.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        if (body.isBlank()) {
            Log.d(TAG, "SMS from $sender had blank body")
            return
        }

        val rawSms = RawSms(
            sender = sender,
            body = body,
            timestamp = messages.first().timestampMillis,
        )

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + ioDispatcher).launch {
            try {
                // goAsync() gives us ~10s. Stay well under it.
                val completed = withTimeoutOrNull(GO_ASYNC_TIMEOUT_MS) {
                    smsProcessor.processNewSms(rawSms)
                    true
                }
                if (completed == null) {
                    Log.w(TAG, "SMS processing timed out for sender=$sender")
                } else {
                    Log.d(TAG, "SMS processed sender=$sender len=${body.length}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to process SMS from $sender", t)
            } finally {
                try {
                    pendingResult.finish()
                } catch (t: Throwable) {
                    Log.w(TAG, "pendingResult.finish() threw", t)
                }
            }
        }
    }

    private companion object {
        const val TAG = "FinTrack.SmsReceiver"
        const val GO_ASYNC_TIMEOUT_MS = 8_500L
    }
}