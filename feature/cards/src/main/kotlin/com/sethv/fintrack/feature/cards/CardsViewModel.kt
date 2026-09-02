package com.sethv.fintrack.feature.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.data.repository.CreditCardRepository
import com.sethv.fintrack.core.model.CardBill
import com.sethv.fintrack.core.model.CreditCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How urgent an unpaid bill is, derived from days until due. */
enum class DueUrgency { OVERDUE, TODAY, WITHIN_3_DAYS, WITHIN_A_WEEK, LATER }

data class BillRow(
    val bill: CardBill,
    val card: CreditCard,
)

data class CardSection(
    val card: CreditCard,
    val unpaid: List<CardBill>,
    val outstanding: Double,
    val paidCount: Int,
)

data class CardsUiState(
    val hasCards: Boolean = false,
    val totalOutstanding: Double = 0.0,
    val nearestDue: BillRow? = null,
    val sections: List<CardSection> = emptyList(),
    val paidHistory: List<BillRow> = emptyList(),
)

sealed interface CardsEvent {
    data class MarkedPaid(val message: String, val billId: Long, val paidAmount: Double) : CardsEvent
    data object Error : CardsEvent
}

@HiltViewModel
class CardsViewModel @Inject constructor(
    private val repository: CreditCardRepository,
) : ViewModel() {

    private val _events = MutableSharedFlow<CardsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CardsEvent> = _events.asSharedFlow()

    val uiState: StateFlow<CardsUiState> = combine(
        repository.getAllCards(),
        repository.getAllBills(),
    ) { cards, bills ->
        val cardsById = cards.associateBy { it.id }
        val rows = bills.mapNotNull { bill -> cardsById[bill.cardId]?.let { BillRow(bill, it) } }

        val unpaidRows = rows.filter { !it.bill.isPaid }.sortedBy { it.bill.dueDate }
        val paidRows = rows.filter { it.bill.isPaid }.sortedByDescending { it.bill.paidAt ?: 0L }

        val sections = cards.mapNotNull { card ->
            val cardBills = rows.filter { it.card.id == card.id }
            val unpaid = cardBills.filter { !it.bill.isPaid }.map { it.bill }.sortedBy { it.dueDate }
            if (cardBills.isEmpty()) return@mapNotNull null // card seen but no statements yet
            CardSection(
                card = card,
                unpaid = unpaid,
                outstanding = unpaid.sumOf { it.totalDue },
                paidCount = cardBills.count { it.bill.isPaid },
            )
        }

        CardsUiState(
            hasCards = rows.isNotEmpty() || cards.isNotEmpty(),
            totalOutstanding = unpaidRows.sumOf { it.bill.totalDue },
            nearestDue = unpaidRows.firstOrNull(),
            sections = sections,
            paidHistory = paidRows.take(10),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CardsUiState(),
    )

    /** Manual "I paid this" action — always trusts the user over heuristics. */
    fun markPaid(row: BillRow) {
        viewModelScope.launch {
            runCatching {
                repository.markBillPaid(row.bill.id, row.bill.totalDue)
            }.onSuccess {
                _events.emit(
                    CardsEvent.MarkedPaid(
                        message = "${row.card.displayName()} bill marked paid",
                        billId = row.bill.id,
                        paidAmount = row.bill.totalDue,
                    ),
                )
            }.onFailure {
                _events.emit(CardsEvent.Error)
            }
        }
    }

    /** Snackbar UNDO hook. */
    fun unmarkPaid(billId: Long) {
        viewModelScope.launch {
            runCatching { repository.unmarkBillPaid(billId) }
        }
    }

    fun renameCard(cardId: Long, label: String) {
        viewModelScope.launch {
            runCatching { repository.renameCard(cardId, label) }
        }
    }

    companion object {
        fun urgencyOf(bill: CardBill, nowMillis: Long = System.currentTimeMillis()): DueUrgency {
            val dayMillis = 24L * 60 * 60 * 1000
            val todayStart = nowMillis - (nowMillis % dayMillis)
            val dueStart = bill.dueDate - (bill.dueDate % dayMillis)
            val days = ((dueStart - todayStart) / dayMillis).toInt()
            return when {
                days < 0 -> DueUrgency.OVERDUE
                days == 0 -> DueUrgency.TODAY
                days <= 3 -> DueUrgency.WITHIN_3_DAYS
                days <= 7 -> DueUrgency.WITHIN_A_WEEK
                else -> DueUrgency.LATER
            }
        }

        fun daysRemaining(bill: CardBill, nowMillis: Long = System.currentTimeMillis()): Int {
            val dayMillis = 24L * 60 * 60 * 1000
            val todayStart = nowMillis - (nowMillis % dayMillis)
            val dueStart = bill.dueDate - (bill.dueDate % dayMillis)
            return ((dueStart - todayStart) / dayMillis).toInt()
        }
    }
}
