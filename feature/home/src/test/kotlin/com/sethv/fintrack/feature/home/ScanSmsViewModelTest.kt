package com.sethv.fintrack.feature.home

import app.cash.turbine.test
import com.sethv.fintrack.service.sms.HistoricalSmsProcessor
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
class ScanSmsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var processor: HistoricalSmsProcessor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        processor = mockk()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `startScan success updates to COMPLETED and emits nav when count greater than zero`() = runTest(testDispatcher) {
        coEvery { processor.scanAndProcess() } returns 5
        val vm = ScanSmsViewModel(processor, testDispatcher)
        assertEquals(ScanStatus.IDLE, vm.scanState.value.status)
        vm.navEvents.test {
            vm.startScan()
            advanceUntilIdle()
            assertEquals(ScanStatus.COMPLETED, vm.scanState.value.status)
            assertEquals(5, vm.scanState.value.transactionsFound)
            assertEquals(ScanNavEvent.NavigateToReview(5), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { processor.scanAndProcess() }
    }

    @Test
    fun `startScan with zero count completes but does not navigate`() = runTest(testDispatcher) {
        coEvery { processor.scanAndProcess() } returns 0
        val vm = ScanSmsViewModel(processor, testDispatcher)
        vm.navEvents.test {
            vm.startScan()
            advanceUntilIdle()
            assertEquals(ScanStatus.COMPLETED, vm.scanState.value.status)
            assertEquals(0, vm.scanState.value.transactionsFound)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startScan error sets ERROR status`() = runTest(testDispatcher) {
        coEvery { processor.scanAndProcess() } throws RuntimeException("permission")
        val vm = ScanSmsViewModel(processor, testDispatcher)
        vm.startScan()
        advanceUntilIdle()
        assertEquals(ScanStatus.ERROR, vm.scanState.value.status)
    }

    @Test
    fun `startScan ignores double start when already SCANNING`() = runTest(testDispatcher) {
        coEvery { processor.scanAndProcess() } coAnswers {
            kotlinx.coroutines.delay(5000)
            1
        }
        val vm = ScanSmsViewModel(processor, testDispatcher)
        vm.startScan()
        // immediately try second
        vm.startScan()
        advanceUntilIdle()
        coVerify(exactly = 1) { processor.scanAndProcess() }
    }

    @Test
    fun `resetScanState returns to IDLE`() = runTest(testDispatcher) {
        coEvery { processor.scanAndProcess() } returns 2
        val vm = ScanSmsViewModel(processor, testDispatcher)
        vm.startScan()
        advanceUntilIdle()
        assertEquals(ScanStatus.COMPLETED, vm.scanState.value.status)
        vm.resetScanState()
        assertEquals(ScanStatus.IDLE, vm.scanState.value.status)
        assertEquals(0, vm.scanState.value.transactionsFound)
    }

    @Test
    fun `scanState transitions IDLE to SCANNING to COMPLETED`() = runTest(testDispatcher) {
        coEvery { processor.scanAndProcess() } coAnswers {
            1
        }
        val vm = ScanSmsViewModel(processor, testDispatcher)
        vm.scanState.test {
            assertEquals(ScanStatus.IDLE, awaitItem().status)
            vm.startScan()
            assertEquals(ScanStatus.SCANNING, awaitItem().status)
            advanceUntilIdle()
            assertEquals(ScanStatus.COMPLETED, expectMostRecentItem().status)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
