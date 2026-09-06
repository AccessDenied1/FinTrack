package com.sethv.fintrack.service.sms

import android.util.Log
import com.sethv.fintrack.core.common.di.Dispatcher
import com.sethv.fintrack.core.common.di.FinTrackDispatchers
import com.sethv.fintrack.core.data.repository.CreditCardRepository
import com.sethv.fintrack.core.data.repository.PendingTransactionRepository
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.PendingStatus
import com.sethv.fintrack.core.model.PendingTransaction
import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.service.categorizer.TransactionCategorizer
import com.sethv.fintrack.service.notification.TransactionNotifier
import com.sethv.fintrack.service.parser.CardSmsParser
import com.sethv.fintrack.service.parser.SmsParser
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class SmsProcessorImpl @Inject constructor(
    private val smsParser: SmsParser,
    private val cardSmsParser: CardSmsParser,
    private val categorizer: TransactionCategorizer,
    private val pendingTransactionRepository: PendingTransactionRepository,
    private val transactionRepository: TransactionRepository,
    private val creditCardRepository: CreditCardRepository,
    private val transactionNotifier: TransactionNotifier,
    @Dispatcher(FinTrackDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : SmsProcessor {

    override suspend fun processNewSms(rawSms: RawSms) {
        withContext(ioDispatcher) {
            // 0. Card statements & payment confirmations bypass the review
            //    queue — they are upserted straight into the cards ledger.
            if (handleCardSms(rawSms)) return@withContext

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
            if (isDuplicate(parsed)) {
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

    /**
     * @return true when this SMS was consumed as a card statement or a
     *         credit-card payment confirmation.
     */
    private suspend fun handleCardSms(rawSms: RawSms): Boolean {
        val bill = try {
            cardSmsParser.parseBill(rawSms)
        } catch (t: Throwable) {
            Log.e(TAG, "Card bill parser threw for sender=${rawSms.sender}", t)
            null
        }

        if (bill != null) {
            val cardId = creditCardRepository.findOrCreateCard(bill.bankHint, bill.cardLastFour)
            val billRowId = creditCardRepository.upsertBill(
                cardId = cardId,
                totalDue = bill.totalDue,
                minDue = bill.minDue ?: 0.0,
                dueDate = bill.dueDate,
                statementLabel = bill.statementLabel,
                creditLimit = bill.creditLimit,
                statementStart = bill.statementStart,
            )
            try {
                transactionNotifier.showCardBillAlert(
                    bankName = bill.bankHint,
                    lastFour = bill.cardLastFour,
                    totalDue = bill.totalDue,
                    minDue = bill.minDue,
                    dueDate = bill.dueDate,
                    billId = billRowId,
                )
            } catch (se: SecurityException) {
                Log.w(TAG, "Card alert not posted — permission denied", se)
            } catch (t: Throwable) {
                Log.e(TAG, "Card bill notifier threw", t)
            }
            return true
        }

        val payment = try {
            cardSmsParser.parsePayment(rawSms)
        } catch (t: Throwable) {
            Log.e(TAG, "Card payment parser threw for sender=${rawSms.sender}", t)
            null
        }

        if (payment != null) {
            val cardId = creditCardRepository.findOrCreateCard(payment.bankHint, payment.cardLastFour)
            val settled = creditCardRepository.settleBillWithPayment(cardId, payment.amount)
            if (settled != null) {
                Log.d(TAG, "Card payment ${payment.amount} settled bill id=${settled.id}")
                try {
                    transactionNotifier.showBillPaidConfirmation(
                        bankName = payment.bankHint,
                        lastFour = payment.cardLastFour,
                        paidAmount = payment.amount,
                    )
                } catch (se: SecurityException) {
                    Log.w(TAG, "Paid confirmation not posted — permission denied", se)
                } catch (t: Throwable) {
                    Log.e(TAG, "Paid notifier threw", t)
                }
            } else {
                // Partial payment (e.g. min due) or a prepay: credit the card's
                // most recent bill instead of dropping the amount silently.
                creditCardRepository.creditPaymentToMostRecentBill(cardId, payment.amount)
                Log.d(TAG, "Card payment ${payment.amount} credited as partial/prepay")
            }
            return true
        }

        return false
    }

    private suspend fun isDuplicate(
        parsed: com.sethv.fintrack.core.model.ParsedTransaction,
    ): Boolean {
        // Minute-bucketed key: the live broadcast carries the SMSC timestamp
        // while a historical rescan reads the handset receive time — the two
        // routinely skew by seconds, so exact-timestamp equality missed dups.
        val bucket = SmsFingerprint.minuteBucketOf(parsed.dateTime)
        if (pendingTransactionRepository.existsBySmsFingerprint(parsed.smsBody, bucket)) {
            return true
        }
        // Also guard against re-ingesting SMS that were already accepted into
        // the ledger (e.g. duplicate broadcast after "Accept All").
        return transactionRepository.existsBySmsFingerprint(parsed.smsBody, bucket)
    }

    private companion object {
        const val TAG = "FinTrack.SmsProcessor"
    }
}
