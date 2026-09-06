package com.sethv.fintrack.feature.expense

import app.cash.turbine.test
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddTransactionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: TransactionRepository
    private val zone = ZoneId.of("Asia/Kolkata")
    private val fixedClock = Clock.fixed(LocalDate.of(2026, 5, 15).atTime(12, 0).atZone(zone).toInstant(), zone)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        coEvery { repository.insertTransaction(any()) } returns 1L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(clock: Clock = fixedClock) = AddTransactionViewModel(repository, clock)

    @Test
    fun `initial state is not savable and date is today at noon`() = runTest(testDispatcher) {
        val vm = buildVm()
        val state = vm.uiState.value
        assertFalse(state.canSave)
        assertEquals("", state.amountText)
        assertEquals("", state.merchant)
        assertEquals(ExpenseCategory.OTHERS, state.category)
        assertEquals(TransactionType.DEBIT, state.type)
        val expectedDate = LocalDate.of(2026, 5, 15).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expectedDate, state.dateMillis)
    }

    @Test
    fun `canSave true only when amount greater than zero and merchant not blank`() = runTest(testDispatcher) {
        val vm = buildVm()
        vm.updateAmountText("100")
        vm.updateMerchant("  ")
        assertFalse(vm.uiState.value.canSave)
        vm.updateMerchant("Swiggy")
        assertTrue(vm.uiState.value.canSave)
        vm.updateAmountText("0")
        assertFalse(vm.uiState.value.canSave)
        vm.updateAmountText("0.01")
        assertTrue(vm.uiState.value.canSave)
        vm.updateMerchant("")
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun `updateAmountText filters invalid input`() = runTest(testDispatcher) {
        val vm = buildVm()
        vm.updateAmountText("12.34")
        assertEquals("12.34", vm.uiState.value.amountText)
        vm.updateAmountText("12.34.5") // invalid second dot
        assertEquals("12.34", vm.uiState.value.amountText)
        vm.updateAmountText("abc")
        assertEquals("12.34", vm.uiState.value.amountText)
        vm.updateAmountText("")
        assertEquals("", vm.uiState.value.amountText)
        vm.updateAmountText("0")
        assertEquals("0", vm.uiState.value.amountText)
    }

    @Test
    fun `update flows for category type date notes`() = runTest(testDispatcher) {
        val vm = buildVm()
        vm.updateCategory(ExpenseCategory.FOOD)
        vm.updateType(TransactionType.CREDIT)
        vm.updateNotes("lunch with team")
        val newDate = LocalDate.of(2026, 1, 10).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        vm.updateDate(newDate)
        val state = vm.uiState.value
        assertEquals(ExpenseCategory.FOOD, state.category)
        assertEquals(TransactionType.CREDIT, state.type)
        assertEquals("lunch with team", state.notes)
        assertEquals(newDate, state.dateMillis)
    }

    @Test
    fun `save inserts trimmed merchant and notes with Manual bank`() = runTest(testDispatcher) {
        val vm = buildVm()
        vm.updateAmountText("250.5")
        vm.updateMerchant("  Zepto  ")
        vm.updateCategory(ExpenseCategory.GROCERIES)
        vm.updateType(TransactionType.DEBIT)
        vm.updateNotes("  quick delivery  ")
        coEvery { repository.insertTransaction(any()) } returns 42L

        vm.saved.test {
            vm.save()
            advanceUntilIdle()
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) {
            repository.insertTransaction(withArg { txn ->
                assertEquals(250.5, txn.amount, 0.01)
                assertEquals("Zepto", txn.merchant)
                assertEquals(ExpenseCategory.GROCERIES, txn.category)
                assertEquals(TransactionType.DEBIT, txn.type)
                assertEquals("Manual", txn.bank)
                assertEquals("quick delivery", txn.notes)
                assertEquals("", txn.smsBody)
            })
        }
    }

    @Test
    fun `save does not insert when canSave false`() = runTest(testDispatcher) {
        val vm = buildVm()
        vm.updateAmountText("")
        vm.updateMerchant("")
        vm.save()
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.insertTransaction(any()) }
    }

    @Test
    fun `save sets isSaving and blocks double save`() = runTest(testDispatcher) {
        val vm = buildVm()
        vm.updateAmountText("100")
        vm.updateMerchant("Test")
        // Make repository hang
        coEvery { repository.insertTransaction(any()) } coAnswers {
            kotlinx.coroutines.delay(5000)
            1L
        }
        vm.save()
        // isSaving should be true immediately
        assertTrue(vm.uiState.value.isSaving)
        // second save should be ignored
        vm.save()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.insertTransaction(any()) }
    }

    @Test
    fun `save failure resets isSaving`() = runTest(testDispatcher) {
        val vm = buildVm()
        vm.updateAmountText("100")
        vm.updateMerchant("Test")
        coEvery { repository.insertTransaction(any()) } throws RuntimeException("db full")
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSaving)
    }
}
