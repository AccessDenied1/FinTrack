package com.sethv.fintrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
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
    val categoryBreakdown: List<CategorySpending> = emptyList(),
    val hasSmsPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadTransactions()
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

    private fun loadTransactions() {
        val (startTime, endTime) = currentMonthRange()
        viewModelScope.launch {
            combine(
                transactionRepository.getAllTransactions(),
                transactionRepository.getTransactionsByDateRange(startTime, endTime),
            ) { allTransactions, monthlyTransactions ->
                val debitTransactions = monthlyTransactions
                    .filter { it.type == TransactionType.DEBIT }
                val monthlyTotal = debitTransactions.sumOf { it.amount }

                val categoryBreakdown = debitTransactions
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

                HomeUiState(
                    recentTransactions = allTransactions.take(5),
                    monthlyTotal = monthlyTotal,
                    categoryBreakdown = categoryBreakdown,
                    hasSmsPermission = _uiState.value.hasSmsPermission,
                    hasNotificationPermission = _uiState.value.hasNotificationPermission,
                )
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

    private fun currentMonthRange(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }
}
