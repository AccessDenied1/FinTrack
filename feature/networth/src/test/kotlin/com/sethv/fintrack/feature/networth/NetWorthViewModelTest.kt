package com.sethv.fintrack.feature.networth

import app.cash.turbine.test
import com.sethv.fintrack.core.data.repository.NetWorthRepository
import com.sethv.fintrack.core.model.NetWorthState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
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
class NetWorthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: NetWorthRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        every { repository.getNetWorthState() } returns flowOf(NetWorthState(1000.0, 1500.0, 2000.0, 1500.0))
        every { repository.hasInitialBalance() } returns flowOf(false)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState reflects repository hasSetInitialBalance false`() = runTest(testDispatcher) {
        val vm = NetWorthViewModel(repository)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hasSetInitialBalance)
        assertEquals(1500.0, vm.uiState.value.netWorth.currentBalance, 0.01)
    }

    @Test
    fun `hasSetInitialBalance true updates state`() = runTest(testDispatcher) {
        every { repository.hasInitialBalance() } returns flowOf(true)
        val vm = NetWorthViewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasSetInitialBalance)
    }

    @Test
    fun `netWorth updates propagate to uiState`() = runTest(testDispatcher) {
        val flow = MutableSharedFlow<NetWorthState>(replay = 1)
        flow.emit(NetWorthState(500.0, 700.0, 1000.0, 800.0))
        every { repository.getNetWorthState() } returns flow
        every { repository.hasInitialBalance() } returns flowOf(true)
        val vm = NetWorthViewModel(repository)
        advanceUntilIdle()
        assertEquals(700.0, vm.uiState.value.netWorth.currentBalance, 0.01)
        flow.emit(NetWorthState(500.0, 900.0, 1200.0, 800.0))
        advanceUntilIdle()
        assertEquals(900.0, vm.uiState.value.netWorth.currentBalance, 0.01)
    }

    @Test
    fun `show and dismiss dialog toggles flag`() = runTest(testDispatcher) {
        val vm = NetWorthViewModel(repository)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showSetBalanceDialog)
        vm.showSetBalanceDialog()
        assertTrue(vm.uiState.value.showSetBalanceDialog)
        vm.dismissSetBalanceDialog()
        assertFalse(vm.uiState.value.showSetBalanceDialog)
    }

    @Test
    fun `setInitialBalance calls repository and hides dialog`() = runTest(testDispatcher) {
        val vm = NetWorthViewModel(repository)
        coEvery { repository.setInitialBalance(any()) } returns Unit
        vm.showSetBalanceDialog()
        assertTrue(vm.uiState.value.showSetBalanceDialog)
        vm.setInitialBalance(2500.0)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.setInitialBalance(2500.0) }
        assertFalse(vm.uiState.value.showSetBalanceDialog)
    }

    @Test
    fun `setInitialBalance with zero still calls repository`() = runTest(testDispatcher) {
        val vm = NetWorthViewModel(repository)
        coEvery { repository.setInitialBalance(any()) } returns Unit
        vm.setInitialBalance(0.0)
        advanceUntilIdle()
        coVerify { repository.setInitialBalance(0.0) }
    }
}
