package com.sethv.fintrack.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.data.repository.PendingTransactionRepository
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.PendingTransaction
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A pending row plus derived context for the reviewer. */
data class PendingReviewItem(
    val pending: PendingTransaction,
    /** Same amount+merchant+type on the same calendar day already exists elsewhere. */
    val possibleDuplicate: Boolean = false,
)

data class PendingReviewUiState(
    val items: List<PendingReviewItem> = emptyList(),
    val isEmpty: Boolean = true,
    val duplicateCount: Int = 0,
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
        combine(
            pendingRepository.getPending(),
            transactionRepository.getAllTransactions(),
        ) { pendings, accepted ->
            val items = pendings.map { p ->
                PendingReviewItem(
                    pending = p,
                    // Exact re-deliveries are already dropped at ingest time;
                    // this catches fuzzy twins so the USER makes the call.
                    possibleDuplicate = isFuzzyDuplicateOfAccepted(p, accepted) ||
                        countSiblingDuplicates(p, pendings) > 0,
                )
            }
            PendingReviewUiState(
                items = items,
                isEmpty = items.isEmpty(),
                duplicateCount = items.count { it.possibleDuplicate },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PendingReviewUiState(),
        )

    private val _events = MutableSharedFlow<PendingReviewEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<PendingReviewEvent> = _events.asSharedFlow()

    // Flipped synchronously in the click handler — a rapid double-tap must
    // never enqueue two actions (the repository guard is defense-in-depth).
    private val actionInProgress = java.util.concurrent.atomic.AtomicBoolean(false)

    private fun launchAction(block: suspend () -> Unit) {
        if (!actionInProgress.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                block()
            } finally {
                actionInProgress.set(false)
            }
        }
    }

    fun accept(item: PendingReviewItem) = launchAction {
        val pending = item.pending
        runCatching {
            transactionRepository.acceptPending(
                pending = pending,
                amount = pending.amount,
                merchant = pending.merchant,
                category = pending.category,
                type = pending.type,
                notes = pending.notes,
            )
        }.onSuccess { resultId ->
            if (resultId != TransactionRepository.ALREADY_HANDLED) {
                _events.emit(PendingReviewEvent.Accepted(1))
            }
        }.onFailure { t ->
            _events.emit(PendingReviewEvent.Error(t.message ?: "Failed to accept"))
        }
    }

    fun reject(item: PendingReviewItem) = launchAction {
        runCatching { pendingRepository.rejectPending(item.pending.id) }
            .onSuccess { _events.emit(PendingReviewEvent.Rejected(1)) }
            .onFailure { _events.emit(PendingReviewEvent.Error(it.message ?: "Failed to skip")) }
    }

    fun acceptAll() {
        val items = uiState.value.items
        if (items.isEmpty()) return
        launchAction {
            runCatching {
                // Single atomic insert + status update instead of N round-trips.
                transactionRepository.acceptAllPending(items.map { it.pending })
            }.onSuccess { insertedIds ->
                if (insertedIds.isNotEmpty()) {
                    _events.emit(PendingReviewEvent.Accepted(insertedIds.size))
                }
            }.onFailure {
                _events.emit(PendingReviewEvent.Error(it.message ?: "Failed to accept all"))
            }
        }
    }

    fun rejectAll() {
        val ids = uiState.value.items.map { it.pending.id }
        if (ids.isEmpty()) return
        launchAction {
            runCatching { pendingRepository.rejectAllPending(ids) }
                .onSuccess { _events.emit(PendingReviewEvent.Rejected(ids.size)) }
                .onFailure { _events.emit(PendingReviewEvent.Error(it.message ?: "Failed to skip all")) }
        }
    }

    private fun isFuzzyDuplicateOfAccepted(
        candidate: PendingTransaction,
        accepted: List<com.sethv.fintrack.core.model.Transaction>,
    ): Boolean = accepted.any { txn ->
        txn.type == candidate.type &&
            txn.amount == candidate.amount &&
            txn.merchant.equals(candidate.merchant, ignoreCase = true) &&
            isSameCalendarDay(txn.dateTime, candidate.dateTime)
    }

    private fun countSiblingDuplicates(
        candidate: PendingTransaction,
        pendings: List<PendingTransaction>,
    ): Int = pendings.count { other ->
        other.id != candidate.id &&
            other.type == candidate.type &&
            other.amount == candidate.amount &&
            other.merchant.equals(candidate.merchant, ignoreCase = true) &&
            isSameCalendarDay(other.dateTime, candidate.dateTime)
    }

    private fun isSameCalendarDay(a: Long, b: Long): Boolean =
        Instant.ofEpochMilli(a).atZone(ZoneId.systemDefault()).toLocalDate() ==
            Instant.ofEpochMilli(b).atZone(ZoneId.systemDefault()).toLocalDate()
}
