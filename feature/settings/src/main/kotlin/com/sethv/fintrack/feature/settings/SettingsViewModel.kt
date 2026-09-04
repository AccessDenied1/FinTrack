package com.sethv.fintrack.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.database.FinTrackDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.room.withTransaction

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val database: FinTrackDatabase,
) : ViewModel() {

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
}
