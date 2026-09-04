package com.sethv.fintrack.feature.expense

import app.cash.turbine.test
import com.sethv.fintrack.core.data.repository.PendingTransactionRepository
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.PendingStatus
import com.sethv.fintrack.core.model.PendingTransaction
import com.sethv.fintrack.core.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ReviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var pendingRepository: PendingTransactionRepository
    private lateinit var transactionRepository: TransactionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pendingRepository = mockk(relaxed = true)
        transactionRepository = mockk(relaxed = true)
        coEvery { pendingRepository.getPendingById(any()) } returns pendingTransaction()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun pendingTransaction() = PendingTransaction(
        id = 42,
        amount = 350.0,
        merchant = "SWIGGY",
        category = ExpenseCategory.FOOD,
        type = TransactionType.DEBIT,
        dateTime = 1_700_000_000_000L,
        bank = "HDFC",
        notes = "",
        smsBody = "sms body",
        status = PendingStatus.PENDING,
    )

    private fun kotlinx.coroutines.test.TestScope.buildViewModel(): ReviewViewModel {
        val vm = ReviewViewModel(
            savedStateHandle = androidx.lifecycle.SavedStateHandle(mapOf(ReviewViewModel.ARG_PENDING_ID to 42L)),
            pendingTransactionRepository = pendingRepository,
            transactionRepository = transactionRepository,
        )
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `rapid double accept only hits the repository once`() = runTest(testDispatcher) {
        // Gate the repository call open so both clicks queue before completion.
        val gate = CompletableDeferred<Long>()
        coEvery { transactionRepository.acceptPending(any(), any(), any(), any(), any(), any()) } coAnswers { gate.await() }

        val vm = buildViewModel()
        vm.acceptTransaction()
        vm.acceptTransaction() // second tap while first is in flight

        gate.complete(1001L)
        advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.acceptPending(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `accepting an already-handled row shows error instead of success event`() = runTest(testDispatcher) {
        coEvery {
            transactionRepository.acceptPending(any(), any(), any(), any(), any(), any())
        } returns TransactionRepository.ALREADY_HANDLED

        val vm = buildViewModel()
        vm.accepted.test {
            vm.acceptTransaction()
            advanceUntilIdle()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals("This transaction was already reviewed.", vm.uiState.value.error)
        assertEquals(false, vm.uiState.value.isSaving)
    }

    @Test
    fun `successful accept emits accepted event`() = runTest(testDispatcher) {
        coEvery { transactionRepository.acceptPending(any(), any(), any(), any(), any(), any()) } returns 777L

        val vm = buildViewModel()
        val loaded = vm.uiState.value.pendingTransaction!!
        vm.accepted.test {
            vm.acceptTransaction()
            advanceUntilIdle()
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) {
            transactionRepository.acceptPending(
                pending = loaded,
                amount = 350.0,
                merchant = "SWIGGY",
                category = ExpenseCategory.FOOD,
                type = TransactionType.DEBIT,
                notes = "",
            )
        }
    }

    @Test
    fun `reject emits rejected event`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        vm.rejected.test {
            vm.rejectTransaction()
            advanceUntilIdle()
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { pendingRepository.rejectPending(42L) }
    }
}
