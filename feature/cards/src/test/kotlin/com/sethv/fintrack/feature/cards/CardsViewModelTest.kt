package com.sethv.fintrack.feature.cards

import com.sethv.fintrack.core.data.repository.CreditCardRepository
import com.sethv.fintrack.core.model.CardBill
import com.sethv.fintrack.core.model.CreditCard
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: CreditCardRepository

    private val card = CreditCard(id = 1, bankName = "HDFC", lastFour = "4521")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
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
    ) = CardBill(
        id = id,
        cardId = card.id,
        totalDue = totalDue,
        minDue = totalDue / 10,
        dueDate = dueDate,
        statementLabel = "August 2026",
        generatedAt = nowMillis,
        isPaid = isPaid,
    )

    private fun buildVm(cards: List<CreditCard>, bills: List<CardBill>): CardsViewModel {
        every { repository.getAllCards() } returns flowOf(cards)
        every { repository.getAllBills() } returns flowOf(bills)
        return CardsViewModel(repository)
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
}
