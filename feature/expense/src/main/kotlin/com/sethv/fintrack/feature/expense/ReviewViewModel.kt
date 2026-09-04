package com.sethv.fintrack.feature.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.data.repository.PendingTransactionRepository
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.PendingTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReviewUiState(
    val isLoading: Boolean = true,
    val pendingTransaction: PendingTransaction? = null,
    val amount: Double = 0.0,
    val merchant: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHERS,
    /** Editable — the SMS heuristics can misclassify the direction. */
    val type: com.sethv.fintrack.core.model.TransactionType = com.sethv.fintrack.core.model.TransactionType.DEBIT,
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

    // Flipped synchronously in the click handler so a rapid double-tap can
    // never enqueue two repository calls (coroutine-internal flags race).
    private val actionInFlight = java.util.concurrent.atomic.AtomicBoolean(false)

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
                            type = pending.type,
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

    fun updateType(type: com.sethv.fintrack.core.model.TransactionType) {
        _uiState.update { it.copy(type = type) }
    }

    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun acceptTransaction() {
        val pending = _uiState.value.pendingTransaction ?: return
        if (!actionInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                acceptInternal(pending)
            } finally {
                actionInFlight.set(false)
            }
        }
    }

    private suspend fun acceptInternal(pending: PendingTransaction) {
        _uiState.update { it.copy(isSaving = true, error = null) }
        try {
            val resultId = transactionRepository.acceptPending(
                pending = pending,
                amount = _uiState.value.amount,
                merchant = _uiState.value.merchant,
                category = _uiState.value.category,
                type = _uiState.value.type,
                notes = _uiState.value.notes,
            )
            if (resultId == TransactionRepository.ALREADY_HANDLED) {
                _uiState.update {
                    it.copy(isSaving = false, error = "This transaction was already reviewed.")
                }
                return
            }
            _uiState.update { it.copy(isSaving = false) }
            _accepted.emit(Unit)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isSaving = false, error = e.message ?: "Failed to accept transaction")
            }
        }
    }

    fun rejectTransaction() {
        val pending = _uiState.value.pendingTransaction ?: return
        if (!actionInFlight.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                rejectInternal(pending)
            } finally {
                actionInFlight.set(false)
            }
        }
    }

    private suspend fun rejectInternal(pending: PendingTransaction) {
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

    companion object {
        const val ARG_PENDING_ID = "pendingId"
    }
}