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
    private val categorizer: TransactionCategorizer = mockk()
    private val pendingRepository: PendingTransactionRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
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
            categorizer = categorizer,
            pendingTransactionRepository = pendingRepository,
            transactionRepository = transactionRepository,
            transactionNotifier = notifier,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        every { smsParser.canParse(any()) } returns true
        every { smsParser.parse(any()) } returns parsed
        every { categorizer.categorize(any()) } returns ExpenseCategory.FOOD
        coEvery { pendingRepository.existsBySmsFingerprint(any(), any()) } returns false
        coEvery { transactionRepository.existsBySmsFingerprint(any(), any()) } returns false
        coEvery { pendingRepository.insertPending(any()) } returns 5L
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
