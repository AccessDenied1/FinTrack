package com.sethv.fintrack.feature.expense

import app.cash.turbine.test
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun transaction(
        id: Long,
        amount: Double,
        type: TransactionType,
        category: ExpenseCategory = ExpenseCategory.FOOD,
    ) = Transaction(
        id = id,
        amount = amount,
        merchant = "m$id",
        category = category,
        type = type,
        dateTime = id * 1_000_000L,
    )

    @Test
    fun `total spending excludes credit transactions`() = runTest(testDispatcher) {
        every { repository.getAllTransactions() } returns flowOf(
            listOf(
                transaction(1, 300.0, TransactionType.DEBIT),
                transaction(2, 5_000.0, TransactionType.CREDIT), // salary/refund
                transaction(3, 200.0, TransactionType.DEBIT),
            ),
        )

        val viewModel = ExpenseListViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(500.0, viewModel.uiState.value.totalAmount, 0.01)
    }

    @Test
    fun `category filter applies to both list and total`() = runTest(testDispatcher) {
        every { repository.getAllTransactions() } returns flowOf(
            listOf(
                transaction(1, 300.0, TransactionType.DEBIT, ExpenseCategory.FOOD),
                transaction(2, 120.0, TransactionType.DEBIT, ExpenseCategory.TRANSPORT),
                transaction(3, 900.0, TransactionType.DEBIT, ExpenseCategory.FOOD),
            ),
        )

        val viewModel = ExpenseListViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setFilter(ExpenseCategory.FOOD)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.transactions.size)
        assertEquals(1200.0, state.totalAmount, 0.01)
    }

    @Test
    fun `search filters by merchant case-insensitively`() = runTest(testDispatcher) {
        every { repository.getAllTransactions() } returns flowOf(
            listOf(
                transaction(1, 300.0, TransactionType.DEBIT, ExpenseCategory.FOOD).copy(merchant = "Swiggy Order"),
                transaction(2, 120.0, TransactionType.DEBIT, ExpenseCategory.TRANSPORT).copy(merchant = "Uber India"),
            ),
        )

        val viewModel = ExpenseListViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setSearchQuery("swiggy")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.transactions.size)
        assertEquals("Swiggy Order", state.transactions.first().merchant)
    }
}
