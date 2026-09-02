package com.sethv.fintrack.service.sms

import com.sethv.fintrack.core.data.repository.PendingTransactionRepository
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.ParsedTransaction
import com.sethv.fintrack.core.model.RawSms
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType
import com.sethv.fintrack.service.categorizer.TransactionCategorizer
import com.sethv.fintrack.service.notification.TransactionNotifier
import com.sethv.fintrack.service.parser.CardSmsParser
import com.sethv.fintrack.service.parser.SmsParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HistoricalSmsProcessorTest {

    private val reader: HistoricalSmsReader = mockk()
    private val smsParser: SmsParser = mockk()
    private val cardSmsParser: CardSmsParser = mockk()
    private val categorizer: TransactionCategorizer = mockk()
    private val pendingRepository: PendingTransactionRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val creditCardRepository: com.sethv.fintrack.core.data.repository.CreditCardRepository = mockk()
    private val notifier: TransactionNotifier = mockk(relaxed = true)

    private lateinit var processor: HistoricalSmsProcessor

    private fun raw(body: String, ts: Long) = RawSms(sender = "AD-HDFCBK", body = body, timestamp = ts)

    private fun parsed(body: String, ts: Long, amount: Double = 100.0) = ParsedTransaction(
        amount = amount,
        merchant = "MERCHANT",
        type = TransactionType.DEBIT,
        dateTime = ts,
        bank = "HDFC",
        smsBody = body,
    )

    @Before
    fun setup() {
        processor = HistoricalSmsProcessor(
            historicalSmsReader = reader,
            smsParser = smsParser,
            cardSmsParser = cardSmsParser,
            categorizer = categorizer,
            pendingTransactionRepository = pendingRepository,
            transactionRepository = transactionRepository,
            creditCardRepository = creditCardRepository,
        )
        every { categorizer.categorize(any()) } returns ExpenseCategory.FOOD
        every { cardSmsParser.parseBill(any()) } returns null
        every { cardSmsParser.parsePayment(any()) } returns null
        coEvery { pendingRepository.insertPending(any()) } returns 1L
        coEvery { transactionRepository.getAllTransactions() } returns flowOf(emptyList())
        coEvery { pendingRepository.existsBySmsFingerprint(any(), any()) } returns false
        coEvery { creditCardRepository.findOrCreateCard(any(), any()) } returns 7L
        coEvery { creditCardRepository.upsertBill(any(), any(), any(), any(), any()) } returns 77L
        coEvery { creditCardRepository.settleBillWithPayment(any(), any()) } returns false
    }

    @Test
    fun `inserts parsed SMS not present anywhere`() = runTest {
        every { reader.readAllSms() } returns listOf(raw("sms one", 1_000_000L))
        every { smsParser.canParse(any()) } returns true
        every { smsParser.parse(any()) } returns parsed("sms one", 1_000_000L)

        val count = processor.scanAndProcess()

        assertEquals(1, count)
        coVerify(exactly = 1) { pendingRepository.insertPending(any()) }
    }

    @Test
    fun `skips SMS matching accepted ledger row via minute bucket`() = runTest {
        // Accepted row carries a skewed handset receive time (+10s — stays
        // within the same minute bucket as the live broadcast timestamp).
        val acceptedTs = 1_000_010_123L
        every { reader.readAllSms() } returns listOf(raw("sms one", 1_000_000_123L))
        every { smsParser.canParse(any()) } returns true
        every { smsParser.parse(any()) } returns parsed("sms one", 1_000_000_123L)
        coEvery { transactionRepository.getAllTransactions() } returns flowOf(
            listOf(
                Transaction(
                    id = 9,
                    amount = 100.0,
                    merchant = "MERCHANT",
                    category = ExpenseCategory.OTHERS,
                    type = TransactionType.DEBIT,
                    dateTime = acceptedTs,
                    bank = "HDFC",
                    smsBody = "sms one",
                ),
            ),
        )

        val count = processor.scanAndProcess()

        assertEquals(0, count)
    }

    @Test
    fun `re-checks live duplicates right before insert - race with receiver`() = runTest {
        // Row appeared in the pending table AFTER the snapshot was taken.
        every { reader.readAllSms() } returns listOf(raw("live race sms", 2_000_000L))
        every { smsParser.canParse(any()) } returns true
        every { smsParser.parse(any()) } returns parsed("live race sms", 2_000_000L)
        coEvery {
            pendingRepository.existsBySmsFingerprint("live race sms", any())
        } returns true

        val count = processor.scanAndProcess()

        assertEquals(0, count)
        coVerify(exactly = 0) { pendingRepository.insertPending(any()) }
    }

    @Test
    fun `one failing insert does not abort the remaining scan`() = runTest {
        val bodies = listOf("bad row", "good row")
        every { reader.readAllSms() } returns bodies.mapIndexed { i, b -> raw(b, (i + 1) * 10_000_000L) }
        every { smsParser.canParse(any()) } returns true
        every { smsParser.parse(any()) } answers { parsed(firstArg<RawSms>().body, firstArg<RawSms>().timestamp) }
        coEvery { pendingRepository.insertPending(match { it.smsBody == "bad row" }) } throws RuntimeException("constraint!")
        coEvery { pendingRepository.insertPending(match { it.smsBody == "good row" }) } returns 2L

        val count = processor.scanAndProcess()

        assertEquals(1, count)
        coVerify(exactly = 1) { pendingRepository.insertPending(match { it.smsBody == "good row" }) }
    }

    @Test
    fun `parser exception on one SMS does not abort the scan`() = runTest {
        every { reader.readAllSms() } returns listOf(raw("explode pls", 5_000_000L), raw("fine sms", 6_000_000L))
        every { smsParser.canParse(any()) } returns true
        every { smsParser.parse(match { it.body == "explode pls" }) } throws IllegalStateException("regex meltdown")
        every { smsParser.parse(match { it.body == "fine sms" }) } returns parsed("fine sms", 6_000_000L)

        val count = processor.scanAndProcess()

        assertEquals(1, count)
    }

    @Test
    fun `unparseable SMS are skipped silently`() = runTest {
        every { reader.readAllSms() } returns listOf(raw("marketing spam", 7_000_000L))
        every { smsParser.canParse(any()) } returns false

        val count = processor.scanAndProcess()

        assertEquals(0, count)
        coVerify(exactly = 0) { pendingRepository.insertPending(any()) }
    }
}
