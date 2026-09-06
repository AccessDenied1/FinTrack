package com.sethv.fintrack.feature.settings

import com.sethv.fintrack.core.data.repository.CreditCardRepository
import com.sethv.fintrack.core.database.FinTrackDatabase
import com.sethv.fintrack.core.model.CreditCard
import io.mockk.coVerify
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
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var database: FinTrackDatabase
    private lateinit var repository: CreditCardRepository

    private val hdfc = CreditCard(id = 1, bankName = "HDFC", lastFour = "4521", creditLimitOverride = 100_000.0)
    private val icici = CreditCard(id = 2, bankName = "ICICI", lastFour = "8877")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        database = mockk(relaxed = true)
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateLimit delegates to repository`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(database, repository)

        vm.onUpdateLimit(7L, 100_000.0)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.updateLimit(7L, 100_000.0) }
    }

    @Test
    fun `onUpdateLimit with null clears the override`() = runTest(testDispatcher) {
        val vm = SettingsViewModel(database, repository)

        vm.onUpdateLimit(7L, null)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.updateLimit(7L, null) }
    }

    @Test
    fun `cards flow propagates to StateFlow`() = runTest(testDispatcher) {
        every { repository.getAllCards() } returns flowOf(emptyList(), listOf(hdfc, icici))
        val vm = SettingsViewModel(database, repository)
        backgroundScope.launch { vm.cards.collect {} }
        advanceUntilIdle()

        assertEquals(listOf(hdfc, icici), vm.cards.value)
    }
}
