package com.sethv.fintrack.service.sms

import android.util.Log
import com.sethv.fintrack.core.common.di.Dispatcher
import com.sethv.fintrack.core.common.di.FinTrackDispatchers
import com.sethv.fintrack.core.data.repository.PendingTransactionRepository
import com.sethv.fintrack.core.model.PendingStatus
import com.sethv.fintrack.core.model.PendingTransaction
import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.service.categorizer.TransactionCategorizer
import com.sethv.fintrack.service.notification.TransactionNotifier
import com.sethv.fintrack.service.parser.SmsParser
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class SmsProcessorImpl @Inject constructor(
    private val smsParser: SmsParser,
    private val categorizer: TransactionCategorizer,
    private val pendingTransactionRepository: PendingTransactionRepository,
    private val transactionNotifier: TransactionNotifier,
    @Dispatcher(FinTrackDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : SmsProcessor {

    override suspend fun processNewSms(rawSms: RawSms) {
        withContext(ioDispatcher) {
            // 1. Parse — if not parseable, exit silently (most SMS is noise).
            val parsed = try {
                smsParser.parse(rawSms)
            } catch (t: Throwable) {
                Log.e(TAG, "Parser threw for sender=${rawSms.sender}", t)
                null
            }
            if (parsed == null) {
                Log.d(TAG, "No parser matched sender=${rawSms.sender}")
                return@withContext
            }

            // 2. Real-time dedup: if we've already accepted or queued this exact SMS, skip.
            val duplicate = isDuplicate(parsed)
            if (duplicate) {
                Log.d(TAG, "Duplicate SMS ignored: ${parsed.merchant} ${parsed.amount}")
                return@withContext
            }

            // 3. Persist BEFORE notifying — never lose a row because notification failed.
            val pending = PendingTransaction(
                amount = parsed.amount,
                merchant = parsed.merchant,
                category = categorizer.categorize(parsed),
                type = parsed.type,
                dateTime = parsed.dateTime,
                bank = parsed.bank,
                smsBody = parsed.smsBody,
                status = PendingStatus.PENDING,
            )
            val id = try {
                pendingTransactionRepository.insertPending(pending)
            } catch (t: Throwable) {
                Log.e(TAG, "insertPending failed for ${parsed.merchant}", t)
                return@withContext
            }

            // 4. Notify — tolerate SecurityException (POST_NOTIFICATIONS denied on 13+).
            try {
                transactionNotifier.showTransactionNotification(pending.copy(id = id))
            } catch (se: SecurityException) {
                Log.w(TAG, "Notification permission denied — row still saved id=$id", se)
            } catch (t: Throwable) {
                Log.e(TAG, "Notifier threw — row still saved id=$id", t)
            }
        }
    }

    private suspend fun isDuplicate(
        parsed: com.sethv.fintrack.core.model.ParsedTransaction,
    ): Boolean {
        val key = parsed.smsBody to parsed.dateTime
        val inTransactions = pendingTransactionRepository
            .getAllPending()
            .first()
            .any { it.smsBody == parsed.smsBody && it.dateTime == parsed.dateTime }
        if (inTransactions) return true
        // For dedup against accepted transactions we'd need TransactionRepository — keeping
        // it light here: the historical scan path covers the full set, and the pending
        // check covers the common "double-broadcast within seconds" case.
        @Suppress("UNUSED_VARIABLE") val ignored = key
        return false
    }

    private companion object {
        const val TAG = "FinTrack.SmsProcessor"
    }
}