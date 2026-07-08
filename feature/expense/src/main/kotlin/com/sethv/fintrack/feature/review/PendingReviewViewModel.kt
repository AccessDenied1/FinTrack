package com.sethv.fintrack.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.data.repository.PendingTransactionRepository
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.PendingTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingReviewUiState(
    val items: List<PendingTransaction> = emptyList(),
    val isEmpty: Boolean = true,
)

sealed interface PendingReviewEvent {
    data class Accepted(val count: Int) : PendingReviewEvent
    data class Rejected(val count: Int) : PendingReviewEvent
    data class Error(val message: String) : PendingReviewEvent
}

@HiltViewModel
class PendingReviewViewModel @Inject constructor(
    private val pendingRepository: PendingTransactionRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    val uiState: StateFlow<PendingReviewUiState> =
        pendingRepository.getPending()
            .map { items -> PendingReviewUiState(items = items, isEmpty = items.isEmpty()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = PendingReviewUiState(),
            )

    private val _events = MutableSharedFlow<PendingReviewEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PendingReviewEvent> = _events.asSharedFlow()

    fun accept(pending: PendingTransaction) {
        viewModelScope.launch {
            runCatching {
                transactionRepository.acceptPending(
                    pending = pending,
                    amount = pending.amount,
                    merchant = pending.merchant,
                    category = pending.category,
                    notes = pending.notes,
                )
            }.onSuccess {
                _events.emit(PendingReviewEvent.Accepted(1))
            }.onFailure { t ->
                _events.emit(PendingReviewEvent.Error(t.message ?: "Failed to accept"))
            }
        }
    }

    fun reject(pending: PendingTransaction) {
        viewModelScope.launch {
            runCatching { pendingRepository.rejectPending(pending.id) }
                .onSuccess { _events.emit(PendingReviewEvent.Rejected(1)) }
                .onFailure { _events.emit(PendingReviewEvent.Error(it.message ?: "Failed to skip")) }
        }
    }

    fun acceptAll() {
        viewModelScope.launch {
            val pending = uiState.value.items
            if (pending.isEmpty()) return@launch
            runCatching {
                // Single atomic insert + status update instead of N round-trips.
                transactionRepository.acceptAllPending(pending)
            }.onSuccess {
                _events.emit(PendingReviewEvent.Accepted(pending.size))
            }.onFailure {
                _events.emit(PendingReviewEvent.Error(it.message ?: "Failed to accept all"))
            }
        }
    }

    fun rejectAll() {
        viewModelScope.launch {
            val ids = uiState.value.items.map { it.id }
            if (ids.isEmpty()) return@launch
            runCatching { pendingRepository.rejectAllPending(ids) }
                .onSuccess { _events.emit(PendingReviewEvent.Rejected(ids.size)) }
                .onFailure { _events.emit(PendingReviewEvent.Error(it.message ?: "Failed to skip all")) }
        }
    }
}