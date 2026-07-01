package com.sethv.fintrack.service.sms

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
            val parsed = smsParser.parse(rawSms) ?: return@withContext

            val category = categorizer.categorize(parsed)
            val pending = PendingTransaction(
                amount = parsed.amount,
                merchant = parsed.merchant,
                category = category,
                type = parsed.type,
                dateTime = parsed.dateTime,
                bank = parsed.bank,
                smsBody = parsed.smsBody,
                status = PendingStatus.PENDING,
            )

            val id = pendingTransactionRepository.insertPending(pending)
            transactionNotifier.showTransactionNotification(pending.copy(id = id))
        }
    }
}
