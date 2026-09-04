package com.sethv.fintrack.feature.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.ExpenseCategory
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddTransactionUiState(
    val amountText: String = "",
    val merchant: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHERS,
    val type: TransactionType = TransactionType.DEBIT,
    /** Selected date as epoch millis (noon, so calendar-day math is TZ-safe). */
    val dateMillis: Long = 0L,
    val notes: String = "",
    val isSaving: Boolean = false,
) {
    val parsedAmount: Double get() = amountText.toDoubleOrNull() ?: 0.0
    val canSave: Boolean = parsedAmount > 0.0 && merchant.isNotBlank()
}

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddTransactionUiState(dateMillis = LocalDate.now(clock).atTime(12, 0)
            .atZone(clock.zone).toInstant().toEpochMilli()),
    )
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    fun updateAmountText(text: String) {
        if (text.isEmpty() || text.matches(AMOUNT_PATTERN)) {
            _uiState.update { it.copy(amountText = text) }
        }
    }

    fun updateMerchant(merchant: String) {
        _uiState.update { it.copy(merchant = merchant) }
    }

    fun updateCategory(category: ExpenseCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun updateType(type: TransactionType) {
        _uiState.update { it.copy(type = type) }
    }

    fun updateDate(dateMillis: Long) {
        _uiState.update { it.copy(dateMillis = dateMillis) }
    }

    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave || state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                transactionRepository.insertTransaction(
                    Transaction(
                        amount = state.parsedAmount,
                        merchant = state.merchant.trim(),
                        category = state.category,
                        type = state.type,
                        dateTime = state.dateMillis,
                        bank = "Manual",
                        notes = state.notes.trim(),
                        smsBody = "",
                    ),
                )
            }.onSuccess { _saved.emit(Unit) }
                .onFailure {
                    _uiState.update { it.copy(isSaving = false) }
                }
        }
    }

    private companion object {
        val AMOUNT_PATTERN = Regex("^\\d*\\.?\\d*$")
    }
}
