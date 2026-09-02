package com.sethv.fintrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategorySpending(
    val category: com.sethv.fintrack.core.model.ExpenseCategory,
    val amount: Double,
    val percentage: Float,
)

data class HomeUiState(
    val recentTransactions: List<Transaction> = emptyList(),
    val monthlyTotal: Double = 0.0,
    val previousMonthTotal: Double = 0.0,
    val categoryBreakdown: List<CategorySpending> = emptyList(),
    /** Daily debit totals for the last 7 calendar days, oldest first. */
    val dailySpendingTrend: List<Double> = emptyList(),
    /** Month the summary cards describe; null means "current month". */
    val selectedMonth: YearMonth? = null,
    val isCurrentMonth: Boolean = true,
    val avgPerDay: Double = 0.0,
    val biggestExpense: Double = 0.0,
    val monthTxnCount: Int = 0,
    /** Current month: avg/day pace × days in month. Past months: the full total. */
    val monthEndProjection: Double = 0.0,
    /** Nearest unpaid card bill — surfaced as a "pay soon" alert on Home. */
    val upcomingCardBill: com.sethv.fintrack.core.model.CardBill? = null,
    val hasSmsPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val creditCardRepository: com.sethv.fintrack.core.data.repository.CreditCardRepository,
    private val clock: Clock,
) : ViewModel() {

    // Bumped at every local-midnight so month windows recompute after rollover
    // even when no new transactions arrive to trigger a Flow emission.
    private val dayTick = MutableStateFlow(0)

    /** Null = live current month; otherwise the user is time-travelling. */
    private val selectedMonth = MutableStateFlow<YearMonth?>(null)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeTransactions()
        scheduleDayTick()
    }

    fun onPreviousMonth() {
        val current = YearMonth.now(clock)
        selectedMonth.value = (selectedMonth.value ?: current).minusMonths(1).coerceAtLeast(current.minusMonths(24))
    }

    fun onNextMonth() {
        val current = YearMonth.now(clock)
        val target = (selectedMonth.value ?: current).plusMonths(1)
        selectedMonth.value = if (target > current) null else target
    }

    fun onCurrentMonthSelected() {
        selectedMonth.value = null
    }

    fun updatePermissions(hasSmsPermission: Boolean, hasNotificationPermission: Boolean) {
        _uiState.update {
            it.copy(
                hasSmsPermission = hasSmsPermission,
                hasNotificationPermission = hasNotificationPermission,
            )
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasSmsPermission = granted) }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasNotificationPermission = granted) }
    }

    private fun observeTransactions() {
        viewModelScope.launch {
            combine(
                transactionRepository.getAllTransactions(),
                creditCardRepository.getNextUnpaidBill(),
                dayTick,
                selectedMonth,
            ) { all, nextBill, _, month ->
                computeUiState(all, month).copy(upcomingCardBill = nextBill)
            }.collect { state ->
                _uiState.update { current ->
                    state.copy(
                        hasSmsPermission = current.hasSmsPermission,
                        hasNotificationPermission = current.hasNotificationPermission,
                    )
                }
            }
        }
    }

    private fun scheduleDayTick() {
        viewModelScope.launch {
            // delay() throws on scope cancellation, ending the loop.
            while (true) {
                delay(millisUntilNextDay())
                dayTick.value++
            }
        }
    }

    internal fun computeUiState(all: List<Transaction>, selected: YearMonth? = null): HomeUiState {
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val currentMonth = YearMonth.from(today)
        val target = selected ?: currentMonth
        val isCurrentMonth = target == currentMonth

        // Month windows are derived per-emission (never frozen at init) and are
        // half-open [start, end) ranges built from zoned instants — gap-free.
        val windowStart = target.atDay(1)
        val windowEnd = target.plusMonths(1).atDay(1)
        val previousStart = target.minusMonths(1).atDay(1)

        fun epochMillis(date: LocalDate): Long =
            date.atStartOfDay(zone).toInstant().toEpochMilli()

        // Comparison span:
        //  - current month → like-for-like (same day-span as elapsed so far);
        //  - past month    → full previous calendar month.
        val previousEnd =
            if (isCurrentMonth) {
                previousStart.plusDays(today.dayOfMonth.toLong())
                    .coerceAtMost(windowStart)
            } else {
                windowStart
            }

        val monthlyDebits = all.filter {
            it.type == TransactionType.DEBIT &&
                it.dateTime >= epochMillis(windowStart) &&
                it.dateTime < epochMillis(windowEnd)
        }
        val previousDebits = all.filter {
            it.type == TransactionType.DEBIT &&
                it.dateTime >= epochMillis(previousStart) &&
                it.dateTime < epochMillis(previousEnd)
        }
        val monthlyTotal = monthlyDebits.sumOf { it.amount }
        val previousMonthTotal = previousDebits.sumOf { it.amount }

        // Quick stats for the hero card.
        val daysElapsed =
            if (isCurrentMonth) today.dayOfMonth else target.lengthOfMonth()
        val avgPerDay = if (daysElapsed > 0) monthlyTotal / daysElapsed else 0.0
        val biggestExpense = monthlyDebits.maxOfOrNull { it.amount } ?: 0.0
        val monthEndProjection =
            if (isCurrentMonth) avgPerDay * target.lengthOfMonth() else monthlyTotal

        // Last 7 calendar days (incl. today), oldest → newest, debits only.
        // Always relative to TODAY regardless of the selected month.
        val dailySpendingTrend = (6L downTo 0L).map { daysAgo ->
            val date = today.minusDays(daysAgo)
            val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            all.filter {
                it.type == TransactionType.DEBIT &&
                    it.dateTime >= startMs &&
                    it.dateTime < endMs
            }.sumOf { it.amount }
        }

        val categoryBreakdown = monthlyDebits
            .groupBy { it.category }
            .map { (category, transactions) ->
                val categoryTotal = transactions.sumOf { it.amount }
                CategorySpending(
                    category = category,
                    amount = categoryTotal,
                    percentage = if (monthlyTotal > 0) (categoryTotal / monthlyTotal * 100).toFloat() else 0f,
                )
            }
            .sortedByDescending { it.amount }

        return HomeUiState(
            recentTransactions = all.take(5),
            monthlyTotal = monthlyTotal,
            previousMonthTotal = previousMonthTotal,
            categoryBreakdown = categoryBreakdown,
            dailySpendingTrend = dailySpendingTrend,
            selectedMonth = target.takeIf { !isCurrentMonth },
            isCurrentMonth = isCurrentMonth,
            avgPerDay = avgPerDay,
            biggestExpense = biggestExpense,
            monthTxnCount = monthlyDebits.size,
            monthEndProjection = monthEndProjection,
        )
    }

    private fun millisUntilNextDay(): Long {
        val now = clock.instant()
        val nextMidnight = now.atZone(clock.zone).toLocalDate()
            .plusDays(1).atStartOfDay(clock.zone).toInstant()
        // +1s buffer so we never wake exactly on the boundary and compute the old day.
        return Duration.between(now, nextMidnight).plusSeconds(1).toMillis()
    }
}
