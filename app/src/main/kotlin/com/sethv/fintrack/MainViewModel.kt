package com.sethv.fintrack

import androidx.lifecycle.ViewModel
import com.sethv.fintrack.core.data.repository.PendingTransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

@HiltViewModel
class MainViewModel @Inject constructor(
    pendingRepository: PendingTransactionRepository,
) : ViewModel() {

    val pendingCount: StateFlow<Int> = pendingRepository.getPendingCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )
}