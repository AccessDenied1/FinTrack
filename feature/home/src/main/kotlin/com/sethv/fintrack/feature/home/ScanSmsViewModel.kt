package com.sethv.fintrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.service.sms.HistoricalSmsProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanState(
    val status: ScanStatus = ScanStatus.IDLE,
    val transactionsFound: Int = 0,
)

enum class ScanStatus {
    IDLE,
    SCANNING,
    COMPLETED,
    ERROR,
}

@HiltViewModel
class ScanSmsViewModel @Inject constructor(
    private val historicalSmsProcessor: HistoricalSmsProcessor,
) : ViewModel() {

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    fun startScan() {
        if (_scanState.value.status == ScanStatus.SCANNING) return

        viewModelScope.launch(Dispatchers.IO) {
            _scanState.update { it.copy(status = ScanStatus.SCANNING) }
            try {
                val count = historicalSmsProcessor.scanAndProcess()
                _scanState.update {
                    it.copy(status = ScanStatus.COMPLETED, transactionsFound = count)
                }
            } catch (e: Exception) {
                _scanState.update { it.copy(status = ScanStatus.ERROR) }
            }
        }
    }

    fun resetScanState() {
        _scanState.update { ScanState() }
    }
}
