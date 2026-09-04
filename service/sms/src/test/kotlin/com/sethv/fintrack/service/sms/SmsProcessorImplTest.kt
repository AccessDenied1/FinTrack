package com.sethv.fintrack.service.sms

import com.sethv.fintrack.core.data.repository.PendingTransactionRepository
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.ParsedTransaction
import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.core.model.TransactionType
import com.sethv.fintrack.service.categorizer.TransactionCategorizer
import com.sethv.fintrack.service.notification.TransactionNotifier
import com.sethv.fintrack.service.parser.SmsParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmsProcessorImplTest {

    private val smsParser: SmsParser = mockk()
    private val cardSmsParser: com.sethv.fintrack.service.parser.CardSmsParser = mockk()
    private val categorizer: TransactionCategorizer = mockk()
    private val pendingRepository: PendingTransactionRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val creditCardRepository: com.sethv.fintrack.core.data.repository.CreditCardRepository = mockk()
    private val notifier: TransactionNotifier = mockk(relaxed = true)

    private lateinit var processor: SmsProcessorImpl

    private val rawSms = RawSms(
        sender = "AD-HDFCBK",
        body = "Rs.350 debited from a/c XX1234 to SWIGGY",
        timestamp = 1_700_000_012_345L,
    )

    private val parsed = ParsedTransaction(
        amount = 350.0,
        merchant = "SWIGGY",
        type = TransactionType.DEBIT,
        dateTime = 1_700_000_012_345L,
        bank = "HDFC",
        smsBody = rawSms.body,
    )

    @Before
    fun setup() {
        processor = SmsProcessorImpl(
            smsParser = smsParser,
            cardSmsParser = cardSmsParser,
            categorizer = categorizer,
            pendingTransactionRepository = pendingRepository,
            transactionRepository = transactionRepository,
            creditCardRepository = creditCardRepository,
            transactionNotifier = notifier,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        every { smsParser.canParse(any()) } returns true
        every { smsParser.parse(any()) } returns parsed
        every { categorizer.categorize(any()) } returns ExpenseCategory.FOOD
        coEvery { pendingRepository.existsBySmsFingerprint(any(), any()) } returns false
        coEvery { transactionRepository.existsBySmsFingerprint(any(), any()) } returns false
        coEvery { pendingRepository.insertPending(any()) } returns 5L
        coEvery { cardSmsParser.parseBill(any()) } returns null
        coEvery { cardSmsParser.parsePayment(any()) } returns null
    }

    @Test
    fun `card bill SMS creates bill and alerts - bypasses review queue`() = runTest {
        val bill = com.sethv.fintrack.service.parser.ParsedCardBill(
            cardLastFour = "4521",
            bankHint = "HDFC",
            totalDue = 45_000.0,
            minDue = 2_250.0,
            dueDate = 1_800_000_000_000L,
            statementLabel = "August 2026",
        )
        every { smsParser.parse(any()) } returns null // txn parser must not claim it
        coEvery { cardSmsParser.parseBill(rawSms) } returns bill
        coEvery { creditCardRepository.findOrCreateCard("HDFC", "4521") } returns 77L
        coEvery {
            creditCardRepository.upsertBill(77L, 45_000.0, 2_250.0, 1_800_000_000_000L, "August 2026")
        } returns 900L

        processor.processNewSms(rawSms)

        coVerify(exactly = 0) { pendingRepository.insertPending(any()) }
        verify(exactly = 1) {
            notifier.showCardBillAlert("HDFC", "4521", 45_000.0, 2_250.0, 1_800_000_000_000L, 900L)
        }
        verify(exactly = 0) { notifier.showTransactionNotification(any()) }
    }

    @Test
    fun `payment confirmation SMS settles matching unpaid bill and confirms`() = runTest {
        val payment = com.sethv.fintrack.service.parser.ParsedCardPayment(
            cardLastFour = "4521",
            bankHint = "HDFC",
            amount = 45_000.0,
        )
        every { smsParser.parse(any()) } returns null
        coEvery { cardSmsParser.parseBill(any()) } returns null
        coEvery { cardSmsParser.parsePayment(rawSms) } returns payment
        coEvery { creditCardRepository.findOrCreateCard("HDFC", "4521") } returns 77L
        coEvery { creditCardRepository.settleBillWithPayment(77L, 45_000.0) } returns
            com.sethv.fintrack.core.model.CardBill(
                id = 901,
                cardId = 77L,
                totalDue = 45_000.0,
                minDue = 2_250.0,
                dueDate = 1_800_000_000_000L,
                statementLabel = "August 2026",
            )

        processor.processNewSms(rawSms)

        verify(exactly = 1) { notifier.showBillPaidConfirmation("HDFC", "4521", 45_000.0) }
        coVerify(exactly = 0) { pendingRepository.insertPending(any()) }
    }

    @Test
    fun `partial payment below total due does NOT auto-settle or notify paid`() = runTest {
        val payment = com.sethv.fintrack.service.parser.ParsedCardPayment(
            cardLastFour = "4521",
            bankHint = "HDFC",
            amount = 2_250.0, // only the minimum
        )
        every { smsParser.parse(any()) } returns null
        coEvery { cardSmsParser.parseBill(any()) } returns null
        coEvery { cardSmsParser.parsePayment(rawSms) } returns payment
        coEvery { creditCardRepository.findOrCreateCard("HDFC", "4521") } returns 77L
        coEvery { creditCardRepository.settleBillWithPayment(77L, 2_250.0) } returns null
        coEvery {
            creditCardRepository.creditPaymentToMostRecentBill(77L, 2_250.0, any())
        } returns Unit

        processor.processNewSms(rawSms)

        verify(exactly = 0) { notifier.showBillPaidConfirmation(any(), any(), any()) }
        coVerify(exactly = 1) { creditCardRepository.creditPaymentToMostRecentBill(77L, 2_250.0, any()) }
    }

    @Test
    fun `inserts pending row and notifies for new SMS`() = runTest {
        processor.processNewSms(rawSms)

        coVerify(exactly = 1) { pendingRepository.insertPending(any()) }
        verify(exactly = 1) { notifier.showTransactionNotification(any()) }
    }

    @Test
    fun `minute-bucket dedup tolerates clock skew between SMSC and handset`() = runTest {
        // A historical copy of this same message stored under a skewed receive
        // time still lands in the SAME minute bucket as the live broadcast.
        coEvery {
            pendingRepository.existsBySmsFingerprint(any(), any())
        } returns true

        processor.processNewSms(rawSms)

        val expectedBucket = 1_700_000_012_345L.floorDiv(60_000L)
        coVerify(exactly = 1) {
            pendingRepository.existsBySmsFingerprint(rawSms.body, expectedBucket)
        }
        coVerify(exactly = 0) { pendingRepository.insertPending(any()) }
    }

    @Test
    fun `fingerprint buckets are stable across sub-minute skew and split across minutes`() {
        val base = 1_700_000_012_345L // sits 32,345ms into its minute

        // +20s skew → same bucket (dedup catches the duplicate copy).
        assertEquals(
            SmsFingerprint.minuteBucketOf(base),
            SmsFingerprint.minuteBucketOf(base + 20_000L),
        )
        // +30s → crosses a minute boundary → genuinely new bucket.
        org.junit.Assert.assertNotEquals(
            SmsFingerprint.minuteBucketOf(base),
            SmsFingerprint.minuteBucketOf(base + 30_000L),
        )
    }

    @Test
    fun `skips SMS already accepted into the ledger`() = runTest {
        coEvery { transactionRepository.existsBySmsFingerprint(any(), any()) } returns true

        processor.processNewSms(rawSms)

        coVerify(exactly = 0) { pendingRepository.insertPending(any()) }
        verify(exactly = 0) { notifier.showTransactionNotification(any()) }
    }

    @Test
    fun `does not notify when insert fails - persist before notify`() = runTest {
        coEvery { pendingRepository.insertPending(any()) } throws RuntimeException("db closed")

        processor.processNewSms(rawSms)

        verify(exactly = 0) { notifier.showTransactionNotification(any()) }
    }

    @Test
    fun `notification SecurityException does not lose the saved row`() = runTest {
        every { notifier.showTransactionNotification(any()) } throws SecurityException("permission denied")

        processor.processNewSms(rawSms)

        coVerify(exactly = 1) { pendingRepository.insertPending(any()) }
    }
}