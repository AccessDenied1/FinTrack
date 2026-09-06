package com.sethv.fintrack.service.sms

import android.util.Log
import com.sethv.fintrack.core.data.repository.PendingTransactionRepository
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.PendingStatus
import com.sethv.fintrack.core.model.PendingTransaction
import com.sethv.fintrack.service.categorizer.TransactionCategorizer
import com.sethv.fintrack.service.parser.SmsParser
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class HistoricalSmsProcessor @Inject constructor(
    private val historicalSmsReader: HistoricalSmsReader,
    private val smsParser: SmsParser,
    private val cardSmsParser: com.sethv.fintrack.service.parser.CardSmsParser,
    private val categorizer: TransactionCategorizer,
    private val pendingTransactionRepository: PendingTransactionRepository,
    private val transactionRepository: TransactionRepository,
    private val creditCardRepository: com.sethv.fintrack.core.data.repository.CreditCardRepository,
) {

    suspend fun scanAndProcess(): Int {
        val allSms = historicalSmsReader.readAllSms()

        // Snapshot of already-accepted ledger rows (minute-bucketed keys —
        // see [SmsFingerprint] for why exact timestamps are not compared).
        val acceptedBuckets = transactionRepository.getAllTransactions().first()
            .mapTo(mutableSetOf()) { SmsFingerprint.minuteBucketOf(it.dateTime) to it.smsBody }

        var count = 0
        for (sms in allSms) {
            // Card statements & payments first — they bypass the txn queue.
            val cardHandled = try {
                ingestCardSms(sms)
            } catch (t: Throwable) {
                Log.e(TAG, "Card ingestion failed during scan", t)
                false
            }
            // Card bills/payments are ingested into the Cards section, not the
            // transaction ledger — so they must NOT inflate the "N transactions
            // imported" count reported back to the user.
            if (cardHandled) {
                continue
            }

            val parsed = try {
                smsParser.parse(sms)
            } catch (t: Throwable) {
                Log.w(TAG, "Parser threw during scan for sender=${sms.sender}", t)
                null
            } ?: continue

            if ((SmsFingerprint.minuteBucketOf(parsed.dateTime) to parsed.smsBody) in acceptedBuckets) {
                continue
            }

            // Live re-check right before insert: an SMS processed by the
            // receiver while this scan was running would otherwise be inserted
            // twice (the snapshot above cannot see it yet).
            val bucket = SmsFingerprint.minuteBucketOf(parsed.dateTime)
            val duplicate = try {
                pendingTransactionRepository.existsBySmsFingerprint(parsed.smsBody, bucket)
            } catch (t: Throwable) {
                Log.e(TAG, "Dedup lookup failed during scan", t)
                false
            }
            if (duplicate) continue

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
            // Isolate failures to the offending row instead of aborting the
            // whole scan with a misleading "scan failed" state.
            count += try {
                pendingTransactionRepository.insertPending(pending)
                1
            } catch (t: Throwable) {
                Log.e(TAG, "insertPending failed during scan for ${parsed.merchant}", t)
                0
            }
        }
        return count
    }

    private companion object {
        const val TAG = "FinTrack.HistScan"
    }

    /** Ingests one historical SMS as a card bill or payment. Returns false when it isn't one. */
    private suspend fun ingestCardSms(sms: com.sethv.fintrack.core.model.RawSms): Boolean {
        val bill = cardSmsParser.parseBill(sms)
        if (bill != null) {
            val cardId = creditCardRepository.findOrCreateCard(bill.bankHint, bill.cardLastFour)
            creditCardRepository.upsertBill(
                cardId = cardId,
                totalDue = bill.totalDue,
                minDue = bill.minDue ?: 0.0,
                dueDate = bill.dueDate,
                statementLabel = bill.statementLabel,
                creditLimit = bill.creditLimit,
                statementStart = bill.statementStart,
            )
            return true
        }
        val payment = cardSmsParser.parsePayment(sms)
        if (payment != null) {
            val cardId = creditCardRepository.findOrCreateCard(payment.bankHint, payment.cardLastFour)
            val settled = creditCardRepository.settleBillWithPayment(cardId, payment.amount)
            if (settled == null) {
                creditCardRepository.creditPaymentToMostRecentBill(cardId, payment.amount)
            }
            return true
        }
        return false
    }
}
