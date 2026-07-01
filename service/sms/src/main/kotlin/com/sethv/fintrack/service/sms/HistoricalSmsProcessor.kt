package com.sethv.fintrack.service.sms

import com.sethv.fintrack.core.data.repository.PendingTransactionRepository
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.PendingStatus
import com.sethv.fintrack.core.model.PendingTransaction
import com.sethv.fintrack.service.categorizer.TransactionCategorizer
import com.sethv.fintrack.service.parser.SmsParser
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class HistoricalSmsProcessor @Inject constructor(
    private val historicalSmsReader: HistoricalSmsReader,
    private val smsParser: SmsParser,
    private val categorizer: TransactionCategorizer,
    private val pendingTransactionRepository: PendingTransactionRepository,
    private val transactionRepository: TransactionRepository,
) {

    suspend fun scanAndProcess(): Int {
        val allSms = historicalSmsReader.readAllSms()

        val existingTransactions = transactionRepository.getAllTransactions().first()
        val existingPending = pendingTransactionRepository.getAllPending().first()

        val existingSmsBodies = buildSet {
            existingTransactions.forEach { add(it.smsBody to it.dateTime) }
            existingPending.forEach { add(it.smsBody to it.dateTime) }
        }

        var count = 0
        for (sms in allSms) {
            val parsed = smsParser.parse(sms) ?: continue

            if ((parsed.smsBody to parsed.dateTime) in existingSmsBodies) continue

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
            pendingTransactionRepository.insertPending(pending)
            count++
        }
        return count
    }
}
