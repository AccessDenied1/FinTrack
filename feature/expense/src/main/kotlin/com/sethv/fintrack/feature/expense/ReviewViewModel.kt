package com.sethv.fintrack.feature.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.data.repository.PendingTransactionRepository
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.PendingTransaction
import com.sethv.fintrack.core.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val isLoading: Boolean = true,
    val pendingTransaction: PendingTransaction? = null,
    val amount: Double = 0.0,
    val merchant: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHERS,
    val notes: String = "",
    val error: String? = null,
    val isSaving: Boolean = false,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pendingTransactionRepository: PendingTransactionRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val pendingId: Long = savedStateHandle.get<Long>(ARG_PENDING_ID) ?: 0L

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val _accepted = MutableSharedFlow<Unit>()
    val accepted: SharedFlow<Unit> = _accepted.asSharedFlow()

    private val _rejected = MutableSharedFlow<Unit>()
    val rejected: SharedFlow<Unit> = _rejected.asSharedFlow()

    init {
        loadTransaction()
    }

    fun loadTransaction() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val pending = pendingTransactionRepository.getPendingById(pendingId)
                if (pending == null) {
                    _uiState.update {
                        it.copy(isLoading = false, error = "Transaction not found")
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingTransaction = pending,
                            amount = pending.amount,
                            merchant = pending.merchant,
                            category = pending.category,
                            notes = pending.notes,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load transaction")
                }
            }
        }
    }

    fun updateAmount(amount: Double) {
        _uiState.update { it.copy(amount = amount) }
    }

    fun updateMerchant(merchant: String) {
        _uiState.update { it.copy(merchant = merchant) }
    }

    fun updateCategory(category: ExpenseCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun acceptTransaction() {
        val pending = _uiState.value.pendingTransaction ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val transaction = Transaction(
                    amount = _uiState.value.amount,
                    merchant = _uiState.value.merchant,
                    category = _uiState.value.category,
                    type = pending.type,
                    dateTime = pending.dateTime,
                    bank = pending.bank,
                    notes = _uiState.value.notes,
                    smsBody = pending.smsBody,
                    createdAt = pending.createdAt,
                )
                transactionRepository.insertTransaction(transaction)
                pendingTransactionRepository.acceptPending(pending.id)
                _uiState.update { it.copy(isSaving = false) }
                _accepted.emit(Unit)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = e.message ?: "Failed to accept transaction")
                }
            }
        }
    }

    fun rejectTransaction() {
        val pending = _uiState.value.pendingTransaction ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                pendingTransactionRepository.rejectPending(pending.id)
                _uiState.update { it.copy(isSaving = false) }
                _rejected.emit(Unit)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = e.message ?: "Failed to reject transaction")
                }
            }
        }
    }

    companion object {
        const val ARG_PENDING_ID = "pendingId"
    }
}
