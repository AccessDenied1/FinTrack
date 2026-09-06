package com.sethv.fintrack.feature.cards

import com.sethv.fintrack.core.data.repository.CreditCardRepository
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.CardBill
import com.sethv.fintrack.core.model.CreditCard
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: CreditCardRepository
    private lateinit var transactionRepository: TransactionRepository

    private val card = CreditCard(id = 1, bankName = "HDFC", lastFour = "4521")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        transactionRepository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Fixed "today": Aug 10 2026 → deterministic urgency math. */
    private fun dueIn(days: Long): Long =
        LocalDate.of(2026, 8, 10).plusDays(days).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    private val nowMillis: Long = LocalDate.of(2026, 8, 10).atStartOfDay(ZoneId.systemDefault())
        .toInstant().toEpochMilli()

    private fun bill(
        id: Long,
        totalDue: Double,
        dueDate: Long,
        isPaid: Boolean = false,
        cardId: Long = card.id,
        creditLimit: Double? = null,
        statementStart: Long = 0L,
    ) = CardBill(
        id = id,
        cardId = cardId,
        totalDue = totalDue,
        minDue = totalDue / 10,
        dueDate = dueDate,
        statementLabel = "August 2026",
        generatedAt = nowMillis,
        isPaid = isPaid,
        creditLimit = creditLimit,
        statementStart = statementStart,
    )

    private fun txn(
        id: Long,
        amount: Double,
        category: ExpenseCategory,
        type: TransactionType,
        dateTime: Long,
        bank: String = "HDFC",
        cardId: Long? = null,
    ) = Transaction(
        id = id,
        amount = amount,
        merchant = "M$id",
        category = category,
        type = type,
        dateTime = dateTime,
        bank = bank,
        cardId = cardId,
    )

    private fun buildVm(
        cards: List<CreditCard>,
        bills: List<CardBill>,
        transactions: List<Transaction> = emptyList(),
        now: Long = nowMillis,
    ): CardsViewModel {
        every { repository.getAllCards() } returns flowOf(cards)
        every { repository.getAllBills() } returns flowOf(bills)
        every { transactionRepository.getAllTransactions() } returns flowOf(transactions)
        val vm = CardsViewModel(repository, transactionRepository)
        vm.now = now
        return vm
    }

    @Test
    fun `urgency buckets map correctly around today`() {
        assertEquals(DueUrgency.OVERDUE, CardsViewModel.urgencyOf(bill(1, 100.0, dueIn(-2)), nowMillis))
        assertEquals(DueUrgency.TODAY, CardsViewModel.urgencyOf(bill(1, 100.0, dueIn(0)), nowMillis))
        assertEquals(DueUrgency.WITHIN_3_DAYS, CardsViewModel.urgencyOf(bill(1, 100.0, dueIn(3)), nowMillis))
        assertEquals(DueUrgency.WITHIN_A_WEEK, CardsViewModel.urgencyOf(bill(1, 100.0, dueIn(7)), nowMillis))
        assertEquals(DueUrgency.LATER, CardsViewModel.urgencyOf(bill(1, 100.0, dueIn(12)), nowMillis))
    }

    @Test
    fun `state aggregates outstanding and nearest due across cards`() = runTest(testDispatcher) {
        val secondCard = CreditCard(id = 2, bankName = "ICICI", lastFour = "8877")
        val bills = listOf(
            bill(id = 1, totalDue = 45_000.0, dueDate = dueIn(5)).copy(cardId = card.id), // HDFC
            bill(id = 2, totalDue = 12_000.0, dueDate = dueIn(2)).copy(cardId = secondCard.id), // ICICI — nearest
            bill(id = 3, totalDue = 9_000.0, dueDate = dueIn(-9), isPaid = true).copy(cardId = card.id),
        )
        val vm = buildVm(listOf(card, secondCard), bills)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(57_000.0, state.totalOutstanding, 0.01)
        assertEquals(12_000.0, state.nearestDue!!.bill.totalDue, 0.01)
        assertEquals("ICICI", state.nearestDue!!.card.bankName)
        assertEquals(true, state.hasCards)
        assertEquals(1, state.paidHistory.size)
    }

    @Test
    fun `sections group unpaid bills per card with outstanding totals`() = runTest(testDispatcher) {
        val bills = listOf(
            bill(id = 1, totalDue = 45_000.0, dueDate = dueIn(5)),
            bill(id = 4, totalDue = 500.0, dueDate = dueIn(35)),
        )
        val vm = buildVm(listOf(card), bills)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val section = vm.uiState.value.sections.single()
        assertEquals(card.id, section.card.id)
        assertEquals(2, section.unpaid.size)
        assertEquals(45_500.0, section.outstanding, 0.01)
        assertEquals(0, section.paidCount)
    }

    @Test
    fun `markPaid delegates to repository and emits event`() = runTest(testDispatcher) {
        val b = bill(id = 1, totalDue = 45_000.0, dueDate = dueIn(5))
        coEvery { repository.markBillPaid(eq(1L), eq(45_000.0), any()) } returns Unit

        val vm = buildVm(listOf(card), listOf(b))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.markPaid(BillRow(b, card))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.markBillPaid(eq(1L), eq(45_000.0), any()) }
    }

    @Test
    fun `unmarkPaid supports snackbar undo`() = runTest(testDispatcher) {
        val vm = buildVm(emptyList(), emptyList())
        coEvery { repository.unmarkBillPaid(42L) } returns Unit

        vm.unmarkPaid(42L)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.unmarkBillPaid(42L) }
    }

    @Test
    fun `deleteCard removes the card via repository`() = runTest(testDispatcher) {
        val vm = buildVm(emptyList(), emptyList())
        coEvery { repository.deleteCard(7L) } returns Unit

        vm.deleteCard(7L)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteCard(7L) }
    }

    @Test
    fun `utilization 42pct when limit 100k and outstanding 42k`() = runTest(testDispatcher) {
        val cards = listOf(card.copy(creditLimitOverride = 100_000.0))
        val vm = buildVm(cards, listOf(bill(id = 1, totalDue = 42_000.0, dueDate = dueIn(5))))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val insights = vm.uiState.value.insights[card.id]!!
        assertEquals(42_000.0, insights.outstanding, 0.01)
        assertEquals(100_000.0, insights.limit!!, 0.01)
        assertEquals(0.42f, insights.utilization!!, 0.001f)
    }

    @Test
    fun `limit prefers the manual override over the parsed bill limit`() = runTest(testDispatcher) {
        val cards = listOf(card.copy(creditLimitOverride = 200_000.0))
        val vm = buildVm(
            cards,
            listOf(bill(id = 1, totalDue = 10_000.0, dueDate = dueIn(5), creditLimit = 100_000.0)),
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(200_000.0, vm.uiState.value.insights[card.id]!!.limit!!, 0.01)
    }

    @Test
    fun `utilization null when no limit`() = runTest(testDispatcher) {
        val vm = buildVm(listOf(card), listOf(bill(id = 1, totalDue = 42_000.0, dueDate = dueIn(5))))
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val insights = vm.uiState.value.insights[card.id]!!
        assertNull(insights.limit)
        assertNull(insights.utilization)
    }

    @Test
    fun `spendByCategory counts only debits in window for the card`() = runTest(testDispatcher) {
        val start = dueIn(-10)
        val due = dueIn(20)
        val vm = buildVm(
            cards = listOf(card),
            bills = listOf(bill(id = 1, totalDue = 42_000.0, dueDate = due, statementStart = start)),
            transactions = listOf(
                txn(id = 1, amount = 100.0, category = ExpenseCategory.FOOD, type = TransactionType.DEBIT, dateTime = start + 1_000, cardId = card.id),
                txn(id = 2, amount = 200.0, category = ExpenseCategory.FUEL, type = TransactionType.DEBIT, dateTime = start + 2_000, bank = "HDFC"),
                txn(id = 3, amount = 300.0, category = ExpenseCategory.FOOD, type = TransactionType.DEBIT, dateTime = start - 2_000, cardId = card.id),
                txn(id = 4, amount = 50.0, category = ExpenseCategory.FOOD, type = TransactionType.CREDIT, dateTime = start + 3_000, cardId = card.id),
                txn(id = 5, amount = 99.0, category = ExpenseCategory.FOOD, type = TransactionType.DEBIT, dateTime = start + 4_000, bank = "ICICI"),
            ),
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val spend = vm.uiState.value.insights[card.id]!!.spendByCategory
        assertEquals(100.0, spend[ExpenseCategory.FOOD]!!, 0.01)
        assertEquals(200.0, spend[ExpenseCategory.FUEL]!!, 0.01)
        assertNull(spend[ExpenseCategory.OTHERS])
    }

    @Test
    fun `spendByCategory skips the bank fallback when two cards share the bank`() = runTest(testDispatcher) {
        val other = CreditCard(id = 2, bankName = "HDFC", lastFour = "0001")
        val start = dueIn(-10)
        val due = dueIn(20)
        val bills = listOf(
            bill(id = 1, totalDue = 42_000.0, dueDate = due, statementStart = start, cardId = card.id),
            bill(id = 2, totalDue = 1_000.0, dueDate = dueIn(21), statementStart = start, cardId = other.id),
        )
        val vm = buildVm(
            cards = listOf(card, other),
            bills = bills,
            transactions = listOf(
                txn(id = 1, amount = 100.0, category = ExpenseCategory.FOOD, type = TransactionType.DEBIT, dateTime = start + 1_000, cardId = card.id),
                txn(id = 2, amount = 200.0, category = ExpenseCategory.FUEL, type = TransactionType.DEBIT, dateTime = start + 2_000, bank = "HDFC"),
            ),
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val insights = vm.uiState.value.insights
        // Exact cardId link still counts for the card; the ambiguous unlinked
        // bank row is dropped for BOTH same-bank cards (no double counting).
        assertEquals(100.0, insights[card.id]!!.spendByCategory[ExpenseCategory.FOOD]!!, 0.01)
        assertTrue(insights[card.id]!!.spendByCategory[ExpenseCategory.FUEL] == null)
        assertTrue(insights[other.id]!!.spendByCategory.isEmpty())
    }

    @Test
    fun `dueCalendar sorted by dueDate unpaid only`() = runTest(testDispatcher) {
        val bills = listOf(
            bill(id = 1, totalDue = 100.0, dueDate = dueIn(10)),
            bill(id = 2, totalDue = 200.0, dueDate = dueIn(1)),
            bill(id = 3, totalDue = 300.0, dueDate = dueIn(5)),
            bill(id = 4, totalDue = 400.0, dueDate = dueIn(2), isPaid = true),
        )
        val vm = buildVm(listOf(card), bills)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val dueCalendar = vm.uiState.value.insights[card.id]!!.dueCalendar
        assertEquals(listOf(2L, 3L, 1L), dueCalendar.map { it.id })
    }

    @Test
    fun `spendTrend lists last 30 days of debit sums oldest first`() = runTest(testDispatcher) {
        val day = 24L * 60 * 60 * 1000
        val vm = buildVm(
            cards = listOf(card),
            bills = listOf(bill(id = 1, totalDue = 42_000.0, dueDate = nowMillis + 20 * day, statementStart = nowMillis - 10 * day)),
            transactions = listOf(
                txn(id = 1, amount = 100.0, category = ExpenseCategory.FOOD, type = TransactionType.DEBIT, dateTime = nowMillis - 20 * day, cardId = card.id),
                txn(id = 2, amount = 150.0, category = ExpenseCategory.FOOD, type = TransactionType.DEBIT, dateTime = nowMillis - 20 * day, cardId = card.id),
                txn(id = 3, amount = 75.0, category = ExpenseCategory.FUEL, type = TransactionType.DEBIT, dateTime = nowMillis - 5 * day, cardId = card.id),
                txn(id = 4, amount = 10.0, category = ExpenseCategory.FOOD, type = TransactionType.DEBIT, dateTime = nowMillis - 40 * day, cardId = card.id),
                txn(id = 5, amount = 5.0, category = ExpenseCategory.FOOD, type = TransactionType.CREDIT, dateTime = nowMillis - 20 * day, cardId = card.id),
            ),
        )
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val trend = vm.uiState.value.insights[card.id]!!.spendTrend
        assertEquals(30, trend.size)
        assertEquals(0.0, trend[0], 0.01)
        assertEquals(250.0, trend[9], 0.01)
        assertEquals(75.0, trend[24], 0.01)
        assertEquals(0.0, trend[29], 0.01)
    }

    @Test
    fun `onSelectCard updates selectedCardId`() = runTest(testDispatcher) {
        val vm = buildVm(listOf(card), emptyList())
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertNull(vm.uiState.value.selectedCardId)

        vm.onSelectCard(card.id)
        advanceUntilIdle()

        assertEquals(card.id, vm.uiState.value.selectedCardId)
    }

    @Test
    fun `onUpdateLimit delegates to repository`() = runTest(testDispatcher) {
        val vm = buildVm(listOf(card), emptyList())
        coEvery { repository.updateLimit(card.id, 123_456.0) } returns Unit

        vm.onUpdateLimit(card.id, 123_456.0)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.updateLimit(card.id, 123_456.0) }
    }
}
