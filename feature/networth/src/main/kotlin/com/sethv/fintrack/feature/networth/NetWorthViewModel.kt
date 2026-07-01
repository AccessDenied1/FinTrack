package com.sethv.fintrack.feature.networth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.core.data.repository.NetWorthRepository
import com.sethv.fintrack.core.model.NetWorthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NetWorthUiState(
    val netWorth: NetWorthState = NetWorthState(0.0, 0.0, 0.0, 0.0),
    val hasSetInitialBalance: Boolean = false,
    val showSetBalanceDialog: Boolean = false,
)

@HiltViewModel
class NetWorthViewModel @Inject constructor(
    private val netWorthRepository: NetWorthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetWorthUiState())
    val uiState: StateFlow<NetWorthUiState> = _uiState

    init {
        observeNetWorth()
        observeHasBalance()
    }

    private fun observeNetWorth() {
        viewModelScope.launch {
            netWorthRepository.getNetWorthState().collect { state ->
                _uiState.update { it.copy(netWorth = state) }
            }
        }
    }

    private fun observeHasBalance() {
        viewModelScope.launch {
            netWorthRepository.hasInitialBalance().collect { has ->
                _uiState.update { it.copy(hasSetInitialBalance = has) }
            }
        }
    }

    fun showSetBalanceDialog() {
        _uiState.update { it.copy(showSetBalanceDialog = true) }
    }

    fun dismissSetBalanceDialog() {
        _uiState.update { it.copy(showSetBalanceDialog = false) }
    }

    fun setInitialBalance(amount: Double) {
        viewModelScope.launch {
            netWorthRepository.setInitialBalance(amount)
            _uiState.update { it.copy(showSetBalanceDialog = false) }
        }
    }
}
