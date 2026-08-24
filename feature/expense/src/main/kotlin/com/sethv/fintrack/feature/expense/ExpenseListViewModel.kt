package com.sethv.fintrack.feature.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExpenseListUiState(
    val transactions: List<Transaction> = emptyList(),
    val selectedCategory: ExpenseCategory? = null,
    val searchQuery: String = "",
    val totalAmount: Double = 0.0,
)

@HiltViewModel
class ExpenseListViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<ExpenseCategory?>(null)
    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<ExpenseListUiState> = combine(
        transactionRepository.getAllTransactions(),
        selectedCategory,
        searchQuery,
    ) { transactions, category, query ->
        val normalizedQuery = query.trim()
        val filtered = transactions
            .filter { category == null || it.category == category }
            .filter { matchesSearch(it, normalizedQuery) }
        val sorted = filtered.sortedByDescending { it.dateTime }
        ExpenseListUiState(
            transactions = sorted,
            selectedCategory = category,
            searchQuery = query,
            // "Total Spending" must not count incoming credits (salary/refunds).
            totalAmount = sorted.filter { it.type == TransactionType.DEBIT }.sumOf { it.amount },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExpenseListUiState(),
    )

    fun setFilter(category: ExpenseCategory?) {
        selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    /** Users previously had NO way to remove a wrongly accepted transaction. */
    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(id)
        }
    }

    private fun matchesSearch(transaction: Transaction, query: String): Boolean {
        if (query.isEmpty()) return true
        return transaction.merchant.contains(query, ignoreCase = true) ||
            transaction.notes.contains(query, ignoreCase = true) ||
            transaction.bank.contains(query, ignoreCase = true) ||
            transaction.category.displayName.contains(query, ignoreCase = true)
    }
}
