package com.localnet.wifihome.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localnet.wifihome.data.model.PingResult
import com.localnet.wifihome.data.model.PingSummary
import com.localnet.wifihome.data.network.PingTool
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PingUiState(
    val host: String = "8.8.8.8",
    val isRunning: Boolean = false,
    val results: List<PingResult> = emptyList(),
    val summary: PingSummary? = null
)

class PingViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PingUiState())
    val uiState: StateFlow<PingUiState> = _uiState.asStateFlow()

    private var pingJob: Job? = null

    fun setHost(host: String) {
        _uiState.value = _uiState.value.copy(host = host)
    }

    fun startPing() {
        if (_uiState.value.isRunning) return
        val host = _uiState.value.host.ifBlank { "8.8.8.8" }
        _uiState.value = _uiState.value.copy(isRunning = true, results = emptyList(), summary = null)

        pingJob = viewModelScope.launch {
            val collected = mutableListOf<PingResult>()
            PingTool.pingStream(host, count = 20, continuous = false).collect { result ->
                collected.add(result)
                _uiState.value = _uiState.value.copy(results = collected.toList())
            }
            _uiState.value = _uiState.value.copy(
                isRunning = false,
                summary = PingTool.summarize(collected)
            )
        }
    }

    fun stopPing() {
        pingJob?.cancel()
        _uiState.value = _uiState.value.copy(isRunning = false)
    }
}
