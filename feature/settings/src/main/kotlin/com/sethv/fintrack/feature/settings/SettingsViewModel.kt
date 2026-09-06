package com.sethv.fintrack.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.data.repository.CreditCardRepository
import com.sethv.fintrack.core.database.FinTrackDatabase
import com.sethv.fintrack.core.model.CreditCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.room.withTransaction

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val database: FinTrackDatabase,
    private val repository: CreditCardRepository,
) : ViewModel() {

    val cards: StateFlow<List<CreditCard>> = repository.getAllCards().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    fun deleteAllData(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            database.withTransaction {
                database.transactionDao().deleteAll()
                database.pendingTransactionDao().deleteAll()
                database.cardBillDao().deleteAll()
                database.bankCardDao().deleteAll()
                database.balanceSettingsDao().deleteAll()
            }
            onDone()
        }
    }

    fun onUpdateLimit(cardId: Long, limit: Double?) {
        viewModelScope.launch {
            repository.updateLimit(cardId, limit)
        }
    }
}
