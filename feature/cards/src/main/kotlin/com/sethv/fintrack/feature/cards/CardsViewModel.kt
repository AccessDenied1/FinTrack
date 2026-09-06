package com.sethv.fintrack.feature.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.data.repository.CreditCardRepository
import com.sethv.fintrack.core.data.repository.TransactionRepository
import com.sethv.fintrack.core.model.CardBill
import com.sethv.fintrack.core.model.CreditCard
import com.sethv.fintrack.core.model.Transaction
import com.sethv.fintrack.core.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
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

/**
 * Per-card insight bundle for the carousel hero + statement deep-dive.
 *
 * @param outstanding sum of the card's unpaid [CardBill.totalDue]
 * @param limit the manual [CreditCard.creditLimitOverride] when set, else the
 *   latest bill's parsed [CardBill.creditLimit]; null when neither is known
 * @param utilization outstanding / limit as a fraction, null when [limit] is
 *   null or non-positive (no divide-by-zero)
 * @param dueCalendar the card's unpaid bills, sorted by [CardBill.dueDate]
 * @param spendByCategory DEBIT spend grouped by category within the current
 *   statement window
 * @param spendTrend daily DEBIT sums for the trailing [TREND_DAYS] days,
 *   oldest first
 */
data class CardInsights(
    val outstanding: Double,
    val limit: Double?,
    val utilization: Float?,
    val dueCalendar: List<CardBill>,
    val spendByCategory: Map<com.sethv.fintrack.core.model.ExpenseCategory, Double>,
    val spendTrend: List<Double>,
)

data class CardsUiState(
    val hasCards: Boolean = false,
    val totalOutstanding: Double = 0.0,
    val nearestDue: BillRow? = null,
    val sections: List<CardSection> = emptyList(),
    val paidHistory: List<BillRow> = emptyList(),
    val selectedCardId: Long? = null,
    val insights: Map<Long, CardInsights> = emptyMap(),
)

sealed interface CardsEvent {
    data class MarkedPaid(val message: String, val billId: Long, val paidAmount: Double) : CardsEvent
    data object Error : CardsEvent
}

@HiltViewModel
class CardsViewModel @Inject constructor(
    private val repository: CreditCardRepository,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _events = MutableSharedFlow<CardsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CardsEvent> = _events.asSharedFlow()

    private val _selectedCard = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<CardsUiState> = combine(
        repository.getAllCards(),
        repository.getAllBills(),
        transactionRepository.getAllTransactions(),
        _selectedCard,
    ) { cards, bills, transactions, selectedCardId ->
        val now = clock.millis()
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
            selectedCardId = selectedCardId,
            insights = buildInsights(cards, bills, transactions, now),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CardsUiState(),
    )

    /**
     * Per-card insights for every registered card: utilization, due calendar,
     * statement-window spend by category, and the trailing-30-day debit trend.
     */
    private fun buildInsights(
        cards: List<CreditCard>,
        bills: List<CardBill>,
        transactions: List<Transaction>,
        nowMillis: Long,
    ): Map<Long, CardInsights> = cards.associateBy(keySelector = { it.id }) { card ->
        val cardBills = bills.filter { it.cardId == card.id }
        val unpaid = cardBills.filter { !it.isPaid }

        val outstanding = unpaid.sumOf { it.totalDue }
        val limit = card.creditLimitOverride
            ?: cardBills.filter { it.creditLimit != null }.maxByOrNull { it.dueDate }?.creditLimit
        val utilization = if (limit != null && limit > 0.0) (outstanding / limit).toFloat() else null
        val dueCalendar = unpaid.sortedBy { it.dueDate }

        // Statement window: [statementStart, dueDate] of the nearest unpaid bill
        // (statementStart of 0 means "unknown" → derive dueDate - 30d); falls
        // back to the trailing 30 days when the card has no outstanding bill.
        val nearest = unpaid.minByOrNull { it.dueDate }
        val windowStart = nearest?.let {
            if (it.statementStart > 0L) it.statementStart else it.dueDate - FALLBACK_WINDOW
        } ?: (nowMillis - FALLBACK_WINDOW)
        val windowEnd = nearest?.dueDate ?: nowMillis

        val debits = cardTransactions(card, cards, transactions)
            .filter { it.type == TransactionType.DEBIT }
        val windowDebits = debits.filter { it.dateTime in windowStart..windowEnd }
        val spendByCategory = windowDebits
            .groupingBy { it.category }
            .fold(0.0) { acc, tx -> acc + tx.amount }

        val todayStart = nowMillis - (nowMillis % DAY_MILLIS)
        val spendTrend = List(TREND_DAYS) { i ->
            val daysAgo = TREND_DAYS - 1 - i // i=0 → oldest, i=last → today
            val dayStart = todayStart - daysAgo * DAY_MILLIS
            debits.filter { it.dateTime in dayStart until dayStart + DAY_MILLIS }.sumOf { it.amount }
        }

        CardInsights(
            outstanding = outstanding,
            limit = limit,
            utilization = utilization,
            dueCalendar = dueCalendar,
            spendByCategory = spendByCategory,
            spendTrend = spendTrend,
        )
    }

    /**
     * Transactions attributable to [card] (controller ruling, Task 3 review —
     * multi-card-same-bank ambiguity):
     *
     * 1. Prefer the exact link: `tx.cardId == card.id`.
     * 2. For rows with `tx.cardId == null` (the legacy 3908 rows, manual adds),
     *    fall back to a case-insensitive, trimmed `tx.bank == card.bankName`
     *    match — BUT only when no OTHER card shares that normalized bank name.
     *    When two cards share a bank name the fallback is ambiguous, so it is
     *    skipped entirely for both (show 0, never double-count a row).
     */
    private fun cardTransactions(
        card: CreditCard,
        cards: List<CreditCard>,
        transactions: List<Transaction>,
    ): List<Transaction> {
        val bank = card.bankName.trim().uppercase()
        val ambiguous = cards.count { it.bankName.trim().uppercase() == bank } > 1
        return transactions.filter { tx ->
            when {
                tx.cardId != null -> tx.cardId == card.id
                ambiguous -> false
                else -> tx.bank.trim().uppercase() == bank
            }
        }
    }

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
            runCatching {
                repository.renameCard(cardId, label)
            }
        }
    }

    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deleteCard(cardId)
            }
        }
    }

    /** Highlights a card in the carousel. */
    fun onSelectCard(id: Long) {
        _selectedCard.value = id
    }

    /** Sets (or clears with null) the manual credit-limit override for a card. */
    fun onUpdateLimit(id: Long, limit: Double?) {
        viewModelScope.launch {
            runCatching { repository.updateLimit(id, limit) }
        }
    }
    companion object {
        const val TREND_DAYS = 30
        private const val DAY_MILLIS: Long = 24L * 60 * 60 * 1000
        private const val FALLBACK_WINDOW: Long = 30L * DAY_MILLIS

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
