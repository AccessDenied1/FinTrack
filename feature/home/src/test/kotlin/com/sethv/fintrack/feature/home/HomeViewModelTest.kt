package com.sethv.fintrack.feature.home

import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType
import io.mockk.coEvery
import io.mockk.mockk
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.data.repository.CreditCardRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val repository: TransactionRepository = mockk()
    private val pendingRepository: com.sethv.fintrack.core.data.repository.PendingTransactionRepository = mockk()
    private val creditCardRepository: CreditCardRepository = mockk()
    private val database: com.sethv.fintrack.core.database.FinTrackDatabase = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * The VM's midnight-ticker schedules an endless chain of virtual delays;
     * runTest's final task drain would chase them forever. Cancelling the
     * scope right away is safe: assertions go through the pure computeUiState.
     */
    private fun buildVm(clock: Clock): HomeViewModel {
        coEvery { repository.getAllTransactions() } returns flowOf(emptyList())
        coEvery { creditCardRepository.getNextUnpaidBill() } returns flowOf(null)
        return HomeViewModel(repository, pendingRepository, creditCardRepository, database, clock).also { vm ->
            vm.viewModelScope.coroutineContext.cancelChildren()
        }
    }

    private fun clockAt(year: Int, month: Int, day: Int, hour: Int = 10): Clock =
        Clock.fixed(LocalDate.of(year, month, day).atTime(hour, 0).atZone(zone).toInstant(), zone)

    private fun transaction(
        id: Long,
        amount: Double,
        dateTimeMs: Long,
        type: TransactionType = TransactionType.DEBIT,
        category: ExpenseCategory = ExpenseCategory.FOOD,
    ) = Transaction(
        id = id,
        amount = amount,
        merchant = "m$id",
        category = category,
        type = type,
        dateTime = dateTimeMs,
    )

    private fun ms(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `monthly total counts only debits inside current month`() = runTest(testDispatcher) {
        val vm = buildVm(clockAt(2026, 7, 15))
        val all = listOf(
            transaction(1, 500.0, ms(2026, 7, 3)),
            transaction(2, 9_000.0, ms(2026, 7, 10), TransactionType.CREDIT), // salary
            transaction(3, 250.0, ms(2026, 6, 28)), // last month
            transaction(4, 120.0, ms(2026, 7, 14)),
            transaction(5, 999.0, ms(2026, 8, 1)), // next month
        )

        val state = vm.computeUiState(all)

        assertEquals(620.0, state.monthlyTotal, 0.01)
    }

    @Test
    fun `previous period compares same day-span not full previous month`() = runTest(testDispatcher) {
        // Today = Jul 15 → comparable window is Jun 1..Jun 16 (exclusive).
        val vm = buildVm(clockAt(2026, 7, 15))
        val all = listOf(
            transaction(1, 1_000.0, ms(2026, 6, 5)), // inside comparable window
            transaction(2, 2_000.0, ms(2026, 6, 20)), // AFTER the window — must be ignored
            transaction(3, 50.0, ms(2026, 5, 30)), // too old
        )

        val state = vm.computeUiState(all)

        assertEquals(1_000.0, state.previousMonthTotal, 0.01)
    }

    @Test
    fun `comparable window clamps when today exceeds previous month length`() = runTest(testDispatcher) {
        // Mar 31 → previous window would run past Feb; clamped to Mar 1.
        val vm = buildVm(clockAt(2026, 3, 31))
        val all = listOf(
            transaction(1, 700.0, ms(2026, 2, 28)), // still inside Feb
            transaction(2, 300.0, ms(2026, 3, 1)), // clamp boundary → excluded
        )

        val state = vm.computeUiState(all)

        assertEquals(700.0, state.previousMonthTotal, 0.01)
    }

    @Test
    fun `windows move with the clock - not frozen at construction`() = runTest(testDispatcher) {
        // Placed inside the first days of June so they also fall inside July's
        // like-for-like comparable window (Jun 1..Jun 6).
        val juneTransactions = listOf(
            transaction(1, 400.0, ms(2026, 6, 2)),
            transaction(2, 60.0, ms(2026, 6, 3)),
        )

        // Constructed in June: those txns are current-month spending.
        val juneVm = buildVm(clockAt(2026, 6, 20))
        assertEquals(460.0, juneVm.computeUiState(juneTransactions).monthlyTotal, 0.01)
        assertEquals(0.0, juneVm.computeUiState(juneTransactions).previousMonthTotal, 0.01)

        // Same data after rollover (July): the rows are now LAST month.
        val julyVm = buildVm(clockAt(2026, 7, 5))
        assertEquals(0.0, julyVm.computeUiState(juneTransactions).monthlyTotal, 0.01)
        assertEquals(460.0, julyVm.computeUiState(juneTransactions).previousMonthTotal, 0.01)
    }

    @Test
    fun `category breakdown percentages are relative to monthly debits`() = runTest(testDispatcher) {
        val vm = buildVm(clockAt(2026, 7, 15))
        val all = listOf(
            transaction(1, 750.0, ms(2026, 7, 3), category = ExpenseCategory.FOOD),
            transaction(2, 250.0, ms(2026, 7, 4), category = ExpenseCategory.TRANSPORT),
            transaction(3, 1_000.0, ms(2026, 7, 5), TransactionType.CREDIT, ExpenseCategory.SHOPPING),
        )

        val state = vm.computeUiState(all)

        assertEquals(2, state.categoryBreakdown.size)
        assertEquals(75f, state.categoryBreakdown[0].percentage)
        assertEquals(25f, state.categoryBreakdown[1].percentage)
        assertEquals(ExpenseCategory.FOOD, state.categoryBreakdown[0].category)
    }

    @Test
    fun `recent transactions capped at five`() = runTest(testDispatcher) {
        val vm = buildVm(clockAt(2026, 7, 15))
        val all = (1L..8L).map { transaction(it, it * 10.0, ms(2026, 7, 1) + it) }

        val state = vm.computeUiState(all)

        assertEquals(5, state.recentTransactions.size)
    }

    @Test
    fun `daily trend covers last seven days oldest first with debits only`() = runTest(testDispatcher) {
        // Clock fixed at Jul 15 → window is Jul 9..Jul 15 inclusive.
        val vm = buildVm(clockAt(2026, 7, 15, hour = 12))
        val all = listOf(
            transaction(1, 100.0, ms(2026, 7, 8) + 5_000L), // before window
            transaction(2, 200.0, ms(2026, 7, 10)), // Jul 10 (index 1 from Jul 9)
            transaction(3, 50.0, ms(2026, 7, 15) + 3_600_000L), // today
            transaction(4, 900.0, ms(2026, 7, 15), TransactionType.CREDIT), // excluded
            transaction(5, 70.0, ms(2026, 7, 10) + 1_000L), // same day as #2
        )

        val trend = vm.computeUiState(all).dailySpendingTrend

        // Index 0 = Jul 9 … index 6 = Jul 15. Jul 10 has both debits.
        assertEquals(listOf(0.0, 270.0, 0.0, 0.0, 0.0, 0.0, 50.0), trend)
    }

    @Test
    fun `selected past month shows full month totals vs full previous month`() = runTest(testDispatcher) {
        // Viewing JUNE while today is Jul 15: June compares FULL June vs FULL May.
        val vm = buildVm(clockAt(2026, 7, 15))
        val all = listOf(
            transaction(1, 500.0, ms(2026, 6, 3)),
            transaction(2, 250.0, ms(2026, 6, 28)),
            transaction(3, 1_000.0, ms(2026, 5, 20)),
            transaction(4, 777.0, ms(2026, 7, 4)), // current-month noise, excluded
        )
        val june = java.time.YearMonth.of(2026, 6)

        val state = vm.computeUiState(all, june)

        assertEquals(false, state.isCurrentMonth)
        assertEquals(java.time.YearMonth.of(2026, 6), state.selectedMonth)
        assertEquals(750.0, state.monthlyTotal, 0.01)
        assertEquals(1_000.0, state.previousMonthTotal, 0.01)
        // Past months use the FULL month for per-day average (30 days in June).
        assertEquals(25.0, state.avgPerDay, 0.01)
    }

    @Test
    fun `quick stats reflect selected month debits`() = runTest(testDispatcher) {
        val vm = buildVm(clockAt(2026, 7, 15))
        val all = listOf(
            transaction(1, 300.0, ms(2026, 7, 3)),
            transaction(2, 120.0, ms(2026, 7, 9)),
            transaction(3, 80.0, ms(2026, 7, 14)),
            transaction(4, 999.0, ms(2026, 7, 10), TransactionType.CREDIT),
        )

        val state = vm.computeUiState(all)

        assertEquals(500.0, state.monthlyTotal, 0.01)
        assertEquals(300.0, state.biggestExpense, 0.01)
        assertEquals(3, state.monthTxnCount)
        assertEquals(true, state.isCurrentMonth)
        // Jul 15 clock → 15 elapsed days.
        assertEquals(33.3333, state.avgPerDay, 0.001)
        // Projection: 33.33/day pace × 31 July days ≈ 1033.
        assertEquals(500.0 / 15 * 31, state.monthEndProjection, 0.01)
    }
}
