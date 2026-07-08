package com.sethv.fintrack.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sethv.fintrack.service.sms.HistoricalSmsProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

/** Emitted when a scan finishes and the user should be taken to the Review tab. */
sealed interface ScanNavEvent {
    data class NavigateToReview(val foundCount: Int) : ScanNavEvent
}

@HiltViewModel
class ScanSmsViewModel @Inject constructor(
    private val historicalSmsProcessor: HistoricalSmsProcessor,
) : ViewModel() {

    private val _scanState = MutableStateFlow(ScanState())
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _navEvents = MutableSharedFlow<ScanNavEvent>(extraBufferCapacity = 4)
    val navEvents: SharedFlow<ScanNavEvent> = _navEvents.asSharedFlow()

    fun startScan() {
        if (_scanState.value.status == ScanStatus.SCANNING) return

        viewModelScope.launch(Dispatchers.IO) {
            _scanState.update { it.copy(status = ScanStatus.SCANNING) }
            try {
                val count = historicalSmsProcessor.scanAndProcess()
                _scanState.update {
                    it.copy(status = ScanStatus.COMPLETED, transactionsFound = count)
                }
                if (count > 0) {
                    _navEvents.emit(ScanNavEvent.NavigateToReview(count))
                }
            } catch (e: Exception) {
                _scanState.update { it.copy(status = ScanStatus.ERROR) }
            }
        }
    }

    fun resetScanState() {
        _scanState.update { ScanState() }
    }

    /** Marks the most-recent NavigateToReview event as handled. */
    fun onNavHandled() {
        // No-op marker; SharedFlow replay=0 + extraBufferCapacity means consumers see each event once.
    }
}