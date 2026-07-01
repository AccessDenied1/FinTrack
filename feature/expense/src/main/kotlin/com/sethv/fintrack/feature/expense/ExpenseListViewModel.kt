package com.sethv.fintrack.feature.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ExpenseListUiState(
    val transactions: List<Transaction> = emptyList(),
    val selectedCategory: ExpenseCategory? = null,
    val totalAmount: Double = 0.0,
)

@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<ExpenseCategory?>(null)

    val uiState: StateFlow<ExpenseListUiState> = combine(
        transactionRepository.getAllTransactions(),
        selectedCategory,
    ) { transactions, category ->
        val filtered = if (category == null) {
            transactions
        } else {
            transactions.filter { it.category == category }
        }
        val sorted = filtered.sortedByDescending { it.dateTime }
        ExpenseListUiState(
            transactions = sorted,
            selectedCategory = category,
            totalAmount = sorted.sumOf { it.amount },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExpenseListUiState(),
    )

    fun setFilter(category: ExpenseCategory?) {
        selectedCategory.value = category
    }

    fun refresh() {
        selectedCategory.value = selectedCategory.value
    }
}
