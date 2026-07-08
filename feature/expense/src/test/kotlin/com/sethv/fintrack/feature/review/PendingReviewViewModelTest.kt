package com.sethv.fintrack.feature.review

import app.cash.turbine.test
import com.sethv.fintrack.core.data.repository.PendingTransactionRepository
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.PendingStatus
import com.sethv.fintrack.core.model.PendingTransaction
import com.sethv.fintrack.core.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
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

/**
 * Unit tests for [PendingReviewViewModel]. Focus is on `acceptAll()` — verifying
 * the bulk-accept path replaces the old N+1 of `acceptPending` calls.
 *
 * Each test keeps `uiState` hot via `backgroundScope.launch { uiState.collect {} }`
 * so `stateIn(... WhileSubscribed ...)` activates the upstream `getPending()` flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingReviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var pendingRepository: PendingTransactionRepository
    private lateinit var transactionRepository: TransactionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pendingRepository = mockk(relaxed = true)
        transactionRepository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun pendingTransaction(
        id: Long,
        amount: Double = 100.0,
        category: ExpenseCategory = ExpenseCategory.FOOD,
        type: TransactionType = TransactionType.DEBIT,
    ) = PendingTransaction(
        id = id,
        amount = amount,
        merchant = "merchant $id",
        category = category,
        type = type,
        dateTime = 1_700_000_000_000L,
        bank = "HDFC",
        notes = "",
        smsBody = "raw sms $id",
        status = PendingStatus.PENDING,
    )

    @Test
    fun `acceptAll calls bulk repository once and emits Accepted with item count`() = runTest(testDispatcher) {
        val items = listOf(pendingTransaction(1), pendingTransaction(2), pendingTransaction(3))
        val insertedIds = listOf(101L, 102L, 103L)
        every { pendingRepository.getPending() } returns flowOf(items)
        coEvery { transactionRepository.acceptAllPending(items) } returns insertedIds

        val viewModel = PendingReviewViewModel(pendingRepository, transactionRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.items.size)

        viewModel.events.test {
            viewModel.acceptAll()
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue("expected Accepted event, got $event", event is PendingReviewEvent.Accepted)
            assertEquals(3, (event as PendingReviewEvent.Accepted).count)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { transactionRepository.acceptAllPending(items) }
        // No fall-through to the single-row acceptPending — that was the old N+1 path.
        coVerify(exactly = 0) { transactionRepository.acceptPending(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `acceptAll does nothing when uiState is empty`() = runTest(testDispatcher) {
        every { pendingRepository.getPending() } returns flowOf(emptyList())
        val viewModel = PendingReviewViewModel(pendingRepository, transactionRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isEmpty)

        viewModel.events.test {
            viewModel.acceptAll()
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { transactionRepository.acceptAllPending(any()) }
    }

    @Test
    fun `acceptAll emits Error when bulk repository throws`() = runTest(testDispatcher) {
        val items = listOf(pendingTransaction(1), pendingTransaction(2))
        every { pendingRepository.getPending() } returns flowOf(items)
        coEvery { transactionRepository.acceptAllPending(items) } throws RuntimeException("db boom")

        val viewModel = PendingReviewViewModel(pendingRepository, transactionRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.acceptAll()
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue("expected Error event, got $event", event is PendingReviewEvent.Error)
            assertEquals("db boom", (event as PendingReviewEvent.Error).message)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { transactionRepository.acceptAllPending(items) }
    }

    @Test
    fun `rejectAll calls bulk repository once and emits Rejected with id count`() = runTest(testDispatcher) {
        val items = listOf(pendingTransaction(1), pendingTransaction(2))
        every { pendingRepository.getPending() } returns flowOf(items)
        coEvery { pendingRepository.rejectAllPending(listOf(1L, 2L)) } returns Unit

        val viewModel = PendingReviewViewModel(pendingRepository, transactionRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.rejectAll()
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue("expected Rejected event, got $event", event is PendingReviewEvent.Rejected)
            assertEquals(2, (event as PendingReviewEvent.Rejected).count)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { pendingRepository.rejectAllPending(listOf(1L, 2L)) }
    }

    @Test
    fun `accept (single) calls acceptPending with parsed fields and emits Accepted(1)`() = runTest(testDispatcher) {
        val item = pendingTransaction(id = 7, amount = 250.0, category = ExpenseCategory.GROCERIES)
        every { pendingRepository.getPending() } returns flowOf(listOf(item))
        coEvery { transactionRepository.acceptPending(any(), any(), any(), any(), any()) } returns 999L

        val viewModel = PendingReviewViewModel(pendingRepository, transactionRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.accept(item)
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is PendingReviewEvent.Accepted)
            assertEquals(1, (event as PendingReviewEvent.Accepted).count)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) {
            transactionRepository.acceptPending(
                pending = item,
                amount = 250.0,
                merchant = "merchant 7",
                category = ExpenseCategory.GROCERIES,
                notes = "",
            )
        }
    }

    @Test
    fun `reject (single) calls rejectPending and emits Rejected(1)`() = runTest(testDispatcher) {
        val item = pendingTransaction(id = 7)
        every { pendingRepository.getPending() } returns flowOf(listOf(item))
        coEvery { pendingRepository.rejectPending(7L) } returns Unit

        val viewModel = PendingReviewViewModel(pendingRepository, transactionRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.events.test {
            viewModel.reject(item)
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is PendingReviewEvent.Rejected)
            assertEquals(1, (event as PendingReviewEvent.Rejected).count)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { pendingRepository.rejectPending(7L) }
    }
}